#!/usr/bin/env python3
"""Exercise native Impulse Hopper editing, side rendering, crafting and migrated placement."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_impulse_hopper import ARCHIVE, BLOCK, BLOCKED_IO, MACHINE, SIGNAL, cap, iron, redstone
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage


PLAYER = "EnderIOQA"
TABLE = "2826 80 12"
DESTINATION = "2824 80 5"
CHARGE = 980
EDITED_IO = '{Modes:{down:"disable",east:"disable",south:"push"}}'
INGREDIENTS = (("enderio:conductive_alloy_ingot", 4), ("minecraft:hopper", 1), ("enderio:iron_gear", 2),
               ("enderio:void_chassis", 1), ("enderio:redstone_alloy_ingot", 1))


def machine_state(charge, io, redstone_mode, *stacks):
    return (f'{{EnergyStored:{charge},IOConfig:{io},RedstoneControl:"{redstone_mode}",'
            f'Items:{{Size:19,Items:[{",".join(stacks)}]}}}}')


def original():
    return machine_state(CHARGE, BLOCKED_IO, "active_with_signal", iron(0, 1), iron(6, 1), redstone(7),
                         iron(12, 1), redstone(13), cap())


def finished():
    return machine_state(CHARGE - 30, EDITED_IO, "always_active", iron(0, 1), iron(6, 2), redstone(7, 2),
                         iron(12, 2), redstone(13), cap())


def archived(slot):
    return f'Items[{{Slot:{slot}b,id:"{BLOCK}",Count:1b}}]'


def move_to(server, pos):
    x, y, z = map(int, pos.split())
    server.command(f"tp {PLAYER} {x + 0.5} {y} {z + 2.5} 180 14")


def setup(server, results):
    server.command("forceload add 2816 0 2831 15")
    server.assert_state("copied_impulse_hopper_fixture_is_loaded",
                        f"if loaded {MACHINE} if loaded {TABLE} if loaded {DESTINATION}", results)
    server.command("gamerule doDaylightCycle false")
    server.command("time set noon")
    server.command("setworldspawn 2826 80 14")
    server.command("fill 2817 79 3 2830 79 15 minecraft:stone_bricks")
    server.command("fill 2817 80 5 2817 81 13 minecraft:bookshelf")
    server.command("setblock 2818 80 8 minecraft:obsidian")
    server.command("setblock 2822 80 8 minecraft:moss_block")
    server.command(f"setblock {TABLE} minecraft:crafting_table")
    server.command(f'data merge block {MACHINE} {{RedstoneControl:"never_active"}}')
    server.command(f"setblock {SIGNAL} minecraft:air")
    server.command(f"data modify block {MACHINE} Items.Items[{{Slot:6}}] set value {iron(6, 1, 138)}")
    server.assert_state("fixture_keeps_the_original_forge_6_drop",
                        f'if data block {ARCHIVE} {archived(0)}.tag."enderio:data"{{"enderio:energy":980}}', results)


def craft(server, client, output, results):
    for slot, (name, count) in enumerate(INGREDIENTS):
        server.command(f"item replace entity {PLAYER} hotbar.{slot} with {name} {count}")
    move_to(server, TABLE)
    (output / "server-craft").touch()
    wait_signal(client, output, "crafted")
    consumed = " ".join(f'unless data entity {PLAYER} Inventory[{{id:"{name}"}}]' for name, _ in INGREDIENTS)
    server.assert_state("native_survival_recipe_consumes_all_nine_ingredients",
                        f'if data entity {PLAYER} Inventory[{{id:"{BLOCK}",Count:1b}}] {consumed}', results)
    server.command(f"clear {PLAYER} {BLOCK}")


def operation(server, client, output, results):
    server.command(f"item replace entity {PLAYER} hotbar.0 with minecraft:iron_ingot{{PortFixture:137}} 2")
    server.command(f"item replace entity {PLAYER} hotbar.1 with minecraft:redstone")
    move_to(server, MACHINE)
    (output / "server-menu").touch()
    wait_signal(client, output, "batch")
    old_output = '{id:"minecraft:iron_ingot",Count:1b,tag:{PortFixture:138}}'
    server.assert_state("native_templates_side_controls_and_complete_batch_match_client",
                        f"if data block {MACHINE} {finished()} unless data block {MACHINE} Items.Items[{{Slot:1}}] "
                        f"if data entity {PLAYER} Inventory[{old_output}]", results)


def reset_and_replant(server, client, output, results):
    server.command(f"loot replace block {ARCHIVE} container.1 mine {MACHINE} minecraft:diamond_pickaxe")
    item_data = f'{archived(1)}.tag."enderio:data"'
    server.assert_state("native_loot_carries_live_templates_charge_and_items_for_reset",
                        f'if data block {ARCHIVE} {item_data}."enderio:energy" if data block {ARCHIVE} '
                        f'{item_data}."enderio:container".Items[{iron(12, 2)}] if data block {ARCHIVE} '
                        f'{item_data}."enderio:container".Items[{iron(6, 2)}]', results)
    server.command(f"clear {PLAYER}")
    server.command(f"item replace entity {PLAYER} hotbar.0 from block {ARCHIVE} container.1")
    server.command(f"item replace block {ARCHIVE} container.1 with minecraft:air")
    move_to(server, TABLE)
    (output / "server-reset").touch()
    wait_signal(client, output, "reset-crafted")
    server.command(f"item replace block {ARCHIVE} container.3 from entity {PLAYER} hotbar.8")
    server.assert_state("factory_reset_yields_one_hopper_with_default_item_state",
                        f"if data block {ARCHIVE} {archived(3)} unless data block {ARCHIVE} {archived(3)}.tag", results)
    server.command(f"clear {PLAYER} {BLOCK}")
    server.command(f"item replace entity {PLAYER} hotbar.0 from block {ARCHIVE} container.0")
    server.command(f"item replace block {ARCHIVE} container.0 with minecraft:air")
    move_to(server, DESTINATION)
    (output / "server-place").touch()
    wait_signal(client, output, "placed")
    server.assert_state("native_placement_restores_the_exact_forge_6_hopper_and_consumes_its_stack",
                        f"if block {DESTINATION} {BLOCK} if data block {DESTINATION} {original()} "
                        f'unless data entity {PLAYER} Inventory[{{id:"{BLOCK}"}}]', results)
    (output / "server-verified").touch()


def multiplayer(args, server, results, report):
    setup(server, results)
    output = ROOT / "build/port-evidence" / args.name
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
               "--launch", str(args.launch.resolve()), "--scenario", "impulse-hopper", "--output", str(output),
               "--server", f"127.0.0.1:{args.port}", "--timeout", "240"]
    with (server.directory / "impulse-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            craft(server, client, output, results)
            operation(server, client, output, results)
            reset_and_replant(server, client, output, results)
            if client.wait(timeout=35) != 0:
                raise AssertionError(f"Impulse Hopper client failed; see {output}")
            summary = json.loads((output / "summary.json").read_text())
            installed = summary["client"]["captures"]["impulse-io"]["loadedMods"]["enderio"]["file"]
            if digest(Path(installed)) != report["jarSha256"]:
                raise AssertionError("Client and server loaded different Ender IO artifacts")
        finally:
            stop_client(client)
            summary = output / "summary.json"
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def reopen(server, results):
    server.assert_state("edited_live_machine_remains_exact_after_restart",
                        f"if data block {MACHINE} {finished()} unless data block {MACHINE} Items.Items[{{Slot:1}}]", results)
    server.assert_state("native_placement_of_original_drop_remains_exact_after_restart",
                        f"if block {DESTINATION} {BLOCK} if data block {DESTINATION} {original()}", results)
    server.assert_state("factory_reset_item_remains_clean_after_restart",
                        f"if data block {ARCHIVE} {archived(3)} unless data block {ARCHIVE} {archived(3)}.tag", results)


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
        (directory / "impulse-menu-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
