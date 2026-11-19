#!/usr/bin/env python3
"""Exercise eight stock repainting recipes and travel to the resulting anchor over TCP."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_painting_machine import CAPACITOR, COST, PLAYER, inventory, paint_and_extract, paint_tag, palette
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage


RECIPES = (
    ("fence", "oak_fence"),
    ("fence_gate", "spruce_fence_gate"),
    ("redstone_block", "redstone_block"),
    ("sand", "sand"),
    ("slab", "oak_slab"),
    ("trapdoor", "acacia_trapdoor"),
    ("wooden_pressure_plate", "birch_pressure_plate"),
    ("travel_anchor", "enderio:travel_anchor"),
)
PAID = 600
ORIGINAL, REPAINT = "gold_block", "diamond_block"
HUB, ANCHOR, BARREL = "2704 80 8", "2712 80 8", "2717 80 8"


def placed_anchor():
    return f'if block {ANCHOR} enderio:painted_travel_anchor if data block {ANCHOR} {{Paint:"minecraft:{REPAINT}"}}'


def painted_item(kind, paint):
    return f'id:"enderio:painted_{kind}",{paint_tag(paint)}'


def output_hopper(slot):
    return f"{2692 + slot * 4} 79 12"


def hopper_result(hopper, kind):
    return f"if data block {hopper} Items[{{Slot:0b,Count:1b,{painted_item(kind, REPAINT)}}}]"


def repaint(server, results, slot, kind, template):
    x = 2692 + slot * 4
    first_hopper = f"{x} 79 3"
    machine, feeder, receiver = f"{x} 80 12", f"{x} 81 12", output_hopper(slot)
    paint_and_extract(server, results, f"{x} 80 3", first_hopper, kind, template, ORIGINAL)
    server.command(f"setblock {machine} enderio:painting_machine[facing=south]")
    server.command(f'data merge block {machine} {{EnergyStored:{PAID},Items:{{Items:[{CAPACITOR},{palette(REPAINT)}]}}}}')
    server.command(f"setblock {feeder} minecraft:hopper[facing=down]")
    server.command(f"item replace block {feeder} container.0 from block {first_hopper} container.0")
    server.command(f"item replace block {first_hopper} container.0 with minecraft:air")
    task = f'{{RecipeId:"enderio:painting/painted_{kind}_frompainted",ProgressMade:{PAID},ProgressRequired:{COST}}}'
    original = f"{{Slot:1,Count:1,{painted_item(kind, ORIGINAL)}}}"
    server.assert_state(f"{kind}_starts_the_shipped_repainting_recipe_from_a_native_hopper",
                        f"{inventory(machine, [original, palette(REPAINT)], 0)} if data block {machine} {{Task:{task}}} "
                        f"unless data block {first_hopper} Items[0] unless data block {feeder} Items[0] "
                        f"unless data block {machine} Items.Items[{{Slot:3}}]", results)
    server.command(f"setblock {receiver} minecraft:hopper[facing=down]")
    server.command(f"data merge block {machine} {{EnergyStored:{COST - PAID}}}")
    server.assert_state(f"{kind}_repainting_spends_exact_charge_and_delivers_one_new_paint",
                        f"{hopper_result(receiver, kind)} {inventory(machine, [palette(REPAINT)], 0)} "
                        f"unless data block {machine} Task unless data block {machine} Items.Items[{{Slot:1}}] "
                        f"unless data block {machine} Items.Items[{{Slot:3}}]", results, timeout=20)


def load(server, results):
    server.command("forceload add 2688 0 2735 15")
    server.assert_state("painting_and_travel_chunks_loaded",
                        f"if loaded 2692 80 3 if loaded 2720 80 12 if loaded {ANCHOR}", results)


def setup(server, results):
    load(server, results)
    for command in ("gamerule doMobSpawning false", "gamerule doDaylightCycle false", "gamerule spawnRadius 0",
                    "time set noon", "fill 2690 79 4 2722 79 15 minecraft:stone_bricks",
                    "setworldspawn 2715 80 8", f"setblock {HUB} enderio:travel_anchor",
                    f"setblock {BARREL} minecraft:barrel"):
        server.command(command)
    for slot, (kind, template) in enumerate(RECIPES):
        repaint(server, results, slot, kind, template)


def run_client(args, server, results, report, phase):
    output = ROOT / "build/port-evidence" / f"{args.name}-{phase}"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "painted-travel", "--output", str(output), "--server", f"127.0.0.1:{args.port}",
               "--multiplayer-phase", phase, "--timeout", "170"]
    summary_path = output / "summary.json"
    with (server.directory / f"painted-travel-client-{phase}.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            if phase == "exercise":
                source = f"{output_hopper(len(RECIPES) - 1)} container.0"
                server.command(f"item replace entity {PLAYER} hotbar.0 from block {source}")
                server.command(f"item replace block {source} with minecraft:air")
            server.command(f"tp {PLAYER} 2715.5 80 8.5 90 18")
            (output / "server-anchor").touch()
            wait_signal(client, output, "menu-verified")
            server.assert_state(f"placed_repainted_anchor_has_only_spent_the_one_item_{phase}",
                                f"{placed_anchor()} unless data entity {PLAYER} Inventory[{{id:\"enderio:painted_travel_anchor\"}}]",
                                results)
            server.command(f"tp {PLAYER} 2704.5 81 8.5 -90 10")
            (output / "server-travel").touch()
            wait_signal(client, output, "arrived")
            server.assert_state(f"native_travel_arrives_on_the_diamond_anchor_{phase}",
                                f"positioned 2712.5 81 8.5 if entity @a[name={PLAYER},distance=..0.4] {placed_anchor()}",
                                results)
            if phase == "exercise":
                server.command(f"loot replace block {BARREL} container.0 mine {ANCHOR} minecraft:diamond_pickaxe")
            server.assert_state(f"native_anchor_loot_contains_the_diamond_paint_{phase}",
                                hopper_result(BARREL, "travel_anchor"), results)
            if phase == "verify":
                server.command(f"setblock {ANCHOR} minecraft:air")
            (output / "server-done").touch()
            if client.wait(timeout=35) != 0:
                raise AssertionError(f"Painted travel client failed; see {output}")
            summary = json.loads(summary_path.read_text())
            loaded = summary["client"]["captures"]["anchor-menu"]["loadedMods"]["enderio"]["file"]
            if digest(Path(loaded)) != report["jarSha256"]:
                raise AssertionError("Client and server loaded different Ender IO jars")
        finally:
            stop_client(client)
            report["clients"][phase] = json.loads(summary_path.read_text()) if summary_path.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for path in ("java", "forge-server", "jar", "launch"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "assertions": [], "servers": [], "clients": {}, "jarSha256": digest(args.jar)}

    def exercise(server, results):
        setup(server, results)
        run_client(args, server, results, report, "exercise")

    def verify(server, results):
        load(server, results)
        for slot, (kind, _) in enumerate(RECIPES[:-1]):
            server.assert_state(f"{kind}_repaint_output_survives_restart", hopper_result(output_hopper(slot), kind), results)
        server.assert_state("freshly_painted_anchor_and_loot_survive_restart",
                            f"{placed_anchor()} {hopper_result(BARREL, 'travel_anchor')}", results)
        run_client(args, server, results, report, "verify")

    try:
        run_stage(args, directory, report, "fresh", exercise)
        run_stage(args, directory, report, "reopened", verify)
        report["passed"] = True
    finally:
        (directory / "painted-travel-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
