#!/usr/bin/env python3
"""Exercise the saved and crafted Powered Spawner on a packaged Forge server."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_server_smoke import ROOT, Server, digest


WORLD = "port-spawners"
MACHINE = "1300 80 8"
VANILLA_SPAWNER = "1305 80 10"
BARREL = "1306 80 10"
LEGACY_DROP_SOURCE = "1309 80 10"
CRAFTED = "1302 80 10"
CARRIED = LEGACY_DROP_SOURCE
TABLE = "1304 80 11"
PLAYER = "EnderIOQA"
SPAWNER = "enderio:powered_spawner"
COWS = "@e[type=minecraft:cow,x=1300.5,y=80,z=8.5,distance=..8]"
COW_SOUL = '{entity_type:"minecraft:cow"}'
BOUND = f'tag:{{"enderio:data":{{"enderio:soul":{COW_SOUL}}}}}'
OLD_SOUL = 'EntityStorage:{Entity:{id:"minecraft:cow"}}'
CAPACITOR = 'id:"enderio:octadic_capacitor"'
COST = 10000
LEGACY_PROGRESS = 4321
CARRY_ENERGY = 877


def setup_scene(server, results):
    server.command("forceload add 1296 0 1311 15")
    server.assert_state("spawner_test_chunk_loaded", f"if loaded {MACHINE} if loaded {BARREL}", results)
    server.command("gamerule doMobSpawning false")
    server.command("fill 1296 78 3 1312 78 14 minecraft:stone")
    server.command(f"setblock {BARREL} minecraft:barrel")
    mine_spawner(server, 0)


def mine_spawner(server, slot):
    server.command(f"setblock {VANILLA_SPAWNER} minecraft:spawner"
                   '{Delay:32767s,SpawnData:{entity:{id:"minecraft:cow"}}}')
    server.command(f"loot replace block {BARREL} container.{slot} mine {VANILLA_SPAWNER} minecraft:diamond_pickaxe")
    server.command(f"setblock {VANILLA_SPAWNER} minecraft:air")


def assert_broken_spawner(server, results, legacy, slot=0):
    tag = (f'BlockEntityTag:{{{OLD_SOUL}}}' if legacy else
           f'"enderio:data":{{"enderio:soul":{COW_SOUL}}}')
    name = "native_loot_creates_typed_broken_spawner" if slot != 0 else "vanilla_loot_retains_mob_type"
    server.assert_state(name,
                        f'if data block {BARREL} Items[{{Slot:{slot}b,id:"enderio:broken_spawner",Count:1b,tag:{{{tag}}}}}]', results)


def legacy_fixture(server, results):
    setup_scene(server, results)
    assert_broken_spawner(server, results, True)
    server.command(f"setblock {MACHINE} enderio:powered_spawner[facing=south]")
    server.command(f'data merge block {MACHINE} {{{OLD_SOUL},Energy:{{EnergyStored:{LEGACY_PROGRESS}}},IoConfig:{{"3":2b}},'
                   f'Items:{{Size:1,Items:[{{Slot:0,{CAPACITOR},Count:1b}}]}}}}')
    server.assert_state("official_machine_records_native_spawn_progress",
                        f'if data block {MACHINE} {{{OLD_SOUL},Energy:{{EnergyStored:0}},Task:{{EnergyConsumed:{LEGACY_PROGRESS}}}}} '
                        f'unless entity {COWS}', results, timeout=20)

    server.command(f"setblock {LEGACY_DROP_SOURCE} enderio:powered_spawner[facing=south]")
    server.command(f'data merge block {LEGACY_DROP_SOURCE} {{{OLD_SOUL},'
                   f'IoConfig:{{"2":4b}},RedstoneControl:3,Items:{{Size:1,Items:[{{Slot:0,{CAPACITOR},Count:1b}}]}}}}')
    # The legacy machine refreshes its redstone cache on a neighbor change.
    server.command("setblock 1309 81 10 minecraft:stone")
    server.command(f"data merge block {LEGACY_DROP_SOURCE} {{Energy:{{EnergyStored:{CARRY_ENERGY}}}}}")
    server.command(f"loot replace block {BARREL} container.1 mine {LEGACY_DROP_SOURCE} minecraft:diamond_pickaxe")
    server.command(f"setblock {LEGACY_DROP_SOURCE} minecraft:air")
    server.command(f"data get block {BARREL} Items")
    server.assert_state("official_powered_spawner_loot_carries_state",
                        f'if data block {BARREL} Items[{{Slot:1b,id:"enderio:powered_spawner",Count:1b,tag:{{BlockEntityTag:{{'
                        f'{OLD_SOUL},Energy:{{EnergyStored:{CARRY_ENERGY}}},IoConfig:{{"2":4b}},RedstoneControl:3,'
                        f'Items:{{Items:[{{Slot:0,{CAPACITOR},Count:1b}}]}}}}}}}}]', results)


def pending_spawn(progress):
    return (f'if data block {MACHINE} {{EnergyStored:0,EntityStorage:{COW_SOUL},'
            f'Task:{{EntityType:"minecraft:cow",SpawnMode:"entity_type",EnergyCost:{COST},EnergyConsumed:{progress}}}}} '
            f'unless entity {COWS}')


def verify_legacy_state(server, results):
    server.command(f"data get block {MACHINE}")
    server.command(f"data get block {BARREL} Items")
    server.assert_state("saved_6_2_machine_keeps_soul_progress_capacitor_and_io",
                        f'{pending_spawn(LEGACY_PROGRESS)} if data block {MACHINE} {{IOConfig:{{Modes:{{south:"pull"}}}},'
                        f'Items:{{Items:[{{Slot:0,{CAPACITOR},Count:1}}]}}}}', results)
    assert_broken_spawner(server, results, False)
    server.assert_state("carried_6_2_spawner_keeps_soul_charge_capacitor_and_io",
                        f'if data block {BARREL} Items[{{Slot:1b,id:"enderio:powered_spawner",Count:1b,tag:{{'
                        f'"enderio:data":{{"enderio:soul":{COW_SOUL},"enderio:energy":{CARRY_ENERGY},'
                        '"enderio:io_config":{Modes:{north:"disable"}},"enderio:redstone_control":"never_active"},'
                        f'BlockEntityTag:{{Items:{{Items:[{{Slot:0,{CAPACITOR},Count:1b}}]}}}}}}}}] '
                        f'unless data block {BARREL} Items[{{Slot:1b}}].tag.BlockEntityTag.EntityStorage', results)


def finish_spawn(server, results, progress):
    server.assert_state("partial_spawn_task_survives_restart", pending_spawn(progress), results)
    server.command(f"data merge block {MACHINE} {{EnergyStored:{COST - progress}}}")
    server.assert_state("remaining_budget_spawns_the_original_mob",
                        f"if entity {COWS} if data block {MACHINE} {{EntityStorage:{COW_SOUL},EnergyStored:0}}", results, timeout=20)


def carry_state(energy, redstone):
    return (f'if block {CARRIED} {SPAWNER} if data block {CARRIED} {{EntityStorage:{COW_SOUL},EnergyStored:{energy},'
            f'RedstoneControl:"{redstone}",IOConfig:{{Modes:{{north:"disable"}}}},'
            f'Items:{{Items:[{{Slot:0,{CAPACITOR},Count:1}}]}}}}')


def crafted_placement_state():
    return (f'if block {CRAFTED} {SPAWNER} if data block {CRAFTED} {{EntityStorage:{COW_SOUL},EnergyStored:0}}')


def filled_vial_state():
    return (f'{carry_state(0, "always_active")} if data block {CARRIED} {{Mode:"capture",'
            f'Items:{{Items:[{{Slot:2,id:"enderio:soul_vial",Count:1,{BOUND}}}]}}}} '
            f'unless data block {CARRIED} Items.Items[{{Slot:1}}] unless entity @e[type=minecraft:cow]')


def exercise_client(args, server, report):
    results = report["assertions"]
    finish_spawn(server, results, LEGACY_PROGRESS)
    server.command(f"kill {COWS}")
    mine_spawner(server, 2)
    assert_broken_spawner(server, results, False, slot=2)
    server.command("fill 1298 79 10 1311 79 14 minecraft:stone")
    server.command("setblock 1309 81 10 minecraft:air")
    server.command(f"setblock {TABLE} minecraft:crafting_table")
    output = ROOT / "build/port-evidence" / (args.name + "-client")
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
               "--launch", str(args.launch.resolve()), "--scenario", "powered-spawner", "--output", str(output),
               "--server", f"127.0.0.1:{args.port}", "--timeout", "225"]
    with (server.directory / "spawner-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            ingredients = ("soularium_ingot", None, "soularium_ingot", "soularium_ingot", "ensouled_chassis",
                           "soularium_ingot", "vibrant_crystal", "z_logic_controller", "vibrant_crystal")
            for slot, ingredient in enumerate(ingredients):
                source = f"with enderio:{ingredient}" if ingredient else f"from block {BARREL} container.0"
                server.command(f"item replace entity {PLAYER} hotbar.{slot} {source}")
            server.command(f"item replace block {BARREL} container.0 with minecraft:air")
            server.command(f"tp {PLAYER} 1304.5 80 13.5 180 14")
            wait_signal(client, output, "taken")
            consumed = ("broken_spawner", "soularium_ingot", "ensouled_chassis", "vibrant_crystal", "z_logic_controller")
            no_ingredients = " ".join(f'unless data entity {PLAYER} Inventory[{{id:"enderio:{name}"}}]' for name in consumed)
            server.assert_state("native_recipe_consumes_ingredients_and_returns_bound_machine",
                                f'if data entity {PLAYER} Inventory[{{id:"{SPAWNER}",Count:1b,{BOUND}}}] {no_ingredients}', results)
            server.command(f"tp {PLAYER} 1302.5 80 13.5 180 14")
            wait_signal(client, output, "placed-crafted")
            server.assert_state("native_crafted_placement_consumes_item_and_retains_cow",
                                f'{crafted_placement_state()} unless data entity {PLAYER} Inventory[{{id:"{SPAWNER}"}}]', results)

            server.command(f"item replace entity {PLAYER} hotbar.0 from block {BARREL} container.1")
            server.command(f"item replace block {BARREL} container.1 with minecraft:air")
            server.command(f"tp {PLAYER} 1309.5 80 13.5 180 14")
            wait_signal(client, output, "placed-carried")
            server.command(f"data get block {CARRIED}")
            server.assert_state("native_legacy_item_placement_restores_disabled_charged_machine",
                                f'{carry_state(CARRY_ENERGY, "never_active")} '
                                f'unless data entity {PLAYER} Inventory[{{id:"{SPAWNER}"}}]', results)
            (output / "server-placed-carried").touch()
            wait_signal(client, output, "capture-mode")
            server.command(f"item replace entity {PLAYER} hotbar.0 with enderio:soul_vial")
            wait_signal(client, output, "vial-inserted")
            server.assert_state("native_menu_switches_to_capture_and_spends_only_carried_charge",
                                f'{carry_state(0, "always_active")} if data block {CARRIED} {{Mode:"capture",'
                                f'Task:{{EnergyCost:{COST},EnergyConsumed:{CARRY_ENERGY}}},'
                                'Items:{Items:[{Slot:1,id:"enderio:soul_vial",Count:1}]}} '
                                f'unless data block {CARRIED} Items.Items[{{Slot:2}}]', results)
            server.command(f"data merge block {CARRIED} {{EnergyStored:{COST - CARRY_ENERGY}}}")
            server.assert_state("capture_consumes_empty_vial_and_exact_remaining_energy", filled_vial_state(), results, timeout=20)
            (output / "server-verified").touch()
            if client.wait(timeout=35) != 0:
                raise AssertionError(f"Powered Spawner client failed; see {output}")
            report["client"] = json.loads((output / "summary.json").read_text())
            frame = report["client"]["client"]["captures"]["powered-spawner-capture"]
            matched = digest(Path(frame["loadedMods"]["enderio"]["file"])) == report["jarSha256"]
            results.append({"assertion": "client_and_server_used_the_same_jar", "passed": matched})
            if not matched:
                raise AssertionError("Powered Spawner client and server jars differ")
        finally:
            stop_client(client)
            summary = output / "summary.json"
            if "client" not in report and summary.exists():
                report["client"] = json.loads(summary.read_text())


def verify_client_results_after_restart(server, results):
    server.assert_state("crafted_machine_retains_cow_after_restart", crafted_placement_state(), results)
    server.assert_state("replanted_machine_and_captured_vial_survive_restart", filled_vial_state(), results)


def prepare_profile(args, directory, jar):
    directory.mkdir()
    shutil.copytree(args.forge_server / "libraries", directory / "libraries")
    (directory / "mods").mkdir()
    shutil.copyfile(jar, directory / "mods/enderio.jar")
    (directory / "eula.txt").write_text("eula=true\n")
    flat = '{"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:plains"}'
    (directory / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={args.port}\nlevel-name={WORLD}\n"
        f"level-type=minecraft:flat\ngenerator-settings={flat}\n"
        "generate-structures=false\nview-distance=3\nsimulation-distance=3\n"
        "online-mode=false\nenforce-secure-profile=false\n")


def run_stage(args, directory, report, name, scenario):
    server = Server(directory, args.java, name)
    try:
        server.wait('For help, type "help"', timeout=150)
        scenario(server, report["assertions"])
        server.command("save-all flush")
    finally:
        exit_code = server.stop()
        errors = [line.strip() for line in server.lines if "/ERROR]" in line or "/FATAL]" in line]
        registry_errors = [line for line in errors if "[ne.mi.re.GameData/REGISTRIES]" in line]
        runtime_errors = [line for line in errors if line not in registry_errors]
        report["servers"].append({"stage": name, "exit": exit_code, "errors": runtime_errors,
                                  "registryErrors": registry_errors, "log": str(server.log_path)})
    if exit_code or runtime_errors or (registry_errors and name != "converted"):
        raise RuntimeError(f"Forge errors in {name}; see {server.log_path}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--legacy-jar", required=True, type=Path)
    parser.add_argument("--launch", type=Path, help="Exercise the crafting and capture menus using a guarded client")
    parser.add_argument("--port", type=int, default=25735)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")

    directory = ROOT / "build/port-runtime" / args.name
    directory.mkdir()
    legacy = directory / "legacy"
    port = directory / "port"
    prepare_profile(args, legacy, args.legacy_jar)
    prepare_profile(args, port, args.jar)
    report = {"passed": False, "jarSha256": digest(port / "mods/enderio.jar"),
              "legacySha256": digest(legacy / "mods/enderio.jar"), "assertions": [], "servers": []}
    try:
        run_stage(args, legacy, report, "legacy", legacy_fixture)
        shutil.copytree(legacy / WORLD, port / WORLD)
        run_stage(args, port, report, "converted", verify_legacy_state)
        if args.launch:
            run_stage(args, port, report, "restart", lambda server, _: exercise_client(args, server, report))
            run_stage(args, port, report, "reopened", verify_client_results_after_restart)
        else:
            run_stage(args, port, report, "restart", lambda server, results: finish_spawn(server, results, LEGACY_PROGRESS))
        report["passed"] = True
    finally:
        output = directory / "spawner-results.json"
        output.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Spawners passed={report['passed']}, {len(report['assertions'])} assertions, {output}", flush=True)


if __name__ == "__main__":
    main()
