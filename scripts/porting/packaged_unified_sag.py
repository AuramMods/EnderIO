#!/usr/bin/env python3
"""Compare live JEI output and transfers with a packaged, unified SAG Mill."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys
import time

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_registry_audit import snapshot
from packaged_sag_progression import charge, mill, output
from packaged_sag_unification import EIO_TIN, MEKANISM_TIN, RELOAD_MARKER, set_priority, write_tin_tag
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage
from packaged_starter_progression import PLAYER, hopper_item

MILL = 3131
INGOT = "mekanism:ingot_tin"
SHARED = {"ae2", "almostunified", "architectury", "computercraft", "curios", "ftblibrary", "ftbultimine",
          "guideme", "inventorysorter", "jade", "jei", "laserio", "mekanism", "patchouli", "refinedstorage"}


def finished(batch):
    pos = mill(MILL)
    receiver = output(MILL)
    conditions = (f'{hopper_item(receiver, MEKANISM_TIN, 1)} if data block {pos} {{EnergyStored:137}} '
                  f'unless data block {pos} Task unless data block {pos} Items.Items[{{Slot:0}}]')
    return conditions if batch == 0 else (
        f'{conditions} if data block {receiver} Items[{{Slot:1b,id:"{EIO_TIN}",Count:1b}}]')


def exercise_batch(server, results, batch):
    expected_inventory = (f'if data entity {PLAYER} Inventory[{{id:"{INGOT}",Count:1b,tag:{{PortStack:177}}}}]'
                          if batch == 0 else f'unless data entity {PLAYER} Inventory[{{id:"{INGOT}"}}]')
    server.assert_state(f"native_jei_transfer_moves_one_tagged_tin_batch_{batch}",
                        f'if data block {mill(MILL)} Items.Items[{{Slot:0,id:"{INGOT}",Count:1,tag:{{PortStack:177}}}}] '
                        f'{expected_inventory}', results)
    charge(server, MILL, 2537)
    server.assert_state(f"unified_dust_matches_the_applicable_server_tag_batch_{batch}", finished(batch), results)


def restrict_tin(server):
    write_tin_tag(server.directory, True)
    server.command("reload")
    deadline = time.monotonic() + 50
    while RELOAD_MARKER not in server.command("function port_sag:tin_reloaded"):
        if time.monotonic() >= deadline:
            raise RuntimeError(f"Tin tag override did not load; see {server.log_path}")
        time.sleep(0.2)


def exercise(args, server, results, report):
    snapshot(server, results)
    observed = set(json.loads((server.directory / "registry-snapshot.json").read_text())["mods"])
    missing = sorted(SHARED - observed)
    report["serverMods"] = sorted(observed)
    results.append({"assertion": "all_fifteen_shared_optional_mods_load", "passed": not missing, "missing": missing})
    if missing:
        raise AssertionError(f"Required integration mods missing from the live server: {missing}")
    for command in ("gamerule doMobSpawning false", "gamerule spawnRadius 0", "gamerule doDaylightCycle false",
                    "time set noon", "forceload add 3120 0 3151 15", "fill 3127 79 5 3136 79 15 minecraft:stone_bricks",
                    "setworldspawn 3131 80 12"):
        server.command(command)
    server.command(f'setblock {mill(MILL)} enderio:sag_mill[facing=south]{{IOConfig:{{Modes:{{down:"push"}}}},'
                   'Items:{Items:[{Slot:6,id:"enderio:basic_capacitor",Count:1}]}}')
    server.command(f"setblock {output(MILL)} minecraft:hopper[facing=down]")

    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "sag-unified", "--server", f"127.0.0.1:{args.port}",
               "--output", str(evidence), "--timeout", "205"]
    with (server.directory / "unified-sag-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            server.command(f"item replace entity {PLAYER} hotbar.1 with {INGOT}{{PortStack:177}} 2")
            server.command(f"tp {PLAYER} {MILL + 0.5} 80 11.5 180 18")
            (evidence / "server-batch-0").touch()
            wait_signal(client, evidence, "transferred-0")
            exercise_batch(server, results, 0)
            charge(server, MILL, 0)
            restrict_tin(server)
            (evidence / "server-batch-1").touch()
            wait_signal(client, evidence, "transferred-1")
            exercise_batch(server, results, 1)
            (evidence / "server-done").touch()
            if client.wait(timeout=45) != 0:
                raise AssertionError(f"Unified SAG Mill client failed; see {evidence}")
            loaded = json.loads((evidence / "summary.json").read_text())["client"]["captures"]["integrated-world"]["loadedMods"]
            for mod in SHARED | {"enderio"}:
                file = Path(loaded[mod]["file"])
                if digest(file) != report["jarSha256"][file.name]:
                    raise AssertionError(f"Client and server loaded different {mod} jars")
        finally:
            stop_client(client)
            summary = evidence / "summary.json"
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", default=26131, type=int)
    parser.add_argument("--mod", action="append", default=[], type=Path, help="Install an optional mod on the server")
    parser.add_argument("--java", required=True)
    for key in ("forge-server", "jar", "launch", "probe"):
        parser.add_argument(f"--{key}", type=Path, required=True)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")

    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    for mod in (args.probe, *args.mod):
        shutil.copyfile(mod, directory / "mods" / mod.name)
    set_priority(directory, ["mekanism", "enderio"])
    write_tin_tag(directory, False)
    report = {"passed": False, "assertions": [], "servers": [],
              "jarSha256": {mod.name: digest(mod) for mod in (directory / "mods").glob("*.jar")}}
    try:
        run_stage(args, directory, report, "fresh", lambda server, results: exercise(args, server, results, report))
        run_stage(args, directory, report, "reopened", lambda server, results:
                  server.assert_state("both_exact_tag_outputs_survive_restart", finished(1), results))
        report["passed"] = True
    finally:
        result = directory / "unified-sag-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Unified SAG Mill passed={report['passed']}, {result}", flush=True)


if __name__ == "__main__":
    main()
