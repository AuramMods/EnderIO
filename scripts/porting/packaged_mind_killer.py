#!/usr/bin/env python3
"""Craft a Mind Killer and change the limits of a fully paid Powered Spawner."""

import argparse
import json
from pathlib import Path
import re
import subprocess
import sys
import time

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_server_smoke import ROOT, digest
from packaged_solar_charging import check
from packaged_spawner_rules import place_powered
from packaged_spawners import PLAYER, prepare_profile, run_stage


MACHINE, MOUNT, NEARBY, CONTROL = "4200 80 8", "4200 81 8", "4203 80 8", "4215 80 8"
COST = 10000
MIND_KILLER = "enderio:mind_killer"
INGREDIENTS = {0: ("zombie_electrode", 1), 1: ("energetic_alloy_ingot", 1), 2: ("soularium_ingot", 2)}
MACHINE_CONFIG = "config/enderio/machines-common.toml"
SPAWNER_RELOAD = "Config file enderio/machines-common.toml changed, sending notifies"
COW = "@e[type=minecraft:cow,x=4200.5,y=80,z=8.5,distance=..8]"
SHEEP = "@e[type=minecraft:sheep,x=4215.5,y=80,z=8.5,distance=..8]"
IMMOBILE_COW = COW[:-1] + ',nbt={NoAI:1b,ForgeData:{"enderio:movable":1b}}]'


def prepare(server):
    for command in ("gamerule doMobSpawning false", "gamerule doLimitedCrafting true", "gamerule spawnRadius 0",
                    "forceload add 4192 0 4223 15", "fill 4194 79 3 4221 79 14 minecraft:grass_block",
                    "setworldspawn 4200 80 12", "setblock 4199 80 9 minecraft:crafting_table",
                    f"setblock {NEARBY} enderio:powered_spawner", "scoreboard objectives add port_spawner dummy"):
        server.command(command)
    place_powered(server, MACHINE, "cow", 0)
    place_powered(server, CONTROL, "sheep", 0)


def paid():
    return (f'if data block {MACHINE} {{EnergyStored:0,Task:{{EnergyCost:{COST},EnergyConsumed:{COST},'
            f'EntityType:"minecraft:cow",SpawnMode:"entity_type"}}}} unless entity {COW}')


def spawned():
    return (f'if block {MOUNT} {MIND_KILLER} if data block {MOUNT} {{id:"{MIND_KILLER}"}} '
            f'if data block {MACHINE} {{EnergyStored:0,EntityStorage:{{entity_type:"minecraft:cow"}}}} '
            f"if entity {IMMOBILE_COW}")


def set_live_limit(server):
    path = server.directory / MACHINE_CONFIG
    debug = server.directory / "logs/debug.log"
    updated, count = re.subn(r"(?m)^(\s*maxSpawners\s*=\s*)0$", r"\g<1>3", path.read_text())
    if count != 1:
        raise AssertionError(f"Expected the server's zero-limit fixture in {path}")
    with debug.open() as log:
        log.seek(0, 2)
        path.write_text(updated)
        deadline = time.monotonic() + 12
        while time.monotonic() < deadline:
            if SPAWNER_RELOAD in log.read():
                return
            time.sleep(0.1)
    raise AssertionError(f"Forge did not observe the live common-config change, see {debug}")


def exercise(args, server, results, report):
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "mind-killer", "--server", f"127.0.0.1:{args.port}", "--output", str(evidence), "--timeout", "180"]
    summary = evidence / "summary.json"
    with (server.directory / "mind-killer-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            for slot, (item, count) in INGREDIENTS.items():
                server.command(f"item replace entity {PLAYER} hotbar.{slot} with enderio:{item} {count}")
            server.command(f"tp {PLAYER} 4200.5 80 11.5 180 10")
            (evidence / "server-ingredients").touch()
            wait_signal(client, evidence, "mounted")
            consumed = " ".join(f'unless data entity {PLAYER} Inventory[{{id:"enderio:{item}"}}]' for item, _ in INGREDIENTS.values())
            server.assert_state("stock_survival_craft_consumes_four_ingredients_and_mounts_exactly_one_block",
                                f'if block {MOUNT} {MIND_KILLER} if data block {MOUNT} {{id:"{MIND_KILLER}"}} '
                                f'{consumed} unless data entity {PLAYER} Inventory[{{id:"{MIND_KILLER}"}}]', results)
            server.command(f"data merge block {MACHINE} {{EnergyStored:{COST}}}")
            (evidence / "server-inspect").touch()
            wait_signal(client, evidence, "paid-and-blocked")
            server.assert_state("a_zero_limit_keeps_the_stock_cow_job_fully_paid_without_spawning", f"{paid()} unless entity {SHEEP}", results)
            set_live_limit(server)
            server.command(f"data merge block {CONTROL} {{EnergyStored:{COST}}}")
            server.assert_state("a_fresh_control_job_observes_the_reloaded_nonzero_limit",
                                f"if entity {SHEEP} if data block {CONTROL} {{EnergyStored:0}}", results, timeout=15)
            server.assert_state("the_same_paid_cow_job_uses_the_new_limit_without_repayment",
                                f"{spawned()} unless data block {MACHINE} Task", results, timeout=10)
            server.command(f"execute store result score #cows port_spawner if entity {COW}")
            server.assert_state("one_original_stock_spawn_has_no_ai_and_can_move", f"if score #cows port_spawner matches 1 if entity {IMMOBILE_COW}", results)
            (evidence / "server-spawned").touch()
            if client.wait(timeout=35) != 0:
                raise AssertionError(f"Mind Killer client failed; see {evidence}")
            capture = json.loads(summary.read_text())["client"]["captures"]["placed-mind-killer"]
            check("client_and_server_use_the_same_production_artifact", report["jarSha256"],
                  digest(Path(capture["loadedMods"]["enderio"]["file"])), results)
        finally:
            stop_client(client)
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for key in ("java", "forge-server", "jar", "launch"):
        parser.add_argument(f"--{key}", type=Path, required=True)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    config = directory / MACHINE_CONFIG
    config.parent.mkdir(parents=True)
    config.write_text("[poweredSpawner]\nmaxSpawners = 0\nspawnAmount = 1\n")
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}

    def fresh(server, results):
        prepare(server)
        exercise(args, server, results, report)

    def reopened(server, results):
        # Loading an empty task host may prepare the next recipe with zero FE paid.
        server.assert_state("restart_keeps_the_mounted_mind_killer_and_a_fresh_unpaid_task", f"{spawned()} "
                            f"if data block {MACHINE} {{Task:{{EnergyConsumed:0}}}}", results)
        server.command(f"execute store result score #cows port_spawner if entity {COW}")
        server.assert_state("restart_keeps_exactly_one_cow", "if score #cows port_spawner matches 1", results)

    try:
        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", reopened)
        report["passed"] = True
    finally:
        result = directory / "mind-killer-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Mind Killer passed={report['passed']}, {len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
