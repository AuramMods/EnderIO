#!/usr/bin/env python3
"""Exercise placed Ender IO timer, counting and toggle filters on a dedicated Forge server."""

import argparse
import json
from pathlib import Path
import shutil
import sys
import time

sys.dont_write_bytecode = True
from packaged_cc_tweaked import CONDUIT, face, set_lever
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage


LEGACY_BARREL = "3309 80 7"
LEGACY_FILTERS = (
    ("redstone_xor_filter", '{Channel1:"BLUE",Channel2:"YELLOW"}',
     "redstone_filter_double_channel", '{channel1:"blue",channel2:"yellow"}'),
    ("redstone_counting_filter", '{Channel:"RED",MaxCount:12,Ticks:7,Deactivated:1b}',
     "redstone_count_filter", '{channel1:"red",maxCount:12,ticks:7,deactivated:1b}'),
    ("redstone_toggle_filter", '{Active:1b,Deactivated:0b}',
     "redstone_tlatch_filter", '{active:1b,deactivated:0b}'),
    ("redstone_timer_filter", '{Ticks:6,MaxTicks:18}',
     "redstone_timer_filter", '{ticks:6,maxTicks:18}'),
)


def pos(x, lane):
    return f"{x} 80 {lane}"


def endpoint(x, lane, side):
    return f'{pos(x, lane)} Connections[0][{{Side:"{side}"}}]'


def configure(server, lane, *, insert, filter_id=None, tag=None):
    path = endpoint(3312, lane, "east") if insert else endpoint(3311, lane, "west")
    server.command(f'data modify block {path}.Config set value {{type:"enderio:redstone",'
                   f'is_insert:{int(insert)}b,is_extract:{int(not insert)}b,insert_channel:"white",'
                   'extract_channel:"white",is_strong_output_signal:0b}')
    if filter_id:
        tag_field = f",tag:{tag}" if tag else ""
        item = f'{{id:"enderio:{filter_id}",Count:1b{tag_field}}}'
        inventory = f'[{{}},{item}]' if insert else f'[{item}]'
        server.command(f"data modify block {path}.Inventory set value {inventory}")


def prepare(server, results):
    server.command("gamerule doMobSpawning false")
    server.command("forceload add 3296 0 3327 15")
    server.assert_state("both_route_chunks_loaded", f"if loaded {pos(3311, 8)} if loaded {pos(3312, 8)}", results)
    server.command("fill 3309 79 7 3314 79 13 minecraft:stone")
    for lane in (8, 12):
        set_lever(server, pos(3310, lane), False)
        server.command(f"setblock {pos(3313, lane)} minecraft:redstone_lamp")
        for x in (3311, 3312):
            server.command(f"setblock {pos(x, lane)} {CONDUIT}")
        conditions = (face(pos(3311, lane), "west"), face(pos(3312, lane), "east"),
                      face(pos(3311, lane), "east", "connected_conduit"),
                      face(pos(3312, lane), "west", "connected_conduit"))
        server.assert_state(f"route_{lane}_crosses_chunk_edge", " ".join(conditions), results)


def lamp(lane, lit):
    return f"if block {pos(3313, lane)} minecraft:redstone_lamp[lit={str(lit).lower()}]"


def latch_state(active, armed):
    path = endpoint(3312, 8, "east")
    stack = f"{path}.Inventory[1]"
    state = f'{stack}.tag."enderio:data"."enderio:redstone_tlatch_filter"'
    return (f'if data block {path}.Inventory[{{id:"enderio:redstone_toggle_filter"}}] '
            f'{"unless" if armed else "if"} data block {state}{{deactivated:0b}} '
            f'{"if" if active else "unless"} data block {state}{{active:1b}}')


def wait_for_count(server, results, name, comparison):
    path = endpoint(3312, 12, "east")
    field = f'{path}.Inventory[1].tag."enderio:data"."enderio:redstone_count_filter".ticks'
    condition = f"if score pulses port_filter {comparison}"
    deadline = time.monotonic() + 8
    marker = "PORT_ASSERT_" + name
    while True:
        server.command(f"execute store result score pulses port_filter run data get block {field}")
        output = server.command(f"execute {condition} run say {marker}")
        passed = f"[Server] {marker}" in output
        if passed or time.monotonic() >= deadline:
            results.append({"assertion": name, "passed": passed, "conditions": condition})
            if not passed:
                server.command(f"data get block {pos(3311, 12)}")
                server.command(f"data get block {pos(3312, 12)}")
                raise AssertionError(f"{name} failed; see {server.log_path}")
            server.command("scoreboard players get pulses port_filter")
            return
        time.sleep(0.1)


def exercise_latch(server, results):
    configure(server, 8, insert=False)
    configure(server, 8, insert=True, filter_id="redstone_toggle_filter")
    server.assert_state("fresh_latch_stays_dark_with_zero_input", lamp(8, False), results)
    set_lever(server, pos(3310, 8), True)
    server.assert_state("first_rising_edge_lights_lamp", f"{lamp(8, True)} {latch_state(True, False)}", results)
    set_lever(server, pos(3310, 8), False)
    server.assert_state("falling_edge_rearms_without_clearing_output", f"{lamp(8, True)} {latch_state(True, True)}", results)
    set_lever(server, pos(3310, 8), True)
    server.assert_state("second_rising_edge_clears_lamp", f"{lamp(8, False)} {latch_state(False, False)}", results)
    set_lever(server, pos(3310, 8), False)
    server.assert_state("next_falling_edge_rearms_with_output_off", f"{lamp(8, False)} {latch_state(False, True)}", results)
    set_lever(server, pos(3310, 8), True)
    server.assert_state("third_rising_edge_leaves_latch_on_and_input_high", f"{lamp(8, True)} {latch_state(True, False)}", results)


def exercise_timer(server, results):
    configure(server, 12, insert=True, filter_id="redstone_counting_filter",
              tag='{"enderio:data":{"enderio:redstone_count_filter":'
                  '{channel1:"white",maxCount:999,ticks:0,deactivated:1b}}}')
    # These are the bytes the previous port wrote for a timer set to 18 ticks.
    configure(server, 12, insert=False, filter_id="redstone_timer_filter",
              tag='{"enderio:data":{"enderio:redstone_timer_filter":{ticks:18,maxTicks:18}}}')
    server.command("scoreboard objectives add port_filter dummy")
    wait_for_count(server, results, "timer_reached_four_distinct_pulses", "matches 4..")
    server.command("scoreboard players operation before_restart port_filter = pulses port_filter")


def exercise(server, results):
    prepare(server, results)
    exercise_latch(server, results)
    exercise_timer(server, results)


def reopened(server, results):
    try:
        server.assert_state("held_high_latch_keeps_saved_output_after_restart",
                            f'if block {pos(3310, 8)} minecraft:lever[powered=true] {lamp(8, True)} {latch_state(True, False)}', results)
        set_lever(server, pos(3310, 8), False)
        server.assert_state("reopened_latch_rearms_on_falling_edge", f"{lamp(8, True)} {latch_state(True, True)}", results)
        set_lever(server, pos(3310, 8), True)
        server.assert_state("reopened_latch_toggles_on_new_rising_edge", f"{lamp(8, False)} {latch_state(False, False)}", results)
    except AssertionError:
        server.command(f"execute {lamp(8, True)} run say PORT_REOPENED_LAMP_LIT")
        for x in (3311, 3312):
            server.command(f"data get block {pos(x, 8)}")
        raise
    wait_for_count(server, results, "saved_counter_receives_more_automatic_pulses_after_restart", "> before_restart port_filter")


def legacy_fixture(server, results):
    server.command("forceload add 3296 0")
    server.assert_state("legacy_barrel_chunk_loaded", f"if loaded {LEGACY_BARREL}", results)
    server.command(f"setblock {LEGACY_BARREL} minecraft:barrel")
    for slot, (item, old, _, _) in enumerate(LEGACY_FILTERS):
        tag = old[:-1] + ',PortFixture:119}'
        server.command(f"item replace block {LEGACY_BARREL} container.{slot} with enderio:{item}{tag}")
        server.assert_state(f"legacy_saved_{item}",
                            f'if data block {LEGACY_BARREL} Items[{{Slot:{slot}b,id:"enderio:{item}",tag:{tag}}}]', results)


def verify_converted(server, results):
    for slot, (item, _, key, value) in enumerate(LEGACY_FILTERS):
        item_path = f'{LEGACY_BARREL} Items[{{Slot:{slot}b,id:"enderio:{item}"}}].tag'
        expected = f'{{PortFixture:119,"enderio:data":{{"enderio:{key}":{value}}}}}'
        former_keys = ("Active", "Deactivated", "Channel", "Channel1", "Channel2", "MaxCount", "MaxTicks", "Ticks")
        removed = " ".join(f"unless data block {item_path}.{name}" for name in former_keys)
        server.assert_state(f"converted_{item}_keeps_its_exact_configuration",
                            f'if data block {item_path}{expected} {removed}', results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--legacy-jar", type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")

    root = ROOT / "build/port-runtime" / args.name
    directory = root
    if args.legacy_jar:
        root.mkdir()
        directory = root / "port"
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "forge": FORGE, "jarSha256": digest(directory / "mods/enderio.jar"),
              "assertions": [], "servers": []}
    try:
        if args.legacy_jar:
            legacy = root / "legacy"
            prepare_profile(args, legacy, args.legacy_jar)
            report["legacyJarSha256"] = digest(legacy / "mods/enderio.jar")
            run_stage(args, legacy, report, "legacy", legacy_fixture)
            shutil.copytree(legacy / WORLD, directory / WORLD)
            run_stage(args, directory, report, "converted", verify_converted)
        run_stage(args, directory, report, "exercise", exercise)
        def after_restart(server, results):
            reopened(server, results)
            if args.legacy_jar:
                verify_converted(server, results)

        run_stage(args, directory, report, "reopened", after_restart)
        report["passed"] = True
    finally:
        path = root / "redstone-filters-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Packaged redstone filters passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
