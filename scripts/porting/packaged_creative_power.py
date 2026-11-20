#!/usr/bin/env python3
"""Measure stock Creative Power delivery across a saved conduit route."""

import argparse
import json
from pathlib import Path
import sys

sys.dont_write_bytecode = True
from packaged_cc_tweaked import face
from packaged_item_progression import bundle
from packaged_laserio import Lane, connection
from packaged_server_smoke import ROOT, digest
from packaged_solar_charging import check, sample
from packaged_spawners import WORLD, prepare_profile, run_stage


LANE = Lane("energy", 8)
SOURCE, LEFT, RIGHT, SINK = (LANE.pos(x) for x in range(4110, 4114))
SIGNAL = "4110 80 7"
RATE = 8000


def write_sampler(directory):
    pack = directory / WORLD / "datapacks/port-creative-power"
    functions = pack / "data/port_power/functions"
    functions.mkdir(parents=True)
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 15, "description": "Creative Power tick accounting"}}) + "\n")
    (functions / "creative_power.mcfunction").write_text("\n".join([
        "data modify storage port_power:sample value set value {}",
        "execute store result storage port_power:sample value.tick int 1 run time query gametime",
        f"execute store result storage port_power:sample value.receiver int 1 run data get block {SINK} EnergyStored",
        f"execute store result storage port_power:sample value.source int 1 run data get block {SOURCE} Energy.EnergyStored",
    ]) + "\n")


def topology():
    return " ".join((bundle(LEFT, "energy"), face(LEFT, "west"), face(LEFT, "east", "connected_conduit"),
                     bundle(RIGHT, "energy"), face(RIGHT, "west", "connected_conduit"), face(RIGHT, "east")))


def measure(server, results, label, rate):
    start = sample(server, "creative_power")
    for _ in range(30):
        end = sample(server, "creative_power")
        ticks = end["tick"] - start["tick"]
        if ticks >= 10:
            break
    else:
        raise AssertionError("The fixture did not advance ten native ticks")
    check(f"{label}_delivers_exactly_{rate}_fe_per_tick_over_{ticks}_ticks", (ticks * rate, 0, 0),
          (end["receiver"] - start["receiver"], start["source"], end["source"]), results)
    return {"before": start, "after": end}


def power_window(server, results, report, phase):
    before = sample(server, "creative_power")["receiver"]
    server.command(f"setblock {SIGNAL} minecraft:redstone_block")
    server.assert_state(f"{phase}_signal_starts_delivery_to_the_wired_charger",
                        f"if block {SIGNAL} minecraft:redstone_block if data block {SINK} EnergyStored "
                        f"unless data block {SINK} {{EnergyStored:{before}}}", results, timeout=8)
    report[phase] = measure(server, results, phase, RATE)
    server.command(f"setblock {SIGNAL} minecraft:air")
    report[phase + "Stopped"] = measure(server, results, phase + "_without_signal", 0)


def fresh(server, results, report):
    server.command("forceload add 4096 0 4127 15")
    server.assert_state("both_conduit_chunks_are_loaded", f"if loaded {SOURCE} if loaded {SINK}", results)
    server.command(f'setblock {SOURCE} enderio:creative_power[facing=south]'
                   '{IOConfig:{Modes:{east:"push"}},RedstoneControl:"active_with_signal"}')
    server.command(f'setblock {SINK} enderio:wired_charger[facing=south]'
                   '{IOConfig:{Modes:{west:"pull"}},Items:{Items:[{Slot:0,id:"enderio:octadic_capacitor",Count:1}]}}')
    for pos in (LEFT, RIGHT):
        server.command(f'setblock {pos} enderio:conduit{{Conduits:["enderio:energy"],Connections:[[]]}}')
    server.assert_state("two_stock_energy_conduits_discover_and_join_the_endpoints", topology(), results)
    for pos, side, insert in ((LEFT, "west", False), (RIGHT, "east", True)):
        server.command(f'data modify block {pos} Connections[0][{{Side:"{side}"}}].Config set value {connection(LANE, insert)}')
    before = measure(server, results, "initially_disabled", 0)
    check("the_wire_starts_with_an_empty_receiver", 0, before["after"]["receiver"], results)
    power_window(server, results, report, "fresh")


def reopened(server, results, report, phase):
    balance = report[phase + "Stopped"]["after"]["receiver"]
    server.assert_state(f"{phase}_restart_restores_the_conduit_network_and_exact_receiver_balance",
                        f'{topology()} if block {SOURCE} enderio:creative_power if block {SIGNAL} minecraft:air '
                        f'if data block {SOURCE} {{Energy:{{EnergyStored:0}},RedstoneControl:"active_with_signal"}} '
                        f'if data block {SINK} {{EnergyStored:{balance}}}', results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for key in ("java", "forge-server", "jar"):
        parser.add_argument(f"--{key}", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    write_sampler(directory)
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}

    def resume(server, results):
        reopened(server, results, report, "fresh")
        report["resumedInitiallyStopped"] = measure(server, results, "resumed_initially_disabled", 0)
        power_window(server, results, report, "resumed")

    try:
        run_stage(args, directory, report, "fresh", lambda server, results: fresh(server, results, report))
        run_stage(args, directory, report, "resume", resume)
        run_stage(args, directory, report, "reopened", lambda server, results: reopened(server, results, report, "resumed"))
        report["passed"] = True
    finally:
        result = directory / "creative-power-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Creative Power passed={report['passed']}, {len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
