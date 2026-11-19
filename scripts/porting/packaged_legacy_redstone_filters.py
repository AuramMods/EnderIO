#!/usr/bin/env python3
"""Convert native Forge 6.2 conduit inventories and exercise the original redstone circuits."""

import argparse
import json
from pathlib import Path
import shutil
import sys

sys.dont_write_bytecode = True
from packaged_cc_tweaked import set_lever
from packaged_levitation_staff import game_tick
from packaged_redstone_filter_menus import wait_for_redstone_sample
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage


CHUNKS = "4512 0 4543 15"
LANES = (1, 4, 8, 12, 15)
XOR = "redstone_xor_filter"
TIMER = "redstone_timer_filter"
COUNT = "redstone_counting_filter"
LATCH = "redstone_toggle_filter"
SENSOR = "redstone_sensor_filter"
NOT = "redstone_not_filter"


def pos(x, z):
    return f"{x} 80 {z}"


def lamp(z, lit):
    return f"if block {pos(4529, z)} minecraft:redstone_lamp[lit={str(lit).lower()}]"


def connection(z, side):
    x = 4528 if side == "east" else 4527
    return f'{pos(x, z)} Connections[0][{{Side:"{side}",Status:"connected_block"}}]'


def stock_tag(marker, key=None, value=None):
    extra = f',"enderio:data":{{"enderio:{key}":{value}}}' if key else ""
    return f'{{PortFilter:"{marker}"{extra}}}'


def installed(z, side, item, marker, key=None, value=None):
    inventory = f"{connection(z, side)}.Inventory"
    index = int(side == "east")
    return (f'if data block {inventory}[{{id:"enderio:{item}",Count:1}}] '
            f'if data block {inventory}[{index}].tag{stock_tag(marker, key, value)}')


def old_slot(z, side, item, tag):
    x = 4528 if side == "east" else 4527
    kind = "InsertFilter" if side == "east" else "ExtractFilter"
    return (f'if data block {pos(x, z)} ConduitBundle.Connections.{side}."0".{kind}'
            f'{{id:"enderio:{item}",Count:1b,tag:{tag}}}')


def load_routes(server, results, stage):
    server.command(f"forceload add {CHUNKS}")
    conditions = " ".join(f"if loaded {pos(x, 8)}" for x in (4527, 4528))
    server.assert_state(f"both_filter_chunks_loaded_{stage}", conditions, results)


def fill_sensor_chest(server, full):
    items = ','.join(f'{{Slot:{slot}b,id:"minecraft:cobblestone",Count:64b}}' for slot in range(5)) if full else ""
    server.command(f"data merge block {pos(4526, 15)} {{Items:[{items}]}}")


def prepare_legacy(server, results):
    load_routes(server, results, "legacy")
    server.command("gamerule doMobSpawning false")
    server.command("fill 4525 79 1 4531 79 15 minecraft:stone")
    for z in LANES:
        server.command(f"setblock {pos(4529, z)} minecraft:redstone_lamp")
    for z in (1, 4, 8, 12):
        set_lever(server, pos(4526, z), False)
    set_lever(server, pos(4527, 3), False)
    server.command(f"setblock {pos(4526, 15)} minecraft:chest")
    output = server.command("port_legacy_redstone_filters")
    if "Legacy redstone filter routes created" not in output:
        raise AssertionError(output)

    set_lever(server, pos(4526, 4), True)
    set_lever(server, pos(4526, 8), True)
    fill_sensor_chest(server, True)
    server.assert_state("original_release_uses_gate_latch_and_sensor",
                        f"{lamp(1, False)} {lamp(4, True)} {lamp(8, True)} {lamp(12, False)} {lamp(15, False)}", results)
    set_lever(server, pos(4527, 3), True)
    server.assert_state("original_two_channels_drive_xor", lamp(4, False), results)
    set_lever(server, pos(4527, 3), False)
    server.assert_state("original_gate_recovers_on_one_channel", lamp(4, True), results)
    fill_sensor_chest(server, False)
    server.assert_state("original_sensor_and_inverter_follow_the_forced_chest_connection", lamp(15, True), results)
    fill_sensor_chest(server, True)
    server.assert_state("original_analog_input_clears_the_inverter", lamp(15, False), results)

    stacks = (
        (4, "east", XOR, '{PortFilter:"gate",Channel1:"RED",Channel2:"BLUE"}'),
        (8, "east", LATCH, '{PortFilter:"latch",Active:1b,Deactivated:0b}'),
        (12, "west", TIMER, '{PortFilter:"timer",Ticks:30,MaxTicks:80}'),
        (12, "east", COUNT, '{PortFilter:"counter",Channel:"PURPLE",MaxCount:8,Ticks:7,Deactivated:1b}'),
        (15, "west", SENSOR, '{PortFilter:"sensor"}'),
        (15, "east", NOT, '{PortFilter:"inverter"}'),
        (1, "west", TIMER, '{PortFilter:"due-timer",Ticks:30,MaxTicks:30}'),
        (1, "east", COUNT, '{PortFilter:"due-counter",Channel:"LIME",MaxCount:3,Ticks:2,Deactivated:1b}'),
    )
    for z, side, item, tag in stacks:
        server.assert_state(f"original_bundle_saves_{item}_in_lane_{z}", old_slot(z, side, item, tag), results)


def modern_snapshot(server, results, stage):
    latch_state = installed(8, "east", LATCH, "latch", "redstone_tlatch_filter", "{active:1b,deactivated:0b}")
    counter_state = installed(12, "east", COUNT, "counter", "redstone_count_filter",
                              '{channel1:"purple",maxCount:8,ticks:7,deactivated:1b}')
    timer_state = installed(12, "west", TIMER, "timer", "redstone_timer_filter", "{ticks:30,maxTicks:80}")
    gate_state = installed(4, "east", XOR, "gate", "redstone_filter_double_channel", '{channel1:"red",channel2:"blue"}')
    server.assert_state(f"keeps_xor_channels_and_input_directions_{stage}", f'{gate_state} '
                        f'if data block {connection(4, "west")}.Config{{is_extract:1b,extract_channel:"red"}} '
                        f'if data block {connection(4, "north")}.Config{{is_extract:1b,extract_channel:"blue"}} '
                        f'{lamp(4, True)}', results)
    server.assert_state(f"keeps_held_latch_output_and_edge_state_{stage}", f'{latch_state} {lamp(8, True)} '
                        f'if block {pos(4526, 8)} minecraft:lever[powered=true]', results)
    server.assert_state(f"keeps_paused_timer_phase_{stage}", f'{timer_state} '
                        f'if data block {connection(12, "west")}.Config{{is_extract:0b,extract_channel:"purple"}}', results)
    server.assert_state(f"keeps_paused_counter_progress_and_arm_{stage}", f'{counter_state} '
                        f'if data block {connection(12, "east")}.Config{{is_insert:0b,insert_channel:"purple"}} {lamp(12, False)}', results)
    server.assert_state(f"keeps_the_sensor_and_inverter_in_their_slots_{stage}",
                        f'{installed(15, "west", SENSOR, "sensor")} {installed(15, "east", NOT, "inverter")} {lamp(15, False)}', results)
    due_timer = installed(1, "west", TIMER, "due-timer", "redstone_timer_filter", "{ticks:29,maxTicks:30}")
    due_counter = installed(1, "east", COUNT, "due-counter", "redstone_count_filter",
                            '{channel1:"lime",maxCount:3,ticks:2,deactivated:1b}')
    server.assert_state(f"keeps_due_timer_ready_for_first_sample_{stage}", f'{due_timer} {due_counter} '
                        f'if data block {connection(1, "west")}.Config{{is_extract:0b,extract_channel:"lime"}} '
                        f'if data block {connection(1, "east")}.Config{{is_insert:0b,insert_channel:"lime"}}', results)
    for z, side, old_key in ((4, "east", "Channel1"), (8, "east", "Active"), (12, "west", "Ticks"), (12, "east", "MaxCount")):
        path = f"{connection(z, side)}.Inventory[{int(side == 'east')}].tag"
        server.assert_state(f"consumes_the_original_{old_key}_tag_{stage}", f"unless data block {path}.{old_key}", results)


def exercise_live_filters(server, results, stage):
    set_lever(server, pos(4527, 3), True)
    server.assert_state(f"xor_clears_with_both_inputs_{stage}", lamp(4, False), results)
    set_lever(server, pos(4527, 3), False)
    server.assert_state(f"xor_returns_with_one_input_{stage}", lamp(4, True), results)
    fill_sensor_chest(server, False)
    server.assert_state(f"sensor_and_inverter_follow_empty_inventory_{stage}", lamp(15, True), results)
    fill_sensor_chest(server, True)
    server.assert_state(f"sensor_and_inverter_follow_filled_inventory_{stage}", lamp(15, False), results)


def converted(server, results):
    load_routes(server, results, "converted")
    wait_for_redstone_sample(server)
    modern_snapshot(server, results, "converted")
    exercise_live_filters(server, results, "converted")
    # Let the server actually unload both chunks. No player or fixture mod holds a ticket.
    server.command(f"forceload remove {CHUNKS}")
    unloaded = " ".join(f"unless loaded {pos(x, 8)}" for x in (4527, 4528))
    server.assert_state("both_filter_chunks_were_evicted", unloaded, results, timeout=40)
    load_routes(server, results, "chunk_reload")
    wait_for_redstone_sample(server)
    modern_snapshot(server, results, "chunk_reload")
    exercise_live_filters(server, results, "chunk_reload")


def resumed_timer(server, results, z, name, saved_phase, period, starting_count, expected_window):
    inlet, outlet = connection(z, "west"), connection(z, "east")
    counter_path = f'{outlet}.Inventory[1].tag."enderio:data"."enderio:redstone_count_filter"'
    server.command(f"data modify block {outlet}.Config.is_insert set value 1b")
    wait_for_redstone_sample(server)
    server.assert_state(f"{name}_counter_waits_for_timer", f'{lamp(z, False)} if data block {counter_path}{{ticks:{starting_count}}}', results)
    start = game_tick(server)
    server.command(f"data modify block {inlet}.Config.is_extract set value 1b")
    server.assert_state(f"{name}_first_pulse_reaches_the_original_limit", f'{lamp(z, True)} '
                        f'if data block {counter_path}{{ticks:{starting_count + 1}}}', results)
    elapsed = game_tick(server) - start
    passed = expected_window[0] <= elapsed <= expected_window[1]
    results.append({"assertion": f"{name}_first_pulse_uses_saved_phase", "passed": passed, "elapsedTicks": elapsed,
                    "expectedRange": expected_window, "fullPeriod": period, "savedPhase": saved_phase})
    if not passed:
        raise AssertionError(f"Expected {name} first pulse in {expected_window} ticks, got {elapsed}")
    server.assert_state(f"{name}_next_pulse_wraps_the_counter", f'{lamp(z, False)} if data block {counter_path}{{ticks:1}}', results)


def recover_gate(server, results):
    gate = pos(4528, 4)
    tag = stock_tag("gate", "redstone_filter_double_channel", '{channel1:"red",channel2:"blue"}')
    stack = f'{{id:"enderio:{XOR}",Count:1b,tag:{tag}}}'
    server.command("scoreboard objectives add port_filter_loot dummy")
    server.command(f"setblock {gate} minecraft:air destroy")
    server.command(f'execute positioned {gate} store result score #all port_filter_loot '
                   f'if entity @e[type=minecraft:item,distance=..4,nbt={{Item:{{id:"enderio:{XOR}"}}}}]')
    server.assert_state("native_conduit_break_drops_one_exact_migrated_gate", f'if score #all port_filter_loot matches 1 '
                        f'positioned {gate} if entity @e[type=minecraft:item,distance=..4,nbt={{Item:{stack}}}]', results)


def reopened(server, results):
    load_routes(server, results, "restart")
    wait_for_redstone_sample(server)
    modern_snapshot(server, results, "restart")
    exercise_live_filters(server, results, "restart")
    latch_input = pos(4526, 8)
    latch = f'{connection(8, "east")}.Inventory[1].tag."enderio:data"."enderio:redstone_tlatch_filter"'
    set_lever(server, latch_input, False)
    server.assert_state("original_latch_rearms_on_release", f'{lamp(8, True)} if data block {latch}{{active:1b,deactivated:1b}}', results)
    set_lever(server, latch_input, True)
    server.assert_state("original_latch_switches_off_on_the_next_rising_edge", f'{lamp(8, False)} '
                        f'if data block {latch}{{deactivated:0b}} unless data block {latch}{{active:1b}}', results)
    resumed_timer(server, results, 12, "partial", 30, 80, 7, [44, 69])
    resumed_timer(server, results, 1, "due", 29, 30, 2, [0, 18])
    recover_gate(server, results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--forge-server", type=Path, required=True)
    parser.add_argument("--legacy-jar", type=Path, required=True)
    parser.add_argument("--fixture", type=Path, required=True)
    parser.add_argument("--jar", type=Path, required=True)
    args = parser.parse_args()
    root = ROOT / "build/port-runtime" / args.name
    root.mkdir()
    legacy, port = root / "legacy", root / "port"
    report = {"passed": False, "jarSha256": digest(args.jar), "legacyJarSha256": digest(args.legacy_jar),
              "assertions": [], "servers": []}
    try:
        prepare_profile(args, legacy, args.legacy_jar)
        shutil.copyfile(args.fixture, legacy / "mods/fixture.jar")
        run_stage(args, legacy, report, "legacy", prepare_legacy)
        prepare_profile(args, port, args.jar)
        shutil.copytree(legacy / WORLD, port / WORLD)
        run_stage(args, port, report, "converted", converted)
        run_stage(args, port, report, "reopened", reopened)
        report["passed"] = True
    finally:
        (root / "legacy-redstone-filter-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
