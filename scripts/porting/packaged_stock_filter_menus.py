#!/usr/bin/env python3
"""Check limited stock and large filter menus over TCP in a clean Forge world."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_laserio import Lane, configure, filter_slot, topology
from packaged_legacy_transfer_filters import wait_ticks
from packaged_redstone_filter_menus import PLAYER
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage


INSERT_STOCK = Lane("item", 1)
EXTRACT_STOCK = Lane("item", 5)
LARGE = Lane("item", 9)
ADVANCED = Lane("item", 13)
FILTERS = (
    ("limited", "limited_item_filter", 'minecraft:paper{MenuStock:32} 32'),
    ("large", "big_item_filter", 'minecraft:paper{LargeSample:1} 7'),
    ("large-advanced", "big_advanced_item_filter", 'minecraft:iron_pickaxe{Damage:27,LargeSample:2}'),
)
SAMPLE = 'id:"minecraft:cobblestone",tag:{StockSample:5}'
WRONG_SAMPLE = 'id:"minecraft:cobblestone",tag:{StockSample:6}'
SAVED_LIMIT = ('{"enderio:data":{"enderio:limited_item_filter":'
               '{items:[{index:17,value:{id:"minecraft:cobblestone",Count:137,tag:{StockSample:5}}}]}}}')


def load(server, results, stage):
    server.command("forceload add 3264 0 3295 15")
    server.assert_state(f"both_chunks_loaded_{stage}", "if loaded 3279 80 1 if loaded 3280 80 13", results)


def prepare(server, results):
    load(server, results, "initial")
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    server.command("fill 3277 79 0 3282 79 15 minecraft:stone")
    server.command("setworldspawn 3279 80 3")
    server.command("scoreboard objectives add port_stock dummy")
    for lane in (INSERT_STOCK, EXTRACT_STOCK, LARGE, ADVANCED):
        server.command(f"setblock {lane.source} minecraft:barrel")
        server.command(f"setblock {lane.target} minecraft:barrel")
        for x in (3279, 3280):
            server.command(f'setblock {lane.pos(x)} enderio:conduit{{Conduits:["enderio:item"],Connections:[[]]}}')
        server.assert_state(f"cross_chunk_topology_{lane.z}", topology(lane), results)
        configure(server, lane, insert=True)
        configure(server, lane, insert=False)


def held(item, slot=0):
    return f'{PLAYER} Inventory[{{Slot:{slot}b,id:"enderio:{item}"}}]'


def settings(target, path, name, *, stock_count=137):
    data = f'{path}.tag."enderio:data"."enderio:{"limited_item_filter" if name == "limited" else "item_filter"}"'
    if name == "limited":
        return (f'if data {target} {data}{{isNbt:1b}} '
                f'if data {target} {data}.items[0].value{{id:"minecraft:paper",Count:64,tag:{{MenuStock:32}}}} '
                f'if data {target} {data}.items[17].value{{id:"minecraft:cobblestone",Count:{stock_count},tag:{{StockSample:5}}}}')
    if name == "large":
        return (f'if data {target} {data}{{isInvert:1b}} '
                f'if data {target} {data}.items[35].value{{id:"minecraft:paper",Count:1,tag:{{LargeSample:1}}}}')
    return (f'if data {target} {data}{{isNbt:1b,damageMode:"up_to_25"}} '
            f'if data {target} {data}.items[35].value{{id:"minecraft:iron_pickaxe",Count:1,tag:{{Damage:27,LargeSample:2}}}}')


def exercise_client(args, server, results, report, *, reopen=False):
    stage = "reopened" if reopen else "edited"
    output = ROOT / "build/port-evidence" / f"{args.name}-{stage}"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
               "--launch", str(args.launch.resolve()), "--scenario", "stock-filter-menus",
               "--multiplayer-phase", "verify" if reopen else "exercise",
               "--output", str(output), "--server", f"127.0.0.1:{args.port}", "--timeout", "195"]
    with (server.directory / f"client-{stage}.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            if reopen:
                server.assert_state("the_original_limited_stack_survives_in_playerdata",
                                    settings("entity", held("limited_item_filter", 3), "limited"), results)
                server.command(f"item replace entity {PLAYER} hotbar.0 from entity {PLAYER} hotbar.3")
                (output / "server-ready-limited").touch()
            else:
                for name, item, sample in FILTERS:
                    filter_tag = SAVED_LIMIT if name == "limited" else ""
                    server.command(f"item replace entity {PLAYER} hotbar.0 with enderio:{item}{filter_tag}")
                    server.command(f"item replace entity {PLAYER} hotbar.1 with {sample}")
                    if name == "limited":
                        server.command(f"item replace entity {PLAYER} hotbar.2 with minecraft:cobblestone{{StockSample:5}} 4")
                        data = f'{held(item)}.tag."enderio:data"."enderio:limited_item_filter"'
                        server.assert_state("held_fixture_already_contains_the_137_item_target",
                                            f'if data entity {data}.items[0].value{{Count:137,tag:{{StockSample:5}}}}', results)
                    (output / f"server-ready-{name}").touch()
                    wait_signal(client, output, f"{name}-edited")
                    server.assert_state(f"{name}_menu_saves_the_exact_stack", settings("entity", held(item), name), results)
                    if name == "limited":
                        server.command(f"item replace entity {PLAYER} hotbar.3 from entity {PLAYER} hotbar.0")
                        install(server, INSERT_STOCK, True, item)
                        install(server, EXTRACT_STOCK, False, item)
                    elif name == "large":
                        install(server, LARGE, False, item)
                    else:
                        install(server, ADVANCED, True, item)
                    (output / f"server-ready-{name}-confirmed").touch()
                    wait_signal(client, output, f"{name}-closed")
            if client.wait(timeout=45) != 0:
                raise AssertionError(f"Guarded stock filter client failed, see {output}")
        finally:
            stop_client(client)
            summary = output / "summary.json"
            if summary.is_file():
                report["clients"][stage] = json.loads(summary.read_text())


def install(server, lane, insert, item):
    path = filter_slot(lane, insert=insert).rsplit("[", 1)[0]
    empty_slots = "[{},{}]" if insert else "[{}]"
    server.command(f"data modify storage port:stock Inventory set value {empty_slots}")
    server.command(f"data modify storage port:stock Inventory[{int(insert)}] set from entity {held(item)}")
    server.command(f"data modify block {path} set from storage port:stock Inventory")


def set_items(server, pos, *items):
    server.command(f"data merge block {pos} {{Items:[{','.join(items)}]}}")


def stack(slot, item, count):
    return f'{{Slot:{slot}b,Count:{count}b,{item}}}'


def quantity(server, label, pos):
    # Every matching stack in the fixture occupies one of the first three slots.
    server.command(f"scoreboard players set #{label} port_stock 0")
    for slot in range(3):
        server.command("scoreboard players set #stack port_stock 0")
        server.command(f"execute store result score #stack port_stock run data get block {pos} "
                       f"Items[{{Slot:{slot}b,{SAMPLE}}}].Count")
        server.command(f"scoreboard players operation #{label} port_stock += #stack port_stock")


def routes(server, results, stage):
    for name, lane, insert in (("limited", INSERT_STOCK, True), ("limited", EXTRACT_STOCK, False),
                               ("large", LARGE, False), ("large-advanced", ADVANCED, True)):
        server.assert_state(f"{name}_configured_on_lane_{lane.z}_{stage}",
                            settings("block", filter_slot(lane, insert=insert), name), results)

    for lane in (INSERT_STOCK, EXTRACT_STOCK):
        set_items(server, lane.source)
        destination = [stack(3, WRONG_SAMPLE, 11)]
        if lane == INSERT_STOCK:
            destination.append(stack(0, SAMPLE, 30))
        set_items(server, lane.target, *destination)
        set_items(server, lane.source, *(stack(i, SAMPLE, 64) for i in range(3)), stack(3, WRONG_SAMPLE, 7))
    for lane, inserted, full in ((INSERT_STOCK, 137, 2), (EXTRACT_STOCK, 55, 0)):
        # A final short stack appears when the live route reaches its target.
        count = 9 if full else inserted
        server.assert_state(f"limited_route_reaches_the_expected_stock_{lane.z}_{stage}",
                            f"if data block {lane.target} Items[{stack(full, SAMPLE, count)}]", results, timeout=25)
    wait_ticks(server, 45)
    for lane, source, target in ((INSERT_STOCK, 85, 137), (EXTRACT_STOCK, 137, 55)):
        quantity(server, "source", lane.source)
        quantity(server, "target", lane.target)
        server.assert_state(f"limited_route_conserves_exact_tagged_stock_{lane.z}_{stage}",
                            f'if score #source port_stock matches {source} if score #target port_stock matches {target} '
                            f'if data block {lane.source} Items[{stack(3, WRONG_SAMPLE, 7)}] '
                            f'if data block {lane.target} Items[{stack(3, WRONG_SAMPLE, 11)}]', results)

    paper = 'id:"minecraft:paper",tag:{LargeSample:99}'
    iron = 'id:"minecraft:iron_ingot"'
    set_items(server, LARGE.source)
    set_items(server, LARGE.target)
    set_items(server, LARGE.source, stack(0, paper, 5), stack(1, iron, 3))
    server.assert_state(f"large_deny_filter_matches_item_type_using_last_slot_{stage}",
                        f'if data block {LARGE.source} Items[{stack(0, paper, 5)}] '
                        f'if data block {LARGE.target} Items[{stack(0, iron, 3)}] '
                        f'unless data block {LARGE.target} Items[{{{paper}}}]', results)

    def pick(damage, tag):
        return f'id:"minecraft:iron_pickaxe",tag:{{Damage:{damage},LargeSample:{tag}}}'

    matching = pick(45, 2)
    worn = pick(80, 2)
    different = pick(45, 3)
    set_items(server, ADVANCED.source)
    set_items(server, ADVANCED.target)
    set_items(server, ADVANCED.source, stack(0, matching, 1), stack(1, worn, 1), stack(2, different, 1))
    server.assert_state(f"large_advanced_combines_tag_matching_with_damage_threshold_{stage}",
                        f'if data block {ADVANCED.target} Items[{stack(0, matching, 1)}] '
                        f'if data block {ADVANCED.source} Items[{stack(1, worn, 1)}] '
                        f'if data block {ADVANCED.source} Items[{stack(2, different, 1)}] '
                        f'unless data block {ADVANCED.target} Items[{{{worn}}}] '
                        f'unless data block {ADVANCED.target} Items[{{{different}}}]', results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--forge-server", type=Path, required=True)
    parser.add_argument("--launch", type=Path, required=True)
    parser.add_argument("--jar", type=Path, required=True)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")
    root = ROOT / "build/port-runtime" / args.name
    root.mkdir()
    directory = root / "server"
    report = {"passed": False, "jarSha256": digest(args.jar), "assertions": [], "servers": [], "clients": {}}
    try:
        prepare_profile(args, directory, args.jar)

        def exercise(server, results):
            prepare(server, results)
            exercise_client(args, server, results, report)
            routes(server, results, "live")

        def reopen(server, results):
            load(server, results, "restart")
            exercise_client(args, server, results, report, reopen=True)
            routes(server, results, "restart")

        run_stage(args, directory, report, "edited", exercise)
        run_stage(args, directory, report, "reopened", reopen)
        report["passed"] = True
    finally:
        (root / "stock-filter-menu-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
