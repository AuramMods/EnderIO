#!/usr/bin/env python3
"""Check real survival placement and rendered Enchanter models in packaged Forge."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage
from packaged_starter_progression import PLAYER


STANDS = (("north", 5282, "5282.5 80 5.5 0 19"),
          ("east", 5290, "5293.5 80 8.5 90 19"),
          ("south", 5298, "5298.5 80 11.5 180 19"),
          ("west", 5306, "5303.5 80 8.5 270 19"))
ENCHANTER = "enderio:enchanter"


def layout():
    return " ".join(f"if block {x} 80 8 {ENCHANTER}[facing={direction}]" for direction, x, _ in STANDS)


def setup(server, results):
    for command in ("gamerule doMobSpawning false", "gamerule doDaylightCycle false",
                    "gamerule doWeatherCycle false", "gamerule spawnRadius 0",
                    "forceload add 5280 0 5311 15", "weather clear 100000", "time set noon"):
        server.command(command)
    server.assert_state("placement_chunks_are_loaded", "if loaded 5282 80 8 if loaded 5306 80 8", results)
    server.command("fill 5280 79 3 5311 79 15 minecraft:stone_bricks")
    server.command("setworldspawn 5282 80 5")


def exercise(args, server, report):
    results = report["assertions"]
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    summary = evidence / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
               "--launch", str(args.launch.resolve()), "--scenario", f"enchanter-presentation-{args.renderer}",
               "--server", f"127.0.0.1:{args.port}", "--output", str(evidence), "--timeout", "175"]
    with (server.directory / "enchanter-presentation-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            server.command(f"item replace entity {PLAYER} hotbar.0 with {ENCHANTER} 5")
            for index, (direction, x, camera) in enumerate(STANDS, start=1):
                server.command(f"tp {PLAYER} {camera}")
                (evidence / f"server-{direction}").touch()
                try:
                    server.assert_state(f"survival_placement_faces_{direction}_and_consumes_one_item",
                                        f'if block {x} 80 8 {ENCHANTER}[facing={direction}] '
                                        f'if data entity {PLAYER} Inventory[{{Slot:0b,id:"{ENCHANTER}",Count:{5 - index}b}}]',
                                        results, timeout=20)
                except AssertionError:
                    server.command(f"data get block {x} 80 8")
                    server.command(f"data get entity {PLAYER} Inventory")
                    server.command(f"data get entity {PLAYER} Pos")
                    server.command(f"data get entity {PLAYER} Rotation")
                    raise
                wait_signal(client, evidence, direction)
            (evidence / "server-inventory").touch()
            wait_signal(client, evidence, "inventory")
            (evidence / "server-reload").touch()
            wait_signal(client, evidence, "reloaded")
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"Enchanter presentation client failed. See {evidence}")
            frame = json.loads(summary.read_text())["client"]["captures"]["north"]
            matches = digest(Path(frame["loadedMods"]["enderio"]["file"])) == report["jarSha256"]
            results.append({"assertion": "client_and_server_load_the_same_distributable", "passed": matches})
            if not matches:
                raise AssertionError("The client and server loaded different product jars")
        finally:
            stop_client(client)
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--renderer", required=True, choices=("core", "embeddium"))
    for path in ("java", "forge-server", "jar", "launch"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")

    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "jarSha256": digest(directory / "mods/enderio.jar"), "assertions": [], "servers": []}

    def first(server, results):
        setup(server, results)
        exercise(args, server, report)

    try:
        run_stage(args, directory, report, "exercise", first)
        run_stage(args, directory, report, "reopened", lambda server, results:
                  server.assert_state("all_four_survival_facings_survive_restart", layout(), results))
        report["passed"] = True
    finally:
        path = directory / "enchanter-presentation-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Enchanter presentation passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
