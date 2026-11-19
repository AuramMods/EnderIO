#!/usr/bin/env python3
"""Break a live filter endpoint, recover its drops and reinstall them over Forge TCP."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_laserio import filter_slot, topology
from packaged_legacy_transfer_filters import wait_ticks
from packaged_redstone_filter_menus import PLAYER
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage
from packaged_stock_filter_menus import INSERT_STOCK, SAMPLE, WRONG_SAMPLE, load, quantity, set_items, settings, stack


ENDPOINT = INSERT_STOCK.pos(3280)
NEIGHBOR = INSERT_STOCK.pos(3279)
CHUNKS = "3264 0 3295 15"
FILTER = '{id:"enderio:limited_item_filter",Count:1b}'
CONDUIT = '{id:"enderio:conduit",Count:1b,tag:{"enderio:data":{"enderio:conduit":"enderio:item"}}}'
DROPS = '@e[type=minecraft:item,distance=..4]'


def item_entity(item, *, single=False):
    limit = ",limit=1" if single else ""
    return f'@e[type=minecraft:item,distance=..4,nbt={{Item:{item}}}{limit}]'


def inventory_item(item):
    return f"{PLAYER} Inventory[{item}]"


def at_endpoint():
    return (f'if data block {ENDPOINT} Connections[0][{{Side:"east"}}].Config{{is_insert:1b}} '
            f'{settings("block", filter_slot(INSERT_STOCK, insert=True), "limited")}')


def exercise_client(args, server, results, report):
    output = ROOT / "build/port-evidence" / args.name
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
               "--launch", str(args.launch.resolve()), "--scenario", "filter-recovery",
               "--output", str(output), "--server", f"127.0.0.1:{args.port}", "--timeout", "195"]
    with (server.directory / "client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            server.command(f"clear {PLAYER}")
            server.command(f"tp {PLAYER} 3280.5 80 3.5 180 15")
            (output / "server-ready-initial").touch()
            wait_signal(client, output, "editor-open")
            server.assert_state("opened_editor_is_bound_to_the_original_installed_stock_filter",
                                f'{at_endpoint()} unless data entity {inventory_item(FILTER)}', results)

            server.command(f"setblock {ENDPOINT} minecraft:air destroy")
            for name, selector in (("all", DROPS), ("filter", item_entity(FILTER)), ("conduit", item_entity(CONDUIT))):
                server.command(f"execute positioned {ENDPOINT} store result score #{name}_drop port_stock if entity {selector}")
            exact_filter = settings("entity", f"{item_entity(FILTER, single=True)} Item", "limited")
            server.assert_state("native_destruction_drops_one_conduit_and_one_exact_filter",
                                f'if block {ENDPOINT} minecraft:air positioned {ENDPOINT} '
                                'if score #all_drop port_stock matches 2 if score #filter_drop port_stock matches 1 '
                                f'if score #conduit_drop port_stock matches 1 {exact_filter}', results)
            (output / "server-ready-broken").touch()
            wait_signal(client, output, "editor-closed")
            server.command(f"execute positioned {ENDPOINT} run tp {DROPS} {PLAYER}")
            wait_signal(client, output, "picked-up")
            server.assert_state("ordinary_pickup_claims_both_physical_drops",
                                f'if data entity {inventory_item(CONDUIT)} '
                                f'{settings("entity", inventory_item(FILTER), "limited")} '
                                f'positioned {ENDPOINT} unless entity {DROPS}', results)
            (output / "server-ready-place").touch()
            wait_signal(client, output, "conduit-placed")
            server.assert_state("recovered_conduit_reconnects_across_the_original_chunk_border",
                                f'{topology(INSERT_STOCK)} unless data entity {inventory_item(CONDUIT)} '
                                f'unless data block {filter_slot(INSERT_STOCK, insert=True)}.id', results)
            (output / "server-ready-reopen").touch()
            wait_signal(client, output, "filter-reinstalled")
            server.assert_state("ordinary_reinstallation_keeps_the_exact_filter_and_one_copy",
                                f'{at_endpoint()} unless data entity {inventory_item(FILTER)} '
                                f'positioned {ENDPOINT} unless entity {DROPS}', results)
            (output / "server-ready-complete").touch()
            if client.wait(timeout=40) != 0:
                raise AssertionError(f"Guarded recovery client failed, see {output}")
        finally:
            stop_client(client)
            summary = output / "summary.json"
            if summary.is_file():
                report["client"] = json.loads(summary.read_text())


def routing(server, results, stage):
    server.assert_state(f"recovered_filter_keeps_the_same_stock_and_tags_{stage}",
                        f"{topology(INSERT_STOCK)} {at_endpoint()}", results)
    set_items(server, INSERT_STOCK.source)
    set_items(server, INSERT_STOCK.target, stack(0, SAMPLE, 30), stack(3, WRONG_SAMPLE, 11))
    set_items(server, INSERT_STOCK.source, *(stack(i, SAMPLE, 64) for i in range(3)), stack(3, WRONG_SAMPLE, 7))
    server.assert_state(f"recovered_filter_reaches_its_native_stock_limit_{stage}",
                        f"if data block {INSERT_STOCK.target} Items[{stack(2, SAMPLE, 9)}]", results, timeout=20)
    wait_ticks(server, 35)
    quantity(server, "source", INSERT_STOCK.source)
    quantity(server, "target", INSERT_STOCK.target)
    server.assert_state(f"recovered_filter_conserves_the_exact_tagged_counts_{stage}",
                        'if score #source port_stock matches 85 if score #target port_stock matches 137 '
                        f'if data block {INSERT_STOCK.source} Items[{stack(3, WRONG_SAMPLE, 7)}] '
                        f'if data block {INSERT_STOCK.target} Items[{stack(3, WRONG_SAMPLE, 11)}]', results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--forge-server", type=Path, required=True)
    parser.add_argument("--launch", type=Path, required=True)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--world", type=Path, required=True)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")
    root = ROOT / "build/port-runtime" / args.name
    root.mkdir()
    directory = root / "server"
    report = {"passed": False, "jarSha256": digest(args.jar), "assertions": [], "servers": []}
    try:
        prepare_profile(args, directory, args.jar)
        shutil.copytree(args.world, directory / WORLD)

        def exercise(server, results):
            load(server, results, "initial")
            exercise_client(args, server, results, report)
            routing(server, results, "reinstalled")
            server.command("setworldspawn 0 80 0")
            server.command(f"forceload remove {CHUNKS}")
            server.assert_state("both_route_chunks_actually_unload_without_the_client",
                                f"unless loaded {NEIGHBOR} unless loaded {ENDPOINT}", results, timeout=45)
            load(server, results, "reloaded")
            routing(server, results, "chunk_reload")

        def reopen(server, results):
            load(server, results, "restart")
            routing(server, results, "restart")

        run_stage(args, directory, report, "recovered", exercise)
        run_stage(args, directory, report, "reopened", reopen)
        report["passed"] = True
    finally:
        (root / "filter-recovery-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
