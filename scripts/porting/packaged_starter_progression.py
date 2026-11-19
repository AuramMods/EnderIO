#!/usr/bin/env python3
"""Run vanilla fire drops through the stock starter crafting and power chain."""

import argparse
import json
from pathlib import Path
import re
import subprocess
import sys
import time

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage


PLAYER = "EnderIOQA"
GRAINS = "enderio:grains_of_infinity"
CAPACITOR = "enderio:basic_capacitor"
COMPOSITE = "enderio:conduit_binder_composite"
BINDER = "enderio:conduit_binder"
ALLOY = "enderio:conductive_alloy_ingot"
GENERATOR, SMELTER = "3039 80 8", "3040 80 8"
GENERATOR_FEED, SMELTER_FEED, ALLOY_OUTPUT = "3039 81 8", "3040 81 8", "3040 79 8"
BLAST, BINDER_OUTPUT = "3034 80 8", "3034 79 8"
ENERGY_LINE = ("3039 80 9", "3040 80 9")
RECIPES = ("iron_gear", "void_chassis", "basic_capacitor", "stirling_generator", "alloy_smelter",
           "conduit_binder_composite", "energy_conduit")
MATERIALS = (("iron_ingot", 28), ("iron_nugget", 16), ("copper_ingot", 2), ("gold_nugget", 8),
             ("furnace", 4), ("obsidian", 3), ("deepslate", 1), ("gravel", 5), ("clay_ball", 2), ("sand", 2))


def drop_selector(z):
    return f'@e[type=item,x=3009,y=79,z={z},dx=12,dy=3,dz=6,nbt={{Item:{{id:"{GRAINS}"}}}}]'


def load(server, results):
    server.command("forceload add 3008 0 3055 15")
    server.assert_state("both_machine_chunks_and_fire_fields_are_loaded",
                        f"if loaded 3009 80 1 if loaded {GENERATOR} if loaded {SMELTER}", results)


def build(server, results):
    load(server, results)
    for command in ("gamerule doMobSpawning false", "gamerule doDaylightCycle false", "gamerule spawnRadius 0",
                    "time set noon", "weather clear 100000", "setworldspawn 3031 80 10",
                    "fill 3029 79 4 3044 79 14 minecraft:stone_bricks", "setblock 3031 80 8 minecraft:crafting_table",
                    "fill 3009 79 1 3020 79 6 minecraft:bedrock", "fill 3009 79 9 3020 79 14 minecraft:deepslate",
                    "fill 3009 80 1 3020 80 6 minecraft:fire", "fill 3009 80 9 3020 80 14 minecraft:fire"):
        server.command(command)
    for base, z in (("bedrock", 1), ("deepslate", 9)):
        server.assert_state(f"default_fire_crafting_on_{base}_produces_real_grains", f"if entity {drop_selector(z)}",
                            results, timeout=65)


def player_count(server, item):
    response = server.command(f"clear {PLAYER} {item} 0")
    match = re.search(r"Found (\d+) matching item", response)
    if match:
        return int(match[1])
    if "No items were found" in response:
        return 0
    raise AssertionError(response)


def collect_grains(server):
    if player_count(server, GRAINS):
        raise AssertionError("The survival player already has grains before collection")
    bedrock_drops = drop_selector(1)[:-1] + ",sort=arbitrary,limit=1]"
    for _ in range(25):
        server.command(f"tp {PLAYER} {bedrock_drops}")
        time.sleep(0.3)
        count = player_count(server, GRAINS)
        if count >= 16:
            return count
    raise AssertionError("The player did not gather the 16 stock bedrock fire drops needed for starter recipes")


def send_to_block(server, item, dest):
    response = server.command(f'data get entity {PLAYER} Inventory[{{id:"{item}"}}].Slot')
    match = re.search(r"has the following entity data: (\d+)b\s*$", response, re.MULTILINE)
    if not match:
        raise AssertionError(response)
    index = int(match[1])
    source = f"hotbar.{index}" if index < 9 else f"inventory.{index - 9}"
    server.command(f"item replace block {dest} from entity {PLAYER} {source}")
    server.command(f"item replace entity {PLAYER} {source} with minecraft:air")


def take_hopper(server, source, slot):
    server.command(f"item replace entity {PLAYER} hotbar.{slot} from block {source} container.0")
    server.command(f"item replace block {source} container.0 with minecraft:air")


def hopper_item(pos, item, count):
    return f'if data block {pos} Items[{{Slot:0b,Count:{count}b,id:"{item}"}}]'


def start_machines(server, results):
    server.assert_state("native_placement_consumes_both_crafted_machines",
                        f"if block {GENERATOR} enderio:stirling_generator if block {SMELTER} enderio:alloy_smelter "
                        f'unless data entity {PLAYER} Inventory[{{id:"enderio:stirling_generator"}}] '
                        f'unless data entity {PLAYER} Inventory[{{id:"enderio:alloy_smelter"}}]', results)
    server.command(f'data merge block {GENERATOR} {{IOConfig:{{Modes:{{up:"pull",east:"push"}}}}}}')
    server.command(f'data merge block {SMELTER} {{IOConfig:{{Modes:{{up:"pull",west:"pull",down:"push"}}}}}}')
    for pos in (GENERATOR_FEED, SMELTER_FEED, ALLOY_OUTPUT, BINDER_OUTPUT):
        server.command(f"setblock {pos} minecraft:hopper[facing=down]")
    server.command(f"setblock {BLAST} minecraft:blast_furnace[facing=south]")
    send_to_block(server, COMPOSITE, f"{BLAST} container.0")
    server.command(f"item replace block {BLAST} container.1 with minecraft:coal 1")
    send_to_block(server, CAPACITOR, f"{GENERATOR_FEED} container.0")
    server.assert_state("generator_installs_one_of_the_crafted_capacitors_and_leaves_the_other",
                        f'if data block {GENERATOR} Items.Items[{{Slot:1,id:"{CAPACITOR}",Count:1}}] '
                        f"{hopper_item(GENERATOR_FEED, CAPACITOR, 1)}", results)
    server.command(f"item replace block {SMELTER_FEED} container.0 from block {GENERATOR_FEED} container.0")
    server.command(f"item replace block {GENERATOR_FEED} container.0 with minecraft:air")
    server.assert_state("smelter_installs_the_second_crafted_capacitor",
                        f'if data block {SMELTER} Items.Items[{{Slot:4,id:"{CAPACITOR}",Count:1}}] '
                        f"unless data block {SMELTER_FEED} Items[0]", results)
    server.command(f"item replace block {SMELTER_FEED} container.0 with minecraft:iron_ingot 2")
    server.command(f"item replace block {SMELTER_FEED} container.1 with minecraft:copper_ingot 2")
    server.command(f"item replace block {GENERATOR_FEED} container.0 with minecraft:coal 1")
    server.assert_state("one_coal_powers_two_native_alloy_batches_and_leaves_17600_fe",
                        f'if data block {GENERATOR} {{BurnTime:0,BurnDuration:600,EnergyStored:0}} '
                        f'if data block {SMELTER} {{EnergyStored:17600}} {hopper_item(ALLOY_OUTPUT, ALLOY, 4)} '
                        f"unless data block {GENERATOR_FEED} Items[0] unless data block {SMELTER_FEED} Items[0]", results, timeout=60)
    server.assert_state("the_stock_blast_furnace_recipe_turns_eight_crafted_composites_into_16_binders",
                        hopper_item(BINDER_OUTPUT, BINDER, 16), results, timeout=30)


def route_state(balance):
    bundles = " ".join(f'if data block {pos} {{Conduits:["enderio:energy"]}}' for pos in ENERGY_LINE)
    return (f'{bundles} if data block {GENERATOR} {{BurnTime:0,EnergyStored:0,IOConfig:{{Modes:{{east:"disable",south:"push"}}}}}} '
            f'if data block {SMELTER} {{EnergyStored:{balance},IOConfig:{{Modes:{{west:"disable",south:"pull"}}}}}}')


def exercise_client(args, server, results, report):
    output = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "starter-progression", "--output", str(output), "--server", f"127.0.0.1:{args.port}",
               "--timeout", "260"]
    with (server.directory / "starter-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            grains = collect_grains(server)
            report["nativeGrainsCollected"] = grains
            for slot, (item, count) in enumerate(MATERIALS):
                server.command(f"item replace entity {PLAYER} inventory.{slot} with minecraft:{item} {count}")
            for recipe in RECIPES:
                server.command(f"recipe give {PLAYER} enderio:{recipe}")
            server.command(f"tp {PLAYER} 3031.5 80 10.5 180 22")
            (output / "server-starter-table").touch()
            wait_signal(client, output, "machines-crafted")
            remaining = player_count(server, GRAINS)
            if remaining != grains - 16:
                raise AssertionError(f"Starter recipes consumed {grains - remaining} grains instead of 16")
            report["nativeGrainsSpent"] = grains - remaining
            server.command(f"tp {PLAYER} 3039.5 80 11.5 180 23")
            (output / "server-place-machines").touch()
            wait_signal(client, output, "machines-placed")
            start_machines(server, results)
            take_hopper(server, ALLOY_OUTPUT, 1)
            take_hopper(server, BINDER_OUTPUT, 2)
            server.command(f"tp {PLAYER} 3031.5 80 10.5 180 22")
            (output / "server-conduit-table").touch()
            wait_signal(client, output, "conduits-crafted")
            server.assert_state("the_native_recipe_spends_three_of_four_alloys_and_six_of_16_binders",
                                f'if data entity {PLAYER} {{Inventory:[{{id:"{ALLOY}",Count:1b}},{{id:"{BINDER}",Count:10b}},'
                                '{id:"enderio:conduit",Count:8b,tag:{"enderio:data":{"enderio:conduit":"enderio:energy"}}}]}', results)
            server.command(f'data merge block {GENERATOR} {{IOConfig:{{Modes:{{east:"disable",south:"push"}}}}}}')
            server.command(f'data merge block {SMELTER} {{IOConfig:{{Modes:{{west:"disable",south:"pull"}}}}}}')
            server.command(f"tp {PLAYER} 3039.5 80 11.5 166 22")
            (output / "server-place-conduits").touch()
            wait_signal(client, output, "conduits-placed")
            server.assert_state("both_crafted_bundles_place_with_direct_transfer_faces_disabled", route_state(17600), results)
            server.command(f"item replace block {GENERATOR_FEED} container.0 with minecraft:coal 1")
            server.assert_state("second_coal_sends_24000_fe_only_through_the_crafted_cross_chunk_conduits",
                                route_state(41600), results, timeout=60)
            (output / "server-complete").touch()
            if client.wait(timeout=35) != 0:
                raise AssertionError(f"Starter progression client failed; see {output}")
            summary = json.loads((output / "summary.json").read_text())
            loaded = summary["client"]["captures"]["starter-energy-route"]["loadedMods"]["enderio"]["file"]
            if digest(Path(loaded)) != report["jarSha256"]:
                raise AssertionError("Client and server loaded different Ender IO jars")
        finally:
            stop_client(client)
            summary = output / "summary.json"
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for path in ("java", "forge-server", "jar", "launch"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(args.jar)}

    def exercise(server, results):
        build(server, results)
        exercise_client(args, server, results, report)

    def reopen(server, results):
        load(server, results)
        server.assert_state("the_crafted_cross_chunk_route_and_its_exact_charge_survive_restart", route_state(41600), results)

    try:
        run_stage(args, directory, report, "fresh", exercise)
        run_stage(args, directory, report, "reopened", reopen)
        report["passed"] = True
    finally:
        (directory / "starter-progression-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
