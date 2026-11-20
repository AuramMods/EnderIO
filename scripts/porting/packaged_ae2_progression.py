#!/usr/bin/env python3
"""Craft stock AE2 conduits in packaged survival and exercise the live channel limits."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_energy_progression import conduit_stack
from packaged_fluid_progression import CHUNKS, LEFT, RIGHT, TABLE
from packaged_item_progression import bundle
from packaged_legacy_migration import assert_probe
from packaged_legacy_transfer_filters import wait_ticks
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage
from packaged_starter_progression import BINDER, PLAYER


MATERIALS = ((BINDER, 19), ("ae2:fluix_glass_cable", 4), ("ae2:orange_covered_cable", 4),
             ("ae2:blue_covered_dense_cable", 4))
ROUTES = (("me", "me"), ("dense_me", "me"), ("dense_me", "dense_me"))


def prepare(server, results):
    for command in ("gamerule doMobSpawning false", "gamerule doDaylightCycle false", "gamerule spawnRadius 0", "time set noon",
                    f"forceload add {CHUNKS}", "fill 3275 79 5 3283 79 15 minecraft:stone_bricks", "setworldspawn 3278 80 14",
                    f"setblock {TABLE} minecraft:crafting_table", "setblock 3277 80 8 ae2:creative_energy_cell",
                    "setblock 3278 80 8 ae2:controller"):
        server.command(command)
    for y, z, face in ((80, 7, "north"), (80, 9, "south"), (81, 8, "north"), (79, 8, "north")):
        server.command(f"fill 3281 {y} {z} 3288 {y} {z} ae2:drive[facing={face}]")
    server.command("setblock 3289 80 8 ae2:drive[facing=east]")
    wait_ticks(server, 5)
    server.command('fill 3281 80 8 3288 80 8 enderio:conduit{Conduits:["enderio:dense_me"],Connections:[[]]}')
    server.assert_state("the_standard_and_dense_trunk_positions_start_empty_across_the_chunk_boundary",
                        f"if loaded {LEFT} if loaded {RIGHT} if block {LEFT} minecraft:air if block {RIGHT} minecraft:air", results)


def check_channels(server, results, name, tiers, drives=33):
    server.assert_state(f"{name}_has_the_expected_stock_variants",
                        f"{bundle(LEFT, tiers[0])} {bundle(RIGHT, tiers[1])}", results)
    dense = [str(tier == "dense_me").lower() for tier in tiers]
    assert_probe(server, name, f"port_storage_native check_channels {' '.join(dense)} {drives}",
                 "Native AE2 channel counts verified", results)


def craft_and_place(server, results, client, output):
    for slot, (item, count) in enumerate(MATERIALS, 1):
        server.command(f"item replace entity {PLAYER} hotbar.{slot} with {item} {count}")
    server.command(f"tp {PLAYER} 3278.5 80 14.5 180 20")
    (output / "server-table").touch()
    wait_signal(client, output, "crafted")
    spares = ",".join(f'{{id:"{item}",Count:1b}}' for item, _ in MATERIALS)
    server.assert_state("three_stock_recipe_crafts_leave_exact_materials_and_native_variant_stacks",
                        f'if data entity {PLAYER} {{Inventory:[{conduit_stack("me", 6)},{conduit_stack("dense_me", 3)},{spares}]}}', results)
    server.command(f"tp {PLAYER} 3280.5 80 11.5 180 17")
    (output / "server-upgrade-0").touch()
    wait_signal(client, output, "upgraded-0")
    for step, name, tiers in ((1, "standard_route_carries_eight_of_thirty_three_drives", ROUTES[0]),
                              (2, "a_single_standard_segment_still_limits_the_mixed_route_to_eight", ROUTES[1]),
                              (3, "both_crafted_dense_segments_carry_thirty_two_of_thirty_three_drives", ROUTES[2])):
        (output / f"server-upgrade-{step}").touch()
        wait_signal(client, output, f"upgraded-{step}")
        check_channels(server, results, name, tiers)
    server.assert_state("both_survival_upgrades_refund_their_standard_conduits",
                        f'if data entity {PLAYER} {{Inventory:[{conduit_stack("me", 6)},{conduit_stack("dense_me", 1)}]}}', results)
    assert_probe(server, "one_active_drive_is_removed_from_the_fully_loaded_grid",
                 "port_storage_native release_channel", "Removed one powered AE2 drive", results)
    check_channels(server, results, "the_waiting_drive_receives_the_freed_channel", ROUTES[2], drives=32)


def run_client(args, server, report):
    output = ROOT / "build/port-evidence" / f"{args.name}-client"
    summary = output / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "ae2-progression", "--server", f"127.0.0.1:{args.port}", "--output", str(output), "--timeout", "195"]
    with (server.directory / "ae2-progression-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            craft_and_place(server, report["assertions"], client, output)
            (output / "server-done").touch()
            if client.wait(timeout=25) != 0:
                raise AssertionError(f"AE2 conduit progression client failed; see {output}")
            frame = json.loads(summary.read_text())["client"]["captures"]["upgraded-conduit-route"]
            for mod in ("enderio", "ae2", "guideme", "refinedstorage"):
                artifact = Path(frame["loadedMods"][mod]["file"])
                if digest(artifact) != report["jarHashes"][artifact.name]:
                    raise AssertionError(f"AE2 progression peers loaded different {mod} jars")
        finally:
            stop_client(client)
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def reload_chunks(server, results):
    server.command("setworldspawn 0 80 0")
    server.command(f"forceload remove {CHUNKS}")
    server.assert_state("both_ae2_trunk_chunks_are_evicted", f"unless loaded {LEFT} unless loaded {RIGHT}", results, timeout=45)
    server.command(f"forceload add {CHUNKS}")
    check_channels(server, results, "the_crafted_dense_route_reopens_with_all_remaining_drives_online", ROUTES[2], drives=32)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for path in ("java", "forge-server", "jar", "launch", "probe"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    parser.add_argument("--mod", action="append", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    for mod in [*args.mod, args.probe]:
        shutil.copyfile(mod, directory / "mods" / mod.name)
    report = {"passed": False, "assertions": [], "servers": [], "jarHashes": {
        mod.name: digest(mod) for mod in sorted((directory / "mods").glob("*.jar"))}}

    def exercise(server, results):
        prepare(server, results)
        run_client(args, server, report)
        reload_chunks(server, results)

    def restart(server, results):
        check_channels(server, results, "dedicated_restart_recovers_all_thirty_two_drive_channels", ROUTES[2], drives=32)

    try:
        run_stage(args, directory, report, "exercise", exercise)
        run_stage(args, directory, report, "reopened", restart)
        report["passed"] = True
    finally:
        (directory / "ae2-progression-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
