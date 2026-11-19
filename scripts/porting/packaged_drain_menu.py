#!/usr/bin/env python3
"""Craft, operate, reset and replant a Drain through real guarded Forge multiplayer."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_drain import BARREL, DRAIN, INTAKE, SWITCH, machine
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage


PLAYER = "EnderIOQA"
DRAIN_ID = "enderio:drain"
TABLE = "2637 80 12"
RESET = "2637 80 8"
LEGACY = "2637 80 4"
CHARGE = 4827
LAVA = 1375
INGREDIENTS = {"enderio:conductive_alloy_ingot": 4, "enderio:clear_glass": 1, "enderio:void_chassis": 1,
               "enderio:iron_gear": 2, "minecraft:bucket": 1}


def setup(server, results):
    server.command("forceload add 2624 0 2639 15")
    server.assert_state("client_crafting_drain_and_placement_sites_are_loaded",
                        f"if loaded {TABLE} if loaded {DRAIN} if loaded {RESET} if loaded {LEGACY}", results)
    server.command("gamerule doMobSpawning false")
    server.command("gamerule doDaylightCycle false")
    server.command("gamerule spawnRadius 0")
    server.command("time set noon")
    server.command("fill 2627 79 3 2638 79 15 minecraft:stone_bricks")
    server.command("fill 2627 80 3 2627 81 15 minecraft:bookshelf")
    server.command("setblock 2629 80 8 minecraft:obsidian")
    server.command("setblock 2633 80 8 minecraft:moss_block")
    server.command(f"setworldspawn 2637 80 14")
    server.command(f"setblock {TABLE} minecraft:crafting_table")
    server.command(f"setblock {SWITCH} minecraft:air")
    server.command(f"setblock {DRAIN} {DRAIN_ID}[facing=south]")
    server.command(f"data merge block {DRAIN} {machine(CHARGE, 0, 0, redstone='never_active', lava=LAVA)}")
    server.command(f"data merge block {DRAIN} {{ActionRange:{{range:0,isVisible:0b}}}}")
    server.command(f"setblock {INTAKE} minecraft:lava[level=0]")
    server.assert_state("copied_world_contains_the_official_forge_6_drain_drop",
                        f'if data block {BARREL} Items[{{Slot:0b,id:"{DRAIN_ID}"}}].tag.BlockEntityTag{{Consumed:1327}}', results)


def move_to(server, site, distance=2.5, pitch=12):
    x, y, z = map(int, site.split())
    server.command(f"tp {PLAYER} {x + 0.5} {y} {z + distance} 180 {pitch}")


def verifies_defaults(server, results, assertion):
    server.assert_state(assertion, f"if block {RESET} {DRAIN_ID}[powered=false] "
                        f'if data block {RESET} {{EnergyStored:0,Consumed:0,RedstoneControl:"always_active"}} '
                        f"unless data block {RESET} ActionRange unless data block {RESET} Items.Items[0] "
                        f"unless data block {RESET} Fluids.Tanks[0].Contents.FluidName", results)


def verifies_legacy(server, results, assertion):
    server.assert_state(assertion, f"if block {LEGACY} {DRAIN_ID} "
                        f"if data block {LEGACY} {machine(0, 1327, 1, lava=1000)}", results)


def verifies_operation(server, results, assertion):
    server.assert_state(assertion, f"if data block {DRAIN} "
                        f"{machine(CHARGE - 1500, 0, 1, redstone='always_active', lava=LAVA + 1000)} "
                        f"if block {INTAKE} minecraft:air if block {DRAIN} {DRAIN_ID}[powered=false]", results)


def gameplay(server, client, output, results):
    wait_signal(client, output, "connected")
    server.command(f"clear {PLAYER}")
    for slot, (item, count) in enumerate(INGREDIENTS.items()):
        server.command(f"item replace entity {PLAYER} hotbar.{slot} with {item} {count}")
    move_to(server, TABLE)
    (output / "server-craft").touch()
    wait_signal(client, output, "crafted")
    no_ingredients = ' '.join(f'unless data entity {PLAYER} Inventory[{{id:"{item}"}}]' for item in INGREDIENTS)
    server.assert_state("shaped_survival_recipe_consumes_all_nine_ingredients",
                        f'if data entity {PLAYER} Inventory[{{id:"{DRAIN_ID}",Count:1b}}] {no_ingredients}', results)
    server.command(f"clear {PLAYER} {DRAIN_ID}")
    move_to(server, DRAIN)
    (output / "server-drain").touch()
    wait_signal(client, output, "menu-complete")
    verifies_operation(server, results, "drain_gui_commits_range_visibility_and_the_exact_operation")
    move_to(server, DRAIN, 6.5, 25)
    (output / "server-range").touch()
    wait_signal(client, output, "overlay")

    server.command(f"loot replace block {BARREL} container.1 mine {DRAIN} minecraft:diamond_pickaxe")
    server.assert_state("native_loot_supplies_one_filled_drain_for_reset",
                        f'if data block {BARREL} Items[{{Slot:1b,id:"{DRAIN_ID}",Count:1b}}].tag."enderio:data"'
                        f'{{"enderio:energy":{CHARGE - 1500},"enderio:item_fluid_content":'
                        f'{{FluidName:"minecraft:lava",Amount:{LAVA + 1000}}}}}', results)
    server.command(f"item replace entity {PLAYER} hotbar.0 from block {BARREL} container.1")
    server.command(f"item replace block {BARREL} container.1 with minecraft:air")
    move_to(server, TABLE)
    (output / "server-reset").touch()
    wait_signal(client, output, "reset-crafted")
    server.assert_state("survival_factory_reset_returns_one_drain_with_no_tags",
                        f'if data entity {PLAYER} Inventory[{{id:"{DRAIN_ID}",Count:1b}}] '
                        f'unless data entity {PLAYER} Inventory[{{id:"{DRAIN_ID}"}}].tag', results)
    move_to(server, RESET)
    (output / "server-place-reset").touch()
    wait_signal(client, output, "reset-placed")
    verifies_defaults(server, results, "clean_drain_placement_creates_default_machine_state")
    server.assert_state("reset_placement_consumes_one_player_item",
                        f'unless data entity {PLAYER} Inventory[{{id:"{DRAIN_ID}"}}]', results)

    server.command(f"item replace entity {PLAYER} hotbar.0 from block {BARREL} container.0")
    server.command(f"item replace block {BARREL} container.0 with minecraft:air")
    move_to(server, LEGACY)
    (output / "server-place-legacy").touch()
    wait_signal(client, output, "legacy-placed")
    verifies_legacy(server, results, "old_drop_places_natively_with_lava_capacitor_range_and_paid_energy")
    server.assert_state("legacy_placement_consumes_one_player_item",
                        f'unless data entity {PLAYER} Inventory[{{id:"{DRAIN_ID}"}}]', results)
    (output / "server-verified").touch()


def multiplayer(args, server, results, report):
    output = ROOT / "build/port-evidence" / args.name
    setup(server, results)
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
               "--launch", str(args.launch.resolve()), "--scenario", "drain", "--output", str(output),
               "--server", f"127.0.0.1:{args.port}", "--timeout", "235"]
    with (server.directory / "drain-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            gameplay(server, client, output, results)
            if client.wait(timeout=35) != 0:
                raise AssertionError(f"Drain client failed; see {output}")
            summary = json.loads((output / "summary.json").read_text())
            installed = summary["client"]["captures"]["drain-menu-zero"]["loadedMods"]["enderio"]["file"]
            if digest(Path(installed)) != report["jarSha256"]:
                raise AssertionError("Client and server loaded different Ender IO artifacts")
        finally:
            stop_client(client)
            summary = output / "summary.json"
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def reopen(server, results):
    verifies_operation(server, results, "operated_drain_state_survives_dedicated_restart")
    verifies_defaults(server, results, "reset_drain_stays_empty_and_default_after_restart")
    verifies_legacy(server, results, "replanted_forge_6_drain_state_survives_restart")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", type=Path, required=True)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--launch", type=Path, required=True)
    parser.add_argument("--legacy-world", type=Path, required=True)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    shutil.copytree(args.legacy_world, directory / WORLD)
    report = {"passed": False, "jarSha256": digest(directory / "mods/enderio.jar"), "assertions": [], "servers": []}
    try:
        run_stage(args, directory, report, "multiplayer", lambda server, results: multiplayer(args, server, results, report))
        run_stage(args, directory, report, "reopened", reopen)
        report["passed"] = True
    finally:
        (directory / "drain-menu-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
