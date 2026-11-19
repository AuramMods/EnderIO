#!/usr/bin/env python3
"""Verify the real VAT screen and a redstone-paused output queue on packaged Forge."""

import argparse
from dataclasses import replace
import json
from pathlib import Path
import subprocess
import sys
import time

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_levitation_staff import game_tick
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage
from packaged_vat_automation import TAGGED, configure, drained, fluid, set_mode, write_pack
from packaged_vat_large_batch import BLUE_ICE, CLOUD, has_batch


QUEUE = replace(BLUE_ICE, x=4470)
PREVIEW = replace(TAGGED, x=4476)
RACK = "4469 80 12"
POWER = "4470 81 8"


def queued(amount):
    return f'if data block {QUEUE.vat} {{PendingOutput:{fluid(CLOUD, amount)}}}'


def modes():
    return f'if data block {QUEUE.vat} {{RedstoneControl:"active_with_signal"}}'


def setup(server, results):
    server.command("forceload add 4464 0 4495 15")
    server.assert_state("vat_chunks_are_loaded", f"if loaded {QUEUE.vat} if loaded {PREVIEW.vat}", results)
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    server.command("fill 4466 79 6 4480 79 14 minecraft:stone")
    server.command("setworldspawn 4473 80 10")
    server.command("time set noon")
    configure(server, PREVIEW, tagged=True)
    set_mode(server, PREVIEW.vat, "north", "pull")
    configure(server, QUEUE)
    server.assert_state("both_real_vats_are_ready_for_the_menu", f'{queued(57000)} {has_batch(QUEUE.receiver, 16000)} '
                        f'if data block {PREVIEW.vat} Task{{RecipeId:"port_qa:tagged_batch",ProgressMade:160}}', results, timeout=60)


def stopped_output():
    return f"{modes()} {queued(57000)} {drained(QUEUE.vat, 1)} {has_batch(QUEUE.receiver, 8000)} {has_batch(RACK, 16000)}"


def resumed_output():
    return (f"{modes()} {queued(25000)} {has_batch(QUEUE.receiver, 32000)} {has_batch(QUEUE.vat, 8000, 1)} "
            f"{has_batch(RACK, 16000)} if block {POWER} minecraft:redstone_block")


def exercise(args, server, results, report):
    setup(server, results)
    output = ROOT / "build/port-evidence" / args.name
    phase = "verify" if args.baseline else "exercise"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "vat-controls", "--multiplayer-phase", phase, "--output", str(output),
               "--server", f"127.0.0.1:{args.port}", "--timeout", "190"]
    with (server.directory / "client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            if not args.baseline:
                wait_signal(client, output, "paused")
                server.assert_state("native_picker_selects_signal_mode_on_server", f"{modes()} {queued(57000)}", results)
                server.command(f"clone {QUEUE.receiver} {QUEUE.receiver} {RACK} replace move")
                server.command(f"setblock {QUEUE.receiver} enderio:pressurized_fluid_tank[facing=south]")
                set_mode(server, QUEUE.receiver, "north", "pull")
                server.assert_state("external_pull_empties_published_output_while_vat_is_paused", stopped_output(), results)
                start = game_tick(server)
                deadline = time.monotonic() + 15
                while game_tick(server) < start + 15:
                    if time.monotonic() >= deadline:
                        raise AssertionError("The paused VAT fixture did not advance its game ticks")
                server.assert_state("empty_output_stays_empty_across_paused_work_cycles", stopped_output(), results)
                (output / "server-drained").touch()
                wait_signal(client, output, "drained")
                server.command(f"setblock {POWER} minecraft:redstone_block")
                server.assert_state("physical_redstone_resumes_exact_paid_output_without_consuming_again", resumed_output(), results)
                (output / "server-resumed").touch()
            if client.wait(timeout=155 if args.baseline else 45) != 0:
                raise AssertionError(f"VAT client failed, see {output}")
            summary = json.loads((output / "summary.json").read_text())
            frame = summary["client"]["captures"]["reversed-reagents"]
            if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("VAT client and server used different production jars")
        finally:
            stop_client(client)
            if (output / "summary.json").exists():
                report["client"] = json.loads((output / "summary.json").read_text())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--forge-server", type=Path, required=True)
    parser.add_argument("--launch", type=Path, required=True)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--baseline", action="store_true")
    args = parser.parse_args()
    root = ROOT / "build/port-runtime" / args.name
    root.mkdir()
    profile = root / "server"
    report = {"passed": False, "jarSha256": digest(args.jar), "assertions": [], "servers": []}
    try:
        prepare_profile(args, profile, args.jar)
        write_pack(profile)
        run_stage(args, profile, report, "exercise", lambda server, results: exercise(args, server, results, report))
        if not args.baseline:
            run_stage(args, profile, report, "reopened", lambda server, results: server.assert_state(
                "redstone_mode_and_paid_remainder_survive_restart", resumed_output(), results))
        report["passed"] = True
    finally:
        (root / "vat-controls-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
