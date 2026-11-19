#!/usr/bin/env python3
"""Exercise the packaged Forge resetting levers and decorative pressure plates."""

import argparse
import json
from pathlib import Path
import sys

sys.dont_write_bytecode = True
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage


LEVER_TIERS = ("five", "ten", "thirty", "sixty", "three_hundred")
LEVERS = tuple(f"resetting_lever_{tier}{suffix}" for tier in LEVER_TIERS for suffix in ("", "_inv"))
PLATES = {
    "dark_steel_pressure_plate": 0,
    "silent_dark_steel_pressure_plate": 0,
    "soularium_pressure_plate": 0,
    "silent_soularium_pressure_plate": 0,
    **{f"silent_{material}_pressure_plate": 15 for material in (
        "oak", "acacia", "dark_oak", "spruce", "birch", "jungle", "crimson", "warped", "stone", "polished_blackstone")},
    "silent_heavy_weighted_pressure_plate": 2,
    "silent_light_weighted_pressure_plate": 12,
}
TRIGGER = "port_plate_trigger"
BARREL = "4033 80 12"


def load(server, results, stage):
    server.command("forceload add 4032 0 4095 15")
    server.assert_state(f"decorative_redstone_chunks_loaded_{stage}", "if loaded 4033 80 8 if loaded 4093 80 8", results)


def verify_levers(server, results, stage):
    for i, lever in enumerate(LEVERS):
        x = 4033 + 4 * i
        lit = str(lever.endswith("_inv")).lower()
        server.assert_state(f"{lever}_resting_output_{stage}",
                            f"if block {x} 80 4 enderio:{lever}[face=floor,powered=false] "
                            f"if block {x + 1} 80 4 minecraft:redstone_lamp[lit={lit}]", results)


def verify_plates(server, results, stage, power):
    for i, (plate, item_signal) in enumerate(PLATES.items()):
        x = 4033 + 4 * i
        strength = power(plate, item_signal)
        lit = str(strength > 0).lower()
        signal_state = f"power={strength}" if "weighted" in plate else f"powered={lit}"
        server.assert_state(f"{plate}_{stage}", f"if block {x} 80 8 enderio:{plate}[{signal_state}] "
                            f"if block {x + 1} 80 8 minecraft:redstone_lamp[lit={lit}]", results)


def occupied_power(plate, item_signal):
    return 15 if "soularium" in plate else item_signal


def exercise(server, results):
    load(server, results, "initial")
    server.command("difficulty normal")
    server.command("gamerule doMobSpawning false")
    server.command("gamerule doMobLoot false")
    server.command("gamerule doDaylightCycle false")
    server.command("time set midnight")
    server.command(f"setblock {BARREL} minecraft:barrel")
    for blocks, z in ((LEVERS, 4), (PLATES, 8)):
        for i, block in enumerate(blocks):
            x = 4033 + 4 * i
            server.command(f"setblock {x} 79 {z} minecraft:stone")
            server.command(f"setblock {x + 1} 80 {z} minecraft:redstone_lamp")
            state = "[face=floor,facing=north,powered=false]" if z == 4 else ""
            server.command(f"setblock {x} 80 {z} enderio:{block}{state}")
            server.command(f"loot replace block {BARREL} container.0 mine {x} 80 {z} minecraft:diamond_pickaxe")
            server.assert_state(f"{block}_native_loot",
                                f'if data block {BARREL} Items[{{Slot:0b,id:"enderio:{block}",Count:1b}}]', results)
    verify_levers(server, results, "initial")
    for i, signal in enumerate(PLATES.values()):
        x = 4033.5 + 4 * i
        for _ in range(12 if signal in (2, 12) else 1):
            server.command(f'summon minecraft:item {x} 80 8.5 {{Tags:["{TRIGGER}"],NoGravity:1b,'
                           'PickupDelay:32767s,Item:{id:"minecraft:cobblestone",Count:1b}}')
    verify_plates(server, results, "with_item_entities", lambda _, value: value)
    for i, plate in enumerate(PLATES):
        if "soularium" not in plate:
            continue
        x = 4033 + 4 * i
        for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            base = 81 if dx == 1 else 80  # Leave the east receiver lamp in place.
            server.command(f"fill {x + dx} {base} {8 + dz} {x + dx} 82 {8 + dz} minecraft:stone")
        server.command(f'summon minecraft:zombie {x + 0.5} 81.25 8.5 {{Tags:["{TRIGGER}"],'
                       'Silent:1b,PersistenceRequired:1b}')
    verify_plates(server, results, "with_persistent_triggers", occupied_power)


def reopen(server, results):
    load(server, results, "restart")
    verify_levers(server, results, "restart")
    verify_plates(server, results, "active_after_restart", occupied_power)
    server.command(f"kill @e[tag={TRIGGER}]")
    verify_plates(server, results, "released_after_restart", lambda _, __: 0)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")
    root = ROOT / "build/port-runtime" / args.name
    root.mkdir()
    directory = root / "server"
    report = {"passed": False, "jarSha256": digest(args.jar), "assertions": [], "servers": []}
    try:
        prepare_profile(args, directory, args.jar)
        run_stage(args, directory, report, "placed", exercise)
        run_stage(args, directory, report, "reopened", reopen)
        report["passed"] = True
    finally:
        (root / "decorative-redstone-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
