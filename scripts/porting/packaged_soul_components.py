#!/usr/bin/env python3
"""Bind the original stock slicer outputs to live captured souls on packaged Forge."""

import argparse
from collections import Counter
from dataclasses import dataclass
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_glass_progression import transfer
from packaged_sag_progression import output
from packaged_server_smoke import ROOT, digest
from packaged_slicer import CASES, finished
from packaged_soul_binder import VIAL, item, xp_tank
from packaged_spawners import WORLD, prepare_profile, run_stage
from packaged_starter_progression import PLAYER
from saved_world import read_data_file


@dataclass(frozen=True)
class Binding:
    slicer: tuple
    mob: str
    result: str

    @property
    def x(self):
        return self.slicer[0]

    @property
    def ingredient(self):
        return f"enderio:{self.slicer[1]}"

    @property
    def label(self):
        return f"port_soul_components_{self.mob}"

    @property
    def payload(self):
        return (f'tag:{{"enderio:data":{{"enderio:soul":{{entity_type:"minecraft:{self.mob}",'
                f'entity_tag:{{Health:7.0f,Tags:["{self.label}"]}}}}}}}}')

    def pos(self, y, z=8, dx=0):
        return f"{self.x + dx} {y} {z}"


BINDINGS = (Binding(CASES[0], "zombie", "frank_n_zombie"), Binding(CASES[4], "witch", "sentient_ender"))
CAP = "enderio:octadic_capacitor"
XP_COST, XP_EXTRA = 800, 37
COST, PAID, FE_EXTRA = 51200, 12300, 317


def capture(server, results, binding):
    dispenser = binding.pos(80, 5, -2)
    power = binding.pos(80, 4, -2)
    server.command(f'setblock {dispenser} minecraft:dispenser[facing=east]{{Items:[{item(0, VIAL, vanilla=True)}]}}')
    server.command(f'summon minecraft:{binding.mob} {binding.x - 0.5} 80 5.5 '
                   f'{{Health:7.0f,NoAI:1b,Silent:1b,PersistenceRequired:1b,Tags:["{binding.label}"]}}')
    server.assert_state(f"{binding.mob}_stands_in_the_dispenser_capture_space",
                        f"if entity @e[tag={binding.label},type=minecraft:{binding.mob}]", results)
    server.command(f"setblock {power} minecraft:redstone_block")
    server.assert_state(f"{binding.mob}_is_captured_by_the_stock_soul_vial_dispenser",
                        f"unless entity @e[tag={binding.label}] "
                        f"if data block {dispenser} Items[{item(0, VIAL, binding.payload, vanilla=True)}]", results)
    server.command(f"setblock {power} minecraft:air")
    transfer(server, dispenser, 0, binding.pos(78, 8, -1), 0)


def filled_inputs(binding, energy, fluid):
    return (f'if data block {binding.pos(78)} {{EnergyStored:{energy},{xp_tank(fluid)},'
            f'Items:{{Items:[{item(0, VIAL, binding.payload)},{item(1, binding.ingredient)},{item(4, CAP)}]}}}} '
            f"unless data block {output(binding.x)} Items[0] "
            f"unless data block {binding.pos(78, 8, -1)} Items[0]")


def pending(binding):
    pos = binding.pos(78)
    products = f'{{Item:{item(None, "enderio:" + binding.result)}}},{{Item:{item(None, VIAL)}}}'
    return (f"{filled_inputs(binding, 0, XP_COST + XP_EXTRA)} "
            f'if data block {pos} {{Task:{{RecipeId:"enderio:soulbinding/{binding.result}",ProgressMade:{PAID},'
            f'ProgressRequired:{COST},HasDeterminedOutputs:1b,Outputs:[{products}]}}}} '
            f"unless data block {pos} Items.Items[{{Slot:2}}] unless data block {pos} Items.Items[{{Slot:3}}]")


def prepare_binding(server, results, binding):
    pos = binding.pos(78)
    tank = binding.pos(78, 7)
    vial_hopper = binding.pos(78, 8, -1)
    # The slicer's existing output hopper already points down into this position.
    server.command(f'setblock {pos} enderio:soul_binder[facing=south]'
                   f'{{EnergyStored:{PAID},Items:{{Items:[{item(4, CAP)}]}},IOConfig:{{Modes:{{down:"push"}}}}}}')
    server.command(f"setblock {vial_hopper} minecraft:hopper[facing=east]")
    server.command(f'setblock {tank} enderio:fluid_tank[facing=south]'
                   f'{{{xp_tank(XP_COST - 1)},IOConfig:{{Modes:{{south:"push"}}}}}}')
    capture(server, results, binding)
    server.assert_state(f"{binding.result}_keeps_the_original_part_vial_and_fe_at_one_mb_below_cost",
                        f"{filled_inputs(binding, PAID, XP_COST - 1)} "
                        f"unless data block {tank} Fluids.Tanks[0].Contents.Amount unless data block {pos} Task", results)
    server.command(f"data merge block {tank} {{{xp_tank(XP_EXTRA + 1)}}}")
    server.assert_state(f"{binding.result}_saves_the_paid_job_and_queued_outputs",
                        pending(binding), results, timeout=23)


def prepare_area(server, results):
    server.command("forceload add 3152 0 3199 15")
    server.command("time set midnight")
    for binding in BINDINGS:
        server.assert_state(f"{binding.slicer[1]}_is_the_original_finished_part_from_the_silicon_world",
                            finished(binding.slicer), results)
        server.command(f"fill {binding.pos(77, 9, -2)} {binding.pos(77, 13, 2)} minecraft:stone")
        server.command(f"fill {binding.pos(78, 9, -2)} {binding.pos(79, 13, 2)} minecraft:air")
        prepare_binding(server, results, binding)


def spent(binding):
    pos = binding.pos(78)
    return (f"if data block {pos} {{EnergyStored:{FE_EXTRA},{xp_tank(XP_EXTRA)}}} "
            f"unless data block {pos} Task unless data block {pos} Items.Items[{{Slot:0}}] "
            f"unless data block {pos} Items.Items[{{Slot:1}}] unless data block {output(binding.x)} Items[0]")


def complete(server, results, binding):
    pos = binding.pos(78)
    server.command(f"data merge block {pos} {{EnergyStored:{COST - PAID + FE_EXTRA}}}")
    product = item(2, "enderio:" + binding.result)
    empty = item(3, VIAL)
    server.assert_state(f"{binding.result}_spends_exact_energy_and_xp_and_returns_an_empty_vial",
                        f"{spent(binding)} if data block {pos} {{Items:{{Items:[{product},{empty}]}}}} "
                        f'unless data block {pos} Items.Items[{{Slot:3}}].tag."enderio:data"."enderio:soul"',
                        results, timeout=40)


def extracted(binding):
    pos = binding.pos(78)
    return (f"{spent(binding)} unless data block {pos} Items.Items[{{Slot:2}}] "
            f"unless data block {pos} Items.Items[{{Slot:3}}]")


def hopper_extracted(binding):
    hopper = binding.pos(77)
    product = item(None, "enderio:" + binding.result, vanilla=True)
    vial = item(None, VIAL, vanilla=True)
    return (f"{extracted(binding)} if data block {hopper} {{Items:[{product},{vial}]}} "
            f'unless data block {hopper} Items[{{id:"{VIAL}"}}].tag."enderio:data"."enderio:soul"')


def run_client(args, server, report):
    results = report["assertions"]
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    summary = evidence / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "soul-components", "--server", f"127.0.0.1:{args.port}", "--output", str(evidence), "--timeout", "195"]
    with (server.directory / "soul-components-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            for index, binding in enumerate(BINDINGS, 1):
                server.command(f"tp {PLAYER} {binding.x + 0.5} 78 11.5 180 18")
                (evidence / f"server-open-{binding.result}").touch()
                wait_signal(client, evidence, f"ready-{binding.result}")
                complete(server, results, binding)
                (evidence / f"server-complete-{binding.result}").touch()
                wait_signal(client, evidence, f"extracted-{binding.result}")
                product = item(None, "enderio:" + binding.result, vanilla=True)
                server.assert_state(f"{binding.result}_menu_transfers_the_exact_product_and_returned_vial",
                                    f"{extracted(binding)} if data entity {PLAYER} Inventory[{product}] "
                                    f'if data entity {PLAYER} Inventory[{{id:"{VIAL}",Count:{index}b}}] '
                                    f'unless data entity {PLAYER} Inventory[{{id:"{VIAL}"}}].tag."enderio:data"."enderio:soul"', results)
            (evidence / "server-finished").touch()
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"Soul Binder client failed; see {evidence}")
            for capture in json.loads(summary.read_text())["client"]["captures"].values():
                if digest(Path(capture["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                    raise AssertionError("Soul Binder client and server jars differ")
        finally:
            stop_client(client)
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def check_player(directory, report):
    player, = (directory / WORLD / "playerdata").glob("*.dat")
    inventory = Counter()
    for stack in read_data_file(player)["Inventory"]:
        inventory[stack["id"]] += stack["Count"]
        if stack["id"] == VIAL and stack.get("tag", {}).get("enderio:data", {}).get("enderio:soul"):
            raise AssertionError("A returned vial is filled in the saved player inventory")
    expected = Counter({VIAL: len(BINDINGS), **{"enderio:" + binding.result: 1 for binding in BINDINGS}})
    if inventory != expected:
        raise AssertionError(f"Saved survival inventory {inventory} differs from {expected}")
    report["savedInventory"] = dict(inventory)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    for key in ("forge-server", "jar", "source-world"):
        parser.add_argument(f"--{key}", type=Path, required=True)
    parser.add_argument("--launch", type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    shutil.copytree(args.source_world, directory / WORLD)
    report = {"passed": False, "assertions": [], "servers": [], "sourceWorld": str(args.source_world.resolve()),
              "jarSha256": digest(directory / "mods/enderio.jar")}

    def resume(server, results):
        for binding in BINDINGS:
            server.assert_state(f"{binding.result}_reopens_with_the_same_part_soul_xp_and_paid_fe", pending(binding), results)
        if args.launch:
            run_client(args, server, report)
        else:
            for binding in BINDINGS:
                complete(server, results, binding)
                server.command(f"setblock {binding.pos(77)} minecraft:hopper[facing=down]")
                server.assert_state(f"{binding.result}_hopper_exports_exactly_the_two_outputs", hopper_extracted(binding), results)

    def reopen(server, results):
        state = extracted if args.launch else hopper_extracted
        for binding in BINDINGS:
            server.assert_state(f"{binding.result}_keeps_the_exact_resources_and_extracted_state_after_restart", state(binding), results)

    try:
        run_stage(args, directory, report, "fresh", prepare_area)
        run_stage(args, directory, report, "resume", resume)
        run_stage(args, directory, report, "reopened", reopen)
        if args.launch:
            check_player(directory, report)
        report["passed"] = True
    finally:
        result = directory / "soul-components-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Soul components passed={report['passed']}, {len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
