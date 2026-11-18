#!/usr/bin/env python3
"""Exercise the real packaged client over loopback TCP against a separately installed Forge server."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[3]
sys.dont_write_bytecode = True
sys.path.insert(0, str(ROOT / "scripts/porting"))
from packaged_server_smoke import Server, digest


def wait_signal(client, output, name):
    deadline = time.monotonic() + 310
    path = output / "signals" / (name + ".json")
    while not path.exists():
        if client.poll() is not None:
            raise RuntimeError(f"Client exited {client.returncode} before {name}; see {output}")
        if time.monotonic() > deadline:
            raise TimeoutError(f"Client did not reach {name}; see {output}")
        time.sleep(0.1)
    return json.loads(path.read_text())


def stop_client(client):
    if client.poll() is not None:
        return
    # The session contains only this runner, native observer and its own Java client.
    os.killpg(client.pid, signal.SIGTERM)
    try:
        client.wait(timeout=10)
    except subprocess.TimeoutExpired:
        os.killpg(client.pid, signal.SIGKILL)
        client.wait()


def tank_state(amount, player=False):
    conditions = ('if data block 0 80 0 {RedstoneControl:"active_with_signal",'
                  f'Fluids:{{Tanks:[{{Index:0,Contents:{{FluidName:"minecraft:water",Amount:{amount}}}}}]}},'
                  'Items:{Items:[{Slot:1,id:"minecraft:bucket",Count:1}]}} '
                  'unless data block 0 80 0 Items.Items[{Slot:0}]')
    if player:
        conditions += ' unless data entity EnderIOQA Inventory[{id:"minecraft:water_bucket"}]'
    return conditions


def write_data_map_fixture(pack, initial_override):
    def write(relative, value):
        path = pack / "data/enderio" / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value) + "\n")

    grinding_tag = ["minecraft:flint", "minecraft:charcoal"] if initial_override else ["minecraft:flint"]
    write("tags/items/port_qa_grinding.json", {"replace": True, "values": grinding_tag})
    grinding = ({"OutputMultiplier": 1.5, "BonusMultiplier": 1.75, "PowerUse": 0.5, "Durability": 45678}
                if initial_override else
                {"OutputMultiplier": 2.0, "BonusMultiplier": 1.25, "PowerUse": 0.75, "Durability": 33333})
    write("data_maps/item/grinding_ball.json", {"values": {"#enderio:port_qa_grinding": grinding}})
    vat = "data_maps/item/vat_reagent.json"
    range_extender = "data_maps/block/range_extender.json"
    if initial_override:
        write(vat, {"values": {"minecraft:gunpowder": {"#enderio:explosives": 2.25}},
                    "remove": ["minecraft:fire_charge"]})
        write(range_extender, {"values": {"enderio:wireless_charger_antenna": {"#enderio:range_extender": 29}},
                               "remove": ["enderio:wireless_charger_antenna_advanced"]})
    else:
        (pack / "data/enderio" / vat).unlink()
        (pack / "data/enderio" / range_extender).unlink()


def run_client(args, server, phase, report):
    output = ROOT / "build/port-evidence" / (args.name + "-" + phase)
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
               "--launch", str(args.launch.resolve()), "--scenario", "multiplayer",
               "--server", f"127.0.0.1:{args.port}", "--multiplayer-phase", phase,
               "--timeout", "300", "--output", str(output)]
    if args.data_maps:
        command.append("--data-maps")
    with (output.parent / (output.name + "-run.log")).open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            print(f"Client {phase}: actual TCP world connection established", flush=True)
            if phase == "exercise":
                if args.data_maps:
                    pack = server.directory / "port-multiplayer/datapacks/port_data_maps"
                    wait_signal(client, output, "data_maps_initial")
                    write_data_map_fixture(pack, True)
                    server.command("reload")
                    wait_signal(client, output, "data_maps_override")
                    write_data_map_fixture(pack, False)
                    server.command("reload")
                    wait_signal(client, output, "data_maps_final")
                server.command("clear EnderIOQA")
                server.command("item replace entity EnderIOQA inventory.0 with minecraft:water_bucket 1")
                server.command("tp EnderIOQA 0.5 80 4.5 180 15")
                wait_signal(client, output, "mode_changed")
                server.assert_state("tcp_widget_bucket_state", tank_state(1000, True), report["assertions"])
                server.command('data merge block 0 80 0 {Fluids:{Tanks:[{Index:0,Contents:{FluidName:"minecraft:water",Amount:1500}}]}}')
                server.assert_state("tcp_updated_state", tank_state(1500, True), report["assertions"])
            client.wait(timeout=310)
            summary = json.loads((output / "summary.json").read_text())
            if client.returncode != 0 or summary["status"] != "pass":
                raise AssertionError(f"Client {phase} failed; see {output}")
        finally:
            stop_client(client)
            summary_path = output / "summary.json"
            report["clients"].append({"phase": phase, "exit": client.returncode, "output": str(output),
                                      "summary": json.loads(summary_path.read_text()) if summary_path.exists() else None})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--installer", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--launch", required=True, type=Path)
    parser.add_argument("--port", type=int, default=25641)
    parser.add_argument("--data-maps", action="store_true", help="Exercise live server-only data map and tag changes")
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a single directory name")
    directory = ROOT / "build/port-runtime" / args.name
    directory.mkdir(parents=True, exist_ok=False)
    artifact = args.jar.read_bytes()
    report = {"passed": False, "port": args.port, "serverAddress": "127.0.0.1", "onlineMode": False,
              "jar": str(args.jar.resolve()), "sha256": hashlib.sha256(artifact).hexdigest(),
              "installerSha256": digest(args.installer), "assertions": [], "clients": [], "servers": [],
              "dataMaps": args.data_maps}
    try:
        with (directory / "installer.log").open("w") as log:
            subprocess.run([args.java, "-Djava.awt.headless=true", "-jar", str(args.installer.resolve()),
                            "--installServer", str(directory)], cwd=directory,
                           stdout=log, stderr=subprocess.STDOUT, check=True)
        (directory / "mods").mkdir(exist_ok=True)
        (directory / "mods/enderio.jar").write_bytes(artifact)
        (directory / "eula.txt").write_text("eula=true\n")
        flat = json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1},
                                      {"block": "minecraft:dirt", "height": 2},
                                      {"block": "minecraft:grass_block", "height": 1}],
                           "biome": "minecraft:plains"}, separators=(",", ":"))
        (directory / "server.properties").write_text(
            f"server-ip=127.0.0.1\nserver-port={args.port}\nonline-mode=false\nenforce-secure-profile=false\n"
            f"level-name=port-multiplayer\nlevel-type=minecraft:flat\ngenerator-settings={flat}\n"
            "generate-structures=false\nview-distance=4\nsimulation-distance=4\nmax-players=2\n"
            "spawn-protection=0\nallow-flight=true\ndifficulty=peaceful\ngamemode=survival\n")
        if args.data_maps:
            pack = directory / "port-multiplayer/datapacks/port_data_maps"
            pack.mkdir(parents=True)
            (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 15, "description": "Ender IO map QA"}}))
        for stage in (1, 2):
            print(f"Starting actual dedicated server session {stage}", flush=True)
            server = Server(directory, args.java, stage)
            try:
                server.wait('For help, type "help"')
                server.command("forceload add -4 -4 4 7")
                if stage == 1:
                    for command in (
                        "fill -4 79 -4 4 79 7 minecraft:stone", "setworldspawn 0 80 4", "gamerule spawnRadius 0",
                        "gamerule doMobSpawning false", "gamerule doDaylightCycle false", "time set noon",
                        "setblock 0 80 0 enderio:fluid_tank", "setblock 2 80 0 enderio:pressurized_fluid_tank",
                        "setblock -2 80 0 enderio:enchanter"):
                        server.command(command)
                else:
                    server.assert_state("disk_restart_preserves_tcp_changes", tank_state(1500), report["assertions"])
                run_client(args, server, "exercise" if stage == 1 else "verify", report)
                server.assert_state(f"state_after_client_session_{stage}", tank_state(1500), report["assertions"])
                server.command("save-all flush")
            finally:
                status = server.stop()
                errors = [line.strip() for line in server.lines if "/ERROR]" in line or "/FATAL]" in line]
                report["servers"].append({"session": stage, "exit": status, "errors": errors, "log": str(server.log_path)})
            if status != 0 or errors:
                raise AssertionError(f"Dedicated server session {stage} failed; see {server.log_path}")
        launch = json.loads(args.launch.read_text())
        client_jar = Path(launch["directory"]) / "mods/enderio.jar"
        report["clientJarSha256"] = digest(client_jar)
        if report["clientJarSha256"] != report["sha256"]:
            raise AssertionError("Client and server did not exercise the same distributable bytes")
        report["passed"] = True
    finally:
        path = directory / "multiplayer-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Multiplayer: passed={report['passed']}; {len(report['assertions'])} server assertions; {path}", flush=True)


if __name__ == "__main__":
    main()
