#!/usr/bin/env python3
"""Craft and compare wireless charger antennas from original stock soul components."""

import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_server_smoke import ROOT, digest
from packaged_solar_charging import check
from packaged_spawners import WORLD, prepare_profile, run_stage
from packaged_starter_progression import PLAYER
from saved_world import read_data_file


X, Z = 3160, 23
CHARGER = f"{X} 80 {Z}"
ANTENNA = f"{X} 81 {Z}"
BASE = "enderio:wireless_charger"
STANDARD = f"{BASE}_antenna"
ADVANCED = f"{STANDARD}_advanced"
STAFF = "enderio:staff_of_travelling"
STAFF_DATA = '{PortAntenna:187,"enderio:data":{"enderio:energy":0}}'
CAP = "enderio:basic_capacitor"
SENTIENT = "enderio:sentient_ender"
START_FE = 32000


def write_measurements(world):
    pack = world / "datapacks/port_antenna"
    functions = pack / "data/port_antenna/functions"
    functions.mkdir(parents=True)
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 15, "description": "Wireless range measurements"}}))
    lines = ["data modify storage port_antenna:sample value set value {}",
             "execute store result storage port_antenna:sample value.tick int 1 run time query gametime",
             f"execute store result storage port_antenna:sample value.charger int 1 run data get block {CHARGER} EnergyStored",
             f'execute store result storage port_antenna:sample value.staff int 1 run data get entity {PLAYER} '
             'Inventory[{Slot:0b}].tag."enderio:data"."enderio:energy"']
    (functions / "read.mcfunction").write_text("\n".join(lines) + "\n")
    (functions / "start.mcfunction").write_text(f"data merge block {CHARGER} {{EnergyStored:{START_FE}}}\nfunction port_antenna:read\n")


def sample(server, start=False):
    server.command("function port_antenna:" + ("start" if start else "read"))
    reply = server.command("data get storage port_antenna:sample value")
    fields = {key: int(value) for key, value in re.findall(r"(\w+): (-?\d+)", reply)}
    if set(fields) != {"tick", "charger", "staff"}:
        raise AssertionError(reply)
    return fields


def measure(server, report, label, distance, expected):
    results = report["assertions"]
    target = f"{X + distance + 0.5} 80 {Z + 0.5}"
    server.command(f"tp {PLAYER} {target} 90 0")
    server.assert_state(label + "_player_at_measured_offset",
                        f"positioned {target} if entity @a[name={PLAYER},distance=..0.2]", results)
    begin = sample(server, start=True)
    time.sleep(0.45)
    end = sample(server)
    server.command(f"data merge block {CHARGER} {{EnergyStored:0}}")
    received, ticks = end["staff"] - begin["staff"], end["tick"] - begin["tick"]
    check(label + "_receives_the_expected_charge", expected, received > 0, results)
    check(label + "_conserves_energy_including_upkeep", START_FE, end["charger"] + received + 2 * ticks, results)
    report["measurements"][label] = {"blocks": distance, "ticks": ticks, "staffGain": received,
                                    "staffObserved": end["staff"], "chargerObserved": end["charger"]}


def range_state(distance, antenna):
    return (f"if block {CHARGER} {BASE} if block {ANTENNA} {antenna} "
            f"if data block {CHARGER} {{ActionRange:{{range:{distance}}},Items:{{Items:[{{Slot:0,id:\"{CAP}\",Count:1}}]}}}}")


def prepare(server, results):
    server.command("forceload add 3152 16 3215 31")
    server.assert_state("workshop_and_range_chunks_are_loaded", f"if loaded {CHARGER} if loaded 3212 80 {Z}", results)
    server.command("fill 3156 79 21 3213 79 29 minecraft:stone")
    server.command("setblock 3159 80 25 minecraft:crafting_table")
    server.command(f"setblock {X + 1} 81 {Z} minecraft:stone")
    server.command("gamerule doLimitedCrafting true")
    server.command("time set noon")


def supply(server, results):
    server.assert_state("the_original_binder_output_is_in_the_survival_inventory",
                        f'if data entity {PLAYER} Inventory[{{id:"{SENTIENT}",Count:1b}}]', results)
    items = (("enderio:conductive_alloy_ingot", 11), ("enderio:pulsating_alloy_ingot", 2), ("enderio:vibrant_alloy_ingot", 2),
             ("enderio:ender_resonator", 2), ("enderio:void_chassis", 1), (CAP, 1))
    for slot, (item, count) in enumerate(items):
        server.command(f"item replace entity {PLAYER} inventory.{slot} with {item} {count}")
    server.command(f"item replace entity {PLAYER} hotbar.0 with {STAFF}{STAFF_DATA}")
    server.command(f"item replace entity {PLAYER} hotbar.1 with minecraft:iron_pickaxe")


def at_charger(server, evidence, signal):
    server.command(f"tp {PLAYER} {X + 0.5} 80 {Z + 2.5} 180 18")
    (evidence / f"server-{signal}").touch()


def install(server, client, evidence, results, label, distance, antenna):
    at_charger(server, evidence, "place-" + label)
    wait_signal(client, evidence, "checked-" + label)
    server.assert_state(label + "_survival_build_sets_the_stock_range_and_uses_the_exact_item",
                        f"{range_state(distance, antenna)} unless data entity {PLAYER} Inventory[{{id:\"{BASE if label == 'base' else antenna}\"}}]",
                        results)


def exercise(server, client, evidence, report):
    results = report["assertions"]
    supply(server, results)
    server.command(f"tp {PLAYER} 3159.5 80 27.5 180 19")
    (evidence / "server-craft").touch()
    wait_signal(client, evidence, "crafted")
    expected = " ".join(f'if data entity {PLAYER} Inventory[{{id:"{item}",Count:1b}}]' for item in (BASE, STANDARD, ADVANCED))
    server.assert_state("survival_crafts_three_original_blocks_and_spends_sentient_ender",
                        f'{expected} unless data entity {PLAYER} Inventory[{{id:"{SENTIENT}"}}]', results)
    install(server, client, evidence, results, "base", 16, "minecraft:air")
    measure(server, report, "base_at_8", 8, True)
    measure(server, report, "base_at_24", 24, False)
    install(server, client, evidence, results, "standard", 32, STANDARD)
    measure(server, report, "standard_at_24", 24, True)
    measure(server, report, "standard_at_40", 40, False)
    at_charger(server, evidence, "remove-standard")
    wait_signal(client, evidence, "removed-standard")
    server.assert_state("native_removal_returns_the_base_range", range_state(16, "minecraft:air"), results)
    measure(server, report, "removed_standard_at_24", 24, False)
    install(server, client, evidence, results, "advanced", 48, ADVANCED)
    measure(server, report, "advanced_at_40", 40, True)
    measure(server, report, "advanced_at_52", 52, False)
    report["preRestartStaff"] = sample(server)["staff"]


def verify(server, client, evidence, report):
    results = report["assertions"]
    server.assert_state("the_original_advanced_antenna_capacitor_and_range_survive_restart",
                        f"{range_state(48, ADVANCED)} if data block {CHARGER} {{EnergyStored:0}}", results)
    check("the_original_staff_restores_its_exact_previous_charge", report["preRestartStaff"], sample(server)["staff"], results)
    at_charger(server, evidence, "place-advanced")
    wait_signal(client, evidence, "checked-advanced")
    measure(server, report, "restored_advanced_at_40", 40, True)
    report["finalStaff"] = sample(server)["staff"]


def run_client(args, server, report, *, restart):
    phase = "verify" if restart else "exercise"
    evidence = ROOT / "build/port-evidence" / f"{args.name}-{phase}-client"
    summary = evidence / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "antenna-progression", "--multiplayer-phase", phase, "--server", f"127.0.0.1:{args.port}",
               "--output", str(evidence), "--timeout", "200"]
    with (server.directory / f"antenna-client-{phase}.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            (verify if restart else exercise)(server, client, evidence, report)
            (evidence / "server-done").touch()
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"Wireless antenna client failed; see {evidence}")
            for capture in json.loads(summary.read_text())["client"]["captures"].values():
                if digest(Path(capture["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                    raise AssertionError("Client and server loaded different Ender IO jars")
        finally:
            stop_client(client)
            report["clients"][phase] = json.loads(summary.read_text()) if summary.exists() else None


def verify_saved_staff(directory, report):
    player, = (directory / WORLD / "playerdata").glob("*.dat")
    staff, = (stack for stack in read_data_file(player)["Inventory"] if stack["id"] == STAFF)
    check("the_final_staff_charge_is_saved", report["finalStaff"], staff["tag"]["enderio:data"]["enderio:energy"], report["assertions"])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    for key in ("forge-server", "jar", "source-world", "launch"):
        parser.add_argument(f"--{key}", type=Path, required=True)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    world = directory / WORLD
    shutil.copytree(args.source_world, world)
    write_measurements(world)
    report = {"passed": False, "assertions": [], "servers": [], "clients": {}, "measurements": {},
              "sourceWorld": str(args.source_world.resolve()), "jarSha256": digest(directory / "mods/enderio.jar")}

    def fresh(server, results):
        prepare(server, results)
        run_client(args, server, report, restart=False)

    try:
        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", lambda server, _: run_client(args, server, report, restart=True))
        verify_saved_staff(directory, report)
        report["passed"] = True
    finally:
        result = directory / "antenna-progression-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Antenna progression passed={report['passed']}, {len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
