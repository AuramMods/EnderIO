#!/usr/bin/env python3
"""Exercise packaged Soul Engine fuels, native crafting and the optional JEI viewer."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage


ENGINE = "enderio:soul_engine"
PLAYER = "EnderIOQA"
FUELS = (
    ("blaze", "minecraft:lava", 800),
    ("creeper", "enderio:fluid_rocket_fuel_still", 800),
    ("enderman", "enderio:fluid_dew_of_the_void_still", 1200),
    ("husk", "enderio:fluid_nutrient_distillation_still", 1000),
    ("zombie", "enderio:fluid_nutrient_distillation_still", 1000),
    ("zombie_villager", "enderio:fluid_nutrient_distillation_still", 1000),
)
CAPACITOR_STACK = '{Slot:0,id:"enderio:basic_capacitor",Count:1}'
CAPACITOR = f'Items:{{Items:[{CAPACITOR_STACK}]}}'
ERASE_SOURCE = "1507 80 7"
ERASED = "1508 80 11"
BARREL = "1506 80 8"
TABLE = "1507 80 11"
RELOADED_ENERGY_PER_MB = 900


def engine_row():
    for index, (mob, fluid, energy) in enumerate(FUELS):
        x = 1474 + index * 4
        yield f"{x} 80 8", f"{x + 1} 80 8", mob, fluid, energy


def bound(mob):
    return f'EntityStorage:{{entity_type:"minecraft:{mob}"}}'


def tank_contents(fluid, amount):
    return f'Fluids:{{Tanks:[{{Index:0,Contents:{{FluidName:"{fluid}",Amount:{amount}}}}}]}}'


def seed_engine(server, pos, mob, state=""):
    server.command(f"setblock {pos} {ENGINE}[facing=south]")
    server.command(f'data merge block {pos} {{{CAPACITOR},EntityStorage:{{entity_type:"minecraft:{mob}",entity_tag:{{}}}}{state}}}')


def supply_fuel(server, pos, fluid):
    # The tank faces south, so local east is the output toward world west.
    server.command(f'data merge block {pos} {{{tank_contents(fluid, 2)},IOConfig:{{Modes:{{east:"push"}}}}}}')


def exhausted_engine(engine, tank, mob, stored_energy):
    return (f'if block {engine} {ENGINE}[powered=false] '
            f'if data block {engine} {{{bound(mob)},EnergyStored:{stored_energy},BurnedTicks:0,{CAPACITOR}}} '
            f'unless data block {engine} Fluids.Tanks[0].Contents.FluidName '
            f'unless data block {tank} Fluids.Tanks[0].Contents.FluidName')


def verify_fuels(server, results):
    server.command("forceload add 1472 0 1519 15")
    server.assert_state("all_engine_chunks_loaded", f"if loaded 1474 80 8 if loaded {ERASED}", results)
    for engine, tank, mob, fluid, _ in engine_row():
        seed_engine(server, engine, mob)
        server.command(f"setblock {tank} enderio:fluid_tank[facing=south]")
        supply_fuel(server, tank, "enderio:fluid_rocket_fuel_still" if mob == "enderman" else fluid)

    for engine, tank, mob, _, energy in engine_row():
        if mob != "enderman":
            server.assert_state(f"{mob}_converts_two_mb_through_forge_fluid_input",
                                exhausted_engine(engine, tank, mob, 2 * energy), results, timeout=20)

    for engine, tank, mob, fluid, energy in engine_row():
        if mob != "enderman":
            continue
        server.assert_state("enderman_rejects_rocket_fuel",
                            f'if data block {engine} {{{bound(mob)},EnergyStored:0,BurnedTicks:0}} '
                            f'unless data block {engine} Fluids.Tanks[0].Contents.FluidName '
                            f'if data block {tank} {{{tank_contents("enderio:fluid_rocket_fuel_still", 2)}}}', results)
        supply_fuel(server, tank, fluid)
        server.assert_state("enderman_converts_two_mb_through_forge_fluid_input",
                            exhausted_engine(engine, tank, mob, 2 * energy), results, timeout=20)


def erased_state():
    return (f'if block {ERASED} {ENGINE}[powered=false] '
            f'if data block {ERASED} {{EnergyStored:0,BurnedTicks:0,RedstoneControl:"always_active"}} '
            f'unless data block {ERASED} EntityStorage.entity_type '
            f'unless data block {ERASED} Fluids.Tanks[0].Contents.FluidName '
            f'unless data block {ERASED} Items.Items[{{Slot:0}}] '
            f'unless data block {ERASED} IOConfig.Modes.north')


def verify_client(args, server, report):
    results = report["assertions"]
    server.command("fill 1503 79 6 1512 79 14 minecraft:stone")
    server.command("gamerule spawnRadius 0")
    server.command("setworldspawn 1507 80 13")
    server.command(f"setblock {TABLE} minecraft:crafting_table")
    server.command(f"setblock {BARREL} minecraft:barrel")
    state = (f',EnergyStored:1543,BurnedTicks:7,RedstoneControl:"never_active",'
             f'IOConfig:{{Modes:{{north:"disable"}}}},{tank_contents("minecraft:lava", 5)}')
    seed_engine(server, ERASE_SOURCE, "blaze", state)
    server.command(f"loot replace block {BARREL} container.0 mine {ERASE_SOURCE} minecraft:diamond_pickaxe")
    server.command(f"data get block {BARREL} Items")
    carried = ('"enderio:soul":{entity_type:"minecraft:blaze"},"enderio:energy":1543,'
               '"enderio:redstone_control":"never_active","enderio:io_config":{Modes:{north:"disable"}},'
               f'"enderio:container":{{Items:[{CAPACITOR_STACK}]}},'
               '"enderio:item_fluid_content":{FluidName:"minecraft:lava",Amount:5}')
    server.assert_state("native_drop_contains_all_tested_carried_state",
                        f'if data block {BARREL} Items[{{Slot:0b,id:"{ENGINE}",Count:1b,'
                        f'tag:{{"enderio:data":{{{carried}}}}}}}]', results)

    output = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
               "--launch", str(args.launch.resolve()), "--scenario", "soul-engine-fuels", "--output", str(output),
               "--server", f"127.0.0.1:{args.port}", "--timeout", "225"]
    with (server.directory / "soul-engine-fuels-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            server.command(f"item replace entity {PLAYER} hotbar.0 from block {BARREL} container.0")
            server.command(f"item replace block {BARREL} container.0 with minecraft:air")
            wait_signal(client, output, "placed")
            server.command(f"data get block {ERASED}")
            server.assert_state("survival_craft_and_replant_restore_factory_state",
                                f'{erased_state()} unless data entity {PLAYER} Inventory[{{id:"{ENGINE}"}}]', results)
            (output / "server-placed").touch()
            wait_signal(client, output, "jei-default")
            pack_data = server.directory / WORLD / "datapacks/soul-engine-rules/data/enderio"
            override = pack_data / "eio_soul/engine/minecraft_blaze.json"
            override.parent.mkdir(parents=True, exist_ok=True)
            override.write_text(json.dumps({"entity": "minecraft:blaze", "fluid": "#enderio:port_engine_qa_fuel",
                                            "power/mb": RELOADED_ENERGY_PER_MB, "tick/mb": 4}) + "\n")
            fuel_tag = pack_data / "tags/fluids/port_engine_qa_fuel.json"
            fuel_tag.parent.mkdir(parents=True, exist_ok=True)
            fuel_tag.write_text(json.dumps({"replace": True, "values": ["minecraft:water"]}) + "\n")
            server.command("reload")
            if client.wait(timeout=70) != 0:
                raise AssertionError(f"Soul Engine fuels client failed; see {output}")
            summary = json.loads((output / "summary.json").read_text())
            frame = summary["client"]["captures"]["jei-reloaded"]
            if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Client and server Ender IO jars differ")
            report["jeiSha256"] = digest(Path(frame["loadedMods"]["jei"]["file"]))
        finally:
            stop_client(client)
            summary_file = output / "summary.json"
            report["client"] = json.loads(summary_file.read_text()) if summary_file.exists() else None
    engine, tank, mob, _, energy = next(engine_row())
    supply_fuel(server, tank, "minecraft:water")
    server.assert_state("blaze_generates_1800_more_fe_from_reloaded_fuel_tag",
                        exhausted_engine(engine, tank, mob, 2 * (energy + RELOADED_ENERGY_PER_MB)), results, timeout=20)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--launch", type=Path, help="Exercise the erase recipe and JEI with a guarded client")
    parser.add_argument("--port", type=int, default=25756)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")

    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "jarSha256": digest(directory / "mods/enderio.jar"), "assertions": [], "servers": []}
    try:
        if args.launch:
            rules = directory / WORLD / "datapacks/soul-engine-rules"
            rules.mkdir(parents=True)
            (rules / "pack.mcmeta").write_text('{"pack":{"pack_format":15,"description":"Ender IO JEI fuel rule"}}\n')

        def fresh(server, results):
            verify_fuels(server, results)
            if args.launch:
                verify_client(args, server, report)

        def reopened(server, results):
            for engine, tank, mob, _, energy in engine_row():
                if args.launch and mob == "blaze":
                    energy += RELOADED_ENERGY_PER_MB
                server.assert_state(f"{mob}_charge_survives_restart",
                                    exhausted_engine(engine, tank, mob, 2 * energy), results)
            if args.launch:
                server.assert_state("factory_state_survives_restart", erased_state(), results)

        run_stage(args, directory, report, "fresh", fresh, startup_timeout=240)
        run_stage(args, directory, report, "reopened", reopened, startup_timeout=240)
        report["passed"] = True
    finally:
        output = directory / "soul-engine-fuels-results.json"
        output.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Soul Engine fuels passed={report['passed']}, {len(report['assertions'])} assertions, {output}", flush=True)


if __name__ == "__main__":
    main()
