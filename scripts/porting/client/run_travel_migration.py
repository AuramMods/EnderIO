#!/usr/bin/env python3
"""Exercise anchors from an official 6.2 save using the packaged port and hidden TCP client."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from run_multiplayer import ROOT, Server, digest, stop_client, wait_signal


WORLD = "port-travel"
PLAYER = "EnderIOQA"
NETHER = "execute in minecraft:the_nether run "


def load_fixture_chunks(server, results, stage):
    for prefix in ("", NETHER):
        server.command(prefix + "forceload add 32 -4 63 4")
    server.assert_state(f"overworld_fixture_loaded_{stage}", "if loaded 32 80 0 if loaded 40 80 0", results)
    server.assert_state(f"nether_fixture_loaded_{stage}", "in minecraft:the_nether if loaded 32 80 0", results)


def create_anchors(server):
    for command in ("fill 31 79 -2 61 79 2 minecraft:stone", "gamerule spawnRadius 0", "setworldspawn 43 80 0",
                    "gamerule doMobSpawning false", "gamerule doDaylightCycle false", "time set noon",
                    NETHER + "fill 31 79 -2 36 79 2 minecraft:stone",
                    NETHER + "fill 31 80 -2 36 84 2 minecraft:air"):
        server.command(command)
    output = server.command("port_legacy_travel")
    if "Legacy travel fixture created" not in output:
        raise AssertionError(output)
    server.command('data merge block 40 80 0 {Paint:"minecraft:purpur_block"}')


def assert_anchors(server, stage, results):
    count = "1b" if stage == "legacy" else "1"
    alloy = "copper_alloy_ingot" if stage == "legacy" else "conductive_alloy_ingot"
    for name, pos, item, dimension in (("hub", "32 80 0", "minecraft:compass", ""),
                                       ("workshop", "40 80 0", "enderio:" + alloy, ""),
                                       ("lift", "32 88 0", "minecraft:ender_pearl", ""),
                                       ("relay", "32 80 0", "minecraft:blaze_rod", "in minecraft:the_nether ")):
        conditions = (f'{dimension}if data block {pos} {{Items:{{Items:[{{Slot:0,id:"{item}",Count:{count}}}]}}}}')
        if name == "workshop":
            conditions += ' if data block 40 80 0 {Paint:"minecraft:purpur_block"}'
        server.assert_state(f"{name}_inventory_{stage}", conditions, results)


def run_client(args, server, stage, report):
    output = ROOT / "build/port-evidence" / (args.name + "-" + stage)
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
               "--launch", str(args.launch.resolve()), "--scenario", "travel", "--timeout", "245",
               "--server", f"127.0.0.1:{args.port}", "--output", str(output),
               "--multiplayer-phase", "verify" if stage == "restart" else "exercise"]
    with (output.parent / (output.name + "-run.log")).open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        summary_path = output / "summary.json"
        try:
            wait_signal(client, output, "connected")
            server.command(f"execute in minecraft:overworld run tp {PLAYER} 43.5 80 0.5 90 5")
            wait_signal(client, output, "menu_verified")
            server.command(f"tp {PLAYER} 32.5 81 0.5 -90 10")
            wait_signal(client, output, "workshop")
            server.assert_state(f"real_horizontal_travel_{stage}",
                                f"in minecraft:overworld positioned 40.5 81 0.5 if entity @a[name={PLAYER},distance=..0.4]",
                                report["assertions"])
            server.command(f"tp {PLAYER} 32.5 81 0.5 -90 10")
            wait_signal(client, output, "lift")
            server.assert_state(f"real_elevator_travel_{stage}",
                                f"in minecraft:overworld positioned 32.5 89 0.5 if entity @a[name={PLAYER},distance=..0.4]",
                                report["assertions"])
            if stage == "converted":
                server.command(f"item replace entity {PLAYER} hotbar.0 from block 43 79 1 container.0")
                server.command("item replace block 43 79 1 container.0 with minecraft:air")
                server.assert_state("staff_removed_from_source_barrel", "unless data block 43 79 1 Items[0]", report["assertions"])
            else:
                server.assert_state("staff_charge_restored_from_player_save", staff_charge(4000), report["assertions"])
            server.command(f"tp {PLAYER} 48.5 80 0.5 90 8")
            wait_signal(client, output, "staff_workshop")
            charge = 5000 if stage == "converted" else 3000
            server.assert_state(f"staff_workshop_{stage}",
                                f"positioned 40.5 81 0.5 if entity @a[name={PLAYER},distance=..0.4] " + staff_charge(charge),
                                report["assertions"])
            # The ray reaches the same floor block at standing and crouching eye heights.
            server.command(f"tp {PLAYER} 52.9 80 0.5 -90 22")
            wait_signal(client, output, "staff_blink")
            server.assert_state(f"staff_blink_{stage}",
                                f"positioned 56.5 80 0.5 if entity @a[name={PLAYER},distance=..0.4] " + staff_charge(charge - 1000),
                                report["assertions"])
            server.command(NETHER + f"tp {PLAYER} 34.5 80 0.5 90 0")
            client.wait(timeout=60)
            if client.returncode or json.loads(summary_path.read_text())["status"] != "pass":
                raise AssertionError(f"Client stage {stage} failed; see {output}")
        finally:
            stop_client(client)
            report["clients"].append({"stage": stage, "exit": client.returncode, "output": str(output),
                                      "summary": json.loads(summary_path.read_text()) if summary_path.exists() else None})


def staff_charge(amount):
    tag = '{Slot:0b,id:"enderio:staff_of_travelling",tag:{"enderio:data":{"enderio:energy":' + str(amount) + '}}}'
    return f"if data entity {PLAYER} Inventory[{tag}]"


def run_stage(args, profile, stage, report):
    server = Server(profile, args.java, stage)
    try:
        server.wait('For help, type "help"')
        load_fixture_chunks(server, report["assertions"], stage)
        if stage == "legacy":
            create_anchors(server)
            server.assert_state("source_staff_charge", 'if data block 43 79 1 '
                                '{Items:[{Slot:0b,id:"enderio:staff_of_travelling",tag:{Energy:{EnergyStored:6000}}}]}',
                                report["assertions"])
        assert_anchors(server, stage, report["assertions"])
        if stage != "legacy":
            run_client(args, server, stage, report)
        server.command("save-all flush")
    finally:
        status = server.stop()
        errors = [line.strip() for line in server.lines if "/ERROR]" in line or "/FATAL]" in line]
        registry = [line for line in errors if "[ne.mi.re.GameData/REGISTRIES]" in line
                    and ("Unidentified mapping from registry" in line or "There are unidentified mappings" in line)]
        report["servers"].append({"stage": stage, "exit": status, "registryErrors": registry,
                                  "runtimeErrors": [line for line in errors if line not in registry], "log": str(server.log_path)})
    if status or len(errors) != len(registry) or (stage != "converted" and registry):
        raise AssertionError(f"Server stage {stage} failed; see {server.log_path}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--legacy-jar", required=True, type=Path)
    parser.add_argument("--legacy-fixture", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--launch", required=True, type=Path)
    parser.add_argument("--port", type=int, default=25649)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("--name must be a single directory name")

    directory = ROOT / "build/port-runtime" / args.name
    directory.mkdir(parents=True)
    report = {"passed": False, "portSha256": digest(args.jar), "legacySha256": digest(args.legacy_jar),
              "fixtureSha256": digest(args.legacy_fixture), "assertions": [], "clients": [], "servers": []}
    try:
        legacy = directory / "legacy"
        converted = directory / "converted"
        flat = '{"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:plains"}'
        for profile, jar in ((legacy, args.legacy_jar), (converted, args.jar)):
            profile.mkdir()
            shutil.copytree(args.forge_server / "libraries", profile / "libraries")
            mods = profile / "mods"
            mods.mkdir()
            shutil.copyfile(jar, mods / "enderio.jar")
            (profile / "eula.txt").write_text("eula=true\n")
            (profile / "server.properties").write_text(
                f"server-ip=127.0.0.1\nserver-port={args.port}\nonline-mode=false\nenforce-secure-profile=false\n"
                f"level-name={WORLD}\nlevel-type=minecraft:flat\ngenerator-settings={flat}\n"
                "generate-structures=false\nview-distance=4\nsimulation-distance=4\n"
                "spawn-protection=0\ndifficulty=peaceful\n")
        shutil.copyfile(args.legacy_fixture, legacy / "mods/port-legacy-fixture.jar")
        run_stage(args, legacy, "legacy", report)
        shutil.copytree(legacy / WORLD, converted / WORLD)
        run_stage(args, converted, "converted", report)
        run_stage(args, converted, "restart", report)
        launch = json.loads(args.launch.read_text())
        report["clientJarSha256"] = digest(Path(launch["directory"]) / "mods/enderio.jar")
        report["passed"] = report["clientJarSha256"] == report["portSha256"]
    finally:
        output = directory / "travel-migration-results.json"
        output.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Travel migration passed={report['passed']}. {len(report['assertions'])} server assertions. {output}", flush=True)
    if not report["passed"]:
        raise AssertionError("Client and server used different Ender IO jars")


if __name__ == "__main__":
    main()
