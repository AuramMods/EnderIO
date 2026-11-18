#!/usr/bin/env python3
"""Convert a disposable Forge 6 Aversion world and replant its original block drop."""

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


PLACED = "1764 80 8"
REPLANTED = "1768 80 8"
BARREL = "1766 80 5"
PLAYER = "EnderIOQA"
BLOCK = "enderio:aversion_obelisk"
CAPACITOR = 'Slot:1,id:"enderio:basic_capacitor"'
OLD_ID = "enderio:entity_filter"
NEW_ID = "enderio:basic_soul_filter"
PIG_TAG = "{CustomName:'{\"text\":\"Legacy Marker\"}'}"
FILTER_PATH = 'tag."enderio:data"."enderio:soul_filter".entities'
OLD_FILTER = ('{PortFixture:74,IsInverted:1b,IsNbt:1b,EntityEntries:[{Entity:{id:"minecraft:pig",'
              + PIG_TAG[1:-1] + '},MaxHealth:10.0f},{Entity:{}},{Entity:{}},{Entity:{}},'
              '{Entity:{id:"minecraft:cow"}}]}')


def filter_tag(compare=True, cow=True):
    last = '{entity_type:"minecraft:cow",entity_tag:{}}' if cow else '{}'
    return ('{PortFixture:74,"enderio:data":{"enderio:soul_filter":{isInvert:1b,'
            f'nbt:{int(compare)}b,entities:[{{index:0,value:{{entity_type:"minecraft:pig",entity_tag:{PIG_TAG}}}}},'
            f'{{index:1,value:{{}}}},{{index:4,value:{last}}}]}}}}}}')


def old_machine(radius, energy):
    return (f'{{Range:{radius},RangeVisible:1b,Energy:{{EnergyStored:{energy}}},RedstoneControl:3,'
            f'Items:{{Size:2,Items:[{{Slot:0,id:"{OLD_ID}",Count:1b,tag:{OLD_FILTER}}},{{{CAPACITOR},Count:1b}}]}}}}')


def new_machine(pos, radius, energy):
    return (f'if block {pos} {BLOCK} if data block {pos} {{EnergyStored:{energy},RedstoneControl:"never_active",'
            f'ActionRange:{{range:{radius},isVisible:1b}},Items:{{Items:[{{{CAPACITOR},Count:1}},'
            f'{{Slot:0,id:"{NEW_ID}",Count:1,tag:{filter_tag()}}}]}}}} '
            f'unless data block {pos} Items.Items[{{Slot:0}}].tag.EntityEntries')


def legacy_fixture(server, results):
    server.command("forceload add 1760 0 1775 15")
    server.assert_state("official_forge6_aversion_chunk_loaded", f"if loaded {PLACED} if loaded {REPLANTED}", results)
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    server.command("fill 1761 79 5 1773 79 14 minecraft:stone")
    server.command("setworldspawn 1764 80 11")
    for pos, radius, energy in ((PLACED, 4, 5842), (REPLANTED, 0, 7123)):
        server.command(f"setblock {pos} {BLOCK}[facing=south]")
        server.command(f"data merge block {pos} {{RedstoneControl:3}}")
        server.command(f"setblock {pos.split()[0]} 81 8 minecraft:stone")
        server.command(f"data merge block {pos} {old_machine(radius, energy)}")
        server.assert_state(f"official_forge6_saves_range_{radius}_and_exact_charge",
                            f"if data block {pos} {old_machine(radius, energy)}", results)
    server.command(f"setblock {BARREL} minecraft:barrel")
    server.command(f"loot replace block {BARREL} container.0 mine {REPLANTED} minecraft:diamond_pickaxe")
    server.command(f"item replace block {BARREL} container.1 with {OLD_ID}{OLD_FILTER}")
    server.assert_state("official_forge6_drop_carries_zero_range_energy_and_filter",
                        f'if data block {BARREL} Items[{{Slot:0b,id:"{BLOCK}",tag:{{BlockEntityTag:{old_machine(0, 7123)}}}}}] '
                        f'if data block {BARREL} Items[{{Slot:1b,id:"{OLD_ID}",tag:{OLD_FILTER}}}]', results)
    server.command(f"setblock {REPLANTED} minecraft:air")
    server.command("setblock 1768 81 8 minecraft:air")


def exercise_client(args, server, report):
    results = report["assertions"]
    output = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "legacy-aversion", "--output", str(output), "--server", f"127.0.0.1:{args.port}", "--timeout", "225"]
    with (server.directory / "aversion-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            server.command(f"tp {PLAYER} 1768.5 80 11.5 180 10")
            server.command(f"item replace entity {PLAYER} hotbar.0 from block {BARREL} container.0")
            server.command(f"item replace block {BARREL} container.0 with minecraft:air")
            (output / "server-replant").touch()
            wait_signal(client, output, "replanted")
            server.command(f"data get block {REPLANTED}")
            server.assert_state("native_replant_consumes_item_and_restores_zero_range_and_filter",
                                new_machine(REPLANTED, 0, 7123) +
                                f' unless data entity {PLAYER} Inventory[{{id:"{BLOCK}"}}]', results)
            server.command(f"item replace entity {PLAYER} hotbar.0 from block {BARREL} container.1")
            server.command(f"item replace block {BARREL} container.1 with minecraft:air")
            (output / "server-edit-filter").touch()
            wait_signal(client, output, "filter-edited")
            server.assert_state("native_filter_screen_updates_fifth_slot_and_comparison_mode",
                                f'if data entity {PLAYER} Inventory[{{Slot:0b,id:"{NEW_ID}",tag:{filter_tag(False, False)}}}] '
                                f'unless data entity {PLAYER} Inventory[{{Slot:0b}}].{FILTER_PATH}[{{index:4}}].value.entity_type '
                                f'unless data entity {PLAYER} Inventory[{{Slot:0b}}].tag.EntityEntries', results)
            (output / "server-verified").touch()
            if client.wait(timeout=25) != 0:
                raise AssertionError(f"Aversion client failed; see {output}")
            summary = json.loads((output / "summary.json").read_text())
            frame = summary["client"]["captures"]["aversion-filter"]
            if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Client and server Ender IO jars differ")
        finally:
            stop_client(client)
            summary = output / "summary.json"
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--legacy-jar", required=True, type=Path)
    parser.add_argument("--launch", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")

    directory = ROOT / "build/port-runtime" / args.name
    directory.mkdir()
    legacy = directory / "legacy"
    port = directory / "port"
    prepare_profile(args, legacy, args.legacy_jar)
    prepare_profile(args, port, args.jar)
    report = {"passed": False, "jarSha256": digest(port / "mods/enderio.jar"),
              "legacySha256": digest(legacy / "mods/enderio.jar"), "assertions": [], "servers": []}
    try:
        run_stage(args, legacy, report, "legacy", legacy_fixture, startup_timeout=240)
        shutil.copytree(legacy / WORLD, port / WORLD)

        def converted(server, results):
            server.command(f"data get block {PLACED}")
            server.command(f"data get block {BARREL} Items")
            server.assert_state("copied_world_restores_range_settings_and_ordered_filter",
                                new_machine(PLACED, 4, 5842), results)
            server.assert_state("barrel_restores_carried_range_and_standalone_filter",
                                f'if data block {BARREL} Items[{{Slot:0b,id:"{BLOCK}",tag:{{"enderio:data":{{'
                                '"enderio:action_range":{range:0,isVisible:1b},"enderio:energy":7123,'
                                '"enderio:redstone_control":"never_active"}}}] '
                                f'if data block {BARREL} Items[{{Slot:1b,id:"{NEW_ID}",tag:{filter_tag()}}}]', results)
            exercise_client(args, server, report)

        def reopened(server, results):
            server.assert_state("both_restored_obelisks_survive_restart_exactly",
                                f"{new_machine(PLACED, 4, 5842)} {new_machine(REPLANTED, 0, 7123)}", results)
            server.assert_state("original_barrel_stays_empty", f"unless data block {BARREL} Items[0]", results)

        run_stage(args, port, report, "converted", converted, startup_timeout=240)
        run_stage(args, port, report, "reopened", reopened, startup_timeout=240)
        report["passed"] = True
    finally:
        path = directory / "aversion-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Legacy Aversion passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
