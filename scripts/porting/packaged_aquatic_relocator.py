#!/usr/bin/env python3
"""Test native aquatic spawning and Relocator destinations in packaged Forge."""

import argparse
from collections import Counter
import json
from math import floor
from pathlib import Path
import shutil
import subprocess
import sys
import time

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_natural_spawns import PLAYER, observations, set_mode
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage


STATION = "5338.5 -59 8.5"
RELOCATOR = "5354 -59 8"
POND = "5349 -60 3 5359 -57 13"
FISH = "minecraft:tropical_fish"
AXOLOTL = "minecraft:axolotl"
FILTER = ('{Slot:0,id:"enderio:basic_soul_filter",Count:1,tag:{"enderio:data":{"enderio:soul_filter":'
          '{entities:[{index:0,value:{entity_type:"minecraft:tropical_fish",entity_tag:{}}}],isInvert:0b,nbt:0b}}}}')


def setup(server, results):
    for command in ("gamerule doMobSpawning false", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                    "gamerule spawnRadius 0", "time set noon", "difficulty normal", "forceload add 5328 0 5391 15"):
        server.command(command)
    server.assert_state("swimming_chamber_and_destination_chunks_are_loaded",
                        f"if loaded 5338 -59 8 if loaded {RELOCATOR} if loaded 5386 -60 8", results)
    server.command("fill 5335 -60 5 5341 -60 11 minecraft:glass")
    server.command("setworldspawn 5338 -59 8")
    server.command("fill 5348 -61 2 5360 -57 14 minecraft:glass hollow")
    server.command("fill 5349 -61 3 5359 -61 13 minecraft:sea_lantern")
    server.command("fill 5349 -57 3 5359 -57 13 minecraft:air")
    server.command("fill 5370 -61 0 5387 -56 15 minecraft:glass hollow")
    server.command("fill 5371 -61 1 5386 -61 14 minecraft:clay")
    server.command("fill 5371 -60 1 5386 -57 14 minecraft:water")
    server.command(f"setblock {RELOCATOR} enderio:relocator_obelisk")
    capacitor = '{Slot:1,id:"enderio:octadic_capacitor",Count:1}'
    server.command(f'data merge block {RELOCATOR} {{Items:{{Size:2,Items:[{FILTER},{capacitor}]}},'
                   'EnergyStored:360000,ActionRange:{range:40,isVisible:0b},RedstoneControl:"always_active"}')
    server.assert_state("relocator_has_its_natural_fish_filter_and_range",
                        f'if data block {RELOCATOR} {{ActionRange:{{range:40}},RedstoneControl:"always_active",'
                        f'Items:{{Items:[{FILTER},{capacitor}]}}}}', results)
    server.assert_state("source_has_water_and_the_dry_pad_is_open",
                        "if block 5376 -60 8 minecraft:water if block 5376 -61 8 minecraft:clay "
                        "if block 5352 -59 8 minecraft:air if block 5352 -61 8 minecraft:sea_lantern", results)


def in_source(pos):
    x, y, z = pos
    return 5371 <= x < 5387 and -60 <= y < -56 and 1 <= z < 15


def in_pond(pos):
    x, y, z = pos
    return 5349 <= x < 5360 and -60 <= y < -57 and 3 <= z < 14


def outcome(sample):
    attempt, join, teleport = (sample[name] for name in ("finalize", "join", "teleport"))
    if join is None:
        return "insertion missing"
    if (attempt["canceled"] or join["canceled"] or attempt["tick"] != join["tick"]
            or attempt["position"] != join["position"] or not (attempt["inWater"] and join["inWater"])):
        return "inconsistent"
    if teleport is None and attempt["origin"] == join["position"]:
        return "original"
    if (teleport and not teleport["canceled"] and teleport["inWater"] and teleport["origin"] == attempt["origin"]
            and teleport["position"] == join["position"] and in_pond(join["position"])):
        return "relocated"
    return "inconsistent"


def clear_mobs(server):
    for mob in (FISH, AXOLOTL, "minecraft:glow_squid"):
        server.command(f"kill @e[type={mob},x=5348,y=-61,z=0,dx=40,dy=8,dz=15]")


def phase(server, results, name, fish_outcome):
    clear_mobs(server)
    start = len(server.lines)
    expected = {FISH: fish_outcome, AXOLOTL: "original"}
    deadline = time.monotonic() + 30
    clear_after = time.monotonic() + 5
    server.command("gamerule doMobSpawning true")
    try:
        while True:
            samples = observations(server, start, in_source, tuple(expected))
            counts = Counter((entry["finalize"]["type"], outcome(entry)) for entry in samples if entry["join"] is not None)
            wrong = any(expected[kind] != result for kind, result in counts)
            if wrong or all(counts[entry] >= 3 for entry in expected.items()) or time.monotonic() >= deadline:
                break
            if server.process.poll() is not None:
                raise RuntimeError(f"Forge quit during {name}. See {server.log_path}")
            if time.monotonic() >= clear_after:
                clear_mobs(server)
                clear_after = time.monotonic() + 5
            with server.condition:
                server.condition.wait(timeout=0.15)
    finally:
        server.command("gamerule doMobSpawning false")

    samples = observations(server, start, in_source, tuple(expected))
    counts = Counter((entry["finalize"]["type"], outcome(entry)) for entry in samples)
    passed = (all(counts[entry] >= 3 for entry in expected.items())
              and all(expected[kind] == result for kind, result in counts))
    results.append({"assertion": name, "passed": passed, "samples": samples,
                    "counts": [{"type": kind, "outcome": result, "count": count}
                               for (kind, result), count in sorted(counts.items())]})
    if not passed:
        raise AssertionError(f"{name}: observed {dict(counts)}. See {server.log_path}")
    sample = next(entry for entry in reversed(samples) if entry["finalize"]["type"] == FISH)
    pos = " ".join(str(floor(value)) for value in sample["join"]["position"])
    server.assert_state(f"{name}_confirms_native_fish_and_water",
                        f'if entity {sample["join"]["uuid"]} if block {pos} minecraft:water', results)


def exercise(server, results, reopened):
    if reopened:
        server.assert_state("restored_pond_and_relocator_keep_water_range_and_filter",
                            f'if block 5352 -59 8 minecraft:water if data block {RELOCATOR} '
                            f'{{ActionRange:{{range:40}},RedstoneControl:"always_active",Items:{{Items:[{FILTER}]}}}}', results)
        phase(server, results, "reopened_relocator_accepts_natural_fish_in_water", "relocated")
        return
    phase(server, results, "charged_relocator_preserves_origin_with_a_dry_destination", "original")
    server.command(f"fill {POND} minecraft:water replace minecraft:air")
    server.assert_state("the_open_pond_is_flooded_and_the_obelisk_is_still_present",
                        f"if block 5352 -59 8 minecraft:water if block {RELOCATOR} enderio:relocator_obelisk", results)
    phase(server, results, "active_relocator_moves_matching_natural_fish_into_water", "relocated")
    set_mode(server, RELOCATOR, False)
    phase(server, results, "disabled_relocator_preserves_origin_with_a_flooded_destination", "original")
    set_mode(server, RELOCATOR, True)


def client_session(args, server, report, reopened):
    stage = "reopened" if reopened else "exercise"
    evidence = ROOT / "build/port-evidence" / f"{args.name}-{stage}-client"
    summary = evidence / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
               "--launch", str(args.launch.resolve()), "--scenario", "natural-spawns",
               "--server", f"127.0.0.1:{args.port}", "--output", str(evidence), "--timeout", "220"]
    with (server.directory / f"aquatic-client-{stage}.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            server.command(f"tp {PLAYER} {STATION} -90 0")
            exercise(server, report["assertions"], reopened)
            (evidence / "server-verified").touch()
            if client.wait(timeout=25) != 0:
                raise AssertionError(f"Aquatic fixture client failed. See {evidence}")
            frame = json.loads(summary.read_text())["client"]["frame"]
            if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("The client and server loaded different Ender IO jars")
        finally:
            stop_client(client)
            report["clients"][stage] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for path in ("java", "forge-server", "jar", "qa-jar", "launch"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")

    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    properties = directory / "server.properties"
    properties.write_text(properties.read_text().replace("minecraft:plains", "minecraft:lush_caves"))
    shutil.copyfile(args.qa_jar, directory / "mods/enderio-client-qa.jar")
    report = {"passed": False, "jarSha256": digest(directory / "mods/enderio.jar"),
              "assertions": [], "clients": {}, "servers": []}

    def first(server, results):
        setup(server, results)
        client_session(args, server, report, False)

    try:
        run_stage(args, directory, report, "exercise", first)
        run_stage(args, directory, report, "reopened", lambda server, _: client_session(args, server, report, True))
        report["passed"] = True
    finally:
        path = directory / "aquatic-relocator-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Aquatic Relocator passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
