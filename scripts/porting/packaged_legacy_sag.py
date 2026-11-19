#!/usr/bin/env python3
"""Open authentic Forge 6.2 SAG Mill jobs with active and carried grinding balls."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_sag_progression import COAL, DUST, PAID, charge, data_read, mill, output, ready_at
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage
from packaged_starter_progression import PLAYER, hopper_item


FLINT_X, COPPER_X, CARRIED_SOURCE_X, PLACED_X = 3090, 3098, 3104, 3110
CHEST = "3105 80 12"
OLD_COPPER = "enderio:copper_alloy_grinding_ball"
NEW_COPPER = "enderio:conductive_alloy_grinding_ball"
FLINT = '{OutputMultiplier:1.2f,BonusMultiplier:1.25f,PowerUse:0.85f,Durability:24000}'
COPPER = '{OutputMultiplier:1.2f,BonusMultiplier:1.65f,PowerUse:0.8f,Durability:40000}'
CAPACITOR = "enderio:basic_capacitor"
IO = 'IOConfig:{Modes:{up:"pull",down:"push"}}'

MILLS = (("flint", FLINT_X, "minecraft:flint", "flint", 2040, FLINT),
         ("copper", COPPER_X, OLD_COPPER, "copper_alloy_grinding_ball", 1920, COPPER),
         ("carried", CARRIED_SOURCE_X, OLD_COPPER, "copper_alloy_grinding_ball", 1920, COPPER))


def load(server, results):
    server.command("forceload add 3088 0 3119 15")
    server.assert_state("legacy_sag_chunks_are_ready", f"if loaded {mill(FLINT_X)} if loaded {mill(PLACED_X)}", results)


def item(slot, name, count, legacy=False, tagged=False, vanilla=False):
    slot_suffix = "b" if vanilla else ""
    count_suffix = "b" if legacy or vanilla else ""
    extra = ',tag:{PortSAG:157}' if tagged else ""
    return f'{{Slot:{slot}{slot_suffix},id:"{name}",Count:{count}{count_suffix}{extra}}}'


def task(cost, paid=PAID):
    return f'{{RecipeId:"{COAL}",ProgressMade:{paid},ProgressRequired:{cost},HasDeterminedOutputs:1b}}'


def old_stack(name, cost):
    return (f'{{Energy:{{EnergyStored:0}},GrindingBallId:"enderio:grindingball/{name}",GrindingBallDamage:{PAID},'
            f'Task:{task(cost)}}}')


def legacy(server, report):
    results = report["assertions"]
    load(server, results)
    for command in ("gamerule doMobSpawning false", "gamerule doDaylightCycle false", "gamerule spawnRadius 0",
                    "time set noon", "fill 3088 79 5 3115 79 15 minecraft:stone_bricks",
                    "setworldspawn 3110 80 12", f"setblock {CHEST} minecraft:barrel"):
        server.command(command)
    for name, x, ball, ball_id, cost, _ in MILLS:
        entries = (item(0, "minecraft:coal", 2, legacy=True), item(5, ball, 2, legacy=True, tagged=True),
                   item(6, CAPACITOR, 1, legacy=True))
        server.command(f'setblock {mill(x)} enderio:sag_mill[facing=south]{{Energy:{{EnergyStored:3000}},'
                       f'IoConfig:{{"0":1b,"1":2b}},Items:{{Size:7,Items:[{",".join(entries)}]}}}}')
        server.assert_state(f"forge6_{name}_activates_its_ball_and_saves_600_fe_of_the_next_grind",
                            f'if data block {mill(x)} {old_stack(ball_id, cost)} '
                            f'if data block {mill(x)} Items.Items[{item(5, ball, 1, legacy=True, tagged=True)}] '
                            f'if data block {mill(x)} Items.Items[{item(0, "minecraft:coal", 1, legacy=True)}]',
                            results, timeout=22)
        report[name] = {"cost": cost,
                        "inOutput": int(data_read(server, mill(x), "Items.Items[{Slot:1}].Count").removesuffix("b")),
                        "rolled": int(data_read(server, mill(x), f'Task.Outputs[{{Item:{{id:"{DUST}"}}}}].Item.Count').removesuffix("b")),
                        "oldOutputs": data_read(server, mill(x), "Task.Outputs")}

    server.command(f"loot replace block {CHEST} container.0 mine {mill(CARRIED_SOURCE_X)} minecraft:diamond_pickaxe")
    server.assert_state("forge6_native_sag_loot_saves_ball_damage_and_the_original_queued_task",
                        f'if data block {CHEST} Items[{{Slot:0b,id:"enderio:sag_mill",Count:1b,'
                        f'tag:{{BlockEntityTag:{old_stack("copper_alloy_grinding_ball", 1920)}}}}}]', results)
    server.command(f"setblock {mill(CARRIED_SOURCE_X)} minecraft:air")
    server.command(f'item replace block {CHEST} container.1 with {OLD_COPPER}{{PortSAG:157}} 3')


def converted_mill(x, modifiers, bill, spare):
    return (f'if data block {mill(x)} {{EnergyStored:0,GrindingBal:{modifiers},GrindingBallDamage:{PAID},Task:{task(bill)},{IO},'
            f'Items:{{Items:[{item(0, "minecraft:coal", 1)},{item(5, spare, 1, tagged=True)},{item(6, CAPACITOR, 1)}]}}}} '
            f"unless data block {mill(x)} GrindingBallId")


def verify_pending(server, report, name, x, modifiers, cost, spare, phase):
    saved = report[name]
    rolled = f'{{Item:{{id:"{DUST}",Count:{saved["rolled"]}}}}}'
    server.command(f"data get block {mill(x)}")
    server.assert_state(f"{phase}_{name}_preserves_historical_ball_wear_io_inventory_and_roll",
                        f'{converted_mill(x, modifiers, cost, spare)} '
                        f'if data block {mill(x)} Items.Items[{item(1, DUST, saved["inOutput"])}] '
                        f'if data block {mill(x)} Task.Outputs[{rolled}]',
                        report["assertions"])


def verify_loose(server, report, phase):
    server.assert_state(f"{phase}_loose_tagged_copper_spares_resolve_to_the_conductive_item",
                        f'if data block {CHEST} Items[{item(1, NEW_COPPER, 3, tagged=True, vanilla=True)}]', report["assertions"])


def verify_original(server, report, phase):
    load(server, report["assertions"])
    for name, x, ball, _, cost, modifiers in MILLS[:2]:
        spare = NEW_COPPER if ball == OLD_COPPER else ball
        verify_pending(server, report, name, x, modifiers, cost, spare, phase)
    verify_loose(server, report, phase)
    server.assert_state(f"{phase}_native_loot_keeps_legacy_ball_id_and_queued_job_until_placement",
                        f'if data block {CHEST} Items[{{Slot:0b,id:"enderio:sag_mill",Count:1b,tag:{{BlockEntityTag:'
                        f'{{GrindingBallId:"enderio:grindingball/copper_alloy_grinding_ball",GrindingBallDamage:{PAID},'
                        f'Task:{task(1920)}}}}}}}]', report["assertions"])


def finished_conditions(report, name, x, modifiers, spare):
    saved = report[name]
    return (f'if data block {mill(x)} {{EnergyStored:0,GrindingBal:{modifiers},GrindingBallDamage:{saved["cost"]},{IO},'
            f'Items:{{Items:[{item(5, spare, 1, tagged=True)},{item(6, CAPACITOR, 1)}]}}}} '
            f'unless data block {mill(x)} Task unless data block {mill(x)} Items.Items[{{Slot:0}}] '
            f'unless data block {mill(x)} Items.Items[{{Slot:1}}] '
            f'{hopper_item(output(x), DUST, saved["inOutput"] + saved["rolled"])}')


def finish_saved(server, report, name, x, modifiers, spare):
    server.command(f"setblock {output(x)} minecraft:hopper[facing=down]")
    charge(server, x, report[name]["cost"] - PAID)
    server.assert_state(f"{name}_pays_only_the_remainder_and_exports_the_exact_historical_roll",
                        finished_conditions(report, name, x, modifiers, spare), report["assertions"], timeout=15)


def run_client(args, server, report):
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "legacy-sag", "--server", f"127.0.0.1:{args.port}", "--output", str(evidence), "--timeout", "170"]
    with (server.directory / "legacy-sag-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            ready_at(server, COPPER_X, "copper", evidence)
            wait_signal(client, evidence, "copper-paused")
            finish_saved(server, report, "copper", COPPER_X, COPPER, NEW_COPPER)
            (evidence / "server-copper-done").touch()
            wait_signal(client, evidence, "copper-done")

            server.command(f"item replace entity {PLAYER} hotbar.0 from block {CHEST} container.0")
            server.command(f"item replace block {CHEST} container.0 with minecraft:air")
            ready_at(server, PLACED_X, "place", evidence)
            wait_signal(client, evidence, "placed")
            verify_pending(server, report, "carried", PLACED_X, COPPER, 1920, NEW_COPPER, "replanted")
            (evidence / "server-carried").touch()
            wait_signal(client, evidence, "carried-paused")
            finish_saved(server, report, "carried", PLACED_X, COPPER, NEW_COPPER)
            (evidence / "server-carried-done").touch()

            if client.wait(timeout=35) != 0:
                raise AssertionError(f"Legacy SAG client failed; see {evidence}")
            summary = json.loads((evidence / "summary.json").read_text())
            loaded = summary["client"]["captures"]["copper-paused"]["loadedMods"]["enderio"]["file"]
            if digest(Path(loaded)) != report["jarSha256"]:
                raise AssertionError("Client and server loaded different Ender IO jars")
        finally:
            stop_client(client)
            summary = evidence / "summary.json"
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def verify_finished(server, report, carried):
    load(server, report["assertions"])
    cases = [("flint", FLINT_X, FLINT, "minecraft:flint"), ("copper", COPPER_X, COPPER, NEW_COPPER)]
    if carried:
        cases.append(("carried", PLACED_X, COPPER, NEW_COPPER))
    for name, x, modifiers, spare in cases:
        server.assert_state(f"restart_keeps_{name}_finished_wear_and_hopper_output",
                            finished_conditions(report, name, x, modifiers, spare), report["assertions"])
    verify_loose(server, report, "finished_restart")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", type=int, required=True)
    for path in ("java", "forge-server", "legacy-jar", "jar"):
        parser.add_argument(f"--{path}", type=Path, required=True)
    parser.add_argument("--launch", type=Path, help="Verify the real menu and survival placement with the guarded client")
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    directory.mkdir()
    source, port = directory / "legacy", directory / "port"
    prepare_profile(args, source, args.legacy_jar)
    prepare_profile(args, port, args.jar)
    report = {"passed": False, "assertions": [], "servers": [], "legacySha256": digest(args.legacy_jar), "jarSha256": digest(args.jar)}

    def resume(server, _):
        verify_original(server, report, "reopened")
        finish_saved(server, report, "flint", FLINT_X, FLINT, "minecraft:flint")
        if args.launch:
            run_client(args, server, report)
        else:
            finish_saved(server, report, "copper", COPPER_X, COPPER, NEW_COPPER)

    try:
        run_stage(args, source, report, "legacy", lambda server, _: legacy(server, report))
        shutil.copytree(source / WORLD, port / WORLD)
        run_stage(args, port, report, "converted", lambda server, _: verify_original(server, report, "converted"))
        run_stage(args, port, report, "reopened", resume)
        run_stage(args, port, report, "finished", lambda server, _: verify_finished(server, report, args.launch is not None))
        report["passed"] = True
    finally:
        results = directory / "legacy-sag-results.json"
        results.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Legacy SAG passed={report['passed']}, {len(report['assertions'])} assertions, {results}", flush=True)


if __name__ == "__main__":
    main()
