#!/usr/bin/env python3
"""Exercise Inventory Sorter gestures and protected Ender IO slots over packaged TCP."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_crafter import CAPACITOR, seed, stack
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_spawners import prepare_profile, run_stage


PLAYER = "EnderIOQA"
BARREL = "3400 80 4"
CRAFTER = "3400 80 7"
ENCHANTER = "3400 80 10"
SPAWNER = "3400 80 13"
FIXTURES = [("barrel", 4), ("crafter", 7), ("enchanter", 10), ("powered_spawner", 13),
            ("basic_item_filter", 16), ("limited_item_filter", 16), ("basic_fluid_filter", 16)]
ITEM_MATCH = '{items:[{index:0,value:{id:"minecraft:paper",Count:1,tag:{PortSorter:7}}}],isNbt:1b}'
LIMITED_MATCH = '{items:[{index:0,value:{id:"minecraft:paper",Count:7,tag:{PortSorter:7}}}],isNbt:1b}'
FLUID_MATCH = ('{fluids:[{index:0,value:{FluidName:"minecraft:water",Amount:1000,Tag:{PortSorter:7}}}],'
               'isNbt:1b,isInvert:0b}')
FILTERS = {"basic_item_filter": ("item_filter", ITEM_MATCH), "limited_item_filter": ("limited_item_filter", LIMITED_MATCH),
           "basic_fluid_filter": ("fluid_filter", FLUID_MATCH)}
CRAFTER_ITEMS = (CAPACITOR, stack(1, "oak_planks"), stack(10, "stick", 4), stack(11, "oak_planks"),
                 stack(14, "oak_planks"), stack(20, "stick", 4))
SPAWNER_VIALS = ('{Slot:1,id:"enderio:soul_vial",Count:1},'
                 '{Slot:2,id:"enderio:soul_vial",Count:1,tag:{"enderio:data":{"enderio:soul":'
                 '{entity_type:"minecraft:cow",entity_tag:{PortSorter:7}}}}}')
ENCHANTER_INPUT = ','.join((stack(0, "writable_book"), stack(1, "quartz", 12), stack(2, "lapis_lazuli", 16)))
ENCHANTER_OUTPUT = ('{Slot:3,id:"minecraft:enchanted_book",Count:1,'
                    'tag:{StoredEnchantments:[{id:"minecraft:sharpness",lvl:1s}]}}')


def machine_state(name):
    if name == "crafter":
        return f'if data block {CRAFTER} {{EnergyStored:3200,RedstoneControl:"never_active",Items:{{Items:[{",".join(CRAFTER_ITEMS)}]}}}}'
    if name == "enchanter":
        return f'if data block {ENCHANTER} {{Items:{{Items:[{ENCHANTER_INPUT},{ENCHANTER_OUTPUT}]}}}}'
    return f'if data block {SPAWNER} {{Mode:"capture",RedstoneControl:"never_active",Items:{{Items:[{SPAWNER_VIALS}]}}}}'


def barrel_state(sorted_items):
    stacks = ('{Slot:0b,id:"minecraft:cobblestone",Count:14b}' if sorted_items else
              '{Slot:5b,id:"minecraft:cobblestone",Count:3b},{Slot:8b,id:"minecraft:cobblestone",Count:11b}')
    extra = f"unless data block {BARREL} Items[{1 if sorted_items else 2}]"
    return f"if data block {BARREL} {{Items:[{stacks}]}} {extra}"


def prepare(server, results):
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    server.command("forceload add 3392 0 3407 31")
    server.assert_state("fixture_chunks_are_loaded", f"if loaded {BARREL} if loaded 3400 80 18", results)
    server.command("fill 3398 79 2 3402 79 20 minecraft:stone")
    server.command("setworldspawn 3400 80 6")
    server.command(f"setblock {BARREL} minecraft:barrel")
    server.command(f"item replace block {BARREL} container.5 with minecraft:cobblestone 3")
    server.command(f"item replace block {BARREL} container.8 with minecraft:cobblestone 11")
    server.assert_state("barrel_contains_two_separate_stacks_before_input", barrel_state(False), results)
    seed(server, CRAFTER, 3200, CRAFTER_ITEMS[1:], "never_active")
    server.command(f"setblock {ENCHANTER} enderio:enchanter")
    server.command(f"data merge block {ENCHANTER} {{Items:{{Items:[{ENCHANTER_INPUT}]}}}}")
    server.command(f"setblock {SPAWNER} enderio:powered_spawner")
    server.command(f'data merge block {SPAWNER} {{Mode:"capture",RedstoneControl:"never_active",'
                   f'Items:{{Items:[{CAPACITOR},{SPAWNER_VIALS}]}}}}')
    for name, _ in FIXTURES[1:4]:
        server.assert_state(f"{name}_fixture_ready", machine_state(name), results)


def configure_filter(server, name):
    key, value = FILTERS[name]
    tag = f'{{"enderio:data":{{"enderio:{key}":{value}}}}}'
    server.command(f"item replace entity {PLAYER} hotbar.0 with enderio:{name}{tag}")
    return f'if data entity {PLAYER} Inventory[{{Slot:0b,id:"enderio:{name}",Count:1b,tag:{tag}}}]'


def exercise(args, server, results, report):
    output = ROOT / "build/port-evidence" / f"{args.name}-client"
    scenario = "inventory-sorter" if args.sorter else "inventory-sorter-absent"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", scenario, "--output", str(output), "--server", f"127.0.0.1:{args.port}", "--timeout", "195"]
    summary = output / "summary.json"
    problems = []
    with (server.directory / "inventory-sorter-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            for name, z in FIXTURES:
                if name in FILTERS:
                    expected = configure_filter(server, name)
                elif name == "barrel":
                    expected = barrel_state(bool(args.sorter))
                else:
                    expected = machine_state(name)
                server.command(f"tp {PLAYER} 3400.5 80 {z + 2.5} 180 22")
                (output / f"server-open-{name}").touch()
                wait_signal(client, output, f"probed-{name}")
                try:
                    server.assert_state(f"expected_native_state_after_{name}_gestures", expected, results, timeout=4)
                except AssertionError as error:
                    problems.append(str(error))
                (output / f"server-verified-{name}").touch()
            if client.wait(timeout=35) != 0:
                problems.append(f"Client gesture checks failed, see {summary}")
            if problems:
                raise AssertionError("\n".join(problems))
            for cap in json.loads(summary.read_text())["client"]["captures"].values():
                for mod, expected in report["jars"].items():
                    if digest(Path(cap["loadedMods"][mod]["file"])) != expected:
                        raise AssertionError(f"Client and server {mod} jars differ")
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
    parser.add_argument("--sorter", type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")

    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    jars = {"enderio": digest(directory / "mods/enderio.jar")}
    if args.sorter:
        sorter = directory / "mods/inventorysorter.jar"
        shutil.copyfile(args.sorter, sorter)
        jars["inventorysorter"] = digest(sorter)
    report = {"passed": False, "forge": FORGE, "jars": jars, "assertions": [], "servers": []}
    try:
        def fresh(server, results):
            prepare(server, results)
            exercise(args, server, results, report)

        def reopened(server, results):
            server.assert_state("barrel_state_survives_restart", barrel_state(bool(args.sorter)), results)
            for name, _ in FIXTURES[1:4]:
                server.assert_state(f"{name}_state_survives_restart", machine_state(name), results)

        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", reopened)
        report["passed"] = True
    finally:
        path = directory / "inventory-sorter-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Inventory Sorter passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
