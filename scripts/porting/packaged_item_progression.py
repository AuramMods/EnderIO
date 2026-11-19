#!/usr/bin/env python3
"""Craft the three item conduit tiers and exercise each upgrade on a real cross-chunk route."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_energy_progression import conduit_stack
from packaged_laserio import Lane, configure, connection, filter_slot, saved_item, topology
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage
from packaged_starter_progression import BINDER, PLAYER


LANE = Lane("item", 8)
TABLE = "3278 80 12"
LEFT, RIGHT = LANE.pos(3279), LANE.pos(3280)
TIERS = ("item", "enhanced_item", "ender_item")
ROUTES = ((TIERS[0], TIERS[0]), (TIERS[1], TIERS[0]), (TIERS[1], TIERS[1]), (TIERS[2], TIERS[1]), (TIERS[2], TIERS[2]))
RECIPES = ("item_conduit", "energetic_item_conduit", "energetic_item_conduit_upgrade",
           "vibrant_item_conduit", "vibrant_item_conduit_upgrade")
MATERIALS = ((BINDER, 34), ("enderio:conductive_alloy_ingot", 3), ("enderio:pulsating_alloy_ingot", 4),
             ("enderio:energetic_alloy_ingot", 5), ("enderio:vibrant_alloy_ingot", 5))
CRAFTED = (7, 15, 16)
FILTERED_ROUTE_FINAL_COUNT = 60
FILTER = '"enderio:item_filter":{items:[{index:0,value:{id:"minecraft:paper",Count:1,tag:{PortItemTier:162}}}],isNbt:1b}'


def bundle(pos, tier):
    return f'if data block {pos} {{Conduits:["enderio:{tier}"]}}'


def endpoint(pos, side, insert):
    return f'if data block {pos} Connections[0][{{Side:"{side}",Config:{connection(LANE, insert)}}}]'


def route(tiers):
    target_filter = filter_slot(LANE, insert=True)
    filter_entry = '{id:"enderio:advanced_item_filter",tag:{"enderio:data":{' + FILTER + '}}}'
    return (f'{bundle(LEFT, tiers[0])} {bundle(RIGHT, tiers[1])} {topology(LANE)} '
            f'{endpoint(LEFT, "west", False)} {endpoint(RIGHT, "east", True)} '
            f'if data block {RIGHT} Connections[0][{{Side:"east",Inventory:[{filter_entry}]}}] '
            f'if data block {target_filter}.tag."enderio:data"{{{FILTER}}}')


def delivered(count):
    return (f'if data block {LANE.target} Items[{{Slot:0b,id:"minecraft:paper",Count:{count}b,tag:{{PortItemTier:162}}}}] '
            f'unless data block {LANE.target} Items[1] '
            f'if data block {LANE.source} Items[{{Slot:1b,id:"minecraft:paper",Count:7b,tag:{{PortItemTier:161}}}}] '
            f'unless data block {LANE.source} Items[{{Slot:0b}}]')


def supply(server, count):
    server.command(f"item replace block {LANE.source} container.0 with minecraft:paper{{PortItemTier:162}} {count}")


def prepare(server, results):
    server.command("gamerule doMobSpawning false")
    server.command("gamerule doDaylightCycle false")
    server.command("gamerule spawnRadius 0")
    server.command("time set noon")
    server.command("forceload add 3264 0 3295 15")
    server.assert_state("both_route_chunks_are_loaded", f"if loaded {LEFT} if loaded {RIGHT}", results)
    server.command("fill 3275 79 5 3283 79 15 minecraft:stone_bricks")
    server.command("setworldspawn 3278 80 14")
    server.command(f"setblock {TABLE} minecraft:crafting_table")
    server.command(f"setblock {LANE.source} minecraft:barrel")
    server.command(f"setblock {LANE.target} minecraft:barrel")


def craft_materials(server):
    for slot, (item, count) in enumerate(MATERIALS, 1):
        server.command(f"item replace entity {PLAYER} hotbar.{slot} with {item} {count}")
    for recipe in RECIPES:
        server.command(f"recipe give {PLAYER} enderio:{recipe}")


def run_client(args, server, report):
    results = report["assertions"]
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "item-progression", "--server", f"127.0.0.1:{args.port}", "--output", str(evidence), "--timeout", "180"]
    with (server.directory / "item-progression-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            craft_materials(server)
            server.command(f"tp {PLAYER} 3278.5 80 14.5 180 20")
            (evidence / "server-table").touch()
            wait_signal(client, evidence, "crafted")
            counts = ",".join(conduit_stack(tier, count) for tier, count in zip(TIERS, CRAFTED))
            surplus = ",".join(f'{{id:"{item}",Count:{4 if item == BINDER else 1}b}}' for item, _ in MATERIALS)
            server.assert_state("five_survival_crafts_conserve_the_materials_and_use_only_the_two_required_lower_tiers",
                                f"if data entity {PLAYER} {{Inventory:[{counts},{surplus}]}}", results)

            server.command(f"tp {PLAYER} 3280.5 80 11.5 180 17")
            (evidence / "server-upgrade-0").touch()
            wait_signal(client, evidence, "upgraded-0")
            server.assert_state("the_first_crafted_basic_conduit_places_in_survival", bundle(LEFT, TIERS[0]), results)
            (evidence / "server-upgrade-1").touch()
            wait_signal(client, evidence, "upgraded-1")
            server.assert_state("two_crafted_basic_conduits_form_the_cross_chunk_route", topology(LANE), results)
            configure(server, LANE, insert=False)
            configured_filter = saved_item("enderio:advanced_item_filter", '{"enderio:data":{' + FILTER + '}}')
            configure(server, LANE, insert=True, filter_stack=configured_filter)
            server.command(f"item replace block {LANE.source} container.1 with minecraft:paper{{PortItemTier:161}} 7")
            for index, tiers in enumerate(ROUTES):
                if index:
                    step = index + 1
                    (evidence / f"server-upgrade-{step}").touch()
                    wait_signal(client, evidence, f"upgraded-{step}")
                server.assert_state(f"route_{index}_retains_its_cross_chunk_connections_and_filter", route(tiers), results)
                supply(server, 12)
                server.assert_state(f"route_{index}_delivers_12_exact_tagged_items_and_rejects_the_other_tag",
                                    f"{route(tiers)} {delivered(12 * (index + 1))}", results, timeout=12)

            returned = ",".join(conduit_stack(tier, count) for tier, count in zip(TIERS, (7, 15, 14)))
            server.assert_state("both_upgrades_return_exactly_one_replaced_part_per_placement",
                                f'if data entity {PLAYER} {{Inventory:[{returned}]}} '
                                f'unless data entity {PLAYER} Inventory[{{id:"enderio:advanced_item_filter"}}]', results)
            (evidence / "server-done").touch()
            if client.wait(timeout=25) != 0:
                raise AssertionError(f"Item conduit progression client failed; see {evidence}")
            summary = json.loads((evidence / "summary.json").read_text())
            frame = summary["client"]["captures"]["upgraded-conduit-route"]
            if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Client and server loaded different Ender IO jars")
        finally:
            stop_client(client)
            summary = evidence / "summary.json"
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def reopen(server, results):
    server.assert_state("reopened_ender_route_keeps_the_filter_and_both_balances",
                        f'{route(ROUTES[-1])} {delivered(FILTERED_ROUTE_FINAL_COUNT)}', results)
    supply(server, 4)
    server.assert_state("reopened_ender_route_fills_one_exact_tagged_stack_without_extra_items",
                        f'{route(ROUTES[-1])} {delivered(64)}', results, timeout=12)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for path in ("java", "forge-server", "jar", "launch"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}

    def fresh(server, results):
        prepare(server, results)
        run_client(args, server, report)

    try:
        run_stage(args, directory, report, "upgrades", fresh)
        run_stage(args, directory, report, "reopened", reopen)
        report["passed"] = True
    finally:
        path = directory / "item-progression-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Item progression passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
