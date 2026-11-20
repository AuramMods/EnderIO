#!/usr/bin/env python3
"""Craft and upgrade the stock Mekanism chemical conduits in packaged survival."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_cc_tweaked import set_lever
from packaged_energy_progression import conduit_stack
from packaged_fluid_progression import CHUNKS, LEFT, RIGHT, TABLE
from packaged_item_progression import bundle
from packaged_laserio import Lane, topology
from packaged_laserio_editor import gas
from packaged_legacy_transfer_filters import wait_ticks
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage
from packaged_starter_progression import BINDER, PLAYER


LANE = Lane("chemical", 8)
LEVER = "3279 80 7"
TIERS = ("chemical", "pressurized_chemical", "ender_chemical")
ROUTES = ((0, 0), (1, 0), (1, 1), (2, 1), (2, 2))
BATCHES = (7_451, 9_203, 13_009, 14_517, 345_031)
RELOAD_BATCH = 11_127
RESTART_BATCH = 92_503
RECIPES = ("mek_basic_pressurized_tube", "mek_advanced_pressurized_tube", "mek_basic_pressurized_tube_upgrade",
           "mek_elite_pressurized_tube", "mek_advanced_pressurized_tube_upgrade")
MATERIALS = ((BINDER, 37), ("mekanism:basic_pressurized_tube", 13), ("mekanism:advanced_pressurized_tube", 4),
             ("mekanism:elite_pressurized_tube", 4), ("mekanism:alloy_infused", 2), ("mekanism:alloy_reinforced", 2))
FILTER_TAG = ('{"enderio:data":{"enderio:chemical_filter":{chemicals:[{index:0,value:{chemical_type:"gas",'
              'id:"mekanism:oxygen",amount:1450s}}],isInvert:0b}}}')
FILTER = f'{{id:"enderio:basic_chemical_filter",Count:1,tag:{FILTER_TAG}}}'


def settings(insert):
    insert_channel, extract_channel = ("blue", "green") if insert else ("green", "blue")
    mode = "never_active" if insert else "active_without_signal"
    return (f'{{type:"enderio:chemical",is_insert:{int(insert)}b,is_extract:{int(not insert)}b,'
            f'insert_channel:"{insert_channel}",extract_channel:"{extract_channel}",'
            f'extract_redstone_control:"{mode}",extract_redstone_channel:"blue"}}')


def route(tiers):
    source = f'{LEFT} Connections[0][{{Side:"west",Config:{settings(False)}}}]'
    target = f'{RIGHT} Connections[0][{{Side:"east",Config:{settings(True)},Inventory:[{FILTER}]}}]'
    return (f'{bundle(LEFT, TIERS[tiers[0]])} {bundle(RIGHT, TIERS[tiers[1]])} {topology(LANE)} '
            f'if data block {source} if data block {target}')


def tank(pos, amount, kind="oxygen"):
    if amount:
        return f"if data block {pos} GasTanks[{gas(kind, amount)}]"
    return f"unless data block {pos} GasTanks[0]"


def balances(source, target, kind="oxygen"):
    return f"{tank(LANE.source, source, kind)} {tank(LANE.target, target)}"


def supply(server, amount, kind="oxygen"):
    server.command(f"data modify block {LANE.source} GasTanks set value [{gas(kind, amount)}]")


def prepare(server, results):
    for command in ("gamerule doMobSpawning false", "gamerule doDaylightCycle false", "gamerule spawnRadius 0", "time set noon",
                    f"forceload add {CHUNKS}", "fill 3275 79 5 3283 79 15 minecraft:stone_bricks", "setworldspawn 3278 80 14",
                    f"setblock {TABLE} minecraft:crafting_table"):
        server.command(command)
    for pos, mode in ((LANE.source, 4), (LANE.target, 1)):
        server.command(f"setblock {pos} mekanism:ultimate_chemical_tank")
        sides = ",".join(f"side{side}:{mode}" for side in range(6))
        server.command(f"data modify block {pos} componentConfig.config2 set value {{{sides}}}")
    set_lever(server, LEVER, True)
    server.assert_state("both_chemical_tanks_and_route_chunks_are_loaded",
                        f"if loaded {LEFT} if loaded {RIGHT} if block {LANE.source} mekanism:ultimate_chemical_tank "
                        f"if block {LANE.target} mekanism:ultimate_chemical_tank", results)


def craft(server, results, client, output):
    for slot, (item, supplied) in enumerate(MATERIALS, 1):
        server.command(f"item replace entity {PLAYER} hotbar.{slot} with {item} {supplied}")
    for recipe in RECIPES:
        server.command(f"recipe give {PLAYER} enderio:{recipe}")
    server.command(f"tp {PLAYER} 3278.5 80 14.5 180 20")
    (output / "server-table").touch()
    wait_signal(client, output, "crafted")
    parts = [conduit_stack(tier, count) for tier, count in zip(TIERS, (4, 3, 11))]
    spare = [f'{{id:"{item}",Count:1b}}' for item, _ in MATERIALS]
    server.assert_state("survival_crafts_consume_the_exact_tubes_binder_alloys_and_eight_lower_tier_parts_per_upgrade",
                        f'if data entity {PLAYER} {{Inventory:[{",".join(parts + spare)}]}}', results)


def next_placement(client, output, index):
    (output / f"server-upgrade-{index}").touch()
    wait_signal(client, output, f"upgraded-{index}")


def operate_route(server, results, client, output):
    server.command(f"tp {PLAYER} 3280.5 80 11.5 180 17")
    for index in range(2):
        next_placement(client, output, index)
    server.assert_state("the_crafted_basic_pair_joined_across_the_chunk_edge", topology(LANE), results)
    inlet = f'{LEFT} Connections[0][{{Side:"west"}}]'
    outlet = f'{RIGHT} Connections[0][{{Side:"east"}}]'
    server.command(f"data modify block {inlet}.Config set value {settings(False)}")
    server.command(f"data modify block {outlet}.Config set value {settings(True)}")
    server.command(f"data modify block {outlet}.Inventory set value [{{}},{FILTER}]")

    set_lever(server, LEVER, False)
    supply(server, 9_731, "hydrogen")
    wait_ticks(server, 30)
    server.assert_state("oxygen_allowlist_rejects_hydrogen_with_the_gate_open_and_target_empty",
                        f"{route(ROUTES[0])} {balances(9731, 0, 'hydrogen')}", results)

    stored = 0
    for index, (tiers, batch) in enumerate(zip(ROUTES, BATCHES)):
        set_lever(server, LEVER, True)
        supply(server, batch)
        server.assert_state(f"route_{index}_holds_the_batch_before_placement",
                            f"{route(ROUTES[max(0, index - 1)])} {balances(batch, stored)}", results)
        if index:
            next_placement(client, output, index + 1)
        wait_ticks(server, 25)
        server.assert_state(f"route_{index}_preserves_gate_filter_and_both_balances",
                            f"{route(tiers)} {balances(batch, stored)}", results)
        set_lever(server, LEVER, False)
        stored += batch
        server.assert_state(f"route_{index}_delivers_the_exact_oxygen_batch_on_signal_removal",
                            f"{route(tiers)} {balances(0, stored)}", results, timeout=15)

    returned = ",".join(conduit_stack(tier, count) for tier, count in zip(TIERS, (4, 3, 9)))
    server.assert_state("each_survival_upgrade_refunds_one_original_part", f"if data entity {PLAYER} {{Inventory:[{returned}]}}", results)


def run_client(args, server, report):
    output = ROOT / "build/port-evidence" / f"{args.name}-client"
    summary = output / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "chemical-progression", "--server", f"127.0.0.1:{args.port}", "--output", str(output), "--timeout", "195"]
    with (server.directory / "chemical-progression-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            craft(server, report["assertions"], client, output)
            operate_route(server, report["assertions"], client, output)
            (output / "server-done").touch()
            if client.wait(timeout=25) != 0:
                raise AssertionError(f"Chemical progression client failed; see {output}")
            frame = json.loads(summary.read_text())["client"]["captures"]["upgraded-conduit-route"]
            for mod in ("enderio", "mekanism"):
                if digest(Path(frame["loadedMods"][mod]["file"])) != report["jarHashes"][mod]:
                    raise AssertionError(f"Chemical progression peers loaded different {mod} jars")
        finally:
            stop_client(client)
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def reload_chunks(server, results):
    server.command("setworldspawn 0 80 0")
    server.command(f"forceload remove {CHUNKS}")
    server.assert_state("both_chemical_route_chunks_evicted", f"unless loaded {LEFT} unless loaded {RIGHT}", results, timeout=45)
    server.command(f"forceload add {CHUNKS}")
    server.assert_state("both_endpoints_reopen_with_the_upgraded_route_filter_and_original_balance",
                        f"{route(ROUTES[-1])} {balances(0, sum(BATCHES))}", results)
    supply(server, RELOAD_BATCH)
    total = sum(BATCHES) + RELOAD_BATCH
    server.assert_state("reloaded_route_delivers_the_new_batch", balances(0, total), results)
    set_lever(server, LEVER, True)
    supply(server, RESTART_BATCH)
    wait_ticks(server, 25)
    server.assert_state("powered_gate_saves_the_waiting_batch_and_filter", f"{route(ROUTES[-1])} {balances(RESTART_BATCH, total)}", results)


def restart(server, results):
    total = sum(BATCHES) + RELOAD_BATCH
    server.assert_state("saved_upgraded_route_keeps_the_gate_filter_and_original_gas_balances",
                        f"{route(ROUTES[-1])} if block {LEVER} minecraft:lever[powered=true] {balances(RESTART_BATCH, total)}", results)
    set_lever(server, LEVER, False)
    server.assert_state("reopened_route_delivers_exactly_the_saved_batch", balances(0, total + RESTART_BATCH), results)
    wait_ticks(server, 25)
    server.assert_state("the_reopened_line_keeps_the_exact_final_balance", f"{route(ROUTES[-1])} {balances(0, total + RESTART_BATCH)}", results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for path in ("java", "forge-server", "jar", "launch", "mekanism"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    shutil.copyfile(args.mekanism, directory / "mods/mekanism.jar")
    report = {"passed": False, "assertions": [], "servers": [], "jarHashes": {
        mod: digest(directory / f"mods/{mod}.jar") for mod in ("enderio", "mekanism")}}

    def exercise(server, results):
        prepare(server, results)
        run_client(args, server, report)
        reload_chunks(server, results)

    try:
        run_stage(args, directory, report, "exercise", exercise)
        run_stage(args, directory, report, "reopened", restart)
        report["passed"] = True
    finally:
        (directory / "chemical-progression-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
