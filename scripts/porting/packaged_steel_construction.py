#!/usr/bin/env python3
"""Verify packaged steel construction and native ladder movement over Forge TCP."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage


PLAYER = "EnderIOQA"
TRIALS = (("vanilla-climb", 4201.5, 84), ("steel-climb", 4205.5, 84),
          ("vanilla-descent", 4201.5, 96), ("steel-descent", 4205.5, 96), ("steel-crouch", 4205.5, 91))
DOOR, HATCH, DARK, END, OBSIDIAN = (4211, 4215, 4218, 4219, 4224)
BARREL = "4207 80 12"
PLACED = (("4205 84 8", "dark_steel_ladder"), (f"{DOOR} 80 8", "dark_steel_door"),
          (f"{HATCH} 80 8", "dark_steel_trapdoor"), (f"{DARK} 80 8", "dark_steel_bars"),
          (f"{END} 80 8", "end_steel_bars"), (f"{OBSIDIAN} 80 8", "reinforced_obsidian_block"))


def load(server, results, stage):
    server.command("forceload add 4192 0 4239 15")
    server.assert_state(f"both_steel_fixture_chunks_are_loaded_{stage}",
                        f"if loaded 4201 84 8 if loaded {OBSIDIAN} 80 8", results)


def door_state(opened):
    switch = str(opened).lower()
    return (f"if block {DOOR} 80 8 enderio:dark_steel_door[half=lower,open={switch},powered={switch}] "
            f"if block {DOOR} 81 8 enderio:dark_steel_door[half=upper,open={switch},powered={switch}] "
            f"if block {HATCH} 80 8 enderio:dark_steel_trapdoor[open={switch},powered={switch}]")


def redstone(server, enabled):
    block = "minecraft:redstone_block" if enabled else "minecraft:air"
    server.command(f"setblock {DOOR + 1} 81 8 {block}")
    server.command(f"setblock {HATCH + 1} 80 8 {block}")


def preserved_blocks(server, results, stage):
    server.assert_state(f"steel_ladders_keep_their_facing_{stage}",
                        "if block 4205 84 8 enderio:dark_steel_ladder[facing=south] "
                        "if block 4205 96 8 enderio:dark_steel_ladder[facing=south]", results)
    server.assert_state(f"bars_connect_and_keep_water_{stage}",
                        f"if block {DARK} 80 8 enderio:dark_steel_bars[east=true] "
                        f"if block {END} 80 8 enderio:end_steel_bars[west=true,waterlogged=true]", results)
    server.assert_state(f"reinforced_obsidian_survives_{stage}",
                        f"if block {OBSIDIAN} 80 8 enderio:reinforced_obsidian_block", results)


def prepare_scene(server, results):
    load(server, results, "initial")
    server.command("gamerule doMobSpawning false")
    server.command("gamerule doDaylightCycle false")
    server.command("time set noon")
    server.command("fill 4195 85 6 4211 93 6 minecraft:mossy_stone_bricks")
    server.command("fill 4195 94 6 4211 102 6 minecraft:bookshelf")
    server.command("fill 4200 80 7 4202 106 7 minecraft:smooth_quartz")
    server.command("fill 4204 80 7 4206 106 7 minecraft:smooth_quartz")
    for x, ladder in ((4201, "minecraft:ladder"), (4205, "enderio:dark_steel_ladder")):
        server.command(f"setblock {x} 80 8 minecraft:stone")
        server.command(f"fill {x} 81 8 {x} 105 8 {ladder}[facing=south]")
    server.command("setblock 4203 91 13 minecraft:barrier")
    server.command(f"setblock {BARREL} minecraft:barrel")
    server.command("scoreboard objectives add port_steel dummy")
    server.command(f"setblock {DOOR} 79 8 minecraft:stone")
    server.command(f"setblock {DOOR} 80 8 enderio:dark_steel_door[facing=south,half=lower]")
    server.command(f"setblock {DOOR} 81 8 enderio:dark_steel_door[facing=south,half=upper]")
    server.command(f"setblock {HATCH} 80 8 enderio:dark_steel_trapdoor[facing=south,half=bottom]")
    server.command(f"setblock {DARK} 80 8 enderio:dark_steel_bars")
    server.command(f"setblock {END} 80 8 enderio:end_steel_bars[waterlogged=true]")
    server.command(f"setblock {OBSIDIAN} 80 8 enderio:reinforced_obsidian_block")
    server.assert_state("closed_door_and_trapdoor_load_with_native_state", door_state(False), results)
    redstone(server, True)
    server.assert_state("powered_upper_half_switches_both_door_halves_and_the_trapdoor", door_state(True), results)
    preserved_blocks(server, results, "before_restart")
    for pos, block in PLACED:
        server.command(f"loot replace block {BARREL} container.0 mine {pos} minecraft:diamond_pickaxe")
        server.assert_state(f"{block}_has_its_native_diamond_loot",
                            f'if data block {BARREL} Items[{{Slot:0b,id:"enderio:{block}",Count:1b}}]', results)


def exercise_ladders(args, server, results, report):
    output = ROOT / "build/port-evidence" / args.name
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
               "--launch", str(args.launch.resolve()), "--scenario", "steel-construction",
               "--output", str(output), "--server", f"127.0.0.1:{args.port}", "--timeout", "175"]
    report["movement"] = {}
    with (server.directory / "client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            for name, x, y in TRIALS:
                server.command(f"tp {PLAYER} {x} {y} 8.5 180 5")
                (output / f"server-ready-{name}").touch()
                result = wait_signal(client, output, name)
                report["movement"][name] = result
                end = round(result["endY"] * 1000)
                server.command(f"execute store result score #end_y port_steel run data get entity {PLAYER} Pos[1] 1000")
                server.assert_state(f"server_agrees_with_{name}_height",
                                    f"if score #end_y port_steel matches {end - 375}..{end + 375}", results)
            server.command(f"tp {PLAYER} 4203.5 92 13.5 180 4")
            (output / "server-ready-verify").touch()
            wait_signal(client, output, "ladder-verified")
            server.command("fill 4218 85 10 4230 97 11 minecraft:smooth_quartz")
            server.command("fill 4219 86 11 4229 96 11 enderio:reinforced_obsidian_block")
            server.command("fill 4218 89 12 4230 89 15 minecraft:smooth_quartz")
            server.assert_state("obsidian_fixture_has_a_single_exposed_side",
                                "if block 4224 91 11 enderio:reinforced_obsidian_block "
                                "if block 4224 91 10 minecraft:smooth_quartz if block 4224 91 12 minecraft:air", results)
            server.command(f"tp {PLAYER} 4224.5 90 15.5 180 -5")
            (output / "server-ready-obsidian").touch()
            if client.wait(timeout=40) != 0:
                raise AssertionError(f"Steel construction client failed, see {output}")
            summary = json.loads((output / "summary.json").read_text())
            jar = summary["client"]["captures"]["obsidian-particles-enabled"]["loadedMods"]["enderio"]["file"]
            if digest(Path(jar)) != report["jarSha256"]:
                raise AssertionError("Steel construction client and server loaded different Ender IO jars")
        finally:
            stop_client(client)
            summary = output / "summary.json"
            if summary.is_file():
                report["client"] = json.loads(summary.read_text())


def reopen(server, results):
    load(server, results, "restart")
    preserved_blocks(server, results, "after_restart")
    server.assert_state("both_opening_blocks_preserve_power_and_position_after_restart", door_state(True), results)
    redstone(server, False)
    server.assert_state("both_opening_blocks_close_and_both_door_halves_stay_intact", door_state(False), results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--forge-server", type=Path, required=True)
    parser.add_argument("--jar", type=Path, required=True)
    scenario = parser.add_mutually_exclusive_group(required=True)
    scenario.add_argument("--launch", type=Path, help="Run a guarded client through the ladder trials")
    scenario.add_argument("--legacy-jar", type=Path, help="Place the six blocks in Forge 6.2 and convert a copy")
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")

    root = ROOT / "build/port-runtime" / args.name
    root.mkdir()
    directory = root / "server"
    report = {"passed": False, "jarSha256": digest(args.jar), "assertions": [], "servers": []}
    try:
        prepare_profile(args, directory, args.jar)

        if args.legacy_jar:
            legacy = root / "legacy"
            report["legacyJarSha256"] = digest(args.legacy_jar)
            prepare_profile(args, legacy, args.legacy_jar)
            run_stage(args, legacy, report, "legacy_placed", prepare_scene)
            shutil.copytree(legacy / WORLD, directory / WORLD)
            run_stage(args, directory, report, "converted", reopen)
        else:
            def exercise(server, results):
                prepare_scene(server, results)
                exercise_ladders(args, server, results, report)

            run_stage(args, directory, report, "steel", exercise)
            run_stage(args, directory, report, "reopened", reopen)
        report["passed"] = True
    finally:
        (root / "steel-construction-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
