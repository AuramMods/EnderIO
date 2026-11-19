#!/usr/bin/env python3
"""Check native SAG Mill tag outputs and Almost Unified priority in a packaged server."""

import argparse
import json
from pathlib import Path
import shutil
import sys
import time

sys.dont_write_bytecode = True
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage


TIN = "2863 80 8"
OSMIUM = "2865 80 8"
IRON = "2867 80 8"
AFTER_RESTART = "2869 80 8"
RESTRICTED_TIN = "2871 80 8"
STORED_ENERGY = 6000
RECIPE_ENERGY = 2400
REMAINDER = STORED_ENERGY - 2 * RECIPE_ENERGY
EIO_TIN = "enderio:powdered_tin"
MEKANISM_TIN = "mekanism:dust_tin"
RELOAD_MARKER = "PORT_SAG_TIN_TAG_RELOADED"


def set_priority(directory, namespaces):
    config = directory / "config/almostunified/unify.json"
    config.parent.mkdir(parents=True, exist_ok=True)
    config.write_text(json.dumps({
        "modPriorities": namespaces,
        "stoneStrata": [],
        "tags": ["forge:dusts/tin"],
        "materials": ["tin"],
        "priorityOverrides": {},
        "customTags": {},
        "tagOwnerships": {},
        "itemTagInheritanceMode": "ALLOW",
        "itemTagInheritance": {},
        "blockTagInheritanceMode": "ALLOW",
        "blockTagInheritance": {},
        "ignoredTags": [],
        "ignoredItems": [],
        "ignoredRecipeTypes": ["enderio:sag_milling"],
        "ignoredRecipes": [],
        "itemsHidingJeiRei": False,
    }, indent=2) + "\n")


def seed(server, pos, item):
    server.command(f'setblock {pos} enderio:sag_mill[facing=south]{{EnergyStored:{STORED_ENERGY},'
                   f'Items:{{Items:[{{Slot:0,id:"{item}",Count:2}},'
                   '{Slot:6,id:"enderio:octadic_capacitor",Count:1}]}}')


def produced(pos, item):
    return (f'if block {pos} enderio:sag_mill if data block {pos} {{EnergyStored:{REMAINDER},'
            f'Items:{{Items:[{{Slot:1,id:"{item}",Count:2}},{{Slot:6,id:"enderio:octadic_capacitor",Count:1}}]}}}} '
            f'unless data block {pos} Items.Items[{{Slot:0}}] unless data block {pos} Task '
            f'unless data block {pos} Items.Items[{{Slot:2}}] unless data block {pos} Items.Items[{{Slot:3}}] '
            f'unless data block {pos} Items.Items[{{Slot:4}}]')


def check_output(server, results, name, pos, item):
    try:
        server.assert_state(name, produced(pos, item), results, timeout=20)
    finally:
        server.command(f"data get block {pos}")


def prepare(server, results):
    server.command("gamerule doMobSpawning false")
    server.command("forceload add 2848 0 2879 15")
    server.assert_state("both_fixture_chunks_are_loaded",
                        f"if loaded {TIN} if loaded {AFTER_RESTART}", results)
    server.command("fill 2859 79 4 2872 79 13 minecraft:stone")


def write_tin_tag(directory, restricted):
    pack = directory / WORLD / "datapacks/port-sag-compat"
    tag = pack / "data/c/tags/items/dusts/tin.json"
    tag.parent.mkdir(parents=True, exist_ok=True)
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 15, "description": "SAG Mill tag fixture"}}) + "\n")
    tag.write_text(json.dumps({"replace": restricted, "values": [EIO_TIN] if restricted else []}) + "\n")
    if restricted:
        marker = pack / "data/port_sag/functions/tin_reloaded.mcfunction"
        marker.parent.mkdir(parents=True, exist_ok=True)
        marker.write_text(f"say {RELOAD_MARKER}\n")


def restrict_tin_at_runtime(server, results):
    write_tin_tag(server.directory, True)
    server.command("reload")
    deadline = time.monotonic() + 40
    while RELOAD_MARKER not in server.command("function port_sag:tin_reloaded"):
        if time.monotonic() >= deadline:
            raise RuntimeError(f"Tin tag override did not load; see {server.log_path}")
        time.sleep(0.2)
    seed(server, RESTRICTED_TIN, "mekanism:ingot_tin")
    check_output(server, results, "only_items_in_the_actual_output_tag_can_win_unification", RESTRICTED_TIN, EIO_TIN)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--mekanism-jar", type=Path)
    parser.add_argument("--almost-unified-jar", type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    if args.almost_unified_jar and not args.mekanism_jar:
        parser.error("the competing tin output requires Mekanism")

    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    if args.mekanism_jar:
        shutil.copyfile(args.mekanism_jar, directory / "mods/mekanism.jar")
    if args.almost_unified_jar:
        shutil.copyfile(args.almost_unified_jar, directory / "mods/almostunified.jar")
        set_priority(directory, ["mekanism", "enderio"])
        write_tin_tag(directory, False)
    tin = MEKANISM_TIN if args.almost_unified_jar else EIO_TIN

    report = {"passed": False, "forge": FORGE, "jarSha256": digest(directory / "mods/enderio.jar"),
              "almostUnifiedSha256": digest(args.almost_unified_jar) if args.almost_unified_jar else None,
              "mekanismSha256": digest(args.mekanism_jar) if args.mekanism_jar else None,
              "assertions": [], "servers": []}
    try:
        def fresh(server, results):
            prepare(server, results)
            seed(server, IRON, "minecraft:iron_ingot")
            check_output(server, results, "iron_control_consumes_exact_energy", IRON, "enderio:powdered_iron")
            if args.mekanism_jar:
                seed(server, TIN, "mekanism:ingot_tin")
                check_output(server, results, "native_tin_recipe_resolves_the_expected_dust", TIN, tin)
                seed(server, OSMIUM, "mekanism:ingot_osmium")
                check_output(server, results, "native_osmium_recipe_uses_forge_materials", OSMIUM, "mekanism:dust_osmium")
            if args.almost_unified_jar:
                restrict_tin_at_runtime(server, results)

        def reopened(server, results):
            check_output(server, results, "iron_output_survives_restart", IRON, "enderio:powdered_iron")
            if args.mekanism_jar:
                check_output(server, results, "existing_tin_output_keeps_its_original_item", TIN, tin)
                check_output(server, results, "osmium_output_survives_restart", OSMIUM, "mekanism:dust_osmium")
                seed(server, AFTER_RESTART, "mekanism:ingot_tin")
                check_output(server, results, "new_tin_jobs_use_current_priority", AFTER_RESTART, EIO_TIN)

        run_stage(args, directory, report, "fresh", fresh)
        if args.almost_unified_jar:
            set_priority(directory, ["enderio", "mekanism"])
            write_tin_tag(directory, False)
        run_stage(args, directory, report, "reopened", reopened)
        report["passed"] = True
    finally:
        path = directory / "sag-unification-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"SAG Mill tag outputs passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
