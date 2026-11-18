#!/usr/bin/env python3
"""Exercise the XP and Weather Obelisks in an isolated packaged Forge world."""

import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage


XP = "1570 80 8"
XP_SUPPLY = "1571 80 8"
WEATHER = "1573 80 8"
WEATHER_SUPPLY = "1574 80 8"
ROOF = "1573 83 8"
LEGACY_XP = "1569 80 5"
LEGACY_BARREL = "1570 80 5"
REPLANTED = "1570 80 11"
PLAYER = "EnderIOQA"
XP_FLUID = "enderio:fluid_xp_juice_still"
RAIN = "enderio:fluid_cloud_seed_still"
THUNDER = "enderio:fluid_cloud_seed_concentrated_still"
CLEAR = "enderio:fluid_liquid_sunshine_still"
ROCKETS = 'Items:{Items:[{Slot:0,id:"minecraft:firework_rocket",Count:2}]}'


def fluid_contents(fluid, amount, tag=""):
    return f'Fluids:{{Tanks:[{{Index:0,Contents:{{FluidName:"{fluid}",Amount:{amount}{tag}}}}}]}}'


def supply(server, pos, fluid, amount, tag=""):
    server.command(f"setblock {pos} enderio:fluid_tank[facing=south]")
    # Local east on the south-facing tank points west into the obelisk.
    server.command(f'data merge block {pos} {{{fluid_contents(fluid, amount, tag)},IOConfig:{{Modes:{{east:"push"}}}}}}')


def force_chunks(server):
    server.command("forceload add 1568 0 1583 15")


def write_predicates(directory):
    pack = directory / WORLD / "datapacks/port-obelisks"
    (pack / "data/port_qa/predicates").mkdir(parents=True)
    (pack / "pack.mcmeta").write_text('{"pack":{"pack_format":15,"description":"Port obelisk weather predicates"}}\n')
    for name, raining, thundering in (("clear", False, False), ("rain", True, False), ("lightning", True, True)):
        predicate = {"condition": "minecraft:weather_check", "raining": raining, "thundering": thundering}
        (pack / f"data/port_qa/predicates/{name}.json").write_text(json.dumps(predicate) + "\n")


def xp_state(amount, pos=XP, tag=""):
    return f'if data block {pos} {{{fluid_contents(XP_FLUID, amount, tag)}}}'


def weather_state(fluid, amount, rockets):
    return (f'if data block {WEATHER} {{{fluid_contents(fluid, amount)},'
            f'Items:{{Items:[{{Slot:0,id:"minecraft:firework_rocket",Count:{rockets}}}]}}}}')


def task_state(recipe, progress):
    return (f'if data block {WEATHER} {{Task:{{RecipeId:"enderio:{recipe}",'
            f'ProgressRequired:600,ProgressMade:{progress}}}}}')


def legacy_fixture(server, results):
    force_chunks(server)
    server.assert_state("legacy_obelisk_chunk_loaded", f"if loaded {LEGACY_XP}", results)
    server.command(f"setblock {LEGACY_XP} enderio:xp_obelisk[facing=south]")
    legacy = ('Fluid:{Tanks:[{Index:0,FluidName:"enderio:xp_juice",Amount:1573,Tag:{PortFixture:72}}]},'
              'IoConfig:{"2":4b},RedstoneControl:3')
    server.command(f'data merge block {LEGACY_XP} {{{legacy}}}')
    server.command(f"setblock {LEGACY_BARREL} minecraft:barrel")
    server.command(f"loot replace block {LEGACY_BARREL} container.0 mine {LEGACY_XP} minecraft:diamond_pickaxe")
    server.command(f"data get block {LEGACY_XP}")
    server.command(f"data get block {LEGACY_BARREL} Items")
    server.assert_state("official_62_saves_placed_and_carried_tagged_experience",
                        f'if data block {LEGACY_XP} {{{legacy}}} if data block {LEGACY_BARREL} '
                        f'Items[{{Slot:0b,id:"enderio:xp_obelisk",tag:{{BlockEntityTag:{{{legacy}}}}}}}]', results)


def prepare_fresh(server, results, legacy):
    force_chunks(server)
    server.assert_state("test_chunk_loaded", f"if loaded {XP} if loaded {WEATHER}", results)
    if legacy:
        server.assert_state("placed_62_obelisk_retains_tagged_fluid_and_modes",
                            xp_state(1573, LEGACY_XP, ',Tag:{PortFixture:72}') +
                            f' if data block {LEGACY_XP} {{IOConfig:{{Modes:{{north:"disable"}}}},RedstoneControl:"never_active"}}', results)
    server.command("fill 1567 79 5 1577 79 13 minecraft:stone")
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    server.command("setworldspawn 1571 80 10")
    server.command(f"setblock {XP} enderio:xp_obelisk[facing=south]")
    supply(server, XP_SUPPLY, XP_FLUID, 27, ',Tag:{PortFixture:72}')
    server.assert_state("forge_tank_feeds_twenty_seven_tagged_xp_mb",
                        xp_state(27, tag=',Tag:{PortFixture:72}') +
                        f" unless data block {XP_SUPPLY} Fluids.Tanks[0].Contents.FluidName", results)
    supply(server, XP_SUPPLY, "minecraft:water", 100)

    server.command("weather clear 10000")
    server.command(f"setblock {WEATHER} enderio:weather_obelisk[facing=south]")
    supply(server, WEATHER_SUPPLY, RAIN, 1237)
    server.assert_state("water_is_rejected_while_weather_tank_supplies_cloud_seed",
                        xp_state(27, tag=',Tag:{PortFixture:72}') + f' if data block {XP_SUPPLY} '
                        f'{{{fluid_contents("minecraft:water", 100)}}} if data block {WEATHER} {{{fluid_contents(RAIN, 1237)}}}'
                        f' unless data block {WEATHER_SUPPLY} Fluids.Tanks[0].Contents.FluidName', results)
    server.command(f"setblock {WEATHER_SUPPLY} minecraft:air")
    server.command(f"setblock {XP_SUPPLY} minecraft:air")
    server.assert_state("rain_task_waits_for_rocket",
                        f'{task_state("rain", 0)} if predicate port_qa:clear', results)
    server.command(f'data merge block {WEATHER} {{{ROCKETS}}}')
    # Wait for real ticks in the open, then pause the in-flight job under a roof.
    for _ in range(40):
        data = server.command(f"data get block {WEATHER} Task.ProgressMade")
        match = re.search(r"following block data: (\d+)", data)
        if match and int(match.group(1)) >= 20:
            break
    else:
        raise AssertionError("Weather Obelisk never made its first twenty rain ticks")
    server.command(f"setblock {ROOF} minecraft:stone")
    paused = re.search(r"following block data: (\d+)", server.command(f"data get block {WEATHER} Task.ProgressMade"))
    if not paused:
        raise AssertionError("Weather Obelisk lost its in-flight rain task when roofed")
    progress = int(paused.group(1))
    server.assert_state("roof_preserves_paid_ticks_fuel_and_rockets", f'{task_state("rain", progress)} '
                        f'{weather_state(RAIN, 1237, 2)} if predicate port_qa:clear', results)
    return progress


def finish_weather(server, results, mode, fuel):
    complete = f'{weather_state(fuel, 237, 1)} unless data block {WEATHER} Task'
    server.assert_state(f"{mode}_launches_firework_and_consumes_exact_inputs",
                        f'{complete} if entity @e[type=minecraft:firework_rocket,x=1573.5,y=81,z=8.5,distance=..25]',
                        results, timeout=58)
    server.assert_state(f"{mode}_applies_published_weather", f"{complete} if predicate port_qa:{mode}", results, timeout=12)


def run_weather_recipe(server, results, mode, fluid):
    server.command(f"setblock {WEATHER} minecraft:air")
    server.command(f"setblock {WEATHER} enderio:weather_obelisk[facing=south]")
    server.command(f'data merge block {WEATHER} {{{ROCKETS}}}')
    supply(server, WEATHER_SUPPLY, fluid, 1237)
    server.assert_state(f"{mode}_accepts_the_published_recipe_fluid", weather_state(fluid, 1237, 2), results)
    server.command(f"setblock {WEATHER_SUPPLY} minecraft:air")
    finish_weather(server, results, mode, fluid)


def verify_client(args, server, report):
    results = report["assertions"]
    output = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "obelisks", "--output", str(output), "--server", f"127.0.0.1:{args.port}", "--timeout", "225"]
    with (server.directory / "obelisks-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            server.command(f"experience add {PLAYER} 5 points")
            wait_signal(client, output, "xp-round-trip")
            server.assert_state("native_xp_menu_preserves_total_fluid_and_player_points",
                                xp_state(7, tag=',Tag:{PortFixture:72}') +
                                f' if data entity {PLAYER} {{XpTotal:6,XpLevel:0}}', results)
            server.command(f"experience add {PLAYER} 210 points")
            server.command(f"experience set {PLAYER} 12 levels")
            server.command(f"experience set {PLAYER} 0 points")
            server.assert_state("cross_level_fixture_starts_on_the_exact_level_threshold",
                                f'if data entity {PLAYER} {{XpTotal:216,XpLevel:12,XpP:0.0f}}', results)
            (output / "server-xp-tens").touch()
            wait_signal(client, output, "xp-tens")
            server.assert_state("cross_level_xp_menu_preserves_all_points_and_fluid",
                                xp_state(7, tag=',Tag:{PortFixture:72}') +
                                f' if data entity {PLAYER} {{XpTotal:216,XpLevel:12}}', results)
            if args.legacy_jar:
                server.command(f"item replace entity {PLAYER} hotbar.0 from block {LEGACY_BARREL} container.0")
                server.command(f"item replace block {LEGACY_BARREL} container.0 with minecraft:air")
                (output / "server-place-legacy").touch()
                wait_signal(client, output, "legacy-placed")
                server.command(f"data get block {REPLANTED}")
                server.assert_state("survival_replant_retains_legacy_fluid_and_io",
                                    xp_state(1573, REPLANTED, ',Tag:{PortFixture:72}') +
                                    f' if data block {REPLANTED} {{IOConfig:{{Modes:{{north:"disable"}}}},RedstoneControl:"never_active"}}'
                                    f' unless data entity {PLAYER} Inventory[{{id:"enderio:xp_obelisk"}}]', results)
            (output / "server-open-weather").touch()
            wait_signal(client, output, "weather-open")
            server.command(f"setblock {ROOF} minecraft:air")
            finish_weather(server, results, "rain", RAIN)
            (output / "server-rain").touch()
            if client.wait(timeout=20) != 0:
                raise AssertionError(f"Obelisk client failed; see {output}")
            summary = json.loads((output / "summary.json").read_text())
            frame = summary["client"]["captures"]["weather-rain"]
            if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Client and server Ender IO jars differ")
        finally:
            stop_client(client)
            summary_file = output / "summary.json"
            report["client"] = json.loads(summary_file.read_text()) if summary_file.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--legacy-jar", type=Path)
    parser.add_argument("--launch", type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    directory.mkdir()
    port = directory / "port"
    prepare_profile(args, port, args.jar)
    report = {"passed": False, "jarSha256": digest(port / "mods/enderio.jar"), "assertions": [], "servers": []}
    try:
        if args.legacy_jar:
            legacy = directory / "legacy"
            prepare_profile(args, legacy, args.legacy_jar)
            report["legacySha256"] = digest(legacy / "mods/enderio.jar")
            run_stage(args, legacy, report, "legacy", legacy_fixture, startup_timeout=240)
            shutil.copytree(legacy / WORLD, port / WORLD)
        write_predicates(port)

        def fresh(server, results):
            report["pausedProgress"] = prepare_fresh(server, results, args.legacy_jar)

        def reopened(server, results):
            server.command(f"data get block {WEATHER}")
            server.assert_state("paid_rain_ticks_are_restored_after_restart",
                                f'{task_state("rain", report["pausedProgress"])} {weather_state(RAIN, 1237, 2)}', results)
            if args.launch:
                verify_client(args, server, report)
            else:
                server.command(f"setblock {ROOF} minecraft:air")
                finish_weather(server, results, "rain", RAIN)
            run_weather_recipe(server, results, "lightning", THUNDER)
            run_weather_recipe(server, results, "clear", CLEAR)

        def settled(server, results):
            server.assert_state("finished_clear_recipe_survives_final_restart",
                                f'{weather_state(CLEAR, 237, 1)} if predicate port_qa:clear', results)
            if args.launch:
                server.assert_state("xp_dust_survives_final_restart", xp_state(7, tag=',Tag:{PortFixture:72}'), results)
                if args.legacy_jar:
                    server.assert_state("replanted_legacy_fluid_survives_restart",
                                        xp_state(1573, REPLANTED, ',Tag:{PortFixture:72}'), results)

        run_stage(args, port, report, "converted" if args.legacy_jar else "fresh", fresh, startup_timeout=240)
        run_stage(args, port, report, "reopened", reopened, startup_timeout=240)
        run_stage(args, port, report, "settled", settled, startup_timeout=240)
        report["passed"] = True
    finally:
        output = directory / "obelisk-results.json"
        output.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Obelisks passed={report['passed']}, {len(report['assertions'])} assertions, {output}", flush=True)


if __name__ == "__main__":
    main()
