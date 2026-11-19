#!/usr/bin/env python3
"""Verify Slice'N'Splice's ordered JEI transfer and a live recipe replacement."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_legacy_painting import stack
from packaged_sag_progression import charge, mill, output, ready_at
from packaged_server_smoke import ROOT, digest
from packaged_slicer import AXE, BLOCK, CASES, COST, SHEARS
from packaged_spawners import WORLD, prepare_profile, run_stage
from packaged_starter_progression import PLAYER, hopper_item


SLICER = 3257
STOCK = CASES[0][2]
STOCK_OUTPUT = "enderio:z_logic_controller"
CUSTOM = ("minecraft:amethyst_shard", "minecraft:kelp", "minecraft:paper", "minecraft:coal", "minecraft:stick", "minecraft:copper_ingot")
CUSTOM_OUTPUT = "minecraft:quartz"
CUSTOM_ID = "port_slicer:slicing/ordered"
HARDWARE = "enderio:octadic_capacitor"
INPUT_TAG = '{PortSlicer:161}'
RESULT_TAG = '{PortSlicerResult:"ordered"}'


def recipe_file(path, updated=False):
    inputs = CUSTOM if updated else (CUSTOM[1], CUSTOM[0], CUSTOM[2], CUSTOM[4], CUSTOM[3], CUSTOM[5])
    result = ({"id": CUSTOM_OUTPUT, "count": 2, "tag": {"PortSlicerResult": "ordered"}} if updated
              else {"id": "minecraft:prismarine_crystals", "count": 1})
    path.write_text(json.dumps({"type": "enderio:slicing", "energy": 1800 if updated else 2400,
                                "inputs": [{"item": ingredient} for ingredient in inputs], "output": result}) + "\n")


def tools(wear):
    tool_stacks = (stack(6, 1, AXE, metadata=f'{{Damage:{7 + wear},PortSlicer:161}}'),
                   stack(7, 1, SHEARS, metadata=f'{{Damage:{11 + wear},PortSlicer:161}}'),
                   stack(9, 1, HARDWARE))
    return ",".join(tool_stacks)


def input_conditions(ingredients, wear):
    expected = ",".join(stack(i, 1, item, metadata=INPUT_TAG) for i, item in enumerate(ingredients))
    return f'if data block {mill(SLICER)} {{Items:{{Items:[{expected},{tools(wear)}]}}}}'


def completed_stock(wear, *, custom=False):
    pos = mill(SLICER)
    empty = " ".join(f'unless data block {pos} Items.Items[{{Slot:{slot}}}]' for slot in (0, 1, 2, 3, 4, 5, 8))
    condition = (f'if data block {pos} {{EnergyStored:0,Items:{{Items:[{tools(wear)}]}}}} '
                 f'unless data block {pos} Task {empty} {hopper_item(output(SLICER), STOCK_OUTPUT, 1)}')
    if custom:
        result = stack(1, 2, CUSTOM_OUTPUT, metadata=RESULT_TAG, slot_suffix="b", count_suffix="b")
        return f'{condition} if data block {output(SLICER)} Items[{result}]'
    return condition


def verify_and_run_stock(server, results):
    # Duplicated ingredients arrive from single tagged stacks. Keep one item of each type in the player's inventory.
    retained = ",".join(f'{{id:"{item}",Count:1b,tag:{INPUT_TAG}}}' for item in dict.fromkeys(STOCK))
    server.assert_state("stock_jei_transfer_preserves_slot_order_tags_and_all_surplus_ingredients",
                        f'{input_conditions(STOCK, 0)} if data entity {PLAYER} {{Inventory:[{retained}]}}', results)
    charge(server, SLICER, COST)
    server.assert_state("stock_recipe_uses_exactly_20000_fe_and_leaves_one_result_and_one_wear",
                        completed_stock(1), results, timeout=32)


def verify_and_run_custom(server, results):
    server.assert_state("jei_transfers_the_reloaded_custom_layout_in_exact_order_with_tags", input_conditions(CUSTOM, 1), results)
    charge(server, SLICER, 600)
    result = f'{{Item:{{id:"{CUSTOM_OUTPUT}",Count:2,tag:{RESULT_TAG}}}}}'
    server.assert_state("the_actual_reloaded_recipe_queues_two_tagged_items_for_1800_fe",
                        f'if data block {mill(SLICER)} {{EnergyStored:0,Task:{{RecipeId:"{CUSTOM_ID}",ProgressMade:600,'
                        f'ProgressRequired:1800,Outputs:[{result}]}}}} {input_conditions(CUSTOM, 1)}', results)
    charge(server, SLICER, 1200)
    server.assert_state("exact_remaining_fe_completes_the_custom_recipe_and_keeps_the_tag",
                        completed_stock(2, custom=True), results)


def run_client(args, server, report, recipe):
    results = report["assertions"]
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    server.command("forceload add 3248 0 3263 15")
    server.assert_state("slicer_test_chunk_is_loaded", f"if loaded {mill(SLICER)}", results)
    server.command("fill 3252 79 5 3261 79 15 minecraft:stone_bricks")
    server.command("setworldspawn 3257 80 12")
    server.command(f'setblock {mill(SLICER)} {BLOCK}[facing=south]{{IOConfig:{{Modes:{{down:"push"}}}},'
                   f'Items:{{Items:[{tools(0)}]}}}}')
    server.command(f"setblock {output(SLICER)} minecraft:hopper[facing=down]")

    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "slicer-jei", "--server", f"127.0.0.1:{args.port}", "--output", str(evidence), "--timeout", "175"]
    with (server.directory / "slicer-jei-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            for slot, item in enumerate((STOCK[3], STOCK[4], STOCK[0], STOCK[1]), 1):
                server.command(f"item replace entity {PLAYER} hotbar.{slot} with {item}{INPUT_TAG} {STOCK.count(item) + 1}")
            ready_at(server, SLICER, "stock", evidence)
            wait_signal(client, evidence, "stock-transferred")
            verify_and_run_stock(server, results)

            recipe_file(recipe, updated=True)
            server.command("reload")
            for slot, item in enumerate(reversed(CUSTOM), 1):
                server.command(f"item replace entity {PLAYER} hotbar.{slot} with {item}{INPUT_TAG}")
            (evidence / "server-custom").touch()
            wait_signal(client, evidence, "custom-transferred")
            verify_and_run_custom(server, results)
            (evidence / "server-finished").touch()

            if client.wait(timeout=25) != 0:
                raise AssertionError(f"Slice'N'Splice JEI client failed; see {evidence}")
            summary = json.loads((evidence / "summary.json").read_text())
            loaded = summary["client"]["captures"]["custom-slicer-jei"]["loadedMods"]
            if digest(Path(loaded["enderio"]["file"])) != report["jarSha256"] or digest(Path(loaded["jei"]["file"])) != report["jeiSha256"]:
                raise AssertionError("Client and server loaded different Ender IO or JEI jars")
        finally:
            stop_client(client)
            summary = evidence / "summary.json"
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for path in ("java", "forge-server", "jar", "jei", "launch"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    shutil.copyfile(args.jei, directory / "mods/jei.jar")
    pack = directory / WORLD / "datapacks/port_slicer"
    recipe = pack / "data/port_slicer/recipes/slicing/ordered.json"
    recipe.parent.mkdir(parents=True)
    (pack / "pack.mcmeta").write_text('{"pack":{"pack_format":15,"description":"Slice and Splice JEI fixture"}}\n')
    recipe_file(recipe)
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar"),
              "jeiSha256": digest(directory / "mods/jei.jar")}
    try:
        run_stage(args, directory, report, "fresh", lambda server, _: run_client(args, server, report, recipe))
        run_stage(args, directory, report, "reopened", lambda server, results: server.assert_state(
            "both_outputs_and_exact_tool_wear_survive_dedicated_restart", completed_stock(2, custom=True), results))
        report["passed"] = True
    finally:
        path = directory / "slicer-jei-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Slicer JEI passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
