#!/usr/bin/env python3
"""Rebind and place a configured stock Powered Spawner in packaged Forge."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_legacy_painting import stack
from packaged_nutritious_stick import advance
from packaged_server_smoke import ROOT, digest
from packaged_soul_binder import VIAL, item, xp_tank
from packaged_spawners import PLAYER, SPAWNER, prepare_profile, run_stage


BINDER = "4882 80 8"
SOURCE = "4885 80 8"
PLACED = "4887 80 8"
BARREL = "4884 80 13"
VIAL_HOPPER = "4881 80 8"
SPAWNER_HOPPER = "4883 80 8"
SIGNAL = "4888 80 8"
CAP = "enderio:octadic_capacitor"
RECIPE = "enderio:soulbinding/powered_spawner"
COW = '{entity_type:"minecraft:cow",entity_tag:{CustomName:\'{"text":"Daisy"}\',Health:18.0f,NoAI:1b}}'
PIG = '{entity_type:"minecraft:pig",entity_tag:{CustomName:\'{"text":"Hog"}\',Health:16.0f}}'
DATA = '"enderio:data"'
SOUL = '"enderio:soul"'
IO = '{Modes:{north:"disable"}}'
REDSTONE = '"active_with_signal"'
ENERGY = 877
PIG_COST = 12000
XP = 2240
XP_LEFT = 37
FE = 288000
PAID = 400
FE_LEFT = 317


def vial_tag(soul):
    return f"{{{DATA}:{{{SOUL}:{soul}}}}}"


def spawner_tag(soul):
    state = (f'{SOUL}:{soul},"enderio:energy":{ENERGY},"enderio:io_config":{IO},'
             f'"enderio:redstone_control":{REDSTONE},"enderio:container":{{Items:[{stack(0, 1, CAP)}]}}')
    return f"{{{DATA}:{{{state}}}}}"


def machine(soul, energy, pos):
    return (f'if data block {pos} {{EntityStorage:{soul},EnergyStored:{energy},RedstoneControl:{REDSTONE},'
            f'IOConfig:{IO},Items:{{Items:[{stack(0, 1, CAP)}]}}}}')


def condition(scope, pos, path, item_id, metadata=None, slot=None, vanilla=False):
    query = item(slot, item_id, f"tag:{metadata}" if metadata else "", vanilla=vanilla)
    return f"if data {scope} {pos} {path}[{query}]"


def prepare(server, results):
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    server.command("forceload add 4880 0 4895 15")
    server.assert_state("rebinding_chunk_is_loaded", f"if loaded {BINDER} if loaded {PLACED}", results)
    server.command("fill 4880 79 2 4895 79 15 minecraft:grass_block")
    server.command("setworldspawn 4882 80 11")
    server.command(f"setblock {BARREL} minecraft:barrel")
    server.command(f"setblock {SOURCE} {SPAWNER}[facing=south]")
    server.command(f"data merge block {SOURCE} {{EntityStorage:{COW},EnergyStored:{ENERGY},RedstoneControl:{REDSTONE},"
                   f"IOConfig:{IO},Items:{{Size:3,Items:[{stack(0, 1, CAP)}]}}}}")
    server.assert_state("native_cow_spawner_has_charge_configuration_and_capacitor", machine(COW, ENERGY, SOURCE), results)
    server.command(f"loot replace block {BARREL} container.0 mine {SOURCE} minecraft:diamond_pickaxe")
    server.command(f"setblock {SOURCE} minecraft:air")
    server.command(f"data get block {BARREL} Items")
    server.assert_state("native_block_loot_packs_the_original_spawner_configuration",
                        condition("block", BARREL, "Items", SPAWNER, spawner_tag(COW), 0, True), results)
    server.command(f"setblock {BINDER} enderio:soul_binder[facing=south]")
    server.command(f"data merge block {BINDER} {{EnergyStored:{PAID},{xp_tank(XP - 1)},"
                   f"Items:{{Size:5,Items:[{stack(4, 1, CAP)}]}}}}")


def original_inputs():
    vial = stack(0, 1, VIAL, metadata=vial_tag(PIG))
    item = stack(1, 1, SPAWNER, metadata=spawner_tag(COW))
    return f"Items:{{Items:[{vial},{item}]}}"


def pending():
    return (f'if data block {BINDER} {{EnergyStored:0,{xp_tank(XP + XP_LEFT)},{original_inputs()},'
            f'Task:{{RecipeId:"{RECIPE}",ProgressMade:{PAID},ProgressRequired:{FE}}}}} '
            f'unless data block {BINDER} Items.Items[{{Slot:2}}]')


def below_threshold(server, results):
    advance(server)
    server.assert_state("one_mb_below_cost_preserves_both_inputs_and_charge",
                        f"if data block {BINDER} {{EnergyStored:{PAID},{xp_tank(XP - 1)},{original_inputs()}}} "
                        f"unless data block {BINDER} Task", results)
    server.command(f"data merge block {BINDER} {{{xp_tank(XP + XP_LEFT)}}}")
    server.assert_state("partial_job_pays_only_400_fe_and_keeps_original_items", pending(), results)


def completed():
    product = stack(2, 1, SPAWNER, metadata=spawner_tag(PIG))
    return (f"if data block {BINDER} {{EnergyStored:{FE_LEFT},{xp_tank(XP_LEFT)},"
            f"Items:{{Items:[{product},{stack(3, 1, VIAL)}]}}}} "
            f"unless data block {BINDER} Task unless data block {BINDER} Items.Items[{{Slot:0}}] "
            f"unless data block {BINDER} Items.Items[{{Slot:1}}] "
            f"unless data block {BINDER} Items.Items[{{Slot:2}}].tag.{DATA}.{SOUL}.entity_tag.NoAI "
            f"unless data block {BINDER} Items.Items[{{Slot:3}}].tag.{DATA}.{SOUL}")


def finish_binding(server, results):
    server.command(f"data merge block {BINDER} {{EnergyStored:{FE - PAID + FE_LEFT}}}")
    server.assert_state("binding_replaces_cow_nbt_keeps_configuration_and_pays_exact_cost", completed(), results, timeout=90)


def load_hoppers(server, results):
    server.command(f"setblock {VIAL_HOPPER} minecraft:hopper[facing=east]"
                   f"{{Items:[{stack(0, 1, VIAL, metadata=vial_tag(PIG), slot_suffix='b', count_suffix='b')}]}}")
    server.command(f"setblock {SPAWNER_HOPPER} minecraft:hopper[facing=west]")
    server.command(f"item replace block {SPAWNER_HOPPER} container.0 from block {BARREL} container.0")
    server.command(f"item replace block {BARREL} container.0 with minecraft:air")
    server.assert_state("vanilla_hoppers_feed_the_native_rebinding_recipe",
                        f"if data block {BINDER} {{{original_inputs()}}} "
                        f"unless data block {VIAL_HOPPER} Items[0] unless data block {SPAWNER_HOPPER} Items[0]", results)


def spawn_with_rebound_spawner(server, results):
    server.assert_state("survival_placement_restores_pig_and_original_machine_configuration",
                        f"if block {PLACED} {SPAWNER} {machine(PIG, ENERGY, PLACED)} "
                        f'unless data entity {PLAYER} Inventory[{{id:"{SPAWNER}"}}] '
                        f"unless data block {PLACED} EntityStorage.entity_tag.NoAI "
                        "unless entity @e[type=minecraft:pig] unless entity @e[type=minecraft:cow]", results)
    server.command(f"setblock {SIGNAL} minecraft:redstone_block")
    server.assert_state("redstone_releases_the_carried_fe_into_a_stock_pig_job",
                        f'{machine(PIG, 0, PLACED)} if data block {PLACED} {{Task:{{EntityType:"minecraft:pig",'
                        f'SpawnMode:"entity_type",EnergyCost:{PIG_COST},EnergyConsumed:{ENERGY}}}}} '
                        "unless entity @e[type=minecraft:pig]", results)
    server.command(f"data merge block {PLACED} {{EnergyStored:{PIG_COST - ENERGY}}}")
    server.assert_state("the_rebound_spawner_spawns_pigs_on_the_stock_pig_budget",
                        f"{machine(PIG, 0, PLACED)} if entity @e[type=minecraft:pig] unless entity @e[type=minecraft:cow]",
                        results, timeout=30)
    server.command(f"setblock {SIGNAL} minecraft:air")


def client_session(args, server, report):
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    summary = evidence / "summary.json"
    results = report["assertions"]
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "spawner-rebinding", "--server", f"127.0.0.1:{args.port}", "--output", str(evidence), "--timeout", "360"]
    with (server.directory / "spawner-rebinding-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            server.command(f"tp {PLAYER} 4882.5 80 10.5 180 15")
            server.command(f"item replace entity {PLAYER} hotbar.3 with {VIAL}{vial_tag(PIG)}")
            server.command(f"item replace entity {PLAYER} hotbar.5 from block {BARREL} container.0")
            server.command(f"item replace block {BARREL} container.0 with minecraft:air")
            (evidence / "server-initial").touch()
            wait_signal(client, evidence, "queued")
            server.assert_state("jei_moves_the_single_native_drop_and_filled_vial",
                                f"if data block {BINDER} {{{original_inputs()}}} "
                                f'unless data entity {PLAYER} Inventory[{{id:"{SPAWNER}"}}] '
                                f'unless data entity {PLAYER} Inventory[{{id:"{VIAL}"}}]', results)
            below_threshold(server, results)
            finish_binding(server, results)
            (evidence / "server-complete").touch()
            wait_signal(client, evidence, "taken")
            rebound = condition("entity", PLAYER, "Inventory", SPAWNER, spawner_tag(PIG), vanilla=True)
            empty = condition("entity", PLAYER, "Inventory", VIAL, vanilla=True)
            server.assert_state("native_quick_move_consumes_both_machine_outputs_and_returns_one_of_each",
                                f"{rebound} {empty} unless data block {BINDER} Items.Items[{{Slot:2}}] "
                                f"unless data block {BINDER} Items.Items[{{Slot:3}}] "
                                f'unless data entity {PLAYER} Inventory[{{id:"{VIAL}"}}].tag.{DATA}.{SOUL}', results)
            server.command(f"tp {PLAYER} 4887.5 80 10.5 180 25")
            (evidence / "server-place").touch()
            spawn_with_rebound_spawner(server, results)
            (evidence / "server-placed").touch()
            if client.wait(timeout=35) != 0:
                raise AssertionError(f"Spawner rebinding client failed; see {evidence}")
            mods = json.loads(summary.read_text())["client"]["captures"]["rebound"]["loadedMods"]
            for mod, key in (("enderio", "jarSha256"), ("jei", "jeiSha256")):
                matched = digest(Path(mods[mod]["file"])) == report[key]
                results.append({"assertion": f"{mod}_client_and_server_jars_match", "passed": matched})
                if not matched:
                    raise AssertionError(f"Unexpected {mod} jar in {evidence}")
        finally:
            stop_client(client)
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", type=int, required=True)
    for path in ("java", "forge-server", "jar"):
        parser.add_argument(f"--{path}", type=Path, required=True)
    parser.add_argument("--launch", type=Path)
    parser.add_argument("--jei", type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")
    if (args.launch is None) != (args.jei is None):
        parser.error("--launch and --jei must be supplied together")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}
    if args.jei:
        shutil.copyfile(args.jei, directory / "mods/jei.jar")
        report["jeiSha256"] = digest(directory / "mods/jei.jar")

    def fresh(server, results):
        prepare(server, results)
        if args.launch:
            client_session(args, server, report)
        else:
            load_hoppers(server, results)
            below_threshold(server, results)

    def reopened(server, results):
        if args.launch:
            server.assert_state("placed_pig_spawner_and_its_spawn_survive_restart",
                                f"{machine(PIG, 0, PLACED)} if entity @e[type=minecraft:pig] unless entity @e[type=minecraft:cow]",
                                results)
        else:
            server.assert_state("paid_rebinding_progress_and_original_inputs_survive_restart", pending(), results)
            finish_binding(server, results)

    try:
        run_stage(args, directory, report, "fresh", fresh, startup_timeout=300)
        run_stage(args, directory, report, "reopened", reopened, startup_timeout=300)
        if not args.launch:
            run_stage(args, directory, report, "finished",
                      lambda server, results: server.assert_state("rebound_outputs_survive_restart", completed(), results),
                      startup_timeout=300)
        report["passed"] = True
    finally:
        path = directory / "spawner-rebinding-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Spawner rebinding passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
