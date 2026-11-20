#!/usr/bin/env python3
"""Author LaserIO ghost filters and verify live item and Mekanism routes."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_laserio import COUNTS, ITEM_FILTER, PAPERS, Lane, configure, filter_slot, item_state, topology
from packaged_niard import await_ticks
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_solar_charging import check
from packaged_spawners import PLAYER, WORLD, prepare_profile, run_stage
from saved_world import read_data_file


CHEMICAL = Lane("chemical", 10)
LANES = (ITEM_FILTER, CHEMICAL)
FILTER = "laserio:filter_basic"
TANK = "mekanism:basic_chemical_tank"
PAPER_TAG = "{PortLaser:1}"


def gas(name, amount):
    return f'{{Tank:0b,stored:{{gasName:"mekanism:{name}",amount:{amount}L}}}}'


TANK_TAG = f"{{mekData:{{GasTanks:[{gas('oxygen', 1450)}]}}}}"
SAMPLES = {"item": ("minecraft:paper", PAPER_TAG), "chemical": (TANK, TANK_TAG)}


def saved_filter(kind):
    sample, tag = SAMPLES[kind]
    allow = int(kind == "chemical")
    ghost = f'{{Slot:3,id:"{sample}",Count:1b,tag:{tag}}}'
    return f'{{allowList:{allow}b,compareNBT:{1 - allow}b,inv:{{Size:15,Items:[{ghost}]}}}}'


def installed(lane):
    stack = f'{{id:"{FILTER}",Count:1,tag:{saved_filter(lane.kind)}}}'
    return (f'if data block {filter_slot(lane, insert=False)}.id if data block {lane.pos(3279)} '
            f'Connections[0][{{Side:"west",Inventory:[{stack}]}}]')


def setup(server, results):
    for command in ("gamerule doMobSpawning false", "gamerule spawnRadius 0", "gamerule doDaylightCycle false",
                    "time set noon", "forceload add 3264 0 3295 15", "fill 3275 79 2 3283 79 14 minecraft:stone",
                    "setworldspawn 3279 80 7"):
        server.command(command)
    for lane in LANES:
        endpoint = "minecraft:barrel" if lane.kind == "item" else "mekanism:ultimate_chemical_tank"
        for pos, mode in ((lane.source, 4), (lane.target, 1)):
            server.command(f"setblock {pos} {endpoint}")
            if lane.kind == "chemical":
                sides = ",".join(f"side{i}:{mode}" for i in range(6))
                server.command(f"data modify block {pos} componentConfig.config2 set value {{{sides}}}")
        for x in (3279, 3280):
            server.command(f'setblock {lane.pos(x)} enderio:conduit{{Conduits:["enderio:{lane.kind}"],Connections:[[]]}}')
        server.assert_state(f"{lane.kind}_bundles_connect_across_the_chunk_edge", topology(lane), results)
        configure(server, lane, insert=False)
        configure(server, lane, insert=True)


def exercise(args, server, results, report):
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "laserio-editor", "--server", f"127.0.0.1:{args.port}", "--output", str(evidence), "--timeout", "190"]
    summary = evidence / "summary.json"
    with (server.directory / "laserio-editor-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            for index, lane in enumerate(LANES):
                sample, tag = SAMPLES[lane.kind]
                slot = index * 2
                server.command(f"item replace entity {PLAYER} hotbar.{slot} with {FILTER}")
                server.command(f"item replace entity {PLAYER} hotbar.{slot + 1} with {sample}{tag}")
                server.command(f"tp {PLAYER} 3279.5 80 {lane.z + 2.5} 180 20")
                (evidence / f"server-{lane.kind}-editor").touch()
                physical = f'if data entity {PLAYER} Inventory[{{Slot:{slot + 1}b,id:"{sample}",Count:1b,tag:{tag}}}]'
                wait_signal(client, evidence, f"{lane.kind}-edited")
                server.assert_state(f"{lane.kind}_native_editor_saves_the_ghost_data_and_preserves_the_physical_input",
                                    f'{physical} if data entity {PLAYER} Inventory[{{Slot:{slot}b,id:"{FILTER}",'
                                    f'Count:1b,tag:{saved_filter(lane.kind)}}}]', results)
                (evidence / f"server-{lane.kind}-conduit").touch()
                wait_signal(client, evidence, f"{lane.kind}-installed")
                server.assert_state(f"{lane.kind}_native_install_consumes_only_the_edited_filter_stack",
                                    f"{installed(lane)} {physical} "
                                    f'unless data entity {PLAYER} Inventory[{{Slot:{slot}b,id:"{FILTER}"}}]', results)
            (evidence / "server-finish").touch()
            if client.wait(timeout=35) != 0:
                raise AssertionError(f"LaserIO editor probe failed; see {evidence}")
            loaded = json.loads(summary.read_text())["client"]["captures"]["chemical-laserio-filter-installed"]["loadedMods"]
            check("both_peers_use_the_same_four_production_artifacts", report["jars"],
                  {name: digest(Path(loaded[name]["file"])) for name in report["jars"]}, results)
        finally:
            stop_client(client)
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def item_delivery(server, results, round_number):
    lane = ITEM_FILTER
    for slot, (stack, count) in enumerate(zip(PAPERS, COUNTS)):
        server.command(f"item replace block {lane.source} container.{slot} with {stack} {count}")
    accepted = (0, COUNTS[1] * round_number, COUNTS[2] * round_number)
    await_ticks(server, 55)
    server.assert_state(f"item_edit_denies_only_the_original_tagged_paper_round_{round_number}",
                        f"{item_state(lane.source, (COUNTS[0], 0, 0))} {item_state(lane.target, accepted)} {installed(lane)}",
                        results)


def chemical_delivery(server, results, round_number, batch):
    lane = CHEMICAL
    # Mekanism skips deserialization for tank indices absent from the list.
    server.command(f"data modify block {lane.target} GasTanks set value [{gas('empty', 0)}]")
    server.command(f"data modify block {lane.source} GasTanks set value [{gas('hydrogen', 2345)}]")
    await_ticks(server, 55)
    server.assert_state(f"chemical_edit_rejects_the_unmatched_gas_into_an_empty_target_round_{round_number}",
                        f"if data block {lane.source} GasTanks[{gas('hydrogen', 2345)}] "
                        f"unless data block {lane.target} GasTanks[0] {installed(lane)}", results)
    server.command(f"data modify block {lane.source} GasTanks set value [{gas('oxygen', batch)}]")
    server.assert_state(f"chemical_edit_routes_the_entire_matching_gas_batch_round_{round_number}",
                        f"unless data block {lane.source} GasTanks[0] if data block {lane.target} GasTanks[{gas('oxygen', batch)}] "
                        f"{installed(lane)}", results, timeout=15)


def reopened(server, results):
    for lane in LANES:
        server.assert_state(f"{lane.kind}_edited_filter_survives_restart_unchanged", installed(lane), results)
    server.assert_state("the_first_delivered_4321_units_survive_restart",
                        f"if data block {CHEMICAL.target} GasTanks[{gas('oxygen', 4321)}]", results)
    (file,) = (server.directory / WORLD / "playerdata").glob("*.dat")
    inv = sorted(read_data_file(file)["Inventory"], key=lambda stack: stack["Slot"])
    check("both_physical_samples_remain_in_the_saved_player_inventory",
          [(1, "minecraft:paper", 1), (3, TANK, 1)], [(s["Slot"], s["id"], s["Count"]) for s in inv], results)
    item_delivery(server, results, 2)
    chemical_delivery(server, results, 2, 5678)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for key in ("java", "forge-server", "jar", "laserio", "patchouli", "mekanism", "launch"):
        parser.add_argument(f"--{key}", type=Path, required=True)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    for name in ("laserio", "patchouli", "mekanism"):
        shutil.copyfile(getattr(args, name), directory / "mods" / f"{name}.jar")
    report = {"passed": False, "forge": FORGE, "assertions": [], "servers": [],
              "jars": {name: digest(directory / "mods" / f"{name}.jar") for name in ("enderio", "laserio", "patchouli", "mekanism")}}

    def fresh(server, results):
        setup(server, results)
        exercise(args, server, results, report)
        item_delivery(server, results, 1)
        chemical_delivery(server, results, 1, 4321)

    try:
        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", reopened)
        report["passed"] = True
    finally:
        result = directory / "laserio-editor-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"LaserIO editor passed={report['passed']}, {len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
