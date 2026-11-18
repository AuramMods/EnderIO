#!/usr/bin/env python3
"""Copy and migrate an actual Ender IO 6.2.18-beta Forge 1.20.1 fixture."""

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
CORE_CONDUITS = [(kind + "_conduit", kind) for kind in
                 ("item", "energy", "redstone", "fluid", "pressurized_fluid", "ender_fluid")]
INTEGRATION_CONDUITS = [(kind + "_conduit", variant) for kind, variant in
                        (("me", "me"), ("dense_me", "dense_me"), ("refined_storage", "rs"), ("chemical", "chemical"),
                         ("pressurized_chemical", "pressurized_chemical"), ("ender_chemical", "ender_chemical"), ("heat", "heat"))]


def tank_state(x, legacy):
    _, capacity, old_fluid, new_fluid, amount = TANKS[x]
    fluid_id = old_fluid if legacy else new_fluid
    stack = f'FluidName:"{fluid_id}",Amount:{amount},Tag:{{PortLegacy:42}}'
    entry = f"{stack},Capacity:{capacity},Index:0" if legacy else f"Index:0,Contents:{{{stack}}}"
    key = "Fluid" if legacy else "Fluids"
    count = "7b" if legacy else "7"
    return (f'{{{key}:{{Tanks:[{{{entry}}}]}},Items:{{Items:[{{Slot:1,id:"minecraft:diamond",Count:{count}}}]}}}}')


def exercise(server, stage, results, conduit_fixture, loose_conduits):
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
    if conduit_fixture:
        exercise_conduits(server, stage, results)
    if loose_conduits:
        exercise_loose_conduits(server, stage, results, loose_conduits)
    server.command("save-all flush")


def loose_stack(slot, original, variant, count, legacy, vanilla_inventory):
    slot_nbt = str(slot) + ("b" if vanilla_inventory else "")
    count_nbt = str(count) + ("b" if legacy or vanilla_inventory else "")
    item = original if legacy else "conduit"
    tag = f"PortSavedType:{count}"
    if not legacy:
        tag += ',"enderio:data":{"enderio:conduit":"enderio:' + variant + '"}'
    return f'{{Slot:{slot_nbt},id:"enderio:{item}",Count:{count_nbt},tag:{{{tag}}}}}'


def exercise_loose_conduits(server, stage, results, conduits):
    legacy = stage == "legacy"
    barrel = "9 80 5"
    machine = "14 80 5"
    stacks = [loose_stack(i, old, variant, i + 3, legacy, True) for i, (old, variant) in enumerate(conduits)]
    machine_stack = loose_stack(3, "item_conduit", "item", 9, legacy, False)
    if legacy:
        server.command(f'setblock {barrel} minecraft:barrel{{Items:[{",".join(stacks)}]}}')
        server.command("setblock 14 79 5 minecraft:stone")
        server.command(f"setblock {machine} enderio:alloy_smelter")
        server.command(f"data merge block {machine} {{RedstoneControl:3,Items:{{Size:5,Items:[{machine_stack}]}}}}")

    server.command(f"data get block {barrel} Items")
    for i, stack in enumerate(stacks):
        server.assert_state(f"conduit_item_{conduits[i][1]}_{stage}", f"if data block {barrel} Items[{stack}]", results)
    server.assert_state(f"no_extra_conduit_stacks_{stage}", f"unless data block {barrel} Items[{len(stacks)}]", results)
    server.assert_state(f"machine_conduit_item_{stage}", f"if data block {machine} Items.Items[{machine_stack}]", results)


def exercise_conduits(server, stage, results):
    legacy = stage == "legacy"
    source = "1023 80 8"
    destination = "1024 80 8"
    upstream = "1022 80 8"
    downstream = "1025 80 8"
    chunks = "1008 0 1039 15"
    server.command(f"forceload add {chunks}")
    server.assert_state(f"both_conduit_chunks_loaded_{stage}", f"if loaded {source} if loaded {destination}", results)
    if legacy:
        server.command("fill 1017 79 7 1026 79 9 minecraft:stone")
        server.command(f'setblock {upstream} minecraft:barrel'
                       '{Items:[{Slot:0b,id:"minecraft:diamond",Count:7b},{Slot:1b,id:"minecraft:emerald",Count:5b}]}')
        server.command(f"setblock {downstream} minecraft:barrel")
        output = server.command("port_legacy_fixture")
        if "Legacy conduit fixture created" not in output:
            raise AssertionError(output)

    for x in (1018, 1023, 1024):
        server.command(f"data get block {x} 80 8")
        server.assert_state(f"conduit_{x}_{stage}", f"if block {x} 80 8 enderio:conduit", results)

    if not legacy:
        server.assert_state(f"separate_graph_{stage}",
                            'if data block 1018 80 8 {Conduits:["enderio:item"]} unless data block 1018 80 8 Conduits[1]', results)
        server.assert_state(f"cross_chunk_link_{stage}",
                            f'if data block {source} Connections[1][{{Side:"east",Status:"connected_conduit"}}] '
                            f'if data block {destination} Connections[1][{{Side:"west",Status:"connected_conduit"}}] '
                            f'if data block {source} Connections[1][{{Side:"north",Status:"disabled"}}]', results)
        server.assert_state(f"extract_settings_{stage}",
                            f'if data block {source} Connections[1][{{Side:"west",Config:{{is_extract:1b,is_round_robin:1b,'
                            'is_self_feed:1b,priority:42,extract_channel:"blue",extract_redstone_channel:"orange",'
                            'extract_redstone_control:"always_active"}}] '
                            f'if data block {source} NodeData[{{Conduit:"enderio:item"}}].Data.round_robin_indexes.west', results)
        server.assert_state(f"insert_settings_{stage}",
                            f'if data block {destination} Connections[1][{{Side:"east",Config:{{is_insert:1b,priority:71,'
                            'insert_channel:"blue",extract_redstone_channel:"light_blue"}}]', results)
        server.assert_state(f"facade_{stage}",
                            f'if data block {destination} {{FacadeProvider:{{id:"enderio:conduit_facade",'
                            'tag:{"enderio:data":{"enderio:block_paint":{paint:"minecraft:polished_andesite"}}}}}', results)

        if stage == "converted":
            server.command(f"forceload remove {chunks}")
            server.assert_state("converted_chunks_unloaded", f"unless loaded {source} unless loaded {destination}", results, timeout=40)
            server.command(f"forceload add {chunks}")
            server.assert_state("converted_chunks_reloaded", f"if loaded {source} if loaded {destination}", results)
        server.command(f"item replace block {upstream} container.0 with minecraft:diamond 3")

    count = {"legacy": 7, "converted": 10, "restart": 13}[stage]
    server.assert_state(f"diamond_delivery_{stage}",
                        f'if data block {downstream} {{Items:[{{id:"minecraft:diamond",Count:{count}b}}]}} '
                        f'unless data block {upstream} Items[{{id:"minecraft:diamond"}}]', results)
    server.assert_state(f"filter_rejects_emeralds_{stage}",
                        f'if data block {upstream} {{Items:[{{id:"minecraft:emerald",Count:5b}}]}} '
                        f'unless data block {downstream} Items[{{id:"minecraft:emerald"}}]', results)


def run_server(directory, java, stage, report, conduit_fixture, loose_conduits):
    server = Server(directory, java, stage)
    try:
        exercise(server, stage, report["assertions"], conduit_fixture, loose_conduits)
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
    parser.add_argument("--conduit-fixture", type=Path, help="Fixture mod built with legacy_fixture/build.py")
    parser.add_argument("--loose-conduits", action="store_true", help="Migrate the six core conduit item stacks")
    parser.add_argument("--loose-integration-conduits", action="store_true", help="Include AE2, Mekanism and Refined Storage conduit items")
    parser.add_argument("--mod", action="append", type=Path, default=[], help="Install a Forge dependency in both profiles")
    parser.add_argument("--port", type=int, default=25647)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("--name must be a single directory name")

    directory = ROOT / "build/port-runtime" / args.name
    directory.mkdir(parents=True)
    report = {"passed": False, "forge": FORGE, "legacySha256": digest(args.legacy_jar),
              "portSha256": digest(args.jar), "mods": {mod.name: digest(mod) for mod in args.mod},
              "assertions": [], "servers": []}
    loose_conduits = []
    if args.loose_conduits or args.loose_integration_conduits:
        loose_conduits.extend(CORE_CONDUITS)
    if args.loose_integration_conduits:
        loose_conduits.extend(INTEGRATION_CONDUITS)
    try:
        legacy = directory / "legacy"
        converted = directory / "converted"
        flat = '{"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:plains"}'
        for profile, jar in ((legacy, args.legacy_jar), (converted, args.jar)):
            profile.mkdir()
            shutil.copytree(args.forge_server / "libraries", profile / "libraries")
            (profile / "mods").mkdir()
            shutil.copyfile(jar, profile / "mods" / "enderio.jar")
            for mod in args.mod:
                shutil.copyfile(mod, profile / "mods" / mod.name)
            (profile / "eula.txt").write_text("eula=true\n")
            (profile / "server.properties").write_text(
                f"server-ip=127.0.0.1\nserver-port={args.port}\nlevel-name={WORLD}\n"
                f"level-type=minecraft:flat\ngenerator-settings={flat}\n"
                "generate-structures=false\nview-distance=3\nsimulation-distance=3\n")
        if args.conduit_fixture:
            shutil.copyfile(args.conduit_fixture, legacy / "mods" / "port-legacy-fixture.jar")
            report["fixtureSha256"] = digest(args.conduit_fixture)
        run_server(legacy, args.java, "legacy", report, args.conduit_fixture, loose_conduits)
        shutil.copytree(legacy / WORLD, converted / WORLD)
        run_server(converted, args.java, "converted", report, args.conduit_fixture, loose_conduits)
        run_server(converted, args.java, "restart", report, args.conduit_fixture, loose_conduits)
        report["passed"] = True
    finally:
        output = directory / "legacy-migration-results.json"
        output.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Legacy migration passed={report['passed']}, {len(report['assertions'])} assertions, {output}", flush=True)


if __name__ == "__main__":
    main()
