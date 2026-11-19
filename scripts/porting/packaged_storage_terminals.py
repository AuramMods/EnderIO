#!/usr/bin/env python3
"""Exercise both native storage screens against a copied, converted dedicated world."""

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
ROUTES = (("ae", 4, 1), ("rs", 13, 2))


def check_route(server, results, name, route, count):
    assert_probe(server, name, f"port_storage_native check_route_items {route} {count}",
                 "Native route item count verified", results)


def setup(server, results):
    server.command("forceload add 1072 0 1103 15")
    server.assert_state("converted_storage_chunks_are_loaded", "if loaded 1085 80 4 if loaded 1089 80 13", results)
    server.command("gamerule doDaylightCycle false")
    server.command("time set noon")
    server.command("fill 1081 79 2 1090 79 15 minecraft:stone_bricks")
    server.command("fill 1082 80 3 1082 81 14 minecraft:bookshelf")
    server.command("gamerule spawnRadius 0")
    server.command("setworldspawn 1083 80 8")
    storage_operation(server, "native_ae2_terminal_fixture", "place_terminal", "Native AE2 terminal placed", results)


def exercise(args, server, results, report, phase):
    reopen = phase == "verify"
    if not reopen:
        setup(server, results)
    output = ROOT / "build/port-evidence" / f"{args.name}-{phase}"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
               "--launch", str(args.launch.resolve()), "--scenario", "storage-terminals", "--output", str(output),
               "--server", f"127.0.0.1:{args.port}", "--multiplayer-phase", phase, "--timeout", "175"]
    with (server.directory / f"storage-client-{phase}.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            if not reopen:
                server.command(f"clear {PLAYER}")
            for name, marker, destination in ROUTES:
                check_route(server, results, f"{name}_{phase}_remote_storage_before_open", marker, 21 if reopen else 37)
                if not reopen:
                    server.command(f"item replace entity {PLAYER} hotbar.0 with minecraft:iron_ingot{{PortRoute:{marker}}} 5")
                server.command(f"tp {PLAYER} 1083.5 80 {marker + 0.5} -90 18")
                if name == "rs":
                    if not reopen:
                        server.command(f"item replace entity {PLAYER} hotbar.8 with refinedstorage:grid")
                        (output / "server-rs-place").touch()
                        wait_signal(client, output, "rs-placed")
                        server.assert_state("survival_grid_placement_consumes_the_item",
                                            f'if block 1085 80 13 refinedstorage:grid unless data entity {PLAYER} '
                                            'Inventory[{id:"refinedstorage:grid"}]', results)
                    assert_probe(server, f"{phase}_rs_grid_joins_its_controller", "port_storage_native check_grid",
                                 "Native RS grid connects to its controller", results)
                (output / f"server-{name}").touch()
                if not reopen:
                    wait_signal(client, output, f"{name}-deposited")
                    check_route(server, results, f"{name}_native_deposit_reaches_the_remote_media", marker, 42)
                    (output / f"server-{name}-deposited").touch()
                wait_signal(client, output, f"{name}-done")
                check_route(server, results, f"{name}_{phase}_exact_remote_remainder", marker, 21)
                server.assert_state(f"{name}_{phase}_exact_survival_player_inventory",
                                    f'if data entity {PLAYER} Inventory[{{Slot:{destination}b,id:"minecraft:iron_ingot",'
                                    f'Count:21b,tag:{{PortRoute:{marker}}}}}] unless data entity {PLAYER} Inventory[{{Slot:0b}}]',
                                    results)
            check_route(server, results, f"{phase}_dense_ae_cell_is_unchanged", 7, 37)
            check_route(server, results, f"{phase}_mixed_ae_cell_is_unchanged", 10, 37)
            (output / "server-verified").touch()
            if client.wait(timeout=25) != 0:
                raise AssertionError(f"Storage client failed during {phase}, see {output}")
            summary = json.loads((output / "summary.json").read_text())
            mods = summary["client"]["captures"]["ae-reopened" if reopen else "ae-initial"]["loadedMods"]
            for mod in ("enderio", "ae2", "guideme", "refinedstorage"):
                path = Path(mods[mod]["file"])
                if digest(path) != report["serverJarHashes"][path.name]:
                    raise AssertionError(f"Client and server loaded different {mod} artifacts")
        finally:
            stop_client(client)
            summary = output / "summary.json"
            report["clients"][phase] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--launch", required=True, type=Path)
    parser.add_argument("--world", required=True, type=Path)
    parser.add_argument("--probe", required=True, type=Path)
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
              "serverJarHashes": {mod.name: digest(mod) for mod in sorted((directory / "mods").glob("*.jar"))}}
    try:
        for phase in ("exercise", "verify"):
            run_stage(args, directory, report, phase, lambda server, results: exercise(args, server, results, report, phase))
        report["passed"] = True
    finally:
        (directory / "storage-terminal-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
