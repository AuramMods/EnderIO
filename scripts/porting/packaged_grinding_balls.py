#!/usr/bin/env python3
"""Compare all nine stock SAG Mill grinding balls on a packaged Forge server."""

import argparse
from dataclasses import asdict, dataclass
import json
from pathlib import Path
import re
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_alloy_progression import CHEST, produce
from packaged_sag_progression import COAL, DUST, charge, data_read, feed, inputs, mill, output, output_count, paused, prepare_mill
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage
from packaged_starter_progression import PLAYER, hopper_item

PAID = 300


@dataclass(frozen=True)
class Ball:
    item: str
    x: int
    multiplier: float
    bonus: float
    power: float
    durability: int

    @property
    def cost(self):
        return round(2400 * self.power)

    @property
    def nbt(self):
        return (f"{{OutputMultiplier:{self.multiplier}f,BonusMultiplier:{self.bonus}f,"
                f"PowerUse:{self.power}f,Durability:{self.durability}}}")

    def stored(self, count):
        pos = mill(self.x)
        return f'if data block {pos} Items.Items[{{Slot:5,id:"{self.item}",Count:{count}}}]'

    def stock_tag(self, wear):
        return f'if data block {mill(self.x)} {{GrindingBal:{self.nbt},GrindingBallDamage:{wear}}}'


BALLS = (
    Ball("enderio:conductive_alloy_grinding_ball", 3240, 1.35, 1.0, 1.0, 40000),
    Ball("enderio:dark_steel_grinding_ball", 3244, 1.35, 2.0, 0.7, 125000),
    Ball("enderio:end_steel_grinding_ball", 3248, 1.4, 2.4, 0.7, 75000),
    Ball("enderio:energetic_alloy_grinding_ball", 3252, 1.6, 1.1, 1.1, 80000),
    Ball("enderio:pulsating_alloy_grinding_ball", 3256, 1.0, 1.85, 1.0, 100000),
    Ball("enderio:redstone_alloy_grinding_ball", 3260, 1.0, 1.0, 0.35, 30000),
    Ball("enderio:soularium_grinding_ball", 3264, 1.2, 2.15, 0.9, 80000),
    Ball("enderio:vibrant_alloy_grinding_ball", 3268, 1.75, 1.35, 1.13, 80000),
    Ball("minecraft:flint", 3272, 1.2, 1.25, 0.85, 24000),
)
DARK = BALLS[1]
FLINT = BALLS[-1]


def setup(server, results):
    server.command("forceload add 3232 0 3279 15")
    server.assert_state("all_nine_sag_mills_are_loaded",
                        " ".join(f"if loaded {mill(ball.x)}" for ball in BALLS), results)
    server.command("fill 3234 79 9 3277 79 14 minecraft:stone_bricks")
    for ball in BALLS:
        prepare_mill(server, ball.x)


def feed_ball(server, ball, count):
    server.command(f"item replace block {feed(ball.x)} container.1 with {ball.item} {count}")


def check_unmodified_stone(server, results, count):
    inputs(server, FLINT.x, "minecraft:stone", 1)
    charge(server, FLINT.x, 2400)
    pos = mill(FLINT.x)
    server.assert_state("stone_recipe_uses_base_fe_and_keeps_the_unclaimed_flint",
                        f"if data block {pos} {{EnergyStored:0}} unless data block {pos} GrindingBal "
                        f"unless data block {pos} Task {FLINT.stored(count)} "
                        f"{hopper_item(output(FLINT.x), 'minecraft:cobblestone', 1)}", results)
    server.command(f"item replace block {output(FLINT.x)} container.0 with minecraft:air")


def queued_roll(server, ball, hopper):
    x = ball.x
    payload = data_read(server, mill(x), "Task.Outputs")
    amount = int(data_read(server, mill(x), f'Task.Outputs[{{Item:{{id:"{DUST}"}}}}].Item.Count'))
    upper = 2 if ball.multiplier == 1 else 4
    if not 1 <= amount <= upper:
        raise AssertionError(f"{ball.item} rolled {amount} powder outside 1..{upper}")
    return {"hopper": hopper, "powder": amount, "nbt": payload}


def pause_all(server, results, report, stocked):
    check_unmodified_stone(server, results, stocked[FLINT.item])
    for ball in BALLS:
        inputs(server, ball.x, "minecraft:coal", 2)
        charge(server, ball.x, 2400 + PAID)
    for ball in BALLS:
        server.assert_state(f"stock_{ball.x}_sets_exact_cost_modifiers_and_wear",
                            f"{paused(ball.x, ball.cost, ball.nbt, PAID, paid=PAID)} {ball.stored(stocked[ball.item] - 1)} "
                            f'unless data block {mill(ball.x)} Task.Outputs[1] '
                            f'if data block {output(ball.x)} Items[{{Slot:0b,id:"{DUST}"}}]', results)
        base = output_count(server, ball.x)
        if not 1 <= base <= 2:
            raise AssertionError(f"{ball.item} changed its first unmodified coal roll to {base}")
        report["rolls"][ball.item] = queued_roll(server, ball, base)


def verify_reopened(server, results, report, stocked):
    for ball in BALLS:
        roll = report["rolls"][ball.item]
        server.assert_state(f"stock_{ball.x}_retains_exact_paused_state_after_restart",
                            f"{paused(ball.x, ball.cost, ball.nbt, PAID, paid=PAID)} {ball.stored(stocked[ball.item] - 1)} "
                            f"{hopper_item(output(ball.x), DUST, roll['hopper'])}", results)
        if data_read(server, mill(ball.x), "Task.Outputs") != roll["nbt"]:
            raise AssertionError(f"{ball.item} rerolled its saved output after restart")


def complete_paid_jobs(server, results, report, stocked):
    for ball in BALLS:
        charge(server, ball.x, ball.cost - PAID)
    for ball in BALLS:
        pos = mill(ball.x)
        roll = report["rolls"][ball.item]
        total = roll["hopper"] + roll["powder"]
        server.assert_state(f"stock_{ball.x}_spends_exactly_the_remaining_fe_and_exports_the_saved_roll",
                            f"if data block {pos} {{EnergyStored:0}} unless data block {pos} Task "
                            f"unless data block {pos} Items.Items[{{Slot:0}}] {ball.stock_tag(ball.cost)} "
                            f"{ball.stored(stocked[ball.item] - 1)} {hopper_item(output(ball.x), DUST, total)}", results)


def exhaust_and_replace(server, results, report, stocked):
    for ball in BALLS:
        pos = mill(ball.x)
        server.command(f"data merge block {pos} {{GrindingBallDamage:{ball.durability - 1}}}")
        inputs(server, ball.x, "minecraft:coal", 1)
        charge(server, ball.x, PAID)
    for ball in BALLS:
        pos = mill(ball.x)
        server.assert_state(f"stock_{ball.x}_expires_without_changing_the_paid_recipe",
                            f'if data block {pos} {{EnergyStored:0,Task:{{RecipeId:"{COAL}",ProgressMade:{PAID},'
                            f'ProgressRequired:{ball.cost}}}}} unless data block {pos} GrindingBal '
                            f"{ball.stored(stocked[ball.item] - 1)}", results)
        old = report["rolls"][ball.item]
        report["exhausted"][ball.item] = queued_roll(server, ball, old["hopper"] + old["powder"])
        charge(server, ball.x, ball.cost - PAID)
    for ball in BALLS:
        roll = report["exhausted"][ball.item]
        pos = mill(ball.x)
        server.assert_state(f"stock_{ball.x}_takes_one_fresh_replacement_only_after_finishing",
                            f"if data block {pos} {{EnergyStored:0}} unless data block {pos} Task "
                            f"{ball.stock_tag(0)} {ball.stored(stocked[ball.item] - 2)} "
                            f"{hopper_item(output(ball.x), DUST, roll['hopper'] + roll['powder'])}", results)


def ready_at(server, ball, evidence, marker):
    server.command(f"tp {PLAYER} {ball.x + 0.5} 80 11.5 180 18")
    (evidence / f"server-{marker}").touch()


def use_crafted_stock(server, results, client, evidence):
    server.command(f"tp {PLAYER} 3210.5 80 12.5 180 19")
    (evidence / "server-recipe").touch()
    wait_signal(client, evidence, "crafted")
    complete = " ".join(f'if data entity {PLAYER} Inventory[{{id:"{ball.item}",Count:24b}}]' for ball in BALLS[:-1])
    server.assert_state("survival_crafts_all_eight_stock_ball_recipes_from_original_smelted_alloys",
                        f"{complete} unless data block {CHEST} Items[0]", results)
    ready_at(server, DARK, evidence, "install")
    wait_signal(client, evidence, "installed")
    server.assert_state("native_menu_transfers_the_crafted_dark_steel_stack",
                        f"{DARK.stored(24)} unless data entity {PLAYER} Inventory[{{id:\"{DARK.item}\"}}]", results)
    for ball in BALLS[:-1]:
        if ball == DARK:
            continue
        selection = f'Inventory[{{id:"{ball.item}",Count:24b}}].Slot'
        reply = server.command(f"data get entity {PLAYER} {selection}")
        found = re.search(r"has the following entity data: (\d+)b", reply)
        if not found:
            raise AssertionError(reply)
        index = int(found[1])
        slot = f"hotbar.{index}" if index < 9 else f"inventory.{index - 9}"
        server.command(f"item replace block {feed(ball.x)} container.1 from entity {PLAYER} {slot}")
        server.command(f"item replace entity {PLAYER} {slot} with minecraft:air")
    feed_ball(server, FLINT, 3)
    no_copies = " ".join(f'unless data entity {PLAYER} Inventory[{{id:"{ball.item}"}}]' for ball in BALLS[:-1])
    native_stocks = " ".join(ball.stored(3 if ball == FLINT else 24) for ball in BALLS)
    server.assert_state("all_original_ball_stacks_reach_their_mills_without_copies", f"{native_stocks} {no_copies}", results)


def run_client(args, server, results, report, stocked, *, restart):
    phase = "verify" if restart else "exercise"
    evidence = ROOT / "build/port-evidence" / f"{args.name}-{phase}-client"
    summary = evidence / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "stock-grinding-balls", "--multiplayer-phase", phase, "--output", str(evidence),
               "--server", f"127.0.0.1:{args.port}", "--timeout", "200"]
    with (server.directory / f"grinding-client-{phase}.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            layout = evidence / "layout.tmp"
            layout.write_text(json.dumps([
                {**asdict(ball), "cost": ball.cost, "spares": stocked[ball.item] - 1} for ball in BALLS
            ]))
            layout.replace(evidence / "layout.json")
            if restart:
                for ball in BALLS:
                    ready_at(server, ball, evidence, f"inspect-{ball.x}")
                    wait_signal(client, evidence, f"inspected-{ball.x}")
            else:
                use_crafted_stock(server, results, client, evidence)
                pause_all(server, results, report, stocked)
            (evidence / "server-done").touch()
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"Grinding ball client failed; see {evidence}")
            captures = json.loads(summary.read_text())["client"]["captures"]
            for capture in captures.values():
                if digest(Path(capture["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                    raise AssertionError("Grinding ball client and server Ender IO jars differ")
        finally:
            stop_client(client)
            report["clients"][phase] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    for key in ("forge-server", "jar"):
        parser.add_argument(f"--{key}", type=Path, required=True)
    parser.add_argument("--launch", type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "assertions": [], "servers": [], "clients": {}, "rolls": {}, "exhausted": {},
              "jarSha256": digest(directory / "mods/enderio.jar")}
    stocked = {ball.item: 24 if args.launch and ball != FLINT else 3 for ball in BALLS}

    def fresh(server, results):
        if args.launch:
            produce(server, results)
        setup(server, results)
        if args.launch:
            run_client(args, server, results, report, stocked, restart=False)
        else:
            for ball in BALLS:
                feed_ball(server, ball, 3)
            server.assert_state("all_stock_ball_stacks_reach_the_mills",
                                " ".join(ball.stored(3) for ball in BALLS), results)
            pause_all(server, results, report, stocked)

    def reopen(server, results):
        verify_reopened(server, results, report, stocked)
        if args.launch:
            run_client(args, server, results, report, stocked, restart=True)
        complete_paid_jobs(server, results, report, stocked)
        exhaust_and_replace(server, results, report, stocked)

    try:
        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", reopen)
        report["passed"] = True
    finally:
        result = directory / "grinding-balls-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Stock grinding balls passed={report['passed']}, {result}", flush=True)


if __name__ == "__main__":
    main()
