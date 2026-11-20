#!/usr/bin/env python3
"""Check paid SAG Mill jobs when a survival player swaps the input."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_grinding_balls import BALLS
from packaged_legacy_painting import stack
from packaged_nutritious_stick import advance
from packaged_sag_progression import COAL, DUST, charge, data_read, mill
from packaged_server_smoke import ROOT, digest
from packaged_spawners import PLAYER, prepare_profile, run_stage


STONE, CHARCOAL = 4980, 4988
TRIALS = ((STONE, "stone"), (CHARCOAL, "charcoal"))
BALL = next(ball for ball in BALLS if ball.item == "enderio:redstone_alloy_grinding_ball")
CAPACITOR = stack(6, 1, "enderio:octadic_capacitor")
ORIGINAL_TAG = "{PortSagInput:233}"
REPLACEMENT_TAG = "{PortSagReplacement:233}"
STOCK_TAG = "{PortSagStock:233}"
FULL_FIRST = stack(1, 64, "minecraft:iron_ingot", metadata=STOCK_TAG)
FULL_OTHERS = [stack(i, 64, "minecraft:cobblestone") for i in range(2, 5)]


def stored(x, stacks, *, wear=BALL.cost):
    items = ",".join([*stacks, stack(5, 1, BALL.item), CAPACITOR])
    return (f"if data block {mill(x)} {{EnergyStored:0,GrindingBal:{BALL.nbt},GrindingBallDamage:{wear},"
            f"Items:{{Items:[{items}]}}}}")


def paid(x):
    item = stack(0, 1, "minecraft:coal", metadata=ORIGINAL_TAG)
    return (f"{stored(x, [item, FULL_FIRST, *FULL_OTHERS])} "
            f'if data block {mill(x)} {{Task:{{RecipeId:"{COAL}",ProgressMade:{BALL.cost},ProgressRequired:{BALL.cost},'
            f'HasDeterminedOutputs:1b}}}} unless data block {mill(x)} Task.Outputs[1]')


def exact_roll(server, x, expected):
    if data_read(server, mill(x), "Task.Outputs") != expected["nbt"]:
        raise AssertionError(f"SAG Mill {x} changed its queued result")


def fresh(server, results, report):
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    server.command("forceload add 4976 0 4991 15")
    server.assert_state("both_sag_mills_load", f"if loaded {mill(STONE)} if loaded {mill(CHARCOAL)}", results)
    server.command("fill 4976 79 6 4991 79 14 minecraft:stone_bricks")
    server.command("setworldspawn 4980 80 11")
    for x, _ in TRIALS:
        original = ",".join([stack(0, 1, "minecraft:coal"), stack(5, 2, BALL.item), CAPACITOR])
        server.command(f"setblock {mill(x)} enderio:sag_mill[facing=south]"
                       f"{{EnergyStored:2400,Items:{{Size:7,Items:[{original}]}}}}")
    for x, name in TRIALS:
        server.assert_state(f"{name}_first_stock_job_claims_one_ball",
                            f'{stored(x, [], wear=0)} unless data block {mill(x)} Task '
                            f'if data block {mill(x)} Items.Items[{{Slot:1,id:"{DUST}"}}]', results, timeout=30)
        charged = ",".join([stack(0, 1, "minecraft:coal", metadata=ORIGINAL_TAG), FULL_FIRST, *FULL_OTHERS,
                            stack(5, 1, BALL.item), CAPACITOR])
        server.command(f"data merge block {mill(x)} {{EnergyStored:{BALL.cost},Items:{{Size:7,Items:[{charged}]}}}}")
    server.assert_state("both_blocked_coal_jobs_pay_exact_fe_and_wear_once", f"{paid(STONE)} {paid(CHARCOAL)}", results, timeout=30)
    for x, name in TRIALS:
        count = int(data_read(server, mill(x), f'Task.Outputs[{{Item:{{id:"{DUST}"}}}}].Item.Count'))
        if not 1 <= count <= 2:
            raise AssertionError(f"Unexpected stock coal roll {count} at {x}")
        report["rolls"][name] = {"powder": count, "nbt": data_read(server, mill(x), "Task.Outputs")}


def active_result(x, *, output, input_stack=None):
    stacks = FULL_OTHERS.copy()
    if output:
        stacks.append(output)
    if input_stack:
        stacks.append(input_stack)
    missing = " ".join(f"unless data block {mill(x)} Items.Items[{{Slot:{slot}}}]"
                       for slot, entry in enumerate((input_stack, output)) if entry is None)
    return f"{stored(x, stacks)} unless data block {mill(x)} Task {missing}"


def exercise(server, client, evidence, results, name, x, hotbar, expected):
    server.command(f"item replace entity {PLAYER} hotbar.{hotbar} with minecraft:{name}{REPLACEMENT_TAG}")
    server.command(f"tp {PLAYER} {x + 0.5} 80 11.5 180 16")
    (evidence / f"server-{name}-open").touch()
    wait_signal(client, evidence, f"{name}-swapped")
    original = stack(hotbar, 1, "minecraft:coal", metadata=ORIGINAL_TAG, slot_suffix="b", count_suffix="b")
    replacement = stack(0, 1, f"minecraft:{name}", metadata=REPLACEMENT_TAG)
    player = f"if data entity {PLAYER} Inventory[{original}]"
    server.assert_state(f"{name}_native_swap_keeps_original_coal_and_stock",
                        f"{stored(x, [replacement, FULL_FIRST, *FULL_OTHERS])} {player}", results)
    if x == CHARCOAL:
        server.assert_state("compatible_swap_keeps_the_job_paid",
                            f'if data block {mill(x)} {{Task:{{RecipeId:"{COAL}",ProgressMade:{BALL.cost},ProgressRequired:{BALL.cost}}}}}', results)
        exact_roll(server, x, expected)
    (evidence / f"server-{name}-clear").touch()
    wait_signal(client, evidence, f"{name}-cleared")
    advance(server)
    server.command(f"execute store result storage port_sag:stock count int 1 run clear {PLAYER} minecraft:iron_ingot{STOCK_TAG} 0")
    server.assert_state(f"{name}_survival_player_keeps_the_original_coal_and_stock",
                        f"{player} if data storage port_sag:stock {{count:{64 * (hotbar + 1)}}}", results)
    count = expected["powder"] if x == CHARCOAL else 0
    outcome = active_result(x, output=stack(1, count, DUST) if count else None, input_stack=None if count else replacement)
    try:
        server.assert_state(f"{name}_uses_only_live_input_and_preserves_ball_wear", outcome, results, timeout=4)
    except AssertionError:
        server.command(f"data get block {mill(x)}")
    (evidence / f"{name}-powder").write_text(str(count))
    (evidence / f"server-{name}-settled").touch()
    wait_signal(client, evidence, f"{name}-done")


def reopened(args, server, results, report):
    server.assert_state("restart_restores_both_fully_paid_jobs", f"{paid(STONE)} {paid(CHARCOAL)}", results)
    for x, name in TRIALS:
        exact_roll(server, x, report["rolls"][name])
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    summary = evidence / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "sag-backpressure", "--server", f"127.0.0.1:{args.port}", "--output", str(evidence), "--timeout", "300"]
    with (server.directory / "sag-backpressure-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            for hotbar, (x, name) in enumerate(TRIALS):
                exercise(server, client, evidence, results, name, x, hotbar, report["rolls"][name])
            report["clientExit"] = client.wait(timeout=35)
        finally:
            stop_client(client)
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None
    if report["client"]:
        mods = report["client"]["client"]["captures"]["charcoal-settled"]["loadedMods"]
        matching = digest(Path(mods["enderio"]["file"])) == report["jarSha256"] and "jei" not in mods
        results.append({"assertion": "client_uses_the_same_packaged_core_jar", "passed": matching})
    if report["clientExit"] or not all(result["passed"] for result in results):
        raise AssertionError(f"SAG Mill input swap failed; see {server.directory}")
    charge(server, STONE, BALL.cost)
    server.assert_state("fresh_exact_budget_mills_only_the_stone_without_extra_wear",
                        active_result(STONE, output=stack(1, 1, "minecraft:cobblestone")), results, timeout=30)


def retained(server, results, report):
    advance(server)
    stone = active_result(STONE, output=stack(1, 1, "minecraft:cobblestone"))
    charcoal = active_result(CHARCOAL, output=stack(1, report["rolls"]["charcoal"]["powder"], DUST))
    server.assert_state("both_results_and_exact_ball_wear_survive_restart", f"{stone} {charcoal}", results)


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
    report = {"passed": False, "assertions": [], "servers": [], "rolls": {}, "jarSha256": digest(directory / "mods/enderio.jar")}
    try:
        run_stage(args, directory, report, "fresh", lambda server, results: fresh(server, results, report), startup_timeout=300)
        run_stage(args, directory, report, "reopened", lambda server, results: reopened(args, server, results, report), startup_timeout=300)
        run_stage(args, directory, report, "retained", lambda server, results: retained(server, results, report), startup_timeout=300)
        report["passed"] = True
    finally:
        path = directory / "sag-backpressure-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"SAG Mill output passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
