#!/usr/bin/env python3
"""Verify native Ultimine selection and surviving conduit components over Forge TCP."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_facades import PLAYER, paint_data
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_spawners import prepare_profile, run_stage


ORIGIN = 2879
NEIGHBOR = 2880
DIFFERENT = 2881
UNCOVERED = 2878
FACADE_ROW = 8
CONDUIT_ROW = 21
ENERGY = "enderio:energy"
REDSTONE = "enderio:redstone"


def bundle(conduits, paint=None):
    kinds = json.dumps(conduits)
    connections = ",".join("[]" for _ in conduits)
    provider = "" if paint is None else f',FacadeProvider:{{id:"enderio:conduit_facade",Count:1b,{paint_data(paint)}}}'
    return f'enderio:conduit{{Conduits:{kinds},Connections:[{connections}]{provider}}}'


def conduit_at(x, row, *kinds):
    pos = f"{x} 80 {row}"
    return f"if block {pos} enderio:conduit if data block {pos} {{Conduits:{json.dumps(kinds)}}}"


def painted_at(x, paint):
    pos = f"{x} 80 {FACADE_ROW}"
    if paint is None:
        return f"unless data block {pos} FacadeProvider.id"
    return f'if data block {pos} {{FacadeProvider:{{id:"enderio:conduit_facade",{paint_data(paint)}}}}}'


def connected_at(x, row, side):
    return f'if data block {x} 80 {row} Connections[0][{{Side:"{side}",Status:"connected_conduit"}}]'


def check_row(server, results, row, name, conditions):
    try:
        server.assert_state(name, conditions, results)
    except AssertionError:
        for x in range(UNCOVERED, DIFFERENT + 1):
            server.command(f"data get block {x} 80 {row}")
        raise


def prepare(server, results):
    server.command("gamerule doMobSpawning false")
    server.command("gamerule doDaylightCycle false")
    server.command("gamerule spawnRadius 0")
    server.command("time set noon")
    server.command("forceload add 2864 0 2895 31")
    server.assert_state("both_sides_of_chunk_boundary_load",
                        f"if loaded {ORIGIN} 80 {FACADE_ROW} if loaded {NEIGHBOR} 80 {CONDUIT_ROW}", results)
    server.command("fill 2873 79 4 2887 79 26 minecraft:stone")
    server.command(f"setworldspawn {ORIGIN} 80 12")
    for x, paint in ((UNCOVERED, None), (ORIGIN, "oak_planks"), (NEIGHBOR, "oak_planks"),
                     (DIFFERENT, "birch_planks")):
        server.command(f"setblock {x} 80 {FACADE_ROW} {bundle([ENERGY], paint)}")
    for x, kinds in ((ORIGIN, [ENERGY]), (NEIGHBOR, [ENERGY, REDSTONE]), (DIFFERENT, [REDSTONE])):
        server.command(f"setblock {x} 80 {CONDUIT_ROW} {bundle(kinds)}")
    server.assert_state("the_preview_fixtures_have_different_paints_and_a_shared_energy_route",
                        f'{painted_at(ORIGIN, "oak_planks")} {painted_at(NEIGHBOR, "oak_planks")} '
                        f'{painted_at(DIFFERENT, "birch_planks")} {painted_at(UNCOVERED, None)} '
                        f'{connected_at(ORIGIN, CONDUIT_ROW, "east")} {connected_at(NEIGHBOR, CONDUIT_ROW, "west")}',
                        results)
    server.command("scoreboard objectives add port_ultimine dummy")


def drop_count(server, row, item=None):
    start = f"execute positioned {ORIGIN + 0.5} 80.5 {row + 0.5}"
    matching = f',nbt={{Item:{item}}}' if item else ""
    selector = f"@e[type=minecraft:item,distance=..5{matching}]"
    server.command("scoreboard players set #total port_ultimine 0")
    server.command(f"{start} as {selector} store result score @s port_ultimine run data get entity @s Item.Count")
    server.command(f"{start} as {selector} run scoreboard players operation #total port_ultimine += @s port_ultimine")


def check_drops(server, results, row, item, expected, note):
    for kind, name in ((item, "matching"), (None, "total")):
        drop_count(server, row, kind)
        try:
            server.assert_state(f"{note}_{name}_drops_equal_{expected}",
                                f"if score #total port_ultimine matches {expected}", results, timeout=1)
        except AssertionError:
            server.command(f"execute positioned {ORIGIN + 0.5} 80.5 {row + 0.5} "
                           "as @e[type=minecraft:item,distance=..5] run data get entity @s Item")
            raise


def check_facades(server, results, with_ultimine, note):
    remaining = " ".join(conduit_at(x, FACADE_ROW, ENERGY) for x in (UNCOVERED, ORIGIN, NEIGHBOR, DIFFERENT))
    neighbor_paint = None if with_ultimine else "oak_planks"
    check_row(server, results, FACADE_ROW, f"{note}_keeps_the_underlying_network_and_excluded_facades",
              f'{remaining} {painted_at(ORIGIN, None)} {painted_at(NEIGHBOR, neighbor_paint)} '
              f'{painted_at(DIFFERENT, "birch_planks")} {painted_at(UNCOVERED, None)} '
              f'{connected_at(ORIGIN, FACADE_ROW, "east")}')
    check_drops(server, results, FACADE_ROW, f'{{id:"enderio:conduit_facade",{paint_data("oak_planks")}}}',
                2 if with_ultimine else 1, note)


def check_conduits(server, results, with_ultimine, note):
    neighbor_types = (REDSTONE,) if with_ultimine else (ENERGY, REDSTONE)
    next_index = len(neighbor_types)
    check_row(server, results, CONDUIT_ROW, f"{note}_keeps_other_conduit_types",
              f"if block {ORIGIN} 80 {CONDUIT_ROW} minecraft:air "
              f"{conduit_at(NEIGHBOR, CONDUIT_ROW, *neighbor_types)} "
              f"unless data block {NEIGHBOR} 80 {CONDUIT_ROW} Conduits[{next_index}] "
              f"{conduit_at(DIFFERENT, CONDUIT_ROW, REDSTONE)} "
              f"unless data block {DIFFERENT} 80 {CONDUIT_ROW} Conduits[1]")
    check_drops(server, results, CONDUIT_ROW, '{id:"enderio:conduit",tag:{"enderio:data":{"enderio:conduit":"enderio:energy"}}}',
                2 if with_ultimine else 1, note)


def aim(server, output, row, index):
    server.command(f"tp {PLAYER} {ORIGIN + 0.5} 80 {row + 3.5} 180 20")
    (output / f"server-{index}").touch()


def exercise_client(args, server, results, report):
    output = ROOT / "build/port-evidence" / f"{args.name}-client"
    scenario = "ultimine" if args.ultimine else "ultimine-absent"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", scenario, "--output", str(output), "--server", f"127.0.0.1:{args.port}", "--timeout", "165"]
    summary_path = output / "summary.json"
    with (server.directory / "ultimine-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            server.command(f"item replace entity {PLAYER} hotbar.0 with minecraft:diamond_pickaxe"
                           '{Enchantments:[{id:"minecraft:efficiency",lvl:5s}]}')
            server.command(f"effect give {PLAYER} minecraft:haste 40 1 true")
            aim(server, output, FACADE_ROW, 0)
            wait_signal(client, output, "facades")
            check_facades(server, results, bool(args.ultimine), "survival_facade_break")
            aim(server, output, CONDUIT_ROW, 1)
            wait_signal(client, output, "conduits")
            check_conduits(server, results, bool(args.ultimine), "survival_conduit_break")
            if client.wait(timeout=45) != 0:
                raise AssertionError(f"Ultimine client failed; see {output}")
            frame = json.loads(summary_path.read_text())["client"]["captures"]["facades"]
            if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Client and server Ender IO jars differ")
        finally:
            stop_client(client)
            report["client"] = json.loads(summary_path.read_text()) if summary_path.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--launch", required=True, type=Path)
    parser.add_argument("--ultimine", nargs=3, metavar=("ULTIMINE_JAR", "FTB_LIBRARY_JAR", "ARCHITECTURY_JAR"), type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")

    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    for mod in args.ultimine or ():
        shutil.copyfile(mod, directory / "mods" / mod.name)
    report = {"passed": False, "forge": FORGE, "jarSha256": digest(directory / "mods/enderio.jar"),
              "optionalMods": {mod.name: digest(mod) for mod in args.ultimine or ()}, "assertions": [], "servers": []}
    try:
        def fresh(server, results):
            prepare(server, results)
            exercise_client(args, server, results, report)

        def reopened(server, results):
            check_facades(server, results, bool(args.ultimine), "reopened_facades")
            check_conduits(server, results, bool(args.ultimine), "reopened_conduits")

        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", reopened)
        report["passed"] = True
    finally:
        path = directory / "ultimine-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Ultimine passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
