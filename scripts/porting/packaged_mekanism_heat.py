#!/usr/bin/env python3
"""Measure a migrated heat route before and after physical reconnection and restart."""

import argparse
import json
from pathlib import Path
import shutil
import sys

sys.dont_write_bytecode = True
from packaged_legacy_migration import assert_probe
from packaged_mekanism_controls import operation
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage


BRIDGE = "1152 80 12"


def observe(server, results, name, connected):
    operation(server, "start_unequal_heat", "Native unequal heat pulse started with capacities")
    state = "connected" if connected else "split"
    assert_probe(server, name, f"port_mekanism_native unequal_heat_{state}",
                 "Native unequal heat matched the route", results)


def exercise(server, results, restart=False):
    server.command("forceload add 1136 0 1167 15")
    server.assert_state("heat_chunks_reloaded" if restart else "heat_chunks_loaded",
                        "if loaded 1150 80 12 if loaded 1153 80 12", results)
    if restart:
        observe(server, results, "replaced_heat_route_survives_dedicated_restart", True)
        return

    server.command("setblock 1150 80 12 mekanism:resistive_heater")
    observe(server, results, "the_larger_native_heater_does_not_overshoot", True)
    server.command(f"setblock {BRIDGE} minecraft:air")
    observe(server, results, "physical_break_stops_the_native_route", False)
    operation(server, "place_heat_bridge", "Native heat bridge placed")
    server.assert_state("bridge_item_replaces_the_original_conduit",
                        f'if data block {BRIDGE} {{Conduits:["enderio:heat"]}} '
                        f'if data block {BRIDGE} Connections[0][{{Side:"east",Status:"connected_block"}}]', results)
    observe(server, results, "native_placement_restores_the_heat_route", True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for path in ("java", "forge-server", "jar", "world", "probe", "mekanism"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    shutil.copytree(args.world, directory / WORLD)
    for mod in (args.mekanism, args.probe):
        shutil.copyfile(mod, directory / "mods" / mod.name)
    report = {"passed": False, "assertions": [], "servers": [],
              "jarHashes": {mod.name: digest(mod) for mod in sorted((directory / "mods").glob("*.jar"))}}
    try:
        run_stage(args, directory, report, "exercise", exercise)
        run_stage(args, directory, report, "restart", lambda server, results: exercise(server, results, restart=True))
        report["passed"] = True
    finally:
        (directory / "mekanism-heat-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
