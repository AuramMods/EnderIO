#!/usr/bin/env python3
"""Verify native JEI tank transfers against a Forge server without JEI."""

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_legacy_painting import stack
from packaged_nutritious_stick import liquid_at
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage
from packaged_starter_progression import PLAYER
from packaged_vat_automation import WATER, fluid, tank_fields


XP = "enderio:fluid_xp_juice_still"
TAG = "{PortTank:224}"


@dataclass(frozen=True)
class Station:
    name: str
    x: int
    tank: str
    input_slot: int
    ingredient: str
    result: str
    liquid: str
    amount: int
    delta: int

    def pos(self, y=80, z=8):
        return f"{self.x} {y} {z}"

    def initial_amount(self):
        return 999 if self.name == "standard-fill" else self.amount


STATIONS = (
    Station("standard-fill", 4738, "fluid_tank", 2, "minecraft:sponge", "minecraft:wet_sponge", WATER, 1137, -1000),
    Station("standard-empty", 4746, "fluid_tank", 0, "minecraft:wet_sponge", "minecraft:sponge", WATER, 137, 1000),
    Station("pressurized-fill", 4754, "pressurized_fluid_tank", 2, "minecraft:glass_bottle", "minecraft:experience_bottle", XP, 18137, -250),
    Station("pressurized-empty", 4762, "pressurized_fluid_tank", 0, "minecraft:experience_bottle", "minecraft:glass_bottle", XP, 18137, 250),
)


def inventory(station, occupied, item, tag=None):
    held = stack(occupied, 1, item, metadata=tag)
    others = " ".join(f"unless data block {station.pos()} Items.Items[{{Slot:{slot}}}]"
                      for slot in range(4) if slot != occupied)
    return f"if data block {station.pos()} Items.Items[{held}] {others}"


def queue(station):
    return (f"{liquid_at(station.pos(), station.liquid, station.amount)} "
            f"{inventory(station, station.input_slot, station.ingredient, TAG)}")


def finished(station):
    return (f"if block {station.pos()} enderio:{station.tank} "
            f"{liquid_at(station.pos(), station.liquid, station.amount + station.delta)} "
            f"{inventory(station, station.input_slot + 1, station.result)}")


def set_liquid(server, station, liquid, amount):
    server.command(f"data merge block {station.pos()} {{{tank_fields(fluid(liquid, amount))}}}")


def prepare(server, results, *, with_client):
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    server.command("forceload add 4736 0 4767 15")
    server.assert_state("both_fixture_chunks_are_loaded", " ".join(f"if loaded {s.pos()}" for s in STATIONS), results)
    server.command("fill 4736 79 5 4764 79 14 minecraft:stone_bricks")
    server.command("setworldspawn 4738 80 11")
    for station in STATIONS:
        amount = station.initial_amount() if with_client else station.amount
        initial = tank_fields(fluid(station.liquid, amount))
        server.command(f'setblock {station.pos()} enderio:{station.tank}[facing=south]'
                       f'{{{initial},RedstoneControl:"active_with_signal",IOConfig:{{Modes:{{up:"pull"}}}}}}')


def process(server, results, station):
    server.command(f"setblock {station.pos(z=7)} minecraft:redstone_block")
    server.assert_state(station.name + "_processes_one_stock_recipe_at_the_exact_fluid_balance",
                        finished(station), results)


def run_client(args, server, report):
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    summary_path = evidence / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "tank-jei", "--server", f"127.0.0.1:{args.port}", "--output", str(evidence), "--timeout", "220"]
    results = report["assertions"]
    with (server.directory / "tank-jei-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            for station in STATIONS:
                server.command(f"tp {PLAYER} {station.x + 0.5} 80 10.5 180 15")
                (evidence / f"server-{station.name}-initial").touch()
                wait_signal(client, evidence, f"{station.name}-missing")
                server.command(f"item replace entity {PLAYER} hotbar.1 with {station.ingredient}{TAG} 2")
                if station is STATIONS[0]:
                    set_liquid(server, station, "minecraft:lava", station.amount)
                    (evidence / f"server-{station.name}-wrong-fluid").touch()
                    wait_signal(client, evidence, f"{station.name}-wrong-fluid")
                    set_liquid(server, station, station.liquid, 999)
                    (evidence / f"server-{station.name}-short-fluid").touch()
                    wait_signal(client, evidence, f"{station.name}-short-fluid")
                    set_liquid(server, station, station.liquid, station.amount)
                (evidence / f"server-{station.name}-ready").touch()
                wait_signal(client, evidence, f"{station.name}-inserted")
                player_item = stack(1, 1, station.ingredient, metadata=TAG, slot_suffix="b", count_suffix="b")
                server.assert_state(station.name + "_server_receives_one_original_item_without_processing",
                                    f"{queue(station)} if data entity {PLAYER} Inventory[{player_item}]", results)
                server.command(f"item replace entity {PLAYER} hotbar.1 with minecraft:air")
                (evidence / f"server-{station.name}-loaded").touch()
                wait_signal(client, evidence, f"{station.name}-loaded")
                server.assert_state(station.name + "_preloaded_input_stays_intact_without_a_player_copy", queue(station), results)
                if station is STATIONS[0]:
                    wrong_item = "minecraft:white_concrete_powder"
                    occupied = stack(station.input_slot, 1, wrong_item)
                    server.command(f"data merge block {station.pos()} {{Items:{{Items:[{occupied}]}}}}")
                    server.command(f"item replace entity {PLAYER} hotbar.1 with {station.ingredient}{TAG}")
                    (evidence / f"server-{station.name}-occupied").touch()
                    wait_signal(client, evidence, f"{station.name}-occupied")
                    server.assert_state("occupied_slot_and_player_copy_are_untouched",
                                        f"{inventory(station, station.input_slot, wrong_item)} if data entity {PLAYER} Inventory[{player_item}]",
                                        results)
                    original = stack(station.input_slot, 1, station.ingredient, metadata=TAG)
                    server.command(f"data merge block {station.pos()} {{Items:{{Items:[{original}]}}}}")
                    server.command(f"item replace entity {PLAYER} hotbar.1 with minecraft:air")
                process(server, results, station)
            if client.wait(timeout=35) != 0:
                raise AssertionError(f"Tank JEI client failed; see {evidence}")
            loaded = json.loads(summary_path.read_text())["client"]["captures"]["standard-fill-recipe"]["loadedMods"]
            if digest(Path(loaded["enderio"]["file"])) != report["enderioSha256"] or digest(Path(loaded["jei"]["file"])) != report["jeiClientSha256"]:
                raise AssertionError("JEI client loaded unexpected jars")
        finally:
            stop_client(client)
            report["client"] = json.loads(summary_path.read_text()) if summary_path.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for path in ("java", "forge-server", "jar"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    parser.add_argument("--jei", type=Path)
    parser.add_argument("--launch", type=Path)
    args = parser.parse_args()
    if bool(args.jei) != bool(args.launch):
        parser.error("provide --jei and --launch together for the packaged client scenario")
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "assertions": [], "servers": [], "enderioSha256": digest(args.jar),
              "jeiClientSha256": digest(args.jei) if args.jei else None}

    def fresh(server, results):
        prepare(server, results, with_client=bool(args.jei))
        if args.jei:
            run_client(args, server, report)
        else:
            for station in STATIONS:
                original = stack(0, 1, station.ingredient, metadata=TAG, slot_suffix="b", count_suffix="b")
                server.command(f"setblock {station.pos(81)} minecraft:hopper[facing=down]{{Items:[{original}]}}")
                process(server, results, station)
                server.assert_state(station.name + "_native_hopper_supplied_the_original_input",
                                    f"unless data block {station.pos(81)} Items[0]", results)

    def reopened(server, results):
        for station in STATIONS:
            server.assert_state(station.name + "_preserves_exact_fluid_and_result_after_restart", finished(station), results)

    try:
        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", reopened)
        report["passed"] = True
    finally:
        path = directory / "tank-jei-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Tank JEI passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
