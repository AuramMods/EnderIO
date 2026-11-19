#!/usr/bin/env python3
"""Exercise scheduled pitcher, torchflower, wart and hoe work on packaged Forge."""

import argparse
import json
from pathlib import Path
import re
import sys

sys.dont_write_bytecode = True
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_spawners import prepare_profile, run_stage


FLOWERS = "2440 80 8"
FIELD = "2472 80 8"
PITCHER = "2439 80 7"
UPPER = "2439 81 7"
TORCHFLOWER = "2441 80 7"
WHEAT = "2471 80 7"
WHEAT_SOIL = "2471 79 7"
WART = "2473 80 7"
LOOSE_FLOWERS = "@e[type=minecraft:item,x=2440.5,y=80,z=7.5,distance=..8]"


def stack(slot, name, count=1, damage=None):
    tag = f",tag:{{Damage:{damage}}}" if damage is not None else ""
    return f'{{Slot:{slot},id:"{name}",Count:{count}{tag}}}'


CAP = stack(0, "enderio:basic_capacitor")
FILLERS = [stack(slot, "minecraft:cobblestone", 64) for slot in range(10, 16)]
FLOWER_RESERVES = [stack(7, "minecraft:pitcher_pod"), stack(4, "minecraft:torchflower_seeds")]
MATURE_FLOWERS = (f"if block {PITCHER} minecraft:pitcher_crop[age=4,half=lower] "
                  f"if block {UPPER} minecraft:pitcher_crop[age=4,half=upper] "
                  f"if block {TORCHFLOWER} minecraft:torchflower")
FLOWER_OUTPUTS = [stack(10, "minecraft:pitcher_plant"), stack(11, "minecraft:torchflower")]


def station_items(items):
    return '{Items:[' + ",".join([CAP, *items]) + ']}'


def inventory(pos, items):
    return f'if data block {pos} {{Items:{station_items(items)}}}'


def start_station(server, pos, items):
    server.command(f"setblock {pos} enderio:farming_station")
    server.command(f'data merge block {pos} {{EnergyStored:6000,RedstoneControl:"always_active",'
                   f'ActionRange:{{range:1,isVisible:0b}},Items:{station_items(items)}}}')


def paused_flowers():
    return (f"{MATURE_FLOWERS} {inventory(FLOWERS, [*FLOWER_RESERVES, *FILLERS])} "
            f"unless data block {FLOWERS} Items.Items[{{Slot:8}}] unless entity {LOOSE_FLOWERS}")


def planted_field():
    return (f"if block {WHEAT_SOIL} minecraft:farmland if block {WHEAT} minecraft:wheat[age=0] "
            f"if block {WART} minecraft:nether_wart[age=0] "
            f"{inventory(FIELD, [stack(7, 'minecraft:wheat_seeds'), stack(4, 'minecraft:nether_wart', 63)])} "
            f"unless data block {FIELD} Items.Items[{{Slot:2}}]")


def harvested_flowers():
    return (f"if block {PITCHER} minecraft:air if block {UPPER} minecraft:air if block {TORCHFLOWER} minecraft:air "
            f"{inventory(FLOWERS, [*FLOWER_RESERVES, *FLOWER_OUTPUTS, *FILLERS[2:]])} unless entity {LOOSE_FLOWERS}")


def fresh(server, results):
    server.command("forceload add 2432 0 2480 15")
    server.assert_state("flower_and_field_chunks_loaded", f"if loaded {FLOWERS} if loaded {FIELD}", results)
    server.command("gamerule randomTickSpeed 0")
    server.command("gamerule doMobSpawning false")
    server.command("gamerule doDaylightCycle false")
    server.command("time set noon")
    server.command("fill 2438 78 6 2442 79 10 minecraft:stone")
    server.command("fill 2470 78 6 2474 79 10 minecraft:stone")
    server.command("setblock 2440 79 6 minecraft:water")
    server.command("setblock 2472 79 6 minecraft:water")
    for x in (2439, 2441):
        server.command(f"setblock {x} 79 7 minecraft:farmland[moisture=7]")
    server.command(f"setblock {WHEAT_SOIL} minecraft:dirt")
    server.command("setblock 2473 79 7 minecraft:soul_sand")

    start_station(server, FLOWERS, [stack(7, "minecraft:pitcher_pod", 2), stack(4, "minecraft:torchflower_seeds", 2),
                                    stack(8, "minecraft:bone_meal", 6), *FILLERS])
    start_station(server, FIELD, [stack(2, "minecraft:iron_hoe", damage=249), stack(7, "minecraft:wheat_seeds", 2),
                                  stack(4, "minecraft:nether_wart", 64), *FILLERS])
    server.assert_state("last_hoe_use_tills_the_plot_and_reserved_seeds_plant_wheat_and_wart",
                        planted_field(), results, timeout=20)
    server.assert_state("six_fertilizer_units_mature_both_flowers_and_full_output_keeps_them_planted",
                        paused_flowers(), results, timeout=40)
    for pos in (FLOWERS, FIELD):
        server.command(f'data merge block {pos} {{RedstoneControl:"never_active",EnergyStored:0}}')


def harvest(server, results, report):
    server.assert_state("mature_flowers_plantings_and_reserves_survive_restart", f"{paused_flowers()} {planted_field()}", results)
    for slot in (10, 11):
        server.command(f"data remove block {FLOWERS} Items.Items[{{Slot:{slot}}}]")
    server.command(f'data merge block {FLOWERS} {{RedstoneControl:"always_active",EnergyStored:3000}}')
    server.assert_state("both_pitcher_halves_and_the_torchflower_yield_only_one_item_each",
                        harvested_flowers(), results, timeout=15)

    # Promote the wart directly to maturity to keep this fixture independent of random growth timing.
    server.command(f"setblock {WART} minecraft:nether_wart[age=3]")
    server.command(f"data modify block {FIELD} Items.Items[{{Slot:4}}].Count set value 64")
    server.command(f'data merge block {FIELD} {{RedstoneControl:"always_active",EnergyStored:3000}}')
    server.assert_state("full_outputs_hold_the_mature_wart_when_the_station_reaches_it",
                        f"if block {WART} minecraft:nether_wart[age=3] if block {FIELD} enderio:farming_station[powered=true] "
                        f"{inventory(FIELD, [stack(4, 'minecraft:nether_wart', 64), *FILLERS])}", results, timeout=15)
    server.command(f"data remove block {FIELD} Items.Items[{{Slot:10}}]")
    server.assert_state("free_space_allows_mature_wart_to_be_collected_and_replanted",
                        f"{planted_field()} if data block {FIELD} Items.Items[{{Slot:10,id:\"minecraft:nether_wart\"}}]",
                        results, timeout=20)
    reply = server.command(f"data get block {FIELD} Items.Items[{{Slot:10}}].Count")
    match = re.search(r"following block data: (\d+)", reply)
    if not match or not 2 <= int(match.group(1)) <= 4:
        raise AssertionError(f"Expected one vanilla mature nether wart yield; see {server.log_path}")
    report["wartYield"] = int(match.group(1))
    for pos in (FLOWERS, FIELD):
        server.command(f'data merge block {pos} {{RedstoneControl:"never_active",EnergyStored:0}}')


def reopened(server, results, yield_count):
    server.assert_state("all_flower_outputs_and_the_exact_wart_yield_survive_the_second_restart",
                        f"{harvested_flowers()} {planted_field()} {inventory(FIELD, [stack(10, 'minecraft:nether_wart', yield_count)])}",
                        results)


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
        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "harvested", lambda server, results: harvest(server, results, report))
        run_stage(args, directory, report, "reopened", lambda server, results: reopened(server, results, report["wartYield"]))
        report["passed"] = True
    finally:
        path = directory / "farming-station-crop-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Farming Station crops passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
