#!/usr/bin/env python3
"""Verify scripted SAG Mill and Slice'N'Splice jobs through packaged Forge."""

import argparse
import json
from pathlib import Path
import shutil
import sys
import time

sys.dont_write_bytecode = True
from packaged_crafttweaker import FIXTURES, check_script
from packaged_sag_progression import charge, feed, inputs, mill, output, prepare_mill
from packaged_server_smoke import ROOT, digest
from packaged_slicer import AXE, BLOCK, CAP, SHEARS, entry
from packaged_spawners import prepare_profile, run_stage
from packaged_starter_progression import hopper_item


FLOWERS = {0: "minecraft:dandelion", 2: "minecraft:poppy", 5: "minecraft:cornflower"}


def exercise(server, results, offset, *, sag_main=None, slice_out=None, sag_fe=800, slice_fe=600):
    server.command("forceload add 3504 0 3535 15")
    sag_x, slice_x = 3506 + offset, 3510 + offset
    sag_pos, slice_pos = mill(sag_x), mill(slice_x)
    server.assert_state("scripted_sag_mill_and_slicer_chunks_loaded",
                        f"if loaded {sag_pos} if loaded {slice_pos}", results)
    prepare_mill(server, sag_x)
    inputs(server, sag_x, "minecraft:paper", 1)
    charge(server, sag_x, sag_fe + 137)

    items = [entry(slot, item) for slot, item in FLOWERS.items()]
    items.extend([entry(6, AXE, 7), entry(7, SHEARS, 11), entry(9, CAP)])
    server.command(f'setblock {slice_pos} {BLOCK}[facing=south]{{Items:{{Items:[{",".join(items)}]}}}}')
    charge(server, slice_x, slice_fe + 137)

    if sag_main is None:
        time.sleep(1)
        server.assert_state("removed_sag_consumes_no_paper_or_energy",
                            f"if data block {sag_pos} {{EnergyStored:{sag_fe + 137}}} unless data block {sag_pos} Task "
                            f"{hopper_item(feed(sag_x), 'minecraft:paper', 1)} unless data block {output(sag_x)} Items[0]", results)
        server.assert_state("removed_slicing_consumes_no_flowers_fe_or_tool_durability",
                            f'if data block {slice_pos} {{EnergyStored:{slice_fe + 137},Items:{{Items:[{",".join(items)}]}}}} '
                            f"unless data block {slice_pos} Task unless data block {slice_pos} Items.Items[{{Slot:8}}]", results)
        return

    server.assert_state("scripted_sag_yields_primary_and_tagged_secondary_at_exact_cost",
                        f"if data block {sag_pos} {{EnergyStored:137}} unless data block {sag_pos} Task "
                        f"unless data block {sag_pos} Items.Items[{{Slot:0}}] unless data block {feed(sag_x)} Items[0] "
                        f"{hopper_item(output(sag_x), 'minecraft:' + sag_main, 2)} "
                        f'if data block {output(sag_x)} Items[{{Slot:1b,id:"minecraft:copper_ingot",Count:1b}}]',
                        results, timeout=25)
    sliced = [entry(6, AXE, 8), entry(7, SHEARS, 12), entry(9, CAP)]
    sliced.append(f'{{Slot:8,id:"minecraft:{slice_out}",Count:2}}')
    emptied = " ".join(f"unless data block {slice_pos} Items.Items[{{Slot:{slot}}}]" for slot in range(6))
    server.assert_state("scripted_slicing_honors_positions_exact_fe_and_tool_wear",
                        f'if data block {slice_pos} {{EnergyStored:137,Items:{{Items:[{",".join(sliced)}]}}}} '
                        f"unless data block {slice_pos} Task {emptied}", results, timeout=25)


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
    script = directory / "scripts/sag_and_slicing.zs"
    script.parent.mkdir()
    original = (FIXTURES / script.name).read_text()
    phases = (
        ("added", "", dict(sag_main="amethyst_shard", slice_out="echo_shard")),
        ("replaced", """
sag.replaceRecipe("crafttweaker:qa_sag", <item:minecraft:paper>, 900, "chance_only",
    SagOutput.item(<item:minecraft:prismarine_shard> * 2, 1.0f, false),
    SagOutput.tag(<tag:items:forge:ingots/copper>, 1, 1.0f, false));
slice.replaceRecipe("crafttweaker:qa_slice", <item:minecraft:glowstone_dust> * 2, 1000,
    <item:minecraft:dandelion>, <item:minecraft:air>, <item:minecraft:poppy>,
    <item:minecraft:air>, <item:minecraft:air>, <item:minecraft:cornflower>);
""", dict(sag_main="prismarine_shard", slice_out="glowstone_dust", sag_fe=900, slice_fe=1000)),
        ("removed", """
sag.remove(<item:minecraft:copper_ingot>);
slice.removeByName("crafttweaker:qa_slice");
""", {}),
    )
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}
    try:
        for index, (phase, extra, expected) in enumerate(phases):
            script.write_text(original + extra)

            def scenario(server, results):
                check_script(server, "PORT_CT_SAG_SLICE_ADD_OK")
                exercise(server, results, 8 * index, **expected)

            run_stage(args, directory, report, phase, scenario)
        report["passed"] = True
    finally:
        result = directory / "crafttweaker-sag-slicing-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"CraftTweaker SAG and slicing passed={report['passed']}, {result}", flush=True)


if __name__ == "__main__":
    main()
