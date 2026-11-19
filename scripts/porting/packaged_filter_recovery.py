#!/usr/bin/env python3
"""Remove a live filter endpoint, recover its drops and reinstall them over Forge TCP."""

import argparse
from contextlib import ExitStack, contextmanager
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_cc_tweaked import set_lever
from packaged_laserio import configure, filter_slot, topology
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
OPERATOR = "EnderIOWrench"
LEVER = "3279 80 2"
LAMP = "3280 80 0"


def item_entity(item, *, single=False):
    limit = ",limit=1" if single else ""
    return f'@e[type=minecraft:item,distance=..4,nbt={{Item:{item}}}{limit}]'


def inventory_item(item):
    return f"{PLAYER} Inventory[{item}]"


def connection(pos, index, side):
    return f'{pos} Connections[{index}][{{Side:"{side}"}}]'


def at_endpoint():
    return (f'if data block {connection(ENDPOINT, 0, "east")}.Config{{is_insert:1b}} '
            f'{settings("block", filter_slot(INSERT_STOCK, insert=True), "limited")}')


def link(pos, index, side, *, internal=False):
    status = "connected_conduit" if internal else "connected_block"
    return f'if data block {pos} Connections[{index}][{{Side:"{side}",Status:"{status}"}}]'


def prepare_mixed(server, results):
    server.assert_state("saved_item_route_has_the_original_limited_filter", at_endpoint(), results)
    inventory = connection(ENDPOINT, 0, "east") + ".Inventory"
    server.command(f"data modify storage port:recovery Inventory set from block {inventory}")
    set_lever(server, LEVER, False)
    server.command(f"setblock {LAMP} minecraft:redstone_lamp")
    for pos in (NEIGHBOR, ENDPOINT):
        server.command(f"setblock {pos} minecraft:air")
        server.command(f'setblock {pos} enderio:conduit{{Conduits:["enderio:item","enderio:redstone"],Connections:[[],[]]}}')
    server.assert_state("both_routes_cross_the_chunk_border",
                        f'{topology(INSERT_STOCK)} {link(NEIGHBOR, 1, "south")} {link(NEIGHBOR, 1, "east", internal=True)} '
                        f'{link(ENDPOINT, 1, "west", internal=True)} {link(ENDPOINT, 1, "north")}', results)
    configure(server, INSERT_STOCK, insert=False)
    configure(server, INSERT_STOCK, insert=True)
    server.command(f"data modify block {inventory} set from storage port:recovery Inventory")
    for path, insert in ((connection(NEIGHBOR, 1, "south"), False), (connection(ENDPOINT, 1, "north"), True)):
        server.command(f'data modify block {path}.Config set value {{type:"enderio:redstone",'
                       f'is_insert:{int(insert)}b,is_extract:{int(not insert)}b,insert_channel:"white",'
                       'extract_channel:"white",is_strong_output_signal:0b}')


def redstone(server, results, stage):
    for active in (True, False):
        set_lever(server, LEVER, active)
        lit = str(active).lower()
        server.assert_state(f"redstone_lamp_{'lights' if active else 'goes_dark'}_{stage}",
                            f"if block {LAMP} minecraft:redstone_lamp[lit={lit}]", results)


@contextmanager
def guarded_client(args, server, report, name, scenario, launch):
    output = ROOT / "build/port-evidence" / name
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
               "--launch", str(launch.resolve()), "--scenario", scenario,
               "--output", str(output), "--server", f"127.0.0.1:{args.port}", "--timeout", "195"]
    with (server.directory / f"{scenario}.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            yield client, output
            (output / "server-ready-complete").touch()
            if client.wait(timeout=40) != 0:
                raise AssertionError(f"Guarded recovery client failed, see {output}")
        finally:
            stop_client(client)
            summary = output / "summary.json"
            if summary.is_file():
                report["clients"][scenario] = json.loads(summary.read_text())


def exercise_client(args, server, results, report):
    mixed = args.operator_launch is not None
    editor = "filter-wrench-editor" if mixed else "filter-recovery"
    with ExitStack() as clients:
        client, output = clients.enter_context(guarded_client(args, server, report, args.name, editor, args.launch))
        server.command(f"clear {PLAYER}")
        server.command(f"tp {PLAYER} 3280.5 80 3.5 180 15")
        if mixed:
            operator, operator_output = clients.enter_context(
                guarded_client(args, server, report, args.name + "-operator", "filter-wrench-operator", args.operator_launch))
            server.command(f"clear {OPERATOR}")
            server.command(f"item replace entity {OPERATOR} hotbar.0 with enderio:yeta_wrench")
            server.command(f"tp {OPERATOR} 3282.5 80 3.5 140 15")

        (output / "server-ready-initial").touch()
        wait_signal(client, output, "editor-open")
        server.assert_state("opened_editor_is_bound_to_the_original_installed_stock_filter",
                            f'{at_endpoint()} unless data entity {inventory_item(FILTER)}', results)

        if mixed:
            (operator_output / "server-ready-wrench").touch()
            wait_signal(operator, operator_output, "wrenched")
            remains = (f'if block {ENDPOINT} enderio:conduit if data block {ENDPOINT} {{Conduits:["enderio:redstone"]}} '
                       f'unless data block {ENDPOINT} Conduits[1] {link(ENDPOINT, 0, "north")} '
                       f'{link(ENDPOINT, 0, "west", internal=True)} '
                       f'unless data block {NEIGHBOR} Connections[0][{{Side:"east",Status:"connected_conduit"}}]')
            server.assert_state("wrench_removes_only_the_item_part_and_its_internal_link", remains, results)
        else:
            server.command(f"setblock {ENDPOINT} minecraft:air destroy")
            remains = f"if block {ENDPOINT} minecraft:air"
        for name, selector in (("all", DROPS), ("filter", item_entity(FILTER)), ("conduit", item_entity(CONDUIT))):
            server.command(f"execute positioned {ENDPOINT} store result score #{name}_drop port_stock if entity {selector}")
        if mixed:
            server.command(f"data get entity {OPERATOR} Inventory")
            server.command(f"execute positioned {ENDPOINT} as {DROPS} run data get entity @s Item")
        exact_filter = settings("entity", f"{item_entity(FILTER, single=True)} Item", "limited")
        server.assert_state("endpoint_removal_drops_one_conduit_and_one_exact_filter",
                            f'{remains} positioned {ENDPOINT} '
                            'if score #all_drop port_stock matches 2 if score #filter_drop port_stock matches 1 '
                            f'if score #conduit_drop port_stock matches 1 {exact_filter}', results)
        (output / "server-ready-broken").touch()
        wait_signal(client, output, "editor-closed")
        if mixed:
            redstone(server, results, "with_only_redstone_installed")

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
    parser.add_argument("--operator-launch", type=Path, help="Use a second guarded profile to wrench a mixed bundle")
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--world", type=Path, required=True)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")
    root = ROOT / "build/port-runtime" / args.name
    root.mkdir()
    directory = root / "server"
    mixed = args.operator_launch is not None
    report = {"passed": False, "jarSha256": digest(args.jar), "assertions": [], "servers": [], "clients": {}}
    try:
        prepare_profile(args, directory, args.jar)
        shutil.copytree(args.world, directory / WORLD)

        def check_routes(server, results, stage):
            routing(server, results, stage)
            if mixed:
                redstone(server, results, stage)

        def exercise(server, results):
            load(server, results, "initial")
            if mixed:
                prepare_mixed(server, results)
                redstone(server, results, "before_wrench_use")
            exercise_client(args, server, results, report)
            check_routes(server, results, "reinstalled")
            server.command("setworldspawn 0 80 0")
            server.command(f"forceload remove {CHUNKS}")
            server.assert_state("both_route_chunks_actually_unload_without_the_client",
                                f"unless loaded {NEIGHBOR} unless loaded {ENDPOINT}", results, timeout=45)
            load(server, results, "reloaded")
            check_routes(server, results, "chunk_reload")

        def reopen(server, results):
            load(server, results, "restart")
            check_routes(server, results, "restart")

        run_stage(args, directory, report, "recovered", exercise)
        run_stage(args, directory, report, "reopened", reopen)
        report["passed"] = True
    finally:
        (root / "filter-recovery-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
