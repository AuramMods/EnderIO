#!/usr/bin/env python3
"""Use an original Ender Crystal to craft, place and run a Weather Obelisk."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_alloy_progression import verify_saved_inventory
from packaged_glass_progression import transfer
from packaged_obelisks import RAIN, fluid_contents, supply, task_state, weather_state, write_predicates
from packaged_server_smoke import ROOT, digest
from packaged_soul_binder import VIAL, item, xp_tank
from packaged_spawners import prepare_profile, run_stage
from packaged_starter_progression import PLAYER


BINDER, BINDER_OUTPUT = "3445 80 5", "3445 79 5"
VIAL_HOPPER, CRYSTAL_HOPPER, XP_TANK = "3444 80 5", "3446 80 5", "3445 80 4"
CHEST = "3453 80 8"
WEATHER, WEATHER_TANK, ROOF = "3457 80 8", "3458 80 8", "3457 83 8"
BLOCK = "enderio:weather_obelisk"
VIBRANT, ENDER = "enderio:vibrant_crystal", "enderio:ender_crystal"
RECIPE = "enderio:soulbinding/ender_crystal"
CAP = "enderio:octadic_capacitor"
SOUL = ('tag:{"enderio:data":{"enderio:soul":{entity_type:"minecraft:enderman",'
        'entity_tag:{Health:7.0f,Tags:["PortCrystal191"]}}}}')
FE_COST, FE_PAID, FE_LEFT = 76800, 12300, 317
XP_COST, XP_LEFT = 1440, 37
STOCK = (("enderio:pulsating_crystal", 2), (VIBRANT, 2), ("enderio:energetic_alloy_ingot", 2),
         ("enderio:soularium_ingot", 3), ("enderio:void_chassis", 1), ("minecraft:firework_rocket", 2))


def capture_soul(server, results):
    dispenser, power = "3441 80 5", "3441 80 4"
    target = "@e[type=minecraft:enderman,tag=PortCrystal191]"
    server.command(f'setblock {dispenser} minecraft:dispenser[facing=east]{{Items:[{item(0, VIAL, vanilla=True)}]}}')
    server.command('summon minecraft:enderman 3442.5 80 5.5 {Health:7.0f,NoAI:1b,Silent:1b,Tags:["PortCrystal191"]}')
    server.assert_state("a_live_enderman_is_in_the_capture_space", f"if entity {target}", results)
    server.command(f"setblock {power} minecraft:redstone_block")
    server.assert_state("a_native_dispenser_captures_the_enderman_in_the_original_vial",
                        f"unless entity {target} if data block {dispenser} Items[{item(0, VIAL, SOUL, vanilla=True)}]", results)
    server.command(f"setblock {power} minecraft:air")
    transfer(server, dispenser, 0, VIAL_HOPPER, 0)


def binding_inputs(energy, xp):
    return (f'if data block {BINDER} {{EnergyStored:{energy},{xp_tank(xp)},'
            f'Items:{{Items:[{item(0, VIAL, SOUL)},{item(1, VIBRANT)},{item(4, CAP)}]}}}} '
            f"unless data block {BINDER_OUTPUT} Items[0]")


def paused_binding():
    outputs = f'{{Item:{item(None, ENDER)}}},{{Item:{item(None, VIAL)}}}'
    return (f"{binding_inputs(0, XP_COST + XP_LEFT)} "
            f'if data block {BINDER} {{Task:{{RecipeId:"{RECIPE}",ProgressMade:{FE_PAID},ProgressRequired:{FE_COST},'
            f'HasDeterminedOutputs:1b,Outputs:[{outputs}]}}}} unless data block {BINDER} Task.Outputs[2]')


def ready_binder():
    slots = " ".join(f"unless data block {BINDER} Items.Items[{{Slot:{i}}}]" for i in range(4))
    return f"if data block {BINDER} {{EnergyStored:{FE_LEFT},{xp_tank(XP_LEFT)}}} unless data block {BINDER} Task {slots}"


def prepare(server, results):
    server.command("forceload add 3440 0 3471 15")
    server.assert_state("binder_and_workshop_chunks_are_loaded", f"if loaded {BINDER} if loaded {WEATHER}", results)
    for command in ("gamerule doMobSpawning false", "gamerule spawnRadius 0", "gamerule doDaylightCycle false",
                    "gamerule doLimitedCrafting true", "time set midnight", "weather clear 10000",
                    "fill 3440 79 4 3460 79 14 minecraft:stone", "setworldspawn 3452 80 12",
                    "setblock 3452 80 8 minecraft:crafting_table", f"setblock {CHEST} minecraft:chest[facing=south]",
                    f"setblock {ROOF} minecraft:stone"):
        server.command(command)
    server.command(f'setblock {BINDER} enderio:soul_binder[facing=south]'
                   f'{{EnergyStored:{FE_PAID},Items:{{Items:[{item(4, CAP)}]}},IOConfig:{{Modes:{{down:"push"}}}}}}')
    server.command(f"setblock {BINDER_OUTPUT} minecraft:hopper[facing=down]")
    server.command(f"setblock {VIAL_HOPPER} minecraft:hopper[facing=east]")
    server.command(f"setblock {CRYSTAL_HOPPER} minecraft:hopper[facing=west]{{Items:[{item(0, VIBRANT, vanilla=True)}]}}")
    server.command(f"setblock {XP_TANK} enderio:fluid_tank[facing=south]")
    server.command(f'data merge block {XP_TANK} {{{xp_tank(XP_COST - 1)},IOConfig:{{Modes:{{south:"push"}}}}}}')
    capture_soul(server, results)
    server.assert_state("the_original_inputs_wait_at_one_mb_below_the_xp_cost",
                        f"{binding_inputs(FE_PAID, XP_COST - 1)} unless data block {BINDER} Task "
                        f"unless data block {VIAL_HOPPER} Items[0] unless data block {CRYSTAL_HOPPER} Items[0]", results)
    server.command(f"data merge block {XP_TANK} {{{xp_tank(XP_LEFT + 1)}}}")
    server.assert_state("the_enderman_job_saves_the_exact_paid_fe_and_queued_outputs", paused_binding(), results)


def complete_binding(server, results):
    server.assert_state("the_enderman_job_restores_the_original_paid_state_after_restart", paused_binding(), results)
    server.command(f"data merge block {BINDER} {{EnergyStored:{FE_COST - FE_PAID + FE_LEFT}}}")
    only_empty_vial = f'Items[{{id:"{VIAL}"}}].tag."enderio:data"."enderio:soul"'
    server.assert_state("the_stock_recipe_spends_exact_xp_and_fe_and_exports_crystal_and_empty_vial",
                        f'{ready_binder()} if data block {BINDER_OUTPUT} {{Items:[{item(0, ENDER, vanilla=True)},'
                        f'{item(1, VIAL, vanilla=True)}]}} unless data block {BINDER_OUTPUT} {only_empty_vial} '
                        f"unless data block {BINDER_OUTPUT} Items[2]", results, timeout=35)
    transfer(server, BINDER_OUTPUT, 0, CHEST, 0)
    transfer(server, BINDER_OUTPUT, 1, CHEST, 1)
    for slot, (ingredient, count) in enumerate(STOCK, 2):
        server.command(f"item replace block {CHEST} container.{slot} with {ingredient} {count}")
    server.command("time set noon")


def completed_weather():
    return f"{weather_state(RAIN, 237, 1, pos=WEATHER)} unless data block {WEATHER} Task"


def run_client(args, server, results, report):
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    summary = evidence / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "weather-progression", "--server", f"127.0.0.1:{args.port}", "--output", str(evidence), "--timeout", "185"]
    with (server.directory / "weather-progression-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            server.command(f"tp {PLAYER} 3452.5 80 11.5 180 16")
            (evidence / "server-workshop").touch()
            wait_signal(client, evidence, "crafted")
            ingredients = (ENDER, "enderio:weather_crystal", *(item_id for item_id, _ in STOCK[:-1]))
            spent = " ".join(f'unless data entity {PLAYER} Inventory[{{id:"{ingredient}"}}]' for ingredient in ingredients)
            server.assert_state("the_original_ender_crystal_becomes_the_single_crafted_obelisk",
                                f'if data entity {PLAYER} Inventory[{item(None, BLOCK, vanilla=True)}] {spent} '
                                f"unless data block {CHEST} Items[0] unless data block {BINDER_OUTPUT} Items[0]", results)
            server.command(f"tp {PLAYER} 3457.5 80 11.5 180 13")
            (evidence / "server-place").touch()
            wait_signal(client, evidence, "placed")
            server.assert_state("the_survival_item_places_one_south_facing_obelisk",
                                f'if block {WEATHER} {BLOCK}[facing=south] unless data entity {PLAYER} Inventory[{{id:"{BLOCK}"}}]', results)
            supply(server, WEATHER_TANK, RAIN, 1237)
            server.assert_state("the_crafted_obelisk_accepts_cloud_seed_from_a_native_tank",
                                f'if data block {WEATHER} {{{fluid_contents(RAIN, 1237)}}} '
                                f"unless data block {WEATHER_TANK} Fluids.Tanks[0].Contents.FluidName", results)
            (evidence / "server-open").touch()
            wait_signal(client, evidence, "loaded")
            server.assert_state("a_roof_holds_the_original_rockets_and_cloud_seed_while_the_world_is_clear",
                                f'{weather_state(RAIN, 1237, 2, pos=WEATHER)} {task_state("rain", 0, pos=WEATHER)} '
                                f"if predicate port_qa:clear if block {ROOF} minecraft:stone", results)
            server.command(f"setblock {ROOF} minecraft:air")
            server.assert_state("the_crafted_obelisk_launches_the_native_firework_on_the_exact_input_budget",
                                f"{completed_weather()} if entity @e[type=minecraft:firework_rocket,x=3457.5,y=81,z=8.5,distance=..25]",
                                results, timeout=53)
            server.assert_state("the_crafted_obelisk_changes_clear_weather_to_rain",
                                f"{completed_weather()} if predicate port_qa:rain", results)
            (evidence / "server-rain").touch()
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"Weather progression client failed; see {evidence}")
            for capture in json.loads(summary.read_text())["client"]["captures"].values():
                if digest(Path(capture["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                    raise AssertionError("Weather client and server Ender IO jars differ")
        finally:
            stop_client(client)
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


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
    write_predicates(directory)
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}

    def resumed(server, results):
        complete_binding(server, results)
        run_client(args, server, results, report)

    def reopened(server, results):
        server.assert_state("the_crafted_obelisk_its_rocket_and_fluid_remainder_and_rain_survive_restart",
                            f"if block {WEATHER} {BLOCK}[facing=south] {completed_weather()} if predicate port_qa:rain", results)
        server.assert_state("the_soul_binder_keeps_the_exact_fe_and_xp_remainders_after_the_original_output_is_used",
                            f"{ready_binder()} unless data block {BINDER_OUTPUT} Items[0] unless data block {CHEST} Items[0]", results)

    try:
        run_stage(args, directory, report, "fresh", prepare)
        run_stage(args, directory, report, "resumed", resumed)
        run_stage(args, directory, report, "reopened", reopened)
        verify_saved_inventory(directory, report, expected={VIAL: 1})
        report["passed"] = True
    finally:
        result = directory / "weather-progression-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Weather progression passed={report['passed']}, {len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
