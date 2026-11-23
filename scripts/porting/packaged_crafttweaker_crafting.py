#!/usr/bin/env python3
"""Verify that the Crafter uses CraftTweaker-edited shaped soul storage recipes."""

import argparse
import json
from pathlib import Path
import shutil
import sys
import time

sys.dont_write_bytecode = True
from packaged_crafttweaker import FIXTURES, check_script
from packaged_crafter import CRAFT_COST, seed
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage


SOUL = 'tag:{"enderio:data":{"enderio:soul":{entity_type:"minecraft:cow",entity_tag:{}}}}'


def storage(slot):
    return f'{{Slot:{slot},id:"enderio:soul_vial",Count:1,{SOUL}}}'


def exercise(server, results, x, product):
    if product:
        check_script(server, "PORT_CT_SHAPED_SOUL_OK")
    else:
        check_script(server, "PORT_CT_SHAPED_SOUL_REMOVED")
    server.command("forceload add 3936 0 3951 15")
    pos = f"{x} 80 8"
    server.assert_state("crafttweaker_crafter_chunk_loaded", f"if loaded {pos}", results)
    seed(server, pos, CRAFT_COST + 137, [storage(1), storage(11)])
    if product:
        server.assert_state("crafter_transfers_the_soul_using_the_scripted_native_serializer",
                            f'if data block {pos} {{EnergyStored:137,Items:{{Items:'
                            f'[{{Slot:10,id:"enderio:{product}",Count:1,{SOUL}}}]}}}} '
                            f"unless data block {pos} Items.Items[{{Slot:1}}]", results, timeout=25)
    else:
        time.sleep(1)
        server.assert_state("removing_the_crafting_recipe_leaves_the_soul_and_fe_untouched",
                            f"if data block {pos} {{EnergyStored:{CRAFT_COST + 137},Items:{{Items:[{storage(1)}]}}}} "
                            f"unless data block {pos} Items.Items[{{Slot:10}}]", results)


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
    shutil.copyfile(args.crafttweaker, directory / "mods" / args.crafttweaker.name)
    script = directory / "scripts/entity_storage.zs"
    script.parent.mkdir()
    source = (FIXTURES / script.name).read_text()
    script.write_text(source)
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}
    try:
        run_stage(args, directory, report, "added", lambda server, results: exercise(server, results, 3938, "broken_spawner"))
        replacement = source.replace("enderio:broken_spawner", "enderio:vibrant_photovoltaic_module")
        script.write_text(source + '\ncraftingTable.removeByName("crafttweaker:qa_entity_storage");\n' + replacement)
        run_stage(args, directory, report, "replaced",
                  lambda server, results: exercise(server, results, 3942, "vibrant_photovoltaic_module"))
        script.write_text(source + """
craftingTable.removeByName("crafttweaker:qa_entity_storage");
if craftingTable.getRecipeByName("crafttweaker:qa_entity_storage") == null {
    println("PORT_CT_SHAPED_SOUL_REMOVED");
}
""")
        run_stage(args, directory, report, "removed", lambda server, results: exercise(server, results, 3946, None))
        report["passed"] = True
    finally:
        result = directory / "crafttweaker-crafting-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"CraftTweaker crafting passed={report['passed']}, {result}", flush=True)


if __name__ == "__main__":
    main()
