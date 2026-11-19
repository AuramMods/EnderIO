#!/usr/bin/env python3
"""Verify the Staff of Levity's migration, native refuelling and flight on packaged Forge."""

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
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage

PLAYER = "EnderIOQA"
BARREL = "4040 80 8"
TANK = "4043 80 8"
STAFF = "enderio:staff_of_levity"
FLUID_KEY = '"enderio:item_fluid_content"'
ENERGY_KEY = '"enderio:energy"'
DATA = '"enderio:data"'
FLUID = "enderio:fluid_vapor_of_levity_still"


def vapor(fluid, amount, batch):
    return f'{{FluidName:"{fluid}",Amount:{amount},Tag:{{PortBatch:"{batch}"}}}}'


def legacy_stack(energy, amount, batch, active):
    return (f'{{Energy:{{EnergyStored:{energy},MaxEnergyStored:10000}},ToggleState:{int(active)}b,'
            f'Fluid:{vapor("enderio:vapor_of_levity", amount, batch)},PortSerial:"{batch}"}}')


def staff(slot, data, serial="", *, machine=False):
    suffix = "" if machine else "b"
    return f'{{Slot:{slot}{suffix},id:"{STAFF}",Count:1{suffix},tag:{{{data}{serial}}}}}'


def legacy_fixture(server, results):
    server.command("forceload add 4032 0 4047 15")
    server.assert_state("test_chunk_is_loaded", f"if loaded {BARREL}", results)
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    server.command("fill 4034 79 3 4046 79 14 minecraft:stone")
    server.command("setworldspawn 4040 80 10")
    server.command(f"setblock {BARREL} minecraft:barrel")
    disabled = legacy_stack(62, 40, "reserve", False)
    active = legacy_stack(82, 18, "flight", True)
    for slot, metadata in enumerate((disabled, active)):
        server.command(f"item replace block {BARREL} container.{slot} with {STAFF}{metadata}")
    server.assert_state("historical_items_save_original_fluid_power_and_toggle_fields",
                        f"if data block {BARREL} Items[{staff(0, disabled[1:-1])}] "
                        f"if data block {BARREL} Items[{staff(1, active[1:-1])}]", results)


def assert_migrated(server, results):
    reserve = staff(0, f'{DATA}:{{{FLUID_KEY}:{vapor(FLUID, 40, "reserve")}}}', ',PortSerial:"reserve"')
    active = staff(1, f'{DATA}:{{{FLUID_KEY}:{vapor(FLUID, 18, "flight")},"enderio:toggled":1b}}', ',PortSerial:"flight"')
    server.command(f"data get block {BARREL} Items")
    server.assert_state("conversion_restores_exact_tagged_fuel_and_active_state",
                        f"if data block {BARREL} Items[{reserve}] if data block {BARREL} Items[{active}] "
                        f"unless data block {BARREL} Items[{{Slot:0b}}].tag.Fluid "
                        f"unless data block {BARREL} Items[{{Slot:1b}}].tag.ToggleState", results, timeout=4)


def give_saved(server, slot, hotbar):
    server.command(f"item replace entity {PLAYER} hotbar.{hotbar} from block {BARREL} container.{slot}")
    server.command(f"item replace block {BARREL} container.{slot} with minecraft:air")


def game_tick(server):
    output = server.command("time query gametime")
    return int(re.search(r"The time is (\d+)", output).group(1))


def modern_fuel_fixture(server, results):
    server.command(f"setblock {TANK} enderio:fluid_tank")
    wrong = vapor("minecraft:water", 110, "wrong")
    fuel = vapor(FLUID, 60, "native")
    empty_staff = staff(2, f"{DATA}:{{{ENERGY_KEY}:185}}", ',PortSerial:"native"', machine=True)
    server.command(f"data merge block {TANK} {{Fluids:{{Tanks:[{{Index:0,Contents:{wrong}}}]}},"
                   f"Items:{{Size:4,Items:[{empty_staff}]}}}}")
    # A tank processes containers every five ticks. Let multiple cycles run with water before checking it.
    after = game_tick(server) + 10
    deadline = time.monotonic() + 10
    while game_tick(server) < after:
        if time.monotonic() >= deadline:
            raise AssertionError("The tank's world did not tick")
    server.assert_state("tank_rejects_water_for_staff", f"if data block {TANK} Items.Items[{empty_staff}] "
                        f"if data block {TANK} Fluids.Tanks[{{Index:0,Contents:{wrong}}}]", results)
    server.command(f"data modify block {TANK} Fluids.Tanks[{{Index:0}}].Contents set value {fuel}")
    filled_staff = staff(2, f"{DATA}:{{{FLUID_KEY}:{fuel},{ENERGY_KEY}:185}}", ',PortSerial:"native"', machine=True)
    server.assert_state("tank_transfers_all_tagged_vapor_into_staff_once", f"if data block {TANK} Items.Items[{filled_staff}] "
                        f"unless data block {TANK} Items.Items[{{Slot:3}}] unless data block {TANK} Fluids.Tanks[{{Index:0}}].Contents.FluidName", results)


def spent(slot, energy, serial, amount=0):
    data = f"{ENERGY_KEY}:{energy}" + (f",{FLUID_KEY}:{vapor(FLUID, amount, serial)}" if amount else "")
    serial_tag = f',PortSerial:"{serial}"'
    path = f"Inventory[{staff(slot, f'{DATA}:{{{data}}}', serial_tag)}]"
    conditions = f"if data entity {PLAYER} {path} unless data entity {PLAYER} {path}.tag.{DATA}.\"enderio:toggled\""
    if not amount:
        conditions += f" unless data entity {PLAYER} {path}.tag.{DATA}.{FLUID_KEY}"
    return conditions


def exercise(args, server, results, report, reopened):
    phase = "verify" if reopened else "exercise"
    output = ROOT / "build/port-evidence" / f"{args.name}-{phase}-client"
    summary = output / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "levitation-staff", "--multiplayer-phase", phase, "--output", str(output),
               "--server", f"127.0.0.1:{args.port}", "--timeout", "160"]
    with (server.directory / f"client-{phase}.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            if reopened:
                server.assert_state("all_three_idle_staves_survive_exactly_after_restart",
                                    f"{spent(0, 5, 'native')} {spent(1, 2, 'reserve', 20)} {spent(2, 28, 'flight')}", results)
            else:
                server.assert_state("player_starts_on_test_floor", f"if entity @a[name={PLAYER},x=4040,y=80,z=10,dx=0,dy=1,dz=0]", results)
                give_saved(server, 1, 2)
                wait_signal(client, output, "legacy-active")
                server.assert_state("saved_active_staff_lifts_and_spends_both_resources_once_per_tick", spent(2, 28, "flight"), results)
                give_saved(server, 0, 1)
                wait_signal(client, output, "legacy-reserve")
                server.assert_state("subtick_energy_remainder_remains_with_unused_fuel", spent(1, 2, "reserve", 20), results)
                modern_fuel_fixture(server, results)
                (output / "server-tank").touch()
                wait_signal(client, output, "native-picked")
                server.assert_state("tank_menu_removes_native_refuelled_staff_once", f"unless data block {TANK} Items.Items[{{Slot:2}}]", results)
                wait_signal(client, output, "native-rising")
                server.assert_state("server_observes_actual_levitation", f"if entity @a[name={PLAYER},x=4038,y=82,z=8,dx=5,dy=11,dz=4]", results)
                wait_signal(client, output, "native-empty")
                server.assert_state("fuel_exhaustion_stops_flight_without_spending_extra_energy", spent(0, 5, "native"), results)
            (output / "server-verified").touch()
            if client.wait(timeout=40) != 0:
                raise AssertionError(f"Levitation Staff client failed, see {summary}")
            capture = json.loads(summary.read_text())["client"]["captures"]["staff"]
            if digest(Path(capture["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Client and server Ender IO jars differ")
        finally:
            stop_client(client)
            report["clients"][phase] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--legacy-jar", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--launch", type=Path)
    args = parser.parse_args()
    directory = ROOT / "build/port-runtime" / args.name
    directory.mkdir()
    legacy, port = directory / "legacy", directory / "port"
    prepare_profile(args, legacy, args.legacy_jar)
    prepare_profile(args, port, args.jar)
    config = port / "config/enderio/base-common.toml"
    config.parent.mkdir(parents=True)
    config.write_text("[items.levitationstaff]\nenergyUse = 3\n")
    report = {"passed": False, "forge": FORGE, "jarSha256": digest(args.jar), "assertions": [], "servers": [], "clients": {}}

    def convert(server, results):
        assert_migrated(server, results)
        if args.launch:
            exercise(args, server, results, report, reopened=False)

    try:
        run_stage(args, legacy, report, "legacy", legacy_fixture)
        shutil.copytree(legacy / WORLD, port / WORLD)
        run_stage(args, port, report, "converted", convert)
        if args.launch:
            run_stage(args, port, report, "reopened", lambda server, results: exercise(args, server, results, report, reopened=True))
        if any(stage["registryErrors"] for stage in report["servers"]):
            raise AssertionError("The Forge 6 conversion reported registry errors")
        report["passed"] = True
    finally:
        (directory / "levitation-staff-results.json").write_text(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
