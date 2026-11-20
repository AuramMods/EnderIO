#!/usr/bin/env python3
"""Migrate an active custom grinding ball from an authentic Forge 6.2 save."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_legacy_sag import CAPACITOR, IO, item, task
from packaged_sag_progression import DUST, PAID, charge, data_read, mill, output, output_count, ready_at
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage
from packaged_starter_progression import PLAYER, hopper_item


MILL_X = 3090
NEW_MILL_X = 3098
MODERN_MILL_X = 3102
PLACED_X = 3100
BALL = "minecraft:prismarine_crystals"
RECIPE = "port_fixture:grindingball/prismarine"
PACK = "port_custom_grinding"
ARCHIVE = "3095 80 12"
EARLY_COST = 1200
LATE_COST = 600
MODERN_COST = 1800
ORIGINAL_BALL = '{OutputMultiplier:2.0f,BonusMultiplier:1.5f,PowerUse:0.5f,Durability:10000}'
UPDATED_BALL = '{OutputMultiplier:2.0f,BonusMultiplier:1.5f,PowerUse:0.25f,Durability:10000}'
MODERN_BALL = '{OutputMultiplier:3.0f,BonusMultiplier:1.0f,PowerUse:0.75f,Durability:20000}'


def write_pack(directory, power):
    pack = directory / WORLD / "datapacks" / PACK
    path = pack / "data/port_fixture/recipes/grindingball/prismarine.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 15, "description": "Forge 6 grinding ball fixture"}}))
    path.write_text(json.dumps({"type": "enderio:grinding_ball", "item": BALL, "grinding": 2.0,
                               "chance": 1.5, "power": power, "durability": 10000}))


def add_current_data_map(directory):
    path = directory / WORLD / "datapacks" / PACK / "data/enderio/data_maps/item/grinding_ball.json"
    path.parent.mkdir(parents=True)
    path.write_text(json.dumps({"values": {BALL: {
        "OutputMultiplier": 3.0, "BonusMultiplier": 1.0, "PowerUse": 0.75, "Durability": 20000,
    }}}))


def tag(cost, wear):
    return f'GrindingBallId:"{RECIPE}",GrindingBallDamage:{wear},Task:{task(cost, wear)}'


def legacy(server, report):
    results = report["assertions"]
    for command in ("gamerule doMobSpawning false", "gamerule spawnRadius 0", "forceload add 3088 0 3103 15"):
        server.command(command)
    server.assert_state("original_world_loads_the_custom_ball_fixture", f"if loaded {mill(MILL_X)}", results)
    server.command("fill 3088 79 5 3103 79 14 minecraft:stone_bricks")
    server.command(f"setworldspawn {PLACED_X} 80 12")
    entries = ",".join((item(0, "minecraft:coal", 2, legacy=True), item(5, BALL, 2, legacy=True, tagged=True),
                        item(6, CAPACITOR, 1, legacy=True)))
    server.command(f'setblock {mill(MILL_X)} enderio:sag_mill[facing=south]{{Energy:{{EnergyStored:3000}},'
                   f'IoConfig:{{"0":1b,"1":2b}},Items:{{Size:7,Items:[{entries}]}}}}')
    server.assert_state("forge6_consumes_one_custom_ball_and_spends_600_fe_on_the_next_coal",
                        f'if data block {mill(MILL_X)} {{Energy:{{EnergyStored:0}},{tag(EARLY_COST, PAID)}}} '
                        f"if data block {mill(MILL_X)} Items.Items[{item(5, BALL, 1, legacy=True, tagged=True)}]",
                        results, timeout=25)
    report["originalOutput"] = int(data_read(server, mill(MILL_X), "Items.Items[{Slot:1}].Count").removesuffix("b"))
    report["originalRoll"] = int(data_read(server, mill(MILL_X),
        f'Task.Outputs[{{Item:{{id:"{DUST}"}}}}].Item.Count').removesuffix("b"))
    server.command(f"setblock {ARCHIVE} minecraft:barrel")
    server.command(f"loot replace block {ARCHIVE} container.0 mine {mill(MILL_X)} minecraft:diamond_pickaxe")
    server.assert_state("forge6_loot_keeps_the_original_id_and_wear",
                        f'if data block {ARCHIVE} Items[{{Slot:0b,id:"enderio:sag_mill",'
                        f'tag:{{BlockEntityTag:{{{tag(EARLY_COST, PAID)}}}}}}}]', results)


def pending(server, report, stage, *, available=True, x=MILL_X):
    pos = mill(x)
    snapshot = f"if data block {pos} {{GrindingBal:{ORIGINAL_BALL}}}" if available else f"unless data block {pos} GrindingBal"
    original_roll = f'{{Item:{{id:"{DUST}",Count:{report["originalRoll"]}}}}}'
    server.command(f"data get block {pos}")
    server.assert_state(f"{stage}_preserves_the_ball_id_wear_and_paid_job",
                        f"if data block {pos} {{{tag(EARLY_COST, PAID)},{IO},EnergyStored:0}} {snapshot} "
                        f"if data block {pos} Items.Items[{item(5, BALL, 1, tagged=True)}] "
                        f"if data block {pos} Task.Outputs[{original_roll}]",
                        report["assertions"], timeout=15)


def finished(report, x=MILL_X):
    total = report["originalOutput"] + report["originalRoll"]
    return (f'if data block {mill(x)} {{GrindingBallId:"{RECIPE}",GrindingBal:{ORIGINAL_BALL},'
            f'GrindingBallDamage:{EARLY_COST},EnergyStored:0,{IO}}} '
            f"unless data block {mill(x)} Task "
            f"if data block {mill(x)} Items.Items[{item(5, BALL, 1, tagged=True)}] "
            f"{hopper_item(output(x), DUST, total)}")


def finish_saved(server, report, x, name):
    server.command(f"setblock {output(x)} minecraft:hopper[facing=down]")
    charge(server, x, EARLY_COST - PAID)
    server.assert_state(name, finished(report, x), report["assertions"], timeout=20)


def resume(server, report):
    pending(server, report, "changed_recipe")
    finish_saved(server, report, MILL_X, "the_original_snapshot_completes_for_exactly_600_more_fe")
    server.assert_state("the_unplaced_original_loot_keeps_its_migration_payload",
                        f'if data block {ARCHIVE} Items[{{Slot:0b,id:"enderio:sag_mill",'
                        f'tag:{{BlockEntityTag:{{{tag(EARLY_COST, PAID)}}}}}}}]', report["assertions"])
    fresh_ball(server, report, NEW_MILL_X, UPDATED_BALL, LATE_COST, 2)


def fresh_ball(server, report, x, modifiers, cost, guaranteed_roll):
    results = report["assertions"]
    pos = mill(x)
    inlet = f"{x} 81 8"
    server.command(f'setblock {pos} enderio:sag_mill[facing=south]{{{IO},'
                   f'Items:{{Size:7,Items:[{item(6, CAPACITOR, 1)}]}}}}')
    server.command(f"setblock {inlet} minecraft:hopper[facing=down]")
    server.command(f"setblock {output(x)} minecraft:hopper[facing=down]")
    server.command(f"item replace block {inlet} container.0 with minecraft:coal 2")
    server.command(f"item replace block {inlet} container.1 with {BALL}{{PortSAG:157}}")
    server.assert_state(f"the_hopper_inserts_the_custom_ball_{x}",
                        f"if data block {pos} Items.Items[{item(5, BALL, 1, tagged=True)}] "
                        f"if data block {pos} Items.Items[{item(0, 'minecraft:coal', 2)}]", results)
    charge(server, x, 2500)
    server.assert_state(f"new_grinding_pays_100_fe_into_the_selected_ball_cost_{x}",
                        f"if data block {pos} {{GrindingBal:{modifiers},GrindingBallDamage:100,EnergyStored:0,"
                        f"Task:{task(cost, 100)}}} if data block {output(x)} Items[{{Slot:0b}}]",
                        results, timeout=20)
    first = output_count(server, x)
    queued = int(data_read(server, pos, f'Task.Outputs[{{Item:{{id:"{DUST}"}}}}].Item.Count').removesuffix("b"))
    valid = 1 <= first <= 2 and guaranteed_roll <= queued <= guaranteed_roll * 2
    results.append({"assertion": f"the_actual_coal_roll_is_within_the_recipe_bounds_{x}", "passed": valid,
                    "firstPowder": first, "queuedPowder": queued, "guaranteedQueuedPowder": guaranteed_roll})
    if not valid:
        raise AssertionError(f"Coal recipe at {pos} yielded {first} powder and queued {queued}; see {server.log_path}")
    charge(server, x, cost - 100)
    server.assert_state(f"new_grinding_uses_the_selected_ball_definition_{x}",
                        f"if data block {pos} {{GrindingBal:{modifiers},GrindingBallDamage:{cost},EnergyStored:0}} "
                        f"unless data block {pos} GrindingBallId unless data block {pos} Task "
                        f"unless data block {pos} Items.Items[{{Slot:5}}] "
                        f"{hopper_item(output(x), DUST, first + queued)}", results, timeout=20)


def restart(server, report):
    server.assert_state("restart_keeps_the_original_snapshot_wear_and_output", finished(report), report["assertions"])
    fresh_ball(server, report, MODERN_MILL_X, MODERN_BALL, MODERN_COST, 3)


def guarded_client(args, server, report):
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "legacy-custom-sag", "--server", f"127.0.0.1:{args.port}", "--output", str(evidence),
               "--timeout", "220"]
    pending(server, report, "client_fixture")
    with (server.directory / "legacy-custom-sag-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            ready_at(server, MILL_X, "custom", evidence)
            wait_signal(client, evidence, "custom-paused")
            finish_saved(server, report, MILL_X, "the_placed_custom_job_finishes_in_the_open_menu")
            (evidence / "server-custom-done").touch()
            wait_signal(client, evidence, "custom-done")

            server.command(f"item replace entity {PLAYER} hotbar.0 from block {ARCHIVE} container.0")
            server.command(f"item replace block {ARCHIVE} container.0 with minecraft:air")
            ready_at(server, PLACED_X, "place", evidence)
            wait_signal(client, evidence, "placed")
            pending(server, report, "survival_replanted", x=PLACED_X)
            (evidence / "server-carried").touch()
            wait_signal(client, evidence, "carried-paused")
            finish_saved(server, report, PLACED_X, "the_original_carried_custom_job_finishes_for_exactly_600_fe")
            (evidence / "server-carried-done").touch()

            if client.wait(timeout=35) != 0:
                raise AssertionError(f"Legacy custom SAG client failed; see {evidence}")
            summary = json.loads((evidence / "summary.json").read_text())
            loaded = summary["client"]["captures"]["custom-paused"]["loadedMods"]["enderio"]["file"]
            if digest(Path(loaded)) != report["jarSha256"]:
                raise AssertionError("Client and server loaded different Ender IO jars")
        finally:
            stop_client(client)
            summary = evidence / "summary.json"
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def client_restart(server, report):
    for name, x in (("placed", MILL_X), ("carried", PLACED_X)):
        server.assert_state(f"{name}_custom_ball_and_exact_hopper_outputs_survive_restart", finished(report, x),
                            report["assertions"])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", type=int, required=True)
    for path in ("java", "forge-server", "legacy-jar", "jar"):
        parser.add_argument(f"--{path}", type=Path, required=True)
    parser.add_argument("--launch", type=Path, help="Verify packet sync, JEI and survival placement with a guarded client")
    parser.add_argument("--jei-jar", type=Path, help="Install the pinned JEI jar for the guarded stage")
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    directory.mkdir()
    source, port, missing = (directory / profile for profile in ("legacy", "port", "missing"))
    prepare_profile(args, source, args.legacy_jar)
    prepare_profile(args, port, args.jar)
    prepare_profile(args, missing, args.jar)
    write_pack(source, 0.5)
    report = {"passed": False, "assertions": [], "servers": [],
              "legacySha256": digest(args.legacy_jar), "jarSha256": digest(args.jar)}
    try:
        run_stage(args, source, report, "legacy", lambda server, _: legacy(server, report))
        shutil.copytree(source / WORLD, port / WORLD)
        shutil.copytree(source / WORLD, missing / WORLD, ignore=shutil.ignore_patterns(PACK))
        run_stage(args, port, report, "converted", lambda server, _: pending(server, report, "converted"))
        write_pack(port, 0.25)
        run_stage(args, port, report, "resumed", lambda server, _: resume(server, report))
        add_current_data_map(port)
        run_stage(args, port, report, "finished", lambda server, _: restart(server, report))
        run_stage(args, missing, report, "missing_definition", lambda server, _: pending(
            server, report, "missing_definition", available=False))
        write_pack(missing, 0.5)
        run_stage(args, missing, report, "returned_definition", lambda server, _: pending(
            server, report, "returned_definition"))
        if args.launch is not None:
            guarded = directory / "guarded"
            prepare_profile(args, guarded, args.jar)
            if args.jei_jar is not None:
                installed = guarded / "mods" / args.jei_jar.name
                shutil.copyfile(args.jei_jar, installed)
                report["jeiSha256"] = digest(installed)
            shutil.copytree(source / WORLD, guarded / WORLD)
            run_stage(args, guarded, report, "guarded", lambda server, _: guarded_client(args, server, report))
            run_stage(args, guarded, report, "guarded_restart", lambda server, _: client_restart(server, report))
        report["passed"] = True
    finally:
        path = directory / "legacy-custom-sag-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Custom SAG passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
