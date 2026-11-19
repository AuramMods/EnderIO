#!/usr/bin/env python3
"""Exercise connected facade textures in a packaged Forge client and TCP world."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_facades import ENERGY_CONDUIT, PLAYER
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_spawners import prepare_profile, run_stage


WEST = "2735 80 9"
CENTER = "2736 80 9"
EAST = "2737 80 9"
BLUE = "enderio:clear_glass_blue"
RED = "enderio:clear_glass_red"


def cover(paint):
    return ('FacadeProvider:{id:"enderio:transparent_conduit_facade",Count:1,'
            f'tag:{{"enderio:data":{{"enderio:block_paint":{{paint:"{paint}"}}}}}}}}')


def layout(center):
    return (f'if block {WEST} enderio:conduit if data block {WEST} {{{cover(BLUE)},Conduits:["enderio:energy"]}} '
            f'if block {CENTER} enderio:conduit if data block {CENTER} {{{cover(center)},Conduits:["enderio:energy"]}} '
            f'if block {EAST} {BLUE}')


def fresh(args, server, results, report):
    server.command("forceload add 2720 0 2751 31")
    server.assert_state("both_glass_chunks_loaded", f"if loaded {WEST} if loaded {EAST}", results)
    server.command("gamerule doMobSpawning false")
    server.command("gamerule doDaylightCycle false")
    server.command("gamerule spawnRadius 0")
    server.command("time set noon")
    server.command("fill 2728 79 5 2744 79 19 minecraft:stone")
    server.command("setworldspawn 2736 80 14")
    for pos in (WEST, CENTER):
        server.command(f"setblock {pos} enderio:conduit{ENERGY_CONDUIT}")
        server.command(f"data merge block {pos} {{{cover(BLUE)}}}")
    server.command(f"setblock {EAST} {BLUE}")
    server.assert_state("matching_layout_spans_two_chunks", layout(BLUE), results)
    exercise_client(args, server, results, report)


def exercise_client(args, server, results, report):
    output = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", f"facade-ctm-{args.renderer}", "--output", str(output),
               "--server", f"127.0.0.1:{args.port}", "--timeout", "175"]
    summary_path = output / "summary.json"
    with (server.directory / "facade-ctm-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            server.command(f"tp {PLAYER} 2736.5 80 14.5 180 10")
            (output / "server-matched").touch()
            wait_signal(client, output, "matched")
            server.command(f"data merge block {CENTER} {{{cover(RED)}}}")
            server.assert_state("only_the_middle_paint_changes", layout(RED), results)
            (output / "server-split").touch()
            wait_signal(client, output, "split")
            server.command(f"data merge block {CENTER} {{{cover(BLUE)}}}")
            server.assert_state("restoring_paint_preserves_both_energy_conduits", layout(BLUE), results)
            (output / "server-rematched").touch()
            if args.renderer.startswith("oculus"):
                wait_signal(client, output, "rematched")
                server.command(f"item replace entity {PLAYER} hotbar.0 with enderio:yeta_wrench")
                (output / "server-wrench").touch()
                wait_signal(client, output, "wrench")
                server.command(f"item replace entity {PLAYER} hotbar.0 with minecraft:air")
                server.assert_state("wrench_visibility_does_not_modify_world_paint", layout(BLUE), results)
                (output / "server-wrench-away").touch()
            if client.wait(timeout=65) != 0:
                raise AssertionError(f"Connected texture client failed; see {output}")
            summary = json.loads(summary_path.read_text())
            frame = summary["client"]["captures"]["connected-facades"]
            if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Client and server Ender IO jars differ")
        finally:
            stop_client(client)
            report["client"] = json.loads(summary_path.read_text()) if summary_path.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--launch", required=True, type=Path)
    parser.add_argument("--renderer", required=True,
                        choices=("core", "athena", "embeddium", "oculus", "oculus-shader", "oculus-terrain"))
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")

    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "forge": FORGE, "jarSha256": digest(directory / "mods/enderio.jar"),
              "assertions": [], "servers": []}
    try:
        run_stage(args, directory, report, "fresh", lambda server, results: fresh(args, server, results, report))
        run_stage(args, directory, report, "reopened", lambda server, results:
                  server.assert_state("matching_layout_reopens_exactly", layout(BLUE), results))
        report["passed"] = True
    finally:
        path = directory / "facade-ctm-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Connected textures passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
