#!/usr/bin/env python3
"""Craft stock Enderios using powder from a packaged survival SAG Mill route."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_alloy_progression import verify_saved_inventory
from packaged_glass_progression import transfer
from packaged_photovoltaic_progression import COST, RESERVE, Grind, batch_loaded, empty_mill
from packaged_sag_progression import charge, inputs, mill, output, prepare_mill
from packaged_server_smoke import ROOT, digest
from packaged_solar_charging import check
from packaged_spawners import WORLD, prepare_profile, run_stage
from packaged_starter_progression import PLAYER, hopper_item, player_count


PEARL = Grind(3621, "minecraft:ender_pearl", "enderio:powdered_ender_pearl", 1)
OBSIDIAN = Grind(3625, "minecraft:obsidian", "enderio:powdered_obsidian", 1)
YIELDS = ((PEARL, 9), (OBSIDIAN, 4))
CEREAL = "enderio:enderios"
BUCKET = "minecraft:bucket"
PARTS = ("minecraft:bowl", "minecraft:milk_bucket", "minecraft:wheat")
CHEST = "3631 80 8"
EXPECTED = {PEARL.dust: 8, OBSIDIAN.dust: 4, CEREAL: 1, BUCKET: 1}


def finished_stock():
    return " ".join(f"{empty_mill(route)} unless data block {output(route.x)} Items[0]" for route, _ in YIELDS)


def workshop(server, results):
    server.command("forceload add 3616 0 3647 15")
    server.assert_state("both_sag_mills_and_the_workshop_are_loaded",
                        f"if loaded {mill(PEARL.x)} if loaded {mill(OBSIDIAN.x)} if loaded {CHEST}", results)
    for command in ("gamerule doMobSpawning false", "gamerule doLimitedCrafting true", "gamerule spawnRadius 0",
                    "gamerule doDaylightCycle false", "time set noon", "fill 3627 79 7 3635 79 15 minecraft:stone",
                    "setworldspawn 3630 80 12", f"setblock {CHEST} minecraft:chest[facing=south]",
                    "setblock 3630 80 8 minecraft:crafting_table"):
        server.command(command)
    for route, _ in YIELDS:
        prepare_mill(server, route.x)
        inputs(server, route.x, route.material, route.count)
    for route, _ in YIELDS:
        batch_loaded(server, results, route, route.count)
        charge(server, route.x, COST + RESERVE)
    for slot, (route, count) in enumerate(YIELDS):
        hopper = output(route.x)
        server.assert_state(f"{route.dust}_exports_exactly_{count}_after_2400_fe",
                            f"{empty_mill(route)} {hopper_item(hopper, route.dust, count)} "
                            f"unless data block {hopper} Items[1]", results)
        transfer(server, hopper, 0, CHEST, slot)
    for slot, part in enumerate(PARTS, 2):
        server.command(f"item replace block {CHEST} container.{slot} with {part}")


def craft(args, server, results, report):
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    summary = evidence / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "pearl-powder", "--server", f"127.0.0.1:{args.port}", "--output", str(evidence), "--timeout", "205"]
    with (server.directory / "pearl-powder-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            server.command(f"tp {PLAYER} 3630.5 80 11.5 180 15")
            (evidence / "server-workshop").touch()
            wait_signal(client, evidence, "crafted")
            for item_id, count in EXPECTED.items():
                check(f"survival_has_{count}_{item_id}", count, player_count(server, item_id), results)
            check("the_remaining_pearl_powder_matches_its_live_common_tag", 8, player_count(server, "#c:dusts/ender_pearl"), results)
            check("the_original_obsidian_powder_matches_its_live_common_tag", 4, player_count(server, "#c:dusts/obsidian"), results)
            consumed = " ".join(f'unless data entity {PLAYER} Inventory[{{id:"{part}"}}]' for part in PARTS)
            server.assert_state("the_original_stock_inputs_are_consumed_without_leaving_copies",
                                f"{consumed} unless data block {CHEST} Items[0] {finished_stock()}", results)
            (evidence / "server-done").touch()
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"Pearl powder client failed; see {evidence}")
            frame = json.loads(summary.read_text())["client"]["captures"]["original-pearl-powder-enderios"]
            if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Pearl powder client and server Ender IO jars differ")
        finally:
            stop_client(client)
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def verify_saved_progression(directory, report):
    world = directory / WORLD
    verify_saved_inventory(directory, report, expected=EXPECTED)
    player, = (world / "playerdata").glob("*.dat")
    stats = json.loads((world / "stats" / f"{player.stem}.json").read_text())["stats"]
    advancements = json.loads((world / "advancements" / f"{player.stem}.json").read_text())
    check("the_saved_vanilla_crafting_stat_awards_one_enderios", 1, stats["minecraft:crafted"].get(CEREAL), report["assertions"])
    unlocked = advancements["enderio:recipes/food/enderios"]
    check("the_saved_recipe_advancement_records_the_native_wheat_unlock", True,
          unlocked["done"] and "has_ingredient" in unlocked["criteria"], report["assertions"])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    for key in ("forge-server", "jar", "launch"):
        parser.add_argument(f"--{key}", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}

    def fresh(server, results):
        workshop(server, results)
        craft(args, server, results, report)

    def reopened(server, results):
        server.assert_state("stock_mill_reserves_and_the_empty_workshop_survive_restart",
                            f"{finished_stock()} unless data block {CHEST} Items[0]", results)

    try:
        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", reopened)
        verify_saved_progression(directory, report)
        report["passed"] = True
    finally:
        result = directory / "pearl-powder-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Pearl powder passed={report['passed']}, {len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
