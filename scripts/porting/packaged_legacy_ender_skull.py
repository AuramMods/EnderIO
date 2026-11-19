#!/usr/bin/env python3
"""Read placed skulls and original loot from a real Forge 6.2.18-beta save."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_ender_skull import HEAD, HEAD_DROP, WALL
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage
from packaged_starter_progression import PLAYER
from saved_world import read_data_file


CHEST = "3362 80 12"
FLOOR, WALL_POS = "3362 80 8", "3368 80 8"
NEW_FLOOR, NEW_WALL = "3373 80 8", "3378 80 8"
PLACED = ((FLOOR, f"{HEAD}[rotation=5]"), (WALL_POS, f"{WALL}[facing=east]"))
REPLANTED = ((NEW_FLOOR, f"{HEAD}[rotation=8]"), (NEW_WALL, f"{WALL}[facing=north]"))
ENTITY = "enderio:ender_skull"
MARKER = "PortLegacySkull"


def blocks(entries):
    return " ".join(f'if block {pos} {state} if data block {pos} {{id:"{ENTITY}"}}' for pos, state in entries)


def legacy_items(target, player=False):
    def item(index):
        slot = "" if player else f"Slot:{index - 1}b,"
        stack = f'{{{slot}id:"{HEAD}",Count:1b,tag:{{{MARKER}:{index}}}}}'
        return f"if data entity {target} Inventory[{stack}]" if player else f"if data block {target} Items[{stack}]"

    return " ".join(item(index) for index in (1, 2))


def legacy_fixture(server, results):
    server.command("forceload add 3344 0 3391 15")
    server.assert_state("forge6_loads_the_room_and_new_supports", f"if loaded {CHEST} if loaded {NEW_WALL}", results)
    for command in ("gamerule doMobSpawning false", "gamerule doDaylightCycle false", "gamerule spawnRadius 0", "time set noon",
                    "fill 3358 79 4 3383 79 15 minecraft:stone", "setworldspawn 3362 80 14",
                    "setblock 3367 80 8 minecraft:stone", "setblock 3378 80 9 minecraft:stone",
                    f"setblock {CHEST} minecraft:chest[facing=south]"):
        server.command(command)
    for slot, (pos, state) in enumerate(PLACED):
        server.command(f"setblock {pos} {state}")
        server.command(f"loot replace block {CHEST} container.{slot} mine {pos} minecraft:iron_pickaxe")
        server.assert_state(f"forge6_native_loot_for_skull_{slot}",
                            f'if data block {CHEST} Items[{{Slot:{slot}b,id:"{HEAD}",Count:1b}}]', results)
        server.command(f"data modify block {CHEST} Items[{{Slot:{slot}b}}].tag.{MARKER} set value {slot + 1}")
    server.assert_state("forge6_saves_both_skull_entities_and_both_original_tagged_drops",
                        f"{blocks(PLACED)} {legacy_items(CHEST)}", results)


def converted(server, results):
    server.assert_state("converted_world_keeps_original_rotation_facing_entities_and_loot",
                        f"{blocks(PLACED)} {legacy_items(CHEST)}", results)


def after_headless(server, results):
    for slot, (pos, _) in enumerate(PLACED, 2):
        server.command(f"loot replace block {CHEST} container.{slot} mine {pos} minecraft:iron_pickaxe")
        server.assert_state(f"converted_skull_{slot - 2}_still_yields_one_native_head",
                            f'if data block {CHEST} Items[{{Slot:{slot}b,id:"{HEAD}",Count:1b}}]', results)


def ready_at(server, evidence, marker, position, yaw):
    server.command(f"tp {PLAYER} {position} {yaw} 17")
    (evidence / f"server-{marker}").touch()


def use_originals(args, server, report):
    results = report["assertions"]
    server.command(f"item replace block {CHEST} container.8 with minecraft:iron_pickaxe")
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    summary = evidence / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "legacy-ender-skull", "--server", f"127.0.0.1:{args.port}", "--output", str(evidence), "--timeout", "190"]
    with (server.directory / "legacy-skull-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            ready_at(server, evidence, "chest", "3362.5 80 14.5", 180)
            wait_signal(client, evidence, "collected")
            server.assert_state("the_original_forge6_drops_move_to_survival_without_copies",
                                f"{legacy_items(PLAYER, player=True)} unless data block {CHEST} Items[0]", results)
            for label, pos, position, yaw in (("floor", FLOOR, "3362.5 80 11.5", 180),
                                               ("wall", WALL_POS, "3370.5 80 8.5", 90)):
                ready_at(server, evidence, f"mine-{label}", position, yaw)
                wait_signal(client, evidence, f"mined-{label}")
                server.assert_state(f"native_{label}_mining_removes_the_original_block", f"if block {pos} minecraft:air", results)
                server.command(f"execute as {HEAD_DROP} at @s run tp {PLAYER} ~ ~ ~")
                wait_signal(client, evidence, f"picked-up-{label}")
                server.assert_state(f"the_original_{label}_block_drop_is_collected", f"unless entity {HEAD_DROP}", results)
            ready_at(server, evidence, "place-floor", "3373.5 80 11.5", 180)
            wait_signal(client, evidence, "placed-floor")
            server.assert_state("original_forge6_floor_loot_places_at_native_rotation_8", blocks(REPLANTED[:1]), results)
            ready_at(server, evidence, "place-wall", "3378.5 80 5.5", 0)
            wait_signal(client, evidence, "placed-wall")
            server.assert_state("original_forge6_wall_loot_replants_facing_north", blocks(REPLANTED[1:]), results)
            (evidence / "server-done").touch()
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"Historical skull client failed; see {evidence}")
            for capture in json.loads(summary.read_text())["client"]["captures"].values():
                if digest(Path(capture["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                    raise AssertionError("Client and server have different Ender IO jars")
        finally:
            stop_client(client)
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def verify_player(directory, report):
    player, = (directory / WORLD / "playerdata").glob("*.dat")
    inventory = [stack for stack in read_data_file(player)["Inventory"] if stack["id"] == HEAD]
    if len(inventory) != 1 or inventory[0]["Count"] != 2 or MARKER in inventory[0].get("tag", {}):
        raise AssertionError(f"Saved heads {inventory} differ from the two loose converted block drops")
    report["savedPlayerHeads"] = inventory


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    for key in ("forge-server", "legacy-jar", "jar"):
        parser.add_argument(f"--{key}", type=Path, required=True)
    parser.add_argument("--launch", type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    directory.mkdir()
    legacy, port = directory / "legacy", directory / "port"
    prepare_profile(args, legacy, args.legacy_jar)
    prepare_profile(args, port, args.jar)
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(port / "mods/enderio.jar"),
              "legacySha256": digest(legacy / "mods/enderio.jar")}

    def exercise(server, results):
        converted(server, results)
        if args.launch:
            use_originals(args, server, report)
        else:
            after_headless(server, results)

    def reopen(server, results):
        if args.launch:
            server.assert_state("both_replanted_heads_and_emptied_source_locations_survive_restart",
                                f"{blocks(REPLANTED)} if block {FLOOR} minecraft:air if block {WALL_POS} minecraft:air "
                                f"unless data block {CHEST} Items[0] unless entity {HEAD_DROP}", results)
        else:
            extra = " ".join(f'if data block {CHEST} Items[{{Slot:{slot}b,id:"{HEAD}",Count:1b}}]' for slot in (2, 3))
            server.assert_state("both_original_heads_and_all_four_loot_stacks_survive_restart",
                                f"{blocks(PLACED)} {legacy_items(CHEST)} {extra}", results)

    try:
        run_stage(args, legacy, report, "legacy", legacy_fixture)
        shutil.copytree(legacy / WORLD, port / WORLD)
        run_stage(args, port, report, "converted", exercise)
        run_stage(args, port, report, "reopened", reopen)
        if args.launch:
            verify_player(port, report)
        report["passed"] = True
    finally:
        result = directory / "legacy-ender-skull-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Legacy Ender Skull passed={report['passed']}, {len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
