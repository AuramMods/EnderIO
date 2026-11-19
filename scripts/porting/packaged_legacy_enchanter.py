#!/usr/bin/env python3
"""Upgrade placed and carried Enchanters from a copied Forge 6 world."""

import argparse
import json
from pathlib import Path
import shutil
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import wait_signal
from packaged_enchanter import BOOK, FRESH, PLAYER, SAVED, exercise, ready, spent
from packaged_enchanter_reload import book
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage


BLOCK = "enderio:enchanter"
DROP_SOURCE = "3458 80 8"
BARREL = "3453 80 12"
CUSTOM = "3460 80 8"
PAGES = '{pages:["Forge 6 Enchanter fixture"]}'


def contents(*, legacy=False, catalyst="quartz", amount=25, lapis=10, preview=None):
    byte = "b" if legacy else ""
    items = [f'{{Slot:0,Count:1{byte},id:"minecraft:writable_book",tag:{PAGES}}}',
             f'{{Slot:1,Count:{amount}{byte},id:"minecraft:{catalyst}"}}',
             f'{{Slot:2,Count:{lapis}{byte},id:"minecraft:lapis_lazuli"}}']
    if preview:
        items.append(f'{{Slot:3,Count:1{byte},{preview}}}')
    return f'{{Items:[{",".join(items)}]}}'


def ready_with_page(pos):
    return f'{ready(pos)} if data block {pos} {{Items:{contents()}}}'


def legacy_drop():
    return f'{{Slot:0b,id:"{BLOCK}",Count:1b,tag:{{BlockEntityTag:{{Items:{contents(legacy=True, preview=BOOK)}}}}}}}'


def custom_preview():
    items = contents(catalyst="amethyst_shard", amount=10, lapis=17, preview=book("mending", 1))
    return f"if data block {CUSTOM} {{Items:{items}}}"


def write_legacy_pack(directory):
    pack = directory / WORLD / "datapacks/legacy-enchanter"
    recipe = pack / "data/port_enchanter/recipes/amethyst.json"
    recipe.parent.mkdir(parents=True)
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 15, "description": "Forge 6 Enchanter fixture"}}))
    recipe.write_text(json.dumps({"type": "enderio:enchanting", "enchantment": "minecraft:mending", "cost_multiplier": 1,
                                  "input": {"count": 6, "ingredient": {"item": "minecraft:amethyst_shard"}}}))


def legacy_fixture(server, results):
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    server.command("forceload add 3440 0 3471 15")
    server.assert_state("forge6_fixture_chunks_are_loaded", f"if loaded {FRESH} if loaded {DROP_SOURCE}", results)
    server.command("fill 3447 79 5 3462 79 14 minecraft:stone")
    server.command("setworldspawn 3450 80 10")
    server.command("scoreboard objectives add port_enchant dummy")
    original = contents(legacy=True)
    carried = contents(legacy=True, preview=BOOK)
    custom = contents(legacy=True, catalyst="amethyst_shard", amount=10, lapis=17)
    for pos, items in ((FRESH, original), (DROP_SOURCE, carried), (CUSTOM, custom)):
        server.command(f"setblock {pos} {BLOCK}[facing=south]")
        server.command(f"data merge block {pos} {{Items:{items}}}")
    server.assert_state("forge6_saves_original_input_slots_and_book_page",
                        f"if data block {FRESH} {{Items:{original}}} if data block {CUSTOM} {{Items:{custom}}} "
                        f"unless data block {FRESH} Items.Items[{{Slot:3}}]", results)
    server.command(f"setblock {BARREL} minecraft:barrel")
    server.command(f"loot replace block {BARREL} container.0 mine {DROP_SOURCE} minecraft:diamond_pickaxe")
    server.assert_state("forge6_native_loot_carries_inputs_and_preview_in_block_entity_tag",
                        f"if data block {BARREL} Items[{legacy_drop()}]", results)
    server.command(f"setblock {DROP_SOURCE} minecraft:air")


def replant(server, results, client, output):
    server.command(f"tp {PLAYER} 3454.5 80 10.5 180 22")
    server.command(f"item replace entity {PLAYER} hotbar.0 from block {BARREL} container.0")
    server.command(f"item replace block {BARREL} container.0 with minecraft:air")
    (output / "server-replant").touch()
    wait_signal(client, output, "replanted")
    server.assert_state("survival_placement_restores_the_original_item_slots_and_spends_the_drop",
                        f'{ready_with_page(SAVED)} unless data entity {PLAYER} Inventory[{{id:"{BLOCK}"}}] '
                        f"unless data block {BARREL} Items[0]", results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--legacy-jar", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--launch", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")

    directory = ROOT / "build/port-runtime" / args.name
    directory.mkdir()
    legacy = directory / "legacy"
    port = directory / "port"
    prepare_profile(args, legacy, args.legacy_jar)
    prepare_profile(args, port, args.jar)
    write_legacy_pack(legacy)
    report = {"passed": False, "forge": FORGE, "jarSha256": digest(port / "mods/enderio.jar"),
              "legacyJarSha256": digest(legacy / "mods/enderio.jar"), "assertions": [], "servers": [], "clients": {}}
    try:
        def converted(server, results):
            server.assert_state("copied_world_rebuilds_missing_preview_and_keeps_the_original_drop",
                                f"{ready_with_page(FRESH)} if data block {BARREL} Items[{legacy_drop()}]", results)
            server.assert_state("historical_nested_datapack_recipe_loads_and_generates_a_preview", custom_preview(), results)
            exercise(args, server, results, report, restarted=False)

        def reopened(server, results):
            server.assert_state("placed_enchanter_stays_paid_and_carried_drop_survives_restart",
                                f"{spent(FRESH)} if data block {BARREL} Items[{legacy_drop()}]", results)
            exercise(args, server, results, report, restarted=True, replant=replant)

        def paid(server, results):
            server.assert_state("both_enchanters_keep_exact_remainders_and_custom_recipe_stays_ready",
                                f"{spent(FRESH)} {spent(SAVED)} {custom_preview()} unless data block {BARREL} Items[0]", results)

        run_stage(args, legacy, report, "legacy", legacy_fixture)
        shutil.copytree(legacy / WORLD, port / WORLD)
        run_stage(args, port, report, "converted", converted)
        run_stage(args, port, report, "reopened", reopened)
        run_stage(args, port, report, "paid", paid)
        report["passed"] = True
    finally:
        path = directory / "legacy-enchanter-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Legacy Enchanter passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
