#!/usr/bin/env python3
"""Craft, read and reopen the stock LaserIO guide using its pinned Forge dependencies."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_solar_charging import check
from packaged_spawners import PLAYER, WORLD, prepare_profile, run_stage
from saved_world import read_data_file


TABLE = "4720 80 8"
CHIP = "laserio:logic_chip"
ITEM = "patchouli:guide_book"
BOOK = "laserio:laseriobook"


def guide_in_inventory():
    return (f'if data entity {PLAYER} Inventory[{{id:"{ITEM}",Count:1b,tag:{{"patchouli:book":"{BOOK}"}}}}] '
            f'unless data entity {PLAYER} Inventory[{{id:"{CHIP}"}}] unless data entity {PLAYER} Inventory[{{id:"minecraft:book"}}]')


def setup(server):
    for command in ("gamerule doMobSpawning false", "gamerule spawnRadius 0", "gamerule doDaylightCycle false",
                    "time set noon", "forceload add 4720 0 4735 15", "fill 4717 79 4 4727 79 14 minecraft:stone",
                    "setworldspawn 4720 80 10", f"setblock {TABLE} minecraft:crafting_table",
                    "scoreboard objectives add port_guide dummy"):
        server.command(command)


def craft(server, results, client, evidence):
    wait_signal(client, evidence, "connected")
    server.command(f"item replace entity {PLAYER} hotbar.0 with {CHIP}")
    server.command(f"item replace entity {PLAYER} hotbar.1 with minecraft:book")
    server.command(f"tp {PLAYER} 4720.5 80 10.5 180 20")
    (evidence / "server-table").touch()
    wait_signal(client, evidence, "crafted")
    server.command(f"execute store result score #guides port_guide run clear {PLAYER} {ITEM} 0")
    server.assert_state("native_guide_craft_consumes_the_book_and_chip_and_keeps_one_named_guide",
                        f"if score #guides port_guide matches 1 {guide_in_inventory()}", results)


def saved_guide(server, results, label):
    (file,) = (server.directory / WORLD / "playerdata").glob("*.dat")
    inventory = read_data_file(file)["Inventory"]
    state = [(stack["id"], stack["Count"], stack.get("tag", {})) for stack in inventory]
    check(label, [(ITEM, 1, {"patchouli:book": BOOK})], state, results)


def exercise(args, server, results, report, stage):
    evidence = ROOT / "build/port-evidence" / f"{args.name}-{stage}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", f"laserio-guide-{stage}", "--server", f"127.0.0.1:{args.port}", "--output", str(evidence),
               "--timeout", "160"]
    summary = evidence / "summary.json"
    with (server.directory / f"laserio-guide-{stage}-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            if stage == "craft":
                craft(server, results, client, evidence)
            else:
                wait_signal(client, evidence, "connected")
                server.assert_state("reconnecting_survival_player_receives_the_saved_guide", guide_in_inventory(), results)
            (evidence / "server-guide").touch()
            wait_signal(client, evidence, "read")
            server.assert_state(f"{stage}_reading_leaves_the_original_guide_in_survival_inventory", guide_in_inventory(), results)
            (evidence / "server-finish").touch()
            if client.wait(timeout=35) != 0:
                raise AssertionError(f"LaserIO guide {stage} client failed; see {evidence}")
            loaded = json.loads(summary.read_text())["client"]["captures"]["laserio-guide-basic-filter"]["loadedMods"]
            check(f"{stage}_client_and_server_use_the_same_three_artifacts", report["jars"],
                  {name: digest(Path(loaded[name]["file"])) for name in report["jars"]}, results)
        finally:
            stop_client(client)
            report["clients"][stage] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for key in ("java", "forge-server", "jar", "laserio", "patchouli", "launch"):
        parser.add_argument(f"--{key}", type=Path, required=True)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    for name in ("laserio", "patchouli"):
        shutil.copyfile(getattr(args, name), directory / "mods" / f"{name}.jar")
    report = {"passed": False, "forge": FORGE, "assertions": [], "servers": [], "clients": {},
              "jars": {name: digest(directory / "mods" / f"{name}.jar") for name in ("enderio", "laserio", "patchouli")}}

    def fresh(server, results):
        setup(server)
        exercise(args, server, results, report, "craft")

    def reopened(server, results):
        saved_guide(server, results, "restart_recovers_the_exact_tagged_guide_from_saved_player_data")
        exercise(args, server, results, report, "reopen")
        saved_guide(server, results, "closing_the_reopened_guide_preserves_the_exact_saved_inventory")

    try:
        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", reopened)
        report["passed"] = True
    finally:
        result = directory / "laserio-guide-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"LaserIO guide passed={report['passed']}, {len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
