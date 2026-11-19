#!/usr/bin/env python3
"""Automate stock alloy recipes and craft the original outputs in survival."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_glass_progression import Smelt, finished_smelter, prepare_smelter, start_smelter, supply, transfer
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage
from packaged_starter_progression import PLAYER
from saved_world import read_data_file

CHEST = "3211 80 10"
ALLOYS = ("redstone_alloy", "pulsating_alloy", "dark_steel", "soularium", "end_steel",
          "conductive_alloy", "energetic_alloy", "vibrant_alloy")
REMAINDERS = (0, 1, 1, 1, 11, 1, 1, 1)

REDSTONE = Smelt(3202, 3, "redstone_alloy_ingot", 9, 3200)
PULSATING = Smelt(3206, 3, "pulsating_alloy_ingot", 10, 2400)
DARK_TO_END = Smelt(3210, 3, "dark_steel_ingot", 10, 1600)
DARK_TO_PLAYER = Smelt(3214, 3, "dark_steel_ingot", 10, 1600)
SAND = Smelt(3218, 3, "soularium_ingot", 5, 5600)
SOIL = Smelt(3222, 3, "soularium_ingot", 5, 5600)
END = Smelt(3226, 3, "end_steel_ingot", 20, 3200)
CONDUCTIVE = Smelt(3202, 6, "conductive_alloy_ingot", 10, 1600)
ENERGETIC = Smelt(3206, 6, "energetic_alloy_ingot", 10, 2400)
VIBRANT = Smelt(3210, 6, "vibrant_alloy_ingot", 10, 2800)
PLAYER_OUTPUTS = (REDSTONE, PULSATING, DARK_TO_PLAYER, SAND, SOIL, END, CONDUCTIVE, ENERGETIC, VIBRANT)


def load(server, results):
    server.command("forceload add 3200 0 3231 15")
    server.assert_state("smelters_and_workshop_are_loaded", f"if loaded {REDSTONE.pos(80)} if loaded {END.pos(80)} if loaded {CHEST}", results)


def produce(server, results):
    load(server, results)
    for command in ("gamerule doMobSpawning false", "gamerule spawnRadius 0", "gamerule doLimitedCrafting true",
                    "gamerule doDaylightCycle false", "time set noon",
                    "fill 3200 78 2 3231 78 6 minecraft:stone", "fill 3206 79 7 3214 79 15 minecraft:stone",
                    "setworldspawn 3210 80 14", f"setblock {CHEST} minecraft:chest[facing=south]",
                    "setblock 3210 80 10 minecraft:crafting_table"):
        server.command(command)
    for job in (*PLAYER_OUTPUTS, DARK_TO_END):
        prepare_smelter(server, job)

    # Smelt's count and cost are per output item. Double-output alloys use half their batch cost here.
    dark_inputs = (("minecraft:iron_ingot", 5), ("enderio:powdered_coal", 5), ("minecraft:obsidian", 5))
    first_jobs = (
        ("redstone", REDSTONE, (("minecraft:redstone", 9), ("minecraft:copper_ingot", 9))),
        ("pulsating", PULSATING, (("minecraft:iron_ingot", 5), ("minecraft:ender_pearl", 5))),
        ("dark_for_end", DARK_TO_END, dark_inputs),
        ("dark_for_player", DARK_TO_PLAYER, dark_inputs),
        ("soul_sand", SAND, (("minecraft:soul_sand", 5), ("minecraft:gold_ingot", 5))),
        ("soul_soil", SOIL, (("minecraft:soul_soil", 5), ("minecraft:gold_ingot", 5))),
        ("conductive", CONDUCTIVE, (("minecraft:iron_ingot", 5), ("minecraft:copper_ingot", 5))),
        ("energetic", ENERGETIC, (("minecraft:redstone", 5), ("enderio:conductive_alloy_ingot", 5), ("minecraft:gold_ingot", 5))),
        ("vibrant", VIBRANT, (("enderio:energetic_alloy_ingot", 5), ("minecraft:ender_pearl", 5), ("minecraft:glowstone_dust", 5))),
    )
    for _, job, inputs in first_jobs:
        for slot, (item, count) in enumerate(inputs):
            supply(server, job, slot, item, count)
    server.assert_state("native_hoppers_load_raw_materials_before_power_is_applied",
                        " ".join(f"unless data block {job.pos(81)} Items[0]" for _, job, _ in first_jobs), results)
    for _, job, _ in first_jobs:
        start_smelter(server, job)
    for label, job, _ in first_jobs:
        server.assert_state(label + "_stock_batch_preserves_exact_fe_and_output", finished_smelter(job), results, timeout=100)

    transfer(server, DARK_TO_END.pos(79), 0, END.pos(81), 0)
    supply(server, END, 1, "minecraft:end_stone", 10)
    supply(server, END, 2, "minecraft:obsidian", 10)
    start_smelter(server, END)
    server.assert_state("end_steel_spends_the_ten_original_dark_steel_ingots",
                        f"{finished_smelter(END)} unless data block {DARK_TO_END.pos(79)} Items[0]", results, timeout=100)
    for slot, job in enumerate(PLAYER_OUTPUTS):
        transfer(server, job.pos(79), 0, CHEST, slot)


def placed_blocks():
    return " ".join(f"if block {3208 + index % 5} 80 {9 + 2 * (index // 5)} enderio:{name}_block"
                    for index, name in enumerate(ALLOYS))


def exercise_client(args, server, results, report, *, scenario="alloy-progression", placements=placed_blocks):
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    summary = evidence / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", scenario, "--output", str(evidence), "--server", f"127.0.0.1:{args.port}",
               "--timeout", "185"]
    with (server.directory / "alloy-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            server.command(f"tp {PLAYER} 3210.5 80 12.5 180 19")
            (evidence / "server-ready").touch()
            wait_signal(client, evidence, "placed")
            server.assert_state("survival_uses_only_the_original_machine_outputs",
                                f"{placements()} unless data block {CHEST} Items[0]", results)
            (evidence / "server-done").touch()
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"Alloy progression client failed; see {evidence}")
            captures = json.loads(summary.read_text())["client"]["captures"]
            for capture in captures.values():
                if digest(Path(capture["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                    raise AssertionError("Alloy client and server Ender IO jars differ")
        finally:
            stop_client(client)
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def verify_saved_inventory(directory, report, *, expected=None):
    world = directory / WORLD
    # The disposable offline profile has exactly one player.
    player, = (world / "playerdata").glob("*.dat")
    counts = {}
    for entry in read_data_file(player)["Inventory"]:
        counts[entry["id"]] = counts.get(entry["id"], 0) + entry["Count"]
    if expected is None:
        expected = {f"enderio:{alloy}_ingot": count for alloy, count in zip(ALLOYS, REMAINDERS) if count}
    report["savedInventory"] = counts
    if counts != expected:
        raise AssertionError(f"Saved survival inventory {counts} differs from {expected}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    for key in ("forge-server", "jar", "launch"):
        parser.add_argument(f"--{key}", type=Path, required=True)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}

    def fresh(server, results):
        produce(server, results)
        exercise_client(args, server, results, report)

    def reopened(server, results):
        load(server, results)
        balances = " ".join(f"if data block {job.pos(80)} {{EnergyStored:137}} unless data block {job.pos(80)} Task"
                            for job in (*PLAYER_OUTPUTS, DARK_TO_END))
        server.assert_state("placed_alloys_and_all_ten_fe_balances_survive_restart",
                            f"{placed_blocks()} {balances} unless data block {CHEST} Items[0]", results)

    try:
        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", reopened)
        verify_saved_inventory(directory, report)
        report["passed"] = True
    finally:
        result = directory / "alloy-progression-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Alloy progression passed={report['passed']}, {result}", flush=True)


if __name__ == "__main__":
    main()
