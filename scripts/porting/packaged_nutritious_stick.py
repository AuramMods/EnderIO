#!/usr/bin/env python3
"""Feed actual VAT Nutrient Distillation into the stock tank filling recipe."""

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import sys
import time

sys.dont_write_bytecode = True
from packaged_levitation_staff import game_tick
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage
from packaged_vat_automation import Batch, NUTRIENT, WATER, configure, drained, fluid, set_mode, tank_fields


BATCH = Batch(3686, "nutrient_distillation", WATER, 3000, ("minecraft:beef", "minecraft:wheat"), 250)
STICK = "minecraft:stick"
NUTRITIOUS = "enderio:nutritious_stick"
FEED = f"{BATCH.x} 81 9"
COLLECT = f"{BATCH.x} 79 9"
SENTINEL = '{PortBatch:"original",OriginalCount:1b}'
AUTHORED_RESULT = "minecraft:feather"
AUTHORED_TAG = {"PortBatch": "authored"}
AUTHORED_AMOUNT = 200


@dataclass(frozen=True)
class RecipeCase:
    mode: str
    pos: str
    tank: str
    ingredient: str
    input_slot: int
    output_slot: int
    final_fluid: int


RECIPES = (
    RecipeCase("fill", "3691 80 8", "enderio:fluid_tank", "minecraft:paper", 2, 3, 800),
    RecipeCase("empty", "3694 80 8", "enderio:pressurized_fluid_tank", "minecraft:rabbit_hide", 0, 1, 1200),
)


def write_tank_recipes(directory):
    pack = directory / WORLD / "datapacks/port-tank-stack"
    folder = pack / "data/port_qa/recipes"
    folder.mkdir(parents=True)
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 15, "description": "Tank result stack fixtures"}}) + "\n")
    for case in RECIPES:
        recipe = {"type": "enderio:tank", "mode": case.mode, "input": {"item": case.ingredient},
                  "output": {"id": AUTHORED_RESULT, "count": 2, "tag": AUTHORED_TAG},
                  "fluid": {"fluid": WATER, "amount": AUTHORED_AMOUNT}}
        (folder / f"{case.mode}.json").write_text(json.dumps(recipe) + "\n")


def authored_output(case, count):
    tag = json.dumps(AUTHORED_TAG)
    return f'{{Slot:{case.output_slot},id:"{AUTHORED_RESULT}",Count:{count},tag:{tag}}}'


def authored_complete(case):
    return (f"{liquid_at(case.pos, WATER, case.final_fluid)} if data block {case.pos} Items.Items[{authored_output(case, 64)}] "
            f"unless data block {case.pos} Items.Items[{{Slot:{case.input_slot}}}]")


def verify_authored_recipes(server, results):
    for case in RECIPES:
        stack = f'{{Slot:{case.input_slot},id:"{case.ingredient}",Count:1}}'
        server.command(f"setblock {case.pos} {case.tank}[facing=south]")
        server.command(f"data merge block {case.pos} {{{tank_fields(fluid(WATER, 1000))},"
                       f"Items:{{Items:[{stack},{authored_output(case, 63)}]}}}}")
        advance(server)
        server.assert_state(f"authored_{case.mode}_cannot_spend_inputs_if_two_results_do_not_fit",
                            f"{liquid_at(case.pos, WATER, 1000)} if data block {case.pos} Items.Items[{stack}] "
                            f"if data block {case.pos} Items.Items[{authored_output(case, 63)}]", results)
        server.command(f"data modify block {case.pos} Items.Items[{{Slot:{case.output_slot}}}].Count set value 62")
        server.assert_state(f"authored_{case.mode}_adds_both_results_with_the_original_tag_at_exact_fluid_cost",
                            authored_complete(case), results)


def supply_reagents(server, count):
    for offset, reagent in zip((-1, 1), BATCH.reagents):
        server.command(f"item replace block {BATCH.x + offset} 80 8 container.0 with {reagent} {count}")


def liquid_at(pos, liquid, amount):
    return f"if data block {pos} {{{tank_fields(fluid(liquid, amount))}}}"


def tank_input(count):
    return f'if data block {BATCH.receiver} Items.Items[{{Slot:2,id:"{STICK}",Count:{count}}}]'


def stock_finished(amount):
    positions = (f"{BATCH.x - 1} 80 8", f"{BATCH.x + 1} 80 8")
    empty_hoppers = " ".join(f"unless data block {pos} Items[0]" for pos in positions)
    return (f"{liquid_at(BATCH.receiver, NUTRIENT, amount)} {drained(BATCH.supply)} {drained(BATCH.vat)} "
            f"{drained(BATCH.vat, 1)} {empty_hoppers} unless data block {BATCH.vat} Items.Items[0] "
            f"unless data block {BATCH.vat} Task unless data block {FEED} Items[0]")


def advance(server, ticks=15):
    target = game_tick(server) + ticks
    deadline = time.monotonic() + 10
    while game_tick(server) < target:
        if time.monotonic() >= deadline:
            raise AssertionError("The dedicated tank test did not advance its native game ticks")


def tagged_result(pos, path, *, vanilla=False):
    suffix = "b" if vanilla else ""
    return f'if data block {pos} {path}[{{id:"{NUTRITIOUS}",Count:1{suffix},tag:{SENTINEL}}}]'


def first_batches(server, results):
    server.command("forceload add 3680 0 3695 15")
    server.assert_state("the_vat_and_tank_chunk_loads", f"if loaded {BATCH.vat} if loaded {COLLECT}", results)
    configure(server, BATCH, reagent_count=3)
    set_mode(server, BATCH.receiver, "up", "pull")
    set_mode(server, BATCH.receiver, "down", "disable")
    server.command(f"setblock {FEED} minecraft:hopper[facing=down]")
    server.command(f"item replace block {FEED} container.0 with {STICK} 2")
    incomplete = f"{stock_finished(750)} {tank_input(2)} unless data block {BATCH.receiver} Items.Items[{{Slot:3}}]"
    server.assert_state("three_stock_batches_produce_750_mb_and_two_sticks_wait", incomplete, results, timeout=48)
    advance(server)
    server.assert_state("the_750_mb_tank_still_holds_both_unprocessed_sticks", incomplete, results)


def fill_behind_tagged_output(server, results):
    incomplete = f"{stock_finished(750)} {tank_input(2)} unless data block {BATCH.receiver} Items.Items[{{Slot:3}}]"
    server.assert_state("the_below_threshold_liquid_and_original_sticks_survive_restart", incomplete, results)
    server.command(f'data modify block {BATCH.receiver} Items.Items append value '
                   f'{{Slot:3,id:"{NUTRITIOUS}",Count:1,tag:{SENTINEL}}}')
    supply_reagents(server, 2)
    server.command(f"data merge block {BATCH.supply} {{{tank_fields(fluid(WATER, 2000))}}}")
    expected = f"{stock_finished(1250)} {tank_input(2)} {tagged_result(BATCH.receiver, 'Items.Items')}"
    try:
        server.assert_state("a_different_output_tag_blocks_crafting_without_spending_liquid_or_sticks",
                            expected, results, timeout=36)
        advance(server)
        server.assert_state("the_tagged_output_still_blocks_the_full_liquid_balance", expected, results)
    finally:
        server.command(f"data get block {BATCH.receiver}")


def output_state():
    return (f'{stock_finished(250)} {tank_input(1)} unless data block {BATCH.receiver} Items.Items[{{Slot:3}}] '
            f'{tagged_result(COLLECT, "Items", vanilla=True)} '
            f'if data block {COLLECT} Items[{{Slot:1b,id:"{NUTRITIOUS}",Count:1b}}] '
            f'unless data block {COLLECT} Items[{{Slot:1b}}].tag unless data block {COLLECT} Items[2]')


def release_output(server, results):
    blocked = f"{stock_finished(1250)} {tank_input(2)} {tagged_result(BATCH.receiver, 'Items.Items')}"
    server.assert_state("the_original_tagged_stack_and_full_liquid_balance_survive_restart", blocked, results)
    server.command(f"setblock {COLLECT} minecraft:hopper[facing=down]")
    set_mode(server, BATCH.receiver, "down", "push")
    server.assert_state("native_extraction_releases_one_new_untagged_stick_and_spends_exactly_1000_mb",
                        output_state(), results, timeout=15)
    verify_authored_recipes(server, results)


def reopened(server, results):
    server.assert_state("both_distinct_outputs_the_waiting_stick_and_250_mb_survive_restart", output_state(), results)
    for case in RECIPES:
        server.assert_state(f"the_exact_authored_{case.mode}_stack_and_tank_balance_survive_restart", authored_complete(case), results)


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
    write_tank_recipes(directory)
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}
    try:
        run_stage(args, directory, report, "fresh", first_batches)
        run_stage(args, directory, report, "blocked", fill_behind_tagged_output)
        run_stage(args, directory, report, "extracted", release_output)
        run_stage(args, directory, report, "reopened", reopened)
        report["passed"] = True
    finally:
        result = directory / "nutritious-stick-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Nutritious Stick passed={report['passed']}, {len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
