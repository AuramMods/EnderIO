#!/usr/bin/env python3
"""Exercise a native AE2 facade on a powered, migrated Ender IO ME conduit."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_legacy_migration import assert_probe, storage_operation
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage


PLAYER = "EnderIOQA"
TARGET = "1087 80 4"
PAINT = 'id:"ae2:facade",tag:{item:"minecraft:diamond_block"}'
INSTALLED = f'if data block {TARGET} {{Conduits:["enderio:me"],FacadeProvider:{{{PAINT},Count:1}}}}'
BARE = f'if data block {TARGET} {{Conduits:["enderio:me"]}} unless data block {TARGET} FacadeProvider'
DROP = f'@e[type=item,x=1087.5,y=80.5,z=4.5,distance=..2,nbt={{Item:{{{PAINT},Count:1b}}}}]'


def check_route(server, results, step):
    assert_probe(server, f"{step}_remote_cell_keeps_its_exact_amount", "port_storage_native check_route_items 4 21",
                 "Native route item count verified", results)


def setup(server, results, phase):
    server.command("forceload add 1072 0 1103 15")
    server.assert_state(f"{phase}_both_route_chunks_are_loaded", f"if loaded {TARGET} if loaded 1089 80 4", results)
    if phase == "exercise":
        server.command("gamerule doDaylightCycle false")
        server.command("gamerule spawnRadius 0")
        server.command("time set noon")
        server.command("setworldspawn 1087 80 9")
        server.command("fill 1083 79 2 1091 79 10 minecraft:stone_bricks")
        server.command("fill 1083 80 2 1091 82 2 minecraft:stone_bricks")
    check_route(server, results, phase)


def exercise(args, server, results, report, phase):
    setup(server, results, phase)
    output = ROOT / "build/port-evidence" / f"{args.name}-{phase}"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
               "--launch", str(args.launch.resolve()), "--scenario", "ae2-facade", "--output", str(output),
               "--server", f"127.0.0.1:{args.port}", "--multiplayer-phase", phase, "--timeout", "175"]
    with (server.directory / f"ae2-facade-client-{phase}.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            if phase == "exercise":
                server.command(f"clear {PLAYER}")
                storage_operation(server, "native_facade_sample", "give_facade", "Native AE2 diamond facade created", results)
                server.command(f"item replace entity {PLAYER} hotbar.4 with minecraft:diamond_pickaxe")
                server.command(f"item replace entity {PLAYER} hotbar.8 with enderio:yeta_wrench")
                camera(server)
                (output / "server-place").touch()
                wait_signal(client, output, "placed")
                server.assert_state("native_ae2_item_install_consumes_the_held_stack",
                                    f"{INSTALLED} unless data entity {PLAYER} Inventory[{{Slot:0b}}]", results)
                check_route(server, results, "covered")
                (output / "server-mine").touch()
                wait_signal(client, output, "mined")
                server.assert_state("ordinary_mining_leaves_the_me_conduit_and_drops_its_ae2_item", f"{BARE} if entity {DROP}", results)
                check_route(server, results, "exposed")
                server.command(f"tp {PLAYER} 1087.5 80 4.94 180 35")
                wait_signal(client, output, "picked-up")
                server.assert_state("survival_pickup_recovers_the_exact_ae2_stack",
                                    f'if data entity {PLAYER} Inventory[{{{PAINT},Count:1b}}] unless entity {DROP}', results)
                camera(server)
                (output / "server-replant").touch()
            else:
                server.assert_state("dedicated_restart_preserves_the_ae2_provider", INSTALLED, results)
                camera(server)
                (output / "server-reopen").touch()
            wait_signal(client, output, "rendered")
            server.assert_state(f"{phase}_rendered_bundle_retains_the_native_ae2_item", INSTALLED, results)
            check_route(server, results, f"{phase}_final")
            if client.wait(timeout=25) != 0:
                raise AssertionError(f"AE2 facade client failed during {phase}, see {output}")
            summary = json.loads((output / "summary.json").read_text())
            frame = summary["client"]["captures"]["ae2-facade-reloaded" if phase == "exercise" else "ae2-facade-reopened"]
            for mod in ("enderio", "ae2", "guideme", "refinedstorage"):
                path = Path(frame["loadedMods"][mod]["file"])
                if digest(path) != report["jarHashes"][path.name]:
                    raise AssertionError(f"Client and server loaded different {mod} artifacts")
        finally:
            stop_client(client)
            summary = output / "summary.json"
            report["clients"][phase] = json.loads(summary.read_text()) if summary.exists() else None


def camera(server):
    server.command(f"tp {PLAYER} 1087.5 80 6.5 180 35")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for path in ("java", "forge-server", "jar", "launch", "world", "probe"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    parser.add_argument("--mod", action="append", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    shutil.copytree(args.world, directory / WORLD)
    for mod in [*args.mod, args.probe]:
        shutil.copyfile(mod, directory / "mods" / mod.name)
    report = {"passed": False, "assertions": [], "servers": [], "clients": {},
              "jarHashes": {mod.name: digest(mod) for mod in sorted((directory / "mods").glob("*.jar"))}}
    try:
        for phase in ("exercise", "verify"):
            run_stage(args, directory, report, phase, lambda server, results: exercise(args, server, results, report, phase))
        report["passed"] = True
    finally:
        (directory / "ae2-facade-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
