#!/usr/bin/env python3
"""Follow stock metal powders through a saved SAG Mill job and native furnaces."""

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import sys

sys.dont_write_bytecode = True
from packaged_glass_progression import transfer
from packaged_sag_progression import charge, data_read, feed, inputs, mill, output, prepare_mill
from packaged_server_smoke import ROOT, digest
from packaged_solar_charging import check
from packaged_spawners import prepare_profile, run_stage
from packaged_starter_progression import hopper_item


COST, PAID, RESERVE = 2400, 600, 137


@dataclass(frozen=True)
class Route:
    x: int
    metal: str
    raw: bool
    byproduct: str | None = None

    @property
    def name(self):
        return f"raw_{self.metal}" if self.raw else self.metal

    @property
    def ingot(self):
        return f"minecraft:{self.metal}_ingot"

    @property
    def ingredient(self):
        return f"minecraft:{self.name}" if self.raw else self.ingot

    @property
    def powder(self):
        return f"enderio:powdered_{self.metal}"

    @property
    def furnace(self):
        return "minecraft:furnace" if self.raw else "minecraft:blast_furnace"

    @property
    def cooking_recipe(self):
        kind = "smelting" if self.raw else "blasting"
        return f"enderio:item.minecraft.{self.metal}_ingot_from_{kind}"

    def below_mill(self, y):
        return f"{self.x} {y} 8"

    @property
    def archive(self):
        return f"{self.x} 79 9"


ROUTES = (
    Route(3586, "iron", True, "enderio:powdered_tin"),
    Route(3590, "gold", True, "enderio:powdered_copper"),
    Route(3594, "copper", True, "enderio:powdered_gold"),
    Route(3598, "iron", False),
    Route(3602, "gold", False),
    Route(3606, "copper", False),
)


def paused(route):
    pos = mill(route.x)
    return (f'if data block {pos} {{EnergyStored:0,Items:{{Items:[{{Slot:0,id:"{route.ingredient}",Count:1}}]}},'
            f'Task:{{RecipeId:"enderio:sag_milling/{route.name}",ProgressMade:{PAID},ProgressRequired:{COST},'
            f'HasDeterminedOutputs:1b}}}} unless data block {pos} GrindingBal '
            f"unless data block {feed(route.x)} Items[0] unless data block {output(route.x)} Items[0]")


def finished_mill(route):
    pos = mill(route.x)
    empty = " ".join(f"unless data block {pos} Items.Items[{{Slot:{slot}}}]" for slot in range(6))
    return f"if data block {pos} {{EnergyStored:{RESERVE}}} unless data block {pos} Task {empty}"


def saved_roll(server, results, route):
    pos = mill(route.x)
    rolled = data_read(server, pos, "Task.Outputs")
    powders = {}
    for powder in (route.powder, route.byproduct):
        if powder is not None and f'id: "{powder}"' in rolled:
            powders[powder] = int(data_read(server, pos, f'Task.Outputs[{{Item:{{id:"{powder}"}}}}].Item.Count'))
    maximum = 2 if route.raw else 1
    check(f"{route.name}_rolls_its_stock_primary_yield_and_at_most_one_byproduct", True,
          1 <= powders.get(route.powder, 0) <= maximum and 0 <= powders.get(route.byproduct, 0) <= 1, results)
    return {"queued": rolled, "powders": powders}


def start(server, results, report):
    server.command("forceload add 3584 0 3615 15")
    server.assert_state("all_six_stock_grinding_and_cooking_routes_are_loaded",
                        " ".join(f"if loaded {mill(route.x)}" for route in ROUTES), results)
    for route in ROUTES:
        prepare_mill(server, route.x)
        inputs(server, route.x, route.ingredient, 1)
    for route in ROUTES:
        server.assert_state(f"{route.name}_enters_through_its_native_input_hopper",
                            f'if data block {mill(route.x)} Items.Items[{{Slot:0,id:"{route.ingredient}",Count:1}}] '
                            f"unless data block {feed(route.x)} Items[0]", results)
        charge(server, route.x, PAID)
    for route in ROUTES:
        server.assert_state(f"{route.name}_saves_after_exactly_{PAID}_fe", paused(route), results)
        report["routes"][route.name] = saved_roll(server, results, route)


def finish_grinds(server, results, report):
    for route in ROUTES:
        server.assert_state(f"{route.name}_restores_its_paused_job_and_original_input", paused(route), results)
        queued = data_read(server, mill(route.x), "Task.Outputs")
        check(f"{route.name}_restores_its_exact_queued_outputs", report["routes"][route.name]["queued"], queued, results)
        charge(server, route.x, COST - PAID + RESERVE)
    for route in ROUTES:
        hopper = output(route.x)
        powders = report["routes"][route.name]["powders"]
        exact = " ".join(f'if data block {hopper} Items[{{id:"{powder}",Count:{count}b}}]'
                         for powder, count in powders.items())
        server.assert_state(f"{route.name}_exports_the_saved_roll_after_spending_only_its_remaining_1800_fe",
                            f"{finished_mill(route)} {exact} unless data block {hopper} Items[{len(powders)}]", results)
        server.command(f"setblock {route.archive} minecraft:barrel")
        if route.byproduct in powders:
            slot = int(data_read(server, hopper, f'Items[{{id:"{route.byproduct}"}}].Slot').removesuffix("b"))
            transfer(server, hopper, slot, route.archive, 0)
        server.command(f"setblock {route.below_mill(78)} {route.furnace}[facing=south]")
        server.command(f"setblock {route.below_mill(77)} minecraft:hopper[facing=down]")


def fuel_cookers(server, results, report):
    for route in ROUTES:
        cooker = route.below_mill(78)
        units = report["routes"][route.name]["powders"][route.powder]
        ticks = 200 if route.raw else 100
        server.assert_state(f"{route.name}_hoppers_feed_only_the_original_powder_into_the_{ticks}_tick_cooker",
                            f'if data block {cooker} {{CookTimeTotal:{ticks},Items:[{{Slot:0b,id:"{route.powder}",Count:{units}b}}]}} '
                            f"unless data block {output(route.x)} Items[0] unless data block {cooker} Items[1]", results)
        server.command(f"item replace block {cooker} container.1 with minecraft:coal")


def collected(server, results, report, *, restarted):
    phase = "reopened" if restarted else "cooked"
    for route in ROUTES:
        cooker = route.below_mill(78)
        powders = report["routes"][route.name]["powders"]
        units = powders[route.powder]
        archive = hopper_item(route.archive, route.byproduct, powders[route.byproduct]) if route.byproduct in powders else (
            f"unless data block {route.archive} Items[0]")
        server.assert_state(f"{route.name}_{phase}_exact_ingots_stock_recipe_and_balances",
                            f'if block {cooker} {route.furnace} '
                            f'if data block {cooker} {{RecipesUsed:{{"{route.cooking_recipe}":{units}}}}} '
                            f"unless data block {cooker} Items[0] unless data block {output(route.x)} Items[0] "
                            f"{hopper_item(route.below_mill(77), route.ingot, units)} "
                            f"unless data block {route.below_mill(77)} Items[1] {finished_mill(route)} {archive}",
                            results, timeout=35)


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
    report = {"passed": False, "assertions": [], "servers": [], "routes": {},
              "jarSha256": digest(directory / "mods/enderio.jar")}

    def cook(server, results):
        finish_grinds(server, results, report)
        fuel_cookers(server, results, report)
        collected(server, results, report, restarted=False)

    try:
        run_stage(args, directory, report, "fresh", lambda server, results: start(server, results, report))
        run_stage(args, directory, report, "cook", cook)
        run_stage(args, directory, report, "reopened", lambda server, results: collected(server, results, report, restarted=True))
        report["passed"] = True
    finally:
        result = directory / "metal-powders-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Metal powders passed={report['passed']}, {len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
