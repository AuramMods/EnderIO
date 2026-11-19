#!/usr/bin/env python3
"""Run the stock concrete, sponge, and experience recipes in both packaged tanks."""

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import sys

sys.dont_write_bytecode = True
from packaged_glass_progression import transfer
from packaged_nutritious_stick import liquid_at
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage
from packaged_starter_progression import hopper_item
from packaged_vat_automation import WATER, drained, fluid, set_mode, tank_fields


XP = "enderio:fluid_xp_juice_still"
REMAINDER = 137
COLORS = (
    "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
    "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black",
)


@dataclass(frozen=True)
class Recipe:
    ingredient: str
    filled: str
    liquid: str
    amount: int
    reversible: bool = False


@dataclass(frozen=True)
class Station:
    recipe: Recipe
    x: int
    z: int
    tank: str
    capacity: int

    def pos(self, y=80):
        return f"{self.x} {y} {self.z}"

    @property
    def name(self):
        return f"{self.tank}_{self.recipe.filled.removeprefix('minecraft:')}"

    def balance(self, amount):
        return liquid_at(self.pos(), self.recipe.liquid, amount)

    @property
    def supply(self):
        return f"{self.x} 80 {self.z - 1}"


RECIPES = tuple(Recipe(f"minecraft:{color}_concrete_powder", f"minecraft:{color}_concrete", WATER, 1000) for color in COLORS) + (
    Recipe("minecraft:sponge", "minecraft:wet_sponge", WATER, 1000, reversible=True),
    Recipe("minecraft:glass_bottle", "minecraft:experience_bottle", XP, 250, reversible=True),
)
STATIONS = tuple(Station(recipe, 3698 + 3 * index, z, tank, capacity)
                 for tank, capacity, z in (("fluid_tank", 16000, 4), ("pressurized_fluid_tank", 32000, 12))
                 for index, recipe in enumerate(RECIPES))
RETURNS = tuple(station for station in STATIONS if station.recipe.reversible)


def machine_stack(station, slot, item, count):
    return f'if data block {station.pos()} Items.Items[{{Slot:{slot},id:"{item}",Count:{count}}}]'


def machine_empty(station, *slots):
    return " ".join(f"unless data block {station.pos()} Items.Items[{{Slot:{slot}}}]" for slot in slots)


def set_liquid(server, station, amount):
    server.command(f"data merge block {station.pos()} {{{tank_fields(fluid(station.recipe.liquid, amount))}}}")


def collected(station, item, count):
    return (f"{hopper_item(station.pos(79), item, count)} unless data block {station.pos(79)} Items[1] "
            f"unless data block {station.pos(81)} Items[0]")


def waiting_to_fill(station):
    recipe = station.recipe
    return (f"{station.balance(recipe.amount - 1)} {machine_stack(station, 2, recipe.ingredient, 1)} "
            f"{machine_stack(station, 3, recipe.filled, 1)} {machine_empty(station, 0, 1)} "
            f"unless data block {station.pos(81)} Items[0]")


def start_filling(server, results):
    server.command("forceload add 3696 0 3759 15")
    server.assert_state("both_tank_rows_are_loaded", f"if loaded {STATIONS[0].pos()} if loaded {STATIONS[-1].pos()}", results)
    for station in STATIONS:
        recipe = station.recipe
        initial = tank_fields(fluid(recipe.liquid, 2 * recipe.amount - 1))
        server.command(f"setblock {station.pos()} enderio:{station.tank}[facing=south]"
                       f'{{{initial},IOConfig:{{Modes:{{up:"pull",down:"disable"}}}}}}')
        server.command(f"setblock {station.pos(81)} minecraft:hopper[facing=down]"
                       f'{{Items:[{{Slot:0b,id:"{recipe.ingredient}",Count:2b}}]}}')
    for station in STATIONS:
        server.assert_state(f"{station.name}_makes_one_stock_result_and_waits_one_mb_short", waiting_to_fill(station), results)


def waiting_to_empty(station):
    recipe = station.recipe
    amount = station.capacity - recipe.amount + 1
    return (f"{station.balance(amount)} {machine_stack(station, 0, recipe.filled, 1)} "
            f"{machine_empty(station, 1, 2, 3)} {collected(station, recipe.ingredient, 1)}")


def refill_and_return(server, results):
    for station in STATIONS:
        server.assert_state(f"{station.name}_restores_its_first_result_and_waiting_input", waiting_to_fill(station), results)
        refill = tank_fields(fluid(station.recipe.liquid, REMAINDER + 1))
        server.command(f"setblock {station.supply} enderio:{station.tank}[facing=south]{{{refill}}}")
        set_mode(server, station.pos(), "north", "pull")
    for station in STATIONS:
        recipe = station.recipe
        server.assert_state(f"{station.name}_merges_a_second_stock_result_for_the_exact_cost",
                            f"{station.balance(REMAINDER)} {machine_stack(station, 3, recipe.filled, 2)} "
                            f"{machine_empty(station, 0, 1, 2)} {drained(station.supply)}", results)
        server.command(f"setblock {station.pos(79)} minecraft:hopper[facing=down]")
        set_mode(server, station.pos(), "down", "push")
    for station in STATIONS:
        recipe = station.recipe
        server.assert_state(f"{station.name}_hoppers_extract_exactly_two_original_results",
                            f"{station.balance(REMAINDER)} {machine_empty(station, 0, 1, 2, 3)} "
                            f"{collected(station, recipe.filled, 2)}", results)
    for station in RETURNS:
        set_mode(server, station.pos(), "down", "disable")
        set_liquid(server, station, station.capacity - 2 * station.recipe.amount + 1)
        transfer(server, station.pos(79), 0, station.pos(81), 0)
        server.assert_state(f"{station.name}_returns_both_original_inputs_before_reopening_the_outlet",
                            f"unless data block {station.pos(79)} Items[0]", results)
        set_mode(server, station.pos(), "down", "push")
        server.assert_state(f"{station.name}_returns_one_original_item_and_waits_one_mb_short_of_space",
                            waiting_to_empty(station), results)


def finished_return(station):
    return (f"{station.balance(station.capacity)} {machine_empty(station, 0, 1, 2, 3)} "
            f"{collected(station, station.recipe.ingredient, 2)}")


def finish_returning(server, results):
    for station in RETURNS:
        server.assert_state(f"{station.name}_restores_its_near_capacity_return_job", waiting_to_empty(station), results)
        set_liquid(server, station, station.capacity - station.recipe.amount)
        server.assert_state(f"{station.name}_uses_the_last_exact_room_and_returns_both_original_items",
                            finished_return(station), results)


def reopen(server, results):
    for station in STATIONS:
        if station.recipe.reversible:
            condition = finished_return(station)
        else:
            condition = (f"{station.balance(REMAINDER)} {machine_empty(station, 0, 1, 2, 3)} "
                         f"{collected(station, station.recipe.filled, 2)}")
        server.assert_state(f"{station.name}_reopens_with_exact_items_and_fluid", condition, results)


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
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}
    try:
        run_stage(args, directory, report, "fresh", start_filling)
        run_stage(args, directory, report, "refill", refill_and_return)
        run_stage(args, directory, report, "return", finish_returning)
        run_stage(args, directory, report, "reopened", reopen)
        report["passed"] = True
    finally:
        result = directory / "stock-tank-recipes-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Stock tank recipes passed={report['passed']}, {len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
