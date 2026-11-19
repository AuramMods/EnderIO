#!/usr/bin/env python3
"""Exercise interrupted tree harvests and capped stalk crops on packaged Forge."""

import argparse
import json
from pathlib import Path
import sys

sys.dont_write_bytecode = True
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_spawners import prepare_profile, run_stage


CHUNKS = "1712 0 1791 15"
TRUNK_STATION = "1720 80 8"
BRANCH_STATION = "1753 80 8"
STALK_STATION = "1784 80 8"
TRUNK = ("1715 80 3", "1715 81 3", "1715 82 3")
BRANCH = ("1758 80 3", "1758 81 3", "1759 82 3", "1760 82 3", "1761 82 3")
STALKS = ((1779, "sugar_cane", "sand"), (1781, "bamboo", "dirt"), (1783, "cactus", "sand"))


def item(slot, name, count=1, damage=None):
    tag = f",tag:{{Damage:{damage}}}" if damage is not None else ""
    return f'{{Slot:{slot},id:"{name}",Count:{count}{tag}}}'


CAPACITOR = item(0, "enderio:basic_capacitor")
FILLED = [item(slot, "minecraft:cobblestone", 64) for slot in range(11, 16)]


def stock_station(server, pos, items):
    server.command(f"setblock {pos} enderio:farming_station")
    entries = ",".join([CAPACITOR, *items])
    server.command(f'data merge block {pos} {{RedstoneControl:"always_active",EnergyStored:6000,'
                   f'Items:{{Items:[{entries}]}}}}')


def inventory(pos, items):
    entries = ",".join([CAPACITOR, *items])
    return f"if data block {pos} {{Items:{{Items:[{entries}]}}}}"


def blocks(positions, name):
    return " ".join(f"if block {pos} minecraft:{name}" for pos in positions)


def held_trunk():
    items = [item(1, "minecraft:diamond_axe", damage=1), item(7, "minecraft:oak_sapling", 2),
             item(10, "minecraft:oak_log", 64), *FILLED]
    return (f"{blocks(TRUNK[:2], 'oak_log')} {blocks(TRUNK[2:], 'air')} "
            f"{inventory(TRUNK_STATION, items)}")


def held_branch():
    items = [item(3, "minecraft:shears", damage=1), item(4, "minecraft:oak_sapling"),
             item(10, "minecraft:oak_leaves"), item(11, "minecraft:oak_log")]
    return (f"{blocks(BRANCH[:3], 'oak_log')} {blocks(BRANCH[3:], 'air')} "
            f"unless data block {BRANCH_STATION} Items.Items[{{Slot:1}}] {inventory(BRANCH_STATION, items)}")


def trimmed_stalks():
    segments = []
    for x, plant, _ in STALKS:
        segments.extend((blocks([f"{x} 80 3"], plant), blocks([f"{x} 81 3", f"{x} 82 3"], "air"),
                         blocks([f"{x} 83 3"], "glass")))
    harvest = [item(slot, f"minecraft:{plant}", 2) for slot, (_, plant, _) in enumerate(STALKS, 10)]
    return f"{' '.join(segments)} {inventory(STALK_STATION, [item(7, 'minecraft:stick'), *harvest])}"


def fresh(server, results):
    server.command(f"forceload add {CHUNKS}")
    server.assert_state("orchards_and_cross_chunk_branch_are_loaded",
                        f"if loaded {TRUNK_STATION} if loaded {BRANCH[-1]} if loaded {STALK_STATION}", results)
    server.command("gamerule doMobSpawning false")
    server.command("gamerule randomTickSpeed 0")
    server.command("setblock 1715 79 3 minecraft:dirt")
    server.command("setblock 1758 79 3 minecraft:dirt")
    for pos in TRUNK + BRANCH[:-1]:
        server.command(f"setblock {pos} minecraft:oak_log")
    server.command(f"setblock {BRANCH[-1]} minecraft:oak_leaves[persistent=true]")
    stock_station(server, TRUNK_STATION, [item(1, "minecraft:diamond_axe"), item(7, "minecraft:oak_sapling", 2),
                                         item(10, "minecraft:oak_log", 63), *FILLED])
    stock_station(server, BRANCH_STATION, [item(1, "minecraft:golden_axe", damage=31), item(3, "minecraft:shears"),
                                          item(4, "minecraft:oak_sapling")])
    server.assert_state("one_free_slot_takes_only_the_highest_log", held_trunk(), results, timeout=20)
    server.assert_state("one_remaining_axe_use_harvests_the_outer_branch_and_keeps_a_connected_trunk",
                        held_branch(), results, timeout=20)

    for station in (TRUNK_STATION, BRANCH_STATION):
        server.command(f'data merge block {station} {{RedstoneControl:"never_active",EnergyStored:0}}')
    server.command("fill 1777 78 2 1786 79 6 minecraft:stone")
    server.command("setblock 1778 79 3 minecraft:water")
    for x, plant, soil in STALKS:
        server.command(f"setblock {x} 79 3 minecraft:{soil}")
        server.command(f"fill {x} 80 3 {x} 82 3 minecraft:{plant}")
        server.command(f"setblock {x} 83 3 minecraft:glass")
    stock_station(server, STALK_STATION, [item(7, "minecraft:stick")])
    server.assert_state("capped_cane_bamboo_and_cactus_each_yield_two_segments_above_their_base",
                        trimmed_stalks(), results, timeout=20)

    server.command(f"forceload remove {CHUNKS}")
    server.assert_state("interrupted_orchards_and_tall_crops_unload",
                        f"unless loaded {TRUNK_STATION} unless loaded {BRANCH[-1]} unless loaded {STALK_STATION}",
                        results, timeout=45)
    server.command(f"forceload add {CHUNKS}")
    server.assert_state("chunk_reload_keeps_the_trunks_sheared_leaf_and_stalk_outputs",
                        f"{held_trunk()} {held_branch()} {trimmed_stalks()}", results, timeout=15)


def reopened(server, results):
    server.assert_state("interrupted_harvests_and_trimmed_crops_survive_restart",
                        f"{held_trunk()} {held_branch()} {trimmed_stalks()}", results, timeout=15)
    server.command(f"data modify block {TRUNK_STATION} Items.Items[{{Slot:10}}].Count set value 61")
    server.command(f'data modify block {BRANCH_STATION} Items.Items append value {item(1, "minecraft:diamond_axe")}')
    for station in (TRUNK_STATION, BRANCH_STATION):
        server.command(f'data merge block {station} {{RedstoneControl:"always_active",EnergyStored:6000}}')
    trunk = inventory(TRUNK_STATION, [item(1, "minecraft:diamond_axe", damage=3), item(7, "minecraft:oak_sapling"),
                                     item(10, "minecraft:oak_log", 63), *FILLED])
    branch = inventory(BRANCH_STATION, [item(1, "minecraft:diamond_axe", damage=3), item(3, "minecraft:shears", damage=1),
                                       item(4, "minecraft:oak_sapling"), item(10, "minecraft:oak_leaves"),
                                       item(11, "minecraft:oak_log", 4)])
    server.assert_state("freeing_output_finishes_the_trunk_and_replants_one_sapling",
                        f"{blocks(TRUNK[:1], 'oak_sapling')} {blocks(TRUNK[1:], 'air')} {trunk}", results, timeout=30)
    server.assert_state("replacement_axe_collects_all_remaining_logs_without_duplicate_leaves",
                        f"{blocks(BRANCH, 'air')} {branch}", results, timeout=30)


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
        run_stage(args, directory, report, "reopened", reopened)
        report["passed"] = True
    finally:
        path = directory / "farming-station-harvest-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Farming Station harvest passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
