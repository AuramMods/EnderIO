#!/usr/bin/env python3
"""Exercise real JEI transfer with overlapping VAT reagent tags and finite stocks."""

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
from packaged_vat_automation import HOOTCH, WATER, drained, fluid, tank_fields


COPPER = "minecraft:copper_ingot"
GOLD = "minecraft:gold_ingot"


@dataclass(frozen=True)
class Fixture:
    name: str
    x: int
    supplies: tuple
    left: str
    count: int
    left_variant: int
    right_variant: int
    remainder: tuple | None = None

    def pos(self, z=8):
        return f"{self.x} 80 {z}"


FIXTURES = (
    Fixture("choice", 4794, ((1, COPPER, 1, 1), (2, GOLD, 1, 3)), GOLD, 1, 3, 1),
    Fixture("shared", 4798, ((1, COPPER, 5, 1),), COPPER, 2, 1, 1, (1, COPPER, 1, 1)),
    Fixture("variants", 4802, ((1, COPPER, 3, 1), (2, COPPER, 3, 2)), COPPER, 3, 1, 2),
    Fixture("loaded", 4806, ((1, COPPER, 7, 1),), COPPER, 3, 2, 1, (1, COPPER, 4, 1)),
    Fixture("capacity", 4810, ((1, COPPER, 64, 1), (2, COPPER, 64, 1), (3, COPPER, 1, 1)), COPPER, 64, 1, 1, (3, COPPER, 1, 1)),
)


def tag(variant):
    return f"{{PortVariant:{variant}}}"


def write_pack(directory):
    root = directory / WORLD / "datapacks/port_vat_shared"

    def write(path, value):
        file = root / path
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(json.dumps(value) + "\n")

    write("pack.mcmeta", {"pack": {"pack_format": 15, "description": "VAT shared reagent fixture"}})
    write("data/port_vat/tags/items/broad.json", {"values": [COPPER, GOLD]})
    write("data/port_vat/tags/items/shared.json", {"values": [COPPER]})
    write("data/port_vat/recipes/shared_reagents.json", {
        "type": "enderio:vat_fermenting", "input": {"fluid": WATER, "amount": 100},
        "left_reagent": "port_vat:broad", "right_reagent": "port_vat:shared", "ticks": 12,
        "output": {"id": HOOTCH, "amount": 100},
    })


def prepare(server, results):
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    server.command("forceload add 4784 0 4815 15")
    server.assert_state("both_vat_chunks_are_loaded", " ".join(f"if loaded {f.pos()}" for f in FIXTURES), results)
    server.command("fill 4790 79 5 4815 79 14 minecraft:stone_bricks")
    server.command("setworldspawn 4794 80 11")
    for fixture in FIXTURES:
        intake = tank_fields(fluid(WATER, 937))
        inventory = ""
        if fixture.name == "loaded":
            inventory = f",Items:{{Items:[{stack(0, 3, COPPER, metadata=tag(2))}]}}"
        server.command(f'setblock {fixture.pos()} enderio:vat[facing=south]'
                       f'{{{intake},RedstoneControl:"active_with_signal"{inventory}}}')


def queued(fixture):
    left = stack(0, fixture.count, fixture.left, metadata=tag(fixture.left_variant))
    right = stack(1, fixture.count, COPPER, metadata=tag(fixture.right_variant))
    intake = tank_fields(fluid(WATER, 937))
    return f"if data block {fixture.pos()} {{{intake},Items:{{Items:[{left},{right}]}}}} {drained(fixture.pos(), 1)}"


def completed(fixture):
    if fixture.name == "capacity":
        return queued(fixture)
    spent = 100 * fixture.count
    left = tank_fields(fluid(WATER, 937 - spent))
    result = tank_fields(fluid(HOOTCH, spent), index=1)
    pos = fixture.pos()
    return f"if data block {pos} {{{left}}} if data block {pos} {{{result}}} unless data block {pos} Items.Items[0] unless data block {pos} Task"


def give(server, supply):
    slot, item, count, variant = supply
    server.command(f"item replace entity {PLAYER} hotbar.{slot} with {item}{tag(variant)} {count}")


def read_remaining(fixture):
    if fixture.remainder is None:
        return f'unless data entity {PLAYER} Inventory[{{id:"{COPPER}"}}]'
    slot, item, count, variant = fixture.remainder
    expected = stack(slot, count, item, metadata=tag(variant), slot_suffix="b", count_suffix="b")
    return f"if data entity {PLAYER} Inventory[{expected}]"


def client_session(args, server, report):
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    summary_path = evidence / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "vat-jei-shared", "--server", f"127.0.0.1:{args.port}", "--output", str(evidence), "--timeout", "220"]
    results = report["assertions"]
    with (server.directory / "vat-shared-jei-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            for fixture in FIXTURES:
                server.command(f"clear {PLAYER}")
                server.command(f"tp {PLAYER} {fixture.x + .5} 80 10.5 180 15")
                if fixture.name == "choice":
                    copper, gold = fixture.supplies
                    give(server, copper)
                    (evidence / "server-choice-initial").touch()
                    wait_signal(client, evidence, "choice-rejected")
                    original = stack(1, 1, COPPER, metadata=tag(1), slot_suffix="b", count_suffix="b")
                    server.assert_state("single_item_request_leaves_player_and_vat_intact",
                                        f"if data entity {PLAYER} Inventory[{original}] unless data block {fixture.pos()} Items.Items[0]", results)
                    give(server, gold)
                else:
                    for supply in fixture.supplies:
                        give(server, supply)
                    (evidence / f"server-{fixture.name}-initial").touch()
                (evidence / f"server-{fixture.name}-supplied").touch()
                wait_signal(client, evidence, f"{fixture.name}-queued")
                server.assert_state(f"{fixture.name}_queues_exact_reagents_and_keeps_the_remainder",
                                    f'{queued(fixture)} {read_remaining(fixture)} unless data entity {PLAYER} Inventory[{{id:"{GOLD}"}}]', results)
                (evidence / f"server-{fixture.name}-verified").touch()
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"Shared VAT JEI client failed; see {evidence}")
            mods = json.loads(summary_path.read_text())["client"]["captures"]["capacity-queued"]["loadedMods"]
            if digest(Path(mods["enderio"]["file"])) != report["enderioSha256"] or digest(Path(mods["jei"]["file"])) != report["jeiClientSha256"]:
                raise AssertionError("VAT JEI client loaded unexpected jars")
        finally:
            stop_client(client)
            report["client"] = json.loads(summary_path.read_text()) if summary_path.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for path in ("java", "forge-server", "jar", "jei", "launch"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    write_pack(directory)
    report = {"passed": False, "assertions": [], "servers": [], "enderioSha256": digest(args.jar),
              "jeiClientSha256": digest(args.jei)}

    def fresh(server, results):
        prepare(server, results)
        client_session(args, server, report)
        for fixture in FIXTURES[:-1]:
            server.command(f"setblock {fixture.pos(z=7)} minecraft:redstone_block")
        for fixture in FIXTURES:
            server.assert_state(f"{fixture.name}_conserves_the_complete_batches_or_saved_queue", completed(fixture), results)

    def reopened(server, results):
        for fixture in FIXTURES:
            server.assert_state(f"{fixture.name}_preserves_exact_inventory_and_fluid_after_restart", completed(fixture), results)

    try:
        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", reopened)
        report["passed"] = True
    finally:
        path = directory / "vat-shared-jei-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Shared VAT JEI passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
