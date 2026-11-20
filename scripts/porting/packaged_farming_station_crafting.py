#!/usr/bin/env python3
"""Craft and factory-reset an original Farming Station in a default survival world."""

import argparse
import json
from pathlib import Path
import re
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_farming_station_carry import BLOCK, CHARGE, INPUTS, WHEAT, block_data, carried_stack
from packaged_niard import packs
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_solar_charging import check
from packaged_spawners import PLAYER, WORLD, prepare_profile, run_stage
from saved_world import read_data_file


TABLE, SOURCE, DESTINATION = (f"{x} 80 8" for x in (4324, 4328, 4333))
DROPS = "@e[type=minecraft:item,x=4328.5,y=80,z=8.5,distance=..12]"
INGREDIENTS = {"enderio:energized_gear": 2, "enderio:pulsating_crystal": 1, "enderio:ensouled_chassis": 1,
               "enderio:dark_steel_ingot": 2, "enderio:z_logic_controller": 1, "enderio:soularium_ingot": 2}
CONTENTS = {re.search(r'id:"([^"]+)"', stack)[1] for stack in [*INPUTS, WHEAT]}


def lacks_inventory(items):
    return " ".join(f'unless data entity {PLAYER} Inventory[{{id:"{item}"}}]' for item in sorted(items))


def defaults(position):
    return (f'if block {position} {BLOCK} if data block {position} {{EnergyStored:0,RedstoneControl:"always_active"}} '
            f"unless data block {position} ActionRange unless data block {position} EntityStorage.entity_type "
            f"unless data block {position} Items.Items[0] "
            + " ".join(f"unless data block {position} IOConfig.Modes.{side}"
                       for side in ("down", "up", "north", "south", "west", "east")))


def stand(server, output, marker, position):
    x, y, z = map(int, position.split())
    server.command(f"tp {PLAYER} {x + 0.5} {y} {z + 2.5} 180 20")
    (output / f"server-{marker}").touch()


def setup(server, results):
    packs(server, results, False)
    for command in ("gamerule doMobSpawning false", "gamerule doLimitedCrafting true", "gamerule spawnRadius 0",
                    "gamerule doDaylightCycle false", "time set noon", "forceload add 4320 0 4351 15",
                    "fill 4320 79 2 4340 79 15 minecraft:stone", "setworldspawn 4324 80 11",
                    f"setblock {TABLE} minecraft:crafting_table", "scoreboard objectives add port_farm dummy"):
        server.command(command)


def craft_place_and_load(server, results, client, output):
    wait_signal(client, output, "connected")
    for slot, (item, count) in enumerate(INGREDIENTS.items()):
        if item == "enderio:soularium_ingot":
            break
        server.command(f"item replace entity {PLAYER} hotbar.{slot} with {item} {count}")
    (output / "server-missing-soularium").touch()
    wait_signal(client, output, "locked")
    server.command(f"item replace entity {PLAYER} hotbar.5 with enderio:soularium_ingot 2")
    stand(server, output, "table", TABLE)
    wait_signal(client, output, "crafted")
    server.command(f"execute store result score #stations port_farm run clear {PLAYER} {BLOCK} 0")
    server.assert_state("crafting_consumes_all_nine_ingredients_for_one_untagged_station",
                        f'if score #stations port_farm matches 1 unless data entity {PLAYER} Inventory[{{id:"{BLOCK}"}}].tag '
                        f"{lacks_inventory(INGREDIENTS)}", results)
    stand(server, output, "source", SOURCE)
    wait_signal(client, output, "placed")
    server.assert_state("fresh_station_places_with_factory_defaults", f"{defaults(SOURCE)} {lacks_inventory([BLOCK])}", results)
    fixture = block_data([WHEAT], CHARGE)
    server.command(f"data merge block {SOURCE} {fixture}")
    server.assert_state("original_placed_station_has_known_inventory_soul_charge_and_controls",
                        f"if data block {SOURCE} {fixture} {lacks_inventory(CONTENTS)}", results)


def mine_erase_and_replant(server, results, client, output):
    server.command(f'item replace entity {PLAYER} hotbar.0 with minecraft:diamond_pickaxe'
                   '{Enchantments:[{id:"minecraft:efficiency",lvl:5s}]}')
    stand(server, output, "mine", SOURCE)
    wait_signal(client, output, "mined")
    typed = carried_stack()
    drop = DROPS[:-1] + f',nbt={{Item:{typed}}},sort=nearest,limit=1]'
    server.assert_state("native_mining_drops_the_populated_machine", f"if block {SOURCE} minecraft:air if entity {drop}", results)
    server.command(f"execute at {drop} run tp {PLAYER} ~ ~ ~")
    wait_signal(client, output, "picked-up")
    server.assert_state("native_pickup_carries_the_original_state",
                        f"if data entity {PLAYER} Inventory[{typed}] unless entity {DROPS}", results)
    stand(server, output, "erase", TABLE)
    wait_signal(client, output, "erased")
    server.command(f"execute store result score #stations port_farm run clear {PLAYER} {BLOCK} 0")
    server.assert_state("factory_reset_consumes_the_populated_item_and_results_in_one_blank_item",
                        f'if score #stations port_farm matches 1 unless data entity {PLAYER} Inventory[{{id:"{BLOCK}"}}].tag '
                        f"{lacks_inventory(CONTENTS)} unless entity {DROPS}", results)
    stand(server, output, "destination", DESTINATION)
    wait_signal(client, output, "replanted")
    server.assert_state("replanted_reset_restores_all_defaults",
                        f"{defaults(DESTINATION)} {lacks_inventory([*CONTENTS, BLOCK])} unless entity {DROPS}", results)
    (output / "server-open").touch()


def exercise(args, server, results, report):
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "farming-station-crafting", "--server", f"127.0.0.1:{args.port}", "--output", str(evidence),
               "--timeout", "205"]
    summary = evidence / "summary.json"
    with (server.directory / "farming-station-crafting-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            craft_place_and_load(server, results, client, evidence)
            mine_erase_and_replant(server, results, client, evidence)
            if client.wait(timeout=35) != 0:
                raise AssertionError(f"Farming Station crafting client failed; see {evidence}")
            capture = json.loads(summary.read_text())["client"]["captures"]["farming-station-reset-menu"]
            check("client_and_server_use_the_same_production_artifact", report["jarSha256"],
                  digest(Path(capture["loadedMods"]["enderio"]["file"])), results)
        finally:
            stop_client(client)
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def reopened(server, results):
    packs(server, results, False)
    server.assert_state("reset_station_stays_blank_after_restart",
                        f"{defaults(DESTINATION)} if block {SOURCE} minecraft:air unless entity {DROPS}", results)
    player, = (server.directory / WORLD / "playerdata").glob("*.dat")
    contents = read_data_file(player)["Inventory"]
    check("disk_player_inventory_never_recovers_the_erased_items", ["minecraft:diamond_pickaxe"],
          [stack["id"] for stack in contents], results)


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
    report = {"passed": False, "forge": FORGE, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}

    def fresh(server, results):
        setup(server, results)
        exercise(args, server, results, report)

    try:
        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", reopened)
        report["passed"] = True
    finally:
        result = directory / "farming-station-crafting-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Farming Station crafting passed={report['passed']}, {len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
