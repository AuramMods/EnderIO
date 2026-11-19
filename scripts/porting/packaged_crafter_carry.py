#!/usr/bin/env python3
"""Verify survival carriage of a Crafter with paid crafting remainders on packaged Forge."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_crafter import CAKE, CAPACITOR, CRAFT_COST, grid, seed, stack, state
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_spawners import prepare_profile, run_stage


PLAYER = "EnderIOQA"
BLOCK = "enderio:crafter"
SOURCE = "3078 80 8"
DESTINATION = "3078 80 13"
HOPPER = "3078 79 13"
BARREL = "3078 78 13"
IO = '{Modes:{east:"disable",south:"pull"}}'
QUEUE = '{id:"minecraft:bucket",Count:1}'
CAKE_SLOTS = [CAPACITOR, stack(10, "cake"), *grid(11, CAKE), stack(20, "cake")]
CONTROLS = f'IOConfig:{IO},RedstoneControl:"never_active"'


def waiting(pos):
    separate_buckets = " ".join(f"if data block {pos} OutputBuffer[{i}]" for i in range(3))
    no_inputs = " ".join(f"unless data block {pos} Items.Items[{{Slot:{slot}}}]" for slot in range(1, 10))
    return (f"{state(pos, CRAFT_COST, *CAKE_SLOTS)} if data block {pos} {{{CONTROLS},OutputBuffer:[{QUEUE}]}} "
            f"{separate_buckets} unless data block {pos} OutputBuffer[3] {no_inputs}")


def setup(server, results):
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    server.command("forceload add 3072 0 3087 15")
    server.assert_state("crafter_fixture_chunk_loaded", f"if loaded {SOURCE} if loaded {DESTINATION}", results)
    server.command("fill 3074 79 5 3083 79 17 minecraft:stone")
    server.command("setworldspawn 3078 80 11")
    seed(server, SOURCE, 2 * CRAFT_COST, [*grid(1, CAKE), *grid(11, CAKE)])
    server.assert_state("crafter_pays_for_one_cake_and_has_pending_buckets",
                        f"{state(SOURCE, CRAFT_COST, *CAKE_SLOTS)} if data block {SOURCE} OutputBuffer[2]", results)
    server.command(f"data merge block {SOURCE} {{{CONTROLS}}}")
    server.assert_state("source_has_saved_queue_slots_charge_and_controls", waiting(SOURCE), results)


def carried_stack():
    contents = f'{{Size:21,Items:[{",".join(CAKE_SLOTS)}]}}'
    return (f'{{id:"{BLOCK}",Count:1b,tag:{{"enderio:data":{{"enderio:energy":{CRAFT_COST},'
            f'"enderio:io_config":{IO},"enderio:redstone_control":"never_active",'
            f'"enderio:container":{contents},"enderio:crafter_output_buffer":[{QUEUE}]}}}}}}')


def exercise(args, server, results, report):
    output = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "crafter-carry", "--output", str(output), "--server", f"127.0.0.1:{args.port}",
               "--timeout", "185"]
    summary_path = output / "summary.json"
    nearby = '@e[type=minecraft:item,x=3078.5,y=80,z=8.5,distance=..3]'
    drop = nearby[:-1] + f',nbt={{Item:{{id:"{BLOCK}"}}}},sort=nearest,limit=1]'
    with (server.directory / "crafter-carry-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            server.wait(f"{PLAYER} joined the game", timeout=150)
            server.command(f'item replace entity {PLAYER} hotbar.0 with minecraft:diamond_pickaxe'
                           '{Enchantments:[{id:"minecraft:efficiency",lvl:5s}]}')
            server.command(f"tp {PLAYER} 3078.5 80 11.5 180 22")
            wait_signal(client, output, "mined")
            server.assert_state("mining_drops_one_crafter", f"if block {SOURCE} minecraft:air if entity {drop}", results)
            server.command(f"data get entity {drop} Item")
            server.assert_state("native_drop_preserves_the_paid_queue_and_all_machine_data",
                                f"if data entity {drop} {{Item:{carried_stack()}}} "
                                f'if data entity {drop} Item.tag."enderio:data"."enderio:crafter_output_buffer"[2] '
                                f'unless data entity {drop} Item.tag."enderio:data"."enderio:crafter_output_buffer"[3]', results)
            server.command(f"execute at {drop} run tp {PLAYER} ~ ~ ~")
            server.assert_state("survival_pickup_preserves_carried_state_without_other_drops",
                                f"unless entity {nearby} if data entity {PLAYER} Inventory[{carried_stack()}]", results)
            server.command(f"tp {PLAYER} 3078.5 80 15.5 180 22")
            wait_signal(client, output, "placement")
            server.assert_state("normal_placement_restores_queue_and_consumes_one_stack",
                                f'{waiting(DESTINATION)} unless data entity {PLAYER} Inventory[{{id:"{BLOCK}"}}]', results)
            (output / "server-verified").touch()
            if client.wait(timeout=35) != 0:
                raise AssertionError(f"Crafter carry client failed; see {output}")
            frame = json.loads(summary_path.read_text())["client"]["captures"]["crafter-replaced"]
            if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Client and server Ender IO jars differ")
        finally:
            stop_client(client)
            report["client"] = json.loads(summary_path.read_text()) if summary_path.exists() else None


def drained(server, results):
    server.assert_state("one_cake_and_three_buckets_recovered_with_exact_remaining_charge",
                        f'{state(DESTINATION, CRAFT_COST, CAPACITOR, *grid(11, CAKE), stack(20, "cake"))} '
                        f"if block {SOURCE} minecraft:air if data block {DESTINATION} {{{CONTROLS}}} "
                        f'if data block {BARREL} {{Items:[{{Slot:0b,id:"minecraft:cake",Count:1b}},'
                        '{Slot:1b,id:"minecraft:bucket",Count:3b}]} '
                        f"unless data block {BARREL} Items[2] unless data block {HOPPER} Items[0] "
                        f"unless data block {DESTINATION} OutputBuffer unless data block {DESTINATION} Items.Items[{{Slot:10}}]",
                        results, timeout=20)


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
              "assertions": [], "servers": []}
    try:
        def fresh(server, results):
            setup(server, results)
            exercise(args, server, results, report)

        def reopened(server, results):
            server.assert_state("replanted_queue_survives_a_full_restart", waiting(DESTINATION), results)
            server.command(f"setblock {BARREL} minecraft:barrel")
            server.command(f"setblock {HOPPER} minecraft:hopper[facing=down]")
            drained(server, results)

        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", reopened)
        run_stage(args, directory, report, "drained", drained)
        report["passed"] = True
    finally:
        path = directory / "crafter-carry-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Crafter carry passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
