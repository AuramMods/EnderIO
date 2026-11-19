#!/usr/bin/env python3
"""Audit the loaded data maps and soul rules, then exercise a live datapack reload."""

import argparse
from io import BytesIO
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile
import time

sys.dont_write_bytecode = True
from packaged_legacy_transfer_filters import wait_ticks
from packaged_registry_audit import BASELINE, snapshot
from packaged_sag_progression import STOCK_BALL, charge, data_read, feed, inputs, mill, output
from packaged_server_smoke import ROOT, digest
from packaged_spawners import CAPACITOR, SPAWNER, WORLD, prepare_profile, run_stage
from packaged_starter_progression import hopper_item


STOCK_MILL, CUSTOM_MILL = 4530, 4534
WIRELESS, ANTENNA, CAPTURE = "4540 80 8", "4540 81 8", "4544 80 8"
RANGE_MAP = "enderio:data_maps/block/range_extender.json"
ADVANCED_ANTENNA = "enderio:wireless_charger_antenna_advanced"
RANGE_TAG = "#enderio:range_extender"
STOCK_RECIPE, CUSTOM_RECIPE = "enderio:sag_milling/coal", "port_rules:awaiting_replacement"
COW = "minecraft:cow"
COW_DATA = '{CustomName:\'{"text":"Reloaded cow"}\',Health:7.0f,Tags:["rule_reload"]}'
COPY_RULE = {"entity": COW, "power": 14000, "type": "copy"}
UNAVAILABLE_STOCK_RULES = {"minecraft:armadillo", "minecraft:bogged", "minecraft:breeze"}


def write_rule(directory, resource, data):
    target = directory / WORLD / "datapacks/port-rules" / resource
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(data) + "\n")


def write_recipe(directory, product, energy):
    write_rule(directory, "data/port_rules/recipes/awaiting_replacement.json", {
        "type": "enderio:sag_milling", "bonus": "none", "energy": energy,
        "input": {"item": "minecraft:golden_carrot"}, "outputs": [{"item": {"id": product, "count": 1}}],
    })


def stock_data(runtime):
    commit = json.loads(BASELINE.read_text())["baseline_commit"]
    archive = subprocess.check_output(["git", "archive", commit], cwd=ROOT)
    maps, souls = {}, {}
    with tarfile.open(fileobj=BytesIO(archive)) as tree:
        for member in tree:
            path = member.name.removeprefix("enderio/src/generated/resources/data/enderio/")
            if not member.isfile() or not path.endswith(".json"):
                continue
            if path.startswith("data_maps/"):
                registry = path.split("/")[1]
                entries = json.load(tree.extractfile(member))["values"]
                resolved = {}
                for selector, value in entries.items():
                    keys = runtime["tags"][registry][selector[1:]] if selector.startswith("#") else (selector,)
                    resolved.update((key, value) for key in keys)
                maps[f"enderio:{path}"] = resolved
            elif path.startswith("eio_soul/"):
                record = json.load(tree.extractfile(member))
                group = path.split("/")[1]
                souls.setdefault(group, {})[record["entity"]] = record
    return maps, souls


def inventory(server, report, stage):
    snapshot(server, report["assertions"])
    path = server.directory / "registry-snapshot.json"
    shutil.copyfile(path, path.with_name(f"registry-{stage}.json"))
    return json.loads(path.read_text())


def audit_stock(runtime, report):
    maps, souls = stock_data(runtime)
    registry_entities = set(runtime["tagRegistryIds"]["entity_type"])
    unavailable = {entity for records in souls.values() for entity in records if entity not in registry_entities}
    checks = {
        "stock_data_map_values_match_the_baseline": maps == runtime["dataMaps"],
        "stock_soul_rule_values_match_the_baseline": souls == runtime["soulData"],
        "only_the_three_later_vanilla_entities_are_unavailable": unavailable == UNAVAILABLE_STOCK_RULES,
    }
    report["stock"] = {"mapEntries": {key: len(value) for key, value in maps.items()},
                       "soulRules": {key: len(value) for key, value in souls.items()},
                       "unavailableEntities": sorted(unavailable)}
    report["assertions"].extend({"assertion": k, "passed": v} for k, v in checks.items())
    if not all(checks.values()):
        raise AssertionError(f"Stock rule mismatch: {checks}")


def mill_setup(server, x, ingredient, charged, ball=""):
    server.command(f'setblock {mill(x)} enderio:sag_mill[facing=south]{{IOConfig:{{Modes:{{up:"pull",down:"push"}}}},'
                   f'Items:{{Items:[{{Slot:6,{CAPACITOR},Count:1}}]}}{ball}}}')
    server.command(f"setblock {feed(x)} minecraft:hopper[facing=down]")
    server.command(f"setblock {output(x)} minecraft:hopper[facing=down]")
    inputs(server, x, ingredient, 1)
    charge(server, x, charged)


def paid_mill(x, recipe, required):
    return (f'if data block {mill(x)} {{EnergyStored:0,Task:{{RecipeId:"{recipe}",ProgressMade:600,'
            f'ProgressRequired:{required},HasDeterminedOutputs:1b}}}} unless data block {output(x)} Items[0]')


def set_capture(server, charge_amount):
    items = f'Items:{{Items:[{{Slot:0,{CAPACITOR},Count:1}},{{Slot:1,id:"enderio:soul_vial",Count:1}}]}}'
    server.command(f'data merge block {CAPTURE} {{Mode:"capture",EnergyStored:{charge_amount},{items},'
                   f'EntityStorage:{{entity_type:"{COW}",entity_tag:{COW_DATA}}}}}')


def capture_job(cost, paid, mode):
    return (f'if data block {CAPTURE} {{EnergyStored:0,Mode:"capture",Task:{{EnergyCost:{cost},'
            f'EnergyConsumed:{paid},EntityType:"{COW}",SpawnMode:"{mode}"}}}} '
            f'unless data block {CAPTURE} Items.Items[{{Slot:2}}]')


def finished_capture(copied):
    slot = f"{CAPTURE} Items.Items[{{Slot:2}}]"
    soul = f'{slot}.tag."enderio:data"."enderio:soul"'
    metadata = (f'if data block {soul}.entity_tag{{Health:7.0f,Tags:["rule_reload"]}}' if copied else
                f'unless data block {soul}.entity_tag.CustomName')
    return (f'if data block {CAPTURE} Items.Items[{{Slot:2,id:"enderio:soul_vial",Count:1}}] '
            f'if data block {soul}{{entity_type:"{COW}"}} {metadata} '
            f'unless data block {CAPTURE} Items.Items[{{Slot:1}}] if data block {CAPTURE} {{EnergyStored:0}}')


def max_range(size):
    return f'if data block {WIRELESS} {{ActionRange:{{range:{size}}}}} if block {ANTENNA} {ADVANCED_ANTENNA}'


def reload(server, report, label, extension):
    write_rule(server.directory, "data/enderio/" + RANGE_MAP.split(":", 1)[1],
               {"values": {ADVANCED_ANTENNA: {RANGE_TAG: extension}}})
    write_rule(server.directory, "data/enderio/eio_soul/spawner/minecraft_cow.json", COPY_RULE)
    server.command("reload")
    deadline = time.monotonic() + 25
    while time.monotonic() < deadline:
        server.command("port_dump_registries")
        data = json.loads((server.directory / "registry-snapshot.json").read_text())
        if data["dataMaps"][RANGE_MAP][ADVANCED_ANTENNA][RANGE_TAG] == extension:
            break
        time.sleep(0.2)
    else:
        raise AssertionError("The active recipe manager did not receive the new range map")
    observed = inventory(server, report, label)
    if observed["soulData"]["spawner"][COW] != COPY_RULE:
        raise AssertionError("The loaded cow rule differs from the authored override")
    wait_ticks(server, 4)


def exercise(server, report):
    results = report["assertions"]
    audit_stock(inventory(server, report, "stock"), report)
    server.command("forceload add 4528 0 4559 15")
    server.assert_state("both_rule_fixture_chunks_loaded", f"if loaded {mill(STOCK_MILL)} if loaded {CAPTURE}", results)
    mill_setup(server, STOCK_MILL, "minecraft:coal", 600, f",GrindingBal:{STOCK_BALL}")
    mill_setup(server, CUSTOM_MILL, "minecraft:golden_carrot", 600)
    server.command(f"setblock {WIRELESS} enderio:wireless_charger[facing=south]")
    server.command(f"setblock {ANTENNA} {ADVANCED_ANTENNA}")
    server.command(f"setblock {CAPTURE} {SPAWNER}[facing=south]")
    set_capture(server, 4000)
    server.assert_state("stock_antenna_uses_its_32_block_extension", max_range(48), results)
    server.assert_state("stock_capture_task_has_paid_4000_of_10000_fe", capture_job(10000, 4000, "entity_type"), results)
    server.assert_state("both_mills_have_paid_600_fe", f"{paid_mill(STOCK_MILL, STOCK_RECIPE, 2040)} "
                        f"{paid_mill(CUSTOM_MILL, CUSTOM_RECIPE, 2000)}", results)
    queued = data_read(server, mill(STOCK_MILL), "Task.Outputs")
    dust = "enderio:powdered_coal"
    powder = int(data_read(server, mill(STOCK_MILL), f'Task.Outputs[{{Item:{{id:"{dust}"}}}}].Item.Count'))
    report["stockMillRoll"] = {"outputs": queued, "coalDust": powder}

    reload(server, report, "reloaded", 8)
    server.assert_state("reloaded_map_shrinks_the_existing_wireless_range", max_range(24), results)
    server.assert_state("the_paid_capture_keeps_its_original_rule_on_reload", capture_job(10000, 4000, "entity_type"), results)
    server.command(f"data get block {mill(STOCK_MILL)}")
    server.assert_state("identical_recipes_keep_paid_progress_during_data_reload", f"{paid_mill(STOCK_MILL, STOCK_RECIPE, 2040)} "
                        f"{paid_mill(CUSTOM_MILL, CUSTOM_RECIPE, 2000)}", results, timeout=3)
    if queued != data_read(server, mill(STOCK_MILL), "Task.Outputs"):
        raise AssertionError("An unchanged recipe rerolled its pending outputs on reload")

    charge(server, STOCK_MILL, 1440)
    server.assert_state("stock_mill_uses_only_its_saved_remainder_and_original_roll",
                        f'if data block {mill(STOCK_MILL)} {{EnergyStored:0,GrindingBallDamage:2040}} '
                        f"{hopper_item(output(STOCK_MILL), dust, powder)} unless data block {mill(STOCK_MILL)} Items.Items[{{Slot:0}}] "
                        f"unless data block {mill(STOCK_MILL)} Task", results)
    server.command(f"data merge block {CAPTURE} {{EnergyStored:6000}}")
    server.assert_state("original_capture_finishes_for_6000_fe_with_a_new_soul", finished_capture(False), results, timeout=20)
    set_capture(server, 4000)
    server.assert_state("next_capture_adopts_the_reloaded_cost_and_copy_mode", capture_job(14000, 4000, "copy"), results)

    write_recipe(server.directory, "minecraft:iron_nugget", 1200)
    reload(server, report, "replaced", 20)
    server.assert_state("the_second_range_update_takes_effect_without_block_replacement", max_range(36), results)
    server.assert_state("changed_recipe_discards_the_pending_output_and_keeps_the_input",
                        f'if data block {mill(CUSTOM_MILL)} {{EnergyStored:0,Items:{{Items:[{{Slot:0,id:"minecraft:golden_carrot",Count:1}}]}}}} '
                        f"unless data block {mill(CUSTOM_MILL)} Task unless data block {output(CUSTOM_MILL)} Items[0]", results)
    charge(server, CUSTOM_MILL, 1200)
    server.assert_state("replacement_recipe_uses_its_own_energy_and_output",
                        f"if data block {mill(CUSTOM_MILL)} {{EnergyStored:0}} "
                        f"{hopper_item(output(CUSTOM_MILL), 'minecraft:iron_nugget', 1)} "
                        f"unless data block {mill(CUSTOM_MILL)} Items.Items[{{Slot:0}}] unless data block {output(CUSTOM_MILL)} Items[1]",
                        results)


def reopen(server, results):
    server.assert_state("reloaded_antenna_and_paid_copy_job_survive_restart",
                        f'{max_range(36)} {capture_job(14000, 4000, "copy")}', results)
    server.command(f"data merge block {CAPTURE} {{EnergyStored:10000}}")
    server.assert_state("restarted_copy_consumes_only_its_remaining_energy_and_preserves_entity_data",
                        finished_capture(True), results, timeout=20)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for arg in ("java", "forge-server", "jar", "probe"):
        parser.add_argument(f"--{arg}", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")

    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    shutil.copyfile(args.probe, directory / "mods" / args.probe.name)
    write_rule(directory, "pack.mcmeta", {"pack": {"pack_format": 15, "description": "Ender IO rule reload test"}})
    write_recipe(directory, "minecraft:gold_nugget", 2000)
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}
    try:
        run_stage(args, directory, report, "reload", lambda server, _: exercise(server, report))
        run_stage(args, directory, report, "reopened", reopen)
        report["passed"] = True
    finally:
        path = directory / "rule-reload-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Rule reload passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
