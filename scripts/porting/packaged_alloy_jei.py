#!/usr/bin/env python3
"""Transfer sized Alloy Smelter recipes in JEI and verify actual Forge processing."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys
import time

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_crafttweaker import check_script
from packaged_crafttweaker_stages import COMPANIONS
from packaged_glass_progression import Smelt, prepare_smelter
from packaged_legacy_painting import stack
from packaged_sag_progression import charge, feed, mill, output, ready_at
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage
from packaged_starter_progression import PLAYER, hopper_item


X = 3257
STOCK = (("minecraft:beetroot", 1), ("minecraft:clay_ball", 3), ("minecraft:egg", 6))
ORIGINAL = (("minecraft:amethyst_shard", 1), ("minecraft:stick", 2))
RELOADED = (("minecraft:paper", 4), ("minecraft:kelp", 2), ("minecraft:amethyst_shard", 3))
RECIPE_ID = "port_alloy:alloy_smelting/sized"
RESULT = "minecraft:quartz"
INPUT_TAG = "{PortAlloy:223}"
RESULT_TAG = '{PortAlloyResult:"sized"}'
CAPACITOR = stack(4, 1, "enderio:octadic_capacitor")


def recipe_definition(reloaded):
    ingredients = RELOADED if reloaded else ORIGINAL
    result = {"id": RESULT, "count": 2, "tag": {"PortAlloyResult": "sized"}} if reloaded else {
        "id": "minecraft:prismarine_crystals", "count": 1,
    }
    return json.dumps({"type": "enderio:alloy_smelting", "energy": 1800 if reloaded else 2400,
                       "inputs": [{"item": item, "count": count} for item, count in ingredients],
                       "experience": 0.0, "output": result})


def write_recipe(path, reloaded=False):
    path.write_text(recipe_definition(reloaded) + "\n")


def script_replacement(server):
    script = server.directory / "scripts/alloy_jei.zs"
    script.parent.mkdir(exist_ok=True)
    script.write_text(f'''import mods.enderio.EnderIOStages;

<recipetype:enderio:alloy_smelting>.replaceRecipeJson("{RECIPE_ID}", {json.dumps(recipe_definition(True))});
EnderIOStages.requireRecipe(<resource:{RECIPE_ID}>, "jei_smelting");
println("PORT_CT_JEI_REPLACED");
''')
    server.command(f"data modify block {mill(X)} owner set from entity {PLAYER} UUID")


def check_scripted_transfer(server, results):
    check_script(server, "PORT_CT_JEI_REPLACED")
    server.command(f"execute as {PLAYER} run ct recipes manager <recipetype:enderio:alloy_smelting>")
    log = (server.directory / "logs/crafttweaker.log").read_text()
    dump = '<recipetype:enderio:alloy_smelting>.addRecipeJson("alloy_smelting/sized",'
    if not any(dump in line and "PortAlloyResult" in line and "1800" in line for line in log.splitlines()):
        raise AssertionError("CraftTweaker's recipe dump did not contain the scripted native fields")
    results.append({"assertion": "crafttweaker_command_dumps_the_replacement_with_native_fields", "passed": True})
    charge(server, X, 537)
    time.sleep(0.55)
    server.assert_state("jei_transfer_keeps_the_recipe_visible_but_cannot_bypass_the_owner_stage",
                        f"if data block {mill(X)} {{EnergyStored:537,Task:{{ProgressMade:0,HasDeterminedOutputs:0b}}}} "
                        f"{stocked_inputs(RELOADED)} unless data block {output(X)} Items[1]", results)
    charge(server, X, 0)
    server.command(f"gamestage add {PLAYER} jei_smelting")


def load_fixture(server, results):
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    server.command("forceload add 3248 0 3263 15")
    server.assert_state("alloy_fixture_chunk_loaded", f"if loaded {mill(X)}", results)
    server.command("fill 3252 79 5 3261 79 15 minecraft:stone_bricks")
    server.command("setworldspawn 3257 80 12")
    prepare_smelter(server, Smelt(X, 8, "", 0, 0))


def stocked_inputs(ingredients):
    items = ",".join(stack(i, count, item, metadata=INPUT_TAG) for i, (item, count) in enumerate(ingredients))
    return f'if data block {mill(X)} {{Items:{{Items:[{items},{CAPACITOR}]}}}}'


def complete_state(reloaded=False):
    pos = mill(X)
    empty = " ".join(f"unless data block {pos} Items.Items[{{Slot:{slot}}}]" for slot in range(4))
    state = (f'if data block {pos} {{EnergyStored:0,Items:{{Items:[{CAPACITOR}]}}}} '
             f'unless data block {pos} Task {empty} {hopper_item(output(X), "minecraft:red_dye", 12)}')
    if reloaded:
        result = stack(1, 2, RESULT, metadata=RESULT_TAG, slot_suffix="b", count_suffix="b")
        state += f" if data block {output(X)} Items[{result}] unless data block {output(X)} Items[2]"
    return state


def verify_transfer(server, results, stage, ingredients, *, player_transfer):
    conditions = stocked_inputs(ingredients)
    if player_transfer:
        retained = ",".join(f'{{id:"{item}",Count:1b,tag:{INPUT_TAG}}}' for item, _ in ingredients)
        conditions += f" if data entity {PLAYER} {{Inventory:[{retained}]}}"
    else:
        conditions += f" unless data block {feed(X)} Items[0]"
    server.assert_state(f"{stage}_inserts_exact_sized_stacks_with_tags", conditions, results)


def process(server, results, *, reloaded=False):
    if reloaded:
        charge(server, X, 500)
        expected_output = f'{{Item:{{id:"{RESULT}",Count:2,tag:{RESULT_TAG}}}}}'
        server.assert_state("reloaded_job_uses_the_new_recipe_id_cost_and_tagged_result",
                            f'if data block {mill(X)} {{EnergyStored:0,Task:{{RecipeId:"{RECIPE_ID}",ProgressMade:500,'
                            f'ProgressRequired:1800,Outputs:[{expected_output}]}}}} {stocked_inputs(RELOADED)}', results)
        charge(server, X, 1300)
    else:
        charge(server, X, 1600)
    label = "reloaded" if reloaded else "stock"
    server.assert_state(f"{label}_uses_exact_energy_and_exports_its_original_output", complete_state(reloaded), results)


def supply_player(server, ingredients):
    for slot, (item, count) in enumerate(reversed(ingredients), 1):
        server.command(f"item replace entity {PLAYER} hotbar.{slot} with {item}{INPUT_TAG} {count + 1}")


def supply_machine(server, ingredients):
    for slot, (item, count) in enumerate(ingredients):
        server.command(f"item replace block {feed(X)} container.{slot} with {item}{INPUT_TAG} {count}")


def run_client(args, server, report, recipe):
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    summary_path = evidence / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "alloy-jei", "--server", f"127.0.0.1:{args.port}", "--output", str(evidence), "--timeout", "200"]
    results = report["assertions"]
    with (server.directory / "alloy-jei-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            supply_player(server, STOCK)
            ready_at(server, X, "stock", evidence)
            wait_signal(client, evidence, "stock-transferred")
            verify_transfer(server, results, "stock", STOCK, player_transfer=True)
            process(server, results)

            if args.crafttweaker_mod_dir:
                script_replacement(server)
            else:
                write_recipe(recipe, reloaded=True)
            server.command("reload")
            supply_player(server, RELOADED)
            ready_at(server, X, "reloaded", evidence)
            wait_signal(client, evidence, "reloaded-transferred")
            verify_transfer(server, results, "reloaded", RELOADED, player_transfer=True)
            if args.crafttweaker_mod_dir:
                check_scripted_transfer(server, results)
            process(server, results, reloaded=True)
            (evidence / "server-finished").touch()
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"Alloy JEI client failed; see {evidence}")
            loaded = json.loads(summary_path.read_text())["client"]["captures"]["reloaded-alloy-jei"]["loadedMods"]
            for mod, expected in report["jars"].items():
                if digest(Path(loaded[mod]["file"])) != expected:
                    raise AssertionError(f"The client and server loaded different {mod} jars")
        finally:
            stop_client(client)
            report["client"] = json.loads(summary_path.read_text()) if summary_path.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for path in ("java", "forge-server", "jar"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    parser.add_argument("--jei", type=Path)
    parser.add_argument("--launch", type=Path)
    parser.add_argument("--crafttweaker-mod-dir", type=Path)
    args = parser.parse_args()
    if bool(args.jei) != bool(args.launch):
        parser.error("provide --jei and --launch together for the packaged client scenario")
    if args.crafttweaker_mod_dir and not args.launch:
        parser.error("provide --launch to test CraftTweaker's live JEI recipes")
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    jars = {"enderio": digest(args.jar)}
    if args.jei:
        shutil.copyfile(args.jei, directory / "mods/jei.jar")
        jars["jei"] = digest(args.jei)
    if args.crafttweaker_mod_dir:
        for name in COMPANIONS[:-1]:
            shutil.copyfile(args.crafttweaker_mod_dir / name, directory / "mods" / name)
    pack = directory / WORLD / "datapacks/port_alloy"
    recipe = pack / "data/port_alloy/recipes/alloy_smelting/sized.json"
    recipe.parent.mkdir(parents=True)
    (pack / "pack.mcmeta").write_text('{"pack":{"pack_format":15,"description":"Alloy JEI fixture"}}\n')
    write_recipe(recipe)
    report = {"passed": False, "assertions": [], "servers": [], "jars": jars}

    def fresh(server, results):
        load_fixture(server, results)
        if args.jei:
            run_client(args, server, report, recipe)
            return
        supply_machine(server, STOCK)
        verify_transfer(server, results, "stock", STOCK, player_transfer=False)
        process(server, results)
        write_recipe(recipe, reloaded=True)
        server.command("reload")
        supply_machine(server, RELOADED)
        verify_transfer(server, results, "reloaded", RELOADED, player_transfer=False)
        process(server, results, reloaded=True)

    try:
        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", lambda server, results: server.assert_state(
            "both_exact_outputs_and_capacitor_survive_restart", complete_state(reloaded=True), results))
        report["passed"] = True
    finally:
        path = directory / "alloy-jei-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Alloy JEI passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
