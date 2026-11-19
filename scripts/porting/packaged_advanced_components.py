#!/usr/bin/env python3
"""Exercise the stock component recipes using live machine outputs and packaged Forge TCP."""

import argparse
from collections import Counter
import json
from pathlib import Path
import sys

sys.dont_write_bytecode = True
from packaged_alloy_progression import CHEST, PLAYER_OUTPUTS, exercise_client, load, produce, verify_saved_inventory
from packaged_glass_progression import transfer
from packaged_sag_progression import charge, data_read, feed, inputs, mill, output, prepare_mill
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage

QUARTZ_MILL = 3230
QUARTZ_INPUT = "minecraft:quartz"
QUARTZ_DUST = "enderio:powdered_quartz"
QUARTZ_BATCH = 6
RETAINED_FE = 137


def prepare_inputs(server, results):
    prepare_mill(server, QUARTZ_MILL)
    inputs(server, QUARTZ_MILL, QUARTZ_INPUT, QUARTZ_BATCH)
    server.assert_state("stock_quartz_loads_through_the_sag_mill_hopper",
                        f'if data block {mill(QUARTZ_MILL)} Items.Items[{{Slot:0,id:"{QUARTZ_INPUT}",Count:{QUARTZ_BATCH}}}] '
                        f"unless data block {feed(QUARTZ_MILL)} Items[0]", results)
    charge(server, QUARTZ_MILL, QUARTZ_BATCH * 2400 + RETAINED_FE)
    server.assert_state("sag_mill_spends_exactly_14400_fe_to_process_the_original_quartz",
                        f"if data block {mill(QUARTZ_MILL)} {{EnergyStored:{RETAINED_FE}}} "
                        f"unless data block {mill(QUARTZ_MILL)} Task unless data block {mill(QUARTZ_MILL)} Items.Items[{{Slot:0}}] "
                        f'if data block {output(QUARTZ_MILL)} Items[{{id:"{QUARTZ_DUST}"}}]', results, timeout=60)
    amount = int(data_read(server, output(QUARTZ_MILL), f'Items[{{id:"{QUARTZ_DUST}"}}].Count').removesuffix("b"))
    if not QUARTZ_BATCH <= amount <= 2 * QUARTZ_BATCH:
        raise AssertionError(f"Stock SAG Mill produced {amount} quartz powder from {QUARTZ_BATCH} quartz")
    transfer(server, output(QUARTZ_MILL), 0, CHEST, len(PLAYER_OUTPUTS))
    extras = ((QUARTZ_INPUT, 1), ("minecraft:iron_ingot", 9), ("enderio:grains_of_infinity", 2))
    for slot, (item, count) in enumerate(extras, start=len(PLAYER_OUTPUTS) + 1):
        server.command(f"item replace block {CHEST} container.{slot} with {item} {count}")
    server.assert_state("the_output_chest_contains_the_original_quartz_powder",
                        f'if data block {CHEST} Items[{{id:"{QUARTZ_DUST}",Count:{amount}b}}] '
                        f"unless data block {output(QUARTZ_MILL)} Items[0]", results)
    return amount


def placed_components():
    return ("if block 3210 80 11 enderio:ensouled_chassis "
            "if block 3212 80 11 enderio:soul_chain[axis=y,waterlogged=false] "
            "if block 3209 80 11 enderio:soul_chain[axis=x,waterlogged=false]")


def remaining_inventory(powder):
    expected = Counter()
    for job in PLAYER_OUTPUTS:
        expected[f"enderio:{job.output}"] += job.count
    for ingot, used in (("soularium", 8), ("energetic_alloy", 5), ("vibrant_alloy", 5), ("dark_steel", 5)):
        expected[f"enderio:{ingot}_ingot"] -= used
    expected.update({"enderio:energetic_alloy_nugget": 5, "enderio:vibrant_alloy_nugget": 5,
                     "enderio:dark_steel_nugget": 5, "enderio:soularium_nugget": 3, "minecraft:iron_nugget": 1,
                     "enderio:vibrant_gear": 1, "enderio:dark_bimetal_gear": 1, QUARTZ_DUST: powder - QUARTZ_BATCH})
    return dict(+expected)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    for key in ("forge-server", "jar", "launch"):
        parser.add_argument(f"--{key}", type=Path, required=True)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}

    def fresh(server, results):
        produce(server, results)
        report["quartzPowder"] = prepare_inputs(server, results)
        exercise_client(args, server, results, report, scenario="advanced-components", placements=placed_components)

    def reopened(server, results):
        load(server, results)
        server.assert_state("survival_chassis_and_both_chain_axes_survive_restart",
                            f"{placed_components()} if data block {mill(QUARTZ_MILL)} {{EnergyStored:{RETAINED_FE}}} "
                            f"unless data block {CHEST} Items[0] unless data block {output(QUARTZ_MILL)} Items[0]", results)

    try:
        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", reopened)
        verify_saved_inventory(directory, report, expected=remaining_inventory(report["quartzPowder"]))
        report["passed"] = True
    finally:
        result = directory / "advanced-components-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Advanced components passed={report['passed']}, {result}", flush=True)


if __name__ == "__main__":
    main()
