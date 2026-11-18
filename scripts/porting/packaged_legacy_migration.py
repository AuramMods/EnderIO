#!/usr/bin/env python3
"""Copy and migrate an actual Ender IO 6.2.18-beta Forge 1.20.1 tank fixture."""

import argparse
import json
from pathlib import Path
import shutil
import sys

sys.dont_write_bytecode = True
from packaged_server_smoke import FORGE, ROOT, Server, digest


WORLD = "port-migration"
TANKS = {
    0: ("fluid_tank", 16000, "minecraft:water", "minecraft:water", 11500),
    2: ("pressurized_fluid_tank", 32000, "enderio:xp_juice", "enderio:fluid_xp_juice_still", 21750),
}
RENAMES = {8: ("copper_alloy_block", "conductive_alloy_block"),
           12: ("industrial_insulation_block", "industrial_insulation")}


def tank_state(x, legacy):
    _, capacity, old_fluid, new_fluid, amount = TANKS[x]
    fluid_id = old_fluid if legacy else new_fluid
    stack = f'FluidName:"{fluid_id}",Amount:{amount},Tag:{{PortLegacy:42}}'
    entry = f"{stack},Capacity:{capacity},Index:0" if legacy else f"Index:0,Contents:{{{stack}}}"
    key = "Fluid" if legacy else "Fluids"
    count = "7b" if legacy else "7"
    return (f'{{{key}:{{Tanks:[{{{entry}}}]}},Items:{{Items:[{{Slot:1,id:"minecraft:diamond",Count:{count}}}]}}}}')


def exercise(server, stage, results):
    server.wait('For help, type "help"')
    server.command("forceload add 0 0 15 15")
    legacy = stage == "legacy"
    if legacy:
        server.command("fill 0 79 0 14 79 4 minecraft:stone")
        for x, tank in TANKS.items():
            server.command(f"setblock {x} 80 0 enderio:{tank[0]}")
            server.command(f"data merge block {x} 80 0 {tank_state(x, True)}")
        for x, names in RENAMES.items():
            server.command(f"setblock {x} 80 0 enderio:{names[0]}")
        server.command('setblock 10 80 0 minecraft:chest{Items:[{Slot:0b,id:"enderio:copper_alloy_ingot",Count:5b}]}')
        server.command("setblock 14 80 0 enderio:alloy_smelter")
        server.command('data merge block 14 80 0 {Energy:{EnergyStored:1250},IoConfig:{"5":2b},RedstoneControl:1,Mode:1,'
                       'Items:{Size:5,Items:[{Slot:4,id:"enderio:basic_capacitor",Count:1b}]}}')

    for x in TANKS:
        server.command(f"data get block {x} 80 0")
        server.assert_state(f"tank_{x}_{stage}", f"if data block {x} 80 0 {tank_state(x, legacy)}", results)
    for x, names in RENAMES.items():
        server.assert_state(f"block_{x}_{stage}", f"if block {x} 80 0 enderio:{names[0 if legacy else 1]}", results)
    ingot = "copper_alloy_ingot" if legacy else "conductive_alloy_ingot"
    server.assert_state(f"chest_{stage}", f'if data block 10 80 0 {{Items:[{{Slot:0b,id:"enderio:{ingot}",Count:5b}}]}}', results)
    machine = ('{Energy:{EnergyStored:1250},IoConfig:{"5":2b},RedstoneControl:1,Mode:1}' if legacy else
               '{EnergyStored:1250,IOConfig:{Modes:{east:"pull"}},RedstoneControl:"active_with_signal",Mode:1}')
    server.command("data get block 14 80 0")
    server.assert_state(f"alloy_smelter_{stage}", f"if data block 14 80 0 {machine}", results)
    server.command("save-all flush")


def run_server(directory, java, stage, report):
    server = Server(directory, java, stage)
    try:
        exercise(server, stage, report["assertions"])
    finally:
        exit_status = server.stop()
        errors = [line.strip() for line in server.lines if "/ERROR]" in line or "/FATAL]" in line]
        # Forge reports unresolved IDs even when the old world contains no instances.
        registry_errors = [line for line in errors if "[ne.mi.re.GameData/REGISTRIES]" in line]
        runtime_errors = [line for line in errors if line not in registry_errors]
        report["servers"].append({"stage": stage, "exit": exit_status, "log": str(server.log_path),
                                  "registryErrors": registry_errors, "runtimeErrors": runtime_errors})
    if exit_status or runtime_errors or (stage == "legacy" and registry_errors):
        raise RuntimeError(f"Server stage {stage} failed; see {server.log_path}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path, help="Directory installed by the official Forge installer")
    parser.add_argument("--legacy-jar", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--port", type=int, default=25647)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("--name must be a single directory name")

    directory = ROOT / "build/port-runtime" / args.name
    directory.mkdir(parents=True)
    report = {"passed": False, "forge": FORGE, "legacySha256": digest(args.legacy_jar),
              "portSha256": digest(args.jar), "assertions": [], "servers": []}
    try:
        legacy = directory / "legacy"
        converted = directory / "converted"
        flat = '{"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:plains"}'
        for profile, jar in ((legacy, args.legacy_jar), (converted, args.jar)):
            profile.mkdir()
            shutil.copytree(args.forge_server / "libraries", profile / "libraries")
            (profile / "mods").mkdir()
            shutil.copyfile(jar, profile / "mods" / "enderio.jar")
            (profile / "eula.txt").write_text("eula=true\n")
            (profile / "server.properties").write_text(
                f"server-ip=127.0.0.1\nserver-port={args.port}\nlevel-name={WORLD}\n"
                f"level-type=minecraft:flat\ngenerator-settings={flat}\n"
                "generate-structures=false\nview-distance=3\nsimulation-distance=3\n")
        run_server(legacy, args.java, "legacy", report)
        shutil.copytree(legacy / WORLD, converted / WORLD)
        run_server(converted, args.java, "converted", report)
        run_server(converted, args.java, "restart", report)
        report["passed"] = True
    finally:
        output = directory / "legacy-migration-results.json"
        output.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Legacy migration passed={report['passed']}, {len(report['assertions'])} assertions, {output}", flush=True)


if __name__ == "__main__":
    main()
