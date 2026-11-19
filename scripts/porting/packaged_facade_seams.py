#!/usr/bin/env python3
"""Verify native mesh culling for neighboring glass facades."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_facades import ENERGY_CONDUIT, PLAYER, paint_data
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_spawners import prepare_profile, run_stage


WEST = "2703 80 9"
EAST = "2704 80 9"
RED = "red_stained_glass"
BLUE = "blue_stained_glass"
FACADE = "enderio:transparent_conduit_facade"


def cover(paint):
    return f'FacadeProvider:{{id:"{FACADE}",Count:1,{paint_data(paint)}}}'


def installed(pos, paint):
    return f"if block {pos} enderio:conduit if data block {pos} {{{cover(paint)},Conduits:[\"enderio:energy\"]}}"


def prepare(server, results):
    server.command("forceload add 2688 0 2719 31")
    server.assert_state("both_facade_sections_are_loaded", f"if loaded {WEST} if loaded {EAST}", results)
    server.command("gamerule doMobSpawning false")
    server.command("gamerule doDaylightCycle false")
    server.command("gamerule spawnRadius 0")
    server.command("time set noon")
    server.command("fill 2696 79 5 2711 79 25 minecraft:stone")
    server.command("setworldspawn 2701 80 14")
    for pos in (WEST, EAST, "2703 80 14", "2704 80 14", "2703 80 19"):
        server.command(f"setblock {pos} enderio:conduit{ENERGY_CONDUIT}")
    for pos, paint in ((WEST, RED), (EAST, RED), ("2703 80 14", RED), ("2704 80 14", BLUE), ("2703 80 19", RED)):
        server.command(f"data merge block {pos} {{{cover(paint)}}}")
    server.command(f"setblock 2704 80 19 minecraft:{RED}")
    server.assert_state("matching_facades_span_two_chunks", f"{installed(WEST, RED)} {installed(EAST, RED)}", results)
    server.assert_state("both_control_pairs_keep_their_original_paints",
                        f'{installed("2703 80 14", RED)} {installed("2704 80 14", BLUE)} '
                        f'{installed("2703 80 19", RED)} if block 2704 80 19 minecraft:{RED}', results)


def exercise_client(args, server, report):
    output = ROOT / "build/port-evidence" / f"{args.name}-client"
    scenario = "facade-seams-embeddium" if args.embeddium else "facade-seams"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", scenario, "--output", str(output), "--server", f"127.0.0.1:{args.port}", "--timeout", "165"]
    with (server.directory / "facade-seam-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            server.command(f"tp {PLAYER} 2700.5 80 12.5 224 12")
            server.command(f"item replace entity {PLAYER} hotbar.8 with enderio:yeta_wrench")
            (output / "server-original").touch()
            wait_signal(client, output, "original")
            server.command(f"data merge block {EAST} {{{cover(BLUE)}}}")
            server.assert_state("neighbor_repaint_preserves_the_conduit", installed(EAST, BLUE), report["assertions"])
            (output / "server-repainted").touch()
            wait_signal(client, output, "repainted")
            server.command(f"data merge block {EAST} {{{cover(RED)}}}")
            server.assert_state("restoring_the_original_paint_preserves_both_bundles",
                                f"{installed(WEST, RED)} {installed(EAST, RED)}", report["assertions"])
            (output / "server-restored").touch()
            if client.wait(timeout=45) != 0:
                raise AssertionError(f"Facade seam client failed; see {output}")
            summary = json.loads((output / "summary.json").read_text())
            frame = summary["client"]["captures"]["matching-facades"]
            if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Client and server Ender IO jars differ")
        finally:
            stop_client(client)
            summary = output / "summary.json"
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--launch", required=True, type=Path)
    parser.add_argument("--embeddium", action="store_true")
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")

    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "forge": FORGE, "jarSha256": digest(directory / "mods/enderio.jar"),
              "assertions": [], "servers": []}
    try:
        def fresh(server, results):
            prepare(server, results)
            exercise_client(args, server, report)

        def reopened(server, results):
            server.assert_state("both_matching_covers_and_conduits_reopen_exactly",
                                f"{installed(WEST, RED)} {installed(EAST, RED)}", results)

        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", reopened)
        report["passed"] = True
    finally:
        path = directory / "facade-seams-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Facade seams passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
