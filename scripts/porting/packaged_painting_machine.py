#!/usr/bin/env python3
"""Exercise Painting Machine payment, advancement and placement on packaged Forge."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_spawners import prepare_profile, run_stage


PLAYER = "EnderIOQA"
MACHINE = "2504 80 8"
RESTART_MACHINE = "2520 80 8"
INVALID_MACHINE = "2523 80 8"
FENCE = "2504 80 13"
BARREL = "2507 80 13"
COST = 2400
CAPACITOR = '{Slot:0,id:"enderio:basic_capacitor",Count:1}'
FULL = '{Slot:3,id:"minecraft:cobblestone",Count:64}'


def template(count):
    return f'{{Slot:1,id:"minecraft:oak_fence",Count:{count}}}'


def palette(block, count=1):
    return f'{{Slot:2,id:"minecraft:{block}",Count:{count}}}'


def paint_tag(block):
    return f'tag:{{"enderio:data":{{"enderio:block_paint":{{paint:"minecraft:{block}"}}}}}}'


def product(block, slot="3"):
    return f'{{Slot:{slot},id:"enderio:painted_fence",{paint_tag(block)}}}'


def inventory(pos, items, charge):
    return f'if data block {pos} {{EnergyStored:{charge},Items:{{Items:[{",".join([CAPACITOR, *items])}]}}}}'


def paint_and_extract(server, results, machine, hopper, kind, template_item, paint):
    server.command(f"setblock {machine} enderio:painting_machine[facing=south]")
    entries = [CAPACITOR, f'{{Slot:1,id:"minecraft:{template_item}",Count:1}}', palette(paint)]
    server.command(f'data merge block {machine} {{EnergyStored:{COST},Items:{{Items:[{",".join(entries)}]}}}}')
    output = f'id:"enderio:painted_{kind}",{paint_tag(paint)}'
    server.assert_state(f"native_recipe_paints_{kind}",
                        f"{inventory(machine, [palette(paint), f'{{Slot:3,Count:1,{output}}}'], 0)} "
                        f"unless data block {machine} Items.Items[{{Slot:1}}]", results, timeout=20)
    server.command(f"setblock {hopper} minecraft:hopper[facing=down]")
    server.assert_state(f"native_hopper_extracts_only_the_painted_{kind}",
                        f'if data block {hopper} Items[{{Slot:0b,Count:1b,{output}}}] '
                        f"{inventory(machine, [palette(paint)], 0)} unless data block {machine} Items.Items[{{Slot:3}}]", results)


def stock(server, pos, block, output=None):
    entries = [CAPACITOR, template(2), palette(block)]
    if output is not None:
        entries.append(output)
    server.command(f"setblock {pos} enderio:painting_machine[facing=south]")
    server.command(f'data merge block {pos} {{EnergyStored:{COST},Items:{{Items:[{",".join(entries)}]}}}}')


def fully_paid(pos):
    return (f"{inventory(pos, [template(2), palette('diamond_block'), FULL], 0)} "
            f'if data block {pos} {{Task:{{RecipeId:"enderio:painting/painted_fence",ProgressMade:{COST},ProgressRequired:{COST}}}}}')


def fence_paint():
    return (f'if block {FENCE} enderio:painted_fence if data block {FENCE} {{Paint:"minecraft:diamond_block"}} '
            f'if data block {BARREL} Items[{product("diamond_block", "0b")}]')


def setup(server, results):
    server.command("forceload add 2496 0 2527 15")
    server.assert_state("machine_and_restart_chunks_are_loaded",
                        f"if loaded {MACHINE} if loaded {RESTART_MACHINE} if loaded {FENCE}", results)
    server.command("gamerule doMobSpawning false")
    server.command("gamerule doDaylightCycle false")
    server.command("gamerule spawnRadius 0")
    server.command("time set noon")
    server.command("fill 2499 79 5 2526 79 18 minecraft:stone")
    server.command("setworldspawn 2504 80 11")
    for pos in (MACHINE, RESTART_MACHINE):
        stock(server, pos, "diamond_block", FULL)
    stock(server, INVALID_MACHINE, "stick")
    server.assert_state("two_blocked_jobs_pay_the_full_cost_without_consuming_inputs",
                        f"{fully_paid(MACHINE)} {fully_paid(RESTART_MACHINE)}", results, timeout=20)
    server.assert_state("invalid_saved_paint_preserves_energy_and_input_without_a_job",
                        f"{inventory(INVALID_MACHINE, [template(2), palette('stick')], COST)} "
                        f"unless data block {INVALID_MACHINE} Task unless data block {INVALID_MACHINE} Items.Items[{{Slot:3}}]", results)


def exercise_client(args, server, report):
    results = report["assertions"]
    output = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "painting-machine", "--output", str(output), "--server", f"127.0.0.1:{args.port}", "--timeout", "155"]
    with (server.directory / "painting-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            server.command(f"tp {PLAYER} 2504.5 80 11.5 180 18")
            nearby_with_advancement = f"@a[name={PLAYER},x=2504.5,y=80,z=8.5,distance=..5,advancements={{enderio:adventure/rich=true}}]"
            server.assert_state("nearby_player_earns_no_advancement_while_the_output_is_full",
                                f"{fully_paid(MACHINE)} if entity @a[name={PLAYER},x=2504.5,y=80,z=8.5,distance=..5] "
                                f"unless entity {nearby_with_advancement}", results)
            server.command(f"data remove block {MACHINE} Items.Items[{{Slot:3}}]")
            server.assert_state("actual_delivery_consumes_one_template_and_awards_the_diamond_advancement",
                                f"{inventory(MACHINE, [template(1), palette('diamond_block'), product('diamond_block')], 0)} "
                                f"if entity {nearby_with_advancement}", results)
            (output / "server-menu").touch()
            wait_signal(client, output, "taken")
            server.assert_state("menu_transfers_the_exact_painted_stack_to_the_player",
                                f'if data entity {PLAYER} Inventory[{{id:"enderio:painted_fence",Count:1b,{paint_tag("diamond_block")}}}] '
                                f"unless data block {MACHINE} Items.Items[{{Slot:3}}]", results)

            server.command(f"tp {PLAYER} 2504.5 80 15.5 180 19")
            (output / "server-place").touch()
            wait_signal(client, output, "placed")
            server.assert_state("survival_placement_moves_the_paint_into_the_block_entity",
                                f'if data block {FENCE} {{Paint:"minecraft:diamond_block"}} '
                                f'unless data entity {PLAYER} Inventory[{{id:"enderio:painted_fence"}}]', results)
            server.command(f"setblock {BARREL} minecraft:barrel")
            server.command(f"loot replace block {BARREL} container.0 mine {FENCE} minecraft:diamond_axe")
            server.assert_state("native_fence_loot_copies_the_placed_paint", fence_paint(), results)
            (output / "server-paint-verified").touch()

            if client.wait(timeout=30) != 0:
                raise AssertionError(f"Painting Machine client failed; see {output}")
            summary = json.loads((output / "summary.json").read_text())
            frame = summary["client"]["captures"]["painting-machine"]
            if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Client and server loaded different Ender IO jars")
        finally:
            stop_client(client)
            summary = output / "summary.json"
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def reopen(server, results):
    server.assert_state("placed_paint_dropped_paint_and_paid_job_survive_restart",
                        f"{fence_paint()} {fully_paid(RESTART_MACHINE)}", results)
    server.command(f"data modify block {RESTART_MACHINE} Items.Items[{{Slot:2}}] set value {palette('gold_block', 2)}")
    server.command(f"data remove block {RESTART_MACHINE} Items.Items[{{Slot:3}}]")
    server.assert_state("saved_paid_job_delivers_the_newly_installed_gold_without_more_energy",
                        inventory(RESTART_MACHINE, [template(1), palette("gold_block", 2), product("gold_block")], 0), results)
    server.command(f"data modify block {INVALID_MACHINE} Items.Items[{{Slot:2}}] set value {palette('stone')}")
    server.assert_state("a_valid_replacement_uses_the_original_saved_charge_and_leaves_the_palette",
                        inventory(INVALID_MACHINE, [template(1), palette("stone"), product("stone")], 0), results, timeout=15)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--launch", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "forge": FORGE, "jarSha256": digest(directory / "mods/enderio.jar"),
              "assertions": [], "servers": []}
    try:
        def fresh(server, results):
            setup(server, results)
            exercise_client(args, server, report)

        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", reopen)
        report["passed"] = True
    finally:
        path = directory / "painting-machine-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Painting Machine passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
