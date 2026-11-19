#!/usr/bin/env python3
"""Craft a Staff of Levity from the original stock fires, alloys and bound crystal."""

import argparse
from dataclasses import replace
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_alloy_progression import verify_saved_inventory
from packaged_bound_crystal_progression import BIND_RESERVE, CAPACITOR, PRESCIENT, XP_RESERVE, binding_inputs, capture, crystal, empty_vial, spent_binder
from packaged_glass_progression import Smelt, finished_smelter, prepare_smelter, start_smelter, supply, transfer
from packaged_levitation_staff import DATA, ENERGY_KEY, FLUID, FLUID_KEY, STAFF, vapor
from packaged_obelisks import fluid_contents
from packaged_server_smoke import ROOT, digest
from packaged_soul_binder import item, xp_tank
from packaged_spawners import WORLD, prepare_profile, run_stage
from packaged_starter_progression import GRAINS, PLAYER, collect_grains, drop_selector, hopper_item, player_count
from saved_world import read_data_file


CHEST, TABLE = "3032 80 8", "3031 80 8"
CHARGER, TANK = "3036 80 8", "3040 80 8"
STAFF_X = 3043.5
DARK = Smelt(3045, 4, "dark_steel_ingot", 2, 1600)
BINDING = replace(PRESCIENT, x=3048)
CHARGE, FUEL = 10000, 60
STICKS = "minecraft:stick"
SEEDS = "enderio:suspicious_seed"
NUGGETS, INGOTS, RODS = "enderio:dark_steel_nugget", "enderio:dark_steel_ingot", "enderio:infinity_rod"


def supply_crystal(server, results):
    route = BINDING
    pos, hopper, tank = route.pos(82), route.pos(81), route.pos(82, z=7)
    server.command(f'setblock {pos} enderio:soul_binder[facing=south]'
                   f'{{Items:{{Items:[{item(4, CAPACITOR)}]}},IOConfig:{{Modes:{{down:"push"}}}}}}')
    server.command(f"setblock {hopper} minecraft:hopper[facing=down]")
    server.command(f"setblock {route.pos(82, dx=1)} minecraft:hopper[facing=west]{{Items:[{item(0, route.ingredient, vanilla=True)}]}}")
    server.command(f'setblock {tank} enderio:fluid_tank[facing=south]'
                   f'{{{xp_tank(route.xp + XP_RESERVE)},IOConfig:{{Modes:{{south:"push"}}}}}}')
    capture(server, results, route)
    server.assert_state("the_actual_shulker_vial_and_stock_crystal_reach_the_unpowered_binder",
                        binding_inputs(route, 0, route.xp + XP_RESERVE), results)
    server.command(f"data merge block {pos} {{EnergyStored:{route.energy + BIND_RESERVE}}}")
    server.assert_state("the_shulker_binding_exports_the_original_prescient_crystal_and_empty_vial",
                        f"{spent_binder(route)} {hopper_item(hopper, crystal(route), 1)} {empty_vial(hopper)} "
                        f"unless data block {hopper} Items[2]", results, timeout=40)
    transfer(server, hopper, 0, CHEST, 1)


def setup(server, results):
    server.command("forceload add 3008 0 3055 15")
    server.assert_state("the_bedrock_fire_field_and_workshop_are_loaded", f"if loaded 3009 80 1 if loaded {CHEST}", results)
    for command in ("gamerule doMobSpawning false", "gamerule doDaylightCycle false", "gamerule spawnRadius 0",
                    "gamerule doLimitedCrafting true", "gamerule fireDamage false", "time set midnight", "weather clear 10000",
                    "fill 3029 79 4 3049 79 14 minecraft:stone", "setworldspawn 3031 80 12",
                    f"setblock {CHEST} minecraft:chest[facing=south]", f"setblock {TABLE} minecraft:crafting_table",
                    "fill 3009 79 1 3020 79 6 minecraft:bedrock", "fill 3009 80 1 3020 80 6 minecraft:fire"):
        server.command(command)
    prepare_smelter(server, DARK)
    for slot, ingredient in enumerate(("minecraft:iron_ingot", "enderio:powdered_coal", "minecraft:obsidian")):
        supply(server, DARK, slot, ingredient, 1)
    start_smelter(server, DARK)
    supply_crystal(server, results)
    server.assert_state("the_stock_alloy_recipe_exports_two_dark_steel_ingots_for_3200_fe", finished_smelter(DARK), results)
    transfer(server, DARK.pos(79), 0, CHEST, 0)
    server.command(f"item replace block {CHEST} container.2 with {STICKS} 2")
    server.command(f'setblock {CHARGER} enderio:wired_charger[facing=south]'
                   f'{{Items:{{Items:[{item(0, "enderio:basic_capacitor")}]}}}}')
    server.command(f'setblock {TANK} enderio:fluid_tank[facing=south]'
                   f'{{{fluid_contents(FLUID, FUEL, ",Tag:{PortBatch:infinity_rod}")}}}')
    server.command("time set noon")
    server.assert_state("the_stock_bedrock_fires_produce_real_grains", f"if entity {drop_selector(1)}", results, timeout=65)


def player_has(item_id, count):
    if count == 0:
        return f'unless data entity {PLAYER} Inventory[{{id:"{item_id}"}}]'
    return f'if data entity {PLAYER} Inventory[{{id:"{item_id}",Count:{count}b}}]'


def spent_staff():
    stack = f'Inventory[{{id:"{STAFF}",Count:1b,tag:{{{DATA}:{{{ENERGY_KEY}:{CHARGE - FUEL}}}}}}}]'
    return (f"if data entity {PLAYER} {stack} unless data entity {PLAYER} {stack}.tag.{DATA}.{FLUID_KEY} "
            f'unless data entity {PLAYER} {stack}.tag.{DATA}."enderio:toggled"')


def machine_remainders():
    return (f"{spent_binder(BINDING)} {empty_vial(BINDING.pos(81))} unless data block {BINDING.pos(81)} Items[1] "
            f"if data block {DARK.pos(80)} {{EnergyStored:137}} unless data block {DARK.pos(79)} Items[0] "
            f"if data block {CHARGER} {{EnergyStored:0}} unless data block {CHARGER} Items.Items[{{Slot:1}}] "
            f"unless data block {CHARGER} Items.Items[{{Slot:2}}] unless data block {TANK} Fluids.Tanks[0].Contents.FluidName "
            f"unless data block {TANK} Items.Items[{{Slot:2}}] unless data block {TANK} Items.Items[{{Slot:3}}] "
            f"unless data block {CHEST} Items[0]")


def stand(server, evidence, marker, x):
    server.command(f"tp {PLAYER} {x} 80 11.5 180 16")
    (evidence / f"server-{marker}").touch()


def run_client(args, server, results, report):
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    summary = evidence / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "infinity-rod-progression", "--server", f"127.0.0.1:{args.port}", "--output", str(evidence), "--timeout", "195"]
    with (server.directory / "infinity-rod-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            collect_grains(server, 4)
            server.command(f"tp {PLAYER} 3031.5 80 11.5 180 16")
            grains = player_count(server, GRAINS)
            seeds = player_count(server, SEEDS)
            report["grainsCollected"] = grains
            report["seedsCollected"] = seeds
            (evidence / "server-workshop").touch()
            wait_signal(client, evidence, "crafted")
            server.assert_state("two_real_fire_drop_rods_and_the_bound_crystal_make_one_fresh_staff",
                                f"{player_has(STAFF, 1)} {player_has(INGOTS, 1)} {player_has(NUGGETS, 1)} {player_has(GRAINS, grains - 4)} "
                                f"{player_has(RODS, 0)} {player_has(STICKS, 0)} {player_has(crystal(BINDING), 0)} {player_has(SEEDS, seeds)}", results)
            stand(server, evidence, "charger", 3036.5)
            wait_signal(client, evidence, "charger-inserted")
            server.assert_state("the_original_staff_enters_the_native_wired_charger_input_once",
                                f"if data block {CHARGER} Items.Items[{item(1, STAFF)}] {player_has(STAFF, 0)}", results)
            server.command(f"data merge block {CHARGER} {{EnergyStored:{CHARGE}}}")
            wait_signal(client, evidence, "charged")
            server.assert_state("the_native_charger_moves_all_10000_fe_to_the_original_staff",
                                f'if data entity {PLAYER} Inventory[{{id:"{STAFF}",tag:{{{DATA}:{{{ENERGY_KEY}:{CHARGE}}}}}}}] '
                                f"if data block {CHARGER} {{EnergyStored:0}} unless data block {CHARGER} Items.Items[{{Slot:2}}]", results)
            stand(server, evidence, "tank", 3040.5)
            wait_signal(client, evidence, "fueled")
            server.assert_state("native_tank_fills_the_original_charged_staff_with_the_whole_tagged_batch",
                                f'if data entity {PLAYER} Inventory[{{id:"{STAFF}",tag:{{{DATA}:{{{FLUID_KEY}:{vapor(FLUID, FUEL, "infinity_rod")}}}}}}}] '
                                f"unless data block {TANK} Fluids.Tanks[0].Contents.FluidName", results)
            stand(server, evidence, "flight", STAFF_X)
            wait_signal(client, evidence, "rising")
            server.assert_state("the_original_crafted_staff_lifts_the_survival_player",
                                f"if entity @a[name={PLAYER},x=3042,y=82,z=10,dx=3,dy=14,dz=3]", results)
            wait_signal(client, evidence, "landed")
            server.assert_state("the_staff_spends_one_fe_per_mb_then_lands_and_preserves_the_exact_remainder",
                                f"{spent_staff()} {machine_remainders()}", results)
            (evidence / "server-done").touch()
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"Infinity Rod progression client failed; see {evidence}")
            for capture_result in json.loads(summary.read_text())["client"]["captures"].values():
                if digest(Path(capture_result["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                    raise AssertionError("Infinity Rod client and server Ender IO jars differ")
        finally:
            stop_client(client)
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def verify_player(directory, report):
    grains = report["grainsCollected"] - 4
    items = {INGOTS: 1, NUGGETS: 1, STAFF: 1}
    if grains:
        items[GRAINS] = grains
    if report["seedsCollected"]:
        items[SEEDS] = report["seedsCollected"]
    verify_saved_inventory(directory, report, expected=items)
    player, = (directory / WORLD / "playerdata").glob("*.dat")
    staff, = (entry for entry in read_data_file(player)["Inventory"] if entry["id"] == STAFF)
    data = staff["tag"]["enderio:data"]
    if data["enderio:energy"] != CHARGE - FUEL or data.get("enderio:item_fluid_content") or data.get("enderio:toggled"):
        raise AssertionError(f"Unexpected saved original Staff energy, fuel or toggle state: {data}")
    report["savedStaffData"] = data


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    for key in ("forge-server", "jar", "launch"):
        parser.add_argument(f"--{key}", type=Path, required=True)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}

    def fresh(server, results):
        setup(server, results)
        run_client(args, server, results, report)

    def reopened(server, results):
        server.assert_state("the_machine_balances_and_empty_vial_survive_restart", machine_remainders(), results)

    try:
        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", reopened)
        verify_player(directory, report)
        report["passed"] = True
    finally:
        result = directory / "infinity-rod-progression-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Infinity Rod progression passed={report['passed']}, {len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
