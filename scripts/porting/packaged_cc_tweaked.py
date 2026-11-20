#!/usr/bin/env python3
"""Verify Ender IO bundled outputs on a packaged Forge server."""

import argparse
import json
from pathlib import Path
import shutil
import sys
import time

sys.dont_write_bytecode = True
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage


REMOTE = "2783 80 8"
LOCAL = "2831 80 8"
BRIDGE = "2832 80 8"
OUTPUT = "2833 80 8"
RECEIVER = "2834 80 8"
RELAY_HOST = "2834 81 8"
WHITE = "2831 80 7"
BLACK = "2782 80 8"
REMOTE_CHUNKS = "2768 0 2815 15"
CONDUIT = 'enderio:conduit{Conduits:["enderio:redstone"],Connections:[[]]}'
COMPUTER_ID = 94
BLACK_BIT = 1 << 15


def set_lever(server, pos, active):
    powered = str(active).lower()
    server.command(f"setblock {pos} minecraft:lever[face=floor,facing=south,powered={powered}]")


def face(pos, side, status="connected_block"):
    return f'if data block {pos} Connections[0][{{Side:"{side}",Status:"{status}"}}]'


def configure(server, pos, side, *, channel, insert):
    flag = "1b" if insert else "0b"
    opposite = "0b" if insert else "1b"
    server.command(f'data modify block {pos} Connections[0][{{Side:"{side}"}}].Config set value '
                   f'{{type:"enderio:redstone",is_insert:{flag},is_extract:{opposite},'
                   f'insert_channel:"white",extract_channel:"{channel}",is_strong_output_signal:0b}}')


def observe_setup(server, results):
    server.assert_state("route_and_its_three_endpoints_exist",
                        f'{face(REMOTE, "west")} {face(LOCAL, "north")} {face(OUTPUT, "east")} '
                        f'{face(REMOTE, "east", "connected_conduit")} {face(LOCAL, "east", "connected_conduit")} '
                        f'{face(OUTPUT, "west", "connected_conduit")}', results)


def prepare(server, results, receiver):
    server.command("gamerule doMobSpawning false")
    server.command("forceload add 2768 0 2847 15")
    server.assert_state("both_source_chunks_and_the_receiver_chunk_are_loaded",
                        f"if loaded {REMOTE} if loaded {LOCAL} if loaded {RECEIVER}", results)
    server.command("fill 2781 79 5 2837 79 11 minecraft:stone")
    set_lever(server, WHITE, False)
    set_lever(server, BLACK, False)
    if receiver:
        computer = receiver
        computer_pos = RECEIVER
        if receiver == "redstone_relay":
            server.command(f"setblock {RECEIVER} computercraft:redstone_relay[facing=east]")
            computer = "computer_normal"
            computer_pos = RELAY_HOST
        server.command(f'setblock {computer_pos} computercraft:{computer}[facing=east]'
                       f'{{ComputerId:{COMPUTER_ID},Label:"Ender IO bundled QA",On:1b}}')
        server.assert_state("real_computer_has_test_id", f'if data block {computer_pos} {{ComputerId:{COMPUTER_ID}}}', results)
    else:
        server.command(f"setblock {RECEIVER} minecraft:redstone_lamp")
    server.command(f"fill {REMOTE} {OUTPUT} {CONDUIT}")
    observe_setup(server, results)
    configure(server, LOCAL, "north", channel="white", insert=False)
    configure(server, REMOTE, "west", channel="black", insert=False)
    configure(server, OUTPUT, "east", channel="red", insert=True)
    server.assert_state("route_has_two_distinct_sources_and_white_analog_output",
                        f'if data block {LOCAL} Connections[0][{{Side:"north",Config:{{extract_channel:"white"}}}}] '
                        f'if data block {REMOTE} Connections[0][{{Side:"west",Config:{{extract_channel:"black"}}}}] '
                        f'if data block {OUTPUT} Connections[0][{{Side:"east",Config:{{is_insert:1b,is_extract:0b,insert_channel:"white"}}}}]',
                        results)


def samples(trace):
    if not trace.exists():
        return []
    return [json.loads(line) for line in trace.read_text().splitlines(keepends=True) if line.endswith("\n")]


def expect_computer(server, results, trace, name, bundled, analog, after):
    expected = {
        "bundled": bundled,
        "analog": analog,
        "white": bool(bundled & 1),
        "black": bool(bundled & BLACK_BIT),
        "red": False,
        "both": bundled == BLACK_BIT + 1,
        "otherSides": {side: 0 for side in ("front", "top", "bottom", "left", "right")},
    }
    deadline = time.monotonic() + 14
    while True:
        observations = samples(trace)
        observed = observations[-1] if observations else None
        if len(observations) > after and observed == expected:
            results.append({"assertion": name, "passed": True, "observed": observed, "sample": len(observations)})
            return
        if time.monotonic() >= deadline:
            results.append({"assertion": name, "passed": False, "expected": expected, "observed": observed,
                            "sample": len(observations)})
            for pos in (REMOTE, LOCAL, BRIDGE, OUTPUT, RECEIVER):
                server.command(f"data get block {pos}")
            raise AssertionError(f"{name}: expected {expected}, observed {observed}; see {trace}")
        time.sleep(0.1)


def expect_output(server, results, trace, name, bundled, analog, action=None):
    after = len(samples(trace)) if trace and action else 0
    if action:
        action()
    if trace:
        expect_computer(server, results, trace, name, bundled, analog, after)
    else:
        lit = str(analog > 0).lower()
        server.assert_state(name, f"if block {RECEIVER} minecraft:redstone_lamp[lit={lit}]", results)


def exercise(server, results, trace):
    expect_output(server, results, trace, "initial_output_is_clear", 0, 0)
    expect_output(server, results, trace, "white_activates_bit_zero", 1, 15,
                  lambda: set_lever(server, WHITE, True))
    expect_output(server, results, trace, "black_joins_without_changing_analog_strength", BLACK_BIT + 1, 15,
                  lambda: set_lever(server, BLACK, True))
    expect_output(server, results, trace, "black_keeps_bit_fifteen_when_white_clears", BLACK_BIT, 0,
                  lambda: set_lever(server, WHITE, False))
    expect_output(server, results, trace, "release_clears_the_last_bit", 0, 0,
                  lambda: set_lever(server, BLACK, False))
    set_lever(server, BLACK, True)
    expect_output(server, results, trace, "both_lanes_are_active_before_splitting", BLACK_BIT + 1, 15,
                  lambda: set_lever(server, WHITE, True))
    expect_output(server, results, trace, "removing_the_last_black_source_clears_only_its_bit", 1, 15,
                  lambda: server.command(f"setblock {BLACK} minecraft:air"))
    server.assert_state("black_endpoint_is_gone_but_the_bridge_still_connects",
                        f'{face(REMOTE, "west", "none")} {face(LOCAL, "east", "connected_conduit")}', results)

    def restore_black_source():
        set_lever(server, BLACK, True)
        server.assert_state("replacement_lever_reconnects", face(REMOTE, "west"), results)
        configure(server, REMOTE, "west", channel="black", insert=False)

    expect_output(server, results, trace, "restoring_black_recovers_both_lanes", BLACK_BIT + 1, 15, restore_black_source)
    expect_output(server, results, trace, "breaking_the_bridge_clears_the_output", 0, 0,
                  lambda: server.command(f"setblock {BRIDGE} minecraft:air"))
    server.assert_state("output_keeps_its_endpoint_after_graph_split",
                        f'{face(OUTPUT, "west", "none")} {face(OUTPUT, "east")}', results)
    expect_output(server, results, trace, "replacing_the_bridge_recovers_both_lanes", BLACK_BIT + 1, 15,
                  lambda: server.command(f"setblock {BRIDGE} {CONDUIT}"))
    observe_setup(server, results)


def exercise_chunk_lifecycle(server, results, trace):
    def evict_source():
        server.command(f"forceload remove {REMOTE_CHUNKS}")
        server.assert_state("remote_source_chunk_evicted_while_local_source_and_receiver_remain_loaded",
                            f"unless loaded {REMOTE} if loaded {LOCAL} if loaded {RECEIVER}", results, timeout=40)

    def reload_source():
        server.command(f"forceload add {REMOTE_CHUNKS}")
        server.assert_state("remote_source_chunk_returns_with_its_powered_lever",
                            f"if loaded {REMOTE} if block {BLACK} minecraft:lever[powered=true]", results)

    expect_output(server, results, trace, "eviction_removes_only_the_remote_black_bit", 1, 15, evict_source)
    expect_output(server, results, trace, "local_source_can_turn_off_while_remote_chunk_is_absent", 0, 0,
                  lambda: set_lever(server, WHITE, False))
    server.assert_state("remote_chunk_stays_absent_after_local_toggle", f"unless loaded {REMOTE}", results)
    expect_output(server, results, trace, "remote_chunk_rejoins_and_restores_black_only", BLACK_BIT, 0, reload_source)
    expect_output(server, results, trace, "both_bits_rejoin_after_reactivating_the_local_lever", BLACK_BIT + 1, 15,
                  lambda: set_lever(server, WHITE, True))
    observe_setup(server, results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--cc-jar", type=Path, help="Install CC:Tweaked. Without it, exercise the same route with a vanilla lamp.")
    parser.add_argument("--receiver", default="computer_normal",
                        choices=("computer_normal", "computer_advanced", "computer_command",
                                 "turtle_normal", "turtle_advanced", "redstone_relay"),
                        help="Choose the CC:Tweaked block that reads the conduit.")
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")

    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    trace = None
    if args.cc_jar:
        shutil.copyfile(args.cc_jar, directory / "mods/computercraft.jar")
        computer = directory / WORLD / "computercraft" / "computer" / str(COMPUTER_ID)
        computer.mkdir(parents=True)
        shutil.copyfile(Path(__file__).with_name("cc_tweaked_bundled.lua"), computer / "startup.lua")
        if args.receiver == "redstone_relay":
            (computer / "port-relay").touch()
        trace = computer / "port-input.jsonl"

    receiver = args.receiver if trace is not None else None
    report = {"passed": False, "forge": FORGE, "receiver": f"computercraft:{receiver}" if receiver else "minecraft:redstone_lamp",
              "ccTweakedSha256": digest(args.cc_jar) if args.cc_jar else None,
              "jarSha256": digest(directory / "mods/enderio.jar"), "assertions": [], "servers": []}
    try:
        def fresh(server, results):
            prepare(server, results, receiver)
            exercise(server, results, trace)
            exercise_chunk_lifecycle(server, results, trace)

        def reopened(server, results):
            observe_setup(server, results)
            expect_output(server, results, trace, "reopened_route_recalculates_both_channels", BLACK_BIT + 1, 15)
            expect_output(server, results, trace, "reopened_route_clears_and_reports_only_black", BLACK_BIT, 0,
                          lambda: set_lever(server, WHITE, False))

        run_stage(args, directory, report, "fresh", fresh)
        if trace:
            trace.rename(directory / "fresh-computer-trace.jsonl")
        run_stage(args, directory, report, "reopened", reopened)
        if trace:
            trace.rename(directory / "reopened-computer-trace.jsonl")
        report["passed"] = True
    finally:
        path = directory / "cc-tweaked-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Bundled redstone passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
