#!/usr/bin/env python3
"""Transfer and grind stock ore on packaged Forge and reload JEI's grinding-ball data."""

import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_sag_progression import charge, data_read, mill, output
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage
from packaged_starter_progression import PLAYER, hopper_item


MILL_X = 3131
ORE = "minecraft:deepslate_iron_ore"
RAW = "minecraft:raw_iron"
BONUS = "minecraft:cobblestone"
RECIPE = "enderio:sag_milling/iron_ore"


def finished(report):
    conditions = (f'if data block {mill(MILL_X)} {{EnergyStored:0}} '
                  f'unless data block {mill(MILL_X)} Task unless data block {mill(MILL_X)} Items.Items[{{Slot:0}}] '
                  f'unless data block {mill(MILL_X)} GrindingBal {hopper_item(output(MILL_X), RAW, report["rawIron"])}')
    if report["cobblestone"]:
        return f'{conditions} if data block {output(MILL_X)} Items[{{id:"{BONUS}",Count:1b}}]'
    return f'{conditions} unless data block {output(MILL_X)} Items[{{id:"{BONUS}"}}]'


def process_ore(server, report):
    results = report["assertions"]
    pos = mill(MILL_X)
    server.assert_state("exactly_one_tagged_ore_moved_and_both_flint_stayed_in_the_player_inventory",
                        f'if data block {pos} Items.Items[{{Slot:0,id:"{ORE}",Count:1,tag:{{PortSAG:158}}}}] '
                        f'unless data block {pos} Items.Items[{{Slot:5}}] '
                        f'if data entity {PLAYER} {{Inventory:[{{id:"{ORE}",Count:1b,tag:{{PortSAG:158}}}},'
                        '{id:"minecraft:flint",Count:2b,tag:{PortSAG:158}}]}', results)
    charge(server, MILL_X, 600)
    server.assert_state("the_real_stock_recipe_spends_600_of_2400_fe_with_no_active_ball",
                        f'if data block {pos} {{EnergyStored:0,Task:{{RecipeId:"{RECIPE}",ProgressMade:600,'
                        f'ProgressRequired:2400,HasDeterminedOutputs:1b}}}} unless data block {pos} GrindingBal', results)
    raw = int(data_read(server, pos, f'Task.Outputs[{{Item:{{id:"{RAW}"}}}}].Item.Count'))
    saved = data_read(server, pos, "Task.Outputs")
    if raw not in (1, 2):
        raise AssertionError(f"Base iron and its optional duplicate produced {raw} raw iron")
    report.update(rawIron=raw, cobblestone=int(re.search(r'id: "minecraft:cobblestone"', saved) is not None), savedRoll=saved)
    charge(server, MILL_X, 1800)
    server.assert_state("the_output_hopper_receives_the_previously_rolled_exact_iron_and_cobblestone", finished(report), results)


def run_client(args, server, report, data_map):
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    server.command("forceload add 3120 0 3151 15")
    server.assert_state("fixture_chunks_are_loaded", f"if loaded {mill(MILL_X)}", report["assertions"])
    server.command("fill 3127 79 5 3136 79 15 minecraft:stone_bricks")
    server.command("setworldspawn 3131 80 12")
    server.command(f'setblock {mill(MILL_X)} enderio:sag_mill[facing=south]{{IOConfig:{{Modes:{{down:"push"}}}},'
                   'Items:{Items:[{Slot:6,id:"enderio:basic_capacitor",Count:1}]}}')
    server.command(f"setblock {output(MILL_X)} minecraft:hopper[facing=down]")

    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "sag-jei", "--server", f"127.0.0.1:{args.port}", "--output", str(evidence), "--timeout", "155"]
    with (server.directory / "sag-jei-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            server.command(f"item replace entity {PLAYER} hotbar.1 with {ORE}{{PortSAG:158}} 2")
            server.command(f"item replace entity {PLAYER} hotbar.2 with minecraft:flint{{PortSAG:158}} 2")
            server.command(f"tp {PLAYER} {MILL_X + 0.5} 80 11.5 180 18")
            if args.jei is None:
                (evidence / "server-client-only").touch()
            (evidence / "server-ready").touch()
            wait_signal(client, evidence, "transferred")
            process_ore(server, report)
            data_map.write_text(json.dumps({"values": {"minecraft:feather": {
                "OutputMultiplier": 2.0, "BonusMultiplier": 3.0, "PowerUse": 0.5, "Durability": 12000,
            }}, "remove": ["minecraft:flint"]}) + "\n")
            server.command("reload")
            if client.wait(timeout=75) != 0:
                raise AssertionError(f"SAG Mill JEI client failed; see {evidence}")
            summary = json.loads((evidence / "summary.json").read_text())
            loaded = summary["client"]["captures"]["iron-ore-feather"]["loadedMods"]
            if digest(Path(loaded["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Client and server loaded different Ender IO jars")
            client_jei = digest(Path(loaded["jei"]["file"]))
            if args.jei and client_jei != report["jeiSha256"]:
                raise AssertionError("Client and server loaded different JEI jars")
            report["jeiSha256"] = client_jei
        finally:
            stop_client(client)
            summary = evidence / "summary.json"
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for path in ("java", "forge-server", "jar", "launch"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    parser.add_argument("--jei", type=Path, help="Install JEI on the server to exercise automatic recipe transfer")
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    pack = directory / WORLD / "datapacks/port_sag_jei"
    data_map = pack / "data/enderio/data_maps/item/grinding_ball.json"
    data_map.parent.mkdir(parents=True)
    (pack / "pack.mcmeta").write_text('{"pack":{"pack_format":15,"description":"SAG Mill viewer fixture"}}\n')
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar"),
              "jeiOnServer": args.jei is not None}
    if args.jei:
        server_jei = directory / "mods/jei.jar"
        shutil.copyfile(args.jei, server_jei)
        report["jeiSha256"] = digest(server_jei)
    try:
        run_stage(args, directory, report, "fresh", lambda server, _: run_client(args, server, report, data_map))
        run_stage(args, directory, report, "reopened", lambda server, results: server.assert_state(
            "grinding_results_survive_dedicated_restart", finished(report), results))
        report["passed"] = True
    finally:
        path = directory / "sag-jei-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"SAG JEI passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
