#!/usr/bin/env python3
"""Test optional GameStages rules through real Ender IO machines and world fire."""

import argparse
import json
from pathlib import Path
import shutil
import sys
import time

sys.dont_write_bytecode = True
from packaged_crafttweaker import FIXTURES, check_script
from packaged_glass_progression import Smelt, finished_smelter, prepare_smelter, supply
from packaged_levitation_staff import game_tick
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage


COMPANIONS = (
    "CraftTweaker-forge-1.20.1-14.0.59.jar",
    "Bookshelf-Forge-1.20.1-20.2.15.jar",
    "GameStages-Forge-1.20.1-15.0.2.jar",
    "ItemStages-Forge-1.20.1-8.0.3.jar",
    "RecipeStages-8.0.0.2.jar",
    "jei-1.20.1-forge-15.20.0.129.jar",
)
ALLOY = Smelt(3802, 3, "amethyst_shard", 1, 1500)
PRIMITIVE = "3806 80 3"
FIRE_BASE, FIRE = "3810 80 3", "3810 81 3"
ALLOY_INPUTS = '[{Slot:0,id:"minecraft:lapis_lazuli",Count:2},{Slot:1,id:"minecraft:quartz",Count:1}]'
PRIMITIVE_INPUTS = ('[{Slot:0,id:"minecraft:coal",Count:2},'
                    '{Slot:1,id:"minecraft:bamboo",Count:2},{Slot:2,id:"minecraft:slime_ball",Count:1}]')


def wait_fire_age(server):
    target = game_tick(server) + 275
    deadline = time.monotonic() + 50
    while game_tick(server) < target:
        if time.monotonic() > deadline:
            raise AssertionError("The server did not tick past the fire crafting age")
        time.sleep(0.2)


def locked(server, results):
    check_script(server, "PORT_CT_STAGES_CONFIGURED")
    server.command("forceload add 3792 0 3823 15")
    server.assert_state("stage_fixture_chunks_loaded", f"if loaded {ALLOY.pos(80)} if loaded {FIRE}", results)
    server.command("gamerule doFireTick false")
    server.command("gamerule doMobSpawning false")
    server.command("fill 3807 79 0 3812 79 6 minecraft:stone")
    prepare_smelter(server, ALLOY)
    # Hopper delivery still works. The stage check runs before work, FE, and extraction.
    supply(server, ALLOY, 0, "minecraft:lapis_lazuli", 2)
    supply(server, ALLOY, 1, "minecraft:quartz", 1)
    server.command(f"data merge block {ALLOY.pos(80)} {{EnergyStored:1637}}")
    server.command(f'setblock {PRIMITIVE} enderio:primitive_alloy_smelter[facing=south]'
                   f'{{Items:{{Items:{PRIMITIVE_INPUTS}}}}}')
    server.command(f"setblock {FIRE_BASE} minecraft:emerald_block")
    server.command(f"setblock {FIRE} minecraft:fire")
    wait_fire_age(server)
    server.assert_state("exact_recipe_stage_holds_unowned_alloy_inputs_and_fe",
                        f'if data block {ALLOY.pos(80)} {{EnergyStored:1637,Items:{{Items:{ALLOY_INPUTS}}}}} '
                        f'unless data block {ALLOY.pos(79)} Items[0]', results)
    server.assert_state("family_stage_consumes_no_new_fuel",
                        f'if data block {PRIMITIVE} {{BurnDuration:0,Items:{{Items:{PRIMITIVE_INPUTS}}}}}', results)
    server.assert_state("ungranted_world_stage_prevents_fire_crafting",
                        f"if block {FIRE_BASE} minecraft:emerald_block if block {FIRE} minecraft:fire", results)


def unlocked(server, results):
    check_script(server, "PORT_CT_STAGES_CONFIGURED")
    server.command("forceload add 3792 0 3823 15")
    # Relight the old untracked fire after granting the world stage.
    server.command(f"setblock {FIRE} minecraft:air")
    server.command(f"setblock {FIRE} minecraft:fire")
    server.assert_state("cleared_stage_resumes_existing_alloy_inputs",
                        finished_smelter(ALLOY, namespace="minecraft"), results, timeout=30)
    server.assert_state("cleared_family_stage_runs_primitive_alloy_once",
                        f'if data block {PRIMITIVE} {{BurnDuration:1600,Items:{{Items:['
                        '{Slot:0,id:"minecraft:coal",Count:1},{Slot:4,id:"minecraft:glow_ink_sac",Count:1}]}} '
                        f'unless data block {PRIMITIVE} Task', results, timeout=30)
    server.assert_state("explicit_world_stage_unlocks_actorless_fire_crafting",
                        f"if block {FIRE_BASE} minecraft:diamond_block if block {FIRE} minecraft:air", results, timeout=35)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    for key in ("forge-server", "jar", "mod-dir"):
        parser.add_argument(f"--{key}", type=Path, required=True)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    directory.parent.mkdir(parents=True, exist_ok=True)
    prepare_profile(args, directory, args.jar)
    for name in COMPANIONS:
        shutil.copyfile(args.mod_dir / name, directory / "mods" / name)
    scripts = directory / "scripts"
    scripts.mkdir()
    source = (FIXTURES / "stage_processing.zs").read_text()
    script = scripts / "stage_processing.zs"
    fire = """
import mods.enderio.FireOutput;
<recipetype:enderio:fire_crafting>.addRecipe("qa_staged_fire", [<block:minecraft:emerald_block>], [],
    [<resource:minecraft:overworld>], <block:minecraft:diamond_block>,
    FireOutput.item(<item:minecraft:rabbit_hide>, 1, 1, 1.0f));
"""
    (scripts / "stage_fire.zs").write_text(fire)
    script.write_text(source)
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}
    try:
        run_stage(args, directory, report, "locked", locked)
        script.write_text(source + """
EnderIOStages.clearRecipe(<resource:crafttweaker:qa_stage_alloy>);
EnderIOStages.clearRecipeType(<resource:enderio:primitive_alloy_smelting>);
EnderIOStages.setWorldStage("fire_works", true);
""")
        run_stage(args, directory, report, "unlocked", unlocked)
        report["passed"] = True
    finally:
        result = directory / "crafttweaker-stages-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"CraftTweaker stages passed={report['passed']}, {result}", flush=True)


if __name__ == "__main__":
    main()
