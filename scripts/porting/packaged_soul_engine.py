#!/usr/bin/env python3
"""Exercise the Soul Engine using packaged Forge dedicated servers."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage


ENGINE = "1452 80 8"
BARREL = "1455 80 10"
DROP_SOURCE = "1457 80 8"
CARRIED = "1459 80 9"
TANK = "1453 80 8"
CHARGER = "1451 80 8"
BINDER = "1464 80 8"
PLAYER = "EnderIOQA"
ENGINE_ITEM = "enderio:soul_engine"
CAPACITOR = 'id:"enderio:basic_capacitor"'
OLD_SOUL = 'EntityStorage:{Entity:{id:"minecraft:blaze"}}'
BLAZE = '{entity_type:"minecraft:blaze"}'
BLAZE_PAYLOAD = '{entity_type:"minecraft:blaze",entity_tag:{}}'
ENERGY = 12345
PROGRESS = 11
FUEL = 3
AFTER_LAVA = ENERGY + 3 * 1200
AFTER_WATER = AFTER_LAVA + 2 * 1350


def tagged_lava(amount):
    return f'FluidName:"minecraft:lava",Amount:{amount},Tag:{{PortSoulEngine:70}}'


def lava_tank(amount):
    return f'Fluids:{{Tanks:[{{Index:0,Contents:{{{tagged_lava(amount)}}}}}]}}'


def legacy_state(energy, progress):
    return (f'{OLD_SOUL},BurnedTicks:{progress},Energy:{{EnergyStored:{energy}}},RedstoneControl:3,'
            f'Fluid:{{Tanks:[{{Index:0,{tagged_lava(FUEL)}}}]}},IoConfig:{{"2":4b}},'
            f'Items:{{Items:[{{Slot:0,{CAPACITOR},Count:1b}}]}}')


def migrated_engine(energy, progress):
    return (f'{{EntityStorage:{BLAZE},EnergyStored:{energy},BurnedTicks:{progress},'
            f'RedstoneControl:"never_active",IOConfig:{{Modes:{{north:"disable"}}}},{lava_tank(FUEL)},'
            f'Items:{{Items:[{{Slot:0,{CAPACITOR},Count:1}}]}}}}')


def seed_legacy(server, where, energy, progress):
    server.command(f"setblock {where} {ENGINE_ITEM}[facing=south]")
    server.command(f'data merge block {where} {{RedstoneControl:3,IoConfig:{{"2":4b}}}}')
    server.command(f"execute positioned {where} run setblock ~ ~1 ~ minecraft:stone")
    server.command(f'data merge block {where} {{{OLD_SOUL},BurnedTicks:{progress},Energy:{{EnergyStored:{energy}}},'
                   f'Fluid:{{Tanks:[{{Index:0,Capacity:2000,{tagged_lava(FUEL)}}}]}},'
                   f'Items:{{Size:1,Items:[{{Slot:0,{CAPACITOR},Count:1b}}]}}}}')
    server.command(f"data get block {where}")


def legacy_fixture(server, results):
    server.command("forceload add 1440 0 1471 15")
    server.assert_state("engine_fixture_chunks_loaded", f"if loaded {ENGINE} if loaded {DROP_SOURCE}", results)
    seed_legacy(server, ENGINE, ENERGY, PROGRESS)
    server.assert_state("official_engine_saves_native_fluid_and_paid_ticks",
                        f"if data block {ENGINE} {{{legacy_state(ENERGY, PROGRESS)}}}", results)
    seed_legacy(server, DROP_SOURCE, ENERGY + 1, PROGRESS + 1)
    server.command(f"setblock {BARREL} minecraft:barrel")
    server.command(f"loot replace block {BARREL} container.0 mine {DROP_SOURCE} minecraft:diamond_pickaxe")
    server.command(f"data get block {BARREL} Items")
    server.assert_state("official_drop_contains_bound_engine",
                        f'if data block {BARREL} Items[{{Slot:0b,id:"{ENGINE_ITEM}",Count:1b,tag:{{BlockEntityTag:{{'
                        f'{legacy_state(ENERGY + 1, PROGRESS + 1)}}}}}}}}}]', results)


def verify_legacy_conversion(server, results):
    server.command(f"data get block {ENGINE}")
    server.command(f"data get block {BARREL} Items")
    server.assert_state("placed_legacy_engine_keeps_soul_charge_fuel_and_progress",
                        f'if data block {ENGINE} {migrated_engine(ENERGY, PROGRESS)}', results)
    portable = (f'{{Slot:0b,id:"{ENGINE_ITEM}",Count:1b,tag:{{"enderio:data":{{"enderio:soul":{BLAZE},'
                f'"enderio:energy":{ENERGY + 1},"enderio:redstone_control":"never_active",'
                f'"enderio:item_fluid_content":{{{tagged_lava(FUEL)}}},'
                '"enderio:io_config":{Modes:{north:"disable"}}},'
                f'BlockEntityTag:{{BurnedTicks:{PROGRESS + 1},Fluid:{{Tanks:[{{Index:0,{tagged_lava(FUEL)}}}]}},'
                f'Items:{{Items:[{{Slot:0,{CAPACITOR},Count:1b}}]}}}}}}}}')
    server.assert_state("legacy_drop_restores_typed_soul_and_keeps_native_fuel_and_progress",
                        f'if data block {BARREL} Items[{portable}] '
                        f'unless data block {BARREL} Items[{{Slot:0b}}].tag.BlockEntityTag.EntityStorage', results)


def charge_state(energy):
    return (f'if block {ENGINE} {ENGINE_ITEM}[powered=false] '
            f'if data block {ENGINE} {{EnergyStored:{energy},BurnedTicks:0,EntityStorage:{BLAZE}}} '
            f'unless data block {ENGINE} Fluids.Tanks[0].Contents.FluidName')


def bind_a_fresh_engine(server, results):
    server.command(f"setblock {BINDER} enderio:soul_binder")
    xp = 'Fluids:{Tanks:[{Index:0,Contents:{FluidName:"enderio:fluid_xp_juice_still",Amount:1127}}]}'
    blaze_vial = f'{{Slot:0,id:"enderio:soul_vial",Count:1,tag:{{"enderio:data":{{"enderio:soul":{BLAZE_PAYLOAD}}}}}}}'
    server.command(f'data merge block {BINDER} {{EnergyStored:188000,{xp},Items:{{Size:5,Items:[{blaze_vial},'
                   f'{{Slot:1,id:"{ENGINE_ITEM}",Count:1}},{{Slot:4,id:"enderio:octadic_capacitor",Count:1}}]}}}}')
    server.command(f"data get block {BINDER}")
    result = f'{{Slot:2,id:"{ENGINE_ITEM}",Count:1,tag:{{"enderio:data":{{"enderio:soul":{BLAZE}}}}}}}'
    server.assert_state("real_soul_binder_recipe_binds_fresh_engine_and_returns_empty_vial",
                        f'if data block {BINDER} {{EnergyStored:0,Fluids:{{Tanks:[{{Index:0,Contents:{{Amount:27}}}}]}},'
                        f'Items:{{Items:[{result},{{Slot:3,id:"enderio:soul_vial",Count:1}}]}}}} '
                        f'unless data block {BINDER} Items.Items[{{Slot:0}}] unless data block {BINDER} Items.Items[{{Slot:1}}] '
                        f'unless data block {BINDER} Items.Items[{{Slot:3}}].tag."enderio:data"."enderio:soul"', results, timeout=65)


def reload_fuel(server, results):
    water = 'Fluids:{Tanks:[{Index:0,Contents:{FluidName:"minecraft:water",Amount:2}}]}'
    server.command(f"setblock {TANK} enderio:fluid_tank[facing=south]")
    # The south-facing tank's world-west side is stored as local east.
    server.command(f'data merge block {TANK} {{{water},IOConfig:{{Modes:{{east:"push"}}}}}}')
    server.command(f"data get block {TANK}")
    server.assert_state("native_tank_cannot_feed_water_to_the_original_lava_rule",
                        f"{charge_state(AFTER_LAVA)} if data block {TANK} {{{water}}}", results)
    override = server.directory / WORLD / "datapacks/soul-engine-rules/data/enderio/eio_soul/engine/minecraft_blaze.json"
    override.parent.mkdir(parents=True, exist_ok=True)
    override.write_text(json.dumps({"entity": "minecraft:blaze", "fluid": "minecraft:water", "power/mb": 900, "tick/mb": 4}) + "\n")
    with server.condition:
        start = len(server.lines)
    server.command("reload")
    server.wait("Data loader for eio_soul/engine loaded", start=start)
    server.assert_state("running_engine_pulls_two_water_and_generates_2700_fe_after_reload",
                        f"{charge_state(AFTER_WATER)} unless data block {TANK} Fluids.Tanks[0].Contents.FluidName", results, timeout=20)


def export_energy(server, results):
    server.command(f"setblock {CHARGER} enderio:wired_charger")
    server.command(f'data merge block {CHARGER} {{Items:{{Size:3,Items:[{{Slot:0,{CAPACITOR},Count:1}}]}}}}')
    server.assert_state("generator_exports_exact_fe_through_forge_energy_capability",
                        f"{charge_state(0)} if data block {CHARGER} {{EnergyStored:{AFTER_WATER}}}", results)


def run_client(args, server, report):
    results = report["assertions"]
    server.assert_state("placed_engine_keeps_all_saved_state_on_first_port_restart",
                        f"if data block {ENGINE} {migrated_engine(ENERGY, PROGRESS)}", results)
    server.command("fill 1449 79 8 1462 79 14 minecraft:stone")
    server.command("gamerule spawnRadius 0")
    server.command("setworldspawn 1458 80 12")
    output = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
               "--launch", str(args.launch.resolve()), "--scenario", "soul-engine", "--output", str(output),
               "--server", f"127.0.0.1:{args.port}", "--timeout", "225"]
    with (server.directory / "soul-engine-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            server.command(f"item replace entity {PLAYER} hotbar.0 from block {BARREL} container.0")
            server.command(f"item replace block {BARREL} container.0 with minecraft:air")
            server.command(f"tp {PLAYER} 1459.5 80 12.5 180 15")
            wait_signal(client, output, "placed")
            server.command(f"data get block {CARRIED}")
            server.assert_state("real_placement_retains_carried_soul_fuel_capacitor_charge_and_progress",
                                f"if data block {CARRIED} {migrated_engine(ENERGY + 1, PROGRESS + 1)} "
                                f'unless data entity {PLAYER} Inventory[{{id:"{ENGINE_ITEM}"}}]', results)
            server.command(f"tp {PLAYER} 1452.5 80 11.5 180 15")
            wait_signal(client, output, "enabled")
            server.assert_state("saved_lava_burn_resumes_and_three_mb_generate_3600_fe", charge_state(AFTER_LAVA), results, timeout=18)
            (output / "server-lava").touch()
            wait_signal(client, output, "lava-seen")
            reload_fuel(server, results)
            (output / "server-water").touch()
            if client.wait(timeout=35) != 0:
                raise AssertionError(f"Soul Engine client failed; see {output}")
            summary = json.loads((output / "summary.json").read_text())
            frame = summary["client"]["captures"]["soul-engine-reloaded"]
            if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Client and server Soul Engine jars differ")
        finally:
            stop_client(client)
            summary_file = output / "summary.json"
            report["client"] = json.loads(summary_file.read_text()) if summary_file.exists() else None
    export_energy(server, results)


def run_headless(server, results):
    server.assert_state("placed_engine_keeps_all_saved_state_on_first_port_restart",
                        f"if data block {ENGINE} {migrated_engine(ENERGY, PROGRESS)}", results)
    server.command(f'data merge block {ENGINE} {{RedstoneControl:"always_active"}}')
    server.command(f"execute positioned {ENGINE} run setblock ~ ~1 ~ minecraft:air")
    server.assert_state("saved_lava_burn_resumes_and_three_mb_generate_3600_fe", charge_state(AFTER_LAVA), results, timeout=18)
    reload_fuel(server, results)
    export_energy(server, results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--legacy-jar", type=Path)
    source.add_argument("--legacy-world", type=Path, help="Reuse a disposable world saved by the official 6.2 fixture")
    parser.add_argument("--launch", type=Path, help="Place the migrated engine and exercise its menu with a guarded client")
    parser.add_argument("--port", type=int, default=25748)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")

    directory = ROOT / "build/port-runtime" / args.name
    directory.mkdir()
    port = directory / "port"
    prepare_profile(args, port, args.jar)
    report = {"passed": False, "jarSha256": digest(port / "mods/enderio.jar"), "assertions": [], "servers": []}
    try:
        if args.legacy_jar:
            legacy = directory / "legacy"
            prepare_profile(args, legacy, args.legacy_jar)
            report["legacySha256"] = digest(legacy / "mods/enderio.jar")
            run_stage(args, legacy, report, "legacy", legacy_fixture, startup_timeout=240)
            legacy_world = legacy / WORLD
        else:
            legacy_world = args.legacy_world.resolve()
            report["legacyWorld"] = str(legacy_world)
        shutil.copytree(legacy_world, port / WORLD)
        config = port / "config/enderio/machines-common.toml"
        config.parent.mkdir(parents=True, exist_ok=True)
        config.write_text('[energy.soulEngine]\ngeneration = 1.5\n')
        rules = port / WORLD / "datapacks/soul-engine-rules"
        rules.mkdir(parents=True)
        (rules / "pack.mcmeta").write_text('{"pack":{"pack_format":15,"description":"Ender IO live Soul Engine rule"}}\n')
        run_stage(args, port, report, "converted", verify_legacy_conversion, startup_timeout=240)
        if args.launch:
            run_stage(args, port, report, "restart", lambda server, _: run_client(args, server, report), startup_timeout=240)
        else:
            run_stage(args, port, report, "restart", run_headless, startup_timeout=240)

        def reopened(server, results):
            server.assert_state("all_transferred_fe_survives_reopen",
                                f"{charge_state(0)} if data block {CHARGER} {{EnergyStored:{AFTER_WATER}}}", results)
            if args.launch:
                server.assert_state("replanted_legacy_progress_and_tagged_fluid_survive_reopen",
                                    f"if data block {CARRIED} {migrated_engine(ENERGY + 1, PROGRESS + 1)}", results)
            bind_a_fresh_engine(server, results)

        run_stage(args, port, report, "reopened", reopened, startup_timeout=240)
        report["passed"] = True
    finally:
        output = directory / "soul-engine-results.json"
        output.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Soul Engine passed={report['passed']}, {len(report['assertions'])} assertions, {output}", flush=True)


if __name__ == "__main__":
    main()
