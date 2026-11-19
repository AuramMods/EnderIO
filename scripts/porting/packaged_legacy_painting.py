#!/usr/bin/env python3
"""Resume a real Forge 6 Painting Machine slab job in a copied world."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage


PLACED = "2600 80 10"
CARRIED = "2607 80 10"
CARRIED_POWER = "2607 80 9"
REPLANTED = "2610 80 13"
POWER = "2600 80 9"
BARREL = "2606 80 7"
HOPPER = "2600 79 10"
MACHINE = "enderio:painting_machine"
SLAB = "enderio:painted_slab"
RECIPE = "enderio:painting/painted_slab"
DIAMOND = "minecraft:diamond_block"
PLAYER = "EnderIOQA"
CAPACITOR = '{Slot:0,id:"enderio:basic_capacitor",Count:1}'
TEMPLATE = '{Slot:1,id:"minecraft:oak_slab",Count:1}'
PALETTE = '{Slot:2,id:"minecraft:diamond_block",Count:2}'
OLD_PAINT = f'{{BlockEntityTag:{{Paint:"{DIAMOND}"}}}}'
NATIVE_PAINT = f'{{"enderio:data":{{"enderio:block_paint":{{paint:"{DIAMOND}"}}}}}}'
LEGACY_SETTINGS = 'RedstoneControl:1,IoConfig:{"5":2b}'
NATIVE_SETTINGS = 'RedstoneControl:"active_with_signal",IOConfig:{Modes:{east:"pull"}}'


def progress(value):
    return f'Task:{{RecipeId:"{RECIPE}",ProgressMade:{value},ProgressRequired:2400}}'


def stack(slot, count, item, *, metadata=None, slot_suffix="", count_suffix=""):
    tag = f",tag:{metadata}" if metadata is not None else ""
    return f'{{Slot:{slot}{slot_suffix},Count:{count}{count_suffix},id:"{item}"{tag}}}'


def state(pos, details):
    return f"if block {pos} {MACHINE} if data block {pos} {{{details}}}"


def stock(server, pos):
    server.command(f"setblock {pos} {MACHINE}[facing=south]")
    server.command(f'data merge block {pos} {{RedstoneControl:0,Energy:{{EnergyStored:3000}},'
                   f'Items:{{Size:4,Items:[{CAPACITOR},{{Slot:1,id:"minecraft:oak_slab",Count:2}},{PALETTE}]}}}}')


def legacy_fixture(server, results):
    server.command("forceload add 2592 0 2623 15")
    server.assert_state("forge6_fixture_chunk_is_loaded", f"if loaded {PLACED} if loaded {CARRIED} if loaded {BARREL}", results)
    server.command("gamerule doMobSpawning false")
    server.command("gamerule doDaylightCycle false")
    server.command("gamerule spawnRadius 0")
    server.command("time set noon")
    server.command("fill 2596 79 6 2611 79 15 minecraft:stone")
    server.command("setworldspawn 2600 80 13")
    legacy_stacks = [
        stack(0, 1, "enderio:basic_capacitor", count_suffix="b"),
        stack(1, 1, "minecraft:oak_slab", count_suffix="b"),
        stack(2, 2, DIAMOND, count_suffix="b"),
        stack(3, 1, SLAB, metadata=OLD_PAINT, count_suffix="b"),
    ]
    inventory = f'Items:{{Items:[{",".join(legacy_stacks)}]}}'
    for label, pos, neighbor in (("placed", PLACED, POWER), ("carried", CARRIED, CARRIED_POWER)):
        stock(server, pos)
        server.command(f"data get block {pos}")
        server.assert_state(f"forge6_{label}_paints_a_slab_and_pays_600_into_the_next",
                            state(pos, f"Energy:{{EnergyStored:0}},{progress(600)},{inventory}"), results, timeout=20)
        server.command(f"data merge block {pos} {{{LEGACY_SETTINGS}}}")
        # The Forge 6 machine refreshes its cached redstone state on a neighbor update.
        server.command(f"setblock {neighbor} minecraft:stone")
        charge = 0 if pos == PLACED else 1800
        if charge:
            server.command(f"data merge block {pos} {{Energy:{{EnergyStored:{charge}}}}}")
        server.command(f"data get block {pos}")
        server.assert_state(f"forge6_{label}_saves_remaining_charge_and_configuration",
                            state(pos, f"Energy:{{EnergyStored:{charge}}},{LEGACY_SETTINGS},{progress(600)},{inventory}"),
                            results)
    server.command(f"setblock {BARREL} minecraft:barrel")
    server.command(f"loot replace block {BARREL} container.0 mine {CARRIED} minecraft:diamond_pickaxe")
    server.assert_state("forge6_native_drop_carries_job_progress_charge_and_both_items",
                        f'if data block {BARREL} Items[{{Slot:0b,id:"{MACHINE}",Count:1b,tag:{{BlockEntityTag:{{'
                        f'Energy:{{EnergyStored:1800}},{LEGACY_SETTINGS},{progress(600)},{inventory}}}}}}}]',
                        results)
    server.command(f"setblock {CARRIED} minecraft:air")


def converted(server, results):
    server.command(f"data get block {PLACED}")
    server.command(f"data get block {BARREL} Items")
    server.assert_state("copied_world_keeps_the_exact_job_and_identities",
                        f"{state(PLACED, f'EnergyStored:0,{NATIVE_SETTINGS},{progress(600)},Items:{{Items:[{CAPACITOR},{TEMPLATE},{PALETTE}]}}')} "
                        f'if data block {BARREL} Items[{{Slot:0b,id:"{MACHINE}",Count:1b}}]', results)


def resume_placed(server, results):
    server.assert_state("placed_job_survives_a_port_restart", state(PLACED, f"EnergyStored:0,{NATIVE_SETTINGS},{progress(600)}"), results)
    server.command(f"data merge block {PLACED} {{EnergyStored:1800}}")
    server.command(f"setblock {POWER} minecraft:redstone_block")
    try:
        server.assert_state("exact_remaining_payment_stacks_the_new_slab_with_the_old_output",
                            f"{state(PLACED, f'EnergyStored:0,{NATIVE_SETTINGS},Items:{{Items:[{CAPACITOR},{PALETTE},{stack(3, 2, SLAB, metadata=NATIVE_PAINT)}]}}')} "
                            f"unless data block {PLACED} Items.Items[{{Slot:1}}] unless data block {PLACED} Task", results, timeout=15)
    finally:
        server.command(f"data get block {PLACED}")
    server.command(f"setblock {POWER} minecraft:air")
    server.command(f"setblock {HOPPER} minecraft:hopper[facing=down]")
    server.assert_state("hopper_extracts_both_migrated_slabs_without_the_reusable_paint",
                        f"{hopper_loot()} "
                        f"unless data block {PLACED} Items.Items[{{Slot:3}}] if data block {PLACED} Items.Items[{PALETTE}]",
                        results)


def hopper_loot():
    output = stack(0, 2, SLAB, metadata=NATIVE_PAINT, slot_suffix="b", count_suffix="b")
    return f"if data block {HOPPER} Items[{output}]"


def placed_slab():
    return f'if block {REPLANTED} {SLAB}[type=bottom] if data block {REPLANTED} {{Paint:"{DIAMOND}"}}'


def exercise_client(args, server, report):
    results = report["assertions"]
    output = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "legacy-painting", "--output", str(output), "--server", f"127.0.0.1:{args.port}", "--timeout", "170"]
    with (server.directory / "legacy-painting-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            server.command(f"tp {PLAYER} 2607.5 80 13.5 180 17")
            server.command(f"item replace entity {PLAYER} hotbar.0 from block {BARREL} container.0")
            server.command(f"item replace block {BARREL} container.0 with minecraft:air")
            (output / "server-replant").touch()
            wait_signal(client, output, "replanted")
            server.command(f"data get block {CARRIED}")
            single = stack(3, 1, SLAB, metadata=NATIVE_PAINT)
            pending = f"EnergyStored:1800,{NATIVE_SETTINGS},{progress(600)},Items:{{Items:[{CAPACITOR},{TEMPLATE},{PALETTE},{single}]}}"
            server.assert_state("survival_replant_preserves_charge_progress_settings_and_items",
                                f'{state(CARRIED, pending)} unless data entity {PLAYER} Inventory[{{id:"{MACHINE}"}}]', results)
            (output / "server-menu").touch()
            wait_signal(client, output, "menu")
            server.command(f"setblock {CARRIED_POWER} minecraft:redstone_block")
            double = stack(3, 2, SLAB, metadata=NATIVE_PAINT)
            finished = f"EnergyStored:0,{NATIVE_SETTINGS},Items:{{Items:[{CAPACITOR},{PALETTE},{double}]}}"
            server.assert_state("replanted_job_completes_from_its_carried_charge",
                                f"{state(CARRIED, finished)} unless data block {CARRIED} Task", results)
            (output / "server-take").touch()
            wait_signal(client, output, "taken")
            server.assert_state("menu_transfers_both_matching_slabs_once",
                                f'if data entity {PLAYER} Inventory[{{Count:2b,id:"{SLAB}",tag:{NATIVE_PAINT}}}] '
                                f"unless data block {CARRIED} Items.Items[{{Slot:3}}]", results)
            server.command(f"tp {PLAYER} 2610.5 80 15.5 180 20")
            (output / "server-slab").touch()
            wait_signal(client, output, "slab")
            server.assert_state("survival_placement_consumes_one_slab_and_keeps_its_paint",
                                f'{placed_slab()} if data entity {PLAYER} Inventory[{{Count:1b,id:"{SLAB}",tag:{NATIVE_PAINT}}}]',
                                results)
            (output / "server-verified").touch()
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"Legacy Painting Machine client failed; see {output}")
            summary = json.loads((output / "summary.json").read_text())
            frame = summary["client"]["captures"]["legacy-painting-menu"]
            if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Client and server Ender IO jars differ")
        finally:
            stop_client(client)
            summary = output / "summary.json"
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def reopened(server, results):
    server.assert_state("both_machines_hopper_and_replanted_paint_survive_restart",
                        f"{hopper_loot()} {placed_slab()} {state(PLACED, NATIVE_SETTINGS)} "
                        f"{state(CARRIED, f'EnergyStored:0,{NATIVE_SETTINGS},Items:{{Items:[{CAPACITOR},{PALETTE}]}}')} "
                        f"unless data block {CARRIED} Task unless data block {CARRIED} Items.Items[{{Slot:1}}] "
                        f"unless data block {CARRIED} Items.Items[{{Slot:3}}] unless data block {BARREL} Items[0]",
                        results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--legacy-jar", required=True, type=Path)
    parser.add_argument("--launch", type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")

    directory = ROOT / "build/port-runtime" / args.name
    directory.mkdir()
    legacy = directory / "legacy"
    port = directory / "port"
    prepare_profile(args, legacy, args.legacy_jar)
    prepare_profile(args, port, args.jar)
    report = {"passed": False, "forge": FORGE, "jarSha256": digest(port / "mods/enderio.jar"),
              "legacySha256": digest(legacy / "mods/enderio.jar"), "assertions": [], "servers": []}
    try:
        run_stage(args, legacy, report, "legacy", legacy_fixture)
        shutil.copytree(legacy / WORLD, port / WORLD)
        run_stage(args, port, report, "converted", converted)

        def resumed(server, results):
            resume_placed(server, results)
            if args.launch:
                exercise_client(args, server, report)

        run_stage(args, port, report, "resumed", resumed)
        if args.launch:
            run_stage(args, port, report, "reopened", reopened)
        report["passed"] = True
    finally:
        path = directory / "legacy-painting-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Legacy painting passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
