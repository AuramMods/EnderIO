#!/usr/bin/env python3
"""Exercise authored translucent terrain and shadow programs on a painted facade."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_facade_ctm import cover
from packaged_facades import ENERGY_CONDUIT, PLAYER
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage

FACADE = "2751 80 9"
CONTROL = "2754 80 9"
BLUE = "minecraft:blue_stained_glass"
LIME = "minecraft:lime_stained_glass"


def layout(paint):
    return (f'if block {FACADE} enderio:conduit if data block {FACADE} '
            f'{{{cover(paint)},Conduits:["enderio:energy"]}} if block {CONTROL} minecraft:red_stained_glass')


def repaint(server, paint):
    server.command(f"data merge block {FACADE} {{{cover(paint)}}}")


def setup(server, results):
    server.command("forceload add 2736 0 2767 31")
    server.assert_state("translucent_facade_and_control_chunks_load", f"if loaded {FACADE} if loaded {CONTROL}", results)
    for command in ("gamerule doMobSpawning false", "gamerule doDaylightCycle false", "gamerule spawnRadius 0",
                    "time set noon", "weather clear 100000", "fill 2745 79 5 2758 79 19 minecraft:stone",
                    "fill 2745 80 6 2758 81 6 minecraft:stone", "setworldspawn 2752 80 15"):
        server.command(command)
    server.command(f"setblock {FACADE} enderio:conduit{ENERGY_CONDUIT}")
    repaint(server, BLUE)
    server.command(f"setblock {CONTROL} minecraft:red_stained_glass")
    server.assert_state("blue_facade_and_ordinary_red_glass_across_chunks", layout(BLUE), results)


def observe(client, output, phase):
    (output / f"server-{phase}").touch()
    wait_signal(client, output, phase)


def exercise(server, client, output, results):
    observe(client, output, "visible")
    repaint(server, LIME)
    server.assert_state("lime_repaint_changes_only_the_facade", layout(LIME), results)
    observe(client, output, "repainted")
    repaint(server, BLUE)
    observe(client, output, "restored")
    server.command(f"item replace entity {PLAYER} hotbar.0 with enderio:yeta_wrench")
    server.assert_state("hidden_blue_facade_retains_its_world_paint_and_conduit", layout(BLUE), results)
    observe(client, output, "wrench")
    server.command(f"item replace entity {PLAYER} hotbar.0 with minecraft:air")
    observe(client, output, "revealed")
    observe(client, output, "reloaded")


def exercise_toggle(server, client, output, results):
    observe(client, output, "visible")
    server.command(f"item replace entity {PLAYER} hotbar.0 with enderio:yeta_wrench")
    for name in ("wrench", "disabled-wrench", "enabled-wrench"):
        observe(client, output, name)
    server.assert_state("shader_toggles_keep_the_wrench_and_loaded_facade",
                        f'{layout(BLUE)} if data entity {PLAYER} {{SelectedItem:{{id:"enderio:yeta_wrench"}}}}', results)
    server.command(f"item replace entity {PLAYER} hotbar.0 with minecraft:air")
    for name in ("revealed", "disabled-visible", "enabled-visible"):
        observe(client, output, name)
    server.assert_state("both_shader_round_trips_keep_the_same_world_paint_and_conduit", layout(BLUE), results)


def run_client(args, server, results, report, phase):
    output = ROOT / "build/port-evidence" / f"{args.name}-{phase}-client"
    summary = output / "summary.json"
    scenario = "facade-shader-toggle" if args.live_toggle else "facade-shader-passes"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
               "--launch", str(args.launch.resolve()), "--scenario", scenario,
               "--output", str(output), "--server", f"127.0.0.1:{args.port}",
               "--multiplayer-phase", phase, "--timeout", "155"]
    with (server.directory / f"shader-passes-{phase}-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            server.command(f"tp {PLAYER} 2752.5 80 15.5 180 11")
            if phase == "verify":
                server.assert_state("saved_facade_and_control_reopen_exactly", layout(BLUE), results)
                observe(client, output, "visible")
            elif args.live_toggle:
                exercise_toggle(server, client, output, results)
            else:
                exercise(server, client, output, results)
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"Shader pass client failed; see {output}")
            frame = json.loads(summary.read_text())["client"]["captures"]["visible"]
            if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Client and server Ender IO jars differ")
        finally:
            stop_client(client)
            report["clients"][phase] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", type=int, default=26124)
    parser.add_argument("--java", required=True)
    parser.add_argument("--live-toggle", action="store_true")
    parser.add_argument("--mod", action="append", default=[], type=Path, help="Install an optional mod on the server")
    for key in ("forge-server", "jar", "launch"):
        parser.add_argument(f"--{key}", type=Path, required=True)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")

    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    for mod in args.mod:
        shutil.copyfile(mod, directory / "mods" / mod.name)
    report = {"passed": False, "assertions": [], "clients": {}, "servers": [],
              "jarSha256": digest(directory / "mods/enderio.jar")}
    try:
        def fresh(server, results):
            setup(server, results)
            run_client(args, server, results, report, "exercise")

        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", lambda server, results:
                  run_client(args, server, results, report, "verify"))
        report["passed"] = True
    finally:
        result = directory / "shader-pass-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Shader passes passed={report['passed']}, {len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
