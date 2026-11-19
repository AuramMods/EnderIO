#!/usr/bin/env python3
"""Exercise two perpendicular fused-quartz panels across render-section edges."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_facade_ctm import cover
from packaged_facades import ENERGY_CONDUIT, PLAYER
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage


FACADE = "2736 80 16"
GLASS = ("2735 79 16", "2735 80 16", "2736 79 16", "2736 79 15", "2736 80 15")
BLUE = "enderio:fused_quartz_blue"
RED = "enderio:fused_quartz_red"
VIEWS = {"south": "2736.0 79 21.5 180 8", "east": "2741.5 79 16.0 90 8"}


def layout(paint):
    ordinary = " ".join(f"if block {pos} {BLUE}" for pos in GLASS)
    return (f'if block {FACADE} enderio:conduit if data block {FACADE} '
            f'{{{cover(paint)},Conduits:["enderio:energy"]}} {ordinary}')


def setup(server, results):
    server.command("forceload add 2720 0 2751 31")
    server.assert_state("both_perpendicular_panels_are_loaded",
                        f"if loaded {FACADE} if loaded {GLASS[0]} if loaded {GLASS[-1]}", results)
    for command in ("gamerule doMobSpawning false", "gamerule doDaylightCycle false", "gamerule spawnRadius 0",
                    "time set noon", "weather clear 100000", "fill 2728 78 7 2747 78 26 minecraft:stone",
                    "setworldspawn 2736 79 22"):
        server.command(command)
    for pos in GLASS:
        server.command(f"setblock {pos} {BLUE}")
    server.command(f"setblock {FACADE} enderio:conduit{ENERGY_CONDUIT}")
    server.command(f"data merge block {FACADE} {{{cover(BLUE)}}}")
    server.assert_state("one_facade_matches_both_sections_of_quartz", layout(BLUE), results)


def observe(server, client, output, phase):
    for view, camera in VIEWS.items():
        server.command(f"tp {PLAYER} {camera}")
        name = f"{phase}-{view}"
        (output / f"server-{name}").touch()
        wait_signal(client, output, name)


def exercise(server, client, output, results):
    observe(server, client, output, "matching")
    server.command(f"item replace entity {PLAYER} hotbar.0 with enderio:yeta_wrench")
    server.assert_state("wrench_leaves_the_world_corner_untouched", layout(BLUE), results)
    observe(server, client, output, "wrench")
    server.command(f"item replace entity {PLAYER} hotbar.0 with minecraft:air")
    observe(server, client, output, "revealed")
    server.command(f"data merge block {FACADE} {{{cover(RED)}}}")
    server.assert_state("only_the_shared_corner_facade_changes_paint", layout(RED), results)
    observe(server, client, output, "repainted")
    server.command(f"data merge block {FACADE} {{{cover(BLUE)}}}")
    server.assert_state("blue_paint_and_the_energy_conduit_restore", layout(BLUE), results)
    observe(server, client, output, "restored")
    observe(server, client, output, "reloaded")


def run_client(args, server, results, report, *, reopened=False):
    phase = "verify" if reopened else "exercise"
    output = ROOT / "build/port-evidence" / f"{args.name}-{phase}-client"
    summary = output / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
               "--launch", str(args.launch.resolve()), "--scenario", f"facade-corners-{args.renderer}",
               "--output", str(output), "--server", f"127.0.0.1:{args.port}",
               "--multiplayer-phase", phase, "--timeout", "195"]
    with (server.directory / f"corner-client-{phase}.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            if reopened:
                server.assert_state("both_saved_panels_and_facade_reopen_exactly", layout(BLUE), results)
                observe(server, client, output, "matching")
            else:
                exercise(server, client, output, results)
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"Fused-quartz corner client failed; see {output}")
            capture = json.loads(summary.read_text())["client"]["captures"]["matching-south"]
            if digest(Path(capture["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Client and server Ender IO jars differ")
        finally:
            stop_client(client)
            report["clients"][phase] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--launch", required=True, type=Path)
    parser.add_argument("--renderer", required=True, choices=("core", "athena", "shader"))
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")

    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "jarSha256": digest(directory / "mods/enderio.jar"),
              "assertions": [], "clients": {}, "servers": []}
    try:
        def first(server, results):
            setup(server, results)
            run_client(args, server, results, report)

        run_stage(args, directory, report, "fresh", first)
        run_stage(args, directory, report, "reopened", lambda server, results:
                  run_client(args, server, results, report, reopened=True))
        report["passed"] = True
    finally:
        path = directory / "facade-corners-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Quartz corners passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
