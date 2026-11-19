#!/usr/bin/env python3
"""Use an actual Dark Steel Sword drop as the wall, floor and Ender Resonator item."""

import argparse
from collections import Counter
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_glass_progression import Smelt, finished_smelter, prepare_smelter, start_smelter, supply, transfer
from packaged_sag_progression import charge, mill
from packaged_server_smoke import ROOT, digest
from packaged_slicer import AXE, CASES, COST, PAID, SHEARS, base_state, feed, finished, partial, prepare_machine, tools_for
from packaged_spawners import WORLD, prepare_profile, run_stage
from packaged_starter_progression import PLAYER


CHEST = "3321 80 10"
SKULL = "3323 80 10"
HEAD = "enderio:enderman_head"
WALL = "enderio:wall_enderman_head"
SWORD = "enderio:dark_steel_sword"
TARGETS = "@e[type=minecraft:enderman,tag=port_skull_target]"
HEAD_DROP = '@e[type=minecraft:item,nbt={Item:{id:"enderio:enderman_head"}},limit=1]'
SMELT = Smelt(3316, 7, "dark_steel_ingot", 2, 1600)
SLICER = (3340, *CASES[4][1:])
BATCH_SIZE, BATCHES = 32, 6


def write_spawn_function(world):
    pack = world / "datapacks/port_skulls"
    functions = pack / "data/port_skulls/functions"
    functions.mkdir(parents=True)
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 15, "description": "Spawn survival attack targets"}}))
    summon = ('summon minecraft:enderman 3328.5 80 8.5 '
              '{Health:1.0f,NoAI:1b,Silent:1b,PersistenceRequired:1b,Tags:["port_skull_target"]}')
    (functions / "spawn.mcfunction").write_text("\n".join([summon] * BATCH_SIZE + [f"team join port_skulls {TARGETS}"]) + "\n")


def prepare(server, results):
    server.command("forceload add 3312 0 3359 15")
    server.assert_state("workshop_skull_station_and_slicer_chunks_are_loaded",
                        f"if loaded {CHEST} if loaded {SKULL} if loaded {mill(SLICER[0])}", results)
    for command in ("gamerule doMobSpawning false", "gamerule doDaylightCycle false", "gamerule doLimitedCrafting true",
                    "gamerule maxEntityCramming 0", "gamerule spawnRadius 0", "time set noon",
                    "fill 3312 79 5 3346 79 15 minecraft:stone", "setworldspawn 3320 80 14",
                    f"setblock {CHEST} minecraft:chest[facing=south]", "setblock 3320 80 10 minecraft:crafting_table",
                    "setblock 3323 80 9 minecraft:stone", "team add port_skulls", "team modify port_skulls collisionRule never"):
        server.command(command)

    prepare_smelter(server, SMELT)
    for slot, item in enumerate(("minecraft:iron_ingot", "enderio:powdered_coal", "minecraft:obsidian")):
        supply(server, SMELT, slot, item, 1)
    start_smelter(server, SMELT)
    server.assert_state("stock_alloy_job_exports_two_real_dark_steel_ingots", finished_smelter(SMELT), results, timeout=35)
    transfer(server, SMELT.pos(79), 0, CHEST, 0)
    server.command(f"item replace block {CHEST} container.1 with minecraft:stick")
    server.command(f"item replace block {CHEST} container.2 with minecraft:iron_pickaxe")


def ready_at(server, evidence, marker, pos):
    server.command(f"tp {PLAYER} {pos} 180 17")
    (evidence / f"server-{marker}").touch()


def player_head():
    return f'if data entity {PLAYER} Inventory[{{id:"{HEAD}",Count:1b}}]'


def pickup(server, results, label):
    # Reposition at the real item only if the player has not collected it already.
    server.command(f'execute unless data entity {PLAYER} Inventory[{{id:"{HEAD}"}}] '
                   f'as {HEAD_DROP} at @s run tp {PLAYER} ~ ~ ~')
    server.assert_state(label + "_collects_exactly_one_original_head", f"{player_head()} unless entity {HEAD_DROP}", results, timeout=10)


def combat(server, client, evidence, report):
    results = report["assertions"]
    # Vanilla sweep attacks skip teammates, so every eligible roll is a direct sword hit.
    server.command(f"team join port_skulls {PLAYER}")
    ready_at(server, evidence, "fight", "3328.5 80 10.5")
    wait_signal(client, evidence, "fight-ready")
    for batch in range(BATCHES):
        server.command("function port_skulls:spawn")
        server.assert_state(f"batch_{batch}_has_live_endermen", f"if entity {TARGETS}", results)
        (evidence / f"server-batch-{batch}").touch()
        state = wait_signal(client, evidence, f"combat-{batch}")
        if state["found"]:
            report["attacks"] = state["attacks"]
            break
        if state["attacks"] != (batch + 1) * BATCH_SIZE:
            raise AssertionError(f"Batch {batch} finished after {state['attacks']} direct attacks")
    else:
        raise AssertionError(f"No Enderman Head dropped after {BATCH_SIZE * BATCHES} native attacks")
    pickup(server, results, "native_sword_drop")
    server.command(f"kill {TARGETS}")
    damage = report["attacks"]
    server.assert_state("the_original_dark_steel_sword_records_native_attack_wear",
                        f'if data entity {PLAYER} Inventory[{{id:"{SWORD}",Count:1b,tag:{{Damage:{damage}}}}}]', results)


def floor_and_wall(server, client, evidence, report):
    results = report["assertions"]
    ready_at(server, evidence, "craft", "3320.5 80 12.5")
    wait_signal(client, evidence, "crafted")
    server.assert_state("the_original_dark_steel_ingots_supply_the_survival_sword",
                        f'if data entity {PLAYER} Inventory[{{id:"{SWORD}",Count:1b}}] '
                        f'unless data entity {PLAYER} Inventory[{{id:"enderio:dark_steel_ingot"}}] unless data block {CHEST} Items[0]', results)
    combat(server, client, evidence, report)
    ready_at(server, evidence, "floor", "3323.5 80 12.5")
    wait_signal(client, evidence, "floor-placed")
    server.assert_state("native_floor_placement_sets_rotation_and_spends_the_only_head",
                        f'if block {SKULL} {HEAD}[rotation=8] unless data entity {PLAYER} Inventory[{{id:"{HEAD}"}}]', results)
    (evidence / "server-mine").touch()
    wait_signal(client, evidence, "head-mined")
    server.assert_state("floor_head_is_removed_by_survival_mining", f"if block {SKULL} minecraft:air", results)
    pickup(server, results, "floor_loot")
    ready_at(server, evidence, "wall", "3323.5 80 12.5")
    wait_signal(client, evidence, "wall-placed")
    server.assert_state("the_original_head_mounts_facing_south",
                        f'if block {SKULL} {WALL}[facing=south] unless data entity {PLAYER} Inventory[{{id:"{HEAD}"}}]', results)


def prepare_slicer(server):
    x = SLICER[0]
    prepare_machine(server, x)
    counts = Counter(item for item in SLICER[2] if item != HEAD)
    axe_damage, shears_damage = tools_for(SLICER)
    stock = list(counts.items()) + [(f"{AXE}{{Damage:{axe_damage},PortSlicer:159}}", 1),
                                   (f"{SHEARS}{{Damage:{shears_damage},PortSlicer:159}}", 1)]
    for slot, (item, count) in enumerate(stock):
        server.command(f"item replace block {feed(x)} container.{slot} with {item} {count}")


def consume(server, client, evidence, report):
    results = report["assertions"]
    server.assert_state("the_original_wall_head_survives_restart_facing_south", f"if block {SKULL} {WALL}[facing=south]", results)
    prepare_slicer(server)
    ready_at(server, evidence, "wall", "3323.5 80 12.5")
    wait_signal(client, evidence, "wall-restored")
    (evidence / "server-mine").touch()
    wait_signal(client, evidence, "head-mined")
    server.assert_state("restored_wall_head_is_removed_by_survival_mining", f"if block {SKULL} minecraft:air", results)
    pickup(server, results, "wall_loot")
    ready_at(server, evidence, "slicer", "3340.5 80 11.5")
    wait_signal(client, evidence, "head-inserted")
    server.assert_state("the_actual_wall_drop_supplies_the_correct_slicer_slot",
                        f'{base_state(SLICER)} unless data entity {PLAYER} Inventory[{{id:"{HEAD}"}}] '
                        f"unless data block {feed(SLICER[0])} Items[0]", results)
    charge(server, SLICER[0], PAID)
    server.assert_state("the_head_queues_one_ender_resonator_at_the_paid_checkpoint",
                        partial(SLICER), results, timeout=14)
    (evidence / "server-partial").touch()
    wait_signal(client, evidence, "partial-seen")
    charge(server, SLICER[0], COST - PAID)
    server.assert_state("the_original_head_is_consumed_for_the_exact_20000_fe_and_one_resonator",
                        finished(SLICER), results, timeout=35)


def run_client(args, server, report, *, restart):
    phase = "verify" if restart else "exercise"
    evidence = ROOT / "build/port-evidence" / f"{args.name}-{phase}-client"
    summary = evidence / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "ender-skull", "--multiplayer-phase", phase, "--server", f"127.0.0.1:{args.port}",
               "--output", str(evidence), "--timeout", "225"]
    with (server.directory / f"skull-client-{phase}.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            (consume if restart else floor_and_wall)(server, client, evidence, report)
            (evidence / "server-done").touch()
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"Ender Skull client failed; see {evidence}")
            for capture in json.loads(summary.read_text())["client"]["captures"].values():
                if digest(Path(capture["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                    raise AssertionError("Client and server loaded different Ender IO jars")
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
    write_spawn_function(directory / WORLD)
    report = {"passed": False, "assertions": [], "servers": [], "clients": {}, "jarSha256": digest(directory / "mods/enderio.jar")}

    def fresh(server, results):
        prepare(server, results)
        run_client(args, server, report, restart=False)

    def reopened(server, results):
        server.assert_state("the_original_head_is_consumed_and_the_resonator_survives_a_second_restart",
                            f"{finished(SLICER)} if block {SKULL} minecraft:air unless entity {HEAD_DROP}", results)

    try:
        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "resume", lambda server, _: run_client(args, server, report, restart=True))
        run_stage(args, directory, report, "reopened", reopened)
        report["passed"] = True
    finally:
        result = directory / "ender-skull-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Ender Skull progression passed={report['passed']}, {len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
