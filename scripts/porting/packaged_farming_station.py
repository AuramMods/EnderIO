#!/usr/bin/env python3
"""Exercise crop backpressure, replanting and hopper transfer on packaged Forge."""

import argparse
import json
from pathlib import Path
import sys

sys.dont_write_bytecode = True
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_spawners import prepare_profile, run_stage


STATION = "1608 80 8"
CROP = "1609 80 8"
OUTSIDE = "1614 80 8"
HOPPER = "1608 79 8"
CAPACITOR = '{Slot:0,id:"enderio:basic_capacitor",Count:1}'
SEEDS = '{Slot:4,id:"minecraft:wheat_seeds",Count:8}'
FILLERS = [f'{{Slot:{slot},id:"minecraft:cobblestone",Count:64}}' for slot in range(10, 16)]
WHEAT = '{Slot:10,id:"minecraft:wheat",Count:1}'
# The basic capacitor spends 40 FE per scan step and requires 400 FE to start it.
# Fifty steps cover two passes through the default 11-by-11 plot, five positions at a time.
SCAN_ENERGY = 2360
SCAN_REMAINDER = 360


def inventory(entries):
    return f'if data block {STATION} {{Items:{{Items:[{",".join(entries)}]}}}}'


def mature(pos):
    return f"if block {pos} minecraft:wheat[age=7]"


def setup(server, results):
    server.command("forceload add 1600 0 1615 15")
    server.assert_state("farm_chunk_is_loaded", f"if loaded {STATION} if loaded {OUTSIDE}", results)
    server.command("gamerule randomTickSpeed 0")
    server.command("gamerule doMobSpawning false")
    server.command("fill 1602 79 2 1615 79 14 minecraft:stone")
    server.command("setblock 1611 79 8 minecraft:water")
    for x in (1609, 1614):
        server.command(f"setblock {x} 79 8 minecraft:farmland[moisture=7]")
        server.command(f"setblock {x} 80 8 minecraft:wheat[age=7]")
    server.command(f"setblock {STATION} enderio:farming_station")
    entries = [CAPACITOR, SEEDS, *FILLERS]
    server.command(f'data merge block {STATION} {{Items:{{Items:[{",".join(entries)}]}},'
                   f'EnergyStored:{SCAN_ENERGY},RedstoneControl:"never_active"}}')
    server.assert_state("charged_station_starts_with_full_outputs", f"{inventory(entries)} {mature(CROP)} "
                        f"if data block {STATION} {{EnergyStored:{SCAN_ENERGY}}}", results)
    server.command(f'data merge block {STATION} {{RedstoneControl:"always_active"}}')
    server.assert_state("scheduler_reaches_the_blocked_plot", f"{mature(CROP)} "
                        f"if block {STATION} enderio:farming_station[powered=true]", results, timeout=20)
    server.assert_state("repeated_harvest_attempts_keep_the_mature_crop_and_seed_balance",
                        f"{mature(CROP)} {inventory(entries)} if data block {STATION} {{EnergyStored:{SCAN_REMAINDER}}}",
                        results, timeout=40)
    server.command(f"data get block {STATION}")


def resume(server, results):
    server.assert_state("full_outputs_and_seed_reserve_survive_restart",
                        f"{mature(CROP)} {inventory([CAPACITOR, SEEDS, *FILLERS])} "
                        f"if data block {STATION} {{EnergyStored:{SCAN_REMAINDER}}}", results)
    server.command(f"data remove block {STATION} Items.Items[{{Slot:10}}]")
    server.command(f"data merge block {STATION} {{EnergyStored:24000}}")
    server.assert_state("free_slot_accepts_one_wheat_and_the_crop_is_replanted",
                        f"{inventory([CAPACITOR, WHEAT, *FILLERS[1:]])} "
                        f"if block {CROP} minecraft:wheat[age=0] {mature(OUTSIDE)}", results, timeout=35)
    server.command(f"data get block {STATION}")


def extract(server, results):
    server.assert_state("replanted_crop_and_exact_output_survive_restart",
                        f"{inventory([CAPACITOR, WHEAT, *FILLERS[1:]])} "
                        f"if block {CROP} minecraft:wheat[age=0] {mature(OUTSIDE)}", results)
    server.command(f"setblock {HOPPER} minecraft:hopper")
    server.assert_state("vanilla_hopper_extracts_the_harvest_through_forge",
                        f'if data block {HOPPER} {{Items:[{{Slot:0b,id:"minecraft:wheat",Count:1b}}]}} '
                        f'unless data block {STATION} Items.Items[{{Slot:10}}] {inventory([CAPACITOR])} '
                        f'if data block {STATION} Items.Items[{{Slot:4,id:"minecraft:wheat_seeds"}}]', results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "forge": FORGE, "jarSha256": digest(directory / "mods/enderio.jar"),
              "assertions": [], "servers": []}
    try:
        for stage, scenario in (("blocked", setup), ("resumed", resume), ("extracted", extract)):
            run_stage(args, directory, report, stage, scenario)
        report["passed"] = True
    finally:
        path = directory / "farming-station-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Farming Station passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
