#!/usr/bin/env python3
"""Verify JEI VAT inputs, reversed reagent transfer, and packaged fermenting."""

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_legacy_painting import stack
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage
from packaged_starter_progression import PLAYER
from packaged_vat_automation import HOOTCH, NUTRIENT, WATER, drained, fluid, tank_fields


LAVA = "minecraft:lava"
ITEM_TAG = "{PortVat:225}"
FLUID_TAG = '{PortVat:"jei",Readings:[I;4,3,1]}'


@dataclass(frozen=True)
class Fixture:
    name: str
    x: int
    first: str
    second: str
    reversed: bool
    source: str
    amount: int
    cost: int
    result: str
    output_amount: int

    def pos(self, dx=0, z=8):
        return f"{self.x + dx} 80 {z}"

    def reagents(self):
        return (self.second, self.first) if self.reversed else (self.first, self.second)


STOCK = Fixture("stock", 4770, "minecraft:beef", "minecraft:wheat", False, WATER, 1000, 1000, NUTRIENT, 250)
AUTHORED = Fixture("reversed", 4778, "minecraft:copper_ingot", "minecraft:gold_ingot", True, LAVA, 537, 500, HOOTCH, 540)
FIXTURES = (STOCK, AUTHORED)


def write_pack(directory):
    root = directory / WORLD / "datapacks/port_vat_jei"

    def write(path, value):
        file = root / path
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(json.dumps(value) + "\n")

    write("pack.mcmeta", {"pack": {"pack_format": 15, "description": "VAT JEI fixture"}})
    write("data/port_vat/tags/items/copper.json", {"values": [AUTHORED.first]})
    write("data/port_vat/tags/items/gold.json", {"values": [AUTHORED.second]})
    write("data/port_vat/recipes/jei_batch.json", {
        "type": "enderio:vat_fermenting",
        "input": {"type": "endercore:compound", "ingredients": [{"fluid": WATER}, {"fluid": LAVA}], "amount": AUTHORED.cost},
        "left_reagent": "port_vat:copper", "right_reagent": "port_vat:gold", "ticks": 160,
        "output": {"id": HOOTCH, "amount": 480, "Tag": FLUID_TAG},
    })
    write("data/enderio/data_maps/item/vat_reagent.json", {"values": {
        AUTHORED.first: {"#port_vat:copper": 1.5}, AUTHORED.second: {"#port_vat:gold": 0.75},
    }})


def prepare(server, results, *, with_client):
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    server.command("forceload add 4768 0 4783 15")
    server.assert_state("both_vat_positions_are_loaded", " ".join(f"if loaded {f.pos()}" for f in FIXTURES), results)
    server.command("fill 4768 79 5 4782 79 14 minecraft:stone_bricks")
    server.command("setworldspawn 4770 80 11")
    for fixture in FIXTURES:
        amount = 999 if fixture is STOCK and with_client else fixture.amount
        initial = tank_fields(fluid(fixture.source, amount))
        inventory = f",Items:{{Items:[{stack(0, 1, fixture.second, metadata=ITEM_TAG)}]}}" if fixture.reversed else ""
        server.command(f'setblock {fixture.pos()} enderio:vat[facing=south]'
                       f'{{{initial},RedstoneControl:"active_with_signal"{inventory}}}')


def set_liquid(server, fixture, name, amount):
    server.command(f"data merge block {fixture.pos()} {{{tank_fields(fluid(name, amount))}}}")


def queued(fixture):
    reagents = ",".join(stack(i, 1, item, metadata=ITEM_TAG) for i, item in enumerate(fixture.reagents()))
    contents = tank_fields(fluid(fixture.source, fixture.amount))
    return (f"if data block {fixture.pos()} {{{contents},Items:{{Items:[{reagents}]}}}} "
            f"{drained(fixture.pos(), 1)}")


def finished(fixture):
    pos = fixture.pos()
    remainder = fixture.amount - fixture.cost
    input_state = f"if data block {pos} {{{tank_fields(fluid(fixture.source, remainder))}}}" if remainder else drained(pos)
    output_state = tank_fields(fluid(fixture.result, fixture.output_amount, FLUID_TAG if fixture.reversed else None), index=1)
    return (f"{input_state} if data block {pos} {{{output_state}}} "
            f"unless data block {pos} Items.Items[0] unless data block {pos} Task unless data block {pos} PendingOutput.FluidName")


def process(server, results, fixture):
    server.command(f"setblock {fixture.pos(z=7)} minecraft:redstone_block")
    server.assert_state(fixture.name + "_consumes_exact_inputs_and_publishes_one_result", finished(fixture), results, timeout=34)


def run_client(args, server, report):
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    summary_path = evidence / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "vat-jei", "--server", f"127.0.0.1:{args.port}", "--output", str(evidence), "--timeout", "220"]
    results = report["assertions"]

    def ready(fixture, phase):
        (evidence / f"server-{fixture.name}-{phase}").touch()

    def wait(fixture, phase):
        wait_signal(client, evidence, f"{fixture.name}-{phase}")

    with (server.directory / "vat-jei-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            for fixture in FIXTURES:
                server.command(f"tp {PLAYER} {fixture.x + 0.5} 80 10.5 180 15")
                ready(fixture, "initial")
                if fixture is STOCK:
                    ready(fixture, "short-and-empty")
                    wait(fixture, "short-and-empty")
                    server.command(f"item replace entity {PLAYER} hotbar.1 with {fixture.second}{ITEM_TAG} 2")
                    ready(fixture, "missing-left")
                    wait(fixture, "missing-left")
                    server.command(f"item replace entity {PLAYER} hotbar.2 with {fixture.first}{ITEM_TAG} 2")
                    set_liquid(server, fixture, LAVA, fixture.amount)
                    ready(fixture, "wrong-fluid")
                    wait(fixture, "wrong-fluid")
                    set_liquid(server, fixture, WATER, 999)
                    ready(fixture, "short-fluid")
                    wait(fixture, "short-fluid")
                    set_liquid(server, fixture, WATER, fixture.amount)
                    ready(fixture, "exact")
                else:
                    ready(fixture, "missing-complement")
                    wait(fixture, "missing-complement")
                    server.command(f"item replace entity {PLAYER} hotbar.2 with {fixture.first}{ITEM_TAG} 2")
                    ready(fixture, "complement")
                wait(fixture, "inserted")
                held = [stack(2, 1, fixture.first, metadata=ITEM_TAG, slot_suffix="b", count_suffix="b")]
                if fixture is STOCK:
                    held.append(stack(1, 1, fixture.second, metadata=ITEM_TAG, slot_suffix="b", count_suffix="b"))
                player = " ".join(f"if data entity {PLAYER} Inventory[{expected}]" for expected in held)
                server.assert_state(fixture.name + "_server_matches_the_exact_jei_placement_and_surplus",
                                    f"{queued(fixture)} {player}", results)
                for slot in (1, 2):
                    server.command(f"item replace entity {PLAYER} hotbar.{slot} with minecraft:air")
                ready(fixture, "loaded")
                wait(fixture, "loaded")
                server.assert_state(fixture.name + "_loaded_inputs_are_unchanged_without_player_copies", queued(fixture), results)
                process(server, results, fixture)
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"VAT JEI client failed; see {evidence}")
            mods = json.loads(summary_path.read_text())["client"]["captures"]["reversed-recipe"]["loadedMods"]
            if digest(Path(mods["enderio"]["file"])) != report["enderioSha256"] or digest(Path(mods["jei"]["file"])) != report["jeiClientSha256"]:
                raise AssertionError("VAT JEI client loaded unexpected jars")
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
    write_pack(directory)
    report = {"passed": False, "assertions": [], "servers": [], "enderioSha256": digest(args.jar),
              "jeiClientSha256": digest(args.jei) if args.jei else None}

    def fresh(server, results):
        prepare(server, results, with_client=bool(args.jei))
        if args.jei:
            run_client(args, server, report)
            return
        for fixture in FIXTURES:
            hoppers = ((1, "west", fixture.first),) if fixture.reversed else (
                (-1, "east", fixture.first), (1, "west", fixture.second))
            for dx, facing, item in hoppers:
                entry = stack(0, 1, item, metadata=ITEM_TAG, slot_suffix="b", count_suffix="b")
                server.command(f"setblock {fixture.pos(dx)} minecraft:hopper[facing={facing}]{{Items:[{entry}]}}")
            process(server, results, fixture)
            server.assert_state(fixture.name + "_hoppers_supply_the_missing_reagents",
                                " ".join(f"unless data block {fixture.pos(dx)} Items[0]" for dx, _, _ in hoppers), results)

    def reopened(server, results):
        for fixture in FIXTURES:
            server.assert_state(fixture.name + "_exact_fluid_and_result_survive_restart", finished(fixture), results)

    try:
        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", reopened)
        report["passed"] = True
    finally:
        path = directory / "vat-jei-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"VAT JEI passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
