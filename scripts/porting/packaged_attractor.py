#!/usr/bin/env python3
"""Exercise specialized Attractor mob behavior on the packaged Forge dedicated server."""

import argparse
import json
import math
from pathlib import Path
import re
import shutil
import sys
import time

sys.dont_write_bytecode = True
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage


MACHINE = "2224 80 8"
CENTER = (2224.5, 80.5, 8.5)
PROBE = "port_attractor_probe"
CAP = '{Slot:1,id:"enderio:octadic_capacitor",Count:1}'


def set_machine(server, types, *, active):
    entries = ",".join(f'{{index:{index},value:{{entity_type:"minecraft:{mob}",entity_tag:{{}}}}}}'
                       for index, mob in enumerate(types))
    soul = ('{Slot:0,id:"enderio:basic_soul_filter",Count:1,tag:{"enderio:data":{"enderio:soul_filter":'
            f'{{entities:[{entries}],isInvert:0b,nbt:0b}}}}}}}}')
    mode = "always_active" if active else "never_active"
    server.command(f'data merge block {MACHINE} {{Items:{{Size:2,Items:[{soul},{CAP}]}},EnergyStored:150000,'
                   f'ActionRange:{{range:24,isVisible:0b}},RedstoneControl:"{mode}"}}')


def set_mode(server, *, active):
    mode = "always_active" if active else "never_active"
    server.command(f'data merge block {MACHINE} {{RedstoneControl:"{mode}"}}')


def summon(server, mob, pos, extra=""):
    server.command(f'summon minecraft:{mob} {pos} {{Tags:["{PROBE}"],PersistenceRequired:1b{extra}}}')


def sample(server, name, start, mobs, accept, results, *, timeout=12):
    deadline = time.monotonic() + timeout
    by_mob = {f"minecraft:{mob}": [] for mob in mobs}
    end = start
    while True:
        with server.condition:
            lines = server.lines[end:]
            end += len(lines)
        for line in lines:
            if "PORT_ATTRACTOR " in line:
                record = json.loads(line.split("PORT_ATTRACTOR ", 1)[1])
                if record["type"] in by_mob:
                    by_mob[record["type"]].append(record)
        passed = all(records and accept(records) for records in by_mob.values())
        if passed or time.monotonic() >= deadline:
            break
        if server.process.poll() is not None:
            raise RuntimeError(f"Server exited during {name}; see {server.log_path}")
        with server.condition:
            server.condition.wait(timeout=0.15)
    results.append({"assertion": name, "passed": passed, "samples": by_mob})
    if not passed:
        latest = {mob: records[-3:] for mob, records in by_mob.items()}
        raise AssertionError(f"{name}: {latest}; see {server.log_path}")


def prepare_arena(server, results):
    server.command("gamerule doMobSpawning false")
    server.command("gamerule doDaylightCycle false")
    server.command("gamerule mobGriefing false")
    server.command("time set midnight")
    server.command("difficulty normal")
    server.command("forceload add 2208 -16 2255 31")
    server.assert_state("attractor_arena_entity_chunks_loaded", f"if loaded {MACHINE} if loaded 2240 82 8", results)
    server.command("fill 2209 79 -13 2253 79 29 minecraft:stone")
    server.command(f"setblock {MACHINE} enderio:attractor_obelisk")


def has_pull(row, speed):
    return row["motion"][0] < -0.1 and math.isclose(math.hypot(*row["motion"]), speed, abs_tol=0.00001)


def moves_in(rows, speed):
    return (any(row["position"][0] < 2231.5 and has_pull(row, speed) for row in rows)
            and rows[-1]["entityTick"] > rows[0]["entityTick"])


def follows_decoy(rows):
    initial_distance = math.dist(rows[0]["position"], CENTER)
    targeted = [row for row in rows if row["decoy"]]
    return len(targeted) >= 4 and any(math.dist(row["position"], CENTER) <= initial_distance - 1.5 for row in targeted)


def ghast(server, results):
    set_machine(server, ["ghast"], active=False)
    start = len(server.lines)
    summon(server, "ghast", "2235.5 83 8.5")
    sample(server, "unpowered_ghast_control_has_no_pull_velocity", start, ["ghast"],
           lambda rows: len(rows) >= 4 and all(not has_pull(row, 1) for row in rows), results)
    start = len(server.lines)
    set_mode(server, active=True)
    sample(server, "powered_attractor_pulls_live_ghast_toward_the_block", start, ["ghast"],
           lambda rows: moves_in(rows, 1), results)
    server.command(f"kill @e[tag={PROBE}]")


def decoy(server, results, mob, position, extra=""):
    set_machine(server, [mob], active=True)
    start = len(server.lines)
    summon(server, mob, position, extra)
    sample(server, f"native_{mob}_follows_the_attractor_decoy", start, [mob], follows_decoy, results, timeout=18)
    start = len(server.lines)
    set_mode(server, active=False)
    sample(server, f"power_off_clears_the_{mob}_decoy_target", start, [mob],
           lambda rows: len(rows) >= 4 and all(not row["decoy"] for row in rows[-4:]), results)
    server.command(f"kill @e[tag={PROBE}]")


def wither(server, results, *, opt_in):
    set_machine(server, ["wither"], active=True)
    start = len(server.lines)
    summon(server, "wither", "2235.5 83 8.5")
    if opt_in:
        sample(server, "restarted_server_with_boss_option_pulls_a_wither", start, ["wither"],
               lambda rows: all(row["bossMode"] for row in rows) and moves_in(rows, 2.25), results)
    else:
        sample(server, "default_boss_option_leaves_a_wither_in_place", start, ["wither"],
               lambda rows: len(rows) >= 12 and all(not row["bossMode"] and not has_pull(row, 2.25) for row in rows), results)
        server.command(f"kill @e[tag={PROBE}]")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--qa-jar", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    shutil.copyfile(args.qa_jar, directory / "mods/enderio-client-qa.jar")
    report = {"passed": False, "jarSha256": digest(directory / "mods/enderio.jar"), "assertions": [], "servers": []}
    try:
        def default(server, results):
            prepare_arena(server, results)
            ghast(server, results)
            decoy(server, results, "phantom", "2233.5 86 3.5")
            decoy(server, results, "slime", "2233.5 80 14.5", ",Size:0")
            wither(server, results, opt_in=False)

        run_stage(args, directory, report, "default", default, startup_timeout=240)
        config = directory / "config/enderio/machines-common.toml"
        config.write_text(re.sub(r"(?m)^(\s*attractorAttractBosses\s*=\s*)false$", r"\g<1>true", config.read_text()))
        run_stage(args, directory, report, "boss-opt-in", lambda server, results: wither(server, results, opt_in=True),
                  startup_timeout=240)
        report["passed"] = True
    finally:
        path = directory / "attractor-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Attractor passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
