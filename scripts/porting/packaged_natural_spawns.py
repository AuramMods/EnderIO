#!/usr/bin/env python3
"""Observe scheduler-driven spawns in a disposable packaged Forge installation."""

import argparse
from collections import Counter
import json
from pathlib import Path
import shutil
import subprocess
import sys
import time

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage


PLAYER = "EnderIOQA"
STATION = "2082.5 -59 8.5"
AVERSION = "2120 -59 8"
RELOCATOR = "2102 -59 8"
INHIBITOR = "2100 -59 8"
AREA = "2080 0 2127 15"
MOBS = "@e[type=!minecraft:player,type=!minecraft:item,x=2096,y=-60,z=0,dx=31,dy=5,dz=15]"
ZOMBIE = "minecraft:zombie"
SKELETON = "minecraft:skeleton"
FILTER = ('{Slot:0,id:"enderio:basic_soul_filter",Count:1,tag:{"enderio:data":{"enderio:soul_filter":'
          '{entities:[{index:0,value:{entity_type:"minecraft:zombie",entity_tag:{}}}],isInvert:0b,nbt:0b}}}}')


def setup(server, results):
    server.command("gamerule doMobSpawning false")
    server.command("gamerule doDaylightCycle false")
    server.command("gamerule spawnRadius 0")
    server.command("time set noon")
    server.command("difficulty normal")
    server.command(f"forceload add {AREA}")
    server.assert_state("spawn_source_pad_and_station_loaded", f"if loaded {AVERSION} if loaded {RELOCATOR} if loaded 2082 -59 8", results)
    server.command("fill 2080 -60 5 2085 -60 11 minecraft:glass")
    server.command("fill 2112 -60 0 2127 -56 15 minecraft:stone hollow")
    server.command("fill 2096 -60 2 2108 -60 14 minecraft:stone")
    server.command("fill 2096 -56 2 2108 -56 14 minecraft:sea_lantern")
    server.command("setworldspawn 2082 -59 8")
    for name, pos, radius, slots in (("aversion", AVERSION, 16, 2), ("relocator", RELOCATOR, 28, 2),
                                      ("inhibitor", INHIBITOR, 9, 1)):
        server.command(f"setblock {pos} enderio:{name}_obelisk")
        cap = "octadic_capacitor" if name == "relocator" else "basic_capacitor"
        capacitor = f'{{Slot:{slots - 1},id:"enderio:{cap}",Count:1}}'
        inventory = f"{FILTER},{capacitor}" if slots == 2 else capacitor
        server.command(f'data merge block {pos} {{Items:{{Size:{slots},Items:[{inventory}]}},EnergyStored:90000,'
                       f'ActionRange:{{range:{radius},isVisible:0b}},RedstoneControl:"never_active"}}')
        server.assert_state(f"{name}_starts_charged_with_its_intended_range",
                            f'if data block {pos} {{EnergyStored:90000,ActionRange:{{range:{radius}}},'
                            f'RedstoneControl:"never_active",Items:{{Items:[{inventory}]}}}}', results)


def set_mode(server, pos, on):
    mode = "always_active" if on else "never_active"
    server.command(f'data merge block {pos} {{RedstoneControl:"{mode}"}}')


def in_source(pos):
    x, y, z = pos
    return 2113 <= x < 2127 and y == -59 and 1 <= z < 15


def on_pad(pos):
    x, y, z = pos
    return 2097 <= x < 2108 and y == -59 and 3 <= z < 14


def observations(server, start):
    with server.condition:
        lines = server.lines[start:]
    attempts, joins, teleports = {}, {}, {}
    for line in lines:
        if "PORT_SPAWN " not in line:
            continue
        event = json.loads(line.split("PORT_SPAWN ", 1)[1])
        if event["event"] == "finalize" and in_source(event["origin"]) and event["type"] in (ZOMBIE, SKELETON):
            attempts[event["uuid"]] = event
        elif event["event"] == "join":
            joins[event["uuid"]] = event
        elif event["event"] == "teleport":
            teleports[event["uuid"]] = event
    return [{"finalize": attempt, "join": joins.get(uid), "teleport": teleports.get(uid)} for uid, attempt in attempts.items()]


def outcome(observed):
    attempt, join, teleport = (observed[name] for name in ("finalize", "join", "teleport"))
    if attempt["tick"] != join["tick"] or attempt["position"] != join["position"]:
        return "inconsistent"
    at_origin = join["position"] == attempt["origin"]
    if attempt["canceled"] and join["canceled"] and at_origin:
        return "denied"
    if attempt["canceled"] or join["canceled"]:
        return "inconsistent"
    if teleport is None:
        return "original" if at_origin else "unobserved teleport"
    if at_origin and teleport["canceled"] and on_pad(teleport["position"]):
        return "blocked destination"
    if (not teleport["canceled"] and teleport["position"] == join["position"]
            and on_pad(join["position"]) and join["supported"] and join["blockLight"] >= 10):
        return "relocated"
    return "inconsistent"


def phase(server, report, name, zombie_outcome):
    server.command(f"kill {MOBS}")
    start = len(server.lines)
    expected = {ZOMBIE: zombie_outcome, SKELETON: "original"}
    deadline = time.monotonic() + 26
    clear_after = time.monotonic() + 4
    server.command("gamerule doMobSpawning true")
    try:
        while True:
            sample = observations(server, start)
            counts = Counter((row["finalize"]["type"], outcome(row)) for row in sample if row["join"] is not None)
            wrong = [key for key in counts if expected[key[0]] != key[1]]
            enough = all(counts[key] >= 3 for key in expected.items())
            if wrong or enough or time.monotonic() >= deadline:
                break
            if server.process.poll() is not None:
                raise RuntimeError(f"Server exited during {name}; see {server.log_path}")
            if time.monotonic() >= clear_after:
                server.command(f"kill {MOBS}")
                clear_after = time.monotonic() + 4
            with server.condition:
                server.condition.wait(timeout=0.15)
    finally:
        server.command("gamerule doMobSpawning false")
    sample = observations(server, start)
    counts = Counter((row["finalize"]["type"], outcome(row) if row["join"] else "insertion missing") for row in sample)
    passed = all(counts[key] >= 3 for key in expected.items()) and all(expected[key[0]] == key[1] for key in counts)
    report["assertions"].append({"assertion": name, "passed": passed, "expected": expected,
                                 "counts": [{"type": key[0], "outcome": key[1], "count": count}
                                            for key, count in sorted(counts.items())], "samples": sample})
    if not passed:
        raise AssertionError(f"{name}: expected {expected}, observed {dict(counts)}; see {server.log_path}")
    if zombie_outcome in ("denied", "relocated"):
        last_zombie = next(row["finalize"]["uuid"] for row in reversed(sample) if row["finalize"]["type"] == ZOMBIE)
        condition = "unless" if zombie_outcome == "denied" else "if"
        server.assert_state(f"{name}_confirms_live_entity_state", f"{condition} entity {last_zombie}", report["assertions"])


def exercise(args, server, report):
    results = report["assertions"]
    output = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "natural-spawns", "--output", str(output), "--server", f"127.0.0.1:{args.port}", "--timeout", "230"]
    with (server.directory / "natural-spawn-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            server.command(f"tp {PLAYER} {STATION} -90 0")
            phase(server, report, "native_scheduler_spawns_both_mobs_with_the_obelisks_disabled", "original")
            set_mode(server, AVERSION, True)
            phase(server, report, "active_aversion_denies_natural_zombies_and_admits_skeletons", "denied")
            set_mode(server, AVERSION, False)
            set_mode(server, RELOCATOR, True)
            phase(server, report, "active_relocator_moves_natural_zombies_to_the_lit_pad", "relocated")
            set_mode(server, INHIBITOR, True)
            phase(server, report, "inhibitor_rejects_relocation_and_natural_zombies_stay_in_the_source", "blocked destination")
            set_mode(server, INHIBITOR, False)
            server.command("fill 2096 -59 2 2108 -57 14 minecraft:stone replace minecraft:air")
            phase(server, report, "sealed_relocator_still_admits_the_original_natural_spawn", "original")
            server.command("fill 2096 -59 2 2108 -57 14 minecraft:air replace minecraft:stone")

            server.command("setworldspawn 0 -59 0")
            server.command("setblock 0 -60 0 minecraft:stone")
            server.command(f"tp {PLAYER} 0.5 -59 0.5")
            server.command(f"forceload remove {AREA}")
            server.assert_state("the_chamber_and_relocator_chunks_unload", f"unless loaded {AVERSION} unless loaded {RELOCATOR}",
                                results, timeout=30)
            server.command(f"tp {PLAYER} {STATION} -90 0")
            server.assert_state("moving_back_loads_both_spawn_chunks", f"if loaded {AVERSION} if loaded {RELOCATOR}", results)
            server.assert_state("relocator_still_has_its_saved_range_and_filter",
                                f'if data block {RELOCATOR} {{ActionRange:{{range:28}},RedstoneControl:"always_active",'
                                f'Items:{{Items:[{FILTER}]}}}}', results)
            phase(server, report, "reloaded_relocator_moves_new_natural_zombies_again", "relocated")

            (output / "server-verified").touch()
            if client.wait(timeout=24) != 0:
                raise AssertionError(f"Natural spawn client failed; see {output}")
            summary = json.loads((output / "summary.json").read_text())
            if digest(Path(summary["client"]["frame"]["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Client and server Ender IO jars differ")
        finally:
            stop_client(client)
            summary = output / "summary.json"
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--qa-jar", required=True, type=Path)
    parser.add_argument("--launch", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")

    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    shutil.copyfile(args.qa_jar, directory / "mods/enderio-client-qa.jar")
    report = {"passed": False, "jarSha256": digest(directory / "mods/enderio.jar"), "assertions": [], "servers": []}
    try:
        def run(server, results):
            setup(server, results)
            exercise(args, server, report)

        run_stage(args, directory, report, "natural", run, startup_timeout=240)
        report["passed"] = True
    finally:
        path = directory / "natural-spawn-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Natural spawns passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
