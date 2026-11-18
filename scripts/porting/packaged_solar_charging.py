#!/usr/bin/env python3
"""Measure native solar and charger energy in a disposable packaged Forge world."""

import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time

sys.dont_write_bytecode = True
from packaged_server_smoke import ROOT, Server, digest
from client.run_multiplayer import stop_client


WORLD = "port-solar-charging"
ARRAY = [(1038, "energetic"), (1039, "energetic"), (1040, "pulsating"), (1041, "vibrant")]
ARRAY_CHUNKS = "1024 0 1055 15"
STAFF_TAG = '{PortSolar:59,"enderio:data":{"enderio:energy":100000}}'
PHANTOM_PANEL = "11 80 9"
WIRELESS = "20 80 10"
WIRELESS_STARTING_ENERGY = 32000


def write_measurement_pack(world):
    pack = world / "datapacks/port_power"
    functions = pack / "data/port_power/functions"
    functions.mkdir(parents=True)
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 15, "description": "Solar and charger measurements"}}))

    def write(name, lines):
        (functions / (name + ".mcfunction")).write_text("\n".join(lines) + "\n")

    fields = {f"panel{x}": f"data get block {x} 80 0 EnergyStored" for x, _ in ARRAY}
    fields["tick"] = "time query gametime"
    scenarios = {
        "array": fields,
        "charge": {
            "tick": "time query gametime", "solar": "data get block 5 80 6 EnergyStored",
            "charger": "data get block 5 79 6 EnergyStored",
        },
        "phantom": {
            "tick": "time query gametime", "solar": f"data get block {PHANTOM_PANEL} EnergyStored",
        },
        "wireless": {
            "tick": "time query gametime", "charger": f"data get block {WIRELESS} EnergyStored",
            "staff": 'data get entity EnderIOQA Inventory[{Slot:0b}].tag."enderio:data"."enderio:energy"',
        },
    }
    for name, sources in scenarios.items():
        write(name + "_sample", [
            "data modify storage port_power:sample value set value {}",
            *(f"execute store result storage port_power:sample value.{key} int 1 run {command}" for key, command in sources.items()),
        ])
    write("wireless_start", [
        f"data merge block {WIRELESS} {{EnergyStored:{WIRELESS_STARTING_ENERGY}}}",
        "function port_power:wireless_sample",
    ])


def sample(server, function):
    server.command(f"function port_power:{function}")
    output = server.command("data get storage port_power:sample value")
    fields = {key: int(value) for key, value in re.findall(r"(\w+): (-?\d+)", output)}
    if "tick" not in fields:
        raise AssertionError(output)
    return fields


def check(name, expected, actual, results):
    passed = expected == actual
    results.append({"assertion": name, "passed": passed, "expected": expected, "observed": actual})
    if not passed:
        raise AssertionError(f"{name}: expected {expected}, observed {actual}")


def test_array(server, results):
    server.command("gamerule doDaylightCycle false")
    server.command("time set midnight")
    server.command("weather clear")
    server.command("forceload add 0 0 31 15")
    server.command(f"forceload add {ARRAY_CHUNKS}")
    server.command("fill 1039 82 0 1041 82 0 minecraft:stone")
    for x, tier in ARRAY:
        server.command(f"setblock {x} 80 0 enderio:{tier}_photovoltaic_module")
    server.command("data merge block 1038 80 0 {EnergyStored:4000}")
    server.command("time set noon")
    time.sleep(0.4)
    first = sample(server, "array_sample")
    time.sleep(0.55)
    observed = sample(server, "array_sample")
    total = sum(observed[f"panel{x}"] for x, _ in ARRAY)
    check("array_conserves_4_fe_per_tick", sum(first[f"panel{x}"] for x, _ in ARRAY) + 4 * (observed["tick"] - first["tick"]),
          total, results)
    server.command("time set midnight")
    before = sample(server, "array_sample")
    server.command(f"forceload remove {ARRAY_CHUNKS}")
    server.assert_state("array_chunks_unloaded", "unless loaded 1039 80 0 unless loaded 1040 80 0", results, timeout=40)
    server.command(f"forceload add {ARRAY_CHUNKS}")
    server.assert_state("array_chunks_reloaded", "if loaded 1039 80 0 if loaded 1040 80 0", results)
    after = sample(server, "array_sample")
    check("array_balances_survive_chunk_eviction", [before[f"panel{x}"] for x, _ in ARRAY],
          [after[f"panel{x}"] for x, _ in ARRAY], results)
    server.command("time set noon")
    time.sleep(0.4)
    first = sample(server, "array_sample")
    time.sleep(0.45)
    reloaded = sample(server, "array_sample")
    check("reloaded_array_shares_generation_across_chunk_edge",
          sum(first[f"panel{x}"] for x, _ in ARRAY) + 4 * (reloaded["tick"] - first["tick"]),
          sum(reloaded[f"panel{x}"] for x, _ in ARRAY), results)
    server.command("time set midnight")


def test_wired(server, results):
    server.command("setblock 8 80 6 enderio:wired_charger")
    server.command('data merge block 8 80 6 {EnergyStored:60,Items:{Size:3,Items:['
                   '{Slot:0,id:"enderio:basic_capacitor",Count:1},{Slot:1,id:"enderio:staff_of_travelling",Count:1,'
                   'tag:{PortSolar:59,"enderio:data":{"enderio:energy":99940}}}]}}')
    server.assert_state("wired_releases_staff_on_final_fe", 'if data block 8 80 6 {EnergyStored:0} '
                        f'if data block 8 80 6 Items.Items[{{Slot:2,id:"enderio:staff_of_travelling",tag:{STAFF_TAG}}}] '
                        'unless data block 8 80 6 Items.Items[{Slot:1}]', results)
    server.command("setblock 5 80 6 enderio:vibrant_photovoltaic_module")
    server.command("setblock 5 79 6 enderio:wired_charger")
    server.command('data merge block 5 79 6 {IOConfig:{Modes:{up:"pull"}},Items:{Size:3,Items:['
                   '{Slot:0,id:"enderio:basic_capacitor",Count:1}]}}')
    server.command("time set noon")
    time.sleep(0.4)
    before = sample(server, "charge_sample")
    server.command('data merge block 5 79 6 {IOConfig:{Modes:{up:"pull"}},Items:{Size:3,Items:['
                   '{Slot:0,id:"enderio:basic_capacitor",Count:1},{Slot:1,id:"enderio:staff_of_travelling",Count:1,'
                   'tag:{PortSolar:59,"enderio:data":{"enderio:energy":99900}}}]}}')
    server.assert_state("solar_charges_staff_and_moves_it_to_output", 'if data block 5 79 6 Items.Items'
                        f'[{{Slot:2,id:"enderio:staff_of_travelling",Count:1,tag:{STAFF_TAG}}}] '
                        'unless data block 5 79 6 Items.Items[{Slot:1}]', results)
    observed = sample(server, "charge_sample")
    check("wired_and_vibrant_energy_balances", 64 * (observed["tick"] - before["tick"]),
          observed["solar"] + observed["charger"] + 100 - before["solar"] - before["charger"], results)
    server.command("time set midnight")
    return sample(server, "charge_sample")["charger"]


def test_wireless(args, server, report):
    results = report["assertions"]
    server.command("forceload add 32 0 47 15")
    server.command("fill 19 79 9 41 79 13 minecraft:stone")
    server.command(f"setblock {WIRELESS} enderio:wireless_charger[facing=south]")
    server.command(f'data merge block {WIRELESS} {{Items:{{Size:1,Items:[{{Slot:0,id:"enderio:basic_capacitor",Count:1}}]}}}}')
    output = ROOT / "build/port-evidence" / (args.name + "-tcp")
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
               "--launch", str(args.launch.resolve()), "--scenario", "wireless-charge", "--output", str(output),
               "--server", f"127.0.0.1:{args.port}", "--timeout", "200"]
    with (server.directory / "client-run.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            server.wait("EnderIOQA joined the game", timeout=150)
            server.command('item replace entity EnderIOQA hotbar.0 with enderio:staff_of_travelling'
                           '{PortSolar:59,"enderio:data":{"enderio:energy":0}}')
            server.command("tp EnderIOQA 40.5 80 10.5 90 10")
            start = sample(server, "wireless_start")["tick"]
            time.sleep(0.45)
            outside = sample(server, "wireless_sample")
            check("wireless_default_range_leaves_staff_uncharged", 0, outside["staff"], results)
            check("wireless_default_range_only_debits_upkeep", WIRELESS_STARTING_ENERGY,
                  outside["charger"] + 2 * (outside["tick"] - start), results)
            server.command("setblock 20 81 10 enderio:wireless_charger_antenna_advanced")
            server.assert_state("advanced_antenna_extends_range_to_48", f'if data block {WIRELESS} {{ActionRange:{{range:48}}}}', results)
            extended = sample(server, "wireless_sample")
            check("antenna_player_receives_energy_at_same_position", True, extended["staff"] > 0, results)
            check("wireless_charge_and_upkeep_conserve_buffer", WIRELESS_STARTING_ENERGY,
                  extended["charger"] + extended["staff"] + 2 * (extended["tick"] - start), results)
            server.command("setblock 20 81 10 minecraft:air")
            server.assert_state("removing_antenna_restores_default_range", f'if data block {WIRELESS} {{ActionRange:{{range:16}}}}', results)
            after_removal = sample(server, "wireless_sample")
            time.sleep(0.45)
            left_outside = sample(server, "wireless_sample")
            check("wireless_stops_charging_after_antenna_removed", after_removal["staff"], left_outside["staff"], results)
            server.command("setblock 20 81 10 enderio:wireless_charger_antenna_advanced")
            server.command("tp EnderIOQA 20.5 80 12.5 180 5")
            if client.wait(timeout=205) != 0:
                raise AssertionError(f"Wireless charger client failed; see {output}")
            report["client"] = json.loads((output / "summary.json").read_text())
            server.assert_state("wireless_range_button_reaches_dedicated_server", f'if data block {WIRELESS} {{ActionRange:{{range:47}}}}', results)
        finally:
            stop_client(client)


def test_phantom_after_restart(server, results):
    server.assert_state("phantom_binding_survives_restart", f'if data block {PHANTOM_PANEL} '
                        '{EntityStorage:{entity_type:"minecraft:phantom"},EnergyStored:0}', results)
    server.command("setblock 11 82 9 minecraft:air")
    time.sleep(0.45)
    first = sample(server, "phantom_sample")
    time.sleep(0.45)
    second = sample(server, "phantom_sample")
    check("phantom_panel_generates_64_fe_per_tick_at_midnight",
          64 * (second["tick"] - first["tick"]), second["solar"] - first["solar"], results)

    server.command("time set noon")
    before = sample(server, "phantom_sample")
    time.sleep(0.45)
    after = sample(server, "phantom_sample")
    check("phantom_panel_stops_at_noon", before["solar"], after["solar"], results)

    rule = server.directory / WORLD / "datapacks/port_power/data/enderio/eio_soul/solar/minecraft_phantom.json"
    rule.parent.mkdir(parents=True)
    rule.write_text(json.dumps({"entity": "minecraft:phantom", "daytime": True, "nighttime": False}) + "\n")
    before_reload = len(server.lines)
    server.command("reload")
    server.wait("Data loader for eio_soul/solar loaded 1 jsons", start=before_reload, timeout=30)
    first = sample(server, "phantom_sample")
    time.sleep(0.45)
    second = sample(server, "phantom_sample")
    check("phantom_panel_uses_reloaded_daytime_rule",
          64 * (second["tick"] - first["tick"]), second["solar"] - first["solar"], results)
    server.command("time set midnight")


def run_stage(directory, java, report, name, scenario):
    server = Server(directory, java, name)
    try:
        server.wait('For help, type "help"')
        return scenario(server, report["assertions"])
    finally:
        code = server.stop()
        errors = [line.strip() for line in server.lines if "/ERROR]" in line or "/FATAL]" in line]
        report["servers"].append({"stage": name, "exit": code, "errors": errors, "log": str(server.log_path)})
        if code or errors:
            raise RuntimeError(f"Packaged Forge errors in {name}; see {server.log_path}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--launch", type=Path, help="Prepared quiet client profile for the TCP wireless charger fixture")
    parser.add_argument("--port", type=int, default=25696)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")
    directory = ROOT / "build/port-runtime" / args.name
    directory.mkdir()
    shutil.copytree(args.forge_server / "libraries", directory / "libraries")
    (directory / "mods").mkdir()
    artifact = directory / "mods/enderio.jar"
    shutil.copyfile(args.jar, artifact)
    (directory / "eula.txt").write_text("eula=true\n")
    flat = '{"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:plains"}'
    (directory / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={args.port}\nonline-mode=false\nlevel-name={WORLD}\n"
        f"level-type=minecraft:flat\ngenerator-settings={flat}\n"
        "generate-structures=false\nview-distance=3\nsimulation-distance=3\nspawn-protection=0\n")
    write_measurement_pack(directory / WORLD)
    report = {"passed": False, "jarSha256": digest(artifact), "assertions": [], "servers": []}

    def initial(server, results):
        test_array(server, results)
        stored = test_wired(server, results)
        server.command("setblock 11 82 9 minecraft:stone")
        server.command(f"setblock {PHANTOM_PANEL} enderio:vibrant_photovoltaic_module")
        server.command(f'data merge block {PHANTOM_PANEL} {{EntityStorage:{{entity_type:"minecraft:phantom",entity_tag:{{}}}}}}')
        array = sample(server, "array_sample")
        server.command("save-all flush")
        return stored, array

    def restart(server, results):
        server.assert_state("wired_charge_and_buffer_survive_restart", f'if data block 5 79 6 {{EnergyStored:{stored}}} '
                            f'if data block 5 79 6 Items.Items[{{Slot:2,id:"enderio:staff_of_travelling",tag:{STAFF_TAG}}}]', results)
        current = sample(server, "array_sample")
        check("array_balances_survive_restart", [array[f"panel{x}"] for x, _ in ARRAY],
              [current[f"panel{x}"] for x, _ in ARRAY], results)
        test_phantom_after_restart(server, results)
        if args.launch:
            test_wireless(args, server, report)

    try:
        stored, array = run_stage(directory, args.java, report, "exercise", initial)
        run_stage(directory, args.java, report, "restart", restart)
        report["passed"] = True
    finally:
        path = directory / "solar-charging-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Solar and charging passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
