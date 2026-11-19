#!/usr/bin/env python3
"""Check the opt-in Niard on a packaged Forge server, including native world reload."""

import argparse
import json
from pathlib import Path
import re
import sys
import time

sys.dont_write_bytecode = True
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_spawners import prepare_profile, run_stage


PACK = "mod/enderio:data/enderio/datapacks/niard"
ENDERFACE_PACK = "mod/enderio:data/enderio/datapacks/enderface"
FARM_PACK = "mod/enderio:data/enderio/datapacks/farming_station"
CENTER = "2920 80 8"
CENTER_TARGET = "2920 79 8"
LOW = "2940 80 8"
LOW_TARGET = "2940 79 8"
RANGE = "2960 80 8"
RANGE_TARGET = "2961 79 6"
WORLD_BOTTOM = "2980 -64 8"
REDSTONE = "3000 80 8"
REDSTONE_TARGET = "3000 79 8"
SOURCE = "minecraft:water[level=0]"
CAPACITOR = '{Slot:0,id:"enderio:basic_capacitor",Count:1}'


def packs(server, results, enabled):
    selected = server.command("datapack list enabled")
    expected = ((PACK, "enderio:niard", enabled), (ENDERFACE_PACK, "enderio:enderface", False),
                (FARM_PACK, "enderio:farming_station", False))
    for pack, flag, active in expected:
        token = f"[{pack} (feature)]"
        response = selected if active else server.command(f'datapack enable "{pack}"')
        missing_flag = f"Pack '{pack}' cannot be enabled, since required flags are not enabled in this world: {flag}!"
        passed = (token in selected) == active and (active or missing_flag in response)
        results.append({"assertion": f"{pack}_active_{active}", "passed": passed,
                        "output": response.strip()})
        if not passed:
            raise AssertionError(f"Unexpected enabled state for {pack}; see {server.log_path}")


def game_tick(server):
    reply = server.command("time query gametime")
    match = re.search(r"The time is (\d+)", reply)
    if not match:
        raise RuntimeError(f"Unable to read actual game time; see {server.log_path}")
    return int(match[1])


def await_ticks(server, ticks):
    target = game_tick(server) + ticks
    deadline = time.monotonic() + 40
    while game_tick(server) < target:
        if time.monotonic() >= deadline:
            raise RuntimeError(f"World did not tick to {target}; see {server.log_path}")
        time.sleep(0.4)


def water(amount):
    return f'{{FluidName:"minecraft:water",Amount:{amount}}}' if amount > 0 else "{}"


def data(energy, amount, radius, *, visible=False, redstone="always_active"):
    visibility = int(visible)
    return (f'{{EnergyStored:{energy},RedstoneControl:"{redstone}",ActionRange:{{range:{radius},isVisible:{visibility}b}},'
            f'Items:{{Items:[{CAPACITOR}]}},Fluids:{{Tanks:[{{Index:0,Contents:{water(amount)}}}]}}}}')


def machine(pos, energy, amount, radius, **settings):
    state = f"if block {pos} enderio:niard if data block {pos} {data(energy, amount, radius, **settings)}"
    if amount == 0:
        state += f" unless data block {pos} Fluids.Tanks[0].Contents.FluidName"
    return state


def seed(server, pos, energy, amount, radius, **settings):
    server.command(f"setblock {pos} enderio:niard[facing=south]")
    server.command(f"data merge block {pos} {data(energy, amount, radius, **settings)}")


def banked_and_blocked(server, results):
    conditions = (
        ("underfunded_niard_keeps_127_fe_and_1300_mb",
         f"{machine(LOW, 127, 1300, 1)} if block {LOW_TARGET} minecraft:air"),
        ("saved_radius_1_excludes_the_only_open_cell_at_radius_2",
         f"{machine(RANGE, 3100, 1250, 1, visible=True)} if block {RANGE_TARGET} minecraft:air"),
        ("rejected_world_floor_placement_preserves_fluid_and_charge",
         machine(WORLD_BOTTOM, 2500, 1500, 0)),
        ("unpowered_redstone_control_prevents_a_valid_placement",
         f'{machine(REDSTONE, 2000, 1000, 0, redstone="active_with_signal")} if block {REDSTONE_TARGET} minecraft:air'),
    )
    for name, condition in conditions:
        server.assert_state(name, condition, results)


def observe_center(server, results):
    server.assert_state("hopper_bucket_and_radius_zero_place_one_source_for_1500_fe",
                        f"{machine(CENTER, 2000, 1200, 0)} if block {CENTER_TARGET} {SOURCE} "
                        f'if data block {CENTER} Items.Items[{{Slot:2,id:"minecraft:bucket",Count:1}}] '
                        f"unless data block {CENTER} Items.Items[{{Slot:1}}] "
                        "unless data block 2920 81 8 Items[0]", results, timeout=24)


def prepare(server, results):
    server.command("gamerule doMobSpawning false")
    server.command("forceload add 2912 0 3007 15")
    server.assert_state("fixture_chunks_are_loaded", f"if loaded {CENTER} if loaded {REDSTONE}", results)
    server.command("fill 2913 78 1 3007 79 15 minecraft:stone")
    for target in (CENTER_TARGET, LOW_TARGET, RANGE_TARGET, REDSTONE_TARGET):
        server.command(f"setblock {target} minecraft:air")
    seed(server, CENTER, 3500, 1200, 0)
    seed(server, LOW, 127, 1300, 1)
    seed(server, RANGE, 3100, 1250, 1, visible=True)
    seed(server, WORLD_BOTTOM, 2500, 1500, 0)
    seed(server, REDSTONE, 2000, 1000, 0, redstone="active_with_signal")
    server.command('setblock 2920 81 8 minecraft:hopper[facing=down]{Items:[{Slot:0b,id:"minecraft:water_bucket",Count:1b}]}')
    observe_center(server, results)
    await_ticks(server, 155)
    observe_center(server, results)
    banked_and_blocked(server, results)


def continue_after_restart(server, results):
    observe_center(server, results)
    banked_and_blocked(server, results)
    server.command(f"data modify block {RANGE} ActionRange.range set value 2")
    server.command("setblock 3000 80 9 minecraft:redstone_block")
    server.assert_state("radius_extension_runs_with_the_restored_fluid_and_fe",
                        f"{machine(RANGE, 1600, 250, 2, visible=True)} if block {RANGE_TARGET} {SOURCE}",
                        results, timeout=24)
    server.assert_state("redstone_enable_places_the_restored_bucket_and_keeps_500_fe",
                        f'{machine(REDSTONE, 500, 0, 0, redstone="active_with_signal")} '
                        f"if block {REDSTONE_TARGET} {SOURCE}", results, timeout=24)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--enable-niard", action="store_true")
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")

    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    if args.enable_niard:
        with (directory / "server.properties").open("a") as properties:
            properties.write(f"initial-enabled-packs=vanilla,{PACK}\n")
    report = {"passed": False, "forge": FORGE, "jarSha256": digest(directory / "mods/enderio.jar"),
              "niardEnabled": args.enable_niard, "assertions": [], "servers": []}
    try:
        def fresh(server, results):
            packs(server, results, args.enable_niard)
            if args.enable_niard:
                prepare(server, results)

        def reopened(server, results):
            packs(server, results, args.enable_niard)
            if args.enable_niard:
                continue_after_restart(server, results)

        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", reopened)
        report["passed"] = True
    finally:
        path = directory / "niard-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Niard passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
