#!/usr/bin/env python3
"""Exercise a live filter editor during conduit removal and recovery over Forge TCP."""

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
REDSTONE_CONDUIT = '{id:"enderio:conduit",Count:1b,tag:{"enderio:data":{"enderio:conduit":"enderio:redstone"}}}'
DROPS = '@e[type=minecraft:item,distance=..4]'
OPERATOR = "EnderIOWrench"
LEVER = "3279 80 2"
LAMP = "3280 80 0"
EDITED_STOCK = 37


def item_entity(item, *, single=False):
    limit = ",limit=1" if single else ""
    return f'@e[type=minecraft:item,distance=..4,nbt={{Item:{item}}}{limit}]'


def inventory_item(item):
    return f"{PLAYER} Inventory[{item}]"


def connection(pos, index, side):
    return f'{pos} Connections[{index}][{{Side:"{side}"}}]'


def at_endpoint(stock_count=137):
    return (f'if data block {connection(ENDPOINT, 0, "east")}.Config{{is_insert:1b}} '
            f'{settings("block", filter_slot(INSERT_STOCK, insert=True), "limited", stock_count=stock_count)}')


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


def redstone_stays_removed(server, results, stage):
    server.assert_state(f"the_powered_lever_no_longer_drives_a_lamp_{stage}",
                        f'if block {LEVER} minecraft:lever[powered=true] '
                        f'if block {LAMP} minecraft:redstone_lamp[lit=false] '
                        f'if data block {ENDPOINT} {{Conduits:["enderio:item"]}} unless data block {ENDPOINT} Conduits[1] '
                        f'unless data block {NEIGHBOR} Connections[1][{{Side:"east",Status:"connected_conduit"}}]',
                        results)


def edit_after_redstone_removal(server, results, client, output, operator, operator_output):
    set_lever(server, LEVER, True)
    server.assert_state("redstone_lamp_is_lit_while_the_original_editor_is_open",
                        f"if block {LAMP} minecraft:redstone_lamp[lit=true]", results)
    (operator_output / "server-ready-wrench").touch()
    wait_signal(operator, operator_output, "wrenched")
    redstone_stays_removed(server, results, "after_wrench")
    server.command(f"execute positioned {ENDPOINT} store result score #all_drop port_stock if entity {DROPS}")
    server.assert_state("only_the_redstone_conduit_drops_and_the_item_filter_remains_installed",
                        f'{at_endpoint()} positioned {ENDPOINT} if score #all_drop port_stock matches 1 '
                        f'if entity {item_entity(REDSTONE_CONDUIT)}', results)
    (output / "server-ready-other-removed").touch()
    wait_signal(client, output, "editor-updated")
    server.assert_state("the_live_editor_has_changed_stock_without_consuming_the_player_sample",
                        f'{at_endpoint(EDITED_STOCK)} if data entity {PLAYER} Inventory[{stack(1, SAMPLE, EDITED_STOCK)}] '
                        f'unless data entity {inventory_item(FILTER)}', results)
    routing(server, results, "while_the_original_editor_is_open", EDITED_STOCK)
    (output / "server-ready-back").touch()
    wait_signal(client, output, "editor-returned")


@contextmanager
def guarded_client(args, server, report, name, scenario, launch):
    output = ROOT / "build/port-evidence" / name
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
               "--launch", str(launch.resolve()), "--scenario", scenario,
               "--output", str(output), "--server", f"127.0.0.1:{args.port}", "--timeout", "195"]
    if args.wrench_redstone:
        command.append("--wrench-redstone")
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
        if args.wrench_redstone:
            server.command(f"item replace entity {PLAYER} hotbar.1 with minecraft:cobblestone{{StockSample:5}} {EDITED_STOCK}")
        server.command(f"tp {PLAYER} 3280.5 80 3.5 180 15")
        if mixed:
            operator, operator_output = clients.enter_context(
                guarded_client(args, server, report, args.name + "-operator", "filter-wrench-operator", args.operator_launch))
            server.command(f"clear {OPERATOR}")
            server.command(f"item replace entity {OPERATOR} hotbar.0 with enderio:yeta_wrench")
            position = "3282.5 80 0.5 70 15" if args.wrench_redstone else "3282.5 80 3.5 140 15"
            server.command(f"tp {OPERATOR} {position}")

        (output / "server-ready-initial").touch()
        wait_signal(client, output, "editor-open")
        server.assert_state("opened_editor_is_bound_to_the_original_installed_stock_filter",
                            f'{at_endpoint()} unless data entity {inventory_item(FILTER)}', results)

        if args.wrench_redstone:
            edit_after_redstone_removal(server, results, client, output, operator, operator_output)
            return

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


def routing(server, results, stage, stock_count=137):
    server.assert_state(f"item_filter_has_the_expected_stock_and_tags_{stage}",
                        f"{topology(INSERT_STOCK)} {at_endpoint(stock_count)}", results)
    set_items(server, INSERT_STOCK.source)
    set_items(server, INSERT_STOCK.target, stack(0, SAMPLE, 30), stack(3, WRONG_SAMPLE, 11))
    set_items(server, INSERT_STOCK.source, *(stack(i, SAMPLE, 64) for i in range(3)), stack(3, WRONG_SAMPLE, 7))
    last_slot, extra_count = divmod(stock_count - 1, 64)
    server.assert_state(f"item_filter_reaches_its_native_stock_limit_{stage}",
                        f"if data block {INSERT_STOCK.target} Items[{stack(last_slot, SAMPLE, extra_count + 1)}]", results, timeout=20)
    wait_ticks(server, 35)
    quantity(server, "source", INSERT_STOCK.source)
    quantity(server, "target", INSERT_STOCK.target)
    server.assert_state(f"item_filter_conserves_the_exact_tagged_counts_{stage}",
                        f'if score #source port_stock matches {222 - stock_count} if score #target port_stock matches {stock_count} '
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
    parser.add_argument("--wrench-redstone", action="store_true", help="Remove redstone and edit the surviving item route")
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--world", type=Path, required=True)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")
    if args.wrench_redstone and args.operator_launch is None:
        parser.error("--wrench-redstone requires --operator-launch")
    root = ROOT / "build/port-runtime" / args.name
    root.mkdir()
    directory = root / "server"
    mixed = args.operator_launch is not None
    report = {"passed": False, "jarSha256": digest(args.jar), "assertions": [], "servers": [], "clients": {}}
    try:
        prepare_profile(args, directory, args.jar)
        shutil.copytree(args.world, directory / WORLD)

        def check_routes(server, results, stage):
            if args.wrench_redstone:
                routing(server, results, stage, EDITED_STOCK)
                redstone_stays_removed(server, results, stage)
            else:
                routing(server, results, stage)
            if mixed and not args.wrench_redstone:
                redstone(server, results, stage)

        def exercise(server, results):
            load(server, results, "initial")
            if mixed:
                prepare_mixed(server, results)
                redstone(server, results, "before_wrench_use")
            exercise_client(args, server, results, report)
            check_routes(server, results, "edited" if args.wrench_redstone else "reinstalled")
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
