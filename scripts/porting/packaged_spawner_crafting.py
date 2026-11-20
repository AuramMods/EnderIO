#!/usr/bin/env python3
"""Craft stock Powered Spawners from native drops and erase the second soul."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_server_smoke import ROOT, digest
from packaged_spawners import PLAYER, SPAWNER, WORLD, prepare_profile, run_stage
from packaged_solar_charging import check
from saved_world import read_data_file


TABLE = "4140 80 8"
PLACED = "4141 80 8"
BARREL = "4144 80 8"
VANILLA_SPAWNER = "4145 80 8"
BROKEN = "enderio:broken_spawner"
INGOTS, CHASSIS = "enderio:soularium_ingot", "enderio:ensouled_chassis"
CRYSTAL, CONTROLLER = "enderio:vibrant_crystal", "enderio:z_logic_controller"
INGREDIENTS = (INGOTS, BROKEN, INGOTS, INGOTS, CHASSIS, INGOTS, CRYSTAL, CONTROLLER, CRYSTAL)
DATA, SOUL = '"enderio:data"', '"enderio:soul"'


def stored_soul(mob):
    return f'tag:{{{DATA}:{{{SOUL}:{{entity_type:"minecraft:{mob}"}}}}}}'


def inventory(item, mob=None):
    soul = f",{stored_soul(mob)}" if mob else ""
    return f'Inventory[{{id:"{item}",Count:1b{soul}}}]'


def placed_cow():
    return f'if block {PLACED} {SPAWNER} if data block {PLACED} {{EntityStorage:{{entity_type:"minecraft:cow"}},EnergyStored:0}}'


def no_ingredients():
    return " ".join(f'unless data entity {PLAYER} Inventory[{{id:"{item}"}}]' for item in dict.fromkeys(INGREDIENTS))


def setup(server, results):
    server.command("forceload add 4128 0 4159 15")
    server.assert_state("the_stock_workshop_chunks_load", f"if loaded {TABLE} if loaded {VANILLA_SPAWNER}", results)
    for command in ("gamerule doMobSpawning false", "gamerule doLimitedCrafting true", "gamerule spawnRadius 0",
                    "fill 4137 79 6 4148 79 14 minecraft:stone", "setworldspawn 4140 80 12",
                    f"setblock {TABLE} minecraft:crafting_table", f"setblock {BARREL} minecraft:barrel"):
        server.command(command)
    for slot, mob in enumerate(("cow", "pig")):
        server.command(f'setblock {VANILLA_SPAWNER} minecraft:spawner{{Delay:32767s,SpawnData:{{entity:{{id:"minecraft:{mob}"}}}}}}')
        server.command(f"loot replace block {BARREL} container.{slot} mine {VANILLA_SPAWNER} minecraft:diamond_pickaxe")
        server.command(f"setblock {VANILLA_SPAWNER} minecraft:air")
        server.assert_state(f"vanilla_spawner_loot_returns_one_original_{mob}_soul",
                            f'if data block {BARREL} Items[{{Slot:{slot}b,id:"{BROKEN}",Count:1b,{stored_soul(mob)}}}]', results)


def supply(server, loot_slot=None):
    for slot, ingredient in enumerate(INGREDIENTS):
        if slot == 1 and loot_slot is not None:
            take_drop(server, loot_slot)
        else:
            server.command(f"item replace entity {PLAYER} hotbar.{slot} with {ingredient}")


def take_drop(server, slot):
    server.command(f"item replace entity {PLAYER} hotbar.1 from block {BARREL} container.{slot}")
    server.command(f"item replace block {BARREL} container.{slot} with minecraft:air")


def exercise(args, server, results, report):
    evidence = ROOT / "build/port-evidence" / (args.name + "-client")
    summary = evidence / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "spawner-crafting", "--server", f"127.0.0.1:{args.port}", "--output", str(evidence), "--timeout", "195"]
    with (server.directory / "spawner-crafting-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            supply(server)
            server.command(f"tp {PLAYER} 4140.5 80 11.5 180 16")
            (evidence / "server-workshop").touch()
            wait_signal(client, evidence, "rejected-unbound")
            server.assert_state("the_unbound_input_returns_and_produces_no_spawner", f"if data entity {PLAYER} {inventory(BROKEN)} "
                                f"unless data entity {PLAYER} {inventory(BROKEN)}.tag.{DATA}.{SOUL} "
                                f'unless data entity {PLAYER} Inventory[{{id:"{SPAWNER}"}}]', results)
            take_drop(server, 0)
            (evidence / "server-cow").touch()
            wait_signal(client, evidence, "placed")
            server.assert_state("the_original_cow_drop_becomes_one_placed_machine", f"{placed_cow()} {no_ingredients()} "
                                f'unless data entity {PLAYER} Inventory[{{id:"{SPAWNER}"}}]', results)
            supply(server, loot_slot=1)
            (evidence / "server-pig").touch()
            wait_signal(client, evidence, "took-pig")
            server.assert_state("the_original_pig_drop_becomes_one_bound_item", f"if data entity {PLAYER} {inventory(SPAWNER, 'pig')} "
                                f"{no_ingredients()} unless data block {BARREL} Items[0]", results)
            (evidence / "server-erase").touch()
            wait_signal(client, evidence, "erased")
            server.assert_state("the_native_erase_recipe_leaves_one_unbound_machine_item", f"if data entity {PLAYER} {inventory(SPAWNER)} "
                                f"unless data entity {PLAYER} {inventory(SPAWNER)}.tag.{DATA}.{SOUL} {no_ingredients()}", results)
            (evidence / "server-finish").touch()
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"Powered Spawner crafting client failed; see {evidence}")
            captures = json.loads(summary.read_text())["client"]["captures"]
            for capture in captures.values():
                check("packaged_client_and_server_jars_match", report["jarSha256"], digest(Path(capture["loadedMods"]["enderio"]["file"])), results)
        finally:
            stop_client(client)
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def verify_player(directory, results):
    player, = (directory / WORLD / "playerdata").glob("*.dat")
    contents = read_data_file(player)["Inventory"]
    check("the_closed_player_save_retains_one_unbound_item", [(SPAWNER, 1, None)], [
        (stack["id"], stack["Count"], stack.get("tag", {}).get("enderio:data", {}).get("enderio:soul")) for stack in contents
    ], results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for key in ("java", "forge-server", "jar", "launch"):
        parser.add_argument(f"--{key}", type=Path, required=True)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}

    def craft(server, results):
        setup(server, results)
        exercise(args, server, results, report)

    def reopen(server, results):
        server.assert_state("the_original_cow_binding_survives_restart", f"{placed_cow()} unless data block {BARREL} Items[0]", results)

    try:
        run_stage(args, directory, report, "craft", craft)
        verify_player(directory, report["assertions"])
        run_stage(args, directory, report, "reopened", reopen)
        verify_player(directory, report["assertions"])
        report["passed"] = True
    finally:
        result = directory / "spawner-crafting-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Spawner crafting passed={report['passed']}, {len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
