#!/usr/bin/env python3
"""Exercise four painted building blocks over packaged Forge multiplayer."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_painting_machine import PLAYER, paint_and_extract, paint_tag
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage


SAMPLES = (
    (2400, "stairs", "spruce_stairs", "emerald_block", "axe"),
    (2407, "wall", "brick_wall", "gold_block", "pickaxe"),
    (2414, "crafting_table", "crafting_table", "diamond_block", "axe"),
    (2421, "glowstone", "glowstone", "obsidian", "pickaxe"),
)
BARREL = "2400 80 13"
STRAIGHT = "[facing=north,half=bottom,shape=straight]"
CORNER = "[facing=north,half=bottom,shape=outer_left]"
SINGLE = "[up=true,west=low,east=none]"
JOINED = "[up=false,west=low,east=low]"
GLOWSTONE = "2421 80 8"


def painted(slot, properties="", z=8):
    x, kind, _, paint, _ = SAMPLES[slot]
    pos = f"{x} 80 {z}"
    return f'if block {pos} enderio:painted_{kind}{properties} if data block {pos} {{Paint:"minecraft:{paint}"}}'


def loot_state():
    return " ".join(f'if data block {BARREL} Items[{{Slot:{slot}b,Count:1b,id:"enderio:painted_{kind}",{paint_tag(paint)}}}]'
                    for slot, (_, kind, _, paint, _) in enumerate(SAMPLES))


def load(server, results, name):
    server.command("forceload add 2384 0 2431 15")
    server.assert_state(name, f"if loaded 2400 80 8 if loaded {GLOWSTONE}", results)


def build_scene(server):
    server.command("gamerule doMobSpawning false")
    server.command("gamerule doDaylightCycle false")
    server.command("gamerule spawnRadius 0")
    server.command("time set noon")
    server.command("fill 2397 79 2 2425 79 14 minecraft:stone_bricks")
    server.command("setworldspawn 2400 80 12")
    server.command(f"setblock {BARREL} minecraft:barrel")
    server.command("setblock 2406 80 8 minecraft:cobblestone_wall")


def setup(server, results):
    load(server, results, "fixture_chunks_loaded")
    build_scene(server)
    for x, kind, template, paint, _ in SAMPLES:
        paint_and_extract(server, results, f"{x} 80 3", f"{x} 79 3", kind, template, paint)


def waterlogged(properties, wet):
    return f"{properties[:-1]},waterlogged=true]" if wet else properties


def exercise(server, results, client, output, *, legacy_loot=None):
    wet = legacy_loot is not None
    for slot, (x, kind, _, _, tool) in enumerate(SAMPLES):
        source = f"{legacy_loot} container.{slot}" if wet else f"{x} 79 3 container.0"
        server.command(f"item replace entity {PLAYER} hotbar.{slot} from block {source}")
        server.command(f"item replace block {source} with minecraft:air")
        if slot == 3:
            server.command("time set midnight")
        server.command(f"tp {PLAYER} {x + 0.5} 80 11.5 180 24")
        (output / f"server-place-{slot}").touch()
        wait_signal(client, output, f"placed-{slot}")
        properties = waterlogged((STRAIGHT, SINGLE, "", "")[slot], wet and slot < 2)
        server.assert_state(f"survival_places_the_painted_{kind}",
                            f"{painted(slot, properties)} unless data entity {PLAYER} Inventory[{{Slot:{slot}b}}]", results)
        (output / f"server-view-{slot}").touch()
        wait_signal(client, output, f"viewed-{slot}")
        if slot == 0:
            server.command("setblock 2400 80 7 minecraft:spruce_stairs[facing=west,half=bottom]")
            server.assert_state("native_neighbor_converts_painted_stairs_to_the_outer_corner",
                                painted(0, waterlogged(CORNER, wet)), results)
        if slot == 1:
            server.command("setblock 2408 80 8 minecraft:cobblestone_wall")
            server.assert_state("both_vanilla_walls_connect_to_the_painted_wall_and_lower_its_post",
                                f"{painted(1, waterlogged(JOINED, wet))} if block 2406 80 8 minecraft:cobblestone_wall[east=low] "
                                "if block 2408 80 8 minecraft:cobblestone_wall[west=low]", results)
        if slot < 2:
            (output / f"server-updated-{slot}").touch()
            wait_signal(client, output, f"updated-{slot}")
        if slot == 2:
            server.command(f"recipe give {PLAYER} minecraft:oak_slab")
            server.command(f"item replace entity {PLAYER} hotbar.8 with minecraft:oak_planks 3")
            (output / "server-craft").touch()
            wait_signal(client, output, "crafted")
            server.assert_state("painted_crafting_table_consumes_three_planks_and_crafts_six_slabs",
                                f'if data entity {PLAYER} Inventory[{{id:"minecraft:oak_slab",Count:6b}}] '
                                f'unless data entity {PLAYER} Inventory[{{id:"minecraft:oak_planks"}}]', results)
        server.command(f"loot replace block {BARREL} container.{slot} mine {x} 80 8 minecraft:diamond_{tool}")
    server.assert_state("native_loot_preserves_all_four_original_paints", loot_state(), results)
    (output / "server-done").touch()


def reopen(server, results, client, output, *, wet=False):
    server.assert_state("the_player_keeps_the_six_crafted_slabs_after_reconnect",
                        f'if data entity {PLAYER} Inventory[{{id:"minecraft:oak_slab",Count:6b}}] '
                        f'unless data entity {PLAYER} Inventory[{{id:"minecraft:oak_planks"}}]', results)
    server.command(f"tp {PLAYER} 2421.5 80 11.5 180 24")
    (output / "server-relight").touch()
    wait_signal(client, output, "relit")
    server.command(f"setblock {GLOWSTONE} minecraft:air")
    server.assert_state("removal_leaves_the_saved_glowstone_loot_intact",
                        f"if block {GLOWSTONE} minecraft:air {loot_state()}", results)
    (output / "server-unlit").touch()
    wait_signal(client, output, "unlit")
    server.command("setblock 2400 80 7 minecraft:air")
    server.command("setblock 2406 80 8 minecraft:air")
    stair = painted(0, waterlogged(STRAIGHT, wet))
    wall = painted(1, waterlogged("[up=true,west=none,east=low]", wet))
    server.assert_state("neighbor_removal_restores_straight_stairs_and_the_wall_post",
                        f"{stair} {wall}", results)
    (output / "server-done").touch()


def run_client(args, server, report, phase, scenario, *, wet=False):
    output = ROOT / "build/port-evidence" / f"{args.name}-{phase}"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "painted-building-wet" if wet else "painted-building",
               "--output", str(output), "--server", f"127.0.0.1:{args.port}",
               "--multiplayer-phase", phase, "--timeout", "185"]
    with (server.directory / f"painted-building-client-{phase}.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            scenario(server, report["assertions"], client, output)
            if client.wait(timeout=25) != 0:
                raise AssertionError(f"Painted building client failed; see {output}")
            summary = json.loads((output / "summary.json").read_text())
            frame = summary["client"]["captures"]["glowstone-placed" if phase == "exercise" else "glowstone-restored"]
            if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Client and server loaded different Ender IO jars")
        finally:
            stop_client(client)
            summary = output / "summary.json"
            report["clients"][phase] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for path in ("java", "forge-server", "jar", "launch"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a single directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "assertions": [], "servers": [], "clients": {}, "jarSha256": digest(args.jar)}

    def first(server, results):
        setup(server, results)
        run_client(args, server, report, "exercise", exercise)

    def restarted(server, results):
        load(server, results, "fixture_chunks_loaded_after_restart")
        all_placed = saved_shapes()
        server.assert_state("all_paint_shapes_and_native_loot_survive_restart",
                            f"{all_placed} {loot_state()}", results)
        run_client(args, server, report, "verify", reopen)

    try:
        run_stage(args, directory, report, "exercise", first)
        run_stage(args, directory, report, "reopened", restarted)
        report["passed"] = True
    finally:
        (directory / "painted-building-results.json").write_text(json.dumps(report, indent=2) + "\n")


def saved_shapes(*, wet=False):
    return " ".join(painted(slot, waterlogged(props, wet and slot < 2))
                    for slot, props in enumerate((CORNER, JOINED, "", "")))


if __name__ == "__main__":
    main()
