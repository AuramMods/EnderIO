#!/usr/bin/env python3
"""Exercise addon filter and heat controls on a stock or converted Forge fixture."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys
import time

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_energy_progression import conduit_stack
from packaged_item_progression import bundle
from packaged_laserio import Lane, connection
from packaged_laserio_editor import gas
from packaged_legacy_migration import assert_probe
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage


PLAYER = "EnderIOQA"
CHEMICAL = "1151 80 0"
HEAT = "1151 80 12"
FILTER_DATA = (f'{CHEMICAL} Connections[0][{{Side:"west"}}].Inventory[0]'
               '.tag."enderio:data"."enderio:chemical_filter"')
SAMPLES = (("gas", "oxygen"), ("infuse_type", "redstone"), ("pigment", "red"), ("slurry", "dirty_iron"))
STOCK_MATERIALS = (("mekanism:ingot_osmium", 2), ("minecraft:paper", 5),
                   ("enderio:conduit_binder", 7), ("mekanism:advanced_thermodynamic_conductor", 4))


def operation(server, action, message):
    response = server.command(f"port_mekanism_native {action}")
    if message not in response:
        raise AssertionError(f"{action} failed: {response}")


def check_filter(server, phase, results, samples):
    conditions = [f"if data block {FILTER_DATA}{{isInvert:1b}}"]
    for slot, (family, chemical) in enumerate(samples):
        conditions.append(f'if data block {FILTER_DATA}.chemicals[{{index:{slot},value:{{chemical_type:"{family}",'
                          f'id:"mekanism:{chemical}",amount:1450s}}}}]')
    server.assert_state(f"{phase}_installed_deny_list_contains_its_ordered_samples", " ".join(conditions), results)


def prepare_stock(server, results):
    server.command("gamerule doMobSpawning false")
    server.command("setblock 1151 80 5 minecraft:crafting_table")
    for pos, mode in (("1150 80 0", 4), ("1153 80 0", 1)):
        server.command(f"setblock {pos} mekanism:ultimate_chemical_tank")
        sides = ",".join(f"side{i}:{mode}" for i in range(6))
        server.command(f"data modify block {pos} componentConfig.config2 set value {{{sides}}}")
    for pos, side, insert in ((CHEMICAL, "west", False), ("1152 80 0", "east", True)):
        server.command(f'setblock {pos} enderio:conduit{{Conduits:["enderio:chemical"],Connections:[[]]}}')
        server.command(f'data modify block {pos} Connections[0][{{Side:"{side}"}}].Config set value '
                       f'{connection(Lane("chemical", 0), insert)}')
    server.command("setblock 1150 80 12 mekanism:resistive_heater")
    server.command("setblock 1153 80 12 mekanism:basic_thermodynamic_conductor")
    server.assert_state("heat_endpoints_are_native_and_the_crafted_positions_are_empty",
                        f"if block 1150 80 12 mekanism:resistive_heater if block {HEAT} minecraft:air "
                        "if block 1152 80 12 minecraft:air if block 1153 80 12 mekanism:basic_thermodynamic_conductor", results)


def craft_stock(server, results, client, output):
    for slot, (item, count) in enumerate(STOCK_MATERIALS, 3):
        server.command(f"item replace entity {PLAYER} hotbar.{slot} with {item} {count}")
    server.command(f"tp {PLAYER} 1151.5 80 7.5 180 16")
    (output / "server-table").touch()
    wait_signal(client, output, "crafted")
    spares = ",".join(f'{{id:"{item}",Count:1b}}' for item, _ in STOCK_MATERIALS)
    server.assert_state("both_stock_crafts_consume_exact_materials_and_return_their_full_results",
                        f'if data entity {PLAYER} {{Inventory:[{{id:"enderio:basic_chemical_filter",Count:1b}},'
                        f'{conduit_stack("heat", 3)},{spares}]}}', results)
    server.command(f"tp {PLAYER} 1151.5 80 10.5 0 16")
    (output / "server-place-heat").touch()
    wait_signal(client, output, "heat-placed")
    server.assert_state("two_crafted_heat_parts_build_the_joined_route_and_one_stays_in_inventory",
                        f'{bundle(HEAT, "heat")} {bundle("1152 80 12", "heat")} '
                        f'if data block {HEAT} Connections[0][{{Side:"west",Status:"connected_block"}}] '
                        f'if data block {HEAT} Connections[0][{{Side:"east",Status:"connected_conduit"}}] '
                        'if data block 1152 80 12 Connections[0][{Side:"east",Status:"connected_block"}] '
                        f'if data entity {PLAYER} {{Inventory:[{conduit_stack("heat", 1)}]}}', results)
    for pos, side, insert in ((HEAT, "west", False), ("1152 80 12", "east", True)):
        server.command(f'data modify block {pos} Connections[0][{{Side:"{side}"}}].Config set value '
                       f'{{type:"enderio:heat",is_insert:{int(insert)}b,is_extract:{int(not insert)}b,'
                       'extract_redstone_control:"always_active",extract_redstone_channel:"orange"}')
    server.command(f'item replace entity {PLAYER} hotbar.7 with mekanism:basic_chemical_tank'
                   f'{{mekData:{{GasTanks:[{gas("oxygen", 1450)}]}}}}')


def observe_stock_heat(server, results, name, connected):
    operation(server, "start_unequal_heat", "Native unequal heat pulse started with capacities")
    state = "connected" if connected else "split"
    assert_probe(server, name, f"port_mekanism_native unequal_heat_{state}",
                 "Native unequal heat matched the route", results)


def exercise(args, server, results, report, phase):
    reopen = phase == "verify"
    server.command("forceload add 1136 0 1167 15")
    server.assert_state(f"{phase}_migration_chunks_loaded", f"if loaded {CHEMICAL} if loaded 1152 80 12", results)
    if not reopen:
        server.command("gamerule doDaylightCycle false")
        server.command("time set noon")
        server.command("gamerule spawnRadius 0")
        server.command("fill 1148 79 -1 1155 79 16 minecraft:stone_bricks")
        server.command("fill 1148 80 0 1148 81 15 minecraft:bookshelf")
        server.command("setworldspawn 1151 80 6")
        if args.stock:
            prepare_stock(server, results)
    output = ROOT / "build/port-evidence" / f"{args.name}-{phase}"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
               "--launch", str(args.launch.resolve()), "--scenario", "mekanism-crafting" if args.stock else "mekanism-controls", "--output", str(output),
               "--server", f"127.0.0.1:{args.port}", "--multiplayer-phase", phase, "--timeout", "170"]
    with (server.directory / f"mekanism-client-{phase}.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            if not reopen:
                server.command(f"clear {PLAYER}")
                if args.stock:
                    craft_stock(server, results, client, output)
                else:
                    server.command(f"item replace entity {PLAYER} hotbar.0 with enderio:basic_chemical_filter")
                    operation(server, "samples", "Four native chemical sample tanks created")
            server.command(f"tp {PLAYER} 1151.5 80 2.5 180 16")
            (output / "server-chemical").touch()
            wait_signal(client, output, "chemical-done")
            server.command(f"data get block {CHEMICAL} Connections")
            check_filter(server, phase, results, SAMPLES[:1] if args.stock else SAMPLES)
            if not reopen:
                operation(server, "filter_oxygen", "Native chemical filter batch seeded")
                time.sleep(2)
                assert_probe(server, "oxygen_refused_by_the_live_deny_list", "port_mekanism_native filter_blocked",
                             "Native chemical filter matched the exact gas balances", results)
                operation(server, "filter_hydrogen", "Native chemical filter batch seeded")
            assert_probe(server, f"{phase}_exact_hydrogen_delivery", "port_mekanism_native filter_delivered",
                         "Native chemical filter matched the exact gas balances", results)
            if args.stock:
                observe_stock_heat(server, results,
                                   "restarted_saved_pause_keeps_the_target_at_ambient" if reopen else "crafted_line_warms_the_native_target",
                                   not reopen)
            server.command(f"tp {PLAYER} 1151.5 80 10.5 0 16")
            (output / "server-heat").touch()
            wait_signal(client, output, "heat-edited")
            mode = "always_active" if reopen else "never_active"
            server.assert_state(f"{phase}_native_heat_picker_saves_{mode}", f'if data block {HEAT} '
                                f'Connections[0][{{Side:"west",Config:{{is_insert:0b,is_extract:1b,'
                                f'extract_redstone_control:"{mode}",extract_redstone_channel:"orange"}}}}]', results)
            if args.stock:
                observe_stock_heat(server, results,
                                   "reopened_menu_restores_heat_transfer" if reopen else "native_menu_pause_keeps_the_target_at_ambient",
                                   reopen)
            else:
                operation(server, "heat_pulse", "Native heat pulse prepared")
                if not reopen:
                    time.sleep(2)
                action = "heat_delivered" if reopen else "heat_blocked"
                assert_probe(server, f"{phase}_native_{action}", f"port_mekanism_native {action}",
                             "Native heat destination matched the selected control", results)
            if client.wait(timeout=25) != 0:
                raise AssertionError(f"Mekanism client failed during {phase}, see {output}")
            summary = json.loads((output / "summary.json").read_text())
            mods = summary["client"]["captures"]["chemical-reopened" if reopen else "chemical-deny-list"]["loadedMods"]
            for mod in ("enderio", "mekanism"):
                path = Path(mods[mod]["file"])
                if digest(path) != report["jarHashes"][path.name]:
                    raise AssertionError(f"Client and server loaded different {mod} artifacts")
        finally:
            stop_client(client)
            summary = output / "summary.json"
            report["clients"][phase] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True, type=Path)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--launch", required=True, type=Path)
    fixture = parser.add_mutually_exclusive_group(required=True)
    fixture.add_argument("--world", type=Path, help="Copy a converted Forge 6.2 world")
    fixture.add_argument("--stock", action="store_true", help="Use a fresh world and craft both items in survival")
    parser.add_argument("--probe", required=True, type=Path)
    parser.add_argument("--mekanism", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    if args.world:
        shutil.copytree(args.world, directory / WORLD)
    for mod in (args.mekanism, args.probe):
        shutil.copyfile(mod, directory / "mods" / mod.name)
    report = {"passed": False, "assertions": [], "servers": [], "clients": {},
              "jarHashes": {mod.name: digest(mod) for mod in sorted((directory / "mods").glob("*.jar"))}}
    try:
        for phase in ("exercise", "verify"):
            run_stage(args, directory, report, phase, lambda server, results: exercise(args, server, results, report, phase))
        report["passed"] = True
    finally:
        (directory / "mekanism-control-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
