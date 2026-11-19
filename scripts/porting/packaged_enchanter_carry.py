#!/usr/bin/env python3
"""Mine, carry, replant and use a ready Enchanter on packaged Forge."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_crafter import stack
from packaged_enchanter import FRESH, PLAYER, exercise, ready, spent
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_spawners import prepare_profile, run_stage


BLOCK = "enderio:enchanter"
SOURCE = "3450 80 13"
INPUTS = ','.join((
    '{Slot:0,id:"minecraft:writable_book",Count:1,tag:{pages:["Survival carry fixture"]}}',
    stack(1, "quartz", 25),
    stack(2, "lapis_lazuli", 10),
))
CONTENTS = f'{{Size:4,Items:[{INPUTS}]}}'
CARRIED = f'{{id:"{BLOCK}",Count:1b,tag:{{"enderio:data":{{"enderio:container":{CONTENTS}}}}}}}'


def ready_with_book(pos):
    return f'{ready(pos)} if data block {pos} {{Items:{CONTENTS}}}'


def setup(server, results):
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    server.command("forceload add 3440 0 3455 31")
    server.assert_state("enchanter_fixture_chunks_are_loaded", f"if loaded {SOURCE} if loaded {FRESH}", results)
    server.command("fill 3447 79 5 3455 79 19 minecraft:stone")
    server.command("setworldspawn 3450 80 10")
    server.command("scoreboard objectives add port_enchant dummy")
    server.command(f"setblock {SOURCE} {BLOCK}")
    server.command(f"data merge block {SOURCE} {{Items:{CONTENTS}}}")
    server.assert_state("source_enchanter_has_tagged_inputs_and_sharpness_ii_preview", ready_with_book(SOURCE), results)


def carry(args, server, results, report):
    output = ROOT / "build/port-evidence" / f"{args.name}-carry-client"
    summary = output / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "enchanter-carry", "--output", str(output), "--server", f"127.0.0.1:{args.port}",
               "--timeout", "185"]
    nearby = '@e[type=minecraft:item,x=3450.5,y=80,z=13.5,distance=..3]'
    drop = nearby[:-1] + f',nbt={{Item:{{id:"{BLOCK}"}}}},sort=nearest,limit=1]'
    with (server.directory / "enchanter-carry-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            server.wait(f"{PLAYER} joined the game", timeout=150)
            server.command(f'item replace entity {PLAYER} hotbar.0 with minecraft:diamond_pickaxe'
                           '{Enchantments:[{id:"minecraft:efficiency",lvl:5s}]}')
            server.command(f"tp {PLAYER} 3450.5 80 16.5 180 22")
            wait_signal(client, output, "mined")
            server.assert_state("survival_mining_drops_an_enchanter_with_exact_inputs",
                                f"if block {SOURCE} minecraft:air if data entity {drop} {{Item:{CARRIED}}}", results)
            server.command(f"data get entity {drop} Item")
            server.command(f"execute at {drop} run tp {PLAYER} ~ ~ ~")
            server.assert_state("pick_up_keeps_tagged_inputs_inside_one_machine_without_other_loot",
                                f"unless entity {nearby} if data entity {PLAYER} Inventory[{CARRIED}] "
                                f'unless data entity {PLAYER} Inventory[{{id:"minecraft:enchanted_book"}}]', results)
            server.command(f"tp {PLAYER} 3450.5 80 10.5 180 22")
            wait_signal(client, output, "placement")
            server.assert_state("placement_restores_inputs_and_preview_and_consumes_carried_machine",
                                f'{ready_with_book(FRESH)} unless data entity {PLAYER} Inventory[{{id:"{BLOCK}"}}]', results)
            (output / "server-verified").touch()
            if client.wait(timeout=35) != 0:
                raise AssertionError(f"Enchanter carry client failed; see {summary}")
            capture = json.loads(summary.read_text())["client"]["captures"]["enchanter-replaced"]
            if digest(Path(capture["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Client and server Ender IO jars differ")
        finally:
            stop_client(client)
            report["clients"]["carry"] = json.loads(summary.read_text()) if summary.exists() else None


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
        parser.error("name must be one directory name")

    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "forge": FORGE, "jarSha256": digest(directory / "mods/enderio.jar"),
              "assertions": [], "servers": [], "clients": {}}
    try:
        def fresh(server, results):
            setup(server, results)
            carry(args, server, results, report)

        def reopened(server, results):
            server.assert_state("replanted_enchanter_retains_tagged_inputs_and_preview_after_restart",
                                f"if block {SOURCE} minecraft:air {ready_with_book(FRESH)}", results)
            exercise(args, server, results, report, restarted=False)

        def paid(server, results):
            server.assert_state("one_payment_persists_after_the_second_restart",
                                f"if block {SOURCE} minecraft:air {spent(FRESH)}", results)

        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", reopened)
        run_stage(args, directory, report, "paid", paid)
        report["passed"] = True
    finally:
        path = directory / "enchanter-carry-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Enchanter carry passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
