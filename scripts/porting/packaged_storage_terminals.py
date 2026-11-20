#!/usr/bin/env python3
"""Exercise native storage screens in a converted world or with crafted RS conduits."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_energy_progression import conduit_stack
from packaged_item_progression import bundle
from packaged_legacy_migration import assert_probe, storage_operation
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage


PLAYER = "EnderIOQA"
ROUTES = (("ae", 4, 1), ("rs", 13, 2))
CHUNKS = "1072 0 1103 15"
RS_HEAD, RS_TAIL = "1087 80 13", "1088 80 13"


def check_route(server, results, name, route, count):
    assert_probe(server, name, f"port_storage_native check_route_items {route} {count}",
                 "Native route item count verified", results)


def setup(server, results, stock):
    server.command(f"forceload add {CHUNKS}")
    server.assert_state("storage_chunks_are_loaded", "if loaded 1085 80 4 if loaded 1089 80 13", results)
    server.command("gamerule doDaylightCycle false")
    server.command("time set noon")
    server.command("fill 1081 79 2 1090 79 16 minecraft:stone_bricks")
    server.command("fill 1082 80 3 1082 81 14 minecraft:bookshelf")
    server.command("gamerule spawnRadius 0")
    server.command("setworldspawn 1083 80 8")
    if stock:
        server.command("gamerule doMobSpawning false")
        server.command("setblock 1084 80 10 minecraft:crafting_table")
        server.command("setblock 1086 80 13 refinedstorage:creative_controller")
        server.command("setblock 1089 80 13 refinedstorage:disk_drive")
        server.assert_state("both_crafted_conduit_positions_start_empty",
                            f"if block {RS_HEAD} minecraft:air if block {RS_TAIL} minecraft:air", results)
    else:
        storage_operation(server, "native_ae2_terminal_fixture", "place_terminal", "Native AE2 terminal placed", results)


def place_grid(server, results, client, output):
    (output / "server-rs-place").touch()
    wait_signal(client, output, "rs-placed")
    server.assert_state("survival_grid_placement_consumes_the_item",
                        f'if block 1085 80 13 refinedstorage:grid unless data entity {PLAYER} '
                        'Inventory[{id:"refinedstorage:grid"}]', results)


def check_rs_path(server, results, name):
    assert_probe(server, name, "port_storage_native check_rs_route",
                 "Native RS grid and remote drive share the crafted route", results)


def craft_stock(server, results, client, output):
    server.command(f"item replace entity {PLAYER} hotbar.3 with enderio:conduit_binder 7")
    server.command(f"item replace entity {PLAYER} hotbar.4 with refinedstorage:cable 4")
    server.command(f"item replace entity {PLAYER} hotbar.5 with minecraft:iron_ingot{{PortRoute:13}} 5")
    server.command(f"item replace entity {PLAYER} hotbar.8 with refinedstorage:grid")
    server.command(f"tp {PLAYER} 1084.5 80 12.5 180 17")
    (output / "server-table").touch()
    wait_signal(client, output, "crafted")
    server.assert_state("stock_recipe_consumes_six_binder_and_three_cables_and_returns_three_rs_conduits",
                        f'if data entity {PLAYER} {{Inventory:[{conduit_stack("rs", 3)},'
                        '{id:"enderio:conduit_binder",Count:1b},{id:"refinedstorage:cable",Count:1b}]}', results)
    server.command(f"tp {PLAYER} 1087.5 80 15.5 180 17")
    (output / "server-place-conduits").touch()
    wait_signal(client, output, "conduits-placed")
    server.assert_state("two_crafted_conduits_join_across_the_chunk_edge_and_one_stays_in_inventory",
                        f'{bundle(RS_HEAD, "rs")} {bundle(RS_TAIL, "rs")} '
                        f'if data block {RS_HEAD} Connections[0][{{Side:"east",Status:"connected_conduit"}}] '
                        f'if data block {RS_TAIL} Connections[0][{{Side:"west",Status:"connected_conduit"}}] '
                        f'if data entity {PLAYER} Inventory[{conduit_stack("rs", 1)}]', results)


def play_stock(server, results, client, output, reopen):
    if not reopen:
        craft_stock(server, results, client, output)
    server.command(f"tp {PLAYER} 1083.5 80 13.5 -90 18")
    if not reopen:
        place_grid(server, results, client, output)
    stage = "reopened" if reopen else "fresh"
    check_rs_path(server, results, f"{stage}_native_grid_and_disk_share_the_crafted_route")
    if not reopen:
        storage_operation(server, "mount_and_seed_a_fresh_native_rs_disk", "seed_rs_disk 37",
                          "Native RS disk mounted and loaded with tagged items", results)
    initial, offer, destination = (21, 3, 6) if reopen else (37, 5, 2)
    check_route(server, results, f"{stage}_physical_disk_before_open", 13, initial)
    if reopen:
        server.command(f"item replace entity {PLAYER} hotbar.5 with minecraft:iron_ingot{{PortRoute:13}} {offer}")
    (output / "server-rs").touch()
    wait_signal(client, output, "rs-deposited")
    check_route(server, results, f"{stage}_native_deposit_reaches_the_remote_disk", 13, initial + offer)
    (output / "server-rs-deposited").touch()
    wait_signal(client, output, "rs-done")
    half = (initial + offer) // 2
    check_route(server, results, f"{stage}_physical_disk_keeps_the_exact_remainder", 13, half)
    inventory = f'if data entity {PLAYER} Inventory[{{Slot:{destination}b,id:"minecraft:iron_ingot",Count:{half}b,tag:{{PortRoute:13}}}}] '
    if reopen:
        inventory += f'if data entity {PLAYER} Inventory[{{Slot:2b,id:"minecraft:iron_ingot",Count:21b,tag:{{PortRoute:13}}}}] '
    server.assert_state(f"{stage}_survival_inventory_keeps_exact_withdrawals_and_the_spare_conduit",
                        inventory + f'unless data entity {PLAYER} Inventory[{{Slot:5b}}] '
                        f'if data entity {PLAYER} Inventory[{conduit_stack("rs", 1)}]', results)


def exercise(args, server, results, report, phase):
    reopen = phase == "verify"
    if not reopen:
        setup(server, results, args.stock)
    elif args.stock:
        check_rs_path(server, results, "cold_start_recovers_the_route_before_player_login")
        check_route(server, results, "cold_start_recovers_the_physical_disk_balance", 13, 21)
    output = ROOT / "build/port-evidence" / f"{args.name}-{phase}"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
               "--launch", str(args.launch.resolve()), "--scenario", "rs-progression" if args.stock else "storage-terminals", "--output", str(output),
               "--server", f"127.0.0.1:{args.port}", "--multiplayer-phase", phase, "--timeout", "175"]
    with (server.directory / f"storage-client-{phase}.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            if not reopen:
                server.command(f"clear {PLAYER}")
            if args.stock:
                play_stock(server, results, client, output, reopen)
            else:
                for name, marker, destination in ROUTES:
                    check_route(server, results, f"{name}_{phase}_remote_storage_before_open", marker, 21 if reopen else 37)
                    if not reopen:
                        server.command(f"item replace entity {PLAYER} hotbar.0 with minecraft:iron_ingot{{PortRoute:{marker}}} 5")
                    server.command(f"tp {PLAYER} 1083.5 80 {marker + 0.5} -90 18")
                    if name == "rs":
                        if not reopen:
                            server.command(f"item replace entity {PLAYER} hotbar.8 with refinedstorage:grid")
                            place_grid(server, results, client, output)
                        assert_probe(server, f"{phase}_rs_grid_joins_its_controller", "port_storage_native check_grid",
                                     "Native RS grid connects to its controller", results)
                    (output / f"server-{name}").touch()
                    if not reopen:
                        wait_signal(client, output, f"{name}-deposited")
                        check_route(server, results, f"{name}_native_deposit_reaches_the_remote_media", marker, 42)
                        (output / f"server-{name}-deposited").touch()
                    wait_signal(client, output, f"{name}-done")
                    check_route(server, results, f"{name}_{phase}_exact_remote_remainder", marker, 21)
                    server.assert_state(f"{name}_{phase}_exact_survival_player_inventory",
                                        f'if data entity {PLAYER} Inventory[{{Slot:{destination}b,id:"minecraft:iron_ingot",'
                                        f'Count:21b,tag:{{PortRoute:{marker}}}}}] unless data entity {PLAYER} Inventory[{{Slot:0b}}]',
                                        results)
                check_route(server, results, f"{phase}_dense_ae_cell_is_unchanged", 7, 37)
                check_route(server, results, f"{phase}_mixed_ae_cell_is_unchanged", 10, 37)
            (output / "server-verified").touch()
            if client.wait(timeout=25) != 0:
                raise AssertionError(f"Storage client failed during {phase}, see {output}")
            summary = json.loads((output / "summary.json").read_text())
            name, state = ("rs" if args.stock else "ae"), ("reopened" if reopen else "initial")
            mods = summary["client"]["captures"][f"{name}-{state}"]["loadedMods"]
            for mod in ("enderio", "ae2", "guideme", "refinedstorage"):
                path = Path(mods[mod]["file"])
                if digest(path) != report["serverJarHashes"][path.name]:
                    raise AssertionError(f"Client and server loaded different {mod} artifacts")
        finally:
            stop_client(client)
            summary = output / "summary.json"
            report["clients"][phase] = json.loads(summary.read_text()) if summary.exists() else None
    if args.stock and not reopen:
        server.command("setworldspawn 0 80 0")
        server.command(f"forceload remove {CHUNKS}")
        server.assert_state("both_crafted_conduit_chunks_are_evicted", f"unless loaded {RS_HEAD} unless loaded {RS_TAIL}", results, timeout=45)
        server.command(f"forceload add {CHUNKS}")
        check_rs_path(server, results, "chunk_reload_recovers_the_crafted_rs_route")
        check_route(server, results, "chunk_reload_keeps_twenty_one_tagged_ingots_on_the_physical_disk", 13, 21)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--launch", required=True, type=Path)
    fixture = parser.add_mutually_exclusive_group(required=True)
    fixture.add_argument("--world", type=Path)
    fixture.add_argument("--stock", action="store_true", help="Craft and place two Refined Storage conduits in a fresh world")
    parser.add_argument("--probe", required=True, type=Path)
    parser.add_argument("--mod", action="append", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    if args.world:
        shutil.copytree(args.world, directory / WORLD)
    for mod in [*args.mod, args.probe]:
        shutil.copyfile(mod, directory / "mods" / mod.name)
    report = {"passed": False, "assertions": [], "servers": [], "clients": {},
              "serverJarHashes": {mod.name: digest(mod) for mod in sorted((directory / "mods").glob("*.jar"))}}
    try:
        for phase in ("exercise", "verify"):
            run_stage(args, directory, report, phase, lambda server, results: exercise(args, server, results, report, phase))
        report["passed"] = True
    finally:
        (directory / "storage-terminal-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
