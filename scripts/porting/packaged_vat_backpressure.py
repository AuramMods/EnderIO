#!/usr/bin/env python3
"""Check both directions of a yield-changing swap in a completed stock VAT job."""

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
from packaged_server_smoke import ROOT, digest
from packaged_spawners import PLAYER, prepare_profile, run_stage
from packaged_vat_automation import WATER, enderio_fluid, fluid, tank_fields


@dataclass(frozen=True)
class Trial:
    name: str
    x: int
    slot: int
    original: str
    replacement: str
    original_yield: int
    new_yield: int

    @property
    def vat(self):
        return f"{self.x} 80 8"

    @property
    def receiver(self):
        return f"{self.x} 80 7"

    @property
    def rack(self):
        return f"{self.x} 80 13"


SNOWBALL = Trial("snowball", 4996, 1, "blue_ice", "snowball", 81000, 110)
BLUE_ICE = Trial("blue-ice", 5004, 0, "snowball", "blue_ice", 110, 81000)
TRIALS = (SNOWBALL, BLUE_ICE)
CLOUD = enderio_fluid("cloud_seed")
RECIPE = "enderio:fermenting/fluid_cloud_seed_still"
WATER_TAG = "{PortVatWater:234}"
ORIGINAL_TAG = "{PortVatOriginal:234}"
REPLACEMENT_TAG = "{PortVatReplacement:234}"


def tank(pos, amount, *, index=0, kind=CLOUD, tag=None):
    return f"if data block {pos} {{{tank_fields(fluid(kind, amount, tag), index)}}}"


def reagents(trial, *, swapped=False):
    catalyst = stack(trial.slot, 1, f"minecraft:{trial.replacement if swapped else trial.original}",
                     metadata=REPLACEMENT_TAG if swapped else ORIGINAL_TAG)
    shard = stack(1 - trial.slot, 1, "minecraft:prismarine_shard", metadata="{PortVatPrismarine:234}")
    return f"Items:{{Size:2,Items:[{catalyst},{shard}]}}"


def blocked(trial, *, swapped=False, stored_output=8000):
    amount = trial.new_yield if swapped else trial.original_yield
    return (f'if data block {trial.vat} {{{reagents(trial, swapped=swapped)},Task:{{RecipeId:"{RECIPE}",'
            f'ProgressMade:400,ProgressRequired:400,Outputs:[{{Fluid:{fluid(CLOUD, amount)}}}]}}}} '
            f"{tank(trial.vat, 1234, kind=WATER, tag=WATER_TAG)} {tank(trial.vat, stored_output, index=1)} "
            f"unless data block {trial.vat} PendingOutput")


def committed(trial, *, remaining=None):
    if remaining is None and trial == BLUE_ICE:
        remaining = BLUE_ICE.new_yield - 109
    if remaining is None:
        queue = f"unless data block {trial.vat} PendingOutput"
    else:
        queue = f"if data block {trial.vat} {{PendingOutput:{fluid(CLOUD, remaining)}}}"
    return (f"{tank(trial.vat, 234, kind=WATER, tag=WATER_TAG)} {tank(trial.vat, 8000, index=1)} {queue} "
            f"unless data block {trial.vat} Task unless data block {trial.vat} Items.Items[0]")


def fresh(server, results):
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    server.command("forceload add 4992 0 5007 15")
    server.assert_state("both_vat_positions_load", " ".join(f"if loaded {trial.vat}" for trial in TRIALS), results)
    server.command("fill 4992 79 6 5007 79 14 minecraft:stone_bricks")
    server.command("setworldspawn 4996 80 11")
    for trial in TRIALS:
        server.command(f"setblock {trial.receiver} minecraft:air")
        fluids = (f"Fluids:{{Tanks:[{{Index:0,Contents:{fluid(WATER, 1234, WATER_TAG)}}},"
                  f"{{Index:1,Contents:{fluid(CLOUD, 8000)}}}]}}")
        server.command(f"setblock {trial.vat} enderio:vat[facing=south]"
                       f'{{IOConfig:{{Modes:{{north:"push"}}}},{fluids},{reagents(trial)}}}')
    server.assert_state("both_cloud_seed_batches_finish_fermenting_with_unspent_inputs_and_full_outputs",
                        " ".join(map(blocked, TRIALS)), results, timeout=75)


def observe(server, trial, name, expected, results):
    try:
        server.assert_state(f"{trial.name}_{name}", expected, results, timeout=4)
    except AssertionError:
        server.command(f"data get block {trial.vat}")


def receiver(server, trial, amount, *, pressurized=False):
    block = "pressurized_fluid_tank" if pressurized else "fluid_tank"
    contents = f"{{{tank_fields(fluid(CLOUD, amount))}}}" if amount else ""
    server.command(f"setblock {trial.receiver} enderio:{block}[facing=south]{contents}")


def exercise(server, client, evidence, trial, hotbar, results):
    server.command(f"item replace entity {PLAYER} hotbar.{hotbar} with minecraft:{trial.replacement}{REPLACEMENT_TAG}")
    server.command(f"tp {PLAYER} {trial.x + 0.5} 80 11.5 180 16")
    (evidence / f"server-{trial.name}-open").touch()
    wait_signal(client, evidence, f"{trial.name}-swapped")
    original = stack(hotbar, 1, f"minecraft:{trial.original}", metadata=ORIGINAL_TAG, slot_suffix="b", count_suffix="b")
    player = f"if data entity {PLAYER} Inventory[{original}]"
    server.assert_state(f"{trial.name}_native_swap_preserves_the_original_catalyst_and_full_tank",
                        f"{player} if data block {trial.vat} {{{reagents(trial, swapped=True)}}} "
                        f"{tank(trial.vat, 8000, index=1)}", results)
    observe(server, trial, "updates_the_queued_yield_without_losing_paid_progress", blocked(trial, swapped=True), results)

    receiver(server, trial, 15891)
    server.assert_state(f"{trial.name}_receiver_takes_exactly_109_mb_of_the_old_output",
                        tank(trial.receiver, 16000), results)
    advance(server)
    if trial == SNOWBALL:
        observe(server, trial, "retains_110_mb_job_and_inputs_with_only_109_mb_free",
                blocked(trial, swapped=True, stored_output=7891), results)
        server.command(f"clone {trial.receiver} {trial.receiver} {trial.rack} replace move")
        receiver(server, trial, 15999)
        server.assert_state("snowball_second_receiver_takes_exactly_one_more_mb",
                            f"{tank(trial.receiver, 16000)} {tank(trial.rack, 16000)}", results)
        advance(server)
    observe(server, trial, "consumes_the_new_reagent_once_and_commits_its_exact_yield",
            f"{committed(trial)} {player}", results)
    (evidence / f"server-{trial.name}-finished").touch()
    wait_signal(client, evidence, f"{trial.name}-done")


def reopened(args, server, results, report):
    server.assert_state("restart_restores_both_completed_jobs_and_original_yields", " ".join(map(blocked, TRIALS)), results)
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    summary = evidence / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "vat-backpressure", "--server", f"127.0.0.1:{args.port}", "--output", str(evidence), "--timeout", "300"]
    with (server.directory / "vat-backpressure-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            for hotbar, trial in enumerate(TRIALS):
                exercise(server, client, evidence, trial, hotbar, results)
            report["clientExit"] = client.wait(timeout=35)
        finally:
            stop_client(client)
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None
    if report["client"]:
        mods = report["client"]["client"]["captures"]["blue-ice-finished"]["loadedMods"]
        match = digest(Path(mods["enderio"]["file"])) == report["jarSha256"] and "jei" not in mods
        results.append({"assertion": "client_uses_the_same_packaged_core_jar", "passed": match})
    if report["clientExit"] or not all(result["passed"] for result in results):
        raise AssertionError(f"VAT yield swap failed; see {server.directory}")


def retained(server, results):
    server.assert_state("both_exact_yields_and_large_paid_remainder_survive_restart",
                        f"{committed(SNOWBALL)} {committed(BLUE_ICE)} {tank(SNOWBALL.rack, 16000)} "
                        f"{tank(SNOWBALL.receiver, 16000)} {tank(BLUE_ICE.receiver, 16000)}", results)
    server.command(f"clone {BLUE_ICE.receiver} {BLUE_ICE.receiver} {BLUE_ICE.rack} replace move")
    receiver(server, BLUE_ICE, 0, pressurized=True)
    server.assert_state("large_paid_remainder_resumes_delivery_without_consuming_again",
                        f"{committed(BLUE_ICE, remaining=48891)} {tank(BLUE_ICE.receiver, 32000)} "
                        f"{tank(BLUE_ICE.rack, 16000)}", results, timeout=25)


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
        path = directory / "vat-backpressure-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"VAT output passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
