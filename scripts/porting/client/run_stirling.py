#!/usr/bin/env python3
"""Check Stirling fuel, transfer, persistence and the packaged TCP menu."""

import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time

sys.dont_write_bytecode = True
from run_multiplayer import ROOT, Server, digest, stop_client


GENERATOR = "0 80 0"
BUFFERED = "1041 80 8"
MENU_GENERATOR = "4 80 0"
WORLD = "port-stirling"


def block_int(server, pos, field):
    output = server.command(f"data get block {pos} {field}")
    match = re.search(r"has the following block data: (-?\d+)\s*$", output, re.MULTILINE)
    if not match:
        raise AssertionError(output)
    return int(match[1])


def wait_for_burn(server, maximum, name, results):
    deadline = time.monotonic() + 10
    while time.monotonic() < deadline:
        ticks = block_int(server, BUFFERED, "BurnTime")
        if 0 < ticks <= maximum:
            results.append({"assertion": name, "passed": True, "burnTime": ticks, "maximum": maximum})
            return ticks
        time.sleep(0.1)
    raise AssertionError(f"{name}: expected a lit generator with at most {maximum} ticks left")


def assert_saved_burn(server, maximum, name, results):
    ticks = block_int(server, BUFFERED, "BurnTime")
    passed = 0 < ticks <= maximum
    results.append({"assertion": name, "passed": passed, "burnTime": ticks, "maximum": maximum})
    if not passed:
        raise AssertionError(f"{name}: loaded {ticks} burn ticks with at most {maximum} expected")
    return ticks


def test_automation(server, results):
    server.command("forceload add -1 -1 15 15")
    server.command(f"setblock {GENERATOR} enderio:stirling_generator[facing=east]")
    server.command(f'data merge block {GENERATOR} {{IOConfig:{{Modes:{{north:"pull",east:"push"}}}},'
                   'RedstoneControl:"active_with_signal",Items:{Size:2,Items:[{Slot:1,id:"enderio:basic_capacitor",Count:1}]}}')
    server.command("setblock 1 80 0 enderio:alloy_smelter")
    server.command('data merge block 1 80 0 {IOConfig:{Modes:{west:"pull"}},'
                   'Items:{Size:5,Items:[{Slot:4,id:"enderio:basic_capacitor",Count:1}]}}')
    server.command('setblock 0 80 -1 minecraft:hopper[facing=south]{Items:[{Slot:0b,id:"minecraft:stick",'
                   'Count:2b,tag:{PortStirling:58}}]}')
    server.assert_state("hopper_inserts_two_tagged_fuels_without_igniting", f'if data block {GENERATOR} '
                        '{BurnTime:0,EnergyStored:0,Items:{Items:[{Slot:0,id:"minecraft:stick",Count:2,tag:{PortStirling:58}}]}} '
                        'unless data block 0 80 -1 Items[0]', results)
    server.command("setblock -1 80 0 minecraft:redstone_block")
    server.assert_state("two_sticks_generate_and_transfer_exactly_2960_fe", f'if data block {GENERATOR} '
                        '{BurnTime:0,BurnDuration:37,EnergyStored:0} if data block 1 80 0 {EnergyStored:2960} '
                        f'unless data block {GENERATOR} Items.Items[{{Slot:0}}]', results)
    server.command("setblock -1 80 0 minecraft:air")


def test_eviction(server, results):
    server.command("forceload add 1041 8")
    server.command(f"setblock {BUFFERED} enderio:stirling_generator")
    server.command(f'data merge block {BUFFERED} {{EnergyStored:63999,Items:{{Size:2,Items:['
                   '{Slot:0,id:"minecraft:coal",Count:2,tag:{PortStirling:58}},{Slot:1,id:"enderio:basic_capacitor",Count:1}]}}')
    server.assert_state("coal_fills_buffer_and_leaves_one_spare", f'if data block {BUFFERED} '
                        '{BurnDuration:600,EnergyStored:64000,Items:{Items:[{Slot:0,id:"minecraft:coal",Count:1,tag:{PortStirling:58}}]}}',
                        results)
    server.command("save-all flush")
    burn_at_save = block_int(server, BUFFERED, "BurnTime")
    before_eviction = wait_for_burn(server, burn_at_save - 24, "burn_ticks_pass_while_full", results)
    server.command("forceload remove 1041 8")
    server.assert_state("full_generator_chunk_unloaded", f"unless loaded {BUFFERED}", results, timeout=40)
    server.command("forceload add 1041 8")
    server.assert_state("full_generator_chunk_reloaded", f"if loaded {BUFFERED}", results)
    after_eviction = assert_saved_burn(server, before_eviction, "reloaded_chunk_keeps_elapsed_burn", results)
    server.command("save-all flush")
    return wait_for_burn(server, after_eviction - 24, "burn_ticks_pass_before_restart", results)


def test_tcp(args, server, report):
    server.command("fill 3 79 0 5 79 4 minecraft:stone")
    server.command(f"setblock {MENU_GENERATOR} enderio:stirling_generator[facing=south]")
    output = ROOT / "build/port-evidence" / (args.name + "-tcp")
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
               "--launch", str(args.launch.resolve()), "--scenario", "stirling", "--output", str(output),
               "--server", f"127.0.0.1:{args.port}", "--timeout", "200"]
    with (server.directory / "client-run.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            server.wait("EnderIOQA joined the game", timeout=150)
            server.command("item replace entity EnderIOQA inventory.0 with minecraft:coal 2")
            server.command("item replace entity EnderIOQA inventory.1 with enderio:basic_capacitor 1")
            server.command("tp EnderIOQA 4.5 80 2.5 180 4")
            if client.wait(timeout=205) != 0:
                raise AssertionError(f"Stirling client failed; see {output}")
            report["client"] = json.loads((output / "summary.json").read_text())
            server.assert_state("tcp_redstone_and_real_fuel_state", f'if data block {MENU_GENERATOR} '
                                '{RedstoneControl:"never_active",BurnDuration:600,Items:{Items:['
                                '{Slot:0,id:"minecraft:coal",Count:1},{Slot:1,id:"enderio:basic_capacitor",Count:1}]}}',
                                report["assertions"])
        finally:
            stop_client(client)


def run_stage(args, directory, report, stage, exercise):
    server = Server(directory, args.java, stage)
    try:
        server.wait('For help, type "help"')
        result = exercise(server, report["assertions"])
    finally:
        code = server.stop()
        errors = [line.strip() for line in server.lines if "/ERROR]" in line or "/FATAL]" in line]
        report["servers"].append({"stage": stage, "exit": code, "errors": errors, "log": str(server.log_path)})
    if code != 0 or errors:
        raise RuntimeError(f"Stirling server stage {stage} failed; see {server.log_path}")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--launch", type=Path)
    parser.add_argument("--port", type=int, default=25693)
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
    report = {"passed": False, "jarSha256": digest(artifact), "assertions": [], "servers": []}

    def initial(server, results):
        test_automation(server, results)
        return test_eviction(server, results)

    def restarted(server, results):
        server.assert_state("native_energy_stays_exact_after_restart", "if data block 1 80 0 {EnergyStored:2960}", results)
        server.assert_state("full_generator_stays_full_after_restart", f'if data block {BUFFERED} '
                            '{BurnDuration:600,EnergyStored:64000,Items:{Items:[{Slot:0,id:"minecraft:coal",Count:1,tag:{PortStirling:58}}]}}',
                            results)
        assert_saved_burn(server, burn_before_stop, "disk_restart_keeps_elapsed_burn", results)
        if args.launch:
            test_tcp(args, server, report)

    try:
        burn_before_stop = run_stage(args, directory, report, "exercise", initial)
        run_stage(args, directory, report, "restart", restarted)
        report["passed"] = True
    finally:
        path = directory / "stirling-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Stirling passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
