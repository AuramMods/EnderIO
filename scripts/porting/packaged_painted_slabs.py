#!/usr/bin/env python3
"""Move real Forge 6 painted slabs through conversion, survival placement and restart."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage


SLAB = "enderio:painted_slab"
SOURCE = "2552 80 8"
OLD = "2552 80 12"
NEW = "2557 80 12"
BARREL = "2554 80 8"
PLAYER = "EnderIOQA"
GOLD = "minecraft:gold_block"
DIAMOND = "minecraft:diamond_block"


def painted(pos, shape, lower=None, upper=None):
    conditions = [f"if block {pos} {SLAB}[type={shape}]"]
    for key, paint in (("Paint", lower), ("Paint2", upper)):
        if paint is None:
            conditions.append(f"unless data block {pos} {key}")
        else:
            conditions.append(f'if data block {pos} {{{key}:"{paint}"}}')
    return " ".join(conditions)


def mixed(pos):
    return painted(pos, "double", GOLD, DIAMOND)


def item(slot, metadata):
    return f'if data block {BARREL} Items[{{Slot:{slot}b,Count:1b,id:"{SLAB}",tag:{{{metadata}}}}}]'


def old_loot():
    return " ".join(item(slot, f'BlockEntityTag:{{{key}:"{paint}"}}')
                    for slot, key, paint in ((0, "Paint", GOLD), (1, "Paint2", DIAMOND)))


def new_loot(first_slot):
    return " ".join(item(slot, f'"enderio:data":{{"enderio:block_paint":{{paint:"{paint}"}}}}')
                    for slot, paint in ((first_slot, GOLD), (first_slot + 1, DIAMOND)))


def extract(server, pos, slot):
    server.command(f"loot replace block {BARREL} container.{slot} 2 mine {pos} minecraft:diamond_axe")


def legacy_fixture(server, results):
    server.command("forceload add 2544 0 2575 31")
    server.assert_state("forge6_fixture_chunks_loaded", f"if loaded {SOURCE} if loaded {OLD} if loaded {NEW}", results)
    server.command("gamerule doMobSpawning false")
    server.command("gamerule doDaylightCycle false")
    server.command("gamerule spawnRadius 0")
    server.command("time set noon")
    server.command("fill 2549 79 5 2561 79 20 minecraft:stone")
    server.command("setworldspawn 2552 80 14")
    server.command("setblock 2552 81 12 minecraft:stone")
    server.command(f'setblock {SOURCE} {SLAB}[type=double]{{Paint:"{GOLD}",Paint2:"{DIAMOND}"}}')
    server.command(f"setblock {BARREL} minecraft:barrel")
    extract(server, SOURCE, 0)
    server.assert_state("forge6_world_and_real_loot_use_distinct_half_tags", f"{mixed(SOURCE)} {old_loot()}", results)


def exercise_client(args, server, report):
    results = report["assertions"]
    output = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "painted-slabs", "--output", str(output), "--server", f"127.0.0.1:{args.port}", "--timeout", "170"]
    with (server.directory / "painted-slabs-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            for slot in range(4):
                server.command(f"item replace entity {PLAYER} hotbar.{slot} from block {BARREL} container.{slot}")

            def place(name, condition, consumed_slot):
                (output / f"server-{name}").touch()
                wait_signal(client, output, name)
                server.assert_state(name.replace("-", "_"),
                                    f"{condition} unless data entity {PLAYER} Inventory[{{Slot:{consumed_slot}b}}]", results)

            server.command(f"tp {PLAYER} 2552.5 80 14.5 180 15")
            place("old-top", painted(OLD, "top", upper=DIAMOND), 1)
            place("old-bottom", mixed(OLD), 0)
            server.command(f"tp {PLAYER} 2557.5 80 14.5 180 15")
            place("new-bottom", painted(NEW, "bottom", lower=GOLD), 2)
            place("new-top", mixed(NEW), 3)
            extract(server, OLD, 4)
            extract(server, NEW, 6)
            server.assert_state("both_placement_orders_yield_the_same_two_typed_drops", f"{new_loot(4)} {new_loot(6)}", results)
            server.command(f"setblock 2552 81 12 minecraft:air")
            server.command(f"tp {PLAYER} 2555 80 18.5 180 9")
            (output / "server-verified").touch()
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"Painted slabs client failed; see {output}")
            summary = json.loads((output / "summary.json").read_text())
            frame = summary["client"]["captures"]["mixed-painted-slabs"]
            if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Client and server Ender IO jars differ")
        finally:
            stop_client(client)
            summary = output / "summary.json"
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--legacy-jar", required=True, type=Path)
    parser.add_argument("--launch", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")

    directory = ROOT / "build/port-runtime" / args.name
    directory.mkdir()
    legacy = directory / "legacy"
    port = directory / "port"
    prepare_profile(args, legacy, args.legacy_jar)
    prepare_profile(args, port, args.jar)
    report = {"passed": False, "forge": FORGE, "jarSha256": digest(port / "mods/enderio.jar"),
              "legacySha256": digest(legacy / "mods/enderio.jar"), "assertions": [], "servers": []}
    try:
        run_stage(args, legacy, report, "legacy", legacy_fixture)
        shutil.copytree(legacy / WORLD, port / WORLD)

        def converted(server, results):
            server.assert_state("copied_save_keeps_the_block_and_imports_both_legacy_items",
                                f"{mixed(SOURCE)} {new_loot(0)} unless data block {BARREL} Items[].tag.BlockEntityTag", results)
            extract(server, SOURCE, 2)
            server.assert_state("converted_slab_loot_writes_both_typed_colors", new_loot(2), results)
            exercise_client(args, server, report)

        def reopened(server, results):
            server.assert_state("all_three_mixed_slabs_and_all_loot_survive_restart",
                                f"{mixed(SOURCE)} {mixed(OLD)} {mixed(NEW)} {new_loot(0)} "
                                f"{new_loot(2)} {new_loot(4)} {new_loot(6)}", results)

        run_stage(args, port, report, "converted", converted)
        run_stage(args, port, report, "reopened", reopened)
        report["passed"] = True
    finally:
        path = directory / "painted-slabs-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Painted slabs passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
