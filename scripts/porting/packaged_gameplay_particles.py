#!/usr/bin/env python3
"""Exercise the packaged Experience Rod and SAG Mill effects during live gameplay."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_sag_progression import charge, feed, inputs, mill, output, prepare_mill
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage
from packaged_starter_progression import hopper_item
from packaged_xp_tools import PLAYER, PLAYER_POS, ROD, TANK, player_xp, set_contents, tank


MILL_X = 6
PAID_STONE = 4
ENERGY = PAID_STONE * 2400
ROD_TRIALS = (("rod-all", 159, 7), ("rod-minimal", 79, 10), ("rod-restored", 79, 13), ("rod-rejected", None, 13))


def ready(evidence, name):
    (evidence / f"server-{name}").touch()


def setup(server, results):
    for command in ("gamerule doMobSpawning false", "gamerule doDaylightCycle false",
                    "gamerule spawnRadius 0", "time set noon", "forceload add -16 -16 15 15",
                    "fill -3 79 -2 10 79 14 minecraft:smooth_quartz", "setworldspawn 0 80 3",
                    f"setblock {TANK} enderio:fluid_tank"):
        server.command(command)
    prepare_mill(server, MILL_X)
    inputs(server, MILL_X, "minecraft:stone", PAID_STONE)
    server.assert_state("the_native_hopper_delivers_four_stone_to_the_idle_mill",
                        f'if data block {mill(MILL_X)} Items.Items[{{Slot:0,id:"minecraft:stone",Count:{PAID_STONE}}}] '
                        f'unless data block {feed(MILL_X)} Items[0] if block {mill(MILL_X)} enderio:sag_mill[powered=false]', results)


def finished():
    return (f'if block {mill(MILL_X)} enderio:sag_mill[powered=false] '
            f'if data block {mill(MILL_X)} {{EnergyStored:0}} unless data block {mill(MILL_X)} Task '
            f'unless data block {mill(MILL_X)} Items.Items[{{Slot:0}}] '
            f'{hopper_item(output(MILL_X), "minecraft:cobblestone", PAID_STONE)}')


def exercise(args, server, results, report):
    setup(server, results)
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    summary = evidence / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "gameplay-particles", "--output", str(evidence), "--server", f"127.0.0.1:{args.port}", "--timeout", "200"]
    with (server.directory / "client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            server.command(f"item replace entity {PLAYER} hotbar.0 with {ROD}")
            server.command(f"tp {PLAYER} {PLAYER_POS} 180 17")
            for name, contents, points in ROD_TRIALS:
                if contents is not None:
                    set_contents(server, contents)
                ready(evidence, name)
                wait_signal(client, evidence, name)
                server.assert_state(f"{name.replace('-', '_')}_leaves_the_exact_xp_and_fluid",
                                    f"{player_xp(points)} {tank(19)}", results)

            server.command(f"tp {PLAYER} {MILL_X + 0.5} 80 11.5 180 17")
            ready(evidence, "mill-idle")
            wait_signal(client, evidence, "mill-idle")
            charge(server, MILL_X, ENERGY)
            server.assert_state("the_stock_stone_recipe_powers_the_actual_machine",
                                f'if block {mill(MILL_X)} enderio:sag_mill[powered=true] '
                                f'if data block {mill(MILL_X)} Task{{RecipeId:"enderio:sag_milling/stone",ProgressRequired:2400}}', results)
            ready(evidence, "mill-started")
            wait_signal(client, evidence, "mill-minimal")
            server.assert_state("the_server_stays_powered_while_the_client_suppresses_both_effects",
                                f"if block {mill(MILL_X)} enderio:sag_mill[powered=true]", results)
            ready(evidence, "mill-resume")
            wait_signal(client, evidence, "mill-restored")
            server.assert_state("the_stock_batch_uses_all_9600_fe_and_exports_four_cobblestone", finished(), results, timeout=45)
            ready(evidence, "mill-finished")
            if client.wait(timeout=20) != 0:
                raise AssertionError(f"Gameplay particle client failed; see {evidence}")
            frame = json.loads(summary.read_text())["client"]["captures"]["mill-active"]
            if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Gameplay particle client and server loaded different Ender IO jars")
        finally:
            stop_client(client)
            if summary.exists():
                report["client"] = json.loads(summary.read_text())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for key in ("java", "forge-server", "jar", "launch"):
        parser.add_argument(f"--{key}", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}
    try:
        run_stage(args, directory, report, "exercise", lambda server, results: exercise(args, server, results, report))
        run_stage(args, directory, report, "reopened", lambda server, results: server.assert_state(
            "the_exact_stone_output_and_remaining_xp_juice_survive_restart", f"{finished()} {tank(19)}", results))
        report["passed"] = True
    finally:
        (directory / "gameplay-particle-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
