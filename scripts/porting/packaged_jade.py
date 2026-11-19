#!/usr/bin/env python3
"""Exercise Jade's real overlay in a packaged Forge session."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_facades import ENERGY_CONDUIT, PLAYER, paint_data
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_spawners import prepare_profile, run_stage


BOUND = "2784 80 8"
UNBOUND = "2787 80 8"
CONDUIT = "2790 80 8"
ENGINE = "enderio:soul_engine"
PAINT = paint_data("oak_planks")
FACADE = f'FacadeProvider:{{id:"enderio:conduit_facade",Count:1,{PAINT}}}'


def bound_state(soul):
    return f'if data block {BOUND} {{EntityStorage:{{entity_type:"minecraft:{soul}"}},EnergyStored:4321}}'


def other_targets():
    return (f"if block {UNBOUND} {ENGINE} unless data block {UNBOUND} EntityStorage.entity_type "
            f'if block {CONDUIT} enderio:conduit if data block {CONDUIT} {{{FACADE},Conduits:["enderio:energy"]}}')


def prepare(server, results):
    server.command("gamerule doMobSpawning false")
    server.command("gamerule doDaylightCycle false")
    server.command("gamerule spawnRadius 0")
    server.command("time set noon")
    server.command("forceload add 2768 0 2815 15")
    server.assert_state("fixture_chunk_loaded", f"if loaded {BOUND} if loaded {CONDUIT}", results)
    server.command("fill 2778 79 4 2798 79 15 minecraft:stone")
    server.command("setworldspawn 2784 80 11")
    server.command(f'setblock {BOUND} {ENGINE}[facing=south]{{EntityStorage:{{entity_type:"minecraft:blaze",entity_tag:{{}}}},'
                   'EnergyStored:4321,RedstoneControl:"never_active",'
                   'Items:{Items:[{Slot:0,id:"enderio:basic_capacitor",Count:1}]}}')
    server.command(f"setblock {UNBOUND} {ENGINE}[facing=south]")
    server.command(f"setblock {CONDUIT} enderio:conduit{ENERGY_CONDUIT}")
    server.command(f"data merge block {CONDUIT} {{{FACADE}}}")
    server.assert_state("bound_engine_has_blaze_and_exact_energy", bound_state("blaze"), results)
    server.assert_state("unbound_engine_and_painted_energy_conduit_are_distinct", other_targets(), results)


def aim(server, output, x, marker):
    server.command(f"tp {PLAYER} {x + 0.5} 80 11.5 180 20.5")
    (output / marker).touch()


def exercise_client(args, server, results, report):
    output = ROOT / "build/port-evidence" / f"{args.name}-client"
    scenario = {
        "both": "jade",
        "client-only": "jade-client-only",
        "server-only": "jade-absent",
        "absent": "jade-absent",
    }[args.profile]
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", scenario, "--output", str(output), "--server", f"127.0.0.1:{args.port}", "--timeout", "165"]
    summary_path = output / "summary.json"
    with (server.directory / "jade-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            server.command(f"item replace entity {PLAYER} hotbar.8 with enderio:yeta_wrench")
            aim(server, output, 2784, "server-bound")
            wait_signal(client, output, "bound")
            server.command(f'data modify block {BOUND} EntityStorage set value {{entity_type:"minecraft:phantom",entity_tag:{{}}}}')
            server.assert_state("live_soul_edit_changes_only_the_soul", bound_state("phantom"), results)
            (output / "server-changed").touch()
            wait_signal(client, output, "changed")
            aim(server, output, 2787, "server-unbound")
            wait_signal(client, output, "unbound")
            aim(server, output, 2790, "server-covered")
            if client.wait(timeout=65) != 0:
                raise AssertionError(f"Jade client failed; see {output}")
            summary = json.loads(summary_path.read_text())
            frame = summary["client"]["captures"]["bound-engine"]
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
    parser.add_argument("--profile", required=True, choices=("both", "client-only", "server-only", "absent"))
    parser.add_argument("--jade-jar", type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    has_server_jade = args.profile in ("both", "server-only")
    if has_server_jade and args.jade_jar is None:
        parser.error("the selected profile requires a server Jade jar")

    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    if has_server_jade:
        shutil.copyfile(args.jade_jar, directory / "mods/jade.jar")
    report = {"passed": False, "forge": FORGE, "profile": args.profile, "jarSha256": digest(directory / "mods/enderio.jar"),
              "assertions": [], "servers": []}
    try:
        def fresh(server, results):
            prepare(server, results)
            exercise_client(args, server, results, report)

        def reopened(server, results):
            server.assert_state("all_three_targets_keep_exact_state_after_restart",
                                f'{bound_state("phantom")} {other_targets()}', results)

        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", reopened)
        report["passed"] = True
    finally:
        path = directory / "jade-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Jade passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
