#!/usr/bin/env python3
"""Observe a real Hang Glider flight from another packaged Forge client."""

import argparse
from contextlib import ExitStack
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_hang_glider import ADVANCEMENT, PLAYER, prepare_criterion, runway
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage


OBSERVER = "EnderIOObserver"
GLIDER = 'enderio:glider{PortGliderRemote:235}'
EQUIPPED = (f'if data entity {PLAYER} Inventory[{{Slot:102b,id:"enderio:glider",Count:1b,'
            'tag:{PortGliderRemote:235}}]')


def launch_client(args, server, resources, phase, role, launch, scenario):
    output = ROOT / "build/port-evidence" / f"{args.name}-{phase}-{role}"
    log = resources.enter_context((server.directory / f"{phase}-{role}.log").open("w"))
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(launch.resolve()),
               "--scenario", scenario, "--multiplayer-phase", "verify", "--server", f"127.0.0.1:{args.port}",
               "--output", str(output), "--timeout", "285"]
    client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
    resources.callback(stop_client, client)
    return client, output


def exercise(args, server, results, report, *, reopened):
    if not reopened:
        runway(server, results)
    phase = "reopened" if reopened else "fresh"
    with ExitStack() as resources:
        observer, observation = launch_client(args, server, resources, phase, "observer", args.observer, "hang-glider-observer")
        wearer, pilot = launch_client(args, server, resources, phase, "wearer", args.wearer, "hang-glider")
        try:
            wait_signal(observer, observation, "connected")
            wait_signal(wearer, pilot, "connected")
            if not reopened:
                server.command(f"item replace entity {PLAYER} armor.chest with {GLIDER}")
            wait_signal(wearer, pilot, "ready")
            server.assert_state(f"{phase}_distinct_survival_players_share_the_equipped_glider",
                                f"if entity @a[name={OBSERVER},gamemode=survival] "
                                f"if entity @a[name={PLAYER},gamemode=survival] {EQUIPPED}", results)
            server.command(f"tp {PLAYER} 3968.5 80 8.5 0 0")
            server.command(f"tp {OBSERVER} 3964.5 80 8.5 -90 0")
            (observation / "server-ground").touch()
            wait_signal(observer, observation, "grounded")

            server.command(f"advancement revoke {PLAYER} only {ADVANCEMENT}")
            server.command(f"tp {PLAYER} 3968.5 97 8.5 0 25")
            server.assert_state(f"{phase}_server_recognizes_flight_from_the_wearers_movement",
                                f"if entity @a[name={PLAYER},advancements={{{ADVANCEMENT}=true}}]", results)
            (observation / "server-gliding").touch()
            wait_signal(wearer, pilot, "landed")
            server.assert_state(f"{phase}_wearer_lands_forward_with_full_health_and_equipment",
                                f'if entity @a[name={PLAYER},x=3966,y=80,z=14,dx=5,dy=1,dz=335,nbt={{Health:20.0f}}] {EQUIPPED}', results)
            server.command(f"execute at {PLAYER} run tp {OBSERVER} ~-4 ~ ~ -90 0")
            (observation / "server-landed").touch()
            wait_signal(observer, observation, "verified")
            (pilot / "server-verified").touch()
            observer_exit = observer.wait(timeout=35)
            wearer_exit = wearer.wait(timeout=35)
        finally:
            for role, output in (("observer", observation), ("wearer", pilot)):
                summary = output / "summary.json"
                if summary.exists():
                    report["clients"][f"{phase}-{role}"] = json.loads(summary.read_text())
    if observer_exit or wearer_exit:
        raise AssertionError(f"Remote Hang Glider check failed. See {observation} and {pilot}")
    for role, frame_name in (("observer", "deployed"), ("wearer", "flight")):
        capture = report["clients"][f"{phase}-{role}"]["client"]["captures"][frame_name]
        path = Path(capture["loadedMods"]["enderio"]["file"])
        matches = digest(path) == report["jarSha256"]
        results.append({"assertion": f"{phase}_{role}_loads_the_server_product_jar", "passed": matches})
        if not matches:
            raise AssertionError(f"Ender IO jar mismatch on {role}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for path in ("java", "forge-server", "jar", "wearer", "observer"):
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
        path = directory / "remote-hang-glider-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Remote Hang Glider passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
