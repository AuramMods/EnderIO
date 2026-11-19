#!/usr/bin/env python3
"""Convert authentic Forge 6.2 Slice'N'Splice jobs and replant native loot over TCP."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_legacy_painting import stack
from packaged_sag_progression import charge, mill, output, ready_at
from packaged_server_smoke import ROOT, digest
from packaged_slicer import AXE, CAP, CASES, COST, PAID, SHEARS
from packaged_spawners import WORLD, prepare_profile, run_stage
from packaged_starter_progression import PLAYER, hopper_item
from saved_world import read_data_file


BLOCK = "enderio:slice_and_splice"
BARREL = "3220 80 12"
ORIGINAL = (3210, *CASES[0][1:])
OLD_SOURCE = (3218, *CASES[4][1:])
OLD_REPLANTED = (3226, *OLD_SOURCE[1:])
NATIVE_SOURCE = (3234, *CASES[1][1:])
NATIVE_REPLANTED = (3242, *NATIVE_SOURCE[1:])
REMAINDER = COST - PAID
LEGACY_IO = 'IoConfig:{"0":1b,"1":2b}'
NATIVE_IO = 'IOConfig:{Modes:{up:"pull",down:"push"}}'
NATIVE_SETTINGS = f'RedstoneControl:"active_with_signal",{NATIVE_IO}'
LEGACY_SETTINGS = f'RedstoneControl:1,{LEGACY_IO}'
TYPED_IO = '"enderio:io_config":{Modes:{up:"pull",down:"push"}}'
TYPED_SETTINGS = f'"enderio:energy":{REMAINDER},"enderio:redstone_control":"active_with_signal",{TYPED_IO}'


def power(x):
    return f"{x} 80 7"


def tool_entries(legacy=False, added_wear=0):
    suffix = "b" if legacy else ""
    return [stack(slot, 1, item, count_suffix=suffix, metadata=f'{{Damage:{damage + added_wear},PortLegacySlicer:160}}')
            for slot, item, damage in ((6, AXE, 17), (7, SHEARS, 23))]


def inventory(case, legacy=False):
    suffix = "b" if legacy else ""
    entries = [stack(i, 1, item, count_suffix=suffix) for i, item in enumerate(case[2])]
    entries += tool_entries(legacy) + [stack(9, 1, CAP, count_suffix=suffix)]
    return f'Items:{{Items:[{",".join(entries)}]}}'


def task(name, legacy=False):
    suffix = "b" if legacy else ""
    return (f'Task:{{RecipeId:"enderio:slicing/{name}",ProgressMade:{PAID},ProgressRequired:{COST},HasDeterminedOutputs:1b,'
            f'Outputs:[{{Item:{{id:"enderio:{name}",Count:1{suffix}}}}}]}}')


def saved(case, energy, legacy=False):
    energy_tag = f"Energy:{{EnergyStored:{energy}}}" if legacy else f"EnergyStored:{energy}"
    settings = LEGACY_SETTINGS if legacy else NATIVE_SETTINGS
    return f'{energy_tag},{settings},{task(case[1], legacy)},{inventory(case, legacy)}'


def legacy(server, results):
    server.command("forceload add 3200 0 3247 15")
    server.assert_state("old_fixture_chunks_load", f"if loaded {mill(ORIGINAL[0])} if loaded {mill(NATIVE_REPLANTED[0])}", results)
    for command in ("gamerule doMobSpawning false", "gamerule doDaylightCycle false", "gamerule spawnRadius 0", "time set noon",
                    "fill 3207 79 5 3245 79 15 minecraft:stone_bricks", "setworldspawn 3224 80 14",
                    f"setblock {BARREL} minecraft:barrel"):
        server.command(command)
    for case, reserve in ((ORIGINAL, 0), (OLD_SOURCE, REMAINDER), (NATIVE_SOURCE, REMAINDER)):
        x, name, _ = case
        server.command(f'setblock {mill(x)} {BLOCK}[facing=south]{{{LEGACY_IO},'
                       f'Energy:{{EnergyStored:{PAID}}},{inventory(case, True)}}}')
        server.assert_state(f"forge6_{name}_pays_3600_and_queues_a_real_stock_output",
                            f'if data block {mill(x)} {{Energy:{{EnergyStored:0}},{task(name, True)},{inventory(case, True)}}}',
                            results, timeout=18)
        server.command(f'data merge block {mill(x)} {{{LEGACY_SETTINGS}}}')
        server.command(f"setblock {power(x)} minecraft:stone")
        if reserve:
            server.command(f'data merge block {mill(x)} {{Energy:{{EnergyStored:{reserve}}}}}')
        server.assert_state(f"forge6_{name}_saves_the_exact_paid_job_tools_and_idle_reserve",
                            f'if data block {mill(x)} {{{saved(case, reserve, True)}}}', results)

    x = OLD_SOURCE[0]
    server.command(f"loot replace block {BARREL} container.0 mine {mill(x)} minecraft:diamond_pickaxe")
    server.assert_state("the_original_native_drop_contains_all_old_machine_state",
                        f'if data block {BARREL} Items[{{Slot:0b,id:"{BLOCK}",Count:1b,'
                        f'tag:{{BlockEntityTag:{{{saved(OLD_SOURCE, REMAINDER, True)}}}}}}}]', results)
    server.command(f"setblock {mill(x)} minecraft:air")


def verify_pending(server, results, case, energy, label):
    x, _, _ = case
    server.command(f"data get block {mill(x)}")
    server.assert_state(label, f'if block {mill(x)} {BLOCK}[powered=false] if data block {mill(x)} {{{saved(case, energy)}}} '
                        f'unless data block {mill(x)} Items.Items[{{Slot:8}}]', results)


def verify_original(server, results, phase):
    verify_pending(server, results, ORIGINAL, 0, f"{phase}_original_z_logic_job_and_all_machine_state")
    verify_pending(server, results, NATIVE_SOURCE, REMAINDER, f"{phase}_original_electrode_job_and_full_remaining_charge")
    server.command(f"data get block {BARREL} Items[0]")
    server.assert_state(f"{phase}_original_loot_keeps_its_task_and_inventory_before_placement",
                        f'if data block {BARREL} Items[{{Slot:0b,id:"{BLOCK}",Count:1b,tag:{{BlockEntityTag:{{'
                        f'{task(OLD_SOURCE[1], True)},{inventory(OLD_SOURCE, True)}}}}}}}]', results)


def finished(case):
    x, name, _ = case
    tools = ",".join(tool_entries(added_wear=1) + [stack(9, 1, CAP)])
    empty_slots = " ".join(f'unless data block {mill(x)} Items.Items[{{Slot:{slot}}}]' for slot in (0, 1, 2, 3, 4, 5, 8))
    return (f'if data block {mill(x)} {{EnergyStored:0,{NATIVE_SETTINGS},Items:{{Items:[{tools}]}}}} '
            f'unless data block {mill(x)} Task {empty_slots} {hopper_item(output(x), "enderio:" + name, 1)}')


def energize(server, case, supply=False):
    x = case[0]
    server.command(f"setblock {output(x)} minecraft:hopper[facing=down]")
    if supply:
        charge(server, x, REMAINDER)
    server.command(f"setblock {power(x)} minecraft:redstone_block")


def native_loot(server, results):
    x, name, _ = NATIVE_SOURCE
    server.command(f"loot replace block {BARREL} container.1 mine {mill(x)} minecraft:diamond_pickaxe")
    carried = (f'"enderio:data":{{{TYPED_SETTINGS},"enderio:slicer_task":{{{task(name)}}},'
               f'"enderio:container":{{Items:[{stack(9, 1, CAP)}]}}}}')
    drop = stack(1, 1, BLOCK, metadata=f'{{{carried}}}', slot_suffix="b", count_suffix="b")
    server.assert_state("modern_native_loot_keeps_the_converted_paid_task_and_portable_fields",
                        f'if data block {BARREL} Items[{drop}]', results)
    server.command(f"setblock {mill(x)} minecraft:air")


def verify_and_resume_client(args, server, report):
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "legacy-slicer", "--server", f"127.0.0.1:{args.port}", "--output", str(evidence), "--timeout", "180"]
    results = report["assertions"]
    with (server.directory / "legacy-slicer-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            ready_at(server, ORIGINAL[0], "original", evidence)
            wait_signal(client, evidence, "original-menu")
            energize(server, ORIGINAL, supply=True)
            for slot, case, label in ((0, OLD_REPLANTED, "legacy"), (1, NATIVE_REPLANTED, "modern")):
                if slot == 1:
                    native_loot(server, results)
                server.command(f"item replace entity {PLAYER} hotbar.0 from block {BARREL} container.{slot}")
                server.command(f"item replace block {BARREL} container.{slot} with minecraft:air")
                ready_at(server, case[0], label, evidence)
                wait_signal(client, evidence, f"{label}-placed")
                verify_pending(server, results, case, REMAINDER, f"{label}_placement_keeps_charge_job_tools_and_sides")
                (evidence / f"server-{label}-menu").touch()
                wait_signal(client, evidence, f"{label}-menu")
                energize(server, case)

            for case in (ORIGINAL, OLD_REPLANTED, NATIVE_REPLANTED):
                try:
                    server.assert_state(f"the_saved_{case[1]}_job_finishes_from_the_remaining_fe_once",
                                        finished(case), results, timeout=39)
                finally:
                    server.command(f"data get block {mill(case[0])}")

            (evidence / "server-finished").touch()
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"Legacy Slice'N'Splice client failed; see {evidence}")
            summary = json.loads((evidence / "summary.json").read_text())
            frame = summary["client"]["captures"]["legacy-replanted-slicer"]
            if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Client and server loaded different Ender IO jars")
        finally:
            stop_client(client)
            summary = evidence / "summary.json"
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def verify_saved_player(directory, report):
    users = json.loads((directory / "usercache.json").read_text())
    player_id = next(user["uuid"] for user in users if user["name"] == PLAYER)
    player_file = directory / WORLD / "playerdata" / f"{player_id}.dat"
    inventory = read_data_file(player_file)["Inventory"]
    if any(stack["id"] == BLOCK for stack in inventory):
        raise AssertionError(f"A carried slicer remained in the saved player inventory: {player_file}")
    report["savedPlayer"] = {"passed": True, "playerFile": str(player_file)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for path in ("java", "forge-server", "jar", "legacy-jar"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    parser.add_argument("--launch", type=Path, help="Exercise survival placement and the native menu")
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")
    root = ROOT / "build/port-runtime" / args.name
    root.mkdir()
    source, port = root / "source", root / "port"
    prepare_profile(args, source, args.legacy_jar)
    prepare_profile(args, port, args.jar)
    report = {"passed": False, "assertions": [], "servers": [], "legacySha256": digest(args.legacy_jar),
              "jarSha256": digest(args.jar)}

    def resume(server, results):
        verify_original(server, results, "reopened")
        if args.launch:
            verify_and_resume_client(args, server, report)
        else:
            energize(server, ORIGINAL, supply=True)
            energize(server, NATIVE_SOURCE)
            for case in (ORIGINAL, NATIVE_SOURCE):
                server.assert_state(f"the_original_{case[1]}_job_finishes_on_its_exact_remaining_fe", finished(case), results, timeout=39)

    def reopen(server, results):
        cases = (ORIGINAL, OLD_REPLANTED, NATIVE_REPLANTED) if args.launch else (ORIGINAL, NATIVE_SOURCE)
        for case in cases:
            server.assert_state(f"{case[1]}_final_wear_controls_and_single_output_survive_restart", finished(case), results)
        if args.launch:
            server.assert_state("the_barrel_and_both_original_sites_remain_empty", f'unless data block {BARREL} Items[0] '
                                f'if block {mill(OLD_SOURCE[0])} minecraft:air if block {mill(NATIVE_SOURCE[0])} minecraft:air', results)

    try:
        run_stage(args, source, report, "legacy", legacy)
        shutil.copytree(source / WORLD, port / WORLD)
        run_stage(args, port, report, "converted", lambda server, results: verify_original(server, results, "converted"))
        run_stage(args, port, report, "reopened", resume)
        run_stage(args, port, report, "completed", reopen)
        if args.launch:
            verify_saved_player(port, report)
        report["passed"] = True
    finally:
        path = root / "legacy-slicer-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Legacy Slice'N'Splice passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
