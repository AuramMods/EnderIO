#!/usr/bin/env python3
"""Verify server-priced Enchanter previews, live recipe replacement and payment."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_crafter import stack
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage


PLAYER = "EnderIOQA"
FIRST = "3472 80 8"
SECOND = "3476 80 8"
INPUTS = ','.join((stack(0, "writable_book"), stack(1, "amethyst_shard", 10), stack(2, "lapis_lazuli", 12)))


def book(name, level):
    return f'id:"minecraft:enchanted_book",tag:{{StoredEnchantments:[{{id:"minecraft:{name}",lvl:{level}s}}]}}'


def ready(pos, name=None, level=None):
    output = f',{{Slot:3,Count:1,{book(name, level)}}}' if name else ""
    missing = "" if name else f" unless data block {pos} Items.Items[{{Slot:3}}]"
    return f"if data block {pos} {{Items:{{Items:[{INPUTS}{output}]}}}}{missing}"


def spent(pos):
    items = ','.join((stack(1, "amethyst_shard", 4), stack(2, "lapis_lazuli", 2)))
    return (f"if data block {pos} {{Items:{{Items:[{items}]}}}} "
            f"unless data block {pos} Items.Items[{{Slot:0}}] unless data block {pos} Items.Items[{{Slot:3}}]")


def write_recipe(directory, enchantment, count):
    pack = directory / WORLD / "datapacks/enchanter-reload"
    recipe = pack / "data/port_enchanter/recipes/amethyst.json"
    recipe.parent.mkdir(parents=True, exist_ok=True)
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 15, "description": "Enchanter reload fixture"}}) + "\n")
    recipe.write_text(json.dumps({"type": "enderio:enchanting", "enchantment": "minecraft:" + enchantment,
                                  "cost_multiplier": 1, "input": {"item": "minecraft:amethyst_shard", "count": count}}) + "\n")
    return recipe


def prepare(server, results):
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    server.command("forceload add 3472 0 3487 15")
    server.assert_state("enchanter_reload_chunk_is_loaded", f"if loaded {FIRST} if loaded {SECOND}", results)
    server.command("fill 3471 79 5 3479 79 14 minecraft:stone")
    server.command("setworldspawn 3472 80 10")
    server.command("scoreboard objectives add port_reload dummy")
    for pos in (FIRST, SECOND):
        server.command(f"setblock {pos} enderio:enchanter")
        server.command(f"data merge block {pos} {{Items:{{Items:[{INPUTS}]}}}}")
    server.assert_state("both_enchanters_preview_the_original_recipe",
                        f"{ready(FIRST, 'sharpness', 2)} {ready(SECOND, 'sharpness', 2)}", results)


def book_count(server, results, name, count, conditions):
    server.command(f"execute store result score PortBooks port_reload run clear {PLAYER} minecraft:enchanted_book 0")
    server.assert_state(name, f"{conditions} if score PortBooks port_reload matches {count}", results)


def exercise(args, server, results, report, recipe):
    output = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "enchanter-reload", "--output", str(output), "--server", f"127.0.0.1:{args.port}",
               "--timeout", "165"]
    summary = output / "summary.json"
    failures = []
    with (server.directory / "enchanter-reload-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            server.command(f"experience set {PLAYER} 19 levels")
            server.command(f"tp {PLAYER} 3472.5 80 10.5 180 20")
            (output / "server-ready").touch()
            wait_signal(client, output, "initial-denied")
            book_count(server, results, "server_rejects_19_levels_without_charging", 0,
                       f'{ready(FIRST, "sharpness", 2)} if data entity {PLAYER} {{XpLevel:19}}')

            recipe.unlink()
            server.command("reload")
            wait_signal(client, output, "recipe-removed")
            try:
                server.assert_state("removed_recipe_preserves_ingredients_and_clears_preview", ready(FIRST), results)
            except AssertionError as error:
                failures.append(str(error))
            server.command(f"data get block {FIRST}")

            write_recipe(server.directory, "mending", 6)
            server.command("reload")
            wait_signal(client, output, "replacement-seen")
            try:
                server.assert_state("open_menu_replaces_preview_without_consuming_ingredients", ready(FIRST, "mending", 1), results)
            except AssertionError as error:
                failures.append(str(error))
            server.command(f"data get block {FIRST}")

            if not failures:
                server.command(f"experience set {PLAYER} 40 levels")
                (output / "server-take").touch()
                wait_signal(client, output, "collected")
                paid = f'{spent(FIRST)} if data entity {PLAYER} {{XpLevel:7}}'
                minted = f'if data entity {PLAYER} Inventory[{{Slot:8b,Count:1b,{book("mending", 1)}}}]'
                book_count(server, results, "one_mending_book_costs_exactly_33_levels_6_shards_and_10_lapis", 1, f"{paid} {minted}")
                (output / "server-paid").touch()
                server.command(f"tp {PLAYER} 3476.5 80 10.5 180 20")
                (output / "server-second").touch()
                wait_signal(client, output, "second-preview")
                server.assert_state("previously_closed_enchanter_updates_without_spending_its_inputs",
                                    ready(SECOND, "mending", 1), results)

            (output / "server-stop").touch()
            if client.wait(timeout=35) != 0:
                failures.append(f"Enchanter reload client checks failed, see {summary}")
            if failures:
                raise AssertionError("\n".join(failures))
            for capture in json.loads(summary.read_text())["client"]["captures"].values():
                if digest(Path(capture["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                    raise AssertionError("Client and server Ender IO jars differ")
        finally:
            stop_client(client)
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--launch", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")

    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    config = directory / "config/enderio/machines-common.toml"
    config.parent.mkdir(parents=True)
    config.write_text("[enchanter]\nlapisCostFactor=2.0\nlevelCostFactor=1.0\nbaseLevelCost=8\n")
    recipe = write_recipe(directory, "sharpness", 4)
    report = {"passed": False, "forge": FORGE, "jarSha256": digest(directory / "mods/enderio.jar"),
              "assertions": [], "servers": []}
    try:
        def fresh(server, results):
            prepare(server, results)
            exercise(args, server, results, report, recipe)

        def reopened(server, results):
            server.assert_state("paid_and_ready_enchanters_keep_their_exact_state_after_restart",
                                f"{spent(FIRST)} {ready(SECOND, 'mending', 1)}", results)

        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", reopened)
        report["passed"] = True
    finally:
        path = directory / "enchanter-reload-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Enchanter reload passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
