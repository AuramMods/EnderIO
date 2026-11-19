#!/usr/bin/env python3
"""Mine the saved Energetic panel and verify both original stock upgrades and power delivery."""

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_alloy_progression import verify_saved_inventory
from packaged_photovoltaic_progression import CHEST, PANEL, PANEL_POS, PLATE, RECEIVER, measure, stock_remains
from packaged_server_smoke import ROOT, digest
from packaged_solar_charging import check, sample
from packaged_spawners import WORLD, prepare_profile, run_stage
from packaged_starter_progression import PLAYER, player_count
from saved_world import read_data_file


@dataclass(frozen=True)
class Upgrade:
    name: str
    lower: str
    fe_per_tick: int
    stock: tuple

    @property
    def module(self):
        return f"enderio:{self.name}_photovoltaic_module"


PULSATING = Upgrade("pulsating", PANEL, 16, (("enderio:pulsating_alloy_ingot", 2), ("enderio:fused_quartz_e", 1),
                                          (PLATE, 2), ("enderio:powdered_coal", 1), ("enderio:double_layer_capacitor", 2)))
VIBRANT = Upgrade("vibrant", PULSATING.module, 64, (("enderio:vibrant_alloy_ingot", 2), ("enderio:fused_quartz_d", 1),
                                                  (PLATE, 2), ("minecraft:glowstone", 1), ("enderio:octadic_capacitor", 2)))
UPGRADES = (PULSATING, VIBRANT)
PICKAXE = "minecraft:iron_pickaxe"


def drop_selector(item):
    return f'@e[type=minecraft:item,x=3574.5,y=80,z=8.5,distance=..5,nbt={{Item:{{id:"{item}",Count:1b}}}}]'


def ready(server, evidence, marker, x):
    server.command(f"tp {PLAYER} {x} 80 11.5 180 16")
    (evidence / f"server-{marker}").touch()


def recover(server, results, client, evidence, upgrade):
    name = upgrade.name
    drop = drop_selector(upgrade.lower)
    ready(server, evidence, f"mine-{name}", 3574.5)
    wait_signal(client, evidence, f"mined-{name}")
    server.assert_state(f"{name}_takes_one_native_drop_from_the_actual_previous_tier",
                        f"if block {PANEL_POS} minecraft:air if entity {drop}", results)
    server.command(f"execute as {drop} at @s run tp {PLAYER} ~ ~ ~")
    wait_signal(client, evidence, f"picked-{name}")
    server.assert_state(f"{name}_the_player_collects_the_single_original_drop",
                        f'if data entity {PLAYER} Inventory[{{id:"{upgrade.lower}",Count:1b}}] unless entity {drop}', results)


def craft(server, results, client, evidence, upgrade):
    for slot, (ingredient, count) in enumerate(upgrade.stock):
        server.command(f"item replace block {CHEST} container.{slot} with {ingredient} {count}")
    ready(server, evidence, f"recipe-{upgrade.name}", 3568.5)
    wait_signal(client, evidence, f"crafted-{upgrade.name}")
    ingredients = (upgrade.lower, *(value for value, _ in upgrade.stock))
    consumed = " ".join(f'unless data entity {PLAYER} Inventory[{{id:"{ingredient}"}}]' for ingredient in ingredients)
    server.assert_state(f"{upgrade.name}_the_original_panel_and_stock_parts_make_one_upgrade",
                        f'if data entity {PLAYER} Inventory[{{id:"{upgrade.module}",Count:1b}}] {consumed} '
                        f"unless data block {CHEST} Items[0]", results)


def build_and_measure(server, results, client, evidence, upgrade, report):
    ready(server, evidence, f"place-{upgrade.name}", 3574.5)
    wait_signal(client, evidence, f"placed-{upgrade.name}")
    server.assert_state(f"{upgrade.name}_actual_survival_placement_uses_the_original_crafted_module",
                        f'if block {PANEL_POS} {upgrade.module} if data block {PANEL_POS} {{EnergyStored:0}} '
                        f'unless data entity {PLAYER} Inventory[{{id:"{upgrade.module}"}}]', results)
    measure(server, results, upgrade.name + "_midnight", 0)
    server.command("time set noon")
    daylight = measure(server, results, upgrade.name + "_noon", upgrade.fe_per_tick)
    server.command("time set midnight")
    saved = measure(server, results, upgrade.name + "_dark_after_generation", 0)
    report[upgrade.name] = {"daylight": daylight, "saved": saved}


def run_client(args, server, results, report):
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    summary = evidence / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "photovoltaic-upgrades", "--server", f"127.0.0.1:{args.port}", "--output", str(evidence), "--timeout", "205"]
    with (server.directory / "photovoltaic-upgrades-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            check("the_copied_survival_inventory_still_has_nine_original_silicon", 9, player_count(server, "enderio:silicon"), results)
            check("the_copied_survival_inventory_starts_without_a_recovery_pickaxe", 0, player_count(server, PICKAXE), results)
            server.command(f"give {PLAYER} {PICKAXE}")
            for upgrade in UPGRADES:
                recover(server, results, client, evidence, upgrade)
                craft(server, results, client, evidence, upgrade)
                build_and_measure(server, results, client, evidence, upgrade, report)
            (evidence / "server-done").touch()
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"Photovoltaic upgrades client failed; see {evidence}")
            for capture in json.loads(summary.read_text())["client"]["captures"].values():
                if digest(Path(capture["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                    raise AssertionError("Photovoltaic client and server Ender IO jars differ")
        finally:
            stop_client(client)
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def verify_player(directory, report):
    verify_saved_inventory(directory, report, expected={PICKAXE: 1, "enderio:silicon": 9})
    player, = (directory / WORLD / "playerdata").glob("*.dat")
    pickaxe, = (stack for stack in read_data_file(player)["Inventory"] if stack["id"] == PICKAXE)
    if pickaxe["tag"]["Damage"] != 2:
        raise AssertionError(f"Expected exactly two survival breaks, saved pickaxe was {pickaxe}")
    report["savedPickaxe"] = pickaxe


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    for key in ("forge-server", "jar", "launch", "source-world"):
        parser.add_argument(f"--{key}", type=Path, required=True)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    shutil.copytree(args.source_world, directory / WORLD)
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}

    def fresh(server, results):
        server.command("time set midnight")
        server.assert_state("the_copied_save_contains_its_original_energetic_panel_and_wired_charger",
                            f"if block {PANEL_POS} {PANEL} if block {RECEIVER} enderio:wired_charger {stock_remains()}", results)
        report["original"] = sample(server, "photovoltaic")
        run_client(args, server, results, report)

    def reopened(server, results):
        server.assert_state("the_original_vibrant_upgrade_and_wired_charger_survive_restart",
                            f"if block {PANEL_POS} {VIBRANT.module} if block {RECEIVER} enderio:wired_charger {stock_remains()}", results)
        after = measure(server, results, "reopened_vibrant_midnight", 0)
        before = report["vibrant"]["saved"]
        check("the_vibrant_and_charger_keep_exact_saved_balances", [before[key] for key in ("panel", "receiver")],
              [after[key] for key in ("panel", "receiver")], results)
        server.command("time set noon")
        report["reopenedNoon"] = measure(server, results, "reopened_vibrant_noon", VIBRANT.fe_per_tick)

    try:
        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", reopened)
        verify_player(directory, report)
        report["passed"] = True
    finally:
        result = directory / "photovoltaic-upgrades-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Photovoltaic upgrades passed={report['passed']}, {len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
