#!/usr/bin/env python3
"""Craft a bundled redstone line and erase its live configured count filter."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_cc_tweaked import face, set_lever
from packaged_energy_progression import conduit_stack
from packaged_legacy_transfer_filters import wait_ticks
from packaged_redstone_filter_menus import item_data
from packaged_redstone_filters import configure, endpoint, lamp, pos
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage
from packaged_starter_progression import PLAYER


LANE = 8
LEFT, RIGHT = pos(3311, LANE), pos(3312, LANE)
LEVER = pos(3310, LANE)
OUTLET = endpoint(3312, LANE, "east")
COUNTER = "enderio:redstone_counting_filter"
KEY = "redstone_count_filter"
INSERT_SLOT = f"{OUTLET}.Inventory[1]"
MATERIALS = (("enderio:conduit_binder", 7), ("enderio:redstone_alloy_ingot", 12),
             ("minecraft:paper", 5), ("minecraft:iron_ingot", 2))


def component(channel, threshold, pulses, released):
    return f'{{channel1:"{channel}",maxCount:{threshold},ticks:{pulses},deactivated:{int(released)}b}}'


def carried_counter(channel, threshold, pulses, released):
    data = item_data(KEY, component(channel, threshold, pulses, released))
    return f'if data entity {PLAYER} Inventory[{{id:"{COUNTER}",Count:1b,tag:{data}}}]'


def installed_counter(channel, threshold, pulses, released):
    state = component(channel, threshold, pulses, released)
    return (f'if data block {OUTLET}.Inventory[{{id:"{COUNTER}",Count:1}}] '
            f'if data block {INSERT_SLOT}.tag."enderio:data"."enderio:{KEY}"{state}')


def prepare(server, results):
    for command in ("gamerule doMobSpawning false", "gamerule spawnRadius 0", "gamerule doDaylightCycle false", "time set noon",
                    "forceload add 3296 0 3327 15", "fill 3309 79 6 3319 79 13 minecraft:stone_bricks",
                    "setworldspawn 3316 80 11", "setblock 3316 80 8 minecraft:crafting_table",
                    "setblock 3313 80 8 minecraft:redstone_lamp"):
        server.command(command)
    set_lever(server, LEVER, False)
    server.assert_state("both_redstone_positions_start_empty_at_the_chunk_edge",
                        f"if loaded {LEFT} if loaded {RIGHT} if block {LEFT} minecraft:air if block {RIGHT} minecraft:air", results)


def action(client, output, name, response):
    (output / f"server-{name}").touch()
    wait_signal(client, output, response)


def take_to_table(server):
    server.command(f"tp {PLAYER} 3316.5 80 10.5 180 17")


def take_to_line(server):
    server.command(f"tp {PLAYER} 3312.5 80 10.5 180 17")


def input_channel(server, color):
    server.command(f'data modify block {endpoint(3311, LANE, "west")}.Config.extract_channel set value "{color}"')


def pulse(server, results, channel, threshold, number):
    set_lever(server, LEVER, False)
    server.assert_state(f"{channel}_rearms_before_pulse_{number}", installed_counter(channel, threshold, number - 1, True), results)
    set_lever(server, LEVER, True)
    server.assert_state(f"{channel}_pulse_{number}_reaches_the_exact_counter_and_lamp_state",
                        f"{installed_counter(channel, threshold, number, False)} {lamp(LANE, number == threshold)}", results)


def craft_and_test(server, results, client, output):
    for slot, (item, supplied) in enumerate(MATERIALS, 1):
        server.command(f"item replace entity {PLAYER} hotbar.{slot} with {item} {supplied}")
    take_to_table(server)
    action(client, output, "table", "crafted")
    spares = ",".join(f'{{id:"{item}",Count:1b}}' for item, _ in MATERIALS)
    server.assert_state("stock_recipes_make_eight_conduits_and_consume_the_actual_crafted_filter_base",
                        f'if data entity {PLAYER} {{Inventory:[{conduit_stack("redstone", 8)},{spares},{{id:"{COUNTER}",Count:1b}}]}} '
                        f'unless data entity {PLAYER} Inventory[{{id:"enderio:redstone_filter_base"}}]', results)
    take_to_line(server)
    action(client, output, "place", "placed")
    server.assert_state("two_crafted_conduits_connect_the_lever_to_the_lamp_across_chunks", " ".join((
        face(LEFT, "west"), face(LEFT, "east", "connected_conduit"),
        face(RIGHT, "west", "connected_conduit"), face(RIGHT, "east"))), results)
    configure(server, LANE, insert=False)
    configure(server, LANE, insert=True)
    input_channel(server, "blue")
    action(client, output, "edit", "edited")
    server.assert_state("native_handheld_menu_configures_blue_and_a_threshold_of_two",
                        carried_counter("blue", 2, 0, False), results)
    action(client, output, "edited", "edit-closed")
    action(client, output, "install", "install-done")
    server.assert_state("the_crafted_counter_is_installed_with_its_menu_configuration",
                        f'{installed_counter("blue", 2, 0, True)} unless data entity {PLAYER} Inventory[{{id:"{COUNTER}"}}]', results)
    for number in (1, 2):
        pulse(server, results, "blue", 2, number)

    action(client, output, "recover", "recover-done")
    server.assert_state("native_removal_returns_the_live_configured_counter_to_inventory",
                        f'{carried_counter("blue", 2, 2, False)} unless data block {INSERT_SLOT}.id', results)
    take_to_table(server)
    action(client, output, "erase", "erased")
    server.assert_state("the_stock_erasure_returns_one_clean_counter_and_keeps_six_unused_conduits",
                        f'if data entity {PLAYER} Inventory[{{id:"{COUNTER}",Count:1b}}] '
                        f'unless data entity {PLAYER} Inventory[{{id:"{COUNTER}"}}].tag '
                        f'if data entity {PLAYER} Inventory[{conduit_stack("redstone", 6)}]', results)
    take_to_line(server)
    action(client, output, "reinstall", "reinstall-done")
    set_lever(server, LEVER, False)
    wait_ticks(server, 6)
    set_lever(server, LEVER, True)
    wait_ticks(server, 10)
    server.assert_state("the_erased_counter_ignores_its_old_blue_channel",
                        f"{installed_counter('green', 8, 0, True)} {lamp(LANE, False)}", results)
    set_lever(server, LEVER, False)
    input_channel(server, "green")
    for number in range(1, 9):
        pulse(server, results, "green", 8, number)


def run_client(args, server, report):
    output = ROOT / "build/port-evidence" / f"{args.name}-client"
    summary = output / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "redstone-progression", "--server", f"127.0.0.1:{args.port}", "--output", str(output), "--timeout", "195"]
    with (server.directory / "client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            craft_and_test(server, report["assertions"], client, output)
            (output / "server-done").touch()
            if client.wait(timeout=25) != 0:
                raise AssertionError(f"Redstone progression client failed; see {output}")
            frame = json.loads(summary.read_text())["client"]["captures"]["redstone_counting_filter_erasure"]
            if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Redstone progression client and server loaded different Ender IO jars")
        finally:
            stop_client(client)
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def reopen(server, results, *, verify_edge):
    held = f'if block {LEVER} minecraft:lever[powered=true] {installed_counter("green", 8, 8, False)} {lamp(LANE, True)}'
    server.assert_state("cold_start_restores_the_eighth_count_with_its_input_held_high", held, results)
    wait_ticks(server, 20)
    server.assert_state("the_saved_high_input_does_not_count_again", held, results)
    if verify_edge:
        set_lever(server, LEVER, False)
        server.assert_state("reopened_counter_rearms_on_an_actual_falling_edge", installed_counter("green", 8, 8, True), results)
        set_lever(server, LEVER, True)
        server.assert_state("the_next_green_edge_wraps_the_saved_count_to_one_and_clears_the_lamp",
                            f"{installed_counter('green', 8, 1, False)} {lamp(LANE, False)}", results)


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
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}

    def exercise(server, results):
        prepare(server, results)
        run_client(args, server, report)

    try:
        run_stage(args, directory, report, "exercise", exercise)
        run_stage(args, directory, report, "reopened-held", lambda server, results: reopen(server, results, verify_edge=False))
        run_stage(args, directory, report, "reopened-edge", lambda server, results: reopen(server, results, verify_edge=True))
        report["passed"] = True
    finally:
        (directory / "redstone-progression-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
