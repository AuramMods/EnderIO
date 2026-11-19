#!/usr/bin/env python3
"""Follow native stock plant rolls into all four green and brown dye recipes."""

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import sys

sys.dont_write_bytecode = True
from packaged_glass_progression import Smelt, prepare_smelter, supply, transfer
from packaged_photovoltaic_progression import COST, RESERVE, Grind, batch_loaded, empty_mill
from packaged_sag_progression import charge, data_read, feed, inputs, mill, output, prepare_mill
from packaged_server_smoke import ROOT, digest
from packaged_solar_charging import check
from packaged_soul_binder import item
from packaged_spawners import WORLD, prepare_profile, run_stage
from packaged_starter_progression import hopper_item
from saved_world import read_chunk


MILL_DEPOSIT, SMELTER_DEPOSIT = 600, 400
INITIAL_PLANTS, EXTRA_PLANTS = 24, 12
EGG_COST, SLIME_COST = 1000, 1600
EGG_MATTER, SLIME_MATTER = 6, 12
GREEN, BROWN = "enderio:plant_matter_green", "enderio:plant_matter_brown"


@dataclass(frozen=True)
class Route:
    recipe: str
    grind: Grind
    smelt: Smelt
    dye_recipes: tuple[str, str]
    allowed: tuple[tuple[str, int], ...]

    @property
    def dye(self):
        return f"minecraft:{self.smelt.output}"


ROUTES = (
    Route("large_fern", Grind(3651, "minecraft:large_fern", GREEN, INITIAL_PLANTS), Smelt(3659, 8, "green_dye", 1, EGG_COST),
          ("green_dye_clippings", "green_dye_double_clippings"), ((GREEN, 5), (BROWN, 1))),
    Route("shrub", Grind(3655, "minecraft:dead_bush", BROWN, INITIAL_PLANTS), Smelt(3663, 8, "brown_dye", 1, EGG_COST),
          ("brown_dye_twigs", "brown_dye_twigs_double"), ((BROWN, 3),)),
)


def paid(pos, recipe, amount, cost):
    return (f'if data block {pos} {{EnergyStored:0,Task:{{RecipeId:"enderio:{recipe}",ProgressMade:{amount},'
            f'ProgressRequired:{cost},HasDeterminedOutputs:1b}}}}')


def paused_mill(route):
    grind = route.grind
    pos = mill(grind.x)
    return (f'{paid(pos, "sag_milling/" + route.recipe, MILL_DEPOSIT, COST)} '
            f'if data block {pos} Items.Items[{{Slot:0,id:"{grind.material}",Count:{grind.count}}}] '
            f"unless data block {feed(grind.x)} Items[0] unless data block {output(grind.x)} Items[0]")


def start_milling(server, results, report):
    server.command("forceload add 3648 0 3663 15")
    server.assert_state("both_mills_and_both_smelters_are_in_the_loaded_chunk",
                        f"if loaded {mill(ROUTES[0].grind.x)} if loaded {ROUTES[-1].smelt.pos(80)}", results)
    for route in ROUTES:
        grind = route.grind
        prepare_mill(server, grind.x)
        server.command(f'data merge block {mill(grind.x)} {{Items:{{Items:[{item(6, "enderio:octadic_capacitor")}]}}}}')
        inputs(server, grind.x, grind.material, grind.count)
    for route in ROUTES:
        grind = route.grind
        batch_loaded(server, results, grind, grind.count)
        charge(server, grind.x, MILL_DEPOSIT)
        server.assert_state(f"{route.recipe}_saves_an_actual_stock_roll_after_{MILL_DEPOSIT}_fe", paused_mill(route), results)
        report["plants"][route.recipe] = {"firstRoll": data_read(server, mill(grind.x), "Task.Outputs"), "inputs": grind.count}


def hopper_snapshot(server, route):
    x = route.grind.x
    chunk = read_chunk(server.directory / WORLD, x // 16, 0)
    hopper, = (entity for entity in chunk["block_entities"] if (entity["x"], entity["y"], entity["z"]) == (x, 79, 8))
    return hopper["Items"]


def yield_total(stacks, material):
    return sum(stack["Count"] for stack in stacks if stack["id"] == material)


def exact_hopper(pos, stacks):
    counts = [f'if data block {pos} Items[{{Slot:{stack["Slot"]}b,id:"{stack["id"]}",Count:{stack["Count"]}b}}]'
              for stack in stacks]
    counts.append(f"unless data block {pos} Items[{len(stacks)}]")
    return " ".join(counts)


def finish_milling(server, results, report):
    for route in ROUTES:
        grind = route.grind
        server.assert_state(f"{route.recipe}_restores_its_original_stock_job", paused_mill(route), results)
        old = report["plants"][route.recipe]["firstRoll"]
        check(f"{route.recipe}_keeps_its_exact_first_roll", old, data_read(server, mill(grind.x), "Task.Outputs"), results)
        charge(server, grind.x, grind.count * COST - MILL_DEPOSIT + RESERVE)
    for route in ROUTES:
        server.assert_state(f"{route.recipe}_finishes_every_input_on_its_stock_energy_budget", empty_mill(route.grind), results, timeout=130)
    server.command("save-all flush")
    for route in ROUTES:
        grind = route.grind
        record = report["plants"][route.recipe]
        stacks = hopper_snapshot(server, route)
        if yield_total(stacks, grind.dust) < EGG_MATTER + SLIME_MATTER:
            inputs(server, grind.x, grind.material, EXTRA_PLANTS)
            batch_loaded(server, results, grind, EXTRA_PLANTS)
            server.assert_state(f"{route.recipe}_starts_the_additional_inputs_with_the_existing_reserve",
                                paid(mill(grind.x), "sag_milling/" + route.recipe, RESERVE, COST), results)
            charge(server, grind.x, EXTRA_PLANTS * COST)
            record["inputs"] += EXTRA_PLANTS
            server.assert_state(f"{route.recipe}_finishes_additional_input_{record['inputs']}", empty_mill(grind), results, timeout=75)
            server.command("save-all flush")
            stacks = hopper_snapshot(server, route)
        types = {material for material, _ in route.allowed}
        check(f"{route.recipe}_exports_only_stock_output_types_at_stock_yield_bounds", True,
              yield_total(stacks, grind.dust) >= EGG_MATTER + SLIME_MATTER
              and all(stack["id"] in types for stack in stacks)
              and all(yield_total(stacks, material) <= record["inputs"] * max_yield for material, max_yield in route.allowed), results)
        record["milled"] = stacks
        record["feSpent"] = record["inputs"] * COST


def smelter_inputs(route, matter, secondary=None):
    pos = route.smelt.pos(80)
    if matter:
        condition = f'if data block {pos} Items.Items[{{Slot:0,id:"{route.grind.dust}",Count:{matter}}}]'
    else:
        condition = f"unless data block {pos} Items.Items[{{Slot:0}}]"
    if secondary:
        condition += f' if data block {pos} Items.Items[{{Slot:1,id:"{secondary}",Count:1}}]'
    else:
        condition += f" unless data block {pos} Items.Items[{{Slot:1}}]"
    return f"{condition} unless data block {route.smelt.pos(81)} Items[0] unless data block {pos} Items.Items[{{Slot:2}}]"


def charge_smelter(server, route, amount):
    server.command(f"data merge block {route.smelt.pos(80)} {{EnergyStored:{amount}}}")


def start_dyes(server, results, report):
    for route in ROUTES:
        job = route.smelt
        source = output(route.grind.x)
        record = report["plants"][route.recipe]
        stacks = record["milled"]
        original = max((stack for stack in stacks if stack["id"] == route.grind.dust), key=lambda stack: stack["Count"])
        record["sentToSmelter"] = original["Count"]
        record["remainingInHopper"] = [stack for stack in stacks if stack["Slot"] != original["Slot"]]
        prepare_smelter(server, job)
        transfer(server, source, original["Slot"], job.pos(81), 0)
        supply(server, job, 1, "minecraft:egg", 1)
        loaded = smelter_inputs(route, original["Count"], "minecraft:egg")
        server.assert_state(f"{route.recipe}_native_smelter_accepts_the_original_milled_stack_and_one_egg", loaded, results)
        charge_smelter(server, route, SMELTER_DEPOSIT)
        pause = paid(job.pos(80), "alloy_smelting/" + route.dye_recipes[0], SMELTER_DEPOSIT, EGG_COST)
        server.assert_state(f"{route.recipe}_egg_recipe_saves_after_400_fe_with_its_original_inputs", f"{pause} {loaded}", results)
        record["eggRoll"] = data_read(server, job.pos(80), "Task.Outputs")


def finished_dyes(route, remaining, count):
    job = route.smelt
    pos = job.pos(80)
    return (f"if data block {pos} {{EnergyStored:{RESERVE}}} unless data block {pos} Task "
            f"{smelter_inputs(route, remaining)} unless data block {pos} Items.Items[{{Slot:3}}] "
            f"{hopper_item(job.pos(79), route.dye, count)} unless data block {job.pos(79)} Items[1]")


def finish_dyes(server, results, report):
    for route in ROUTES:
        job = route.smelt
        record = report["plants"][route.recipe]
        original = record["sentToSmelter"]
        pause = paid(job.pos(80), "alloy_smelting/" + route.dye_recipes[0], SMELTER_DEPOSIT, EGG_COST)
        server.assert_state(f"{route.recipe}_egg_recipe_and_inputs_survive_restart",
                            f'{pause} {smelter_inputs(route, original, "minecraft:egg")}', results)
        check(f"{route.recipe}_keeps_the_exact_queued_dye", record["eggRoll"], data_read(server, job.pos(80), "Task.Outputs"), results)
        charge_smelter(server, route, EGG_COST - SMELTER_DEPOSIT + RESERVE)
        remaining = original - EGG_MATTER
        server.assert_state(f"{route.recipe}_egg_recipe_spends_six_and_delivers_one_dye",
                            finished_dyes(route, remaining, 1), results, timeout=18)
        supply(server, job, 0, "minecraft:slime_ball", 1)
        loaded = smelter_inputs(route, remaining, "minecraft:slime_ball")
        server.assert_state(f"{route.recipe}_routes_one_slimeball_to_the_remaining_stock", loaded, results)
        pause = paid(job.pos(80), "alloy_smelting/" + route.dye_recipes[1], RESERVE, SLIME_COST)
        server.assert_state(f"{route.recipe}_uses_the_exact_stock_slime_recipe_and_cost", f"{pause} {loaded}", results)
        charge_smelter(server, route, SLIME_COST)
        server.assert_state(f"{route.recipe}_slime_recipe_spends_twelve_and_delivers_two_more_dye",
                            finished_dyes(route, remaining - SLIME_MATTER, 3), results, timeout=18)


def reopened(server, results, report):
    for route in ROUTES:
        record = report["plants"][route.recipe]
        left = record["sentToSmelter"] - EGG_MATTER - SLIME_MATTER
        server.assert_state(f"{route.recipe}_reopens_with_exact_plant_stacks_three_dyes_and_both_fe_reserves",
                            f"{empty_mill(route.grind)} {finished_dyes(route, left, 3)} "
                            f'{exact_hopper(output(route.grind.x), record["remainingInHopper"])}', results)


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
    report = {"passed": False, "assertions": [], "servers": [], "plants": {}, "jarSha256": digest(directory / "mods/enderio.jar")}

    def milling_resume(server, results):
        finish_milling(server, results, report)
        start_dyes(server, results, report)

    try:
        run_stage(args, directory, report, "fresh", lambda server, results: start_milling(server, results, report))
        run_stage(args, directory, report, "milling", milling_resume)
        run_stage(args, directory, report, "smelting", lambda server, results: finish_dyes(server, results, report))
        run_stage(args, directory, report, "reopened", lambda server, results: reopened(server, results, report))
        report["passed"] = True
    finally:
        result = directory / "plant-matter-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Plant matter passed={report['passed']}, {len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
