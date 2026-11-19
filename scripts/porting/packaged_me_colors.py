#!/usr/bin/env python3
"""Verify data-pack ME conduit colors on two powered AE2 routes in packaged Forge."""

import argparse
import json
from pathlib import Path
import shutil
import sys

sys.dont_write_bytecode = True
from packaged_legacy_migration import assert_probe, storage_operation
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage


NAMESPACE = "enderio_modded_conduits_tests"
COLORS = "colored-me-conduits"
CHUNKS = "1104 0 1135 15"
MATCHING = 4
MIXED = 8


def install_datapack(directory):
    pack = directory / WORLD / "datapacks" / COLORS
    registry = Path("data") / NAMESPACE / "enderio/conduit"
    fixtures = ROOT / "enderio-modded-conduits/src/gametest/resources" / registry
    (pack / registry).mkdir(parents=True)
    (pack / "pack.mcmeta").write_text(
        json.dumps({"pack": {"pack_format": 15, "description": "Ender IO test ME conduit colors"}}) + "\n"
    )
    for name in ("red_me.json", "blue_me.json"):
        shutil.copyfile(fixtures / name, pack / registry / name)
    with (directory / "server.properties").open("a") as properties:
        properties.write(f"initial-enabled-packs=vanilla,file/{COLORS}\n")


def place(server, results, name, route, head, red):
    storage_operation(server, name, f"place_colored_me {route} {str(head).lower()} {str(red).lower()}",
                      "Native colored ME conduit placed", results)


def check(server, results, name, route, connected):
    head = f"1119 80 {route}"
    tail = f"1120 80 {route}"
    tail_color = "red" if connected else "blue"
    link = "if" if connected else "unless"
    server.assert_state(f"{name}_saved_ids_and_link",
                        f'if data block {head} {{Conduits:["{NAMESPACE}:red_me"]}} '
                        f'if data block {tail} {{Conduits:["{NAMESPACE}:{tail_color}_me"]}} '
                        f'{link} data block {head} Connections[0][{{Side:"east",Status:"connected_conduit"}}] '
                        f'{link} data block {tail} Connections[0][{{Side:"west",Status:"connected_conduit"}}]', results)
    assert_probe(server, f"{name}_ae2_grid_and_tagged_cell", f"port_storage_native check_colored_route {route} {str(connected).lower()}",
                 "Native colored ME route verified", results)


def load(server, results, name):
    server.command(f"forceload add {CHUNKS}")
    server.assert_state(name, f"if loaded 1119 80 {MATCHING} if loaded 1120 80 {MIXED}", results)


def exercise(server, results):
    load(server, results, "both_route_chunks_loaded")
    server.command("fill 1117 79 3 1122 79 9 minecraft:stone_bricks")
    for route in (MATCHING, MIXED):
        server.command(f"setblock 1117 80 {route} ae2:creative_energy_cell")
        server.command(f"setblock 1118 80 {route} ae2:controller")
        server.command(f"setblock 1121 80 {route} ae2:drive")
        server.command(f"setblock 1122 80 {route} ae2:creative_energy_cell")
        place(server, results, f"route_{route}_native_head_placement", route, True, True)
        place(server, results, f"route_{route}_native_tail_placement", route, False, route == MATCHING)
    storage_operation(server, "seed_separate_native_cells", "seed_colored_cells", "Native colored ME cells loaded", results)
    check(server, results, "matching_red_reaches_cell", MATCHING, True)
    check(server, results, "red_blue_keeps_powered_cells_separate", MIXED, False)

    for red, name in ((True, "red_replacement_exposes_cell"), (False, "blue_replacement_isolates_cell_again")):
        server.command(f"setblock 1120 80 {MIXED} minecraft:air")
        place(server, results, name + "_placement", MIXED, False, red)
        check(server, results, name, MIXED, red)

    server.command("save-all flush")
    server.command(f"forceload remove {CHUNKS}")
    server.assert_state("both_route_chunks_unloaded", f"unless loaded 1119 80 {MATCHING} unless loaded 1120 80 {MIXED}",
                        results, timeout=40)
    load(server, results, "both_route_chunks_reloaded")
    check(server, results, "matching_red_reaches_cell_after_chunk_reload", MATCHING, True)
    check(server, results, "red_blue_stays_split_after_chunk_reload", MIXED, False)


def verify(server, results):
    load(server, results, "both_route_chunks_loaded_after_restart")
    check(server, results, "matching_red_reaches_cell_after_restart", MATCHING, True)
    check(server, results, "red_blue_stays_split_after_restart", MIXED, False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for path in ("java", "forge-server", "jar", "probe"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    parser.add_argument("--mod", action="append", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    install_datapack(directory)
    for mod in [*args.mod, args.probe]:
        shutil.copyfile(mod, directory / "mods" / mod.name)
    report = {"passed": False, "assertions": [], "servers": [],
              "jarHashes": {mod.name: digest(mod) for mod in sorted((directory / "mods").glob("*.jar"))}}
    try:
        run_stage(args, directory, report, "exercise", exercise)
        run_stage(args, directory, report, "restart", verify)
        report["passed"] = True
    finally:
        (directory / "me-colors-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
