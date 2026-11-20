#!/usr/bin/env python3
"""Verify bundled redstone gates across actual chunk eviction and cold starts."""

import argparse
import json
from pathlib import Path
import sys

sys.dont_write_bytecode = True
from packaged_cc_tweaked import configure, face, set_lever
from packaged_legacy_transfer_filters import wait_ticks
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage


REMOTE = "3362 80 8"
LEVER = "3361 80 8"
REMOTE_CHUNKS = "3360 0 3391 15"
LOCAL_CHUNKS = "3392 0 3455 15"
LOCAL = "3431 80 8"
INVERTED = "3434 80 8"
ENERGY = "3437 80 8"
WATER_SOURCE = "3431 80 7"
WATER_TARGET = "3431 80 9"
INVERTED_SOURCE = "3434 80 7"
INVERTED_TARGET = "3434 80 9"
ENERGY_SOURCE = "3437 80 7"
ENERGY_TARGET = "3437 80 9"


def water(amount):
    return f'{{Index:0,Contents:{{FluidName:"minecraft:water",Amount:{amount},Tag:{{PortBundledGate:244}}}}}}'


def tank(pos, amount):
    if amount:
        return f"if data block {pos} Fluids.Tanks[{water(amount)}]"
    return f"unless data block {pos} Fluids.Tanks[0].Contents.FluidName"


def fill(server, pos, amount):
    server.command(f"data modify block {pos} Fluids.Tanks set value [{water(amount)}]")


def port(server, pos, side, kind, *, insert=False, mode="never_active"):
    channels = ',insert_channel:"green",extract_channel:"green"' if kind == "fluid" else ""
    server.command(f'data modify block {pos} Connections[0][{{Side:"{side}"}}].Config set value '
                   f'{{type:"enderio:{kind}",is_insert:{int(insert)}b,is_extract:{int(not insert)}b,'
                   f'extract_redstone_control:"{mode}",extract_redstone_channel:"blue"{channels}}}')


def energy(source, target):
    return (f'if data block {ENERGY_SOURCE} {{EnergyStored:{source}}} '
            f'if data block {ENERGY_TARGET} {{EnergyStored:{target}}}')


def charge(server, amount):
    server.command(f"data merge block {ENERGY_SOURCE} {{EnergyStored:{amount}}}")


def stable(server, results, name, condition):
    wait_ticks(server, 24)
    server.assert_state(name, condition, results)


def local_balances(source=0, target=1600, inverse_source=1350, inverse_target=1120, source_fe=910, target_fe=900):
    return " ".join((tank(WATER_SOURCE, source), tank(WATER_TARGET, target),
                     tank(INVERTED_SOURCE, inverse_source), tank(INVERTED_TARGET, inverse_target), energy(source_fe, target_fe)))


def prepare(server, results):
    server.command(f"forceload add {REMOTE_CHUNKS}")
    server.command(f"forceload add {LOCAL_CHUNKS}")
    server.assert_state("both_fixture_ends_are_loaded", f"if loaded {REMOTE} if loaded {ENERGY}", results)
    server.command("gamerule doMobSpawning false")
    server.command("fill 3360 79 7 3439 79 9 minecraft:stone")
    set_lever(server, LEVER, True)
    server.command(f'fill {REMOTE} {ENERGY} enderio:conduit{{Conduits:["enderio:redstone"],Connections:[[]]}}')
    for pos in (WATER_SOURCE, WATER_TARGET, INVERTED_SOURCE, INVERTED_TARGET):
        server.command(f"setblock {pos} enderio:pressurized_fluid_tank")
    server.command(f'setblock {ENERGY_SOURCE} enderio:stirling_generator{{IOConfig:{{Modes:{{south:"push"}}}},'
                   'Items:{Size:2,Items:[{Slot:1,id:"enderio:basic_capacitor",Count:1b}]}}')
    server.command(f'setblock {ENERGY_TARGET} enderio:alloy_smelter{{IOConfig:{{Modes:{{north:"pull"}}}},'
                   'Items:{Size:5,Items:[{Slot:4,id:"enderio:basic_capacitor",Count:1b}]}}')
    for pos, variant, kind, mode in ((LOCAL, "ender_fluid", "fluid", "active_with_signal"),
                                      (INVERTED, "pressurized_fluid", "fluid", "active_without_signal"),
                                      (ENERGY, "energy", "energy", "active_without_signal")):
        server.command(f"setblock {pos} minecraft:air")
        server.command(f'setblock {pos} enderio:conduit{{Conduits:["enderio:{variant}","enderio:redstone"],Connections:[[],[]]}}')
        port(server, pos, "north", kind, mode=mode)
        port(server, pos, "south", kind, insert=True)
    configure(server, REMOTE, "west", channel="blue", insert=False)
    gates = " ".join(f'if data block {pos} Connections[0][{{Side:"north",Status:"connected_block",'
                     f'Config:{{extract_redstone_control:"{mode}",extract_redstone_channel:"blue"}}}}]'
                     for pos, mode in ((LOCAL, "active_with_signal"), (INVERTED, "active_without_signal"), (ENERGY, "active_without_signal")))
    server.assert_state("three_local_routes_listen_to_the_same_remote_blue_signal",
                        f'{face(REMOTE, "west")} {gates} if block {LEVER} minecraft:lever[powered=true]', results)


def exercise(server, results):
    prepare(server, results)
    fill(server, WATER_SOURCE, 860)
    fill(server, INVERTED_SOURCE, 1120)
    charge(server, 900)
    stable(server, results, "connected_power_opens_the_positive_gate_and_blocks_both_inverse_gates",
           local_balances(target=860, inverse_source=1120, inverse_target=0, source_fe=900, target_fe=0))

    server.command(f"forceload remove {REMOTE_CHUNKS}")
    server.assert_state("the_remote_signal_chunk_is_evicted_while_all_local_routes_stay_loaded",
                        f"unless loaded {REMOTE} if loaded {LOCAL} if loaded {ENERGY}", results, timeout=45)
    fill(server, WATER_SOURCE, 740)
    stable(server, results, "eviction_closes_the_positive_gate_and_releases_exact_inverse_balances",
           f"unless loaded {REMOTE} " + local_balances(source=740, target=860, inverse_source=0, source_fe=0))

    server.command(f"forceload add {REMOTE_CHUNKS}")
    server.assert_state("the_saved_powered_lever_returns_and_releases_the_waiting_740_mb",
                        f"if loaded {REMOTE} if block {LEVER} minecraft:lever[powered=true] "
                        + local_balances(inverse_source=0, source_fe=0), results)
    fill(server, INVERTED_SOURCE, 1350)
    charge(server, 910)
    stable(server, results, "recovered_power_blocks_both_new_inverse_batches_before_saving", local_balances())


def verify_cold_start(server, results, stage, *, release):
    server.assert_state(f"{stage}_both_inverse_routes_wait_at_the_original_balances",
                        f"if loaded {REMOTE} if block {LEVER} minecraft:lever[powered=true] {local_balances()}", results)
    stable(server, results, f"{stage}_no_transfer_after_all_routes_resume_ticking", local_balances())
    if release:
        set_lever(server, LEVER, False)
        server.assert_state("after_the_final_restart_removing_power_transfers_both_original_batches_once",
                            local_balances(inverse_source=0, inverse_target=2470, source_fe=0, target_fe=1810), results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for path in ("java", "forge-server", "jar"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "jarSha256": digest(directory / "mods/enderio.jar"), "assertions": [], "servers": []}
    try:
        run_stage(args, directory, report, "exercise", exercise)
        for index in range(3):
            stage = f"reopened-{index + 1}"
            run_stage(args, directory, report, stage, lambda server, results: verify_cold_start(server, results, stage, release=index == 2))
        report["passed"] = True
    finally:
        (directory / "bundled-gate-lifecycle-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
