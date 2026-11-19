#!/usr/bin/env python3
"""Edit migrated item and fluid filters from a guarded client on a real Forge server."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_legacy_transfer_filters import empty_destination, fill, installed, inventory, items, load, pos, tank, wait_ticks
from packaged_redstone_filter_menus import PLAYER
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage


OFFHAND = f'{PLAYER} Inventory[{{Slot:-106b,id:"enderio:advanced_item_filter"}}]'
FLUID_FILTER = installed(8, "west", False)
ITEM_FILTER = installed(5, "east", False)
WATER = "minecraft:water"


def filter_state(path, kind):
    return f'{path}.tag."enderio:data"."enderio:{kind}_filter"'


def item_conditions(target, path):
    data = filter_state(path, "item")
    return (f'if data {target} {path}.tag{{PortFilter:"advanced"}} '
            f'if data {target} {data}{{isNbt:1b,compareLegacyTags:1b}} '
            f'unless data {target} {data}.isInvert '
            f'if data {target} {data}.items[0].value{{id:"minecraft:gold_ingot",Count:1,tag:{{MenuSample:71}}}} '
            f'if data {target} {data}.items[9].value{{id:"minecraft:iron_pickaxe",tag:{{Damage:27,Sample:42}}}}')


def fluid_conditions():
    data = filter_state(FLUID_FILTER, "fluid")
    return (f'if data block {FLUID_FILTER}.tag{{PortFilter:"fluid"}} '
            f'if data block {data}{{legacyMatching:1b,isNbt:0b,isInvert:0b}} '
            f'if data block {data}.fluids[0].value{{FluidName:"minecraft:water",Amount:1000}} '
            f'unless data block {data}.fluids[0].value.Tag '
            f'unless data block {data}.fluids[4].value.FluidName')


def exercise_client(args, server, results, report):
    output = ROOT / "build/port-evidence" / args.name
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
               "--launch", str(args.launch.resolve()), "--scenario", "transfer-filter-menus",
               "--output", str(output), "--server", f"127.0.0.1:{args.port}", "--timeout", "195"]
    load(server, results, "before_editing")
    server.command("gamerule spawnRadius 0")
    server.command("setworldspawn 4639 80 10")
    with (server.directory / "client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            server.command(f'item replace entity {PLAYER} hotbar.0 with enderio:advanced_item_filter{{PortFilter:"unchanged-main"}}')
            server.command(f'item replace entity {PLAYER} weapon.offhand from block {pos(4638, 14)} container.3')
            server.command(f'item replace entity {PLAYER} hotbar.1 with minecraft:gold_ingot{{MenuSample:71}} 7')
            server.command(f'item replace entity {PLAYER} hotbar.2 with minecraft:water_bucket')
            (output / "server-ready-item").touch()

            wait_signal(client, output, "item-edited")
            server.assert_state("only_the_offhand_filter_changes_after_editing", f'{item_conditions("entity", OFFHAND)} '
                                f'if data entity {PLAYER} Inventory[{{Slot:0b,id:"enderio:advanced_item_filter",'
                                f'tag:{{PortFilter:"unchanged-main"}}}}] '
                                f'unless data entity {PLAYER} Inventory[{{Slot:0b}}].tag."enderio:data" '
                                f'if data entity {PLAYER} Inventory[{{Slot:1b,id:"minecraft:gold_ingot",Count:7b,'
                                f'tag:{{MenuSample:71}}}}]', results)
            # Place the exact edited stack in the test route while retaining it in the player's save.
            server.command(f"data modify block {ITEM_FILTER} set from entity {OFFHAND}")
            server.command(f"tp {PLAYER} 4639.5 80 10.5 180 15")
            (output / "server-ready-fluid").touch()
            wait_signal(client, output, "fluid-edited")
            server.assert_state("installed_fluid_edit_keeps_legacy_mode_and_the_bucket", f'{fluid_conditions()} '
                                f'if data entity {PLAYER} Inventory[{{Slot:2b,id:"minecraft:water_bucket",Count:1b}}]', results)
            (output / "server-ready-complete").touch()
            if client.wait(timeout=35) != 0:
                raise AssertionError(f"Guarded client failed, see {output}")
        finally:
            stop_client(client)
            summary = output / "summary.json"
            if summary.is_file():
                report["client"] = json.loads(summary.read_text())


def verify_routes(server, results, stage):
    server.assert_state(f"exact_menu_item_and_fluid_settings_{stage}",
                        f'{item_conditions("block", ITEM_FILTER)} {fluid_conditions()}', results)
    allowed = '{Slot:0b,id:"minecraft:gold_ingot",Count:5b,tag:{MenuSample:71}}'
    denied = '{Slot:1b,id:"minecraft:gold_ingot",Count:3b,tag:{MenuSample:72}}'
    items(server, 4641, 5)
    items(server, 4638, 5, allowed, denied)
    server.assert_state(f"edited_advanced_filter_accepts_only_the_tagged_gold_sample_{stage}",
                        f'{inventory(4641, 5, allowed)} {inventory(4638, 5, denied)} '
                        f'unless data block {pos(4641, 5)} Items[{{tag:{{MenuSample:72}}}}]', results)

    empty_destination(server, 8, False)
    fill(server, 8, False, WATER, 880, None)
    server.assert_state(f"edited_installed_filter_transfers_all_untagged_water_{stage}",
                        f'{tank(4641, 8, False, WATER, 880)} {tank(4638, 8, False)} '
                        f'unless data block {pos(4641, 8)} Fluids.Tanks[0].Contents.Tag', results)
    empty_destination(server, 8, False)
    fill(server, 8, False, WATER, 420, 55)
    wait_ticks(server, 24)
    server.assert_state(f"edited_installed_filter_keeps_tagged_water_at_the_source_{stage}",
                        f'{tank(4638, 8, False, WATER, 420, 55)} {tank(4641, 8, False)}', results)


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
            exercise_client(args, server, results, report)
            verify_routes(server, results, "live")

        def reopen(server, results):
            load(server, results, "restart")
            verify_routes(server, results, "restart")

        run_stage(args, directory, report, "edited", exercise)
        run_stage(args, directory, report, "reopened", reopen)
        report["passed"] = True
    finally:
        (root / "transfer-filter-menu-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
