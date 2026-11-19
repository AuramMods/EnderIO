#!/usr/bin/env python3
"""Smelt the original crystal ingredients, craft in survival, and resume both powder jobs."""

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_alloy_progression import verify_saved_inventory
from packaged_glass_progression import Smelt, finished_smelter, prepare_smelter, start_smelter, supply, transfer
from packaged_sag_progression import charge, mill, output, prepare_mill
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage
from packaged_starter_progression import PLAYER, hopper_item


@dataclass(frozen=True)
class Crystal:
    kind: str
    x: int
    smelt: Smelt

    @property
    def item(self):
        return f"enderio:{self.kind}_crystal"

    @property
    def powder(self):
        return f"enderio:{self.kind}_powder"

    @property
    def recipe(self):
        return f"enderio:sag_milling/{self.kind}_crystal"


CHEST = "3403 80 8"
PULSATING = Crystal("pulsating", 3406, Smelt(3398, 3, "pulsating_alloy_ingot", 2, 2400))
VIBRANT = Crystal("vibrant", 3409, Smelt(3410, 3, "vibrant_alloy_ingot", 8, 2800))
CRYSTALS = (PULSATING, VIBRANT)
CONDUCTIVE = Smelt(3402, 3, "conductive_alloy_ingot", 2, 1600)
ENERGETIC = Smelt(3406, 3, "energetic_alloy_ingot", 4, 2400)
COST, PAID, RESERVE = 2400, 600, 137
REMAINDER = {"enderio:pulsating_alloy_ingot": 1, "enderio:vibrant_alloy_ingot": 7,
             "enderio:pulsating_alloy_nugget": 1, "enderio:vibrant_alloy_nugget": 1}


def load(server, results):
    server.command("forceload add 3392 0 3423 15")
    server.assert_state("both_mills_and_the_workshop_are_loaded",
                        f"if loaded {CHEST} if loaded {mill(VIBRANT.x)}", results)


def smelt(server, results, job, stock, previous=None):
    if previous is not None:
        transfer(server, previous.pos(79), 0, job.pos(81), len(stock))
    for slot, (item, count) in enumerate(stock):
        supply(server, job, slot, item, count)
    start_smelter(server, job)
    old_output = f" unless data block {previous.pos(79)} Items[0]" if previous is not None else ""
    server.assert_state(f"stock_{job.output}_smelts_on_its_exact_budget", finished_smelter(job) + old_output, results, timeout=40)


def produce(server, results):
    load(server, results)
    for command in ("gamerule doMobSpawning false", "gamerule doDaylightCycle false", "gamerule spawnRadius 0",
                    "gamerule doLimitedCrafting true", "time set noon", "fill 3397 78 2 3411 78 8 minecraft:stone",
                    "fill 3401 79 8 3411 79 14 minecraft:stone", "setworldspawn 3402 80 12",
                    "setblock 3402 80 8 minecraft:crafting_table", f"setblock {CHEST} minecraft:chest[facing=south]"):
        server.command(command)
    for job in (PULSATING.smelt, CONDUCTIVE, ENERGETIC, VIBRANT.smelt):
        prepare_smelter(server, job)
    smelt(server, results, PULSATING.smelt, (("minecraft:iron_ingot", 1), ("minecraft:ender_pearl", 1)))
    smelt(server, results, CONDUCTIVE, (("minecraft:iron_ingot", 1), ("minecraft:copper_ingot", 1)))
    smelt(server, results, ENERGETIC, (("minecraft:redstone", 2), ("minecraft:gold_ingot", 2)), CONDUCTIVE)
    smelt(server, results, VIBRANT.smelt, (("minecraft:ender_pearl", 4), ("minecraft:glowstone_dust", 4)), ENERGETIC)
    for slot, crystal in enumerate(CRYSTALS):
        transfer(server, crystal.smelt.pos(79), 0, CHEST, slot)
        prepare_mill(server, crystal.x)
        server.command(f"item replace block {crystal.x} 81 8 container.0 with minecraft:flint")
    for slot, gem in enumerate(("diamond", "emerald"), 2):
        server.command(f"item replace block {CHEST} container.{slot} with minecraft:{gem}")


def ball_untouched(crystal):
    pos = mill(crystal.x)
    return f'if data block {pos} Items.Items[{{Slot:5,id:"minecraft:flint",Count:1}}] unless data block {pos} GrindingBal'


def paused(crystal):
    pos = mill(crystal.x)
    return (f'if data block {pos} {{EnergyStored:0,Task:{{RecipeId:"{crystal.recipe}",ProgressMade:{PAID},'
            f'ProgressRequired:{COST},HasDeterminedOutputs:1b}}}} '
            f'if data block {pos} Items.Items[{{Slot:0,id:"{crystal.item}",Count:1}}] '
            f'if data block {pos} Task.Outputs[{{Item:{{id:"{crystal.powder}",Count:1}}}}] '
            f"unless data block {pos} Task.Outputs[1] unless data block {output(crystal.x)} Items[0] {ball_untouched(crystal)}")


def finished(crystal):
    pos = mill(crystal.x)
    hopper = output(crystal.x)
    return (f"if data block {pos} {{EnergyStored:{RESERVE}}} unless data block {pos} Task "
            + " ".join(f"unless data block {pos} Items.Items[{{Slot:{slot}}}]" for slot in range(5))
            + f" {hopper_item(hopper, crystal.powder, 1)} unless data block {hopper} Items[1] {ball_untouched(crystal)}")


def run_client(args, server, results, report):
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    summary = evidence / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "crystal-progression", "--server", f"127.0.0.1:{args.port}", "--output", str(evidence), "--timeout", "180"]
    with (server.directory / "crystal-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            server.command(f"tp {PLAYER} 3402.5 80 11.5 180 16")
            (evidence / "server-workshop").touch()
            wait_signal(client, evidence, "crafted")
            crafted = REMAINDER | {crystal.item: 1 for crystal in CRYSTALS}
            inventory = " ".join(f'if data entity {PLAYER} Inventory[{{id:"{item}",Count:{count}b}}]'
                                 for item, count in crafted.items())
            server.assert_state("survival_crafts_exactly_one_crystal_per_alloy_and_empties_the_source_chest",
                                f"{inventory} unless data block {CHEST} Items[0]", results)
            for crystal in CRYSTALS:
                server.command(f"tp {PLAYER} {crystal.x + 0.5} 80 11.5 180 16")
                (evidence / f"server-{crystal.kind}").touch()
                wait_signal(client, evidence, f"inserted-{crystal.kind}")
                pos = mill(crystal.x)
                server.assert_state(f"the_original_{crystal.kind}_crystal_reaches_the_mill_once",
                                    f'if data block {pos} Items.Items[{{Slot:0,id:"{crystal.item}",Count:1}}] '
                                    f'unless data entity {PLAYER} Inventory[{{id:"{crystal.item}"}}] {ball_untouched(crystal)}', results)
                charge(server, crystal.x, PAID)
                server.assert_state(f"{crystal.kind}_pauses_at_600_fe_with_one_queued_powder", paused(crystal), results)
                wait_signal(client, evidence, f"paused-{crystal.kind}")
            (evidence / "server-done").touch()
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"Crystal progression client failed; see {evidence}")
            for capture in json.loads(summary.read_text())["client"]["captures"].values():
                if digest(Path(capture["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                    raise AssertionError("Crystal client and server Ender IO jars differ")
        finally:
            stop_client(client)
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


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
        run_client(args, server, results, report)

    def resume(server, results):
        for crystal in CRYSTALS:
            server.assert_state(f"{crystal.kind}_preserves_the_paid_recipe_and_queued_powder_after_restart", paused(crystal), results)
            charge(server, crystal.x, COST - PAID + RESERVE)
        for crystal in CRYSTALS:
            server.assert_state(f"{crystal.kind}_exports_one_powder_after_spending_the_exact_remaining_fe", finished(crystal), results)

    def reopen(server, results):
        for crystal in CRYSTALS:
            server.assert_state(f"{crystal.kind}_keeps_its_powder_reserve_energy_and_unused_flint", finished(crystal), results)

    try:
        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "resumed", resume)
        run_stage(args, directory, report, "reopened", reopen)
        verify_saved_inventory(directory, report, expected=REMAINDER)
        report["passed"] = True
    finally:
        result = directory / "crystal-progression-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Crystal progression passed={report['passed']}, {len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
