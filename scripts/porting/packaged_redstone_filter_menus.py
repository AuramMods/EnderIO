#!/usr/bin/env python3
"""Drive the native handheld filter screens and use their saved stacks in a Forge circuit."""

import argparse
import json
from pathlib import Path
import subprocess
import sys
import time

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_cc_tweaked import set_lever
from packaged_levitation_staff import game_tick
from packaged_redstone_filters import configure, endpoint, lamp, pos, prepare
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage


PLAYER = "EnderIOQA"
BARREL = pos(3315, 8)
TIMER = "redstone_timer_filter"
COUNTER = "redstone_counting_filter"
GATE = "redstone_and_filter"


def item_data(key, value):
    return f'{{"enderio:data":{{"enderio:{key}":{value}}}}}'


def held(item, slot, tag):
    return f'if data entity {PLAYER} Inventory[{{Slot:{slot}b,id:"enderio:{item}",tag:{tag}}}]'


def fill_hand(server, hand, item, tag=""):
    server.command(f"item replace entity {PLAYER} weapon.{hand} with enderio:{item}{tag}")


def confirm_menu(server, results, client, output, name, hand, slot, conditions):
    (output / f"ready-{name}").touch()
    wait_signal(client, output, f"edited-{name}")
    server.assert_state(f"{name}_screen_updates_the_expected_server_stack", conditions, results)
    server.command(f"item replace block {BARREL} container.{slot} from entity {PLAYER} weapon.{hand}")
    (output / f"confirmed-{name}").touch()
    wait_signal(client, output, f"closed-{name}")


def play_menus(args, server, results, report):
    output = ROOT / "build/port-evidence" / args.name
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "redstone-filters", "--output", str(output), "--server", f"127.0.0.1:{args.port}", "--timeout", "195"]
    with (server.directory / "client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            fill_hand(server, "mainhand", TIMER)
            confirm_menu(server, results, client, output, "timer", "mainhand", 0,
                         held(TIMER, 0, item_data(TIMER, "{ticks:0,maxTicks:40}")))

            count_key = "redstone_count_filter"
            control = item_data(count_key, '{channel1:"blue",maxCount:11,ticks:3,deactivated:1b}')
            offhand = item_data(count_key, '{channel1:"green",maxCount:8,ticks:1,deactivated:1b}')
            fill_hand(server, "mainhand", COUNTER, control)
            fill_hand(server, "offhand", COUNTER, offhand)
            if args.baseline:
                (output / "ready-counter").touch()
                if client.wait(timeout=45) == 0:
                    raise AssertionError("The previous offhand screen unexpectedly passed")
                return
            updated = item_data(count_key, '{channel1:"white",maxCount:2,ticks:1,deactivated:1b}')
            confirm_menu(server, results, client, output, "counter", "offhand", 1,
                         f"{held(COUNTER, -106, updated)} {held(COUNTER, 0, control)}")

            fill_hand(server, "mainhand", GATE)
            confirm_menu(server, results, client, output, "and-gate", "mainhand", 2,
                         held(GATE, 0, item_data("redstone_filter_double_channel", '{channel1:"red",channel2:"blue"}')))
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"Redstone filter client failed, see {output}")
        finally:
            stop_client(client)
            if (output / "summary.json").exists():
                report["client"] = json.loads((output / "summary.json").read_text())
    for frame in report["client"]["client"]["captures"].values():
        if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
            raise AssertionError("The client and server used different Ender IO jars")


def install(server, lane, slot, *, insert):
    x, side = (3312, "east") if insert else (3311, "west")
    path = endpoint(x, lane, side)
    output = server.command(f"data get block {BARREL} Items[{{Slot:{slot}b}}]")
    marker = " has the following block data: "
    stack = next((line.split(marker, 1)[1] for line in output.splitlines() if marker in line), None)
    if stack is None:
        raise AssertionError(f"The archived filter is missing from barrel slot {slot}; see {server.log_path}")
    # The bundle omits empty inventories entirely when saving between commands.
    inventory = f"[{{}},{stack}]" if insert else f"[{stack}]"
    server.command(f"data modify block {path}.Inventory set value {inventory}")


def check_counter(server, results):
    path = endpoint(3312, 8, "east")
    filter_state = f'{path}.Inventory[1].tag."enderio:data"."enderio:redstone_count_filter"'
    server.assert_state("timer_pulse_lights_lamp_at_configured_count", f'{lamp(8, True)} if data block {filter_state}{{ticks:2}}', results)
    server.assert_state("next_timer_pulse_wraps_count_and_clears_lamp", f'{lamp(8, False)} if data block {filter_state}{{ticks:1}}', results)


def wait_for_redstone_sample(server):
    end_tick = game_tick(server) + 6
    deadline = time.monotonic() + 8
    while game_tick(server) < end_tick:
        if time.monotonic() > deadline:
            raise AssertionError("The server did not tick the redstone fixture")


def exercise(args, server, results, report):
    prepare(server, results)
    server.command("gamerule spawnRadius 0")
    server.command("fill 3314 79 6 3319 79 10 minecraft:stone")
    server.command("setworldspawn 3317 80 8")
    server.command(f"setblock {BARREL} minecraft:barrel")
    play_menus(args, server, results, report)
    if args.baseline:
        if report["client"]["client"]["checks"].get("offhand_counter_menu_reads_the_counter_that_opened_it") is not False:
            raise AssertionError("The baseline failed before it displayed the wrong counter")
        return

    configure(server, 8, insert=True)
    install(server, 8, 1, insert=True)
    configure(server, 8, insert=False)
    install(server, 8, 0, insert=False)
    configured_timer = f'{endpoint(3311, 8, "west")}.Inventory[0].tag."enderio:data"."enderio:redstone_timer_filter"'
    server.assert_state("installed_timer_has_the_period_from_its_screen", f"if data block {configured_timer}{{maxTicks:40}}", results)
    check_counter(server, results)

    north = pos(3311, 11)
    west = pos(3310, 12)
    set_lever(server, north, False)
    configure(server, 12, insert=True)
    configure(server, 12, insert=False)
    inputs = endpoint(3311, 12, "west")
    north_input = endpoint(3311, 12, "north")
    server.command(f'data modify block {inputs}.Config.extract_channel set value "red"')
    server.command(f"data modify block {north_input}.Config set from block {inputs}.Config")
    server.command(f'data modify block {north_input}.Config.extract_channel set value "blue"')
    install(server, 12, 2, insert=True)
    wait_for_redstone_sample(server)
    server.assert_state("configured_and_gate_is_dark_with_both_inputs_low", lamp(12, False), results)
    set_lever(server, west, True)
    wait_for_redstone_sample(server)
    server.assert_state("configured_and_gate_rejects_red_alone", lamp(12, False), results)
    set_lever(server, north, True)
    server.assert_state("configured_and_gate_lights_with_red_and_blue", lamp(12, True), results)
    set_lever(server, west, False)
    server.assert_state("configured_and_gate_rejects_blue_alone", lamp(12, False), results)
    set_lever(server, west, True)
    server.assert_state("both_configured_channels_are_active_before_restart", lamp(12, True), results)


def reopened(server, results):
    server.assert_state("installed_and_gate_keeps_both_channels_after_restart", lamp(12, True), results)
    set_lever(server, pos(3311, 11), False)
    server.assert_state("reopened_gate_responds_to_blue_removal", lamp(12, False), results)
    check_counter(server, results)


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
    try:
        prepare_profile(args, profile, args.jar)
        run_stage(args, profile, report, "exercise", lambda server, results: exercise(args, server, results, report))
        if not args.baseline:
            run_stage(args, profile, report, "reopened", reopened)
        report["passed"] = True
    finally:
        (root / "redstone-filter-menu-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
