#!/usr/bin/env python3
"""Exercise modern and Forge 6.2 Painted Fences over packaged TCP."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_painting_machine import PLAYER, paint_and_extract, paint_tag
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage


SITES = ((4800, "emerald_block"), (4807, "diamond_block"))
OLD_SITE = "4800 80 3"
LOOT = "4803 80 12"
MODERN_HOPPER = "4807 79 3"


def fence_state(x, z, paint, *, north=False, east=False, west=False):
    directions = f"north={str(north).lower()},east={str(east).lower()},west={str(west).lower()},south=false"
    pos = f"{x} 80 {z}"
    return f'if block {pos} enderio:painted_fence[waterlogged=true,{directions}] if data block {pos} {{Paint:"minecraft:{paint}"}}'


def item_in_barrel(slot, paint):
    return f'if data block {LOOT} Items[{{Slot:{slot}b,id:"enderio:painted_fence",Count:1b,{paint_tag(paint)}}}]'


def update_neighbors(server, x, z, *, swapped=False):
    for side, pos in (("west", f"{x - 1} 80 {z}"), ("east", f"{x + 1} 80 {z}")):
        wood = (side == "east") == swapped
        server.command(f"setblock {pos} minecraft:{'oak_fence' if wood else 'nether_brick_fence'}")
    server.command(f"setblock {x} 80 {z - 1} minecraft:{'air' if swapped else 'stone_bricks'}")


def load_scene(server, results, stage):
    server.command("forceload add 4784 0 4815 15")
    server.assert_state(stage + "_fixture_chunks_loaded", f"if loaded {OLD_SITE} if loaded 4807 80 8", results)


def legacy_fixture(server, results):
    load_scene(server, results, "forge6")
    for command in ("gamerule doMobSpawning false", "gamerule spawnRadius 0", "gamerule doDaylightCycle false", "time set noon",
                    "fill 4797 79 1 4810 79 14 minecraft:stone_bricks", "setworldspawn 4800 80 11", f"setblock {LOOT} minecraft:barrel",
                    f'setblock {OLD_SITE} enderio:painted_fence[waterlogged=true]{{Paint:"minecraft:emerald_block"}}'):
        server.command(command)
    update_neighbors(server, 4800, 3)
    server.assert_state("forge6_saved_fence_is_waterlogged_and_connects_to_oak_and_solid_stone",
                        fence_state(4800, 3, "emerald_block", north=True, west=True), results)
    server.command(f"loot replace block {LOOT} container.0 mine {OLD_SITE} minecraft:diamond_axe")
    server.assert_state("forge6_native_drop_keeps_emerald_paint",
                        f'if data block {LOOT} Items[{{Slot:0b,id:"enderio:painted_fence",Count:1b,'
                        'tag:{BlockEntityTag:{Paint:"minecraft:emerald_block"}}}]', results)


def prepare_replants(server, results):
    load_scene(server, results, "converted")
    server.assert_state("converted_placement_retains_legacy_paint_water_and_both_arms",
                        fence_state(4800, 3, "emerald_block", north=True, west=True), results)
    server.assert_state("original_drop_migrates_to_typed_paint",
                        f"{item_in_barrel(0, 'emerald_block')} unless data block {LOOT} Items[{{Slot:0b}}].tag.BlockEntityTag.Paint", results)
    paint_and_extract(server, results, "4807 80 3", MODERN_HOPPER, "fence", "spruce_fence", "diamond_block")
    for x, _ in SITES:
        update_neighbors(server, x, 8)
        server.command(f"fill {x} 79 9 {x} 79 10 minecraft:air")
        server.command(f"setblock {x} 80 8 minecraft:water")
    server.assert_state("both_survival_placement_cells_start_with_source_water",
                        " ".join(f"if block {x} 80 8 minecraft:water[level=0]" for x, _ in SITES), results)


def visit(server, output, x, marker):
    server.command(f"tp {PLAYER} {x + 0.5} 80 11.5 180 22")
    (output / f"server-{marker}").touch()


def exercise(server, results, client, output):
    for slot, (x, paint) in enumerate(SITES):
        source = f"{LOOT if slot == 0 else MODERN_HOPPER} container.0"
        server.command(f"item replace entity {PLAYER} hotbar.{slot} from block {source}")
        server.command(f"item replace block {source} with minecraft:air")
        visit(server, output, x, f"place-{slot}")
        wait_signal(client, output, f"used-{slot}")
        server.assert_state(f"site_{slot}_native_placement_reciprocates_oak_but_ignores_nether_brick",
                            f"{fence_state(x, 8, paint, north=True, west=True)} "
                            f"if block {x - 1} 80 8 minecraft:oak_fence[east=true] "
                            f"if block {x + 1} 80 8 minecraft:nether_brick_fence[west=false] "
                            f"unless data entity {PLAYER} Inventory[{{Slot:{slot}b}}]", results)
        (output / f"server-placed-{slot}").touch()
        wait_signal(client, output, f"placed-{slot}")
        update_neighbors(server, x, 8, swapped=True)
        server.assert_state(f"site_{slot}_native_shape_updates_keep_paint_and_water",
                            f"{fence_state(x, 8, paint, east=True)} "
                            f"if block {x - 1} 80 8 minecraft:nether_brick_fence[east=false] "
                            f"if block {x + 1} 80 8 minecraft:oak_fence[west=true]", results)
        (output / f"server-swapped-{slot}").touch()
        wait_signal(client, output, f"swapped-{slot}")
        server.command(f"loot replace block {LOOT} container.{slot} mine {x} 80 8 minecraft:diamond_axe")
        server.assert_state(f"site_{slot}_native_loot_keeps_the_correct_paint", item_in_barrel(slot, paint), results)


def reopen(server, results, client, output):
    for slot, (x, paint) in enumerate(SITES):
        visit(server, output, x, f"restored-{slot}")
        wait_signal(client, output, f"restored-{slot}")
        server.command(f"setblock {x + 1} 80 8 minecraft:air")
        server.assert_state(f"site_{slot}_last_arm_disappears_without_draining_water_or_changing_paint", fence_state(x, 8, paint), results)
        (output / f"server-isolated-{slot}").touch()
        wait_signal(client, output, f"isolated-{slot}")


def run_client(args, server, report, phase, scenario):
    output = ROOT / "build/port-evidence" / f"{args.name}-{phase}"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "painted-fence", "--output", str(output), "--server", f"127.0.0.1:{args.port}",
               "--multiplayer-phase", phase, "--timeout", "195"]
    with (server.directory / f"client-{phase}.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            scenario(server, report["assertions"], client, output)
            (output / "server-done").touch()
            if client.wait(timeout=25) != 0:
                raise AssertionError(f"Painted fence client failed; see {output}")
            summary = json.loads((output / "summary.json").read_text())
            frame = next(iter(summary["client"]["captures"].values()))
            if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Client and port server loaded different Ender IO jars")
        finally:
            stop_client(client)
            summary = output / "summary.json"
            report["clients"][phase] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for path in ("java", "forge-server", "legacy-jar", "jar", "launch"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")
    directory = ROOT / "build/port-runtime" / args.name
    directory.mkdir()
    legacy = directory / "legacy"
    port = directory / "port"
    prepare_profile(args, legacy, args.legacy_jar)
    prepare_profile(args, port, args.jar)
    report = {"passed": False, "jarSha256": digest(port / "mods/enderio.jar"), "legacySha256": digest(legacy / "mods/enderio.jar"),
              "assertions": [], "servers": [], "clients": {}}

    def converted(server, results):
        prepare_replants(server, results)
        run_client(args, server, report, "exercise", exercise)

    def restarted(server, results):
        load_scene(server, results, "reopened")
        saved = " ".join(f"{fence_state(x, 8, paint, east=True)} {item_in_barrel(slot, paint)}" for slot, (x, paint) in enumerate(SITES))
        server.assert_state("both_replants_and_loot_keep_their_exact_paint_water_and_saved_arms", saved, results)
        server.assert_state("untouched_legacy_fence_still_has_both_original_arms_and_its_paint",
                            fence_state(4800, 3, "emerald_block", north=True, west=True), results)
        run_client(args, server, report, "verify", reopen)

    try:
        run_stage(args, legacy, report, "legacy", legacy_fixture)
        shutil.copytree(legacy / WORLD, port / WORLD)
        run_stage(args, port, report, "converted", converted)
        run_stage(args, port, report, "reopened", restarted)
        report["passed"] = True
    finally:
        (directory / "painted-fence-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
