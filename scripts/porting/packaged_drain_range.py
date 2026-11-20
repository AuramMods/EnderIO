#!/usr/bin/env python3
"""Exercise the packaged Drain at radius ten with vanilla lava or Mekanism heavy water."""

import argparse
import json
from pathlib import Path
import shutil
import sys

sys.dont_write_bytecode = True
from packaged_drain import machine, tank_contents
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage


CHUNKS = "3056 0 3087 31"
DRAIN = "3071 80 15"
SWITCH = "3070 80 15"
TANK = "3072 80 15"
INTAKE = (3071, 79, 15)
FIRST = (3061, 59, 5)
LAST = (3081, 79, 25)
OUTSIDE_X = (3082, 76, 20)
OUTSIDE_Y = (3065, 58, 8)
OTHER_FLUID = (3066, 64, 6)
WATER = "minecraft:water[level=0]"
EMPTY_DRAIN = f"unless data block {DRAIN} Fluids.Tanks[0].Contents.FluidName"


def block(position, state):
    return f"if block {' '.join(map(str, position))} {state}"


def source_cell(server, position, source):
    x, y, z = position
    server.command(f"fill {x-1} {y-1} {z-1} {x+1} {y+1} {z+1} minecraft:stone")
    server.command(f"setblock {x} {y} {z} {source}")


def survivors(source):
    return " ".join((block(OUTSIDE_X, source), block(OUTSIDE_Y, source), block(OTHER_FLUID, WATER)))


def load_chunks(server, results, stage):
    server.command(f"forceload add {CHUNKS}")
    server.assert_state(f"all_four_scan_chunks_are_loaded_{stage}",
                        f"if loaded {DRAIN} if loaded {TANK} if loaded 3071 80 16 if loaded 3081 79 25",
                        results, timeout=30)


def pending_work(server, results, fluid, stage):
    source = f"{fluid}[level=0]"
    server.assert_state(f"the_chunk_boundary_source_and_600_fe_payment_are_held_{stage}",
                        f"if data block {DRAIN} {machine(0, 600, 10)} "
                        f"if data block {DRAIN} {{{tank_contents(1000, fluid=fluid)}}} "
                        f"if block {SWITCH} minecraft:redstone_block "
                        f"{block(FIRST, 'minecraft:air')} {block(LAST, source)} "
                        f"{block(INTAKE, source)} {survivors(source)}", results, timeout=100)


def start_scan(server, results, fluid):
    source = f"{fluid}[level=0]"
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    load_chunks(server, results, "before_setup")
    for position in (FIRST, LAST, INTAKE, OUTSIDE_X, OUTSIDE_Y):
        source_cell(server, position, source)
    source_cell(server, OTHER_FLUID, WATER)
    server.command(f"setblock {DRAIN} enderio:drain[facing=south]")
    server.command(f"data merge block {DRAIN} {machine(2100, 0, 10)}")
    server.assert_state("the_blocked_drain_has_both_corners_and_still_has_2100_fe",
                        f"if data block {DRAIN} {machine(2100, 0, 10)} {EMPTY_DRAIN} "
                        f"{block(FIRST, source)} {block(LAST, source)} {block(INTAKE, source)} {survivors(source)}",
                        results)
    server.command(f"setblock {SWITCH} minecraft:redstone_block")
    server.assert_state("the_first_corner_yields_one_bucket_for_1500_fe",
                        f"if data block {DRAIN} {machine(600, 0, 10)} "
                        f"if data block {DRAIN} {{{tank_contents(1000, fluid=fluid)}}} "
                        f"{block(FIRST, 'minecraft:air')} {block(LAST, source)} {block(INTAKE, source)}",
                        results, timeout=15)
    pending_work(server, results, fluid, "before_eviction")
    server.command(f"forceload remove {CHUNKS}")
    server.assert_state("all_four_scan_chunks_are_evicted_during_the_paid_job",
                        f"unless loaded {DRAIN} unless loaded {TANK} unless loaded 3071 80 16 unless loaded 3081 79 25",
                        results, timeout=45)
    load_chunks(server, results, "after_eviction")
    pending_work(server, results, fluid, "after_chunk_reload")


def collected(server, results, fluid, stage):
    server.assert_state(f"three_buckets_are_exported_for_exactly_4500_fe_{stage}",
                        f"if data block {DRAIN} {machine(0, 0, 10)} {EMPTY_DRAIN} "
                        f"if data block {TANK} {{{tank_contents(3000, fluid=fluid)}}} "
                        f"{block(FIRST, 'minecraft:air')} {block(LAST, 'minecraft:air')} {block(INTAKE, 'minecraft:air')} "
                        f"{survivors(f'{fluid}[level=0]')}", results, timeout=100)


def finish_scan(server, results, fluid):
    pending_work(server, results, fluid, "after_cold_start")
    server.command(f"setblock {TANK} enderio:fluid_tank")
    server.command(f"data merge block {DRAIN} {{EnergyStored:2400}}")
    source = f"{fluid}[level=0]"
    server.assert_state("the_last_corner_is_collected_and_exported_across_the_chunk_edge_for_900_fe_more",
                        f"if data block {DRAIN} {machine(1500, 0, 10)} {EMPTY_DRAIN} "
                        f"if data block {TANK} {{{tank_contents(2000, fluid=fluid)}}} "
                        f"{block(LAST, 'minecraft:air')} {block(INTAKE, source)} {survivors(source)}",
                        results, timeout=100)
    collected(server, results, fluid, "after_the_intake_is_collected")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--mekanism-jar", type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    fluid = "minecraft:lava"
    report = {"passed": False, "jarSha256": digest(directory / "mods/enderio.jar"), "assertions": [], "servers": []}
    if args.mekanism_jar is not None:
        installed = directory / "mods" / args.mekanism_jar.name
        shutil.copyfile(args.mekanism_jar, installed)
        report["mekanismSha256"] = digest(installed)
        fluid = "mekanism:heavy_water"
    report["fluid"] = fluid
    try:
        run_stage(args, directory, report, "range", lambda server, results: start_scan(server, results, fluid))
        run_stage(args, directory, report, "resume", lambda server, results: finish_scan(server, results, fluid))
        run_stage(args, directory, report, "reopened", lambda server, results: collected(server, results, fluid, "after_restart"))
        report["passed"] = True
    finally:
        (directory / "drain-range-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
