#!/usr/bin/env python3
"""Exercise native redstone filter slots, strong receivers and a cross-chunk logic fixture."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_cc_tweaked import set_lever
from packaged_redstone_filter_menus import PLAYER, item_data, wait_for_redstone_sample
from packaged_redstone_filters import configure, endpoint, lamp, pos, prepare
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage


STRONG_INPUT = pos(3310, 8)
DISPENSER = pos(3313, 8)
STRONG_LAMP = pos(3314, 8)
OUTPUT_SLOT = endpoint(3312, 8, "east") + ".Inventory[1]"
INVERTER = '{id:"enderio:redstone_not_filter",tag:{PortFilter:"inverter"}}'
GATE_STATE = item_data("redstone_filter_double_channel", '{channel1:"red",channel2:"blue"}')
TAGGED_GATE = GATE_STATE[:-1] + ',PortFilter:"gate"}'
GATE = f'{{id:"enderio:redstone_and_filter",tag:{TAGGED_GATE}}}'

# Columns are no input, red only, blue only, and both.
TRUTH_TABLES = {
    "and": (False, False, False, True),
    "nand": (True, True, True, False),
    "or": (False, True, True, True),
    "nor": (True, False, False, False),
    "xor": (False, True, True, False),
    "xnor": (True, False, False, True),
}


def strong_lamp(lit):
    return f"if block {STRONG_LAMP} minecraft:redstone_lamp[lit={str(lit).lower()}]"


def setup(server, results):
    prepare(server, results)
    server.command("gamerule spawnRadius 0")
    server.command("setworldspawn 3312 80 10")
    server.command(f"setblock {STRONG_LAMP} minecraft:redstone_lamp")
    server.command(f"setblock {DISPENSER} minecraft:dispenser[facing=north]")
    configure(server, 8, insert=False)
    configure(server, 8, insert=True, filter_id="redstone_not_filter", tag='{PortFilter:"inverter"}')
    server.command(f"data modify block {endpoint(3312, 8, 'east')}.Config.is_strong_output_signal set value 1b")
    set_lever(server, STRONG_INPUT, True)
    wait_for_redstone_sample(server)
    server.assert_state("strong_inverter_passes_zero_when_the_source_is_powered", strong_lamp(False), results)
    set_lever(server, STRONG_INPUT, False)
    server.assert_state("inverter_powers_lamp_through_a_real_dispenser", strong_lamp(True), results)


def ready(args, server, results, report):
    output = ROOT / "build/port-evidence" / args.name
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "conduit-filters", "--multiplayer-phase", "verify" if args.baseline else "exercise",
               "--output", str(output), "--server", f"127.0.0.1:{args.port}", "--timeout", "195"]
    with (server.directory / "client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            server.command(f"item replace entity {PLAYER} hotbar.0 with enderio:redstone_and_filter{TAGGED_GATE}")
            server.command(f"item replace entity {PLAYER} hotbar.2 with minecraft:stick")
            server.command(f"tp {PLAYER} 3312.5 80 10.5 180 15")
            (output / "server-ready").touch()
            wait_signal(client, output, "inverter-picked-up")
            wait_for_redstone_sample(server)
            pickup_result = ("previous_filter_pickup_leaves_the_strong_lamp_lit" if args.baseline
                             else "native_filter_pickup_clears_the_strong_receiver_with_stable_input")
            server.assert_state(pickup_result,
                                f'unless data block {OUTPUT_SLOT}.id {strong_lamp(args.baseline)} '
                                f'if block {STRONG_INPUT} minecraft:lever[powered=false]', results)
            (output / "server-inverter-removed").touch()
            if not args.baseline:
                wait_signal(client, output, "gate-shifted-in")
                server.assert_state("native_shift_click_installs_only_the_compatible_filter",
                                    f'if data block {OUTPUT_SLOT}.id if data block {OUTPUT_SLOT}.tag{TAGGED_GATE} '
                                    f'unless data block {endpoint(3312, 8, "east")}.Inventory[0].id '
                                    f'unless data entity {PLAYER} Inventory[{{id:"enderio:redstone_and_filter"}}] '
                                    f'if data entity {PLAYER} Inventory[{{Slot:2b,id:"minecraft:stick",Count:1b}}]', results)
                (output / "server-gate-installed").touch()
                wait_signal(client, output, "gate-shifted-out")
                server.assert_state("native_shift_click_recovers_the_tagged_gate", f'unless data block {OUTPUT_SLOT}.id '
                                    f'if data entity {PLAYER} Inventory[{GATE}]', results)
                (output / "server-gate-recovered").touch()
                wait_signal(client, output, "inverter-restored")
                server.assert_state("native_filter_reinstall_powers_the_unchanged_strong_route",
                                    f'if data block {OUTPUT_SLOT}.tag{{PortFilter:"inverter"}} {strong_lamp(True)} '
                                    f'unless data entity {PLAYER} Inventory[{{id:"enderio:redstone_not_filter"}}]', results)
                (output / "server-inverter-lit").touch()
            if client.wait(timeout=45) != 0:
                raise AssertionError(f"Conduit filter client failed, see {output}")
        finally:
            stop_client(client)
            if (output / "summary.json").exists():
                report["client"] = json.loads((output / "summary.json").read_text())
    frame = report["client"]["client"]["captures"]["redstone-conduit-filters"]
    if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
        raise AssertionError("The client and server used different Ender IO jars")


def exercise_logic(server, results):
    red, blue = pos(3310, 12), pos(3311, 11)
    set_lever(server, blue, False)
    configure(server, 12, insert=False)
    configure(server, 12, insert=True)
    red_side = endpoint(3311, 12, "west")
    blue_side = endpoint(3311, 12, "north")
    server.command(f'data modify block {red_side}.Config.extract_channel set value "red"')
    server.command(f"data modify block {blue_side}.Config set from block {red_side}.Config")
    server.command(f'data modify block {blue_side}.Config.extract_channel set value "blue"')
    for gate, expected in TRUTH_TABLES.items():
        configure(server, 12, insert=True, filter_id=f"redstone_{gate}_filter", tag=GATE_STATE)
        for (left, right), lit in zip(((False, False), (True, False), (False, True), (True, True)), expected, strict=True):
            set_lever(server, red, left)
            set_lever(server, blue, right)
            wait_for_redstone_sample(server)
            server.assert_state(f"{gate}_red_{int(left)}_blue_{int(right)}", lamp(12, lit), results)

    set_lever(server, blue, False)
    server.command(f'data remove block {endpoint(3312, 12, "east")}.Inventory')
    configure(server, 12, insert=False, filter_id="redstone_not_filter")
    set_lever(server, red, False)
    server.assert_state("input_inverter_drives_lamp_when_source_is_low", lamp(12, True), results)
    set_lever(server, red, True)
    server.assert_state("input_inverter_clears_lamp_when_source_is_high", lamp(12, False), results)

    server.command(f"setblock {red} minecraft:dropper")
    server.command(f"setblock {pos(3313, 12)} minecraft:redstone_wire")
    configure(server, 12, insert=True)
    configure(server, 12, insert=False, filter_id="redstone_sensor_filter")
    for slots, count, power in ((0, 0, 0), (1, 1, 1), (9, 64, 15), (5, 64, 8)):
        items = ",".join(f'{{Slot:{i}b,id:"minecraft:cobblestone",Count:{count}b}}' for i in range(slots))
        server.command(f"data merge block {red} {{Items:[{items}]}}")
        server.assert_state(f"sensor_carries_analog_power_{power}", wire(power), results)


def wire(power):
    return f"if block {pos(3313, 12)} minecraft:redstone_wire[power={power}]"


def reopen(server, results):
    server.assert_state("reopened_routes_keep_strong_inversion_and_analog_power", f"{strong_lamp(True)} {wire(8)}", results)
    set_lever(server, STRONG_INPUT, True)
    server.command(f"data merge block {pos(3310, 12)} {{Items:[]}}")
    server.assert_state("reopened_routes_follow_new_sources", f"{strong_lamp(False)} {wire(0)}", results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--forge-server", type=Path, required=True)
    parser.add_argument("--launch", type=Path, required=True)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--baseline", action="store_true")
    args = parser.parse_args()
    root = ROOT / "build/port-runtime" / args.name
    root.mkdir()
    profile = root / "server"
    report = {"passed": False, "jarSha256": digest(args.jar), "assertions": [], "servers": []}

    def exercise(server, results):
        setup(server, results)
        ready(args, server, results, report)
        if not args.baseline:
            exercise_logic(server, results)

    try:
        prepare_profile(args, profile, args.jar)
        run_stage(args, profile, report, "exercise", exercise)
        if not args.baseline:
            run_stage(args, profile, report, "reopened", reopen)
        report["passed"] = True
    finally:
        (root / "conduit-filter-slot-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
