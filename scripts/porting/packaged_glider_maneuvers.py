#!/usr/bin/env python3
"""Verify native Hang Glider sprint input and water entry on packaged Forge."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_hang_glider import ADVANCEMENT, PLAYER, prepare_criterion, runway
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage


GLIDER = (f'if data entity {PLAYER} Inventory[{{Slot:102b,id:"enderio:glider",Count:1b,'
          'tag:{PortGliderManeuvers:237}}]')


def pool(server, results):
    runway(server, results)
    server.command("fill 3964 91 6 3972 93 193 minecraft:stone")
    server.command("fill 3965 92 7 3971 93 192 minecraft:water")
    server.assert_state("runway_has_a_contained_pool_below_the_flight_path",
                        "if block 3968 93 8 minecraft:water if block 3968 93 190 minecraft:water "
                        "if block 3968 91 8 minecraft:stone if block 3972 93 8 minecraft:stone", results)


def exercise(args, server, results, report, *, reopened):
    phase = "reopened" if reopened else "fresh"
    if not reopened:
        pool(server, results)
    output = ROOT / "build/port-evidence" / f"{args.name}-{phase}-client"
    summary = output / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "hang-glider-maneuvers", "--server", f"127.0.0.1:{args.port}",
               "--output", str(output), "--timeout", "180"]
    with (server.directory / f"{phase}-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            if not reopened:
                server.command(f"item replace entity {PLAYER} armor.chest with enderio:glider{{PortGliderManeuvers:237}}")
            wait_signal(client, output, "ready")
            server.assert_state(f"{phase}_original_tagged_glider_is_equipped_in_survival",
                                f"if entity @a[name={PLAYER},gamemode=survival] {GLIDER}", results)
            server.command(f"advancement revoke {PLAYER} only {ADVANCEMENT}")
            server.command(f"tp {PLAYER} 3968.5 105 8.5 0 20")
            wait_signal(client, output, "water")
            server.assert_state(f"{phase}_server_accepts_glide_into_water_with_full_health_and_equipment",
                                f"at {PLAYER} if block ~ ~ ~ minecraft:water {GLIDER} "
                                f'if entity @a[name={PLAYER},x=3964,y=91,z=10,dx=8,dy=4,dz=180,'
                                f'advancements={{{ADVANCEMENT}=true}},nbt={{Health:20.0f}}]', results)
            (output / "server-verified").touch()
            if client.wait(timeout=30):
                raise AssertionError(f"Glider maneuvers failed. See {output}")
            capture = json.loads(summary.read_text())["client"]["captures"]["water"]
            match = digest(Path(capture["loadedMods"]["enderio"]["file"])) == report["jarSha256"]
            results.append({"assertion": f"{phase}_client_uses_the_server_product_jar", "passed": match})
            if not match:
                raise AssertionError("Glider client and server used different jars")
        finally:
            stop_client(client)
            if summary.exists():
                report["clients"][phase] = json.loads(summary.read_text())


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
    prepare_criterion(directory)
    report = {"passed": False, "assertions": [], "servers": [], "clients": {}, "jarSha256": digest(directory / "mods/enderio.jar")}
    try:
        for phase in ("fresh", "reopened"):
            run_stage(args, directory, report, phase, lambda server, results: exercise(
                args, server, results, report, reopened=phase == "reopened"), startup_timeout=300)
        report["passed"] = True
    finally:
        path = directory / "glider-maneuvers-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Glider maneuvers passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
