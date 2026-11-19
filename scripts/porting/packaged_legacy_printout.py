#!/usr/bin/env python3
"""Exercise historical Location Printouts in a copied Forge 6.2 world."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_coordinate_printout import ITEM, NETHER, PLAYER
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage

BARREL = "3177 80 8"
OLD = ('{Level:"minecraft:overworld",BlockPos:{X:-3161,Y:43,Z:-12}}',
       '{Level:"example:lost_dimension",BlockPos:{X:12000,Y:-32,Z:-542}}',
       '{Level:"minecraft:overworld",BlockPos:{X:7,Y:46}}')
NEW = ('{Level:"minecraft:overworld",Pos:[I;-3161,43,-12]}',
       '{Level:"example:lost_dimension",Pos:[I;12000,-32,-542]}')
NORTH = json.dumps({"color": "aqua", "text": "North Survey"}, separators=(",", ":"))
RENAMED = json.dumps({"color": "aqua", "text": "Nether Survey"}, separators=(",", ":"))
KEY = '"enderio:coordinate_selection"'
DATA = '"enderio:data"'
ENTRY = f'Inventory[{{id:"{ITEM}",Count:1b,tag:{{PortSerial:179}}}}]'


def old_tag(slot):
    return f"{{PortSerial:{179 + slot},CoordinateSelection:{OLD[slot]},display:{{Name:'{NORTH}'}}}}"


def barrel_slot(slot, tag):
    return f'Items[{{Slot:{slot}b,id:"{ITEM}",Count:1b,tag:{tag}}}]'


def write_historical_world(server, results):
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    server.command("forceload add 3168 0 3183 15")
    server.assert_state("historical_fixture_chunk_loads", f"if loaded {BARREL}", results)
    server.command("fill 3174 79 5 3180 79 14 minecraft:stone")
    server.command("setworldspawn 3177 80 12")
    server.command(f"setblock {BARREL} minecraft:barrel")
    for slot in range(len(OLD)):
        server.command(f"item replace block {BARREL} container.{slot} with {ITEM}{old_tag(slot)}")
    server.assert_state("released_forge6_stores_the_original_printout_format",
                        " ".join(f"if data block {BARREL} {barrel_slot(i, old_tag(i))}" for i in range(len(OLD))), results)


def verify_barrel(server, results, first_item_stored):
    slots = range(len(NEW)) if first_item_stored else (1,)
    valid = []
    for slot in slots:
        tag = f"{{PortSerial:{179 + slot},{DATA}:{{{KEY}:{NEW[slot]}}},display:{{Name:'{NORTH}'}}}}"
        valid.extend((f"if data block {BARREL} {barrel_slot(slot, tag)}",
                      f"unless data block {BARREL} Items[{{Slot:{slot}b}}].tag.CoordinateSelection"))
    untouched = (f"if data block {BARREL} {barrel_slot(2, old_tag(2))} "
                 f"unless data block {BARREL} Items[{{Slot:2b}}].tag.{DATA}.{KEY}")
    server.command(f"data get block {BARREL} Items")
    server.assert_state("saved_printouts_restore_exact_locations_and_preserve_incomplete_data",
                        " ".join((*valid, untouched)), results)


def verify_held(server, results, label, name):
    display = (f"if data entity {PLAYER} {ENTRY}.tag.display{{Name:'{name}'}}" if name else
               f"unless data entity {PLAYER} {ENTRY}.tag.display.Name")
    server.assert_state(label, f"if data entity {PLAYER} {ENTRY}.tag.{DATA}{{{KEY}:{NEW[0]}}} "
                        f"unless data entity {PLAYER} {ENTRY}.tag.CoordinateSelection "
                        f'unless data entity {PLAYER} Inventory[{{id:"minecraft:paper"}}] {display}', results)


def run_client(args, server, results, report, restarted):
    phase = "verify" if restarted else "exercise"
    evidence = ROOT / "build/port-evidence" / f"{args.name}-{phase}-client"
    summary = evidence / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "legacy-printout", "--multiplayer-phase", phase, "--output", str(evidence),
               "--server", f"127.0.0.1:{args.port}", "--timeout", "145"]
    with (server.directory / f"client-{phase}.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            if restarted:
                verify_held(server, results, "same_original_item_and_renamed_location_survive_restart", RENAMED)
            else:
                server.command(f"item replace entity {PLAYER} hotbar.0 from block {BARREL} container.0")
                server.command(f"item replace block {BARREL} container.0 with minecraft:air")
                verify_held(server, results, "player_receives_the_copied_worlds_original_printout", NORTH)
                for event in ("forceload add 3168 0 3183 15", "fill 3174 80 8 3180 83 14 minecraft:air",
                              "fill 3174 79 8 3180 79 14 minecraft:stone", f"tp {PLAYER} 3177.5 80 11.5 180 0"):
                    server.command(NETHER + event)
            (evidence / "server-edit").touch()
            if restarted:
                wait_signal(client, evidence, "clear-clicked")
                verify_held(server, results, "clearing_the_label_preserves_original_coordinates", None)
                (evidence / "server-reopen").touch()
                wait_signal(client, evidence, "cleared")
                verify_held(server, results, "reopening_keeps_the_original_item_unnamed", None)
            else:
                wait_signal(client, evidence, "renamed")
                verify_held(server, results, "native_rename_keeps_the_original_coordinates", RENAMED)
            (evidence / "server-done").touch()
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"Legacy printout client failed; see {evidence}")
            captures = json.loads(summary.read_text())["client"]["captures"]
            for capture in captures.values():
                if digest(Path(capture["loadedMods"]["enderio"]["file"])) != report["portJarSha256"]:
                    raise AssertionError("Legacy printout client and server Ender IO jars differ")
        finally:
            stop_client(client)
            report["clients"][phase] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--java", required=True)
    for key in ("forge-server", "legacy-jar", "jar"):
        parser.add_argument(f"--{key}", type=Path, required=True)
    parser.add_argument("--launch", type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    directory.mkdir()
    legacy, port = directory / "legacy", directory / "port"
    prepare_profile(args, legacy, args.legacy_jar)
    prepare_profile(args, port, args.jar)
    report = {"passed": False, "assertions": [], "servers": [], "clients": {},
              "legacyJarSha256": digest(legacy / "mods/enderio.jar"), "portJarSha256": digest(port / "mods/enderio.jar")}

    def converted(server, results):
        verify_barrel(server, results, True)
        if args.launch:
            run_client(args, server, results, report, restarted=False)

    def reopened(server, results):
        verify_barrel(server, results, not args.launch)
        if args.launch:
            run_client(args, server, results, report, restarted=True)

    try:
        run_stage(args, legacy, report, "legacy", write_historical_world)
        shutil.copytree(legacy / WORLD, port / WORLD)
        run_stage(args, port, report, "converted", converted)
        run_stage(args, port, report, "reopened", reopened)
        if any(stage["registryErrors"] for stage in report["servers"]):
            raise AssertionError("Printout conversion reported registry errors")
        report["passed"] = True
    finally:
        result = directory / "legacy-printout-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Forge 6 printout migration passed={report['passed']}, {result}", flush=True)


if __name__ == "__main__":
    main()
