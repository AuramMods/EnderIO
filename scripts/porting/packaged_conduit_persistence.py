#!/usr/bin/env python3
"""Load, unload, restart and break a mixed conduit bundle in an official Forge server."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from packaged_server_smoke import FORGE, ROOT, Server, digest


POS = "1024 80 1024"
CHUNK = "1024 1024"
WATER = '{side:"west",fluid:{FluidName:"minecraft:water",Amount:750,Tag:{Conservation:42}}}'
OXYGEN = '{side:"east",chemical:{chemical_type:"gas",id:"mekanism:oxygen",amount:4294967297L}}'
NODES = ('{Conduit:"enderio:fluid",Data:{type:"enderio:fluid",pending:[' + WATER + ']}},'
         '{Conduit:"enderio:chemical",Data:{type:"enderio:chemical",pending:[' + OXYGEN + ']}}')
BUNDLE = '{Conduits:["enderio:fluid","enderio:chemical"],Connections:[[],[]],NodeData:[' + NODES + ']}'
FLUID_DROP = '{Item:{id:"enderio:conduit",Count:1b,tag:{"enderio:retained_fluid":{pending:[' + WATER + ']}}}}'
CHEMICAL_DROP = ('{Item:{id:"enderio:conduit",Count:1b,tag:{"enderio:data":{"enderio:retained_chemical":'
                 '{pending:[' + OXYGEN + ']}}}}}')


def exercise(server, stage, assertions):
    server.wait('For help, type "help"')
    server.command(f"forceload add {CHUNK}")
    server.assert_state(f"chunk_loaded_stage_{stage}", f"if loaded {POS}", assertions)
    if stage == 1:
        server.command("fill 1022 79 1022 1026 79 1026 minecraft:stone")
        server.command(f"setblock {POS} enderio:conduit{BUNDLE}")
    if stage in (1, 2):
        server.command(f"data get block {POS}")
        server.assert_state(f"retained_bundle_stage_{stage}",
                            f"if data block {POS} {{NodeData:[{NODES}]}}", assertions)
    if stage == 1:
        server.command(f"forceload remove {CHUNK}")
        server.assert_state("chunk_actually_unloaded", f"unless loaded {POS}", assertions, timeout=40)
        server.command(f"forceload add {CHUNK}")
        server.assert_state("chunk_actually_loaded_again", f"if loaded {POS}", assertions)
        server.assert_state("retained_bundle_after_chunk_reload",
                            f"if data block {POS} {{NodeData:[{NODES}]}}", assertions)
    if stage == 2:
        server.command("scoreboard objectives add port_count dummy")
        server.command(f"setblock {POS} minecraft:air destroy")
    if stage in (2, 3):
        server.assert_state(f"removed_bundle_stage_{stage}", f"if block {POS} minecraft:air", assertions)
        for resource, nbt in (("fluid", FLUID_DROP), ("chemical", CHEMICAL_DROP)):
            marker = "Port_" + resource
            server.command(f"execute positioned {POS} store result score {marker} port_count "
                           f"if entity @e[type=minecraft:item,distance=..4,nbt={nbt}]")
            server.assert_state(f"one_{resource}_drop_stage_{stage}",
                                f"if score {marker} port_count matches 1", assertions)
    server.command("save-all flush")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--installer", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--mekanism", required=True, type=Path)
    parser.add_argument("--port", type=int, default=25643)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("--name must be a single directory name")

    directory = ROOT / "build/port-runtime" / args.name
    directory.mkdir(parents=True, exist_ok=False)
    report = {"passed": False, "forge": FORGE, "installerSha256": digest(args.installer),
              "artifacts": {}, "assertions": [], "servers": []}
    try:
        mods = directory / "mods"
        mods.mkdir()
        for name, artifact in (("enderio.jar", args.jar), ("mekanism.jar", args.mekanism)):
            contents = artifact.read_bytes()
            (mods / name).write_bytes(contents)
            report["artifacts"][name] = {"source": str(artifact.resolve()), "sha256": hashlib.sha256(contents).hexdigest()}
        with (directory / "installer.log").open("w") as log:
            subprocess.run([args.java, "-Djava.awt.headless=true", "-jar", str(args.installer.resolve()),
                            "--installServer", str(directory)], cwd=directory, stdout=log, stderr=subprocess.STDOUT,
                           check=True)
        (directory / "eula.txt").write_text("eula=true\n")
        flat = json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1},
                                      {"block": "minecraft:dirt", "height": 2},
                                      {"block": "minecraft:grass_block", "height": 1}],
                           "biome": "minecraft:plains"}, separators=(",", ":"))
        (directory / "server.properties").write_text(
            f"server-ip=127.0.0.1\nserver-port={args.port}\nlevel-name=port-conduits\n"
            f"level-type=minecraft:flat\ngenerator-settings={flat}\n"
            "generate-structures=false\nview-distance=3\nsimulation-distance=3\n")
        for stage in (1, 2, 3):
            print(f"Starting packaged dedicated server stage {stage}", flush=True)
            server = Server(directory, args.java, stage)
            try:
                exercise(server, stage, report["assertions"])
            finally:
                exit_status = server.stop()
                errors = [line.strip() for line in server.lines if "/ERROR]" in line or "/FATAL]" in line]
                report["servers"].append({"stage": stage, "pid": server.process.pid, "exit": exit_status,
                                          "log": str(server.log_path), "errors": errors})
            if exit_status != 0 or errors:
                raise RuntimeError(f"Server stage {stage} failed; see {server.log_path}")
        report["passed"] = True
    finally:
        output = directory / "conduit-persistence-results.json"
        output.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Conduit persistence passed={report['passed']}, {len(report['assertions'])} assertions, {output}", flush=True)


if __name__ == "__main__":
    main()
