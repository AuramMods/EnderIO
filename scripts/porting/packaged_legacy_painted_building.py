#!/usr/bin/env python3
"""Convert painted building blocks, replant them in water and resume repainting jobs."""

import argparse
import json
from pathlib import Path
import shutil
import sys

sys.dont_write_bytecode = True
from packaged_painted_building import CORNER, JOINED, SAMPLES, build_scene, exercise, load, loot_state, painted, reopen
from packaged_painted_building import run_client, saved_shapes, waterlogged
from packaged_painting_machine import CAPACITOR, COST, inventory, paint_tag, palette
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage


LEGACY_ROW = 34
LEGACY_LOOT = "2398 80 13"
REPAINT_LOOT = "2397 80 13"
PAID = 600
NEW_PAINTS = ("gold_block", "diamond_block", "obsidian", "emerald_block")


def item(kind, paint):
    return f'id:"enderio:painted_{kind}",{paint_tag(paint)}'


def saved_originals():
    return " ".join(painted(slot, waterlogged(props, slot < 2), LEGACY_ROW)
                    for slot, props in enumerate((CORNER.replace("bottom", "top"), JOINED, "", "")))


def load_rows(server, results, label):
    load(server, results, label)
    server.command("forceload add 2384 16 2431 47")
    server.assert_state(label + "_originals_and_painters", "if loaded 2400 80 34 if loaded 2421 80 17", results)


def drain(server, x, z):
    # Keep the solid support under the source. Water escapes down adjacent shafts.
    for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        server.command(f"setblock {x + dx} 79 {z + dz} minecraft:air")


def legacy_fixture(server, results):
    load_rows(server, results, "forge6_painted_building_chunks_loaded")
    build_scene(server)
    server.command("fill 2397 79 32 2425 79 39 minecraft:stone_bricks")
    for x, *_ in SAMPLES[:2]:
        drain(server, x, LEGACY_ROW)
    server.command(f"setblock 2400 80 {LEGACY_ROW - 1} minecraft:spruce_stairs[facing=west,half=top]")
    for x in (2406, 2408):
        server.command(f"setblock {x} 80 {LEGACY_ROW} minecraft:cobblestone_wall")
    states = (waterlogged(CORNER.replace("bottom", "top"), True), waterlogged(JOINED, True), "", "")
    for slot, (x, kind, _, paint, _) in enumerate(SAMPLES):
        server.command(f'setblock {x} 80 {LEGACY_ROW} enderio:painted_{kind}{states[slot]}{{Paint:"minecraft:{paint}"}}')
    server.assert_state("forge6_saves_original_paints_corner_wall_joins_and_water", saved_originals(), results)
    for barrel in (LEGACY_LOOT, REPAINT_LOOT):
        server.command(f"setblock {barrel} minecraft:barrel")
    for slot, (x, kind, _, paint, tool) in enumerate(SAMPLES):
        checks = []
        for barrel in (LEGACY_LOOT, REPAINT_LOOT):
            server.command(f"loot replace block {barrel} container.{slot} mine {x} 80 {LEGACY_ROW} minecraft:diamond_{tool}")
            checks.append(f'if data block {barrel} Items[{{Slot:{slot}b,Count:1b,id:"enderio:painted_{kind}",'
                          f'tag:{{BlockEntityTag:{{Paint:"minecraft:{paint}"}}}}}}]')
        server.assert_state(f"forge6_native_loot_copies_both_{kind}_items", " ".join(checks), results)


def check_imported_loot(server, results):
    for slot, (_, kind, _, paint, _) in enumerate(SAMPLES):
        checks = [
            f'if data block {barrel} Items[{{Slot:{slot}b,Count:1b,{item(kind, paint)}}}] '
            f"unless data block {barrel} Items[{{Slot:{slot}b}}].tag.BlockEntityTag.Paint"
            for barrel in (LEGACY_LOOT, REPAINT_LOOT)
        ]
        server.assert_state(f"both_original_{kind}_items_convert_to_typed_paint", " ".join(checks), results)


def pending(x, kind, original, new):
    machine = f"{x} 80 17"
    template = f'{{Slot:1,Count:1,{item(kind, original)}}}'
    recipe = f'{{RecipeId:"enderio:painting/painted_{kind}_frompainted",ProgressMade:{PAID},ProgressRequired:{COST}}}'
    return (f"{inventory(machine, [template, palette(new)], 0)} if data block {machine} {{Task:{recipe}}} "
            f"unless data block {x} 81 17 Items[0] unless data block {machine} Items.Items[{{Slot:3}}]")


def start_repainting(server, results):
    for slot, ((x, kind, _, original, _), new) in enumerate(zip(SAMPLES, NEW_PAINTS)):
        machine = f"{x} 80 17"
        server.command(f"setblock {machine} enderio:painting_machine[facing=south]")
        server.command(f'data merge block {machine} {{EnergyStored:{PAID},Items:{{Items:[{CAPACITOR},{palette(new)}]}}}}')
        server.command(f"setblock {x} 81 17 minecraft:hopper[facing=down]")
        server.command(f"item replace block {x} 81 17 container.0 from block {REPAINT_LOOT} container.{slot}")
        server.command(f"item replace block {REPAINT_LOOT} container.{slot} with minecraft:air")
        server.assert_state(f"native_hopper_feeds_original_{kind}_into_the_repainting_recipe",
                            pending(x, kind, original, new), results)


def finish_repainting(server, results):
    for (x, kind, _, original, _), new in zip(SAMPLES, NEW_PAINTS):
        server.assert_state(f"repainting_{kind}_retains_exact_paid_progress_after_restart",
                            pending(x, kind, original, new), results)
        machine, hopper = f"{x} 80 17", f"{x} 79 17"
        server.command(f"setblock {hopper} minecraft:hopper[facing=down]")
        server.command(f"data merge block {machine} {{EnergyStored:{COST - PAID}}}")
        server.assert_state(f"repainting_{kind}_uses_only_the_remaining_charge_and_outputs_new_paint",
                            f'if data block {hopper} Items[{{Slot:0b,Count:1b,{item(kind, new)}}}] '
                            f"{inventory(machine, [palette(new)], 0)} unless data block {machine} Task "
                            f"unless data block {machine} Items.Items[{{Slot:1}}] "
                            f"unless data block {machine} Items.Items[{{Slot:3}}]", results, timeout=20)


def convert(args, server, report):
    results = report["assertions"]
    load_rows(server, results, "converted_painted_building_chunks_loaded")
    server.assert_state("conversion_retains_all_original_paints_shapes_and_source_water", saved_originals(), results)
    check_imported_loot(server, results)
    start_repainting(server, results)
    for x, *_ in SAMPLES[:2]:
        drain(server, x, 8)
        server.command(f"setblock {x} 80 8 minecraft:water")
    # Match the dry scene's non-emitting backdrop for both midnight frames.
    for x, *_ in SAMPLES[2:]:
        server.command(f"setblock {x} 80 3 enderio:painting_machine[facing=south]")
    server.assert_state("both_survival_replant_sites_contain_real_water_sources",
                        "if block 2400 80 8 minecraft:water[level=0] if block 2407 80 8 minecraft:water[level=0]", results)
    run_client(args, server, report, "exercise",
               lambda active, checks, client, output: exercise(active, checks, client, output, legacy_loot=LEGACY_LOOT), wet=True)
    server.assert_state("survival_uses_one_original_item_of_each_kind_and_keeps_the_original_scene",
                        f"{saved_originals()} unless data block {LEGACY_LOOT} Items[0] "
                        f"unless data block {REPAINT_LOOT} Items[0]", results)


def restart(args, server, report):
    results = report["assertions"]
    load_rows(server, results, "painted_building_chunks_loaded_after_restart")
    server.assert_state("original_and_replanted_shapes_paint_water_and_loot_survive_restart",
                        f"{saved_originals()} {saved_shapes(wet=True)} {loot_state()}", results)
    finish_repainting(server, results)
    run_client(args, server, report, "verify",
               lambda active, checks, client, output: reopen(active, checks, client, output, wet=True), wet=True)


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
    report = {"passed": False, "assertions": [], "servers": [], "clients": {},
              "legacySha256": digest(args.legacy_jar), "jarSha256": digest(args.jar)}
    try:
        run_stage(args, legacy, report, "legacy", legacy_fixture)
        shutil.copytree(legacy / WORLD, port / WORLD)
        run_stage(args, port, report, "converted", lambda server, results: convert(args, server, report))
        run_stage(args, port, report, "reopened", lambda server, results: restart(args, server, report))
        report["passed"] = True
    finally:
        (directory / "legacy-painted-building-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
