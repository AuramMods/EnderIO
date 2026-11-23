#!/usr/bin/env python3
"""Verify CraftTweaker soul rules and optional loading on a packaged server."""

import argparse
import json
from pathlib import Path
import shutil
import sys

sys.dont_write_bytecode = True
from packaged_crafttweaker import FIXTURES, check_script
from packaged_farming_station_souls import PLOT, STATION, prepare_rules, wheat_harvest
from packaged_server_smoke import ROOT, digest
from packaged_soul_engine_fuels import exhausted_engine, seed_engine, supply_fuel
from packaged_spawner_rules import place_powered
from packaged_spawners import prepare_profile, run_stage


SPAWNER, ENGINE, TANK, SOLAR, CONTROL = ("3880 80 8", "3884 80 8", "3885 80 8", "3893 80 8", "3899 80 8")


def exercise(server, results):
    check_script(server, "PORT_CT_SOUL_RULES_CONFIGURED")
    server.command("forceload add 3872 0 3903 15")
    server.command("forceload add 1632 0 1647 15")
    server.assert_state("soul_rule_test_chunks_loaded", f"if loaded {ENGINE} if loaded {PLOT} if loaded {SOLAR}", results)
    server.command("gamerule doMobSpawning false")
    server.command("gamerule randomTickSpeed 0")
    server.command("gamerule doDaylightCycle false")
    server.command("time set midnight")
    server.command("fill 3872 79 0 3903 79 15 minecraft:stone")
    server.command("fill 1633 79 2 1645 79 10 minecraft:stone")
    server.command("setblock 1636 79 3 minecraft:water")

    place_powered(server, SPAWNER, "cow", 3600, '{Tags:["ct_copy_probe"],NoAI:1b}')
    seed_engine(server, ENGINE, "cow")
    server.command(f"setblock {TANK} enderio:fluid_tank[facing=south]")
    supply_fuel(server, TANK, "minecraft:water")
    for pos in (SOLAR, CONTROL):
        server.command(f"setblock {pos} enderio:vibrant_photovoltaic_module")
    server.command(f'data merge block {SOLAR} {{EntityStorage:{{entity_type:"minecraft:cow",entity_tag:{{}}}}}}')

    server.command(f"setblock {STATION} enderio:farming_station[facing=south]")
    server.command(f'data merge block {STATION} {{EntityStorage:{{entity_type:"minecraft:bee",entity_tag:{{}}}}}}')
    wheat_harvest(server, results, "farming_uses_scripted_fertilizer_and_seed_values", 3, 0)

    server.assert_state("scripted_spawner_charge_and_copy_mode_spawn_the_bound_cow",
                        f'if data block {SPAWNER} {{EnergyStored:0}} '
                        'if entity @e[type=minecraft:cow,tag=ct_copy_probe,x=3880,y=80,z=8,distance=..8]',
                        results, timeout=30)
    server.assert_state("scripted_engine_uses_tagged_fuel_and_exact_fe",
                        exhausted_engine(ENGINE, TANK, "cow", 394), results, timeout=15)
    server.command("scoreboard objectives add soul_solar dummy")
    server.command(f"execute store result score #bound soul_solar run data get block {SOLAR} EnergyStored")
    server.command(f"execute store result score #control soul_solar run data get block {CONTROL} EnergyStored")
    server.assert_state("scripted_solar_rule_generates_from_the_bound_cow_at_midnight",
                        "if score #bound soul_solar matches 1.. if score #control soul_solar matches 0", results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    for key in ("forge-server", "jar", "crafttweaker"):
        parser.add_argument(f"--{key}", type=Path, required=True)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    directory.parent.mkdir(parents=True, exist_ok=True)
    prepare_profile(args, directory, args.jar)
    prepare_rules(directory)
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}
    try:
        run_stage(args, directory, report, "no_integrations", lambda *_: None)
        shutil.copyfile(args.crafttweaker, directory / "mods" / args.crafttweaker.name)
        scripts = directory / "scripts"
        scripts.mkdir()
        script = scripts / "soul_rules.zs"
        source = (FIXTURES / script.name).read_text()
        script.write_text(source)
        run_stage(args, directory, report, "soul_rules", exercise)
        script.write_text(source + """
SoulRules.remove("spawner", cow);
SoulRules.remove("engine", cow);
SoulRules.remove("farm", bee);
SoulRules.remove("solar", cow);
if SoulRules.getJson("spawner", cow) == null && SoulRules.getJson("engine", cow) == null
        && SoulRules.getJson("farm", bee) == null && SoulRules.getJson("solar", cow) == null {
    println("PORT_CT_SOUL_RULES_REMOVED");
}
""")
        run_stage(args, directory, report, "removed", lambda server, _: check_script(server, "PORT_CT_SOUL_RULES_REMOVED"))
        report["passed"] = True
    finally:
        result = directory / "crafttweaker-souls-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"CraftTweaker soul rules passed={report['passed']}, {result}", flush=True)


if __name__ == "__main__":
    main()
