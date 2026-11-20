#!/usr/bin/env python3
"""Pour and recover every shipped Ender IO bucket in survival across Forge restarts."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_solar_charging import check
from packaged_spawners import PLAYER, WORLD, prepare_profile, run_stage
from saved_world import read_data_file


FLUIDS = ("nutrient_distillation", "dew_of_the_void", "vapor_of_levity", "hootch", "rocket_fuel", "fire_water",
          "xp_juice", "liquid_sunshine", "liquid_darkness", "cloud_seed", "cloud_seed_concentrated")
EMPTY = "minecraft:bucket"


def x_coord(index):
    return 4460 + index * 18


def bucket_id(fluid):
    return f"enderio:{fluid}_bucket"


def slot(index):
    return index if index < 8 else index + 1


def item_in_inventory(index, item):
    return f'if data entity {PLAYER} Inventory[{{Slot:{slot(index)}b,id:"{item}",Count:1b}}]'


def pool(index, fluid):
    x = x_coord(index)
    return f"if block {x} 80 8 enderio:{fluid}[level=0] if block {x + 1} 80 8 enderio:{fluid}[level=1]"


def cleared(index):
    x = x_coord(index)
    return f"if block {x} 80 8 minecraft:air if block {x + 1} 80 8 minecraft:air"


def setup(server):
    for command in ("gamerule doMobSpawning false", "gamerule doFireTick false", "gamerule spawnRadius 0",
                    "gamerule doDaylightCycle false", "time set noon", "forceload add 4448 0 4655 15",
                    "fill 4456 79 5 4646 79 12 minecraft:stone", "setworldspawn 4458 80 11"):
        server.command(command)
    for index in range(len(FLUIDS)):
        x = x_coord(index)
        server.command(f"fill {x - 1} 80 7 {x + 2} 80 9 minecraft:stone")
        server.command(f"fill {x} 80 8 {x + 1} 80 8 minecraft:air")


def stand(server, output, index, fluid, marker, *, flowing=False):
    x = x_coord(index) + int(flowing)
    # Aim from the rim at the floor of the source or of the adjacent flow cell.
    server.command(f"tp {PLAYER} {x + 0.5} 81 9.5 180 69")
    (output / f"server-{fluid}-{marker}").touch()


def place(server, results, client, output):
    wait_signal(client, output, "connected")
    for index, fluid in enumerate(FLUIDS):
        position = f"hotbar.{index}" if index < 8 else f"inventory.{index - 8}"
        server.command(f"item replace entity {PLAYER} {position} with {bucket_id(fluid)}")
    for index, fluid in enumerate(FLUIDS):
        stand(server, output, index, fluid, "ready")
        wait_signal(client, output, f"{fluid}-complete")
        server.assert_state(f"{fluid}_pours_one_source_and_returns_one_empty_bucket",
                            f"{pool(index, fluid)} {item_in_inventory(index, EMPTY)} "
                            f'unless data entity {PLAYER} Inventory[{{id:"{bucket_id(fluid)}"}}]', results)


def recover(server, results, client, output):
    wait_signal(client, output, "connected")
    for index, fluid in enumerate(FLUIDS):
        stand(server, output, index, fluid, "ready", flowing=True)
        wait_signal(client, output, f"{fluid}-rejected-flow")
        hand = min(index, 8)
        server.assert_state(f"{fluid}_cannot_fill_from_the_flowing_cell",
                            f'{pool(index, fluid)} if data entity {PLAYER} Inventory[{{Slot:{hand}b,id:"{EMPTY}",Count:1b}}] '
                            f'unless data entity {PLAYER} Inventory[{{id:"{bucket_id(fluid)}"}}]', results)
        stand(server, output, index, fluid, "source")
        wait_signal(client, output, f"{fluid}-complete")
        server.assert_state(f"{fluid}_pickup_returns_one_full_bucket_and_clears_both_cells",
                            f"{cleared(index)} {item_in_inventory(index, bucket_id(fluid))} "
                            f'unless data entity {PLAYER} Inventory[{{Slot:8b}}]', results)


def check_saved_player(server, results, filled):
    (player,) = (server.directory / WORLD / "playerdata").glob("*.dat")
    saved = read_data_file(player)["Inventory"]
    stacks = sorted((entry["Slot"], entry["id"], entry["Count"]) for entry in saved)
    expected = [(slot(index), bucket_id(fluid) if filled else EMPTY, 1) for index, fluid in enumerate(FLUIDS)]
    check("saved_player_has_eleven_exact_filled_buckets" if filled else "saved_player_has_eleven_exact_empty_buckets",
          expected, stacks, results)


def client_stage(args, server, results, report, name, scenario):
    evidence = ROOT / "build/port-evidence" / f"{args.name}-{name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", f"fluid-buckets-{name}", "--server", f"127.0.0.1:{args.port}", "--output", str(evidence),
               "--timeout", "205"]
    summary = evidence / "summary.json"
    with (server.directory / f"{name}-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            scenario(server, results, client, evidence)
            (evidence / "server-finish").touch()
            if client.wait(timeout=35) != 0:
                raise AssertionError(f"Fluid bucket {name} client failed; see {evidence}")
            if name == "place":
                capture = json.loads(summary.read_text())["client"]["captures"]["vapor_of_levity-placed"]
                check("client_and_server_use_the_same_production_artifact", report["jarSha256"],
                      digest(Path(capture["loadedMods"]["enderio"]["file"])), results)
        finally:
            stop_client(client)
            report["clients"][name] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for key in ("java", "forge-server", "jar", "launch"):
        parser.add_argument(f"--{key}", type=Path, required=True)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "forge": FORGE, "assertions": [], "servers": [], "clients": {},
              "jarSha256": digest(directory / "mods/enderio.jar")}

    def fresh(server, results):
        setup(server)
        client_stage(args, server, results, report, "place", place)

    def reopened(server, results):
        check_saved_player(server, results, filled=False)
        for index, fluid in enumerate(FLUIDS):
            server.assert_state(f"{fluid}_source_and_flow_survive_restart", pool(index, fluid), results)
        client_stage(args, server, results, report, "collect", recover)

    def verified(server, results):
        check_saved_player(server, results, filled=True)
        for index, fluid in enumerate(FLUIDS):
            server.assert_state(f"{fluid}_basin_stays_empty_after_restart", cleared(index), results)

    try:
        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", reopened)
        run_stage(args, directory, report, "verified", verified)
        report["passed"] = True
    finally:
        path = directory / "fluid-buckets-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Fluid buckets passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
