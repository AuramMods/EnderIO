#!/usr/bin/env python3
"""Craft the stock fluid tiers over TCP and preserve filtered transfer through upgrades."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_energy_progression import conduit_stack
from packaged_item_progression import bundle
from packaged_laserio import Lane, configure, connection, topology
from packaged_legacy_transfer_filters import wait_ticks
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage
from packaged_starter_progression import BINDER, PLAYER


LANE = Lane("fluid", 8)
TABLE = "3278 80 12"
LEFT, RIGHT = LANE.pos(3279), LANE.pos(3280)
TIERS = ("fluid", "pressurized_fluid", "ender_fluid")
ROUTES = ((0, 0), (1, 0), (1, 1), (2, 1), (2, 2))
BATCHES = (6000, 6000, 6000, 6000, 6500)
RECIPES = ("fluid_conduit", "pressurized_fluid_conduit", "pressurized_fluid_conduit_upgrade", "ender_fluid", "ender_fluid_conduit_upgrade")
MATERIALS = ((BINDER, 34, 4), ("enderio:conductive_alloy_ingot", 3, 1), ("enderio:energetic_alloy_ingot", 5, 1),
             ("enderio:vibrant_alloy_ingot", 5, 1), ("enderio:clear_glass", 2, 1), ("enderio:fused_quartz", 3, 1))
CHUNKS = "3264 0 3295 15"
FILTER_DATA = ('{"enderio:data":{"enderio:fluid_filter":{fluids:[{index:0,value:'
               '{FluidName:"minecraft:water",Amount:37}}],isNbt:0b,isInvert:0b}}}')
FILTER_ITEM = f'{{id:"enderio:basic_fluid_filter",Count:1,tag:{FILTER_DATA}}}'


def route(tiers):
    source = f'{LEFT} Connections[0][{{Side:"west",Config:{connection(LANE, False)}}}]'
    target = f'{RIGHT} Connections[0][{{Side:"east",Config:{connection(LANE, True)},Inventory:[{FILTER_ITEM}]}}]'
    return (f'{bundle(LEFT, TIERS[tiers[0]])} {bundle(RIGHT, TIERS[tiers[1]])} {topology(LANE)} '
            f'if data block {source} if data block {target}')


def fluid(kind, amount):
    return f'{{FluidName:"minecraft:{kind}",Amount:{amount},Tag:{{PortFluidTier:242}}}}'


def tank_state(pos, amount, kind="water"):
    if amount == 0:
        return f"unless data block {pos} Fluids.Tanks[0].Contents.FluidName"
    return f"if data block {pos} Fluids.Tanks[{{Index:0,Contents:{fluid(kind, amount)}}}]"


def supply(server, amount, kind="water"):
    server.command(f"data modify block {LANE.source} Fluids.Tanks set value [{{Index:0,Contents:{fluid(kind, amount)}}}]")


def transfer(server, results, name, tiers, amount, stored, remainder=0):
    supply(server, amount)
    server.assert_state(name, f"{route(tiers)} {tank_state(LANE.source, remainder)} {tank_state(LANE.target, stored)}", results, timeout=15)


def prepare(server, results):
    for command in ("gamerule doMobSpawning false", "gamerule doDaylightCycle false", "gamerule spawnRadius 0", "time set noon",
                    f"forceload add {CHUNKS}", "fill 3275 79 5 3283 79 15 minecraft:stone_bricks", "setworldspawn 3278 80 14",
                    f"setblock {TABLE} minecraft:crafting_table", f"setblock {LANE.source} enderio:pressurized_fluid_tank",
                    f"setblock {LANE.target} enderio:pressurized_fluid_tank"):
        server.command(command)
    server.assert_state("both_route_chunks_are_loaded", f"if loaded {LEFT} if loaded {RIGHT}", results)


def craft(server, results, client, output):
    for slot, (item, supplied, _) in enumerate(MATERIALS, 1):
        server.command(f"item replace entity {PLAYER} hotbar.{slot} with {item} {supplied}")
    for recipe in RECIPES:
        server.command(f"recipe give {PLAYER} enderio:{recipe}")
    server.command(f"tp {PLAYER} 3278.5 80 14.5 180 20")
    (output / "server-table").touch()
    wait_signal(client, output, "crafted")
    parts = [conduit_stack(tier, count) for tier, count in zip(TIERS, (7, 15, 16))]
    spare = [f'{{id:"{item}",Count:{left}b}}' for item, _, left in MATERIALS]
    server.assert_state("five_survival_crafts_spend_exact_materials_and_both_lower_tier_items",
                        f'if data entity {PLAYER} {{Inventory:[{",".join(parts + spare)}]}}', results)


def placement(server, results, client, output):
    server.command(f"tp {PLAYER} 3280.5 80 11.5 180 17")
    for index in range(2):
        (output / f"server-upgrade-{index}").touch()
        wait_signal(client, output, f"upgraded-{index}")
    server.assert_state("both_original_basic_conduits_form_the_cross_chunk_route", topology(LANE), results)
    configure(server, LANE, insert=False)
    configure(server, LANE, insert=True, filter_stack=FILTER_ITEM)
    supply(server, 615, "lava")
    wait_ticks(server, 30)
    server.assert_state("outlet_filter_rejects_lava_and_leaves_the_full_input_at_source",
                        f"{route(ROUTES[0])} {tank_state(LANE.source, 615, 'lava')} {tank_state(LANE.target, 0)}", results)
    stored = 0
    for index, (tiers, batch) in enumerate(zip(ROUTES, BATCHES)):
        if index:
            (output / f"server-upgrade-{index + 1}").touch()
            wait_signal(client, output, f"upgraded-{index + 1}")
        stored += batch
        transfer(server, results, f"route_{index}_keeps_its_filter_and_delivers_exact_tagged_water", tiers, batch, stored)
    returned = ",".join(conduit_stack(tier, count) for tier, count in zip(TIERS, (7, 15, 14)))
    server.assert_state("survival_replacements_refund_one_original_part_each", f"if data entity {PLAYER} {{Inventory:[{returned}]}}", results)


def run_client(args, server, report):
    output = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "fluid-progression", "--server", f"127.0.0.1:{args.port}", "--output", str(output), "--timeout", "195"]
    with (server.directory / "fluid-progression-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            craft(server, report["assertions"], client, output)
            placement(server, report["assertions"], client, output)
            (output / "server-done").touch()
            if client.wait(timeout=25) != 0:
                raise AssertionError(f"Fluid conduit progression client failed; see {output}")
            summary = json.loads((output / "summary.json").read_text())
            frame = summary["client"]["captures"]["upgraded-conduit-route"]
            if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Fluid conduit client and server loaded different Ender IO jars")
        finally:
            stop_client(client)
            summary = output / "summary.json"
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def reload_chunks(server, results):
    server.command("setworldspawn 0 80 0")
    server.command(f"forceload remove {CHUNKS}")
    server.assert_state("both_route_chunks_are_evicted", f"unless loaded {LEFT} unless loaded {RIGHT}", results, timeout=45)
    server.command(f"forceload add {CHUNKS}")
    server.assert_state("the_reloaded_ender_route_keeps_30500_mb_and_the_filter",
                        f"{route(ROUTES[-1])} {tank_state(LANE.source, 0)} {tank_state(LANE.target, 30_500)}", results)
    transfer(server, results, "the_reloaded_route_delivers_another_500_mb", ROUTES[-1], 500, 31_000)


def restart(server, results):
    server.command(f"forceload add {CHUNKS}")
    server.assert_state("restart_keeps_31000_mb_and_the_filter",
                        f"{route(ROUTES[-1])} {tank_state(LANE.source, 0)} {tank_state(LANE.target, 31_000)}", results)
    transfer(server, results, "exactly_1000_mb_fills_the_saved_target_and_500_stays_at_source", ROUTES[-1], 1500, 32_000, remainder=500)
    server.command(f"data remove block {LANE.target} Fluids.Tanks[0].Contents")
    server.assert_state("freeing_the_target_delivers_only_the_retained_500_mb_once",
                        f"{route(ROUTES[-1])} {tank_state(LANE.source, 0)} {tank_state(LANE.target, 500)}", results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for path in ("java", "forge-server", "jar", "launch"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}

    def exercise(server, results):
        prepare(server, results)
        run_client(args, server, report)
        reload_chunks(server, results)

    try:
        run_stage(args, directory, report, "exercise", exercise)
        run_stage(args, directory, report, "reopened", restart)
        report["passed"] = True
    finally:
        (directory / "fluid-progression-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
