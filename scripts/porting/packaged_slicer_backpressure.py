#!/usr/bin/env python3
"""Check paid stock Slice'N'Splice jobs when a required input leaves the menu."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_legacy_painting import stack
from packaged_nutritious_stick import advance
from packaged_sag_progression import charge, mill, output
from packaged_server_smoke import ROOT, digest
from packaged_slicer import AXE, BLOCK, CAP, COST, MAIN, SHEARS, entry
from packaged_spawners import PLAYER, prepare_profile, run_stage


CONTROL_X, TEST_X = 4946, 4954
CONTROL, TEST = mill(CONTROL_X), mill(TEST_X)
COLLECTOR = output(CONTROL_X)
LOCK = f"{CONTROL_X} 78 8"
OUTPUT = "enderio:z_logic_controller"
HEAD = "minecraft:zombie_head"
HEAD_TAG = "{PortSlicerIngredient:231}"
RECIPE = "enderio:slicing/z_logic_controller"


def tool_entries(wear):
    return [entry(6, AXE, 7 + wear), entry(7, SHEARS, 11 + wear), entry(9, CAP)]


def ingredients(head=True):
    return [stack(i, 1, item, metadata=HEAD_TAG if item == HEAD else None)
            for i, item in enumerate(MAIN[2]) if head or item != HEAD]


def inventory(pos, stacks):
    return f"if data block {pos} {{EnergyStored:0,Items:{{Items:[{','.join(stacks)}]}}}}"


def paid(pos):
    return (f'{inventory(pos, ingredients() + tool_entries(0) + [stack(8, 64, OUTPUT)])} '
            f'if data block {pos} {{Task:{{RecipeId:"{RECIPE}",ProgressMade:{COST},ProgressRequired:{COST},'
            f'HasDeterminedOutputs:1b,Outputs:[{{Item:{{id:"{OUTPUT}",Count:1}}}}]}}}}')


def completed(pos, count):
    empty_inputs = " ".join(f"unless data block {pos} Items.Items[{{Slot:{i}}}]" for i in range(6))
    return f"{inventory(pos, tool_entries(1) + [stack(8, count, OUTPUT)])} {empty_inputs} unless data block {pos} Task"


def supplied(server, x):
    pos = mill(x)
    stacks = ",".join(tool_entries(0) + [stack(8, 64, OUTPUT)])
    server.command(f'setblock {pos} {BLOCK}[facing=south]{{EnergyStored:{COST},IOConfig:{{Modes:{{up:"pull"}}}},'
                   f'Items:{{Size:10,Items:[{stacks}]}}}}')
    counts = {item: MAIN[2].count(item) for item in dict.fromkeys(MAIN[2])}
    items = [stack(i, count, item, metadata=HEAD_TAG if item == HEAD else None, slot_suffix="b", count_suffix="b")
             for i, (item, count) in enumerate(counts.items())]
    server.command(f"setblock {x} 81 8 minecraft:hopper[facing=down]{{Items:[{','.join(items)}]}}")


def fresh(server, results):
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    server.command("forceload add 4944 0 4959 15")
    server.assert_state("both_slicers_load", f"if loaded {CONTROL} if loaded {TEST}", results)
    server.command("fill 4944 79 6 4959 79 14 minecraft:stone_bricks")
    server.command("setworldspawn 4954 80 12")
    supplied(server, CONTROL_X)
    supplied(server, TEST_X)
    server.command(f"setblock {LOCK} minecraft:redstone_block")
    collector = [stack(0, 63, OUTPUT, slot_suffix="b", count_suffix="b")]
    collector.extend(stack(i, 64, "minecraft:cobblestone", slot_suffix="b", count_suffix="b") for i in range(1, 5))
    server.command(f"setblock {COLLECTOR} minecraft:hopper[facing=down]{{Items:[{','.join(collector)}]}}")
    server.assert_state("paid_jobs_keep_all_inputs_and_unworn_tools_behind_full_outputs",
                        f"{paid(CONTROL)} {paid(TEST)}", results, timeout=90)


def collector_full():
    return f"if data block {COLLECTOR} Items[{stack(0, 64, OUTPUT, slot_suffix='b', count_suffix='b')}]"


def removed_head():
    return (f"if data entity {PLAYER} Inventory["
            f"{stack(0, 1, HEAD, metadata=HEAD_TAG, slot_suffix='b', count_suffix='b')}]")


def player_outputs():
    return f'if data entity {PLAYER} Inventory[{{id:"{OUTPUT}",Count:64b}}]'


def exercise_client(args, server, results, report):
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    summary = evidence / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "slicer-backpressure", "--server", f"127.0.0.1:{args.port}", "--output", str(evidence), "--timeout", "360"]
    with (server.directory / "slicer-backpressure-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            server.command(f"tp {PLAYER} 4954.5 80 11.5 180 16")
            (evidence / "server-slicer").touch()
            wait_signal(client, evidence, "head-taken")
            without_head = inventory(TEST, ingredients(head=False) + tool_entries(0))
            head_absent = f"unless data block {TEST} Items.Items[{{Slot:1}}]"
            server.assert_state("survival_menu_moves_the_tagged_head_to_the_hotbar",
                                f"{without_head} {head_absent} {removed_head()} "
                                f"if data block {TEST} Items.Items[{stack(8, 64, OUTPUT)}]", results)
            (evidence / "server-clear").touch()
            wait_signal(client, evidence, "output-cleared")
            advance(server)
            server.command(f"data get block {TEST}")
            blocked = f"{without_head} {head_absent} unless data block {TEST} Items.Items[{{Slot:8}}] unless data block {TEST} Task"
            server.assert_state("clearing_output_with_no_head_preserves_ingredients_tools_and_existing_output",
                                f"{blocked} {removed_head()} {player_outputs()}", results)
            (evidence / "server-unblocked").touch()
            wait_signal(client, evidence, "head-returned")
            advance(server)
            server.assert_state("returning_the_same_head_leaves_the_unpowered_slicer_idle",
                                f"{inventory(TEST, ingredients() + tool_entries(0))} unless data block {TEST} Task "
                                f"unless data block {TEST} Items.Items[{{Slot:8}}] {player_outputs()}", results)
            charge(server, TEST_X, COST)
            server.assert_state("fresh_energy_crafts_one_controller_and_wears_both_tools_once",
                                f"{completed(TEST, 1)} {player_outputs()} "
                                f'unless data entity {PLAYER} Inventory[{{id:"{HEAD}"}}]', results, timeout=90)
            (evidence / "server-finished").touch()
            if client.wait(timeout=35) != 0:
                raise AssertionError(f"Slice'N'Splice client failed; see {evidence}")
            mods = json.loads(summary.read_text())["client"]["captures"]["recovered"]["loadedMods"]
            match = digest(Path(mods["enderio"]["file"])) == report["jarSha256"] and "jei" not in mods
            results.append({"assertion": "guarded_client_loads_core_forge_and_the_same_product_jar", "passed": match})
            if not match:
                raise AssertionError("Slice'N'Splice client loaded unexpected mods")
        finally:
            stop_client(client)
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def reopened(args, server, results, report):
    server.assert_state("both_paid_jobs_save_with_unchanged_tools_and_full_stock", f"{paid(CONTROL)} {paid(TEST)}", results)
    server.command(f"setblock {LOCK} minecraft:air")
    server.assert_state("an_unchanged_job_delivers_once_when_the_hopper_frees_one_place",
                        f"{completed(CONTROL, 64)} {collector_full()}", results)
    exercise_client(args, server, results, report)


def retained(server, results):
    advance(server)
    server.assert_state("both_results_and_exact_tool_wear_survive_restart",
                        f"{completed(CONTROL, 64)} {completed(TEST, 1)} {collector_full()}", results)


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
        run_stage(args, directory, report, "retained", retained, startup_timeout=300)
        report["passed"] = True
    finally:
        destination = directory / "slicer-backpressure-results.json"
        destination.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Slice'N'Splice output passed={report['passed']}, {len(report['assertions'])} assertions, {destination}", flush=True)


if __name__ == "__main__":
    main()
