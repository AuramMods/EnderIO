#!/usr/bin/env python3
"""Run CraftTweaker scripts and native machine assertions on packaged Forge."""

import argparse
import json
from pathlib import Path
import shutil
import sys
import time

sys.dont_write_bytecode = True
from packaged_glass_progression import Smelt, finished_smelter, prepare_smelter, start_smelter, supply
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage


FIXTURES = Path(__file__).parent / "fixtures/crafttweaker"
PRIMITIVE = "3444 80 3"


def check_script(server, marker):
    log = (server.directory / "logs/crafttweaker.log").read_text()
    if marker not in log or any(level in log for level in ("[ERROR]", "[FATAL]")):
        raise AssertionError(f"CraftTweaker did not report {marker}; see {server.directory / 'logs/crafttweaker.log'}")


def alloys(server, results, *, powered_output, primitive_output):
    server.command("forceload add 3440 0")
    job = Smelt(3440, 3, powered_output, 2, 750 if powered_output == "amethyst_shard" else 1200)
    server.assert_state("crafttweaker_alloy_chunk_loaded", f"if loaded {PRIMITIVE}", results)
    prepare_smelter(server, job)
    supply(server, job, 0, "minecraft:lapis_lazuli", 2)
    supply(server, job, 1, "minecraft:quartz", 1)
    start_smelter(server, job)
    server.command(f"setblock {PRIMITIVE} enderio:primitive_alloy_smelter[facing=south]"
                   '{Items:{Size:5,Items:[{Slot:0,id:"minecraft:coal",Count:2},'
                   '{Slot:1,id:"minecraft:bamboo",Count:2},{Slot:2,id:"minecraft:slime_ball",Count:1}]}}')
    server.assert_state("powered_alloy_spends_exact_fe_and_inputs", finished_smelter(job, namespace="minecraft"),
                        results, timeout=25)
    server.assert_state("primitive_alloy_spends_one_coal_and_exact_inputs",
                        f'if data block {PRIMITIVE} {{BurnDuration:1600,Items:{{Items:'
                        f'[{{Slot:0,id:"minecraft:coal",Count:1}},{{Slot:4,id:"minecraft:{primitive_output}",Count:2}}]}}}} '
                        f'unless data block {PRIMITIVE} Task unless data block {PRIMITIVE} Items.Items[{{Slot:1}}] '
                        f'unless data block {PRIMITIVE} Items.Items[{{Slot:2}}]', results, timeout=25)


def removed(server, results):
    check_script(server, "PORT_CT_ALLOY_REMOVE_OK")
    server.command("forceload add 3440 0")
    job = Smelt(3440, 3, "amethyst_shard", 2, 0)
    server.assert_state("crafttweaker_removed_chunk_loaded", f"if loaded {PRIMITIVE}", results)
    prepare_smelter(server, job)
    start_smelter(server, job)
    supply(server, job, 0, "minecraft:lapis_lazuli", 2)
    supply(server, job, 1, "minecraft:quartz", 1)
    server.command(f"setblock {PRIMITIVE} enderio:primitive_alloy_smelter[facing=south]"
                   '{Items:{Size:5,Items:[{Slot:0,id:"minecraft:coal",Count:2},'
                   '{Slot:1,id:"minecraft:bamboo",Count:2},{Slot:2,id:"minecraft:slime_ball",Count:1}]}}')
    # Wait through multiple hopper transfer intervals before asserting the recipes did no work.
    time.sleep(1)
    server.assert_state("removed_recipe_keeps_inputs_and_spends_no_fuel_or_fe",
                        f'if data block {job.pos(80)} {{EnergyStored:137}} unless data block {job.pos(80)} Task '
                        f'if data block {job.pos(81)} {{Items:[{{Slot:0b,id:"minecraft:lapis_lazuli",Count:2b}}]}} '
                        f'if data block {job.pos(80)} {{Items:{{Items:[{{id:"minecraft:quartz",Count:1}}]}}}} '
                        f'unless data block {job.pos(79)} Items[0] '
                        f'if data block {PRIMITIVE} {{BurnDuration:0,Items:{{Items:[{{Slot:0,Count:2}},'
                        f'{{Slot:1,Count:2}},{{Slot:2,Count:1}}]}}}} unless data block {PRIMITIVE} Task', results)


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
    ct_jar = directory / "mods" / args.crafttweaker.name
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}
    try:
        run_stage(args, directory, report, "no_optional_mods", lambda *_: None)
        shutil.copyfile(args.crafttweaker, ct_jar)
        script = directory / "scripts/alloy.zs"
        script.parent.mkdir(exist_ok=True)
        fixture = (FIXTURES / script.name).read_text()
        script.write_text(fixture)

        def added(server, results):
            check_script(server, "PORT_CT_ALLOY_LOOKUP_OK")
            alloys(server, results, powered_output="amethyst_shard", primitive_output="glow_ink_sac")

        run_stage(args, directory, report, "added", added)
        script.write_text(fixture + """
powered.replaceRecipe("crafttweaker:qa_powered_alloy", <item:minecraft:prismarine_shard> * 2, 2400, 0.75f,
    <item:minecraft:lapis_lazuli> * 2, <item:minecraft:quartz>);
primitive.replaceRecipeJson("crafttweaker:qa_primitive_alloy",
    '{"inputs":[{"item":"minecraft:bamboo","count":2},{"item":"minecraft:slime_ball"}],"output":{"id":"minecraft:ink_sac","count":2},"energy":120,"experience":0.5}');
if powered.getRecipesByOutput(<item:minecraft:prismarine_shard>).length == 1
        && primitive.getRecipesByOutput(<item:minecraft:ink_sac>).length == 1 {
    println("PORT_CT_ALLOY_REPLACE_OK");
}
""")

        def replaced(server, results):
            check_script(server, "PORT_CT_ALLOY_REPLACE_OK")
            alloys(server, results, powered_output="prismarine_shard", primitive_output="ink_sac")

        run_stage(args, directory, report, "replaced", replaced)
        script.write_text(fixture + """
powered.removeByName("crafttweaker:qa_powered_alloy");
primitive.remove(<item:minecraft:glow_ink_sac>);
if powered.getRecipeByName("crafttweaker:qa_powered_alloy") == null
        && primitive.getRecipeByName("crafttweaker:qa_primitive_alloy") == null {
    println("PORT_CT_ALLOY_REMOVE_OK");
}
""")
        run_stage(args, directory, report, "removed", removed)
        report["passed"] = True
    finally:
        result = directory / "crafttweaker-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"CraftTweaker passed={report['passed']}, {result}", flush=True)


if __name__ == "__main__":
    main()
