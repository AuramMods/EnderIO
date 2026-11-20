#!/usr/bin/env python3
"""Verify fully paid stock Soul Binder jobs behind a full return slot."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_legacy_painting import stack
from packaged_nutritious_stick import advance
from packaged_server_smoke import ROOT, digest
from packaged_soul_binder import VIAL, item, xp_tank
from packaged_spawners import PLAYER, prepare_profile, run_stage


UNCHANGED = "4914 80 8"
SWAPPED = "4922 80 8"
COLLECTOR = "4914 79 8"
LOCK = "4914 78 8"
PANEL = "enderio:energetic_photovoltaic_module"
RECIPE = "enderio:soulbinding/energetic_photovoltaic_module"
CAPACITOR = "enderio:octadic_capacitor"
ENERGY = 12800
XP_COST = 2240
XP_REMAINDER = 37
XP_STOCK = XP_COST + XP_REMAINDER
MATERIAL = "{PortStock:230}"
SOUL_PATH = '"enderio:data"."enderio:soul"'


def soul(name, health, extra=""):
    value = json.dumps({"text": name}, separators=(",", ":"))
    entity_tag = f"{{CustomName:'{value}',Health:{health}.0f{extra}}}"
    return '{"enderio:data":{"enderio:soul":{entity_type:"minecraft:phantom",entity_tag:' + entity_tag + '}}}'


ORIGINAL = soul("Flint", 18, ",NoAI:1b")
REPLACEMENT = soul("Nova", 16)


def paid(pos, vial):
    inventory = ",".join((stack(0, 1, VIAL, metadata=vial), stack(1, 1, PANEL, metadata=MATERIAL), stack(3, 64, VIAL)))
    return (f'if data block {pos} {{EnergyStored:0,{xp_tank(XP_STOCK)},Items:{{Items:[{inventory}]}},'
            f'Task:{{RecipeId:"{RECIPE}",ProgressMade:{ENERGY},ProgressRequired:{ENERGY},HasDeterminedOutputs:1b}}}} '
            f"unless data block {pos} Items.Items[{{Slot:2}}]")


def delivered(pos, soul_tag, vials):
    product = stack(2, 1, PANEL, metadata=soul_tag)
    return (f"if data block {pos} {{EnergyStored:0,{xp_tank(XP_REMAINDER)},"
            f"Items:{{Items:[{product},{stack(3, vials, VIAL)},{stack(4, 1, CAPACITOR)}]}}}} "
            f"unless data block {pos} Task unless data block {pos} Items.Items[{{Slot:0}}] "
            f"unless data block {pos} Items.Items[{{Slot:1}}] unless data block {pos} Items.Items[{{Slot:3}}].tag.{SOUL_PATH}")


def load_job(server, x):
    pos = f"{x} 80 8"
    server.command(f"setblock {pos} enderio:soul_binder[facing=south]")
    server.command(f"data merge block {pos} {{EnergyStored:{ENERGY},{xp_tank(XP_STOCK)},"
                   f"Items:{{Size:5,Items:[{stack(3, 64, VIAL)},{stack(4, 1, CAPACITOR)}]}}}}")
    for offset, facing, value, tag in ((-1, "east", VIAL, ORIGINAL), (1, "west", PANEL, MATERIAL)):
        supply = f"{x + offset} 80 8"
        input_stack = item(0, value, f"tag:{tag}", vanilla=True)
        server.command(f"setblock {supply} minecraft:hopper[facing={facing}]{{Items:[{input_stack}]}}")


def fresh(server, results):
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    server.command("forceload add 4912 0 4927 15")
    server.assert_state("both_binders_are_loaded", f"if loaded {UNCHANGED} if loaded {SWAPPED}", results)
    server.command("fill 4912 79 6 4927 79 14 minecraft:stone_bricks")
    server.command("setworldspawn 4922 80 11")
    load_job(server, 4914)
    load_job(server, 4922)
    server.command(f"setblock {LOCK} minecraft:redstone_block")
    filler = [stack(index, 64, "minecraft:cobblestone", slot_suffix="b", count_suffix="b") for index in range(1, 5)]
    stock = stack(0, 63, VIAL, slot_suffix="b", count_suffix="b")
    server.command(f"setblock {COLLECTOR} minecraft:hopper[facing=down]{{Items:[{','.join([stock, *filler])}]}}")
    server.assert_state("both_jobs_pay_once_and_wait_for_the_return_slot",
                        f"{paid(UNCHANGED, ORIGINAL)} {paid(SWAPPED, ORIGINAL)}", results, timeout=40)
    advance(server)
    server.assert_state("blocked_outputs_keep_both_phantom_vials_and_all_xp",
                        f"{paid(UNCHANGED, ORIGINAL)} {paid(SWAPPED, ORIGINAL)}", results)


def stacked_vials():
    return f"if data block {COLLECTOR} Items[{stack(0, 64, VIAL, slot_suffix='b', count_suffix='b')}]"


def customer_stock():
    original = item(5, VIAL, f"tag:{ORIGINAL}", vanilla=True)
    removed = f'{{id:"{VIAL}",Count:64b}}'
    return (f"if data entity {PLAYER} Inventory[{original}] if data entity {PLAYER} Inventory[{removed}] "
            f'unless data entity {PLAYER} Inventory[{{id:"{PANEL}"}}]')


def exercise_client(args, server, results, report):
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    summary = evidence / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "soul-binder-backpressure", "--server", f"127.0.0.1:{args.port}",
               "--output", str(evidence), "--timeout", "360"]
    with (server.directory / "binder-backpressure-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            server.command(f"tp {PLAYER} 4922.5 80 10.5 180 15")
            server.command(f"item replace entity {PLAYER} hotbar.5 with {VIAL}{REPLACEMENT}")
            (evidence / "server-initial").touch()
            wait_signal(client, evidence, "swapped")
            returned = item(5, VIAL, f"tag:{ORIGINAL}", vanilla=True)
            server.assert_state("live_hotbar_swap_preserves_the_old_vial_and_paid_work",
                                f"{paid(SWAPPED, REPLACEMENT)} if data entity {PLAYER} Inventory[{returned}]", results)
            advance(server)
            server.command(f"data get block {SWAPPED} Task.Outputs")
            (evidence / "server-clear").touch()
            try:
                wait_signal(client, evidence, "completed")
            except RuntimeError:
                server.command(f"data get block {SWAPPED}")
                raise
            server.assert_state("the_replacement_phantom_is_bound_with_no_extra_fe_or_xp",
                                f"{delivered(SWAPPED, REPLACEMENT, 1)} {customer_stock()} "
                                f"unless data block {SWAPPED} Items.Items[{{Slot:2}}].tag.{SOUL_PATH}.entity_tag.NoAI", results)
            (evidence / "server-verified").touch()
            if client.wait(timeout=35) != 0:
                raise AssertionError(f"Soul Binder output client failed; see {evidence}")
            mods = json.loads(summary.read_text())["client"]["captures"]["completed"]["loadedMods"]
            match = digest(Path(mods["enderio"]["file"])) == report["jarSha256"] and "jei" not in mods
            results.append({"assertion": "guarded_client_loads_the_same_core_product_jar", "passed": match})
            if not match:
                raise AssertionError("Soul Binder output test loaded unexpected mods")
        finally:
            stop_client(client)
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def reopened(args, server, results, report):
    server.assert_state("both_fully_paid_jobs_survive_restart_behind_full_outputs",
                        f"{paid(UNCHANGED, ORIGINAL)} {paid(SWAPPED, ORIGINAL)}", results)
    server.command(f"setblock {LOCK} minecraft:air")
    server.assert_state("one_hopper_extraction_releases_exactly_one_paid_product_and_vial",
                        f"{delivered(UNCHANGED, ORIGINAL, 64)} {stacked_vials()}", results)
    exercise_client(args, server, results, report)


def retained(server, results):
    advance(server)
    server.assert_state("both_bound_products_remaining_xp_and_returned_vials_survive_restart",
                        f"{delivered(UNCHANGED, ORIGINAL, 64)} {delivered(SWAPPED, REPLACEMENT, 1)} {stacked_vials()}", results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for path in ("java", "forge-server", "jar", "launch"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}
    try:
        run_stage(args, directory, report, "fresh", fresh, startup_timeout=300)
        run_stage(args, directory, report, "reopened", lambda server, results: reopened(args, server, results, report),
                  startup_timeout=300)
        run_stage(args, directory, report, "retained", retained, startup_timeout=300)
        report["passed"] = True
    finally:
        destination = directory / "binder-backpressure-results.json"
        destination.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Soul Binder output passed={report['passed']}, {len(report['assertions'])} assertions, {destination}", flush=True)


if __name__ == "__main__":
    main()
