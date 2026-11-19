#!/usr/bin/env python3
"""Verify native survival crafting and editable Location Printouts across dimensions."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage

PLAYER = "EnderIOQA"
ITEM = "enderio:location_printout"
SELECTION = '{"enderio:data":{"enderio:coordinate_selection":{Level:"minecraft:overworld",Pos:[I;3161,81,0]}}}'
PRINT = f'Inventory[{{id:"{ITEM}",Count:1b,tag:{SELECTION}}}]'
PAPER = 'Inventory[{Slot:-106b,id:"minecraft:paper",Count:1b}]'
NETHER = "execute in minecraft:the_nether run "


def setup(server):
    for command in ("gamerule doMobSpawning false", "gamerule spawnRadius 0", "time set noon",
                    "forceload add 3152 0 3167 15", "fill 3158 79 0 3164 79 14 minecraft:stone",
                    "setblock 3161 80 9 minecraft:crafting_table", "setblock 3161 81 0 minecraft:diamond_block",
                    "setworldspawn 3161 80 13"):
        server.command(command)


def assert_printout(server, results, label, name):
    entry = f'Inventory[{{id:"{ITEM}"}}]'
    if name:
        serialized = json.dumps({"color": "aqua", "text": name}, separators=(",", ":"))
        named = f"if data entity {PLAYER} {entry}.tag.display{{Name:'{serialized}'}}"
    else:
        named = f"unless data entity {PLAYER} {entry}.tag.display.Name"
    server.assert_state(label, f"if data entity {PLAYER} {PRINT} if data entity {PLAYER} {PAPER} {named}", results)


def signals(args, server, results, report, restarted):
    phase = "verify" if restarted else "exercise"
    output = ROOT / "build/port-evidence" / f"{args.name}-{phase}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "coordinate-printout", "--multiplayer-phase", phase,
               "--output", str(output), "--server", f"127.0.0.1:{args.port}", "--timeout", "145"]
    with (server.directory / f"client-{phase}.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        summary = output / "summary.json"
        try:
            wait_signal(client, output, "connected")
            if restarted:
                assert_printout(server, results, "named_location_and_remaining_paper_survive_restart", "Nether Survey")
                (output / "server-edit").touch()
                wait_signal(client, output, "clear-clicked")
                assert_printout(server, results, "pressing_ok_clears_only_the_custom_name", None)
                (output / "server-reopen").touch()
                wait_signal(client, output, "cleared")
                assert_printout(server, results, "clearing_and_reopening_keeps_exact_location_and_paper", None)
            else:
                for item, count in (("enderio:conductive_alloy_ingot", 4), ("minecraft:compass", 1), ("minecraft:ender_pearl", 1)):
                    server.command(f"give {PLAYER} {item} {count}")
                server.assert_state("survival_ingredient_advancement_unlocks_the_recipe",
                                    f"if entity @a[name={PLAYER},advancements={{enderio:recipes/tools/coordinate_selector=true}}]",
                                    results)
                server.command(f"tp {PLAYER} 3161.5 80 11.5 180 0")
                (output / "server-craft").touch()
                wait_signal(client, output, "no-paper")
                server.assert_state("survival_craft_consumes_exact_inputs_and_no_printout_without_paper",
                                    f'if data entity {PLAYER} Inventory[{{id:"enderio:coordinate_selector",Count:1b}}] '
                                    f'unless data entity {PLAYER} Inventory[{{id:"{ITEM}"}}] '
                                    f'unless data entity {PLAYER} Inventory[{{id:"enderio:conductive_alloy_ingot"}}] '
                                    f'unless data entity {PLAYER} Inventory[{{id:"minecraft:compass"}}] '
                                    f'unless data entity {PLAYER} Inventory[{{id:"minecraft:ender_pearl"}}]', results)
                server.command(f"item replace entity {PLAYER} weapon.offhand with minecraft:paper 2")
                wait_signal(client, output, "created")
                assert_printout(server, results, "one_offhand_sheet_prints_the_exact_overworld_location", "North Survey")
                for command in ("forceload add 3152 0 3167 15", "fill 3158 80 8 3164 83 14 minecraft:air",
                                "fill 3158 79 8 3164 79 14 minecraft:stone", f"tp {PLAYER} 3161.5 80 11.5 180 0"):
                    server.command(NETHER + command)
                (output / "server-edit").touch()
                wait_signal(client, output, "renamed")
                assert_printout(server, results, "renaming_in_nether_keeps_overworld_location_and_paper", "Nether Survey")
            (output / "server-done").touch()
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"Location Printout client failed; see {output}")
            captures = json.loads(summary.read_text())["client"]["captures"]
            for capture in captures.values():
                if digest(Path(capture["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                    raise AssertionError("Location Printout client and server Ender IO jars differ")
        finally:
            stop_client(client)
            report["clients"][phase] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    for key in ("forge-server", "jar", "launch"):
        parser.add_argument(f"--{key}", type=Path, required=True)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")

    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "assertions": [], "clients": {}, "servers": [],
              "jarSha256": digest(directory / "mods/enderio.jar")}

    def fresh(server, results):
        setup(server)
        signals(args, server, results, report, False)

    try:
        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", lambda server, results: signals(args, server, results, report, True))
        report["passed"] = True
    finally:
        result = directory / "coordinate-printout-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Location Printout passed={report['passed']}, {len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
