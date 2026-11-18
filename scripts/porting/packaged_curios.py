#!/usr/bin/env python3
"""Exercise Ender IO's Curios and ordinary inventory paths over packaged Forge TCP."""

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
from packaged_server_smoke import ROOT, Server, digest
from packaged_solar_charging import check


WORLD = "port-curios"
PLAYER = "EnderIOQA"
CHARGER = "20 80 10"
STAFF = "enderio:staff_of_travelling"
MAGNET = "enderio:electromagnet"
CURIO_INVENTORY = 'ForgeCaps."curios:inventory".Curios'
STAFF_SLOT = f'{CURIO_INVENTORY}[{{Identifier:"port_staff"}}].StacksHandler.Stacks.Items[{{id:"{STAFF}"}}]'
MAGNET_SLOT = f'{CURIO_INVENTORY}[{{Identifier:"port_magnet"}}].StacksHandler.Stacks.Items[{{id:"{MAGNET}"}}]'
ORDINARY_STAFF = 'Inventory[{Slot:1b,id:"enderio:staff_of_travelling"}]'
ORDINARY_MAGNET = 'Inventory[{Slot:2b,id:"enderio:electromagnet"}]'
ENERGY = '.tag."enderio:data"."enderio:energy"'
STORED = 32000


def staff(amount):
    return STAFF + '{"enderio:data":{"enderio:energy":' + str(amount) + '}}'


def magnet(amount, enabled=False):
    return MAGNET + '{"enderio:data":{"enderio:energy":' + str(amount) + ',"enderio:toggled":' + str(int(enabled)) + 'b}}'


def create_datapack(world, curios):
    pack = world / "datapacks/port_curios"

    def write(path, content):
        file = pack / path
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(content)

    def write_json(path, value):
        write(path, json.dumps(value) + "\n")

    write_json("pack.mcmeta", {"pack": {"pack_format": 15, "description": "Ender IO inventory QA"}})
    if curios:
        slots = {"port_staff": STAFF, "port_magnet": MAGNET}
        for slot, item in slots.items():
            write_json(f"data/curios/curios/slots/{slot}.json", {"size": 1})
            write_json(f"data/curios/tags/items/{slot}.json", {"values": [item]})
        write_json("data/curios/curios/entities/port_player.json",
                   {"entities": ["minecraft:player"], "slots": list(slots)})

    measurements = {
        "tick": "time query gametime",
        "buffer": f"data get block {CHARGER} EnergyStored",
        "ordinary": f"data get entity {PLAYER} {ORDINARY_STAFF}{ENERGY}",
    }
    if curios:
        measurements["equipped"] = f"data get entity {PLAYER} {STAFF_SLOT}{ENERGY}"
    write("data/port_curios/functions/sample.mcfunction",
          "data modify storage port_curios:sample value set value {}\n"
          + "".join(f"execute store result storage port_curios:sample value.{key} int 1 run {command}\n"
                    for key, command in measurements.items()))
    write("data/port_curios/functions/start.mcfunction",
          f"data merge block {CHARGER} {{EnergyStored:{STORED}}}\nfunction port_curios:sample\n")


def sample(server, start=False):
    server.command("function port_curios:" + ("start" if start else "sample"))
    output = server.command("data get storage port_curios:sample value")
    return {key: int(value) for key, value in re.findall(r"(\w+): (-?\d+)", output.split("storage", 1)[-1])}


def equip(server, slot, stack):
    server.command(f"curios replace {slot} 0 {PLAYER} with {stack}")


def assert_staff(server, results, path, charge, name):
    server.assert_state(name, f'if data entity {PLAYER} {path}.tag."enderio:data"'
                        '{"enderio:energy":' + str(charge) + '}', results)


def assert_magnet(server, results, path, enabled, name):
    server.assert_state(name, f'if data entity {PLAYER} {path}.tag."enderio:data"'
                        '{"enderio:toggled":' + str(int(enabled)) + 'b}', results)


def fixture(server, results):
    server.command("forceload add 16 0 63 15")
    server.assert_state("fixture_chunks_loaded", "if loaded 20 80 10 if loaded 56 80 0", results)
    for command in (
        "fill 19 79 0 60 79 13 minecraft:stone",
        "gamerule spawnRadius 0",
        "setworldspawn 20 80 12",
        "gamerule doDaylightCycle false",
        "gamerule doMobSpawning false",
        "time set noon",
        f"setblock {CHARGER} enderio:wireless_charger",
        f'data merge block {CHARGER} {{Items:{{Size:1,Items:[{{Slot:0,id:"enderio:basic_capacitor",Count:1}}]}}}}',
    ):
        server.command(command)


def exercise(server, client, output, report, curios):
    results = report["assertions"]
    server.command(f"tp {PLAYER} 20.5 80 12.5")
    server.command(f"item replace entity {PLAYER} hotbar.1 with {staff(99980)}")
    assert_staff(server, results, ORDINARY_STAFF, 99980, "ordinary_staff_seed")
    if curios:
        equip(server, "port_staff", staff(0))
        server.command(f"data get entity {PLAYER} {CURIO_INVENTORY}")
        assert_staff(server, results, STAFF_SLOT, 0, "equipped_staff_seed")
    initial = sample(server, start=True)
    time.sleep(0.5)
    charged = sample(server)
    report["energySamples"] = {"initial": initial, "charged": charged}
    ticks = charged["tick"] - initial["tick"]
    ordinary_gain = charged["ordinary"] - initial["ordinary"]
    equipped_gain = charged.get("equipped", 0) - initial.get("equipped", 0)
    check("charger_fills_ordinary_staff", 20, ordinary_gain, results)
    check("single_shared_transfer_budget", 60 * ticks if curios else 20, ordinary_gain + equipped_gain, results)
    check("buffer_conserves_transfer_and_upkeep", STORED, charged["buffer"] + ordinary_gain + equipped_gain + 2 * ticks, results)
    wait_signal(client, output, "charged")

    server.command(f"data merge block {CHARGER} {{EnergyStored:0}}")
    server.command(f"item replace entity {PLAYER} hotbar.1 with {staff(9000)}")
    if curios:
        equip(server, "port_staff", staff(6000))
        equip(server, "port_magnet", magnet(50000))
        assert_staff(server, results, STAFF_SLOT, 6000, "charged_equipped_staff_seed")
        assert_magnet(server, results, MAGNET_SLOT, False, "equipped_magnet_seed")
    server.command(f"item replace entity {PLAYER} hotbar.2 with {magnet(50000)}")
    server.command(f"tp {PLAYER} 52.9 80 0.5 -90 22")

    wait_signal(client, output, "blink")
    server.assert_state("staff_blink_reaches_real_server", f"positioned 56.5 80 0.5 if entity @a[name={PLAYER},distance=..0.4]", results)
    if curios:
        assert_staff(server, results, STAFF_SLOT, 5000, "exact_equipped_staff_cost")
        assert_magnet(server, results, MAGNET_SLOT, True, "equipped_magnet_toggled")
        assert_magnet(server, results, ORDINARY_MAGNET, False, "ordinary_magnet_preserved")
    else:
        assert_magnet(server, results, ORDINARY_MAGNET, True, "ordinary_magnet_fallback_toggled")
    assert_staff(server, results, ORDINARY_STAFF, 9000 if curios else 8000, "ordinary_staff_charge")


def run_client(args, server, stage, report):
    output = ROOT / "build/port-evidence" / f"{args.name}-{stage}"
    output.parent.mkdir(parents=True, exist_ok=True)
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
               "--launch", str(args.launch.resolve()), "--scenario", "curios", "--timeout", "180",
               "--server", f"127.0.0.1:{args.port}", "--output", str(output),
               "--multiplayer-phase", "verify" if stage == "restart" else "exercise"]
    with output.with_name(output.name + "-run.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            if stage == "restart":
                wait_signal(client, output, "restored")
                assert_staff(server, report["assertions"], STAFF_SLOT, 5000, "equipped_staff_survives_restart")
                assert_magnet(server, report["assertions"], MAGNET_SLOT, True, "equipped_magnet_survives_restart")
            else:
                exercise(server, client, output, report, bool(args.curios_jar))
            (output / "server-verified").touch()
            client.wait(timeout=30)
            summary = json.loads((output / "summary.json").read_text())
            if client.returncode or summary["status"] != "pass":
                raise AssertionError(f"Client failed; see {output}")
            loaded = summary["client"]["capture"]["loadedMods"]
            check(f"expected_curios_presence_{stage}", bool(args.curios_jar), "curios" in loaded, report["assertions"])
            for mod, expected in report["mods"].items():
                file = Path(loaded[mod]["file"])
                check(f"same_{mod}_artifact_{stage}", expected, digest(file), report["assertions"])
        finally:
            stop_client(client)
            report["clients"].append({"stage": stage, "exit": client.returncode, "evidence": str(output)})


def run_server(args, directory, stage, report):
    server = Server(directory, args.java, stage)
    try:
        server.wait('For help, type "help"')
        if stage == "initial":
            fixture(server, report["assertions"])
        run_client(args, server, stage, report)
        server.command("save-all flush")
    finally:
        status = server.stop()
        errors = [line.strip() for line in server.lines if "/ERROR]" in line or "/FATAL]" in line]
        report["servers"].append({"stage": stage, "exit": status, "errors": errors, "log": str(server.log_path)})
    if status or errors:
        raise AssertionError(f"Server failed; see {server.log_path}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--curios-jar", type=Path)
    parser.add_argument("--launch", required=True, type=Path)
    parser.add_argument("--port", type=int, default=25697)
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9_-]+", args.name):
        parser.error("--name must be a single directory name")

    directory = ROOT / "build/port-runtime" / args.name
    directory.mkdir()
    shutil.copytree(args.forge_server / "libraries", directory / "libraries")
    mods = directory / "mods"
    mods.mkdir()
    artifacts = {"enderio": args.jar}
    if args.curios_jar:
        artifacts["curios"] = args.curios_jar
    for mod, jar in artifacts.items():
        shutil.copyfile(jar, mods / f"{mod}.jar")
    (directory / "eula.txt").write_text("eula=true\n")
    flat = '{"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:plains"}'
    (directory / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={args.port}\nonline-mode=false\nenforce-secure-profile=false\n"
        f"level-name={WORLD}\nlevel-type=minecraft:flat\ngenerator-settings={flat}\n"
        "generate-structures=false\nview-distance=4\nsimulation-distance=4\nspawn-protection=0\ndifficulty=peaceful\n")
    create_datapack(directory / WORLD, bool(args.curios_jar))

    report = {"passed": False, "mods": {mod: digest(mods / f"{mod}.jar") for mod in artifacts},
              "assertions": [], "servers": [], "clients": []}
    try:
        run_server(args, directory, "initial", report)
        if args.curios_jar:
            run_server(args, directory, "restart", report)
        report["passed"] = True
    finally:
        output = directory / "curios-results.json"
        output.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Curios scenario passed={report['passed']}, {len(report['assertions'])} assertions. {output}", flush=True)


if __name__ == "__main__":
    main()
