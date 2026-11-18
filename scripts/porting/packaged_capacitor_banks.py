#!/usr/bin/env python3
"""Verify capacitor-bank graph ownership and exact energy on a packaged Forge server."""

import argparse
import gzip
import json
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import time

sys.dont_write_bytecode = True
from packaged_server_smoke import ROOT, Server, digest
from client.run_multiplayer import stop_client, wait_signal

WORLD = "port-capacitor-banks"
CHUNKS = "1024 0 1055 15"
BANKS = {1039: ("basic", 500000), 1040: ("basic", 500000),
         1041: ("advanced", 2000000), 1042: ("vibrant", 4000000)}
TOTAL = sum(capacity for _, capacity in BANKS.values())
BRIDGE = "1040 80 8"
BRIDGE_BLOCK = "enderio:basic_capacitor_bank"
BRIDGE_IO = '{Modes:{north:"disable",south:"pull"}}'
BRIDGE_ITEM = (f'id:"{BRIDGE_BLOCK}",Count:1b,tag:{{"enderio:data":'
               f'{{"enderio:energy":{BANKS[1040][1]},"enderio:io_config":{BRIDGE_IO}}}}}')
RECEIVER = "1041 80 9"


def read_saved_networks(path):
    """Read the compound/list/number layout written by the native SavedData codec."""
    with gzip.open(path, "rb") as data:
        def number(fmt):
            return struct.unpack(">" + fmt, data.read(struct.calcsize(fmt)))[0]

        def string():
            return data.read(number("H")).decode("utf-8")

        def value(kind):
            if kind in (1, 2, 3, 4):
                return number({1: "b", 2: "h", 3: "i", 4: "q"}[kind])
            if kind == 8:
                return string()
            if kind == 9:
                element = number("B")
                return [value(element) for _ in range(number("i"))]
            if kind == 10:
                fields = {}
                while field_type := number("B"):
                    name = string()
                    fields[name] = value(field_type)
                return fields
            if kind in (11, 12):
                return [number("i" if kind == 11 else "q") for _ in range(number("i"))]
            raise ValueError(f"Unexpected NBT tag type {kind} in {path}")

        root_type = number("B")
        string()
        return value(root_type)["data"]["Networks"]


def check(name, expected, observed, results):
    results.append({"assertion": name, "passed": expected == observed, "expected": expected, "observed": observed})


def observe(server, stage, results, *networks):
    server.command("save-all flush")
    path = server.directory / WORLD / "data/enderio_capacitor_bank_network.dat"
    observed = []
    for network in read_saved_networks(path):
        nodes = network["nodes"]
        xs = [node["pos"][0] for node in nodes]
        edges = [tuple(sorted((xs[e["first"]], xs[e["second"]]))) for e in network["edges"]]
        observed.append((sorted((node["pos"][0], node["capacity"]) for node in nodes), sorted(edges), network["energy"]))
    expected = []
    for xs, energy in networks:
        expected.append(([(x, BANKS[x][1]) for x in xs], list(zip(xs, xs[1:])), energy))
    check(stage, sorted(expected), sorted(observed), results)


def first_run(args, server, report):
    results = report["assertions"]
    server.command(f"forceload add {CHUNKS}")
    server.assert_state("array_chunks_loaded", "if loaded 1039 80 8 if loaded 1042 80 8", results)
    for x, (tier, _) in BANKS.items():
        server.command(f"setblock {x} 80 8 enderio:{tier}_capacitor_bank")
    server.command("setblock 1038 80 8 enderio:creative_power")
    # A live external source fills the mixed-tier array only through the leftmost bank.
    observe(server, "mixed_tiers_share_capacity_and_charge", results, (list(BANKS), TOTAL))
    server.command("setblock 1038 80 8 minecraft:air")
    server.command(f"forceload remove {CHUNKS}")
    server.assert_state("both_array_chunks_actually_unload", "unless loaded 1039 80 8 unless loaded 1042 80 8", results, timeout=40)
    observe(server, "unloaded_array_keeps_original_nodes_and_charge", results, (list(BANKS), TOTAL))
    server.command(f"forceload add {CHUNKS}")
    server.assert_state("both_array_chunks_reload", "if loaded 1039 80 8 if loaded 1042 80 8", results)
    observe(server, "reload_keeps_unique_positions_and_capacity", results, (list(BANKS), TOTAL))

    if args.carry_launch:
        test_carry(args, server, report)
        return
    server.command(f"setblock {BRIDGE} minecraft:air destroy")
    output = server.command('data get entity @e[type=minecraft:item,x=1040,y=80,z=8,distance=..3,limit=1] Item')
    check("bridge_drop_keeps_its_charge", True, '"enderio:energy": 500000' in output, results)
    observe(server, "removal_splits_the_saturated_bank_exactly", results, ([1039], 500000), ([1041, 1042], 6000000))
    server.command(f"setblock {BRIDGE} enderio:basic_capacitor_bank")
    observe(server, "replacement_rejoins_each_fragment_once", results, (list(BANKS), TOTAL - 500000))


def restart(server, results, carry):
    server.assert_state("restarted_array_chunks_loaded", "if loaded 1039 80 8 if loaded 1042 80 8", results)
    remaining = TOTAL if carry else TOTAL - BANKS[1040][1]
    observe(server, "restarted_array_retains_exact_state", results, (list(BANKS), remaining))
    if carry:
        assert_bridge(server, results, "restart_retains_survival_bank_and_side_configuration")
    server.command(f"setblock {RECEIVER} enderio:wired_charger")
    server.command(f'data merge block {RECEIVER} {{Items:{{Size:3,Items:[{{Slot:0,id:"enderio:basic_capacitor",Count:1}}]}}}}')
    server.command('data merge block 1041 80 8 {IOConfig:{Modes:{south:"push"}}}')
    server.assert_state("bank_powers_a_real_neighbor_after_restart",
                        f"if data block {RECEIVER} {{EnergyStored:128000}}", results)
    observe(server, "bank_and_receiver_account_for_every_fe", results, (list(BANKS), remaining - 128000))


def assert_bridge(server, results, name):
    server.assert_state(name, f"if block {BRIDGE} {BRIDGE_BLOCK} if data block {BRIDGE} {{IOConfig:{BRIDGE_IO}}}", results)


def test_carry(args, server, report):
    results = report["assertions"]
    server.command("fill 1037 79 6 1045 79 13 minecraft:stone")
    server.command("gamerule doMobSpawning false")
    server.command(f"data merge block {BRIDGE} {{IOConfig:{BRIDGE_IO}}}")
    assert_bridge(server, results, "survival_source_has_two_custom_sides")
    output = ROOT / "build/port-evidence" / (args.name + "-carry")
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
               "--launch", str(args.carry_launch.resolve()), "--scenario", "capacitor-bank-carry",
               "--output", str(output), "--server", f"127.0.0.1:{args.port}", "--timeout", "190"]
    drop = '@e[type=minecraft:item,x=1040.5,y=80,z=8.5,distance=..3]'
    nearest = drop[:-1] + ',sort=nearest,limit=1]'
    with (server.directory / "carry-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            server.wait("EnderIOQA joined the game", timeout=150)
            server.command('item replace entity EnderIOQA hotbar.0 with minecraft:diamond_pickaxe'
                           '{Enchantments:[{id:"minecraft:efficiency",lvl:10s}]}')
            server.command("tp EnderIOQA 1040.5 80 11.5 180 28")
            wait_signal(client, output, "mined")
            server.command(f"data get entity {nearest} Item")
            server.assert_state("survival_drop_keeps_charge_and_both_sides",
                                f"if block {BRIDGE} minecraft:air if data entity {nearest} {{Item:{{{BRIDGE_ITEM}}}}}", results)
            observe(server, "survival_split_conserves_6500000_fe_in_the_fragments", results,
                    ([1039], 500000), ([1041, 1042], 6000000))
            server.command(f"execute at {nearest} run tp EnderIOQA ~ ~ ~")
            server.assert_state("normal_pickup_moves_the_drop_into_inventory",
                                f"unless entity {drop} if data entity EnderIOQA Inventory[{{{BRIDGE_ITEM}}}]", results)
            server.command("tp EnderIOQA 1040.5 80 10.5 180 30")
            wait_signal(client, output, "placement")
            assert_bridge(server, results, "normal_placement_restores_both_sides")
            server.assert_state("survival_placement_consumes_the_item",
                                f'unless entity {drop} unless data entity EnderIOQA Inventory[{{id:"{BRIDGE_BLOCK}"}}]', results)
            observe(server, "survival_replant_rejoins_at_exactly_7000000_fe", results, (list(BANKS), TOTAL))
            (output / "server-verified").touch()
            if client.wait(timeout=35) != 0:
                raise AssertionError(f"Capacitor bank carry client failed; see {output}")
            report["carryClient"] = json.loads((output / "summary.json").read_text())
            capture = report["carryClient"]["client"]["captures"]["capacitor-bank-replaced"]
            check("capacitor_carry_client_and_server_match", report["jarSha256"], digest(Path(capture["loadedMods"]["enderio"]["file"])), results)
        finally:
            stop_client(client)


def test_menu(args, server, report):
    results = report["assertions"]
    server.command("fill 1037 79 7 1047 79 13 minecraft:stone")
    output = ROOT / "build/port-evidence" / (args.name + "-menu")
    output.parent.mkdir(parents=True, exist_ok=True)
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
               "--launch", str(args.launch.resolve()), "--scenario", "capacitor-bank", "--output", str(output),
               "--server", f"127.0.0.1:{args.port}", "--timeout", "185"]
    with (server.directory / "menu-run.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            server.wait("EnderIOQA joined the game", timeout=145)
            server.command("tp EnderIOQA 1042.5 80 11.5 180 12")
            wait_signal(client, output, "edited")
            modes = " ".join(f'if data block {x} 80 8 {{RedstoneControl:"never_active"}}' for x in BANKS)
            server.assert_state("menu_redstone_applies_to_all_four_bank_blocks", modes, results)
            server.command(f"data merge block {RECEIVER} {{EnergyStored:0}}")
            time.sleep(0.6)
            server.assert_state("disabled_bank_stops_pushing_energy", f"if data block {RECEIVER} {{EnergyStored:0}}", results)
            observe(server, "disabled_bank_preserves_its_balance", results, (list(BANKS), TOTAL - 500000 - 128000))
            (output / "server-verified").touch()
            if client.wait(timeout=25) != 0:
                raise AssertionError(f"Capacitor bank client failed; see {output}")
            report["client"] = json.loads((output / "summary.json").read_text())
            client_jar = report["client"]["client"]["capture"]["loadedMods"]["enderio"]["file"]
            check("capacitor_client_and_server_match", report["jarSha256"], digest(Path(client_jar)), results)
        finally:
            stop_client(client)


def run_stage(args, directory, report, name):
    server = Server(directory, args.java, name)
    try:
        server.wait('For help, type "help"', timeout=150)
        if name == "exercise":
            first_run(args, server, report)
        else:
            restart(server, report["assertions"], bool(args.carry_launch))
        if name == "restart" and args.launch:
            test_menu(args, server, report)
    finally:
        code = server.stop()
        errors = [line.strip() for line in server.lines if "/ERROR]" in line or "/FATAL]" in line]
        report["servers"].append({"stage": name, "exit": code, "errors": errors, "log": str(server.log_path)})
        if code or errors:
            raise RuntimeError(f"Forge errors in {name}; see {server.log_path}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    client = parser.add_mutually_exclusive_group()
    client.add_argument("--launch", type=Path, help="Prepared quiet client profile for the capacitor bank menu")
    client.add_argument("--carry-launch", type=Path, help="Prepared quiet client profile for survival pickup and replant")
    parser.add_argument("--port", type=int, default=25707)
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
    connection = "online-mode=false\nenforce-secure-profile=false\n" if args.launch or args.carry_launch else ""
    (directory / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={args.port}\nlevel-name={WORLD}\n"
        f"level-type=minecraft:flat\ngenerator-settings={flat}\n"
        "generate-structures=false\nview-distance=3\nsimulation-distance=3\n" + connection)
    report = {"passed": False, "jarSha256": digest(artifact), "assertions": [], "servers": []}
    try:
        run_stage(args, directory, report, "exercise")
        run_stage(args, directory, report, "restart")
        report["passed"] = all(result["passed"] for result in report["assertions"])
    finally:
        path = directory / "capacitor-bank-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Capacitor banks passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)
    if not report["passed"]:
        raise AssertionError("Capacitor bank accounting differs from the expected graph, see " + str(path))


if __name__ == "__main__":
    main()
