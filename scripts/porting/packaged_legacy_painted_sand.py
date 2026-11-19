#!/usr/bin/env python3
"""Convert native Forge 6 painted sand blocks, loot and an airborne entity."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_painted_sand import BARREL, DROP, LAND, PAINT, build_scene, falling, freeze, load, painted_fall
from packaged_painting_machine import PLAYER, paint_tag
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage


SAND = "enderio:painted_sand"
GOLD = "1802 80 8"
EMERALD = "1808 80 8"
AIRBORNE = f"{LAND} 88 8"
REPLANTED = f"{DROP} 88 8"
LANDED = f"{LAND} 80 8"


def painted_block(pos, paint):
    return f'if block {pos} {SAND} if data block {pos} {{Paint:"minecraft:{paint}"}}'


def barrel_item(slot, tag):
    return f'if data block {BARREL} Items[{{Slot:{slot}b,id:"{SAND}",Count:1b,{tag}}}]'


def original_scene():
    return f"{painted_block(GOLD, 'gold_block')} {painted_block(EMERALD, 'emerald_block')}"


def legacy_fixture(server, results):
    load(server, results, "forge6_painted_sand_chunks_loaded")
    build_scene(server)
    for pos, paint in ((GOLD, "gold_block"), (EMERALD, "emerald_block"), (AIRBORNE, "diamond_block")):
        server.command(f'setblock {pos} {SAND}{{Paint:"minecraft:{paint}"}}')
    server.command(f"setblock {BARREL} minecraft:barrel")
    server.command(f"loot replace block {BARREL} container.0 mine {EMERALD} minecraft:diamond_shovel")
    server.assert_state("forge6_placed_blocks_and_native_drop_have_the_original_paint",
                        f"{original_scene()} {painted_block(AIRBORNE, 'diamond_block')} "
                        + barrel_item(0, 'tag:{BlockEntityTag:{Paint:"minecraft:emerald_block"}}'), results)
    freeze(server, results, LAND)


def converted(server, results):
    load(server, results, "converted_painted_sand_chunks_loaded")
    server.assert_state("placed_gold_and_emerald_paint_survive_conversion", original_scene(), results)
    server.assert_state("original_emerald_drop_converts_to_typed_item_data",
                        f"{barrel_item(0, paint_tag('emerald_block'))} unless data block {BARREL} Items[{{Slot:0b}}].tag.BlockEntityTag.Paint",
                        results)
    server.assert_state("original_diamond_entity_is_still_airborne_after_conversion",
                        f"if block {AIRBORNE} minecraft:air if entity {painted_fall(LAND, frozen=True)}", results)


def exercise_client(args, server, report):
    results = report["assertions"]
    output = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
               "--launch", str(args.launch.resolve()), "--scenario", "painted-sand", "--multiplayer-phase", "verify",
               "--output", str(output), "--server", f"127.0.0.1:{args.port}", "--timeout", "165"]
    with (server.directory / "legacy-painted-sand-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            server.command(f"tp {PLAYER} {LAND + 0.5} 88 11.5 180 34")
            (output / "server-airborne").touch()
            wait_signal(client, output, "legacy-airborne")
            server.command(f"item replace entity {PLAYER} hotbar.0 from block {BARREL} container.0")
            server.command(f"item replace block {BARREL} container.0 with minecraft:air")
            server.assert_state("survival_player_receives_the_original_typed_emerald_drop",
                                f'if data entity {PLAYER} Inventory[{{Slot:0b,id:"{SAND}",Count:1b,{paint_tag("emerald_block")}}}] '
                                f"unless data block {BARREL} Items[{{Slot:0b}}]", results)
            server.command(f"tp {PLAYER} {DROP + 0.5} 88 11.5 180 24")
            (output / "server-place-legacy").touch()
            wait_signal(client, output, "legacy-placed")
            server.assert_state("native_survival_placement_restores_emerald_paint_and_consumes_the_drop",
                                f'{painted_block(REPLANTED, "emerald_block")} unless data entity {PLAYER} Inventory[{{id:"{SAND}"}}]',
                                results)
            (output / "server-legacy-verified").touch()
            if client.wait(timeout=25) != 0:
                raise AssertionError(f"Legacy painted sand client failed; see {output}")
            summary = json.loads((output / "summary.json").read_text())
            frame = summary["client"]["captures"]["painted-sand-legacy-airborne"]
            if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Client and server loaded different Ender IO jars")
        finally:
            stop_client(client)
            summary = output / "summary.json"
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def resume_fall(server, results):
    server.command(f"data merge entity {falling(LAND)} {{NoGravity:0b}}")
    server.assert_state("original_entity_resumes_and_lands_with_diamond_paint",
                        f"if block {LANDED} {SAND} if data block {LANDED} {PAINT} unless entity {falling(LAND)}", results)
    server.command(f"loot replace block {BARREL} container.1 mine {LANDED} minecraft:diamond_shovel")
    server.assert_state("native_loot_from_the_landed_forge6_entity_is_typed",
                        barrel_item(1, paint_tag("diamond_block")), results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for path in ("java", "forge-server", "legacy-jar", "jar"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    parser.add_argument("--launch", type=Path, help="Exercise the saved entity and original drop in a guarded client")
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a single directory name")
    directory = ROOT / "build/port-runtime" / args.name
    directory.mkdir()
    legacy = directory / "legacy"
    port = directory / "port"
    prepare_profile(args, legacy, args.legacy_jar)
    prepare_profile(args, port, args.jar)
    report = {"passed": False, "assertions": [], "servers": [],
              "jarSha256": digest(args.jar), "legacySha256": digest(args.legacy_jar)}

    def exercise(server, results):
        converted(server, results)
        if args.launch:
            exercise_client(args, server, report)
        resume_fall(server, results)

    def reopen(server, results):
        load(server, results, "painted_sand_chunks_load_after_dedicated_restart")
        replanted = (painted_block(REPLANTED, "emerald_block") if args.launch else barrel_item(0, paint_tag("emerald_block")))
        server.assert_state("all_original_paints_and_landed_loot_survive_restart",
                            f"{original_scene()} {replanted} {painted_block(LANDED, 'diamond_block')} "
                            f"{barrel_item(1, paint_tag('diamond_block'))} unless entity {falling(LAND)}", results)

    try:
        run_stage(args, legacy, report, "legacy", legacy_fixture)
        shutil.copytree(legacy / WORLD, port / WORLD)
        run_stage(args, port, report, "converted", exercise)
        run_stage(args, port, report, "reopened", reopen)
        report["passed"] = True
    finally:
        (directory / "legacy-painted-sand-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
