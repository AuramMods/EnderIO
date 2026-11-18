#!/usr/bin/env python3
"""Move tanks written by Forge 6.2 through normal player actions on the packaged port."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from run_multiplayer import ROOT, Server, digest, stop_client, wait_signal


WORLD = "port-carried-tanks"
BARREL = "6 80 3"
PLAYER = "EnderIOQA"
WATER = "minecraft:water"
XP = "enderio:fluid_xp_juice_still"
PLACED = {"untouched": (2, 0, WATER, 11500, 31), "partial": (4, 2, XP, 20750, 51),
          "drained": (0, 2, WATER, 0, 31)}


def contents(fluid, amount, mark):
    return f'{{FluidName:"{fluid}",Amount:{amount},Tag:{{PortLegacyCarried:{mark}}}}}' if amount else "{}"


def stored_state(fluid, amount, mark):
    return f'Fluids:{{Tanks:[{{Index:0,Contents:{contents(fluid, amount, mark)}}}]}}'


def placed_state(scenario):
    x, z, fluid, amount, mark = PLACED[scenario]
    pos = f"{x} 80 {z}"
    block = "enderio:pressurized_fluid_tank" if scenario == "partial" else "enderio:fluid_tank"
    machine = ('RedstoneControl:"active_with_signal",IOConfig:{Modes:{east:"pull"}},'
               'Items:{Items:[{Slot:1,id:"minecraft:diamond",Count:7}]}')
    result = f'if block {pos} {block} if data block {pos} {{{stored_state(fluid, amount, mark)},{machine}}}'
    return result if amount else result + f" unless data block {pos} Fluids.Tanks[0].Contents.FluidName"


def verify_receivers(server, stage, results):
    server.assert_state(f"water_receiver_{stage}", f'if data block 0 80 0 {{{stored_state(WATER, 11500, 31)}}}', results)
    server.assert_state(f"xp_receiver_{stage}", f'if data block 4 80 0 {{{stored_state(XP, 32000, 51)}}}', results)


def create_legacy(server, results):
    server.command("fill 0 79 0 15 79 12 minecraft:stone")
    server.command("setworldspawn 2 80 3")
    server.command("gamerule spawnRadius 0")
    server.command("gamerule doMobSpawning false")
    output = server.command("port_legacy_carried_tanks")
    if "Legacy carried tanks created" not in output:
        raise AssertionError(output)
    server.command(f"data get block {BARREL}")
    for slot, item, fluid, amount, mark in ((0, "fluid_tank", WATER, 11500, 31), (1, "pressurized_fluid_tank", "enderio:xp_juice", 21750, 51),
                                           (2, "fluid_tank", WATER, 11500, 31)):
        block_data = ('RedstoneControl:1,IoConfig:{"5":2b},Items:{Items:[{Slot:1,id:"minecraft:diamond",Count:7b}]},'
                      f'Fluid:{{Tanks:[{contents(fluid, amount, mark)}]}}')
        stack = f'{{Slot:{slot}b,id:"enderio:{item}",Count:1b,tag:{{BlockEntityTag:{{{block_data}}}}}}}'
        server.assert_state(f"legacy_carried_slot_{slot}", f"if data block {BARREL} Items[{stack}]", results)


def run_client(args, server, stage, report):
    output = ROOT / "build/port-evidence" / (args.name + "-" + stage)
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "carried-tanks", "--server", f"127.0.0.1:{args.port}", "--timeout", "225",
               "--multiplayer-phase", "verify" if stage == "restart" else "exercise", "--output", str(output)]
    with (output.parent / (output.name + "-run.log")).open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        summary_path = output / "summary.json"
        try:
            wait_signal(client, output, "connected")
            if stage == "converted":
                for slot, scenario in enumerate(PLACED):
                    x = PLACED[scenario][0]
                    server.command(f"tp {PLAYER} {x + 0.5} 80 3.5 180 10")
                    server.command(f"item replace entity {PLAYER} hotbar.0 from block {BARREL} container.{slot}")
                    server.command(f"item replace block {BARREL} container.{slot} with minecraft:air")
                    wait_signal(client, output, scenario + "_placement_requested")
                    server.command(f"data get block {x} 80 {PLACED[scenario][1]}")
                    server.assert_state(f"{scenario}_placement_preserves_machine_state", placed_state(scenario), report["assertions"])
                    wait_signal(client, output, scenario)
            client.wait(timeout=60)
            if client.returncode or json.loads(summary_path.read_text())["status"] != "pass":
                raise AssertionError(f"Client stage {stage} failed; see {output}")
        finally:
            stop_client(client)
            report["clients"].append({"stage": stage, "exit": client.returncode, "output": str(output),
                                      "summary": json.loads(summary_path.read_text()) if summary_path.exists() else None})


def run_stage(args, profile, stage, report):
    server = Server(profile, args.java, stage)
    try:
        server.wait('For help, type "help"')
        server.command("forceload add 0 0 15 15")
        server.assert_state(f"fixture_chunk_loaded_{stage}", f"if loaded {BARREL}", report["assertions"])
        if stage == "legacy":
            create_legacy(server, report["assertions"])
        else:
            if stage == "converted":
                server.command("setblock 0 80 0 enderio:fluid_tank")
                server.command(f"setblock 4 80 0 enderio:pressurized_fluid_tank{{{stored_state(XP, 31000, 51)}}}")
            else:
                for name in PLACED:
                    server.assert_state(f"{name}_disk_restored", placed_state(name), report["assertions"])
            run_client(args, server, stage, report)
            verify_receivers(server, stage, report["assertions"])
            server.assert_state(f"no_extra_tanks_in_source_barrel_{stage}", f"unless data block {BARREL} Items[0]", report["assertions"])
        server.command("save-all flush")
    finally:
        status = server.stop()
        errors = [line.strip() for line in server.lines if "/ERROR]" in line or "/FATAL]" in line]
        registry_errors = [line for line in errors if "[ne.mi.re.GameData/REGISTRIES]" in line
                           and ("Unidentified mapping from registry" in line or "There are unidentified mappings" in line)]
        report["servers"].append({"stage": stage, "exit": status, "registryErrors": registry_errors,
                                  "runtimeErrors": [line for line in errors if line not in registry_errors], "log": str(server.log_path)})
    if status or len(errors) != len(registry_errors) or (stage != "converted" and registry_errors):
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
    parser.add_argument("--port", type=int, default=25655)
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
                "generate-structures=false\nview-distance=4\nsimulation-distance=4\nspawn-protection=0\ndifficulty=peaceful\n")
        shutil.copyfile(args.legacy_fixture, legacy / "mods/port-legacy-fixture.jar")
        run_stage(args, legacy, "legacy", report)
        shutil.copytree(legacy / WORLD, converted / WORLD)
        run_stage(args, converted, "converted", report)
        run_stage(args, converted, "restart", report)
        launch = json.loads(args.launch.read_text())
        report["clientJarSha256"] = digest(Path(launch["directory"]) / "mods/enderio.jar")
        report["passed"] = report["clientJarSha256"] == report["portSha256"]
    finally:
        output = directory / "carried-tanks-results.json"
        output.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Carried tank migration passed={report['passed']}. {len(report['assertions'])} server assertions. {output}", flush=True)
    if not report["passed"]:
        raise AssertionError("Client and server used different Ender IO jars")


if __name__ == "__main__":
    main()
