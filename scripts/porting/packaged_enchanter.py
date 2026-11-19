#!/usr/bin/env python3
"""Check native Enchanter collection, exact costs and restart on packaged Forge."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_crafter import stack
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_spawners import prepare_profile, run_stage


PLAYER = "EnderIOQA"
FRESH = "3450 80 8"
SAVED = "3454 80 8"
INPUTS = ','.join((stack(0, "writable_book"), stack(1, "quartz", 25), stack(2, "lapis_lazuli", 10)))
BOOK = 'id:"minecraft:enchanted_book",tag:{StoredEnchantments:[{id:"minecraft:sharpness",lvl:2s}]}'
BOOK_PLAYER = f'Count:1b,{BOOK}'


def ready(pos):
    return f'if data block {pos} {{Items:{{Items:[{INPUTS},{{Slot:3,Count:1,{BOOK}}}]}}}}'


def spent(pos):
    return (f'if data block {pos} {{Items:{{Items:[{stack(1, "quartz")},{stack(2, "lapis_lazuli", 4)}]}}}} '
            f'unless data block {pos} Items.Items[{{Slot:0}}] unless data block {pos} Items.Items[{{Slot:3}}]')


def prepare(server, results):
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    server.command("forceload add 3440 0 3455 15")
    server.assert_state("enchanter_chunk_is_loaded", f"if loaded {FRESH} if loaded {SAVED}", results)
    server.command("fill 3447 79 5 3455 79 14 minecraft:stone")
    server.command("setworldspawn 3450 80 10")
    server.command("scoreboard objectives add port_enchant dummy")
    for pos in (FRESH, SAVED):
        server.command(f"setblock {pos} enderio:enchanter")
        server.command(f"data merge block {pos} {{Items:{{Items:[{INPUTS}]}}}}")
    server.assert_state("both_native_enchanters_preview_sharpness_ii", f"{ready(FRESH)} {ready(SAVED)}", results)


def player_has_book(slot):
    return f'if data entity {PLAYER} Inventory[{{Slot:{slot}b,{BOOK_PLAYER}}}]'


def assert_books(server, results, name, count, conditions):
    server.command(f"execute store result score PortBooks port_enchant run clear {PLAYER} minecraft:enchanted_book 0")
    server.assert_state(name, f"{conditions} if score PortBooks port_enchant matches {count}", results)


def exercise(args, server, results, report, *, restarted, replant=None):
    phase = "verify" if restarted else "exercise"
    pos = SAVED if restarted else FRESH
    output = ROOT / "build/port-evidence" / f"{args.name}-{phase}-client"
    scenario = "legacy-enchanter" if replant else "enchanter"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", scenario, "--multiplayer-phase", phase, "--output", str(output),
               "--server", f"127.0.0.1:{args.port}", "--timeout", "155"]
    summary = output / "summary.json"
    with (server.directory / f"enchanter-{phase}-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            if replant:
                replant(server, results, client, output)
            if restarted:
                server.assert_state("player_rejoins_with_the_original_book_and_exact_xp",
                                    f'if data entity {PLAYER} {{XpLevel:19}} {player_has_book(9)}', results)
                for slot in range(9):
                    server.command(f"item replace entity {PLAYER} hotbar.{slot} with minecraft:dirt 64")
                for slot in range(1, 27):
                    server.command(f"item replace entity {PLAYER} inventory.{slot} with minecraft:dirt 64")
            else:
                server.command(f"experience set {PLAYER} 10 levels")
            x = int(pos.split()[0])
            server.command(f"tp {PLAYER} {x + 0.5} 80 10.5 180 22")
            (output / "server-ready").touch()
            wait_signal(client, output, "restricted-attempt")
            if restarted:
                assert_books(server, results, "full_inventory_does_not_spend_xp_or_ingredients", 1,
                             f'{ready(pos)} if data entity {PLAYER} {{XpLevel:19}} {player_has_book(9)}')
                server.command(f"item replace entity {PLAYER} hotbar.8 with minecraft:air")
            else:
                assert_books(server, results, "insufficient_xp_blocks_both_gestures_without_payment", 0,
                             f'{ready(pos)} if data entity {PLAYER} {{XpLevel:10}}')
                server.command(f"experience set {PLAYER} 30 levels")
            (output / "server-unlocked").touch()
            wait_signal(client, output, "collected")
            if restarted:
                assert_books(server, results, "shift_take_after_restart_pays_exactly_once", 2,
                             f'{spent(pos)} if data entity {PLAYER} {{XpLevel:8}} {player_has_book(8)} {player_has_book(9)}')
            else:
                assert_books(server, results, "normal_pickup_pays_exactly_once", 1,
                             f'{spent(pos)} if data entity {PLAYER} {{XpLevel:19}} {player_has_book(9)}')
            (output / "server-confirmed").touch()
            if client.wait(timeout=35) != 0:
                raise AssertionError(f"Enchanter client failed, see {summary}")
            capture = json.loads(summary.read_text())["client"]["captures"]["collected-enchanted-book"]
            if digest(Path(capture["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Client and server Ender IO jars differ")
        finally:
            stop_client(client)
            report["clients"][phase] = json.loads(summary.read_text()) if summary.exists() else None
            server.command(f"data get block {pos}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--launch", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")

    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "forge": FORGE, "jarSha256": digest(directory / "mods/enderio.jar"),
              "assertions": [], "servers": [], "clients": {}}
    try:
        def fresh(server, results):
            prepare(server, results)
            exercise(args, server, results, report, restarted=False)

        def reopened(server, results):
            server.assert_state("saved_recipe_is_ready_and_first_craft_stays_spent",
                                f"{ready(SAVED)} {spent(FRESH)}", results)
            exercise(args, server, results, report, restarted=True)

        def verify_spent(server, results):
            server.assert_state("both_crafts_stay_spent_after_a_full_restart", f"{spent(FRESH)} {spent(SAVED)}", results)

        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", reopened)
        run_stage(args, directory, report, "spent", verify_spent)
        report["passed"] = True
    finally:
        path = directory / "enchanter-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Enchanter passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
