#!/usr/bin/env python3
"""Exercise paid alloy and furnace jobs after native hotbar swaps on Forge."""

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_legacy_painting import stack
from packaged_nutritious_stick import advance
from packaged_sag_progression import mill
from packaged_server_smoke import ROOT, digest
from packaged_spawners import PLAYER, prepare_profile, run_stage


@dataclass(frozen=True)
class Job:
    name: str
    x: int
    mode: int
    inputs: tuple
    swapped: int
    product: str
    recipe: str
    cost: int
    queued: int

    @property
    def pos(self):
        return mill(self.x)


ALLOY = Job("alloy", 4962, 2, (("beetroot", 1), ("clay_ball", 3), ("egg", 6)), 2,
            "red_dye", "alloy_smelting/red_dye", 1600, 12)
FURNACE = Job("furnace", 4970, 0, (("raw_iron", 3),), 0,
              "iron_ingot", "smelting/minecraft/iron_ingot_from_smelting_raw_iron", 1500, 3)
JOBS = (ALLOY, FURNACE)
TAG = "{PortAlloy:232}"
REPLACEMENT_TAG = "{PortAlloyReplacement:232}"
CAPACITOR = stack(4, 1, "enderio:octadic_capacitor")


def ingredient_stacks(job, *, remaining=False):
    return [stack(i, count, f"minecraft:{item}", metadata=TAG)
            for i, (item, count) in enumerate(job.inputs) if not remaining or i != job.swapped]


def stored(job, stacks):
    contents = ",".join([CAPACITOR, *stacks])
    return f"if data block {job.pos} {{EnergyStored:0,Items:{{Items:[{contents}]}}}}"


def paid(job):
    return (f'{stored(job, ingredient_stacks(job) + [stack(3, 64, f"minecraft:{job.product}")])} '
            f'if data block {job.pos} {{Task:{{RecipeId:"enderio:{job.recipe}",ProgressMade:{job.cost},'
            f'ProgressRequired:{job.cost},ProcessedInputs:{job.queued if job == FURNACE else 1},'
            f'Outputs:[{{Item:{{id:"minecraft:{job.product}",Count:{job.queued}}}}}]}}}}')


def absent(job, *slots):
    return " ".join(f"unless data block {job.pos} Items.Items[{{Slot:{i}}}]" for i in slots)


def settled(job):
    if job == ALLOY:
        return f"{stored(job, ingredient_stacks(job, remaining=True))} {absent(job, 2, 3)} unless data block {job.pos} Task"
    return f'{stored(job, [stack(3, 1, "minecraft:iron_ingot")])} {absent(job, 0, 1, 2)} unless data block {job.pos} Task'


def fresh(server, results):
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    server.command("forceload add 4960 0 4975 15")
    server.assert_state("both_smelter_positions_load", " ".join(f"if loaded {job.pos}" for job in JOBS), results)
    server.command("fill 4960 79 6 4975 79 14 minecraft:stone_bricks")
    server.command("setworldspawn 4962 80 11")
    for job in JOBS:
        items = ",".join([*ingredient_stacks(job), stack(3, 64, f"minecraft:{job.product}"), CAPACITOR])
        server.command(f"setblock {job.pos} enderio:alloy_smelter[facing=south]"
                       f"{{Mode:{job.mode},EnergyStored:{job.cost},Items:{{Size:5,Items:[{items}]}}}}")
    server.assert_state("stock_alloy_and_furnace_jobs_pay_exact_cost_with_full_outputs", " ".join(map(paid, JOBS)), results, timeout=30)


def check_settled(server, job, results):
    try:
        server.assert_state(f"{job.name}_delivers_only_what_live_inputs_support", settled(job), results, timeout=3)
    except AssertionError:
        # Record both machines even if the first has a broken result.
        server.command(f"data get block {job.pos}")


def exercise_job(server, client, evidence, job, results):
    if job == FURNACE:
        server.command(f"item replace entity {PLAYER} hotbar.0 with minecraft:raw_iron{REPLACEMENT_TAG} 1")
    server.command(f"tp {PLAYER} {job.x + 0.5} 80 11.5 180 16")
    (evidence / f"server-{job.name}-open").touch()
    wait_signal(client, evidence, f"{job.name}-swapped")
    item, count = job.inputs[job.swapped]
    old_stack = stack(0, count, f"minecraft:{item}", metadata=TAG, slot_suffix="b", count_suffix="b")
    full = stack(3, 64, f"minecraft:{job.product}")
    if job == ALLOY:
        swap_state = f"{stored(job, ingredient_stacks(job, remaining=True) + [full])} {absent(job, job.swapped)}"
    else:
        swap_state = stored(job, [stack(job.swapped, 1, "minecraft:raw_iron", metadata=REPLACEMENT_TAG), full])
    server.assert_state(f"{job.name}_survival_swap_preserves_original_input_and_full_output",
                        f"{swap_state} if data entity {PLAYER} Inventory[{old_stack}]", results)
    (evidence / f"server-{job.name}-clear").touch()
    wait_signal(client, evidence, f"{job.name}-cleared")
    advance(server)
    original_outputs = f'if data entity {PLAYER} Inventory[{{id:"minecraft:{job.product}",Count:64b}}]'
    server.assert_state(f"{job.name}_survival_player_takes_exact_original_stack",
                        f"{original_outputs} if data entity {PLAYER} Inventory[{old_stack}]", results)
    check_settled(server, job, results)
    (evidence / f"server-{job.name}-settled").touch()
    wait_signal(client, evidence, f"{job.name}-done")


def reopened(args, server, results, report):
    server.assert_state("restart_preserves_both_paid_jobs_and_queued_counts", " ".join(map(paid, JOBS)), results)
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    summary_path = evidence / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "alloy-backpressure", "--server", f"127.0.0.1:{args.port}", "--output", str(evidence), "--timeout", "300"]
    with (server.directory / "alloy-backpressure-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            for job in JOBS:
                exercise_job(server, client, evidence, job, results)
            report["clientExit"] = client.wait(timeout=35)
        finally:
            stop_client(client)
    if summary_path.exists():
        report["client"] = json.loads(summary_path.read_text())
        mods = report["client"]["client"]["captures"]["furnace-settled"]["loadedMods"]
        same_jar = digest(Path(mods["enderio"]["file"])) == report["jarSha256"] and "jei" not in mods
        results.append({"assertion": "quiet_client_loads_the_same_core_forge_jar", "passed": same_jar})


def retained(server, results):
    advance(server)
    server.assert_state("final_outputs_and_remaining_ingredients_survive_restart",
                        " ".join(map(settled, JOBS)), results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for path in ("java", "forge-server", "jar", "launch"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}
    try:
        run_stage(args, directory, report, "fresh", fresh, startup_timeout=300)
        run_stage(args, directory, report, "reopened", lambda server, results: reopened(args, server, results, report),
                  startup_timeout=300)
        if report["clientExit"] or not all(result["passed"] for result in report["assertions"]):
            raise AssertionError(f"An Alloy Smelter check failed. See {directory}")
        run_stage(args, directory, report, "retained", retained, startup_timeout=300)
        report["passed"] = True
    finally:
        destination = directory / "alloy-backpressure-results.json"
        destination.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Alloy Smelter output passed={report['passed']}, {len(report['assertions'])} assertions, {destination}", flush=True)


if __name__ == "__main__":
    main()
