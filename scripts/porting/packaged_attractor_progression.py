#!/usr/bin/env python3
"""Spend the original saved Enticing Crystal and exercise the crafted Attractor Obelisk."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_alloy_progression import verify_saved_inventory
from packaged_bound_crystal_progression import ENTICING, crystal, empty_vial, enticing_delivery, spent_binder
from packaged_entity_obelisks import PIG_FILTER_TAG, machine_state
from packaged_glass_progression import transfer
from packaged_sag_progression import data_read
from packaged_server_smoke import ROOT, digest
from packaged_soul_binder import item
from packaged_spawners import WORLD, prepare_profile, run_stage
from packaged_starter_progression import PLAYER


SOURCE = ENTICING.pos(80)
CHEST, TABLE = "3525 80 8", "3524 80 8"
OBELISK, SIGNAL = "3528 80 8", "3528 80 7"
BLOCK = "enderio:attractor_obelisk"
STOCK = (("enderio:energetic_alloy_ingot", 2), ("enderio:energized_gear", 1), ("enderio:soularium_ingot", 2),
         ("enderio:ensouled_chassis", 1), ("enderio:basic_capacitor", 1), (f"enderio:basic_soul_filter{PIG_FILTER_TAG}", 1))
PIG_FILTER = "type=minecraft:pig,tag=port_attractor_progression"
PIG = f"@e[{PIG_FILTER},limit=1]"
NEAR = f"@e[{PIG_FILTER},x=3528.5,y=80,z=8.5,distance=..2.75]"
STARTING_FE, FE_PER_TICK = 12000, 18


def original_vial():
    return (f'{empty_vial(SOURCE)} unless data block {SOURCE} Items[{{id:"{crystal(ENTICING)}"}}] '
            f"unless data block {SOURCE} Items[1]")


def configured():
    return (f"{machine_state('attractor', 3528, 7, True)} if block {OBELISK} {BLOCK}[facing=south] "
            f'if data block {OBELISK} {{RedstoneControl:"active_with_signal"}}')


def setup(server, results):
    server.command("forceload add 3520 0 3551 15")
    server.assert_state("the_saved_original_crystal_and_returned_vial_are_present_in_the_copied_world",
                        f"{spent_binder(ENTICING)} {enticing_delivery()} if loaded {OBELISK}", results)
    for command in ("gamerule doLimitedCrafting true", "gamerule spawnRadius 0", "time set noon",
                    "fill 3522 79 5 3535 79 15 minecraft:stone", "setworldspawn 3524 80 12",
                    f"setblock {CHEST} minecraft:chest[facing=south]", f"setblock {TABLE} minecraft:crafting_table",
                    "fill 3529 80 7 3534 81 7 minecraft:stone", "fill 3529 80 9 3534 81 9 minecraft:stone",
                    "fill 3534 80 8 3534 81 8 minecraft:stone"):
        server.command(command)
    for slot, (ingredient, count) in enumerate(STOCK, 1):
        server.command(f"item replace block {CHEST} container.{slot} with {ingredient} {count}")
    transfer(server, SOURCE, 0, CHEST, 0)
    server.assert_state("only_the_original_crystal_goes_to_the_workshop_and_the_vial_stays_behind",
                        f"if data block {CHEST} Items[{item(0, crystal(ENTICING), vanilla=True)}] {original_vial()}", results)


def draw_pig(server, results, phase, starting_energy):
    server.command(f'summon minecraft:pig 3532.5 80 8.5 {{Tags:["port_attractor_progression"],PersistenceRequired:1b}}')
    server.assert_state(f"{phase}_keeps_the_pig_outside_reach_without_a_signal_and_preserves_its_charge",
                        f"if entity {PIG} unless entity {NEAR} if block {SIGNAL} minecraft:air "
                        f"if data block {OBELISK} {{EnergyStored:{starting_energy}}}", results)
    server.command(f"setblock {SIGNAL} minecraft:redstone_block")
    try:
        server.assert_state(f"{phase}_the_powered_obelisk_draws_the_pig_via_native_navigation",
                            f"if entity {NEAR}", results, timeout=15)
    finally:
        server.command(f"setblock {SIGNAL} minecraft:air")
        server.command(f"data get entity {PIG} Pos")
    remaining = int(data_read(server, OBELISK, "EnergyStored"))
    if not 0 < remaining < starting_energy or (starting_energy - remaining) % FE_PER_TICK:
        raise AssertionError(f"Obelisk did not spend whole {FE_PER_TICK} FE ticks: {starting_energy} to {remaining}")
    server.command(f"kill {PIG}")
    return remaining


def run_client(args, server, results, report):
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    summary = evidence / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "attractor-progression", "--server", f"127.0.0.1:{args.port}", "--output", str(evidence), "--timeout", "175"]
    with (server.directory / "attractor-progression-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            server.command(f"tp {PLAYER} 3524.5 80 11.5 180 16")
            (evidence / "server-workshop").touch()
            wait_signal(client, evidence, "crafted")
            ingredients = (crystal(ENTICING), *(ingredient for ingredient, _ in STOCK[:4]))
            spent = " ".join(f'unless data entity {PLAYER} Inventory[{{id:"{ingredient}"}}]' for ingredient in ingredients)
            server.assert_state("survival_crafts_one_obelisk_from_the_original_crystal",
                                f'if data entity {PLAYER} Inventory[{item(None, BLOCK, vanilla=True)}] {spent} '
                                f"unless data block {CHEST} Items[0] {original_vial()}", results)
            server.command(f"tp {PLAYER} 3528.5 80 11.5 180 16")
            (evidence / "server-place").touch()
            wait_signal(client, evidence, "placed")
            server.assert_state("survival_places_the_crafted_obelisk_with_empty_slots",
                                f'if block {OBELISK} {BLOCK}[facing=south] unless data block {OBELISK} Items.Items[0] '
                                f'unless data entity {PLAYER} Inventory[{{id:"{BLOCK}"}}]', results)
            (evidence / "server-open").touch()
            wait_signal(client, evidence, "configured")
            server.assert_state("native_menu_persists_range_visibility_redstone_filter_and_capacitor",
                                f'{configured()} unless data entity {PLAYER} Inventory[{{id:"enderio:basic_capacitor"}}] '
                                f'unless data entity {PLAYER} Inventory[{{id:"enderio:basic_soul_filter"}}]', results)
            server.command(f"data merge block {OBELISK} {{EnergyStored:{STARTING_FE}}}")
            report["savedEnergy"] = draw_pig(server, results, "fresh", STARTING_FE)
            (evidence / "server-done").touch()
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"Attractor progression client failed; see {evidence}")
            for capture in json.loads(summary.read_text())["client"]["captures"].values():
                if digest(Path(capture["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                    raise AssertionError("Attractor client and server Ender IO jars differ")
        finally:
            stop_client(client)
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    for key in ("forge-server", "jar", "launch", "source-world"):
        parser.add_argument(f"--{key}", type=Path, required=True)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    shutil.copytree(args.source_world, directory / WORLD)
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}

    def fresh(server, results):
        setup(server, results)
        run_client(args, server, results, report)

    def reopened(server, results):
        energy = report["savedEnergy"]
        server.assert_state("the_original_attractor_retains_its_settings_power_and_consumed_ingredients_after_restart",
                            f"{configured()} if data block {OBELISK} {{EnergyStored:{energy}}} "
                            f"{original_vial()} unless data block {CHEST} Items[0]", results)
        report["reopenedEnergy"] = draw_pig(server, results, "reopened", energy)

    try:
        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", reopened)
        verify_saved_inventory(directory, report, expected={})
        report["passed"] = True
    finally:
        result = directory / "attractor-progression-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Attractor progression passed={report['passed']}, {len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
