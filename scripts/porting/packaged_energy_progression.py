#!/usr/bin/env python3
"""Continue the starter world through higher alloys, capacitors and live conduit upgrades."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage
from packaged_starter_progression import ALLOY, ALLOY_OUTPUT, GENERATOR, GENERATOR_FEED, SMELTER, SMELTER_FEED
from packaged_starter_progression import BINDER, PLAYER, hopper_item, load, route_state


ENERGETIC = "enderio:energetic_alloy_ingot"
VIBRANT = "enderio:vibrant_alloy_ingot"
BARREL = "3043 80 8"
RECIPES = ("double_layer_capacitor", "octadic_capacitor", "enhanced_energy_conduit", "enhanced_energy_conduit_upgrade",
           "ender_energy_conduit", "ender_energy_conduit_upgrade")
UPGRADES = (("enhanced_energy", "energy"), ("enhanced_energy", "enhanced_energy"),
            ("ender_energy", "enhanced_energy"), ("ender_energy", "ender_energy"))
STICK_FE = 1480
FINAL_FE = 16000 + len(UPGRADES) * STICK_FE


def item_to_feeder(server, slot, item, count):
    server.command(f"item replace block {SMELTER_FEED} container.{slot} with {item} {count}")


def stock_batch(server, results, output, count, energy):
    kind = output.removeprefix("enderio:")
    server.assert_state(f"stock_{kind}_batches_finish_with_exact_output_and_energy",
                        f'if data block {SMELTER} {{EnergyStored:{energy}}} '
                        f"{hopper_item(ALLOY_OUTPUT, output, count)} unless data block {SMELTER_FEED} Items[0] "
                        f"unless data block {SMELTER} Task", results, timeout=135)


def stockpile_item(pos, slot, item, count):
    return f'if data block {pos} Items[{{Slot:{slot}b,Count:{count}b,id:"{item}"}}]'


def produce(server, results):
    load(server, results)
    server.assert_state("the_previous_cycles_crafted_route_starts_with_41600_fe", route_state(41600), results)
    item_to_feeder(server, 0, "minecraft:iron_ingot", 4)
    item_to_feeder(server, 1, "minecraft:copper_ingot", 4)
    server.command(f"item replace block {GENERATOR_FEED} container.0 with minecraft:coal 2")
    # Run the second set of alloy inputs while the generator is still burning.
    server.assert_state("four_conductive_batches_produce_eight_native_ingots", hopper_item(ALLOY_OUTPUT, ALLOY, 8),
                        results, timeout=60)
    server.command(f"item replace block {SMELTER_FEED} container.0 from block {ALLOY_OUTPUT} container.0")
    server.command(f"item replace block {ALLOY_OUTPUT} container.0 with minecraft:air")
    item_to_feeder(server, 1, "minecraft:redstone", 8)
    item_to_feeder(server, 2, "minecraft:gold_ingot", 8)
    stock_batch(server, results, ENERGETIC, 16, 38400)
    server.assert_state("both_coals_finish_and_deliver_all_48000_fe_through_the_crafted_route",
                        f'if data block {GENERATOR} {{BurnTime:0,EnergyStored:0}} '
                        f"unless data block {GENERATOR_FEED} Items[0]", results)

    server.command(f"setblock {BARREL} minecraft:barrel")
    server.command(f"item replace block {BARREL} container.0 from block {ALLOY_OUTPUT} container.0")
    server.command(f"item replace block {ALLOY_OUTPUT} container.0 with minecraft:air")
    # Split the exact output stack before consuming four of its ingots in the next alloy.
    server.command(f"item replace block {BARREL} container.2 from block {BARREL} container.0")
    server.command(f"data modify block {BARREL} Items[{{Slot:0b}}].Count set value 12b")
    server.command(f"data modify block {BARREL} Items[{{Slot:2b}}].Count set value 4b")
    server.command(f"item replace block {SMELTER_FEED} container.0 from block {BARREL} container.2")
    server.command(f"item replace block {BARREL} container.2 with minecraft:air")
    item_to_feeder(server, 1, "minecraft:ender_pearl", 4)
    item_to_feeder(server, 2, "minecraft:glowstone_dust", 4)
    stock_batch(server, results, VIBRANT, 8, 16000)
    server.command(f"item replace block {BARREL} container.1 from block {ALLOY_OUTPUT} container.0")
    server.command(f"item replace block {ALLOY_OUTPUT} container.0 with minecraft:air")
    server.assert_state("stockpile_retains_12_energetic_and_eight_vibrant_ingots",
                        f"{stockpile_item(BARREL, 0, ENERGETIC, 12)} {stockpile_item(BARREL, 1, VIBRANT, 8)} "
                        f'unless data block {SMELTER} Items.Items[{{id:"{ENERGETIC}"}}]', results)


def installed_octadic():
    return f'if data block {SMELTER} Items.Items[{{Slot:4,id:"enderio:octadic_capacitor",Count:1}}]'


def conduit_stack(variant, amount):
    return (f'{{id:"enderio:conduit",Count:{amount}b,tag:{{"enderio:data":'
            f'{{"enderio:conduit":"enderio:{variant}"}}}}}}')


def feed_player(server):
    for slot in (0, 1):
        server.command(f"item replace entity {PLAYER} inventory.{slot} from block {BARREL} container.{slot}")
        server.command(f"item replace block {BARREL} container.{slot} with minecraft:air")
    # Supply earlier-tier ingredients. The smelter and both installed tubes still come from the starter world.
    server.command(f"give {PLAYER} {BINDER} 16")
    for slot, item, count in ((3, "enderio:basic_capacitor", 4), (4, "enderio:powdered_coal", 2),
                              (5, "minecraft:glowstone", 1)):
        server.command(f"item replace entity {PLAYER} inventory.{slot} with {item} {count}")
    for recipe in RECIPES:
        server.command(f"recipe give {PLAYER} enderio:{recipe}")


def run_client(args, server, results, report):
    output = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "energy-progression", "--output", str(output), "--server", f"127.0.0.1:{args.port}",
               "--timeout", "185"]
    with (server.directory / "energy-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            feed_player(server)
            server.command(f"tp {PLAYER} 3031.5 80 10.5 180 22")
            (output / "server-table").touch()
            wait_signal(client, output, "crafted")
            leftovers = ','.join((conduit_stack("energy", 5), conduit_stack("enhanced_energy", 15), conduit_stack("ender_energy", 16),
                                  f'{{id:"{ENERGETIC}",Count:3b}}', f'{{id:"{VIBRANT}",Count:1b}}',
                                  f'{{id:"{BINDER}",Count:2b}}', '{id:"enderio:octadic_capacitor",Count:1b}'))
            server.assert_state("survival_crafting_conserves_the_lower_tiers_and_new_alloys",
                                f"if data entity {PLAYER} {{Inventory:[{leftovers}]}} "
                                f'unless data entity {PLAYER} Inventory[{{id:"enderio:basic_capacitor"}}] '
                                f'unless data entity {PLAYER} Inventory[{{id:"enderio:double_layer_capacitor"}}]', results)
            server.command(f"tp {PLAYER} 3039.5 80 11.5 166 22")
            (output / "server-smelter").touch()
            wait_signal(client, output, "capacitor-installed")
            server.assert_state("the_native_menu_installs_octadic_and_returns_the_original_basic_capacitor",
                                f'{installed_octadic()} {route_state(16000)} '
                                f'if data entity {PLAYER} Inventory[{{id:"enderio:basic_capacitor",Count:1b}}]', results)

            for step, variants in enumerate(UPGRADES):
                before = 16000 + step * STICK_FE
                (output / f"server-upgrade-{step}").touch()
                wait_signal(client, output, f"upgraded-{step}")
                server.assert_state(f"native_upgrade_{step}_retains_the_route_and_exact_balance", route_state(before, variants), results)
                server.command(f"item replace block {GENERATOR_FEED} container.0 with minecraft:stick 1")
                server.assert_state(f"live_upgraded_route_{step}_moves_every_fe_from_one_stick",
                                    route_state(before + STICK_FE, variants), results, timeout=15)
            returned = ','.join((conduit_stack("energy", 7), conduit_stack("enhanced_energy", 15), conduit_stack("ender_energy", 14)))
            server.assert_state("in_place_replacement_returns_each_lower_tier_exactly_once",
                                f"if data entity {PLAYER} {{Inventory:[{returned}]}}", results)
            (output / "server-done").touch()
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"Energy progression client failed; see {output}")
            summary = json.loads((output / "summary.json").read_text())
            loaded = summary["client"]["captures"]["upgraded-conduit-route"]["loadedMods"]["enderio"]["file"]
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
    for path in ("java", "forge-server", "jar"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--starter-world", type=Path, help="A saved starter world to produce the native alloys")
    source.add_argument("--alloy-world", type=Path, help="A saved alloy-production world to exercise survival upgrades")
    parser.add_argument("--launch", type=Path, help="The quiet packaged client profile for the survival upgrades")
    args = parser.parse_args()
    if args.alloy_world and not args.launch:
        parser.error("--alloy-world requires --launch")
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    shutil.copytree(args.starter_world or args.alloy_world, directory / WORLD)
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(args.jar)}

    def exercise(server, results):
        load(server, results)
        server.assert_state("native_alloys_and_the_16000_fe_balance_survive_a_fresh_installation",
                            f"{route_state(16000)} {stockpile_item(BARREL, 0, ENERGETIC, 12)} "
                            f"{stockpile_item(BARREL, 1, VIBRANT, 8)}", results)
        run_client(args, server, results, report)

    def reopen(server, results):
        load(server, results)
        server.assert_state("both_ender_conduits_and_the_octadic_smelters_exact_balance_survive_restart",
                            f"{route_state(FINAL_FE, UPGRADES[-1])} {installed_octadic()}", results)

    try:
        if args.starter_world:
            run_stage(args, directory, report, "alloys", produce)
        if args.launch:
            run_stage(args, directory, report, "upgrades", exercise)
            run_stage(args, directory, report, "reopened", reopen)
        report["passed"] = True
    finally:
        (directory / "energy-progression-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
