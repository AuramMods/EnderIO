#!/usr/bin/env python3
"""Verify a carried Farming Station and its controls over guarded Forge TCP multiplayer."""

import argparse
import json
from pathlib import Path
import re
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_spawners import prepare_profile, run_stage


PLAYER = "EnderIOQA"
BLOCK = "enderio:farming_station"
SOURCE = "2407 80 8"
DESTINATION = "2407 80 13"
INSIDE = "2409 80 13"
OUTSIDE = "2407 80 10"
SWITCH = "2406 80 13"
CHARGE = 6177
SOUL = '{entity_type:"minecraft:bee",entity_tag:{PortFixture:81}}'
IO = '{Modes:{east:"disable",south:"pull"}}'


def item(slot, name, count, damage=None):
    tag = f",tag:{{Damage:{damage},PortFixture:81}}" if damage is not None else ""
    return f'{{Slot:{slot},id:"{name}",Count:{count}{tag}}}'


INPUTS = [item(0, "enderio:basic_capacitor", 1), item(1, "minecraft:diamond_axe", 1, 5),
          item(2, "minecraft:iron_hoe", 1, 3), item(3, "minecraft:shears", 1, 2),
          item(4, "minecraft:wheat_seeds", 1), item(5, "minecraft:carrot", 1), item(6, "minecraft:potato", 1),
          item(7, "minecraft:beetroot_seeds", 1), item(8, "minecraft:bone_meal", 6), item(9, "minecraft:bone_meal", 2),
          item(15, "minecraft:apple", 2)]
WHEAT = item(10, "minecraft:wheat", 7)
PUMPKIN = item(11, "minecraft:pumpkin", 1)


def inventory(outputs):
    return f'{{Size:16,Items:[{",".join([*INPUTS, *outputs])}]}}'


def block_data(outputs, charge=None, *, visible=False, redstone="active_with_signal"):
    energy = f"EnergyStored:{charge}," if charge is not None else ""
    return (f'{{{energy}EntityStorage:{SOUL},ActionRange:{{range:2,isVisible:{int(visible)}b}},'
            f'IOConfig:{IO},RedstoneControl:"{redstone}",Items:{inventory(outputs)}}}')


def carried_stack():
    return (f'{{id:"{BLOCK}",Count:1b,tag:{{"enderio:data":{{"enderio:energy":{CHARGE},"enderio:soul":{SOUL},'
            f'"enderio:action_range":{{range:2,isVisible:0b}},"enderio:io_config":{IO},'
            f'"enderio:redstone_control":"active_with_signal","enderio:container":{inventory([WHEAT])}}}}}}}')


def setup(server, results):
    server.command("forceload add 2400 0 2431 31")
    server.assert_state("farming_station_and_plot_chunks_are_loaded", f"if loaded {SOURCE} if loaded {DESTINATION}", results)
    server.command("gamerule doMobSpawning false")
    server.command("gamerule randomTickSpeed 0")
    server.command("gamerule doDaylightCycle false")
    server.command("time set noon")
    server.command("gamerule spawnRadius 0")
    server.command("fill 2399 79 5 2415 79 20 minecraft:stone")
    server.command("setworldspawn 2407 80 11")
    server.command(f"setblock {SOURCE} {BLOCK}[facing=south]")
    server.command(f"data merge block {SOURCE} {block_data([WHEAT], CHARGE)}")
    server.assert_state("source_has_the_bound_soul_charge_tools_and_controls",
                        f"if data block {SOURCE} {block_data([WHEAT], CHARGE)}", results)


def transfer_station(server, client, output, results):
    server.command(f'item replace entity {PLAYER} hotbar.0 with minecraft:diamond_pickaxe'
                   '{Enchantments:[{id:"minecraft:efficiency",lvl:5s}]}')
    server.command(f"tp {PLAYER} 2407.5 80 11.5 180 22")
    (output / "server-mine").touch()
    wait_signal(client, output, "mined")
    near = '@e[type=minecraft:item,x=2407.5,y=80,z=8.5,distance=..3]'
    drop = near[:-1] + f',nbt={{Item:{{id:"{BLOCK}"}}}},sort=nearest,limit=1]'
    server.command(f"data get entity {drop} Item")
    server.assert_state("survival_mining_drops_the_complete_station",
                        f"if block {SOURCE} minecraft:air if data entity {drop} {{Item:{carried_stack()}}}", results)
    server.command(f"execute at {drop} run tp {PLAYER} ~ ~ ~")
    server.assert_state("ordinary_pickup_leaves_no_loose_items_and_carries_all_state",
                        f"unless entity {near} if data entity {PLAYER} Inventory[{carried_stack()}]", results)
    server.command(f"tp {PLAYER} 2407.5 80 15.5 180 22")
    (output / "server-place").touch()
    wait_signal(client, output, "placed")
    server.command(f"data get block {DESTINATION}")
    server.assert_state("survival_placement_consumes_one_item_and_restores_exact_state",
                        f'if block {DESTINATION} {BLOCK} if data block {DESTINATION} {block_data([WHEAT], CHARGE)} '
                        f'unless data entity {PLAYER} Inventory[{{id:"{BLOCK}"}}]', results)
    (output / "server-placed").touch()


def exercise_range_and_controls(server, client, output, report):
    results = report["assertions"]
    wait_signal(client, output, "menu-initial")
    for plot in (INSIDE, OUTSIDE):
        server.command(f"setblock {plot} minecraft:pumpkin")
    # Supply a neighbor signal without rewriting the station's block data or range.
    server.command(f"setblock {SWITCH} minecraft:redstone_block")
    server.assert_state("replanted_station_harvests_the_inner_plot_and_leaves_the_outer_one",
                        f"if block {INSIDE} minecraft:air if block {OUTSIDE} minecraft:pumpkin "
                        f"if data block {DESTINATION} {block_data([WHEAT, PUMPKIN])}", results, timeout=40)
    server.command(f"setblock {SWITCH} minecraft:air")
    (output / "server-harvested").touch()
    wait_signal(client, output, "settings")
    server.assert_state("menu_clicks_update_range_redstone_and_transfer_exactly_seven_wheat",
                        f'if data block {DESTINATION} {block_data([PUMPKIN], visible=True, redstone="never_active")} '
                        f'unless data block {DESTINATION} Items.Items[{{Slot:10}}] '
                        f'if data entity {PLAYER} Inventory[{{id:"minecraft:wheat",Count:7b}}]', results)
    reply = server.command(f"data get block {DESTINATION} EnergyStored")
    match = re.search(r"following block data: (\d+)", reply)
    if not match or not 0 < int(match.group(1)) < CHARGE:
        raise AssertionError(f"Expected a partially spent energy buffer; see {server.log_path}")
    report["remainingEnergy"] = int(match.group(1))
    (output / "server-controls-verified").touch()
    wait_signal(client, output, "reopened-menu")
    server.command(f"tp {PLAYER} 2407.5 80 18.5 180 14")
    (output / "server-range-camera").touch()


def multiplayer(args, server, report):
    setup(server, report["assertions"])
    output = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "farming-station", "--output", str(output), "--server", f"127.0.0.1:{args.port}", "--timeout", "195"]
    with (server.directory / "farming-station-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            transfer_station(server, client, output, report["assertions"])
            exercise_range_and_controls(server, client, output, report)
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"Farming Station client failed; see {output}")
            summary = json.loads((output / "summary.json").read_text())
            frame = summary["client"]["captures"]["replanted-farming-station"]
            if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Client and server loaded different Ender IO jars")
        finally:
            stop_client(client)
            summary = output / "summary.json"
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def reopened(server, results, energy):
    server.assert_state("replanted_station_soul_slots_radius_and_controls_survive_restart",
                        f"if block {SOURCE} minecraft:air if block {DESTINATION} {BLOCK} "
                        f'if data block {DESTINATION} {block_data([PUMPKIN], energy, visible=True, redstone="never_active")} '
                        f"unless data block {DESTINATION} Items.Items[{{Slot:10}}] "
                        f"if block {INSIDE} minecraft:air if block {OUTSIDE} minecraft:pumpkin", results, timeout=15)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--launch", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "forge": FORGE, "jarSha256": digest(directory / "mods/enderio.jar"),
              "assertions": [], "servers": []}
    try:
        run_stage(args, directory, report, "fresh", lambda server, _: multiplayer(args, server, report))
        run_stage(args, directory, report, "reopened", lambda server, results: reopened(server, results, report["remainingEnergy"]))
        report["passed"] = True
    finally:
        path = directory / "farming-station-carry-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Farming Station carry passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
