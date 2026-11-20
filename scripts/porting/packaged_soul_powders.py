#!/usr/bin/env python3
"""Verify the original Soularium, Wither Skeleton skull, and Wither Rose powder routes."""

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import sys
from zipfile import ZipFile

sys.dont_write_bytecode = True
from packaged_glass_progression import Smelt, finished_smelter, prepare_smelter, start_smelter, supply, transfer
from packaged_photovoltaic_progression import COST, RESERVE, Grind, batch_loaded, empty_mill
from packaged_plant_matter import paid
from packaged_sag_progression import charge, data_read, feed, inputs, mill, output, prepare_mill
from packaged_server_smoke import ROOT, digest
from packaged_solar_charging import check
from packaged_spawners import WORLD, prepare_profile, run_stage
from packaged_starter_progression import hopper_item
from saved_world import read_chunk


DEPOSIT, SAMPLE_COUNT = 600, 24
SOULARIUM = Smelt(3970, 8, "soularium_ingot", 1, 5600)
SOUL = "enderio:soul_powder"
WITHERING = "enderio:withering_powder"
CONFUSION = "enderio:confusing_powder"
ARCHIVE, ARCHIVE_FEED = "3973 79 12", "3973 80 12"


@dataclass(frozen=True)
class Route:
    recipe: str
    grind: Grind
    lower: int
    upper: int


SOUL_ROUTE = Route("soularium", Grind(3974, "enderio:soularium_ingot", SOUL, 1), 1, 1)
SKULLS = tuple(Route("wither_skull", Grind(3978 + 2 * index, "minecraft:wither_skeleton_skull", WITHERING, 1), 2, 3)
               for index in range(SAMPLE_COUNT))
ROSES = tuple(Route("wither_rose", Grind(4026 + 2 * index, "minecraft:wither_rose", WITHERING, 1), 0, 1)
              for index in range(SAMPLE_COUNT))
ROUTES = (SOUL_ROUTE, *SKULLS, *ROSES)


def archive_state():
    return (f"{hopper_item(ARCHIVE, CONFUSION, 3)} unless data block {ARCHIVE} Items[1] "
            f"unless data block {ARCHIVE_FEED} Items[0]")


def paused(route):
    grind = route.grind
    pos = mill(grind.x)
    payment = paid(pos, "sag_milling/" + route.recipe, DEPOSIT, COST)
    return (f'{payment} if data block {pos} Items.Items[{{Slot:0,id:"{grind.material}",Count:1}}] '
            f"unless data block {feed(grind.x)} Items[0] unless data block {output(grind.x)} Items[0]")


def prepare(server, results, report):
    server.command("forceload add 3968 0 4079 15")
    server.assert_state("the_smelter_all_powder_mills_and_confusion_archive_are_loaded",
                        f"if loaded {SOULARIUM.pos(80)} if loaded {mill(ROUTES[-1].grind.x)} if loaded {ARCHIVE}", results)
    server.command(f"setblock {ARCHIVE} minecraft:barrel")
    server.command(f"setblock {ARCHIVE_FEED} minecraft:hopper[facing=down]")
    server.command(f"item replace block {ARCHIVE_FEED} container.0 with {CONFUSION} 3")
    prepare_smelter(server, SOULARIUM)
    supply(server, SOULARIUM, 0, "minecraft:soul_sand", 1)
    supply(server, SOULARIUM, 1, "minecraft:gold_ingot", 1)
    server.assert_state("native_inputs_feed_gold_and_soul_sand_to_the_stock_smelter",
                        f'if data block {SOULARIUM.pos(80)} Items.Items[{{id:"minecraft:soul_sand",Count:1}}] '
                        f'if data block {SOULARIUM.pos(80)} Items.Items[{{id:"minecraft:gold_ingot",Count:1}}] '
                        f"unless data block {SOULARIUM.pos(81)} Items[0]", results)
    start_smelter(server, SOULARIUM)
    for route in ROUTES:
        grind = route.grind
        prepare_mill(server, grind.x)
        if route != SOUL_ROUTE:
            inputs(server, grind.x, grind.material, 1)
    server.assert_state("the_original_soularium_costs_exactly_5600_fe", finished_smelter(SOULARIUM), results)
    transfer(server, SOULARIUM.pos(79), 0, feed(SOUL_ROUTE.grind.x), 0)
    for route in ROUTES:
        grind = route.grind
        batch_loaded(server, results, grind, 1)
        charge(server, grind.x, DEPOSIT)
    for route in ROUTES:
        grind = route.grind
        server.assert_state(f"{route.recipe}_{grind.x}_saves_600_fe_and_its_native_roll", paused(route), results)
        report["rolls"][str(grind.x)] = {"recipe": route.recipe, "queued": data_read(server, mill(grind.x), "Task.Outputs")}
    server.assert_state("registered_confusion_powder_moves_as_a_native_item_through_a_hopper", archive_state(), results)


def record_saved_yields(directory, report):
    chunks = {x: read_chunk(directory / WORLD, x, 0)["block_entities"] for x in {route.grind.x // 16 for route in ROUTES}}
    for route in ROUTES:
        grind = route.grind
        entity, = (e for e in chunks[grind.x // 16] if (e["x"], e["y"], e["z"]) == (grind.x, 80, 8))
        stacks = [output["Item"] for output in entity["Task"]["Outputs"]]
        count = sum(stack["Count"] for stack in stacks)
        check(f"{route.recipe}_{grind.x}_has_only_its_stock_powder_and_valid_yield",
              True, route.lower <= count <= route.upper and all(stack["id"] == grind.dust for stack in stacks), report["assertions"])
        report["rolls"][str(grind.x)]["count"] = count


def preserved_output(server, results, report, phase):
    for route in ROUTES:
        grind = route.grind
        count = report["rolls"][str(grind.x)]["count"]
        hopper = output(grind.x)
        inventory = f"{hopper_item(hopper, grind.dust, count)} unless data block {hopper} Items[1]" if count else (
            f"unless data block {hopper} Items[0]")
        server.assert_state(f"{route.recipe}_{grind.x}_{phase}_retains_the_exact_powder_yield_and_reserve",
                            f"{empty_mill(grind)} {inventory}", results)
    server.assert_state(f"{phase}_keeps_confusion_powder_and_the_original_smelter_balance",
                        f"{archive_state()} if data block {SOULARIUM.pos(80)} {{EnergyStored:{RESERVE}}} "
                        f"unless data block {SOULARIUM.pos(80)} Task unless data block {SOULARIUM.pos(79)} Items[0]", results)


def resume(server, results, report):
    for route in ROUTES:
        grind = route.grind
        server.assert_state(f"{route.recipe}_{grind.x}_restores_the_original_input_and_paid_progress", paused(route), results)
        record = report["rolls"][str(grind.x)]
        check(f"{route.recipe}_{grind.x}_restores_its_exact_queued_powder", record["queued"],
              data_read(server, mill(grind.x), "Task.Outputs"), results)
        charge(server, grind.x, COST - DEPOSIT + RESERVE)
    preserved_output(server, results, report, "completed")


def verify_confusion_assets(path, results):
    with ZipFile(path) as jar:
        model = json.loads(jar.read("assets/enderio/models/item/confusing_powder.json"))
        lang = json.loads(jar.read("assets/enderio/lang/en_us.json"))
        check("confusion_powder_keeps_its_shipped_model_and_texture", "enderio:item/confusing_powder", model["textures"]["layer0"], results)
        check("confusion_powder_texture_is_present_in_the_production_jar", True,
              jar.read("assets/enderio/textures/item/confusing_powder.png").startswith(b"\x89PNG\r\n\x1a\n"), results)
        check("confusion_powder_keeps_its_shipped_name", "Confusion Powder", lang["item.enderio.confusing_powder"], results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for key in ("java", "forge-server", "jar"):
        parser.add_argument(f"--{key}", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")

    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    jar = directory / "mods/enderio.jar"
    report = {"passed": False, "assertions": [], "servers": [], "rolls": {}, "jarSha256": digest(jar)}
    try:
        run_stage(args, directory, report, "fresh", lambda server, results: prepare(server, results, report))
        record_saved_yields(directory, report)
        run_stage(args, directory, report, "resume", lambda server, results: resume(server, results, report))
        run_stage(args, directory, report, "reopened", lambda server, results: preserved_output(server, results, report, "reopened"))
        verify_confusion_assets(jar, report["assertions"])
        report["passed"] = True
    finally:
        result = directory / "soul-powder-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Soul powders passed={report['passed']}, {len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
