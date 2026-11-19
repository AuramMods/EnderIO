#!/usr/bin/env python3
"""Recover and fully export a real Blue Ice VAT batch across packaged Forge restarts."""

import argparse
import json
from pathlib import Path
import shutil
import sys

sys.dont_write_bytecode = True
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage
from packaged_vat_automation import Batch, WATER, configure, drained, enderio_fluid, fluid, tank_fields


CLOUD = enderio_fluid("cloud_seed")
BLUE_ICE = Batch(4418, "cloud_seed", WATER, 1000, ("minecraft:prismarine_shard", "minecraft:blue_ice"), 81000)
RECIPE = "enderio:fermenting/fluid_cloud_seed_still"
RACK = tuple(f"{BLUE_ICE.x + offset} 80 11" for offset in (0, 2, 4))


def has_batch(pos, amount, index=0):
    return f'if data block {pos} {{{tank_fields(fluid(CLOUD, amount), index)}}}'


def has_water(pos):
    return f'if data block {pos} {{{tank_fields(fluid(WATER, 1000))}}}'


def pending(amount):
    return f'if data block {BLUE_ICE.vat} {{PendingOutput:{fluid(CLOUD, amount)}}}'


def ingredients(right):
    return (f'if data block {BLUE_ICE.vat} {{Items:{{Items:[{{id:"minecraft:prismarine_shard",Count:1}},'
            f'{{id:"minecraft:{right}",Count:1}}]}}}}')


def empty_job():
    return (f'{drained(BLUE_ICE.vat)} unless data block {BLUE_ICE.vat} Items.Items[0] '
            f'unless data block {BLUE_ICE.vat} Task')


def baseline_stall(server, results):
    server.command("forceload add 4416 0 4431 15")
    server.assert_state("blue_ice_station_loaded", f"if loaded {BLUE_ICE.vat}", results)
    configure(server, BLUE_ICE)
    task = (f'if data block {BLUE_ICE.vat} {{Task:{{RecipeId:"{RECIPE}",ProgressMade:400,ProgressRequired:400,'
            f'Outputs:[{{Fluid:{fluid(CLOUD, BLUE_ICE.output_amount)}}}]}}}}')
    server.assert_state("old_production_stalls_with_all_81000_mb_and_unspent_inputs",
                        f"{task} {ingredients('blue_ice')} {has_water(BLUE_ICE.vat)} "
                        f"{drained(BLUE_ICE.supply)} {drained(BLUE_ICE.vat, 1)} {drained(BLUE_ICE.receiver)}", results, timeout=35)


def first_delivery(server, results):
    server.assert_state("saved_old_job_commits_once_and_exports_first_16000_mb",
                        f"{has_batch(BLUE_ICE.receiver, 16000)} {has_batch(BLUE_ICE.vat, 8000, 1)} "
                        f"{pending(57000)} {empty_job()}", results, timeout=20)
    supply_state = tank_fields(fluid(WATER, 1000))
    server.command(f"data merge block {BLUE_ICE.supply} {{{supply_state}}}")
    server.command(f'data merge block {BLUE_ICE.x - 1} 80 8 '
                   '{Items:[{Slot:0b,id:"minecraft:prismarine_shard",Count:1b}]}')
    server.command(f'data merge block {BLUE_ICE.x + 1} 80 8 '
                   '{Items:[{Slot:0b,id:"minecraft:snowball",Count:1b}]}')
    server.assert_state("waiting_stock_recipe_keeps_inputs_unspent_while_paid_batch_is_blocked",
                        f"{has_water(BLUE_ICE.vat)} {ingredients('snowball')} {pending(57000)} "
                        f"unless data block {BLUE_ICE.vat} Task", results)


def move_receiver(server, rack, next_tank):
    server.command(f"clone {BLUE_ICE.receiver} {BLUE_ICE.receiver} {rack} replace move")
    server.command(f"setblock {BLUE_ICE.receiver} enderio:{next_tank}[facing=south]")


def remaining_delivery(server, results):
    server.assert_state("paid_remainder_and_following_ingredients_survive_restart",
                        f"{has_batch(BLUE_ICE.receiver, 16000)} {has_batch(BLUE_ICE.vat, 8000, 1)} {pending(57000)} "
                        f"{has_water(BLUE_ICE.vat)} {ingredients('snowball')}", results)
    move_receiver(server, RACK[0], "pressurized_fluid_tank")
    server.assert_state("first_pressurized_receiver_accepts_exactly_32000_mb",
                        f"{has_batch(RACK[0], 16000)} {has_batch(BLUE_ICE.receiver, 32000)} "
                        f"{has_batch(BLUE_ICE.vat, 8000, 1)} {pending(25000)} {ingredients('snowball')}", results)
    move_receiver(server, RACK[1], "pressurized_fluid_tank")
    server.assert_state("next_recipe_waited_for_the_paid_batch_then_produces_110_mb",
                        f"{has_batch(RACK[1], 32000)} {has_batch(BLUE_ICE.receiver, 32000)} "
                        f"{has_batch(BLUE_ICE.vat, 1110, 1)} {empty_job()} unless data block {BLUE_ICE.vat} PendingOutput", results, timeout=35)
    move_receiver(server, RACK[2], "fluid_tank")
    verify_batches(server, results, "entire_81110_mb_delivered_to_four_separate_receivers")


def verify_batches(server, results, name):
    all_receivers = " ".join(has_batch(pos, amount) for pos, amount in zip((*RACK, BLUE_ICE.receiver), (16000, 32000, 32000, 1110)))
    server.assert_state(name, f"{all_receivers} {empty_job()} {drained(BLUE_ICE.vat, 1)} "
                        f"{drained(BLUE_ICE.supply)} unless data block {BLUE_ICE.vat} PendingOutput", results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--prior-jar", required=True, type=Path)
    parser.add_argument("--jar", type=Path, help="Copy the stalled world and exercise it with the fixed package")
    args = parser.parse_args()

    root = ROOT / "build/port-runtime" / args.name
    root.mkdir()
    original, fixed = root / "original", root / "fixed"
    report = {"passed": False, "forge": FORGE, "priorJarSha256": digest(args.prior_jar), "assertions": [], "servers": []}
    try:
        prepare_profile(args, original, args.prior_jar)
        run_stage(args, original, report, "stalled", baseline_stall)
        if args.jar:
            report["jarSha256"] = digest(args.jar)
            prepare_profile(args, fixed, args.jar)
            shutil.copytree(original / WORLD, fixed / WORLD)
            run_stage(args, fixed, report, "imported", first_delivery)
            run_stage(args, fixed, report, "resumed", remaining_delivery)
            run_stage(args, fixed, report, "verified",
                      lambda server, results: verify_batches(server, results, "second_restart_keeps_all_receiving_tanks_and_no_replay"))
        report["passed"] = True
    finally:
        (root / "vat-large-batch-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
