#!/usr/bin/env python3
"""Render the decorative redstone set and restart Forge with ten active lever pulses."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_decorative_redstone import LEVERS, PLATES
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage
from saved_world import read_chunk, read_data_file


PLAYER = "EnderIOQA"
OBJECTIVE = "port_pulse"
DELAYS = (100, 200, 600, 1200, 6000)
LEVER_CAMERA = "4102.5 86 2.5 0 45"
PLATE_CAMERA = "4103.5 87 13.5 0 50"


def samples():
    return {
        "levers": [{"x": 4100 + i // 2, "y": 80, "z": 8 + i % 2 * 2, "id": f"enderio:{block}"}
                   for i, block in enumerate(LEVERS)],
        "plates": [{"x": 4100 + i % 8, "y": 80, "z": 19 + i // 8 * 2, "id": f"enderio:{block}"}
                   for i, block in enumerate(PLATES)],
    }


def coordinate(cell):
    return f'{cell["x"]} {cell["y"]} {cell["z"]}'


def lever_state(cell, on):
    powered = str(on).lower()
    return f'if block {coordinate(cell)} {cell["id"]}[powered={powered}]'


def install_clock(directory, levers):
    pack = directory / WORLD / "datapacks/port_pulse"
    tags = pack / "data/minecraft/tags/functions"
    tags.mkdir(parents=True)
    functions = pack / "data/port_pulse/functions"
    functions.mkdir(parents=True)
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 15, "description": "Port lever tick recorder"}}))
    (tags / "tick.json").write_text(json.dumps({"values": ["port_pulse:armed"]}))
    (functions / "armed.mcfunction").write_text(f"execute if score #armed {OBJECTIVE} matches 1 run function port_pulse:record\n")
    commands = [f"execute store result score #now {OBJECTIVE} run time query gametime"]
    for i, lever in enumerate(levers):
        commands.append(f"execute {lever_state(lever, True)} unless score #on{i} {OBJECTIVE} matches 0.. "
                        f"run scoreboard players operation #on{i} {OBJECTIVE} = #now {OBJECTIVE}")
        commands.append(f"execute {lever_state(lever, False)} if score #on{i} {OBJECTIVE} matches 0.. "
                        f"unless score #off{i} {OBJECTIVE} matches 0.. "
                        f"run scoreboard players operation #off{i} {OBJECTIVE} = #now {OBJECTIVE}")
    (functions / "record.mcfunction").write_text("\n".join(commands) + "\n")


def prepare_scene(server, results, scene):
    server.command("forceload add 4096 0 4111 31")
    server.assert_state("both_scene_chunks_are_loaded", "if loaded 4100 80 8 if loaded 4107 80 21", results)
    # The spawn chunk is prepared before the first ticking world update after a restart.
    server.command("setworldspawn 4102 80 9")
    server.command("gamerule doMobSpawning false")
    server.command("gamerule doDaylightCycle false")
    server.command("time set noon")
    server.command("fill 4099 79 7 4105 79 11 minecraft:smooth_quartz")
    server.command("fill 4099 79 18 4108 79 22 minecraft:smooth_quartz")
    server.command("setblock 4102 85 2 minecraft:barrier")
    server.command("setblock 4103 86 13 minecraft:barrier")
    for lever in scene["levers"]:
        server.command(f'setblock {coordinate(lever)} {lever["id"]}[face=floor,facing=north,powered=false]')
    for plate in scene["plates"]:
        server.command(f'setblock {coordinate(plate)} {plate["id"]}')
    server.command(f"scoreboard objectives add {OBJECTIVE} dummy")


def occupied_plates(server, plates):
    for plate in plates:
        block = plate["id"]
        properties = "powered=true"
        if "weighted" in block:
            properties = "power=2" if "heavy" in block else "power=12"
        server.command(f"setblock {coordinate(plate)} {block}[{properties}]")


def play_client(args, server, results, report, scene, layout):
    output = ROOT / "build/port-evidence" / args.name
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
               "--launch", str(args.launch.resolve()), "--scenario", "decorative-redstone",
               "--output", str(output), "--server", f"127.0.0.1:{args.port}", "--timeout", "190",
               "--redstone-layout", str(layout)]
    with (server.directory / "client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            server.command(f"clear {PLAYER}")
            server.command(f"tp {PLAYER} {PLATE_CAMERA}")
            (output / "server-ready-plates-up").touch()
            wait_signal(client, output, "plates-up")
            occupied_plates(server, scene["plates"])
            (output / "server-ready-plates-down").touch()
            wait_signal(client, output, "plates-down")
            server.command(f"tp {PLAYER} {LEVER_CAMERA}")
            (output / "server-ready-levers").touch()
            wait_signal(client, output, "levers")

            server.command(f"scoreboard players set #armed {OBJECTIVE} 1")
            server.command(f"tp {PLAYER} 4102.5 80 9.5 180 35")
            (output / "server-ready-fire").touch()
            wait_signal(client, output, "uses-sent")
            active = " ".join(lever_state(cell, True) for cell in scene["levers"])
            recorded = " ".join(f"if score #on{i} {OBJECTIVE} matches 0.." for i in range(len(LEVERS)))
            server.assert_state("all_ten_native_use_actions_start_timed_pulses", f"{active} {recorded}", results)
            server.command(f"tp {PLAYER} {LEVER_CAMERA}")
            (output / "server-ready-fired").touch()
            wait_signal(client, output, "fired")
            if client.wait(timeout=4) != 0:
                raise AssertionError(f"Decorative redstone client failed, see {output}")
            for label, idle, pressed in (("plates", "plates-up", "plates-down"), ("levers", "levers-at-rest", "levers-pulled")):
                changed = digest(output / f"{idle}.png") != digest(output / f"{pressed}.png")
                results.append({"assertion": f"{label}_render_differently_when_activated", "passed": changed})
                if not changed:
                    raise AssertionError(f"Identical packaged frames for {label}; see {output}")
            server.assert_state("all_ten_pulses_are_still_active_before_shutdown", active, results)
            # Refresh this chunk's save after the activation snapshot and before shutting down.
            server.command("setblock 4106 79 11 minecraft:smooth_quartz")
        finally:
            stop_client(client)
            summary = output / "summary.json"
            if summary.is_file():
                report["client"] = json.loads(summary.read_text())


def saved_pulses(world, levers):
    level_time = read_data_file(world / "level.dat")["Data"]["Time"]
    scores = read_data_file(world / "data/scoreboard.dat")["data"]["PlayerScores"]
    starts = {row["Name"]: row["Score"] for row in scores if row["Objective"] == OBJECTIVE}
    first = levers[0]
    chunk = read_chunk(world, first["x"] // 16, first["z"] // 16)
    pending = {(tick["x"], tick["y"], tick["z"], tick["i"]): tick["t"] for tick in chunk["block_ticks"]}
    return {
        "levelTime": level_time,
        "chunkTime": chunk["LastUpdate"],
        "pulses": [{"id": cell["id"], "activatedAt": starts[f"#on{i}"],
                    "remaining": pending.get((cell["x"], cell["y"], cell["z"], cell["id"]))}
                   for i, cell in enumerate(levers)],
    }


def reopen(server, results, levers, snapshot):
    active = " ".join(lever_state(cell, True) for cell in levers)
    server.assert_state("all_ten_pulses_remain_active_after_restart", active, results)
    previous = 0
    for tier, delay in enumerate(DELAYS):
        pair = (2 * tier, 2 * tier + 1)
        both_reset = " ".join(f"if score #off{i} {OBJECTIVE} matches 0.. {lever_state(levers[i], False)}" for i in pair)
        server.assert_state(f"both_{delay}_tick_levers_reset_after_restart", both_reset, results,
                            timeout=35 + (delay - previous) / 20)
        for i in pair:
            name = levers[i]["id"].split(":")[1]
            pulse = snapshot["pulses"][i]
            remaining = pulse["remaining"]
            saved = remaining is not None and 0 < remaining < delay and (
                snapshot["chunkTime"] + remaining == pulse["activatedAt"] + delay)
            results.append({"assertion": f"{name}_saves_the_expected_remaining_duration", "passed": saved,
                            "remaining": remaining, "originalDelay": delay})
            if not saved:
                raise AssertionError(f"{name} has an unexpected saved tick: {pulse}, chunk saved at {snapshot['chunkTime']}")
            server.command(f"scoreboard players get #off{i} {OBJECTIVE}")
            resumed_deadline = snapshot["levelTime"] + remaining
            server.assert_state(f"{name}_resets_on_its_persisted_deadline",
                                f"if score #off{i} {OBJECTIVE} matches {resumed_deadline}", results)
        previous = delay


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--forge-server", type=Path, required=True)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--launch", type=Path, required=True)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")

    root = ROOT / "build/port-runtime" / args.name
    root.mkdir()
    directory = root / "server"
    report = {"passed": False, "jarSha256": digest(args.jar), "assertions": [], "servers": []}
    scene = samples()
    layout = root / "layout.json"
    layout.write_text(json.dumps(scene))
    try:
        prepare_profile(args, directory, args.jar)
        install_clock(directory, scene["levers"])

        def exercise(server, results):
            prepare_scene(server, results, scene)
            play_client(args, server, results, report, scene, layout)

        run_stage(args, directory, report, "fired", exercise)
        report["restartSnapshot"] = saved_pulses(directory / WORLD, scene["levers"])
        run_stage(args, directory, report, "reopened",
                  lambda server, results: reopen(server, results, scene["levers"], report["restartSnapshot"]))
        report["passed"] = True
    finally:
        (root / "lever-persistence-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
