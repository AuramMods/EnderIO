#!/usr/bin/env python3
"""Convert four powered painted blocks and native loot from Forge 6.2."""

import argparse
import json
from pathlib import Path
import shutil
import sys

sys.dont_write_bytecode = True
from packaged_painted_redstone import SAMPLES, build_scene, exercise_client, item, load, place_receivers, placed_state, reopen
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage


LEGACY_ROW = 3
LEGACY_LOOT = "2198 80 13"
LEGACY_TRIGGER = "legacy_painted_plate_trigger"


def original_states(active=True):
    states = [placed_state(sample, open_=active and slot >= 2, powered=active and slot >= 1,
                           waterlogged=True, z=LEGACY_ROW) for slot, sample in enumerate(SAMPLES)]
    states.extend((f"if block 2214 80 {LEGACY_ROW} enderio:painted_fence_gate[facing=north]",
                   f"if block 2221 80 {LEGACY_ROW} enderio:painted_trapdoor[facing=north,half=top]"))
    return " ".join(states)


def old_loot(slot, kind, paint):
    return (f'if data block {LEGACY_LOOT} Items[{{Slot:{slot}b,id:"enderio:painted_{kind}",Count:1b,'
            f'tag:{{BlockEntityTag:{{Paint:"minecraft:{paint}"}}}}}}]')


def legacy_fixture(server, results):
    load(server, results, "forge6_painted_redstone_chunks_loaded")
    build_scene(server)
    place_receivers(server, LEGACY_ROW)
    for x, z in ((2220, LEGACY_ROW), (2222, LEGACY_ROW), (2221, LEGACY_ROW + 1), (2221, LEGACY_ROW - 1)):
        server.command(f"setblock {x} 80 {z} minecraft:stone_bricks")
    properties = ("", "[powered=false]", "[facing=north,in_wall=true,open=false,powered=false]",
                  "[facing=north,half=top,waterlogged=true,open=false,powered=false]")
    for (x, kind, _, paint), state in zip(SAMPLES, properties):
        server.command(f'setblock {x} 80 {LEGACY_ROW} enderio:painted_{kind}{state}{{Paint:"minecraft:{paint}"}}')
    server.assert_state("original_blocks_have_their_saved_paint_and_orientation", original_states(False), results)
    server.command(f'summon minecraft:item 2207.5 80 {LEGACY_ROW + 0.5} {{Tags:["{LEGACY_TRIGGER}"],NoGravity:1b,'
                   'PickupDelay:32767s,Item:{id:"minecraft:cobblestone",Count:1b}}')
    for x, _, _, _ in SAMPLES[2:]:
        server.command(f"setblock {x} 80 {LEGACY_ROW - 1} minecraft:redstone_block")
    server.assert_state("forge6_saves_active_redstone_and_the_waterlogged_open_trapdoor", original_states(), results)
    server.command(f"setblock {LEGACY_LOOT} minecraft:barrel")
    for slot, (x, kind, _, paint) in enumerate(SAMPLES):
        tool = "pickaxe" if slot == 0 else "axe"
        server.command(f"loot replace block {LEGACY_LOOT} container.{slot} mine {x} 80 {LEGACY_ROW} minecraft:diamond_{tool}")
        server.assert_state(f"forge6_native_loot_copies_{kind}_paint", old_loot(slot, kind, paint), results)


def converted(args, server, report):
    results = report["assertions"]
    load(server, results, "converted_painted_redstone_chunks_loaded")
    server.assert_state("conversion_preserves_original_paints_power_orientation_and_water", original_states(), results)
    for slot, (_, kind, _, paint) in enumerate(SAMPLES):
        server.assert_state(f"original_{kind}_loot_imports_to_typed_item_data",
                            f'if data block {LEGACY_LOOT} Items[{{Slot:{slot}b,Count:1b,{item(kind, paint)}}}] '
                            f"unless data block {LEGACY_LOOT} Items[{{Slot:{slot}b}}].tag.BlockEntityTag.Paint", results)
    place_receivers(server)
    # The source stays over a solid support. Its south edge drains away from the player's platform.
    server.command("fill 2221 79 9 2221 79 10 minecraft:air")
    for x, z in ((2220, 8), (2222, 8), (2221, 7)):
        server.command(f"setblock {x} 80 {z} minecraft:stone_bricks")
    server.command("setblock 2221 80 8 minecraft:water")
    server.assert_state("replanting_cell_holds_a_real_water_source", "if block 2221 80 8 minecraft:water[level=0]", results)
    exercise_client(args, server, report, legacy_loot=LEGACY_LOOT)
    server.assert_state("survival_uses_all_four_original_forge6_loot_items",
                        f"{original_states()} unless data block {LEGACY_LOOT} Items[0]", results)


def restarted(server, results):
    reopen(server, results, wet=True)
    server.assert_state("original_forge6_blocks_remain_powered_after_dedicated_restart", original_states(), results)
    server.command(f"kill @e[type=item,tag={LEGACY_TRIGGER}]")
    for x, _, _, _ in SAMPLES[2:]:
        replacement = "minecraft:stone_bricks" if x == 2221 else "minecraft:air"
        server.command(f"setblock {x} 80 {LEGACY_ROW - 1} {replacement}")
    server.assert_state("migrated_gate_and_trapdoor_close_and_the_plate_releases_with_paint_intact",
                        original_states(False), results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for path in ("java", "forge-server", "legacy-jar", "jar", "launch"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    directory.mkdir()
    legacy = directory / "legacy"
    port = directory / "port"
    prepare_profile(args, legacy, args.legacy_jar)
    prepare_profile(args, port, args.jar)
    report = {"passed": False, "assertions": [], "servers": [],
              "jarSha256": digest(args.jar), "legacySha256": digest(args.legacy_jar)}
    try:
        run_stage(args, legacy, report, "legacy", legacy_fixture)
        shutil.copytree(legacy / WORLD, port / WORLD)
        run_stage(args, port, report, "converted", lambda server, results: converted(args, server, report))
        run_stage(args, port, report, "reopened", restarted)
        report["passed"] = True
    finally:
        (directory / "legacy-painted-redstone-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
