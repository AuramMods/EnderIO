#!/usr/bin/env python3
"""Exercise stock SAG Mill automation, saved roll and grinding-ball durability."""

import argparse
import json
from pathlib import Path
import re
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage
from packaged_starter_progression import PLAYER, hopper_item, player_count


DUST = "enderio:powdered_coal"
COAL = "enderio:sag_milling/coal"
STOCK_X, AUTHORED_X = 3063, 3071
STOCK_BALL = '{OutputMultiplier:1.2f,BonusMultiplier:1.25f,PowerUse:0.85f,Durability:24000}'
AUTHORED_BALL = '{OutputMultiplier:2.0f,BonusMultiplier:10.0f,PowerUse:0.5f,Durability:2200}'
PAID = 600


def mill(x):
    return f"{x} 80 8"


def feed(x):
    return f"{x} 81 8"


def output(x):
    return f"{x} 79 8"


def write_data_map(directory):
    pack = directory / WORLD / "datapacks/port_sag_ball"
    data_map = pack / "data/enderio/data_maps/item/grinding_ball.json"
    data_map.parent.mkdir(parents=True)
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 15, "description": "SAG Mill ball fixture"}}) + "\n")
    data_map.write_text(json.dumps({"values": {"minecraft:feather": {
        "OutputMultiplier": 2.0, "BonusMultiplier": 10.0, "PowerUse": 0.5, "Durability": 2200,
    }}}) + "\n")


def data_read(server, pos, path):
    reply = server.command(f"data get block {pos} {path}")
    result = re.search(r"has the following block data: (.+)$", reply, re.MULTILINE)
    if not result:
        raise AssertionError(reply)
    return result[1]


def output_count(server, x):
    return int(data_read(server, output(x), "Items[{Slot:0b}].Count").removesuffix("b"))


def charge(server, x, fe):
    server.command(f"data merge block {mill(x)} {{EnergyStored:{fe}}}")


def inputs(server, x, item, count):
    server.command(f"item replace block {feed(x)} container.0 with {item} {count}")


def load(server, results):
    server.command("forceload add 3056 0 3087 15")
    server.assert_state("both_sag_mills_and_workbench_are_loaded", f"if loaded {mill(STOCK_X)} if loaded 3074 80 8", results)


def setup(server, results, x, item, balls, coal):
    server.command(f'setblock {mill(x)} enderio:sag_mill[facing=south]{{IOConfig:{{Modes:{{up:"pull",down:"push"}}}},'
                   'Items:{Items:[{Slot:6,id:"enderio:basic_capacitor",Count:1}]}}')
    server.command(f"setblock {feed(x)} minecraft:hopper[facing=down]")
    server.command(f"setblock {output(x)} minecraft:hopper[facing=down]")
    inputs(server, x, "minecraft:coal", coal)
    server.command(f"item replace block {feed(x)} container.1 with {item} {balls}")
    server.assert_state(f"native_hopper_inserts_coal_and_grinding_ball_{x}",
                        f'if data block {mill(x)} {{Items:{{Items:[{{Slot:0,id:"minecraft:coal",Count:{coal}}},'
                        f'{{Slot:5,id:"{item}",Count:{balls}}}]}}}} unless data block {feed(x)} Items[0]', results)


def paused(x, cost, ball, wear):
    return (f'if block {mill(x)} enderio:sag_mill if data block {mill(x)} {{EnergyStored:0,GrindingBal:{ball},'
            f'GrindingBallDamage:{wear},Items:{{Items:[{{Slot:0,id:"minecraft:coal",Count:1}}]}},'
            f'Task:{{RecipeId:"{COAL}",ProgressMade:{PAID},ProgressRequired:{cost},HasDeterminedOutputs:1b}}}}')


def archived_roll(server, report, name, x, min_count, max_count):
    hopper = output_count(server, x)
    roll = int(data_read(server, mill(x), f'Task.Outputs[{{Item:{{id:"{DUST}"}}}}].Item.Count'))
    if not min_count <= roll <= max_count:
        raise AssertionError(f"{name} rolled {roll} powder outside {min_count}..{max_count}")
    report[name] = {"hopperBefore": hopper, "queuedPowder": roll,
                    "savedOutputs": data_read(server, mill(x), "Task.Outputs")}


def fresh(server, report):
    results = report["assertions"]
    load(server, results)
    for command in ("gamerule doMobSpawning false", "gamerule doDaylightCycle false", "gamerule spawnRadius 0",
                    "time set noon", "fill 3059 79 5 3077 79 15 minecraft:stone_bricks",
                    "setblock 3067 80 8 minecraft:crafting_table", "setworldspawn 3067 80 11"):
        server.command(command)
    setup(server, results, STOCK_X, "minecraft:flint", 2, 2)
    setup(server, results, AUTHORED_X, "minecraft:feather", 1, 3)
    charge(server, STOCK_X, 3000)
    charge(server, AUTHORED_X, 4200)
    server.assert_state("stock_flint_pays_2040_fe_and_retains_one_spare_and_partial_progress",
                        f'{paused(STOCK_X, 2040, STOCK_BALL, 600)} '
                        f'if data block {mill(STOCK_X)} Items.Items[{{Slot:5,id:"minecraft:flint",Count:1}}] '
                        f'if data block {output(STOCK_X)} Items[{{Slot:0b,id:"{DUST}"}}]', results, timeout=30)
    server.assert_state("authored_ball_pays_1200_fe_and_saves_its_first_and_partial_second_grind",
                        f'{paused(AUTHORED_X, 1200, AUTHORED_BALL, 1800)} '
                        f'unless data block {mill(AUTHORED_X)} Items.Items[{{Slot:5}}] '
                        f'if data block {output(AUTHORED_X)} Items[{{Slot:0b,id:"{DUST}"}}]', results, timeout=30)
    archived_roll(server, report, "stock", STOCK_X, 1, 4)
    archived_roll(server, report, "authored", AUTHORED_X, 4, 4)
    server.assert_state("the_four_identical_bonus_items_save_as_one_stack_without_empty_outputs",
                        f'unless data block {mill(AUTHORED_X)} Task.Outputs[1]', results)
    if report["stock"]["hopperBefore"] not in (1, 2) or report["authored"]["hopperBefore"] not in (5, 6):
        raise AssertionError("The output hoppers did not receive the baseline and multiplied powder yields")


def compare_saved_rolls(server, report):
    results = report["assertions"]
    load(server, results)
    for name, x, cost, ball, damage in (("stock", STOCK_X, 2040, STOCK_BALL, 600),
                                        ("authored", AUTHORED_X, 1200, AUTHORED_BALL, 1800)):
        old = report[name]
        server.assert_state(f"{name}_ball_damage_progress_and_hopper_contents_survive_restart",
                            f'{paused(x, cost, ball, damage)} {hopper_item(output(x), DUST, old["hopperBefore"])}', results)
        saved = data_read(server, mill(x), "Task.Outputs")
        if saved != old["savedOutputs"]:
            raise AssertionError(f"The {name} grinding task rerolled on restart: {old['savedOutputs']} to {saved}")


def finish_coal(server, report, name, x, charge_remaining, ball_conditions):
    charge(server, x, charge_remaining)
    old = report[name]
    expected = old["hopperBefore"] + old["queuedPowder"]
    server.assert_state(f"{name}_grind_uses_only_its_remaining_payment_and_extracts_the_saved_roll",
                        f'if data block {mill(x)} {{EnergyStored:0}} {hopper_item(output(x), DUST, expected)} '
                        f'unless data block {mill(x)} Task unless data block {mill(x)} Items.Items[{{Slot:0}}] '
                        f'{ball_conditions}', report["assertions"], timeout=15)


def finish_stock(server, report):
    finish_coal(server, report, "stock", STOCK_X, 1440,
                f'if data block {mill(STOCK_X)} {{GrindingBal:{STOCK_BALL},GrindingBallDamage:2040,Items:{{Items:['
                '{Slot:5,id:"minecraft:flint",Count:1}]}}')


def finish_authored(server, report):
    finish_coal(server, report, "authored", AUTHORED_X, 600, f"unless data block {mill(AUTHORED_X)} GrindingBal")
    inputs(server, AUTHORED_X, "minecraft:coal", 1)
    charge(server, AUTHORED_X, 600)
    server.assert_state("after_ball_breakage_the_next_coal_requires_the_full_2400_fe",
                        f'if data block {mill(AUTHORED_X)} {{EnergyStored:0,Task:{{RecipeId:"{COAL}",ProgressMade:600,'
                        f'ProgressRequired:2400}}}} unless data block {mill(AUTHORED_X)} GrindingBal', report["assertions"])
    report["fallback"] = {"hopperBefore": output_count(server, AUTHORED_X), "queuedPowder":
                          int(data_read(server, mill(AUTHORED_X), f'Task.Outputs[{{Item:{{id:"{DUST}"}}}}].Item.Count'))}
    if report["fallback"]["queuedPowder"] not in (1, 2):
        raise AssertionError("The expired ball still affects the subsequent roll")
    finish_coal(server, report, "fallback", AUTHORED_X, 1800, f"unless data block {mill(AUTHORED_X)} GrindingBal")


def ready_at(server, x, label, evidence):
    server.command(f"tp {PLAYER} {x + 0.5} 80 11.5 180 18")
    (evidence / f"server-{label}").touch()


def run_client(args, server, report):
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "sag-progression", "--server", f"127.0.0.1:{args.port}", "--output", str(evidence),
               "--timeout", "170"]
    with (server.directory / "sag-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            ready_at(server, STOCK_X, "stock", evidence)
            wait_signal(client, evidence, "stock-paused")
            finish_stock(server, report)
            (evidence / "server-stock-finished").touch()
            wait_signal(client, evidence, "stock-finished")
            ready_at(server, AUTHORED_X, "authored", evidence)
            wait_signal(client, evidence, "authored-paused")
            finish_authored(server, report)
            (evidence / "server-authored-finished").touch()
            wait_signal(client, evidence, "authored-finished")

            if player_count(server, DUST):
                raise AssertionError("The player's powder must come from the stock SAG Mill hopper")
            old = output_count(server, STOCK_X)
            server.command(f"item replace entity {PLAYER} inventory.0 from block {output(STOCK_X)} container.0")
            server.command(f"item replace block {output(STOCK_X)} container.0 with minecraft:air")
            server.command(f"item replace entity {PLAYER} inventory.1 with enderio:basic_capacitor 2")
            server.command(f"item replace entity {PLAYER} inventory.2 with enderio:energetic_alloy_ingot 2")
            server.command(f"recipe give {PLAYER} enderio:double_layer_capacitor")
            ready_at(server, 3067, "table", evidence)
            wait_signal(client, evidence, "crafted")
            server.assert_state("the_capacitor_recipe_consumes_one_native_powder_and_the_two_upgrades",
                                f'if data entity {PLAYER} {{Inventory:[{{id:"enderio:double_layer_capacitor",Count:1b}},'
                                f'{{id:"{DUST}",Count:{old - 1}b}}]}} unless data block {output(STOCK_X)} Items[0] '
                                f'unless data entity {PLAYER} Inventory[{{id:"enderio:basic_capacitor"}}] '
                                f'unless data entity {PLAYER} Inventory[{{id:"enderio:energetic_alloy_ingot"}}]', report["assertions"])
            (evidence / "server-complete").touch()
            if client.wait(timeout=25) != 0:
                raise AssertionError(f"SAG progression client failed; see {evidence}")
            summary = json.loads((evidence / "summary.json").read_text())
            loaded = summary["client"]["captures"]["stock-flint-paused"]["loadedMods"]["enderio"]["file"]
            if digest(Path(loaded)) != report["jarSha256"]:
                raise AssertionError("Client and server loaded different Ender IO jars")
        finally:
            stop_client(client)
            summary = evidence / "summary.json"
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for path in ("java", "forge-server", "jar"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    parser.add_argument("--launch", type=Path, help="Use the quiet packaged client to inspect and craft on restart")
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    write_data_map(directory)
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(args.jar)}

    def resume(server, _):
        compare_saved_rolls(server, report)
        if args.launch:
            run_client(args, server, report)
        else:
            finish_stock(server, report)
            finish_authored(server, report)

    try:
        run_stage(args, directory, report, "fresh", lambda server, _: fresh(server, report))
        run_stage(args, directory, report, "reopened", resume)
        report["passed"] = True
    finally:
        (directory / "sag-progression-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
