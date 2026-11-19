#!/usr/bin/env python3
"""Craft shipped filter recipes and edit the crafted stacks from native conduit screens."""

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_laserio import filter_slot
from packaged_legacy_transfer_filters import wait_ticks
from packaged_redstone_filter_menus import PLAYER
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage
from packaged_stock_filter_menus import ADVANCED, EXTRACT_STOCK, LARGE, load, prepare, set_items, stack


TABLE = "3279 80 3"
ARCHIVE = "3283 80 14"
PAPER = "minecraft:paper 4"
REDSTONE = "minecraft:redstone 4"
ADVANCED_INGREDIENTS = (REDSTONE, PAPER, "enderio:z_logic_controller")
BASE = "enderio:redstone_filter_base"
ALLOY = "enderio:redstone_alloy_ingot"
TORCH = "minecraft:redstone_torch"


@dataclass(frozen=True)
class Craft:
    recipe: str
    ingredients: tuple
    output: str | None = None
    archive: int | None = None

    @property
    def result(self):
        return self.output or self.recipe


CONFIGURED_ITEMS = {
    "basic_item_filter": '"enderio:item_filter":{items:[{index:0,value:{id:"minecraft:paper",Count:1}}],isInvert:1b}',
    "advanced_item_filter": '"enderio:item_filter":{items:[{index:9,value:{id:"minecraft:paper",Count:1}}],isNbt:1b}',
    "limited_item_filter": '"enderio:limited_item_filter":{items:[{index:17,value:{id:"minecraft:paper",Count:137}}]}',
    "basic_fluid_filter": ('"enderio:fluid_filter":{fluids:[{index:4,value:{FluidName:"minecraft:water",Amount:37}}],'
                           'isNbt:1b,isInvert:0b}'),
}
RECIPES = (
    Craft("basic_item_filter", (PAPER, "minecraft:hopper")),
    Craft("basic_fluid_filter", (PAPER, "minecraft:bucket")),
    Craft("basic_soul_filter", (PAPER, "enderio:soul_vial")),
    Craft("advanced_item_filter", ADVANCED_INGREDIENTS, archive=0),
    Craft("big_advanced_item_filter", ("@0", "minecraft:shulker_shell 2"), archive=3),
    Craft("advanced_item_filter", ADVANCED_INGREDIENTS, archive=1),
    Craft("limited_item_filter", ("@1", "minecraft:comparator 2"), archive=4),
    Craft("big_item_filter", ("enderio:powdered_obsidian 4", PAPER, "enderio:skeletal_contractor"), archive=2),
    Craft("redstone_filter_base", (f"{ALLOY} 4", PAPER, "minecraft:iron_ingot"), archive=8),
    Craft("redstone_not_filter", ("@8", ALLOY, TORCH)),
    Craft("redstone_and_filter", (BASE, f"{TORCH} 2"), archive=5),
    Craft("nand_filter_from_and_filter", ("@5", TORCH), "redstone_nand_filter", 5),
    Craft("and_filter_from_nand_filter", ("@5", TORCH), "redstone_and_filter"),
    Craft("redstone_or_filter", (BASE, f"{ALLOY} 2"), archive=6),
    Craft("nor_filter_from_or_filter", ("@6", TORCH), "redstone_nor_filter", 6),
    Craft("or_filter_from_nor_filter", ("@6", TORCH), "redstone_or_filter"),
    Craft("redstone_xor_filter", (BASE, f"{ALLOY} 2", f"{TORCH} 2"), archive=7),
    Craft("xnor_filter_from_xor_filter", ("@7", TORCH), "redstone_xnor_filter", 7),
    Craft("xor_filter_from_xnor_filter", ("@7", TORCH), "redstone_xor_filter"),
    Craft("redstone_toggle_filter", (BASE, ALLOY, "minecraft:lever")),
    Craft("redstone_counting_filter", (BASE, f"{ALLOY} 4")),
    Craft("redstone_sensor_filter", (BASE, ALLOY, "minecraft:comparator")),
    Craft("redstone_timer_filter", (BASE, ALLOY, "minecraft:clock")),
    *(Craft(f"erase_{item}", (f'enderio:{item}{{RecipeMarker:73,"enderio:data":{{{state}}}}}',), item)
      for item, state in CONFIGURED_ITEMS.items()),
    *(Craft(f"redstone_{name}_filter_erasure", (f"enderio:redstone_{name}_filter{{RecipeMarker:73}}",),
            f"redstone_{name}_filter")
      for name in ("and", "nand", "or", "nor", "xor", "xnor", "counting", "timer")),
)

ENDPOINTS = (
    ("large", LARGE, False, 2, 'big_item_filter', 'minecraft:paper{LargeSample:1} 7'),
    ("large-advanced", ADVANCED, True, 3, 'big_advanced_item_filter', 'minecraft:iron_pickaxe{Damage:27,LargeSample:2}'),
    ("limited", EXTRACT_STOCK, True, 4, 'limited_item_filter', 'minecraft:cobblestone{StockSample:5} 37'),
)


def inventory_slot(index):
    return f"hotbar.{index}" if index < 9 else f"inventory.{index - 9}"


def transfer_archived(server, target, archive):
    server.command(f"item replace entity {PLAYER} {target} from block {ARCHIVE} container.{archive}")
    server.command(f"item replace block {ARCHIVE} container.{archive} with minecraft:air")


def craft_recipes(server, results, client, output):
    for index, craft in enumerate(RECIPES):
        server.command(f"clear {PLAYER}")
        for slot, ingredient in enumerate(craft.ingredients):
            if ingredient.startswith("@"):
                transfer_archived(server, inventory_slot(slot), int(ingredient[1:]))
            else:
                server.command(f"item replace entity {PLAYER} {inventory_slot(slot)} with {ingredient}")
        published = output / f"recipe-{index}.txt"
        temp = published.with_suffix(".tmp")
        temp.write_text(f"enderio:{craft.recipe}\nenderio:{craft.result}\n")
        temp.rename(published)
        slot = wait_signal(client, output, f"crafted-{index}")["inventorySlot"]
        server.command(f"execute store result score #inventory port_stock if data entity {PLAYER} Inventory[]")
        item = f'{PLAYER} Inventory[{{Slot:{slot}b,id:"enderio:{craft.result}",Count:1b}}]'
        server.assert_state(f"recipe_{index}_{craft.recipe}_consumes_the_expected_inputs",
                            f"if score #inventory port_stock matches 1 if data entity {item} unless data entity {item}.tag", results)
        if craft.archive is not None:
            server.command(f"item replace block {ARCHIVE} container.{craft.archive} from entity {PLAYER} {inventory_slot(slot)}")
    (output / "server-ready-recipes-complete").touch()
    wait_signal(client, output, "crafting-closed")


def state(name, lane, insert):
    item = filter_slot(lane, insert=insert)
    key = "limited_item_filter" if name == "limited" else "item_filter"
    data = f'{item}.tag."enderio:data"."enderio:{key}"'
    if name == "large":
        return (f'if data block {data}{{isInvert:1b}} '
                f'if data block {data}.items[35].value{{id:"minecraft:paper",Count:1,tag:{{LargeSample:1}}}}')
    if name == "large-advanced":
        return (f'if data block {data}{{isNbt:1b,damageMode:"up_to_25"}} '
                f'if data block {data}.items[35].value{{id:"minecraft:iron_pickaxe",Count:1,tag:{{Damage:27,LargeSample:2}}}}')
    return (f'if data block {data}{{isNbt:1b}} '
            f'if data block {data}.items[17].value{{id:"minecraft:cobblestone",Count:37,tag:{{StockSample:5}}}}')


def edit_conduits(server, results, client, output):
    for name, lane, insert, archive, item, sample in ENDPOINTS:
        x = 3280 if insert else 3279
        stand = f"{x + 0.5} 80 {lane.z + 2.5} 180 15"
        server.command(f"clear {PLAYER}")
        transfer_archived(server, "hotbar.0", archive)
        server.command(f"item replace entity {PLAYER} hotbar.1 with {sample}")
        server.command(f"tp {PLAYER} {stand}")
        (output / f"server-ready-{name}").touch()
        wait_signal(client, output, f"{name}-edited")
        server.assert_state(f"crafted_{name}_is_installed_and_edited_in_place",
                            f'{state(name, lane, insert)} unless data entity {PLAYER} Inventory[{{id:"enderio:{item}"}}] '
                            f'unless data block {ARCHIVE} Items[{{id:"enderio:{item}"}}]', results)
        if name == "limited":
            server.command(f"tp {PLAYER} {x + 0.5} 80 {lane.z + 12} 180 15")
            (output / "server-ready-distance").touch()
            wait_signal(client, output, "closed-out-of-range")
            server.assert_state("distance_close_preserves_the_live_filter", state(name, lane, insert), results)
            server.command(f"tp {PLAYER} {stand}")
            (output / "server-ready-return").touch()
        wait_signal(client, output, f"{name}-closed")


def exercise_client(args, server, results, report):
    output = ROOT / "build/port-evidence" / args.name
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
               "--launch", str(args.launch.resolve()), "--scenario", "filter-progression",
               "--output", str(output), "--server", f"127.0.0.1:{args.port}", "--timeout", "245"]
    with (server.directory / "client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            server.command(f"tp {PLAYER} 3279.5 80 5.5 180 15")
            (output / "server-ready-table").touch()
            craft_recipes(server, results, client, output)
            edit_conduits(server, results, client, output)
            if client.wait(timeout=40) != 0:
                raise AssertionError(f"Guarded progression client failed, see {output}")
        finally:
            stop_client(client)
            summary = output / "summary.json"
            if summary.is_file():
                report["client"] = json.loads(summary.read_text())


def routes(server, results, stage):
    for name, lane, insert, _, _, _ in ENDPOINTS:
        server.assert_state(f"crafted_{name}_keeps_its_configuration_{stage}", state(name, lane, insert), results)
        set_items(server, lane.source)
        set_items(server, lane.target)

    paper = 'id:"minecraft:paper",tag:{LargeSample:99}'
    iron = 'id:"minecraft:iron_ingot"'
    set_items(server, LARGE.source, stack(0, paper, 5), stack(1, iron, 3))
    matching_pick = 'id:"minecraft:iron_pickaxe",tag:{Damage:45,LargeSample:2}'
    worn_pick = 'id:"minecraft:iron_pickaxe",tag:{Damage:80,LargeSample:2}'
    set_items(server, ADVANCED.source, stack(0, matching_pick, 1), stack(1, worn_pick, 1))
    cobble = 'id:"minecraft:cobblestone",tag:{StockSample:5}'
    wrong_cobble = 'id:"minecraft:cobblestone",tag:{StockSample:6}'
    set_items(server, EXTRACT_STOCK.source, stack(0, cobble, 64), stack(1, wrong_cobble, 5))
    server.assert_state(f"crafted_large_filter_denies_paper_and_passes_iron_{stage}",
                        f'if data block {LARGE.target} Items[{stack(0, iron, 3)}] '
                        f'if data block {LARGE.source} Items[{stack(0, paper, 5)}]', results)
    server.assert_state(f"crafted_advanced_filter_honors_the_edited_damage_setting_{stage}",
                        f'if data block {ADVANCED.target} Items[{stack(0, matching_pick, 1)}] '
                        f'if data block {ADVANCED.source} Items[{stack(1, worn_pick, 1)}]', results)
    server.assert_state(f"crafted_limited_filter_hits_37_{stage}",
                        f'if data block {EXTRACT_STOCK.target} Items[{stack(0, cobble, 37)}]', results, timeout=15)
    wait_ticks(server, 35)
    server.assert_state(f"crafted_limited_filter_retains_the_exact_excess_and_other_tag_{stage}",
                        f'if data block {EXTRACT_STOCK.target} Items[{stack(0, cobble, 37)}] '
                        f'if data block {EXTRACT_STOCK.source} Items[{stack(0, cobble, 27)}] '
                        f'if data block {EXTRACT_STOCK.source} Items[{stack(1, wrong_cobble, 5)}]', results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--forge-server", type=Path, required=True)
    parser.add_argument("--launch", type=Path, required=True)
    parser.add_argument("--jar", type=Path, required=True)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")
    root = ROOT / "build/port-runtime" / args.name
    root.mkdir()
    directory = root / "server"
    report = {"passed": False, "jarSha256": digest(args.jar), "recipes": len(RECIPES), "assertions": [], "servers": []}
    try:
        prepare_profile(args, directory, args.jar)

        def exercise(server, results):
            prepare(server, results)
            server.command(f"setblock {TABLE} minecraft:crafting_table")
            server.command(f"setblock {ARCHIVE} minecraft:barrel")
            server.command("forceload add 3278 16 3282 19")
            server.command("fill 3278 79 16 3282 79 19 minecraft:stone")
            exercise_client(args, server, results, report)
            routes(server, results, "live")

        def reopen(server, results):
            load(server, results, "restart")
            routes(server, results, "restart")

        run_stage(args, directory, report, "edited", exercise)
        run_stage(args, directory, report, "reopened", reopen)
        report["passed"] = True
    finally:
        (root / "filter-progression-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
