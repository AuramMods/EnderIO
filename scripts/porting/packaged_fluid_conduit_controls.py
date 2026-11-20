#!/usr/bin/env python3
"""Configure a stock mixed-tier fluid route through the packaged TCP menus."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_cc_tweaked import face, set_lever
from packaged_fluid_progression import CHUNKS, LANE, LEFT, RIGHT, supply, tank_state
from packaged_legacy_transfer_filters import wait_ticks
from packaged_server_smoke import ROOT, digest
from packaged_spawners import PLAYER, prepare_profile, run_stage


LEVER = "3280 80 7"
REDSTONE_INPUT = ('{type:"enderio:redstone",is_insert:0b,is_extract:1b,insert_channel:"green",'
                  'extract_channel:"blue",is_strong_output_signal:0b}')


def config(pos, side, *, insert=False, insert_channel="green", extract_channel="green", mode="never_active", redstone="red", priority=0):
    extra = f",insert_priority:{priority}" if priority else ""
    return (f'if data block {pos} Connections[0][{{Side:"{side}",Config:{{type:"enderio:fluid",'
            f'is_insert:{int(insert)}b,is_extract:{int(not insert)}b,insert_channel:"{insert_channel}",'
            f'extract_channel:"{extract_channel}",extract_redstone_channel:"{redstone}",'
            f'extract_redstone_control:"{mode}"{extra}}}}}]')


def inlet(mode, redstone="blue"):
    return config(LEFT, "west", extract_channel="blue", mode=mode, redstone=redstone)


def outlet(color="blue"):
    return config(RIGHT, "east", insert=True, insert_channel=color, priority=2)


def balance(source, target):
    return f"{tank_state(LANE.source, source)} {tank_state(LANE.target, target)}"


def stable(server, results, name, conditions):
    wait_ticks(server, 25)
    server.assert_state(name, conditions, results)


def prepare(server, results):
    for command in ("gamerule doMobSpawning false", "gamerule spawnRadius 0", "gamerule doDaylightCycle false", "time set noon",
                    f"forceload add {CHUNKS}", "fill 3276 79 6 3283 79 12 minecraft:stone_bricks", "setworldspawn 3279 80 10",
                    f"setblock {LANE.source} enderio:pressurized_fluid_tank", f"setblock {LANE.target} enderio:pressurized_fluid_tank"):
        server.command(command)
    set_lever(server, LEVER, False)
    for pos, variant in ((LEFT, "ender_fluid"), (RIGHT, "pressurized_fluid")):
        server.command(f'setblock {pos} enderio:conduit{{Conduits:["enderio:{variant}","enderio:redstone"],Connections:[[],[]]}}')
    server.assert_state("both_bundled_routes_cross_the_chunk_boundary", " ".join(
        f'if data block {pos} Connections[{index}][{{Side:"{side}",Status:"connected_conduit"}}]'
        for pos, side in ((LEFT, "east"), (RIGHT, "west")) for index in (0, 1)), results)
    server.command(f'data modify block {RIGHT} Connections[1][{{Side:"north"}}].Config set value {REDSTONE_INPUT}')
    server.assert_state("the_stock_ports_begin_at_default_and_the_lever_feeds_blue",
                        f'{face(LEFT, "west")} {face(RIGHT, "east")} '
                        f'unless data block {LEFT} Connections[0][{{Side:"west"}}].Config '
                        f'unless data block {RIGHT} Connections[0][{{Side:"east"}}].Config '
                        f'if data block {RIGHT} Connections[1][{{Side:"north",Status:"connected_block",Config:{REDSTONE_INPUT}}}]', results)


def action(server, client, output, label, x):
    server.command(f"tp {PLAYER} {x + 0.5} 80 10.5 180 17")
    (output / f"server-{label}").touch()
    wait_signal(client, output, label)


def exercise(server, results, client, output):
    action(server, client, output, "outlet-blue", 3280)
    server.assert_state("outlet_widgets_set_insertion_blue_and_priority_two", outlet(), results)

    action(server, client, output, "gate-red", 3279)
    supply(server, 1440)
    stable(server, results, "matching_fluid_channels_require_a_signal_on_the_selected_channel",
           f"{inlet('active_with_signal', 'red')} {outlet()} {balance(1440, 0)}")
    set_lever(server, LEVER, True)
    stable(server, results, "a_blue_signal_does_not_open_the_red_gate",
           f"{inlet('active_with_signal', 'red')} {balance(1440, 0)}")

    action(server, client, output, "gate-blue", 3279)
    server.assert_state("changing_the_gate_picker_to_blue_releases_the_full_batch",
                        f"{inlet('active_with_signal')} {outlet()} {balance(0, 1440)}", results)

    action(server, client, output, "outlet-green", 3280)
    supply(server, 1920)
    stable(server, results, "mismatched_fluid_channels_pause_even_while_blue_redstone_is_active",
           f"{inlet('active_with_signal')} {outlet('green')} {balance(1920, 1440)}")
    action(server, client, output, "outlet-matched", 3280)
    server.assert_state("restoring_both_blue_fluid_channels_releases_the_second_batch",
                        f"{outlet()} {balance(0, 3360)}", results)

    action(server, client, output, "gate-inverted", 3279)
    supply(server, 2100)
    stable(server, results, "inverted_control_stops_flow_while_the_lever_is_on",
           f"{inlet('active_without_signal')} {outlet()} {balance(2100, 3360)}")
    set_lever(server, LEVER, False)
    server.assert_state("inverted_control_delivers_on_removal_of_the_signal", balance(0, 5460), results)
    set_lever(server, LEVER, True)
    supply(server, 730)
    stable(server, results, "the_world_saves_a_powered_gate_with_fluid_waiting_at_source",
           f"{inlet('active_without_signal')} {outlet()} {balance(730, 5460)}")


def reopen(server, results, client, output):
    action(server, client, output, "saved-inlet", 3279)
    action(server, client, output, "saved-outlet", 3280)
    stable(server, results, "reopened_menus_and_powered_route_keep_the_original_settings_and_balances",
           f"{inlet('active_without_signal')} {outlet()} {balance(730, 5460)}")
    set_lever(server, LEVER, False)
    server.assert_state("the_reopened_line_delivers_the_saved_730_mb_once", balance(0, 6190), results)


def run_client(args, server, report, phase, scenario):
    output = ROOT / "build/port-evidence" / f"{args.name}-{phase}"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "fluid-conduit-controls", "--output", str(output), "--server", f"127.0.0.1:{args.port}",
               "--multiplayer-phase", phase, "--timeout", "195"]
    with (server.directory / f"client-{phase}.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            scenario(server, report["assertions"], client, output)
            (output / "server-done").touch()
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"Fluid conduit controls client failed; see {output}")
            summary = json.loads((output / "summary.json").read_text())
            frame = next(iter(summary["client"]["captures"].values()))
            if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Fluid controls client and server loaded different Ender IO jars")
        finally:
            stop_client(client)
            summary = output / "summary.json"
            report["clients"][phase] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for path in ("java", "forge-server", "jar", "launch"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "jarSha256": digest(directory / "mods/enderio.jar"), "assertions": [], "servers": [], "clients": {}}

    def first(server, results):
        prepare(server, results)
        run_client(args, server, report, "exercise", exercise)

    def restarted(server, results):
        server.command(f"forceload add {CHUNKS}")
        server.assert_state("the_offline_save_reopens_with_original_gate_settings_and_balances",
                            f"{inlet('active_without_signal')} {outlet()} {balance(730, 5460)} "
                            f"if block {LEVER} minecraft:lever[powered=true]", results)
        run_client(args, server, report, "verify", reopen)

    try:
        run_stage(args, directory, report, "exercise", first)
        run_stage(args, directory, report, "reopened", restarted)
        report["passed"] = True
    finally:
        (directory / "fluid-conduit-controls-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
