#!/usr/bin/env python3
"""Exercise Forge furnace inheritance before and after a live CraftTweaker reload."""

import argparse
import json
from pathlib import Path
import shutil
import sys
import time

sys.dont_write_bytecode = True
from packaged_crafttweaker import FIXTURES, check_script
from packaged_glass_progression import Smelt, finished_smelter, hopper_item, prepare_smelter, start_smelter, supply
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage


def install_datapack(directory):
    pack = directory / WORLD / "datapacks/furnace_fixture"
    recipe_dir = pack / "data/crafttweaker/recipes"
    recipe_dir.mkdir(parents=True)
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 15, "description": "Furnace inheritance fixture"}}))
    (recipe_dir / "qa_furnace_override.json").write_text(json.dumps({
        "type": "minecraft:smelting",
        "ingredient": {"item": "minecraft:feather"},
        "result": "minecraft:gold_nugget",
        "experience": 0.5,
        "cookingtime": 60,
    }))
    # An increment from Minecraft's load function confirms that the reloaded resources were installed.
    load_functions = pack / "data/minecraft/tags/functions"
    load_functions.mkdir(parents=True)
    (load_functions / "load.json").write_text(json.dumps({"values": ["crafttweaker:qa_loaded"]}))
    functions = pack / "data/crafttweaker/functions"
    functions.mkdir()
    (functions / "qa_loaded.mcfunction").write_text(
        "scoreboard objectives add furnace_qa dummy\nscoreboard players add #loads furnace_qa 1\n")


def exercise(server, results, rows, z):
    server.command("forceload add 3472 0 3499 15")
    server.assert_state("furnace_inheritance_test_chunks_loaded", "if loaded 3472 80 3 if loaded 3499 80 3", results)
    inactive = []
    for index, (ingredient, output) in enumerate(rows):
        job = Smelt(3472 + 4 * index, z, output, 1, 1500)
        prepare_smelter(server, job, mode=0)
        supply(server, job, 0, f"minecraft:{ingredient}", 1)
        start_smelter(server, job)
        if output:
            server.assert_state(f"furnace_inheritance_{ingredient}_produces_{output}",
                                finished_smelter(job, namespace="minecraft"), results, timeout=25)
        else:
            inactive.append((ingredient, job))
    time.sleep(1)
    for ingredient, job in inactive:
        pos = job.pos(80)
        server.assert_state(f"furnace_inheritance_{ingredient}_stays_inactive",
                            f"if data block {pos} {{EnergyStored:1637}} unless data block {pos} Task "
                            f"unless data block {job.pos(79)} Items[0] "
                            f"{hopper_item(job.pos(81), 'minecraft:' + ingredient, 1)}", results)


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
    install_datapack(directory)
    shutil.copyfile(args.crafttweaker, directory / "mods" / args.crafttweaker.name)
    script = directory / "scripts/furnace.zs"
    script.parent.mkdir()
    fixture = (FIXTURES / script.name).read_text()
    script.write_text(fixture)

    def scenario(server, results):
        check_script(server, "PORT_CT_FURNACE_RULES_OK")
        exercise(server, results, (("wheat", "amethyst_shard"), ("feather", "emerald"),
                                   ("fern", None), ("potato", None), ("raw_gold", None)), 3)
        script.unlink()
        server.command("reload")
        server.assert_state("live_reload_installed_the_rebuilt_recipe_manager",
                            "if score #loads furnace_qa matches 2", results, timeout=45)
        exercise(server, results, (("wheat", None), ("feather", "gold_nugget"),
                                   ("potato", "baked_potato"), ("raw_gold", "gold_ingot")), 7)

    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}
    try:
        run_stage(args, directory, report, "furnace", scenario)
        report["passed"] = True
    finally:
        result = directory / "crafttweaker-furnace-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"CraftTweaker furnace passed={report['passed']}, {result}", flush=True)


if __name__ == "__main__":
    main()
