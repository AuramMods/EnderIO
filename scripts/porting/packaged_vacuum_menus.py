#!/usr/bin/env python3
"""Exercise both vacuum menus and recipes on a disposable, guarded Forge connection."""

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage
from packaged_vacuums import ARCHIVE, CHEST, CHEST_ID, JUICE, SWITCHES, XP, XP_ID
from packaged_vacuums import entity, fluid, iron, item_on_ground, stack, stored_iron, summon_item, summon_orb


PLAYER = "EnderIOQA"
TABLE = "2747 80 13"
FILTER_PATH = 'Items.Items[{Slot:27,id:"enderio:basic_item_filter"}].tag."enderio:data"."enderio:item_filter".items[0].value'


@dataclass(frozen=True)
class Device:
    name: str
    block: str
    destination: str
    range: int
    barrel_slot: int
    ingredient: str

    @property
    def is_chest(self):
        return self.name == CHEST_ID

    @property
    def feature(self):
        return self.name.split(":")[1]


DEVICES = (Device(CHEST_ID, CHEST, "2743 80 5", 1, 0, "minecraft:chest"),
           Device(XP_ID, XP, "2754 80 7", 2, 1, "enderio:void_vial"))


def settings(device, *, visible=True, mode="active_with_signal"):
    return f'{{ActionRange:{{range:{device.range},isVisible:{int(visible)}b}},RedstoneControl:"{mode}"}}'


def archive_item(device, slot):
    return f'Items[{{Slot:{slot}b,id:"{device.name}",Count:1b}}]'


def contents(device, pos, count):
    if device.is_chest:
        return f"{stored_iron(pos, count)} if data block {pos} {FILTER_PATH}{iron(1)}"
    return f"if data block {pos} {{{fluid(count)}}}"


def verify_machine(server, results, device, place, count, name, mode):
    server.assert_state(name, f"if block {place} {device.name} if data block {place} {settings(device, mode=mode)} "
                        f"{contents(device, place, count)}", results)


def move_to(server, pos, south=2.5, pitch=14):
    x, y, z = map(int, pos.split())
    server.command(f"tp {PLAYER} {x + 0.5} {y} {z + south} 180 {pitch}")


def send(output, device, step):
    (output / f"server-{device.feature}-{step}").touch()


def wait(client, output, device, step):
    wait_signal(client, output, f"{device.feature}-{step}")


def setup(server, results):
    server.command("forceload add 2736 0 2767 31")
    server.assert_state("vacuum_sites_and_crafting_table_chunk_are_loaded",
                        f"if loaded {CHEST} if loaded {XP} if loaded {TABLE}", results)
    server.command("gamerule doDaylightCycle false")
    server.command("time set noon")
    server.command("fill 2737 79 3 2763 79 18 minecraft:stone_bricks")
    server.command("fill 2745 80 4 2745 81 11 minecraft:bookshelf")
    server.command("fill 2756 80 7 2756 81 12 minecraft:bookshelf")
    server.command("setblock 2742 80 8 minecraft:obsidian")
    server.command("setblock 2753 80 8 minecraft:moss_block")
    server.command("setworldspawn 2747 80 15")
    server.command(f"setblock {TABLE} minecraft:crafting_table")
    for device, switch in zip(DEVICES, SWITCHES):
        server.command(f"data merge block {device.block} {settings(device, visible=False, mode='never_active')}")
        server.command(f"setblock {switch} minecraft:air")
        server.assert_state(f"original_{device.feature}_loot_is_in_the_copied_world",
                            f'if data block {ARCHIVE} {archive_item(device, device.barrel_slot)}.tag."enderio:data"."enderio:action_range"',
                            results)


def craft(server, client, output, results, device):
    server.command(f"clear {PLAYER}")
    ingredients = (("minecraft:iron_ingot", 7), (device.ingredient, 1), ("enderio:pulsating_crystal", 1))
    for slot, (name, count) in enumerate(ingredients):
        server.command(f"item replace entity {PLAYER} hotbar.{slot} with {name} {count}")
    move_to(server, TABLE)
    send(output, device, "craft")
    wait(client, output, device, "crafted")
    consumed = " ".join(f'unless data entity {PLAYER} Inventory[{{id:"{name}"}}]' for name, _ in ingredients)
    server.assert_state(f"survival_{device.feature}_recipe_consumes_nine_ingredients",
                        f'if data entity {PLAYER} Inventory[{{id:"{device.name}",Count:1b}}] {consumed}', results)
    server.command(f"clear {PLAYER} {device.name}")


def operate(server, client, output, results, device):
    move_to(server, device.block)
    send(output, device, "menu")
    wait(client, output, device, "active")
    server.assert_state(f"{device.feature}_menu_sets_live_range_and_redstone",
                        f"if data block {device.block} {settings(device, mode='always_active')}", results)
    x, y, z = (int(value) for value in device.block.split())
    position = f"{x + 0.5} {y + 1.1} {z + 0.5}"
    if device.is_chest:
        summon_item(server, position, "menu_item", iron(3, byte=True))
        summon_item(server, position, "menu_mismatch", stack("minecraft:iron_ingot", 5, byte=True))
        world = (f"unless entity {entity('item', 'tag=menu_item')} if entity "
                 f"{item_on_ground('menu_mismatch', stack('minecraft:iron_ingot', 5, byte=True))}")
        count = 5
    else:
        # A nearby survival player can take a member of the merged orb before the machine scans it.
        move_to(server, device.block, 6.5)
        summon_orb(server, position, "menu_orb", 7, 2)
        world = f"unless entity {entity('experience_orb', 'tag=menu_orb')}"
        count = 420
        server.assert_state("only_the_xp_vacuum_collects_the_fourteen_points",
                            f"{contents(device, device.block, count)} {world} if data entity {PLAYER} {{XpTotal:0}}", results)
    wait(client, output, device, "menu")
    server.assert_state(f"{device.feature}_menu_and_world_have_the_exact_collected_balance",
                        f"{contents(device, device.block, count)} {world} unless data entity {PLAYER} Inventory[{{id:\"minecraft:iron_ingot\"}}]",
                        results)
    move_to(server, device.block, 6.5, 27)
    send(output, device, "range")
    wait(client, output, device, "range")


def reset_and_replant(server, client, output, results, device):
    loot_slot = 2 + device.barrel_slot
    reset_slot = 4 + device.barrel_slot
    server.command(f"loot replace block {ARCHIVE} container.{loot_slot} mine {device.block} minecraft:diamond_pickaxe")
    item_data = f'{archive_item(device, loot_slot)}.tag."enderio:data"'
    carried_contents = ('"enderio:container".Items' + f'[{iron(5, slot=0)}]' if device.is_chest else
                        f'"enderio:item_fluid_content"{{FluidName:"{JUICE}",Amount:420}}')
    server.assert_state(f"ported_{device.feature}_loot_has_contents_before_reset",
                        f"if data block {ARCHIVE} {item_data}.{carried_contents}", results)
    server.command(f"item replace entity {PLAYER} hotbar.0 from block {ARCHIVE} container.{loot_slot}")
    server.command(f"item replace block {ARCHIVE} container.{loot_slot} with minecraft:air")
    move_to(server, TABLE)
    send(output, device, "reset")
    wait(client, output, device, "reset")
    server.command(f"item replace block {ARCHIVE} container.{reset_slot} from entity {PLAYER} hotbar.8")
    server.assert_state(f"native_factory_reset_delivers_one_clean_{device.feature}",
                        f"if data block {ARCHIVE} {archive_item(device, reset_slot)} "
                        f"unless data block {ARCHIVE} {archive_item(device, reset_slot)}.tag", results)
    server.command(f"clear {PLAYER} {device.name}")
    server.command(f"item replace entity {PLAYER} hotbar.0 from block {ARCHIVE} container.{device.barrel_slot}")
    server.command(f"item replace block {ARCHIVE} container.{device.barrel_slot} with minecraft:air")
    move_to(server, device.destination)
    send(output, device, "place")
    wait(client, output, device, "placed")
    verify_machine(server, results, device, device.destination, 2 if device.is_chest else 140,
                   f"native_placement_restores_forge_6_{device.feature}_state", "active_with_signal")
    server.assert_state(f"native_{device.feature}_placement_consumes_the_carried_stack",
                        f'unless data entity {PLAYER} Inventory[{{id:"{device.name}"}}]', results)
    send(output, device, "placed")


def multiplayer(args, server, results, report):
    setup(server, results)
    output = ROOT / "build/port-evidence" / args.name
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
               "--launch", str(args.launch.resolve()), "--scenario", "vacuums", "--output", str(output),
               "--server", f"127.0.0.1:{args.port}", "--timeout", "240"]
    with (server.directory / "vacuum-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            for device in DEVICES:
                craft(server, client, output, results, device)
                operate(server, client, output, results, device)
                reset_and_replant(server, client, output, results, device)
            if client.wait(timeout=35) != 0:
                raise AssertionError(f"Vacuum client failed; see {output}")
            summary = json.loads((output / "summary.json").read_text())
            installed = summary["client"]["captures"]["xp_vacuum-menu"]["loadedMods"]["enderio"]["file"]
            if digest(Path(installed)) != report["jarSha256"]:
                raise AssertionError("Client and server loaded different Ender IO artifacts")
        finally:
            stop_client(client)
            summary = output / "summary.json"
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def reopen(server, results):
    for device in DEVICES:
        verify_machine(server, results, device, device.block, 5 if device.is_chest else 420,
                       f"operated_{device.feature}_survives_restart", "always_active")
        verify_machine(server, results, device, device.destination, 2 if device.is_chest else 140,
                       f"replanted_{device.feature}_survives_restart", "active_with_signal")
        server.assert_state(f"factory_reset_{device.feature}_stack_stays_clean_after_restart",
                            f"if data block {ARCHIVE} {archive_item(device, 4 + device.barrel_slot)} "
                            f"unless data block {ARCHIVE} {archive_item(device, 4 + device.barrel_slot)}.tag", results)


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
        (directory / "vacuum-menu-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
