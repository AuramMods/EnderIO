#!/usr/bin/env python3
"""Reconcile a packaged Forge server's live content with the fixed source baseline."""

import argparse
from io import BytesIO
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile
from zipfile import ZipFile

sys.dont_write_bytecode = True
from packaged_server_smoke import ROOT, digest
from packaged_legacy_transfer_filters import wait_ticks
from packaged_sag_progression import charge, data_read, feed, inputs, mill, output
from packaged_spawners import WORLD, prepare_profile, run_stage
from packaged_starter_progression import hopper_item
from packaged_vat_automation import Batch, WATER, configure, drained, fluid, tank_fields


BASELINE = ROOT / "docs" / "porting" / "1.20.1" / "baseline.json"
EXPERIMENTS = ("enderface", "niard", "farming_station")
GATED_BLOCKS = ("enderface", "niard")
ORPHAN_RECIPE = "enderio:darksteel_upgrade"
MODEL_ONLY = {"enderio:conduit_probe_copy", "enderio:photovoltaic_cell", "enderio:soiredne",
              "enderio:soul_vial_filled", "enderio:void_vial_filled"}
ALUMINUM = {"ingots": "minecraft:echo_shard", "ores": "minecraft:nautilus_shell",
            "raw_materials": "minecraft:prismarine_bricks", "dusts": "minecraft:phantom_membrane"}


def resource_id(path, folder):
    parts = path.split("/")
    prefix, name = "/".join(parts[2:]), parts[1]
    if path.endswith(".json") and prefix.startswith(folder + "/"):
        return f"{name}:{prefix[len(folder) + 1:-5]}"
    return None


def baseline_resources(commit):
    archive = subprocess.check_output(["git", "archive", commit], cwd=ROOT)
    outputs = {folder: {} for folder in ("recipe", "advancement", "loot_table", "enderio/conduit")}
    item_models = set()
    with tarfile.open(fileobj=BytesIO(archive)) as tree:
        for member in tree:
            if not member.isfile() or "/resources/" not in member.name:
                continue
            path = member.name.split("/resources/", 1)[1]
            if path.startswith("assets/enderio/models/item/") and path.endswith(".json"):
                item_models.add(resource_id(path, "models/item"))
            if not path.startswith("data/enderio/"):
                continue
            for folder, output in outputs.items():
                key = resource_id(path, folder)
                if key is not None:
                    output[key] = json.load(tree.extractfile(member))
    return outputs, item_models


def packaged_resources(jar):
    recipes = {}
    item_models = set()
    blockstates = set()

    def collect(archive):
        for path in archive.namelist():
            if path.startswith("data/enderio/recipes/") and path.endswith(".json"):
                recipes[resource_id(path, "recipes")] = json.loads(archive.read(path))
            elif path.startswith("assets/enderio/models/item/") and path.endswith(".json"):
                item_models.add(resource_id(path, "models/item"))
            elif path.startswith("assets/enderio/blockstates/") and path.endswith(".json"):
                blockstates.add(resource_id(path, "blockstates"))
            elif path.startswith("META-INF/jarjar/") and path.endswith(".jar"):
                with ZipFile(BytesIO(archive.read(path))) as nested:
                    collect(nested)

    with ZipFile(jar) as archive:
        collect(archive)
    return recipes, item_models, blockstates


def satisfied(condition, runtime):
    kind = condition["type"].split(":", 1)[1]
    if kind == "mod_loaded":
        return condition["modid"] in runtime["mods"]
    if kind == "tag_empty":
        return not runtime["tags"]["item"].get(condition["tag"])
    if kind == "not":
        return not satisfied(condition["value"], runtime)
    raise ValueError(f"Unhandled resource condition {condition}")


def expected_entries(entries, condition_field, runtime):
    expected = {key for key, data in entries.items()
                if all(satisfied(condition, runtime) for condition in data.get(condition_field, []))}
    return expected, entries.keys() - expected


def compare(expected, actual):
    actual = set(actual)
    return {"expected": len(expected), "actual": len(actual), "missing": sorted(expected - actual),
            "extra": sorted(actual - expected)}


def check_resources(baseline, actual, runtime, condition_field):
    expected, disabled = expected_entries(baseline, condition_field, runtime)
    check = compare(expected, actual)
    check["disabled"] = sorted(disabled)
    check["unexpectedlyActive"] = sorted(disabled & set(actual))
    return check


def check_recipes(baseline, actual, runtime, condition_field):
    check = check_resources(baseline, actual, runtime, condition_field)
    check["serializerDifferences"] = {
        key: {"expected": baseline[key]["type"], "actual": actual[key]}
        for key in sorted(baseline.keys() & actual.keys()) if baseline[key]["type"] != actual[key]}
    return check


def check_tags(inventory, runtime):
    missing = []
    expected = 0
    for folder, entries in inventory.items():
        namespace, category = folder.split("/")
        if category != "tags":
            continue
        for entry in entries:
            registry, path = entry.split("/", 1)
            tag = f"{namespace}:{path}"
            expected += 1
            if tag not in runtime["tags"].get(registry, {}):
                missing.append(f"{registry}#{tag}")
    return {"expected": expected, "actual": expected - len(missing), "missing": sorted(missing)}


def check_smelting_inheritance(packaged, recipes, runtime):
    expected = {f"enderio:smelting/{key.replace(':', '/', 1)}" for key in runtime["smeltingRecipes"]} - packaged.keys()
    extra_recipes = {key: serializer for key, serializer in recipes.items() if key not in packaged}
    check = compare(expected, extra_recipes)
    check["unexpectedlyActive"] = check["extra"]
    check["serializerDifferences"] = {key: extra_recipes[key] for key in expected & extra_recipes.keys()
                                      if extra_recipes[key] != "enderio:alloy_smelting"}
    return check


def reconcile(jar, runtime, experiments):
    baseline = json.loads(BASELINE.read_text())
    resources, item_models = baseline_resources(baseline["baseline_commit"])
    inventory = baseline["resource_ids"]
    blocks = {f"enderio:{key}" for key in inventory["enderio/blockstates"]}
    disabled = set() if experiments else {f"enderio:{key}" for key in GATED_BLOCKS}
    optional_items = set() if "mekanism" in runtime["mods"] else {"enderio:basic_chemical_filter"}
    items = item_models - MODEL_ONLY - optional_items
    registries = runtime["registries"]
    recipes = runtime["recipes"]
    packaged, packaged_models, packaged_states = packaged_resources(jar)
    baseline_recipes = check_recipes(resources["recipe"], recipes, runtime, "neoforge:conditions")
    baseline_recipes["knownBaselineOrphan"] = ORPHAN_RECIPE
    baseline_recipes["orphanUnexpectedlyRegistered"] = ORPHAN_RECIPE in recipes
    registered_items = compare(items, registries.get("minecraft:item", []))
    registered_items["modelOnly"] = sorted(MODEL_ONLY)
    registered_items["optionalAbsent"] = sorted(optional_items)
    registered_items["unexpectedlyActive"] = sorted(optional_items & set(registries.get("minecraft:item", [])))
    enabled_blocks = compare(blocks - disabled, runtime["enabledBlocks"])
    enabled_blocks["unexpectedlyActive"] = sorted(disabled & set(runtime["enabledBlocks"]))
    enabled_items = compare(items - disabled, runtime["enabledItems"])
    enabled_items["unexpectedlyActive"] = sorted((disabled | optional_items) & set(runtime["enabledItems"]))
    flags = compare({f"enderio:{name}" for name in EXPERIMENTS} if experiments else set(),
                    [name for name in runtime["features"] if name.startswith("enderio:")])
    flags["unexpectedlyActive"] = flags["extra"]

    return {
        "baselineRecipes": baseline_recipes,
        "packagedRecipes": check_recipes(packaged, recipes, runtime, "conditions"),
        "inheritedAlloyRecipes": check_smelting_inheritance(packaged, recipes, runtime),
        "baselineBlocks": compare(blocks, registries.get("minecraft:block", [])),
        "baselineItems": registered_items,
        "baselineEnabledBlocks": enabled_blocks,
        "baselineEnabledItems": enabled_items,
        "packagedBlockstates": compare(blocks, packaged_states),
        "packagedItemModels": compare(item_models, packaged_models),
        "baselineAdvancements": check_resources(resources["advancement"], runtime["advancements"], runtime, "neoforge:conditions"),
        "baselineLootTables": check_resources(resources["loot_table"], runtime["lootTables"], runtime, "neoforge:conditions"),
        "baselineConduits": check_resources(resources["enderio/conduit"], registries.get("enderio:conduit", []), runtime,
                                           "neoforge:conditions"),
        "baselineTags": check_tags(inventory, runtime),
        "baselineExperimentFlags": flags,
    }


def snapshot(server, results):
    output = server.command("port_dump_registries")
    passed = "Packaged registry snapshot written" in output
    results.append({"assertion": "loaded_server_writes_registry_snapshot", "passed": passed})
    if not passed:
        raise AssertionError(output)


def write_aluminum_fixture(directory):
    pack = directory / WORLD / "datapacks/port_aluminum"
    tags = pack / "data/forge/tags/items"
    for form, item in ALUMINUM.items():
        folder = tags / form
        folder.mkdir(parents=True)
        (folder / "aluminum.json").write_text(json.dumps({"values": [item]}) + "\n")
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 15, "description": "Aluminum recipe fixture"}}) + "\n")


def grind_aluminum(server, results):
    server.command("forceload add 3400 8 3406 8")
    server.assert_state("aluminum_mill_chunk_loaded", f"if loaded {mill(3400)}", results)
    for x, recipe, input_kind, output_kind in (
            (3400, "aluminum", "ingots", "dusts"),
            (3403, "aluminum_ore", "ores", "raw_materials"),
            (3406, "raw_aluminum", "raw_materials", "dusts")):
        server.command(f'setblock {mill(x)} enderio:sag_mill[facing=south]{{IOConfig:{{Modes:{{up:"pull",down:"push"}}}},'
                       'Items:{Items:[{Slot:6,id:"enderio:basic_capacitor",Count:1}]}}')
        server.command(f"setblock {feed(x)} minecraft:hopper[facing=down]")
        server.command(f"setblock {output(x)} minecraft:hopper[facing=down]")
        inputs(server, x, ALUMINUM[input_kind], 1)
        charge(server, x, 600)
        product = ALUMINUM[output_kind]
        server.assert_state(f"{recipe}_selects_the_expected_recipe_and_spends_600_fe",
                            f'if data block {mill(x)} {{EnergyStored:0,Task:{{RecipeId:"enderio:sag_milling/{recipe}",'
                            'ProgressMade:600,ProgressRequired:2400}} '
                            f'if data block {mill(x)} Task.Outputs[{{Item:{{id:"{product}"}}}}]', results)
        quantity = int(data_read(server, mill(x), f'Task.Outputs[{{Item:{{id:"{product}"}}}}].Item.Count'))
        charge(server, x, 1800)
        server.assert_state(f"{recipe}_exports_the_rolled_form_and_uses_exactly_2400_fe",
                            f"if data block {mill(x)} {{EnergyStored:0}} unless data block {mill(x)} Task "
                            f'unless data block {mill(x)} Items.Items[{{Slot:0}}] unless data block {feed(x)} Items[0] '
                            f"{hopper_item(output(x), product, quantity)}", results)


def tag_progression(server, results):
    crops = ("cactus", "cocoa_beans", "melon", "pumpkin", "sugar_cane")
    vats = [Batch(4490 + 5 * index, "hootch", WATER, 1000, ("minecraft:wheat_seeds", f"minecraft:{crop}"),
                  125 if crop == "sugar_cane" else 250) for index, crop in enumerate(crops)]
    vats.append(Batch(4515, "hootch", WATER, 1000, ("minecraft:torchflower_seeds", "minecraft:wheat"), 250))
    server.command("forceload add 4480 0 4527 15")
    server.assert_state("vat_and_wrench_chunks_loaded", "if loaded 4490 80 8 if loaded 4515 80 8", results)
    for batch in vats:
        configure(server, batch)

    runtime = json.loads((server.directory / "registry-snapshot.json").read_text())
    wrenches = ["enderio:yeta_wrench"]
    for mod, tools in (("ae2", ("certus_quartz_wrench", "nether_quartz_wrench", "network_tool")),
                       ("laserio", ("laser_wrench",)), ("mekanism", ("configurator",))):
        if mod in runtime["mods"]:
            wrenches.extend(f"{mod}:{item}" for item in tools)

    failures = []

    def check(name, conditions):
        try:
            server.assert_state(name, conditions, results, timeout=0)
        except AssertionError as error:
            failures.append(str(error))

    for index, item in enumerate([*wrenches, "minecraft:stick"]):
        pos = f"{4490 + 3 * index} 80 3"
        server.command(f"setblock {pos} enderio:alloy_smelter[facing=south]")
        reply = server.command(f"port_use_fixture_item {pos} {item}")
        if "Native fixture item used" not in reply:
            failures.append(reply)
        # The east world face is stored as west in this machine's relative configuration.
        result = (f'if data block {pos} {{IOConfig:{{Modes:{{west:"push"}}}}}}' if item in wrenches else
                  f'unless data block {pos} IOConfig.Modes.west')
        untouched = " ".join(f"unless data block {pos} IOConfig.Modes.{side}"
                             for side in ("north", "south", "east", "up", "down"))
        check(f"{item.replace(':', '_')}_affects_only_the_intended_face", f"{result} {untouched}")

    wait_ticks(server, 235)
    for batch in vats:
        seed, crop = batch.reagents
        status = tank_fields(fluid(batch.output, batch.output_amount))
        check(f"{seed.split(':')[1]}_and_{crop.split(':')[1]}_ferment_with_the_stock_multiplier",
              f'if data block {batch.receiver} {{{status}}} {drained(batch.supply)} {drained(batch.vat)} '
              f'{drained(batch.vat, 1)} unless data block {batch.vat} Items.Items[0] '
              f'unless data block {batch.x - 1} 80 8 Items[0] unless data block {batch.x + 1} 80 8 Items[0]')
    if failures:
        raise AssertionError("\n".join(failures))


def verify_unified_furnaces(server, results):
    """Observe the exact Mekanism recipe that cooks each Ender IO dust after deduplication."""
    server.command("forceload add 2880 0 2895 15")
    server.assert_state("unified_furnace_fixture_chunk_loaded", "if loaded 2881 80 8", results)
    jobs = []
    for index, material in enumerate(("copper", "gold", "iron")):
        for offset, (kind, block) in enumerate((("blasting", "blast_furnace"), ("smelting", "furnace"))):
            pos = f"{2881 + 2 * index + offset} 80 8"
            server.command(f"setblock {pos} minecraft:{block}")
            server.command(f"item replace block {pos} container.0 with enderio:powdered_{material}")
            server.command(f"item replace block {pos} container.1 with minecraft:coal")
            jobs.append((kind, material, pos))

    replacements = {}
    for kind, material, pos in jobs:
        removed = f"enderio:item.minecraft.{material}_ingot_from_{kind}"
        retained = f"mekanism:processing/{material}/ingot/from_dust_{kind}"
        server.assert_state(f"unified_{kind}_cooks_the_original_enderio_{material}_dust",
                            f'if data block {pos} {{RecipesUsed:{{"{retained}":1}}}} '
                            f'if data block {pos} Items[{{Slot:2b,id:"minecraft:{material}_ingot",Count:1b}}] '
                            f'unless data block {pos} Items[{{Slot:0b}}]', results, timeout=22)
        replacements[removed] = retained
    return replacements


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--mod", action="append", default=[], type=Path)
    parser.add_argument("--experiments", action="store_true", help="Enable the three built-in experimental packs at world creation")
    parser.add_argument("--aluminum-fixture", action="store_true", help="Supply Forge aluminum tags and run the three SAG recipes")
    parser.add_argument("--tag-progression", action="store_true", help="Run the stock VAT variants and use native Forge wrenches")
    parser.add_argument("--unified-furnaces", action="store_true", help="Demonstrate Almost Unified's six furnace replacements")
    for path in ("java", "forge-server", "jar", "probe"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")

    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    for mod in (args.probe, *args.mod):
        shutil.copyfile(mod, directory / "mods" / mod.name)
    packs = ["vanilla"]
    if args.experiments:
        packs.extend(f"mod/enderio:data/enderio/datapacks/{name}" for name in EXPERIMENTS)
    if args.aluminum_fixture:
        write_aluminum_fixture(directory)
        packs.append("file/port_aluminum")
    if len(packs) > 1:
        with (directory / "server.properties").open("a") as properties:
            properties.write(f"initial-enabled-packs={','.join(packs)}\n")

    report = {"passed": False, "assertions": [], "servers": [],
              "sha256": {mod.name: digest(mod) for mod in sorted((directory / "mods").glob("*.jar"))}}

    def exercise(server, results):
        snapshot(server, results)
        if args.aluminum_fixture:
            grind_aluminum(server, results)
        if args.tag_progression:
            tag_progression(server, results)
        if args.unified_furnaces:
            report["furnaceReplacements"] = verify_unified_furnaces(server, results)

    try:
        run_stage(args, directory, report, "audit", exercise)
        runtime = json.loads((directory / "registry-snapshot.json").read_text())
        audit = reconcile(args.jar, runtime, args.experiments)
        if args.unified_furnaces:
            for key in ("baselineRecipes", "packagedRecipes"):
                missing = audit[key]["missing"]
                audit[key]["provedReplacements"] = {
                    name: retained for name, retained in report["furnaceReplacements"].items() if name in missing
                }
                audit[key]["missing"] = [name for name in missing if name not in audit[key]["provedReplacements"]]
        report["audit"] = audit
        differences = ("missing", "unexpectedlyActive", "serializerDifferences", "orphanUnexpectedlyRegistered")
        report["passed"] = all(not check.get(field) for check in audit.values() for field in differences)
    finally:
        output = directory / "registry-audit-results.json"
        output.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Packaged registry audit passed={report['passed']}, {output}", flush=True)
    if not report["passed"]:
        sys.exit(1)


if __name__ == "__main__":
    main()
