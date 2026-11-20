#!/usr/bin/env python3
"""Check live capacitor-bank corner textures in guarded packaged Forge clients."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_capacitor_banks import check, read_saved_networks
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage
from packaged_starter_progression import PLAYER


CORNER = (5248, 80, 8)
CELLS = {
    (5245, 79, 8): ("basic", "disable"),
    (5246, 79, 8): ("vibrant", "push"),
    (5246, 80, 8): ("vibrant", None),
    (5247, 79, 8): ("advanced", "pull"),
    (5247, 80, 8): ("advanced", None),
    (5248, 79, 8): ("advanced", "both"),
    CORNER: ("advanced", None),
}
CAPACITY = {"basic": 500_000, "advanced": 2_000_000, "vibrant": 4_000_000}
CAMERA = "5247.5 78 14.5 180 0"


def block(tier):
    return f"enderio:{tier}_capacitor_bank"


def position(cell):
    return " ".join(map(str, cell))


def check_layout(server, results, label, corner_tier):
    cells = {**CELLS, CORNER: (corner_tier, None)}
    conditions = []
    for cell, (tier, mode) in cells.items():
        pos = position(cell)
        conditions.append(f"if block {pos} {block(tier)}")
        if mode:
            conditions.append(f'if data block {pos} {{IOConfig:{{Modes:{{south:"{mode}"}}}}}}')
    server.assert_state(f"{label}_retains_tiers_and_exposed_io", " ".join(conditions), results)

    server.command("save-all flush")
    networks = read_saved_networks(server.directory / WORLD / "data/enderio_capacitor_bank_network.dat")
    observed = [sorted((tuple(node["pos"]), node["capacity"]) for node in network["nodes"]) for network in networks]
    expected = [sorted((cell, CAPACITY[tier]) for cell, (tier, _) in cells.items())]
    check(f"{label}_keeps_all_tiers_in_one_exact_graph", expected, observed, results)
    if expected != observed:
        raise AssertionError(f"Unexpected bank graph during {label}: {observed}")


def setup(server, results):
    for command in ("gamerule doMobSpawning false", "gamerule doDaylightCycle false",
                    "gamerule doWeatherCycle false", "gamerule spawnRadius 0",
                    "forceload add 5232 0 5263 15", "weather clear 100000", "time set noon"):
        server.command(command)
    server.assert_state("both_bank_chunks_loaded", "if loaded 5247 79 8 if loaded 5248 80 8", results)
    server.command("fill 5241 77 5 5253 77 18 minecraft:stone_bricks")
    server.command("setworldspawn 5247 78 14")
    for cell, (tier, mode) in CELLS.items():
        nbt = f'{{IOConfig:{{Modes:{{south:"{mode}"}}}}}}' if mode else ""
        server.command(f"setblock {position(cell)} {block(tier)}{nbt}")
    check_layout(server, results, "matching", "advanced")


def exercise(args, server, report):
    results = report["assertions"]
    setup(server, results)
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    summary = evidence / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
               "--launch", str(args.launch.resolve()), "--scenario", f"bank-textures-{args.renderer}",
               "--server", f"127.0.0.1:{args.port}", "--output", str(evidence), "--timeout", "175"]
    with (server.directory / "bank-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            server.command(f"tp {PLAYER} {CAMERA}")
            for phase, tier in (("matching", "advanced"), ("mixed", "vibrant"),
                                ("restored", "advanced"), ("reloaded", "advanced")):
                if phase in ("mixed", "restored"):
                    server.command(f"setblock {position(CORNER)} {block(tier)}")
                    check_layout(server, results, phase, tier)
                (evidence / f"server-{phase}").touch()
                wait_signal(client, evidence, phase)
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"Bank texture client failed. See {evidence}")
            actual = json.loads(summary.read_text())["client"]["captures"]["matching"]["loadedMods"]["enderio"]["file"]
            actual_sha = digest(Path(actual))
            check("client_and_server_load_the_same_distributable", report["jarSha256"], actual_sha, results)
            if actual_sha != report["jarSha256"]:
                raise AssertionError("The client and server loaded different product jars")
        finally:
            stop_client(client)
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--renderer", required=True, choices=("core", "athena", "embeddium"))
    for path in ("java", "forge-server", "jar", "launch"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")

    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "jarSha256": digest(directory / "mods/enderio.jar"), "assertions": [], "servers": []}
    try:
        run_stage(args, directory, report, "exercise", lambda server, _: exercise(args, server, report))
        run_stage(args, directory, report, "reopened",
                  lambda server, results: check_layout(server, results, "reopened", "advanced"))
        report["passed"] = True
    finally:
        path = directory / "bank-textures-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Bank textures passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
