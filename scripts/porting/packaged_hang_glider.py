#!/usr/bin/env python3
"""Craft, equip and fly the Hang Glider in a clean Forge client over dedicated TCP."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage

PLAYER = "EnderIOQA"
GLIDER = 'Inventory[{Slot:102b,id:"enderio:glider",Count:1b}]'
ADVANCEMENT = "port_qa:glide"


def prepare_criterion(profile):
    pack = profile / WORLD / "datapacks/port-glider"
    advancement = pack / "data/port_qa/advancements/glide.json"
    advancement.parent.mkdir(parents=True)
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 15, "description": "Observe actual Hang Glider use"}}))
    advancement.write_text(json.dumps({"criteria": {"glide": {"trigger": "enderio:use_glider"}}}))


def runway(server, results):
    server.command("forceload add 3952 0 3983 351")
    server.assert_state("runway_end_is_loaded", "if loaded 3968 80 350", results, timeout=30)
    server.command("gamerule doMobSpawning false")
    server.command("gamerule naturalRegeneration false")
    server.command("fill 3954 79 1 3982 79 350 minecraft:stone")
    server.command("setblock 3968 80 4 minecraft:crafting_table")
    server.command("setworldspawn 3968 80 7")
    server.command("gamerule spawnRadius 0")
    server.command("time set noon")


def craft(server, output):
    server.command(f"give {PLAYER} enderio:dark_steel_ingot 8")
    server.command(f"give {PLAYER} minecraft:leather 6")
    server.command(f"recipe give {PLAYER} enderio:glider_wing")
    server.command(f"recipe give {PLAYER} enderio:glider")
    server.command(f"tp {PLAYER} 3968.5 80 6.5 180 8")
    (output / "server-craft").touch()


def exercise(args, server, results, report, reopened):
    phase = "verify" if reopened else "exercise"
    if not reopened:
        runway(server, results)
    output = ROOT / "build/port-evidence" / f"{args.name}-{phase}-client"
    summary = output / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "hang-glider", "--multiplayer-phase", phase, "--output", str(output),
               "--server", f"127.0.0.1:{args.port}", "--timeout", "160"]
    with (server.directory / f"client-{phase}.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            if not reopened:
                craft(server, output)
            else:
                server.command(f"effect give {PLAYER} minecraft:instant_health 1 3 true")
            wait_signal(client, output, "ready")
            server.assert_state(f"{phase}_chest_glider_synced_to_server", f"if data entity {PLAYER} {GLIDER}", results)
            if not reopened:
                server.assert_state("crafting_preserves_exact_material_balance",
                                    f'unless data entity {PLAYER} Inventory[{{id:"enderio:glider_wing"}}] '
                                    f'unless data entity {PLAYER} Inventory[{{id:"enderio:dark_steel_ingot"}}] '
                                    f'unless data entity {PLAYER} Inventory[{{id:"minecraft:leather"}}]', results)
            server.command(f"advancement revoke {PLAYER} only {ADVANCEMENT}")
            server.command(f"tp {PLAYER} 3968.5 97 8.5 0 25")
            wait_signal(client, output, "landed")
            server.assert_state(f"{phase}_native_landing_is_forward_and_undamaged",
                                f'if entity @a[name={PLAYER},x=3966,y=80,z=14,dx=5,dy=1,dz=335,nbt={{Health:20.0f}}]', results)
            server.assert_state(f"{phase}_server_recognizes_glider_use",
                                f"if entity @a[name={PLAYER},advancements={{{ADVANCEMENT}=true}}]", results)
            if not reopened:
                server.command(f"advancement revoke {PLAYER} only {ADVANCEMENT}")
                (output / "server-control").touch()
                wait_signal(client, output, "sneak")
                server.command(f"tp {PLAYER} 3968.5 97 8.5 0 25")
                wait_signal(client, output, "control-landed")
                server.assert_state("sneaking_fall_does_not_award_glider_criterion",
                                    f"if entity @a[name={PLAYER},x=3968,y=80,z=8,dx=0,dy=1,dz=0,"
                                    f"advancements={{{ADVANCEMENT}=false}}] unless data entity {PLAYER} {{Health:20.0f}}", results)
            (output / "server-verified").touch()
            if client.wait(timeout=35) != 0:
                raise AssertionError(f"Hang Glider client failed, see {summary}")
            capture = json.loads(summary.read_text())["client"]["captures"]["flight"]
            if digest(Path(capture["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Client and server Ender IO jars differ")
        finally:
            if client.poll() is None and (output / "result.json").exists():
                client.wait(timeout=25)
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
    args = parser.parse_args()

    directory = ROOT / "build/port-runtime" / args.name
    directory.mkdir()
    profile = directory / "server"
    report = {"jarSha256": digest(args.jar), "assertions": [], "servers": [], "clients": {}}
    try:
        prepare_profile(args, profile, args.jar)
        prepare_criterion(profile)
        for name, reopened in (("fresh", False), ("reopened", True)):
            run_stage(args, profile, report, name,
                      lambda server, results: exercise(args, server, results, report, reopened))
    finally:
        (directory / "hang-glider-results.json").write_text(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
