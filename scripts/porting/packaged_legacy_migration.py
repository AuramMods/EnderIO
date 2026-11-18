#!/usr/bin/env python3
"""Copy and migrate an actual Ender IO 6.2.18-beta Forge 1.20.1 fixture."""

import argparse
import json
from pathlib import Path
import shutil
import sys
import time

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
STORAGE_ROUTES = {4: ("me", "me"), 7: ("dense_me", "dense_me"), 10: ("me", "dense_me"), 13: ("rs", "rs")}
MEKANISM_ROUTES = {0: "chemical", 3: "pressurized_chemical", 6: "ender_chemical",
                   9: "ender_chemical", 12: "heat", 15: "chemical"}
LIGHTS = {
    "light": (1028, 95, 1029, "floor", "east"),
    "light_inverted": (1034, 95, 1029, "ceiling", "west"),
    "powered_light": (1028, 95, 1040, "wall", "east"),
    "powered_light_inverted": (1034, 95, 1040, "floor", "north"),
    "powered_light_wireless": (1041, 95, 1030, "floor", "south"),
    "powered_light_inverted_wireless": (1048, 95, 1030, "wall", "north"),
}


def tank_state(x, legacy):
    _, capacity, old_fluid, new_fluid, amount = TANKS[x]
    fluid_id = old_fluid if legacy else new_fluid
    stack = f'FluidName:"{fluid_id}",Amount:{amount},Tag:{{PortLegacy:42}}'
    entry = f"{stack},Capacity:{capacity},Index:0" if legacy else f"Index:0,Contents:{{{stack}}}"
    key = "Fluid" if legacy else "Fluids"
    count = "7b" if legacy else "7"
    return (f'{{{key}:{{Tanks:[{{{entry}}}]}},Items:{{Items:[{{Slot:1,id:"minecraft:diamond",Count:{count}}}]}}}}')


def exercise(server, stage, results, conduit_fixture, loose_conduits, placed_storage_conduits,
             placed_mekanism_conduits, extraction_upgrades, legacy_lights):
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
    if placed_storage_conduits:
        exercise_storage_conduits(server, stage, results)
    if placed_mekanism_conduits:
        exercise_mekanism_conduits(server, stage, results)
    if extraction_upgrades:
        exercise_extraction_upgrades(server, stage, results)
    if legacy_lights:
        exercise_lights(server, stage, results)
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


def exercise_storage_conduits(server, stage, results):
    legacy = stage == "legacy"
    chunks = "1072 0 1103 15"
    server.command(f"forceload add {chunks}")
    server.assert_state(f"storage_chunks_loaded_{stage}", "if loaded 1087 80 4 if loaded 1088 80 4", results)
    if legacy:
        server.command("fill 1086 79 3 1089 79 14 minecraft:stone")
        for z in (4, 7, 10):
            server.command(f"setblock 1086 80 {z} ae2:creative_energy_cell")
            server.command(f"setblock 1089 80 {z} ae2:drive")
        server.command("setblock 1086 80 13 refinedstorage:creative_controller")
        server.command("setblock 1089 80 13 refinedstorage:disk_drive")
        output = server.command("port_legacy_storage_conduits")
        if "Legacy storage conduits created" not in output:
            raise AssertionError(output)

    if stage == "converted":
        assert_storage_routes(server, stage, results)
        server.command(f"forceload remove {chunks}")
        server.assert_state("storage_chunks_unloaded", "unless loaded 1087 80 4 unless loaded 1088 80 4", results, timeout=40)
        server.command(f"forceload add {chunks}")
        server.assert_state("storage_chunks_reloaded", "if loaded 1087 80 4 if loaded 1088 80 4", results)
        stage = "chunk_reload"
    assert_storage_routes(server, stage, results)


def assert_storage_routes(server, stage, results):
    legacy = stage == "legacy"
    for z in STORAGE_ROUTES:
        for x in range(1086, 1090):
            server.command(f"data get block {x} 80 {z}")
    for z, variants in STORAGE_ROUTES.items():
        for x, side, variant in zip((1087, 1088), ("east", "west"), variants):
            pos = f"{x} 80 {z}"
            old_id = "refined_storage" if variant == "rs" else variant
            conditions = (f'if data block {pos} {{ConduitBundle:{{Types:["enderio:{old_id}"]}}}}' if legacy else
                          f'if data block {pos} {{Conduits:["enderio:{variant}"]}} '
                          f'if data block {pos} Connections[0][{{Side:"{side}",Status:"connected_conduit"}}]')
            server.assert_state(f"storage_bundle_{x}_{z}_{stage}", conditions, results)
    command = "port_storage_native seed_legacy_owners" if legacy else "port_storage_native"
    assert_probe(server, f"native_storage_routes_and_owners_{stage}", command,
                 "Native AE2 and RS storage routes and owners verified", results)


def assert_probe(server, name, command, message, results):
    deadline = time.monotonic() + 12
    while time.monotonic() < deadline:
        output = server.command(command)
        if message in output:
            results.append({"assertion": name, "passed": True, "command": command})
            return
        time.sleep(0.2)
    results.append({"assertion": name, "passed": False, "command": command})
    raise AssertionError(f"{name} failed; see {server.log_path}")


def exercise_mekanism_conduits(server, stage, results):
    chunks = "1136 0 1167 15"
    left, right = "1151 80 12", "1152 80 12"
    server.command(f"forceload add {chunks}")
    server.assert_state(f"mekanism_chunks_loaded_{stage}", f"if loaded {left} if loaded {right}", results)
    if stage == "legacy":
        server.command("fill 1150 79 0 1153 79 15 minecraft:stone")
        for z, route in MEKANISM_ROUTES.items():
            block = "basic_thermodynamic_conductor" if route == "heat" else "ultimate_chemical_tank"
            for x in (1150, 1153):
                server.command(f"setblock {x} 80 {z} mekanism:{block}")
        for command, message in (
            ("port_mekanism_native configure", "Native Mekanism endpoints configured"),
            ("port_legacy_mekanism_conduits", "Legacy Mekanism conduits created"),
        ):
            output = server.command(command)
            if message not in output:
                raise AssertionError(output)
    assert_mekanism_routes(server, stage, results)
    if stage == "converted":
        server.command(f"forceload remove {chunks}")
        server.assert_state("mekanism_chunks_unloaded", f"unless loaded {left} unless loaded {right}", results, timeout=40)
        server.command(f"forceload add {chunks}")
        server.assert_state("mekanism_chunks_reloaded", f"if loaded {left} if loaded {right}", results)
        assert_mekanism_routes(server, "chunk_reload", results)
    if stage == "restart":
        output = server.command("port_mekanism_native seed_hydrogen")
        if "Hydrogen batch seeded" not in output:
            raise AssertionError(output)
        assert_probe(server, "mekanism_matching_lock", "port_mekanism_native check_hydrogen",
                     "Native Mekanism hydrogen lock passed its matching batch", results)


def assert_mekanism_routes(server, stage, results):
    legacy = stage == "legacy"
    for z, kind in MEKANISM_ROUTES.items():
        for x, inside, outside in ((1151, "east", "west"), (1152, "west", "east")):
            pos = f"{x} 80 {z}"
            server.command(f"data get block {pos}")
            if legacy:
                conditions = f'if data block {pos} {{ConduitBundle:{{Types:["enderio:{kind}"]}}}}'
            else:
                conditions = (f'if data block {pos} {{Conduits:["enderio:{kind}"]}} '
                              f'if data block {pos} Connections[0][{{Side:"{inside}",Status:"connected_conduit"}}] '
                              f'if data block {pos} Connections[0][{{Side:"{outside}",Status:"connected_block"}}]')
            server.assert_state(f"mekanism_bundle_{x}_{z}_{stage}", conditions, results)
    if legacy:
        server.assert_state("mekanism_legacy_lock",
                            'if data block 1151 80 15 ConduitExtraData[0].LockedFluid{gasName:"mekanism:hydrogen"}', results)
    else:
        server.assert_state(f"mekanism_heat_settings_{stage}",
                            'if data block 1151 80 12 Connections[0][{Side:"west",Config:{is_insert:0b,is_extract:1b,'
                            'extract_redstone_control:"always_active",extract_redstone_channel:"orange"}}] '
                            'if data block 1152 80 12 Connections[0][{Side:"east",Config:{is_insert:1b,is_extract:0b,'
                            'extract_redstone_control:"never_active",extract_redstone_channel:"light_blue"}}] '
                            'if data block 1151 80 12 Connections[0][{Side:"north",Status:"disabled"}]', results)
    output = server.command("port_mekanism_native batch")
    if "Mekanism batch and heat pulse seeded" not in output:
        raise AssertionError(output)
    total = {"legacy": 1450, "converted": 2900, "chunk_reload": 4350, "restart": 5800}[stage]
    assert_probe(server, f"native_mekanism_delivery_{stage}", f"port_mekanism_native check {total}",
                 "Native Mekanism chemical totals, lock and heat transfer verified", results)


def upgrade_stack(tier, marker, count, byte_count):
    return (f'{{id:"enderio:extraction_speed_upgrade_{tier}",Count:{count}{"b" if byte_count else ""},'
            f'tag:{{{marker}:{tier}}}}}')


def exercise_extraction_upgrades(server, stage, results):
    chunks = "1200 0 1231 15"
    left, right = "1215 80 1", "1216 80 1"
    loose_barrel = "12 80 4"
    server.command(f"forceload add {chunks}")
    server.assert_state(f"upgrade_chunks_loaded_{stage}", f"if loaded {left} if loaded {right}", results)
    if stage == "legacy":
        server.command("fill 1214 79 0 1217 79 11 minecraft:stone")
        for tier in range(1, 5):
            for x in (1214, 1217):
                server.command(f"setblock {x} 80 {tier * 3 - 2} minecraft:barrel")
        stacks = [f'{{Slot:{tier - 1}b,' + upgrade_stack(tier, "PortLooseTier", tier + 1, True)[1:]
                  for tier in range(1, 5)]
        server.command(f'setblock {loose_barrel} minecraft:barrel{{Items:[{",".join(stacks)}]}}')
        output = server.command("port_legacy_extraction_upgrades")
        if "Legacy extraction upgrade routes created" not in output:
            raise AssertionError(output)
    assert_extraction_upgrades(server, stage, results, loose_barrel)
    if stage == "converted":
        server.command(f"forceload remove {chunks}")
        server.assert_state("upgrade_chunks_unloaded", f"unless loaded {left} unless loaded {right}", results, timeout=40)
        server.command(f"forceload add {chunks}")
        server.assert_state("upgrade_chunks_reloaded", f"if loaded {left} if loaded {right}", results)
        assert_extraction_upgrades(server, "chunk_reload", results, loose_barrel)
    if stage == "restart":
        destroyed = "1215 80 10"
        server.command("scoreboard objectives add port_upgrade dummy")
        server.command(f"setblock {destroyed} minecraft:air destroy")
        stack = upgrade_stack(4, "PortInstalledTier", 1, True)
        server.command(f"execute positioned {destroyed} store result score #upgrade port_upgrade "
                       f"if entity @e[type=minecraft:item,distance=..4,nbt={{Item:{stack}}}]")
        server.assert_state("installed_upgrade_drops_once",
                            "if score #upgrade port_upgrade matches 1", results)


def assert_extraction_upgrades(server, stage, results, loose_barrel):
    legacy = stage == "legacy"
    for tier in range(1, 5):
        pos = f"1215 80 {tier * 3 - 2}"
        server.command(f"data get block {pos}")
        installed = upgrade_stack(tier, "PortInstalledTier", 1, legacy)
        if legacy:
            conditions = f'if data block {pos} ConduitBundle.Connections.west."0".ExtractUpgrade{installed}'
        else:
            face = 'Connections[0][{Side:"west",Status:"connected_block"}].Inventory'
            conditions = (f'if data block {pos} {face}[{installed}] '
                          f'if data block {pos} {face}[2].tag{{PortInstalledTier:{tier}}}')
        server.assert_state(f"installed_upgrade_{tier}_{stage}", conditions, results)
        loose = upgrade_stack(tier, "PortLooseTier", tier + 1, True)
        server.assert_state(f"loose_upgrade_{tier}_{stage}",
                            f"if data block {loose_barrel} Items[{loose}]", results)
    batches = {"legacy": 0, "converted": 1, "chunk_reload": 2, "restart": 3}[stage]
    output = server.command(f"port_extract_upgrades seed {batches}")
    if "Extraction upgrade batches seeded" not in output:
        raise AssertionError(output)
    assert_probe(server, f"first_extraction_upgrade_transfer_{stage}", "port_extract_upgrades check",
                 "Native extraction upgrade rates verified: [8, 16, 32, 64]", results)


def exercise_lights(server, stage, results):
    chunks = "1024 1024 1055 1055"
    server.command(f"forceload add {chunks}")
    legacy = stage == "legacy"
    barrel = "1035 95 1047"
    stacks = [f'{{Slot:{i}b,id:"enderio:{name}",Count:{i + 2}b,tag:{{PortLegacyLight:{i}}}}}'
              for i, name in enumerate(LIGHTS)]
    if legacy:
        for support in ("1028 94 1029", "1034 96 1029", "1027 95 1040", "1034 94 1040",
                        "1041 94 1030", "1048 95 1031"):
            server.command(f"setblock {support} minecraft:stone")
        for name, (x, y, z, face, facing) in LIGHTS.items():
            server.command(f"setblock {x} {y} {z} enderio:{name}[face={face},facing={facing}]")
        server.command("setblock 1049 95 1030 minecraft:redstone_block")
        server.command(f'setblock {barrel} minecraft:barrel{{Items:[{",".join(stacks)}]}}')

    stages = [stage]
    if stage == "converted":
        stages.append("chunk_reload")
    for observation in stages:
        if observation == "chunk_reload":
            server.command(f"forceload remove {chunks}")
            server.assert_state("light_chunks_unloaded", "unless loaded 1028 95 1029 unless loaded 1041 95 1030",
                                results, timeout=40)
            server.command(f"forceload add {chunks}")
        for name, (x, y, z, face, facing) in LIGHTS.items():
            expected = f"face={face},facing={facing}"
            if not legacy or not name.startswith("powered_"):
                enabled = name in ("light", "powered_light_wireless", "powered_light_inverted_wireless")
                expected += f",enabled={str(enabled).lower()}"
            server.assert_state(f"{name}_{observation}", f"if block {x} {y} {z} enderio:{name}[{expected}]", results)
        for i, stack in enumerate(stacks):
            server.assert_state(f"light_item_{i}_{observation}", f"if data block {barrel} Items[{stack}]", results)
        for name, node, owner_x in (("standard", "1042 95 1030", 1041), ("cross_chunk", "1039 95 1030", 1041),
                                    ("inverted", "1047 95 1030", 1048)):
            owner = f"{{BlockPos:{{X:{owner_x},Y:95,Z:1030}}}}"
            server.assert_state(f"{name}_node_owner_{observation}", f"if block {node} enderio:light_node "
                                f"if data block {node} {owner} if data block {owner_x} 95 1030 {{Active:1b}}", results)

    if stage == "converted":
        server.command("setblock 1028 95 1030 minecraft:redstone_block")
        server.command("setblock 1035 95 1029 minecraft:redstone_block")
        server.command("setblock 1041 95 1031 minecraft:redstone_block")
        server.command("setblock 1049 95 1030 minecraft:air")
        for name, (x, y, z, _, _) in LIGHTS.items():
            if name in ("powered_light", "powered_light_inverted"):
                continue
            enabled = str(name == "light_inverted").lower()
            server.assert_state(f"{name}_redstone", f"if block {x} {y} {z} enderio:{name}[enabled={enabled}]", results)
        server.assert_state("disabled_nodes_removed", "if block 1042 95 1030 minecraft:air "
                            "if block 1047 95 1030 minecraft:air", results)
        server.command("setblock 1028 95 1030 minecraft:air")
        server.command("setblock 1035 95 1029 minecraft:air")
        server.command("setblock 1041 95 1031 minecraft:air")
        server.command("setblock 1049 95 1030 minecraft:redstone_block")
        server.command("setblock 1042 95 1030 minecraft:stone")
        server.assert_state("node_cannot_replace_block", "if block 1042 95 1030 minecraft:stone "
                            "if block 1043 95 1030 minecraft:air", results)
        server.command("setblock 1042 95 1030 minecraft:air")
        server.assert_state("unblocked_light_returns", "if block 1042 95 1030 enderio:light_node "
                            "if block 1043 95 1030 enderio:light_node", results)
    if stage == "restart":
        server.command("setblock 1048 95 1030 minecraft:air destroy")
        server.assert_state("broken_light_clears_owned_nodes_and_drops_item", "if block 1047 95 1030 minecraft:air "
                            'if entity @e[type=item,x=1048,y=95,z=1030,distance=..2,'
                            'nbt={Item:{id:"enderio:powered_light_inverted_wireless",Count:1b}}]', results)


def run_server(directory, java, stage, report, conduit_fixture, loose_conduits, placed_storage_conduits,
               placed_mekanism_conduits, extraction_upgrades, legacy_lights):
    server = Server(directory, java, stage)
    try:
        exercise(server, stage, report["assertions"], conduit_fixture, loose_conduits,
                 placed_storage_conduits, placed_mekanism_conduits, extraction_upgrades, legacy_lights)
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
    parser.add_argument("--placed-storage-conduits", action="store_true",
                        help="Migrate placed AE2 and RS routes with the legacy fixture and storage probe")
    parser.add_argument("--placed-mekanism-conduits", action="store_true",
                        help="Migrate placed heat and chemical routes with the legacy fixture and Mekanism probe")
    parser.add_argument("--extraction-upgrades", action="store_true",
                        help="Migrate loose and installed extraction upgrades with the legacy fixture and upgrade probe")
    parser.add_argument("--legacy-lights", action="store_true", help="Migrate placed lights, nodes and tagged light stacks")
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
        fixtures = (args.conduit_fixture, loose_conduits, args.placed_storage_conduits,
                    args.placed_mekanism_conduits, args.extraction_upgrades, args.legacy_lights)
        run_server(legacy, args.java, "legacy", report, *fixtures)
        shutil.copytree(legacy / WORLD, converted / WORLD)
        run_server(converted, args.java, "converted", report, *fixtures)
        run_server(converted, args.java, "restart", report, *fixtures)
        report["passed"] = True
    finally:
        output = directory / "legacy-migration-results.json"
        output.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Legacy migration passed={report['passed']}, {len(report['assertions'])} assertions, {output}", flush=True)


if __name__ == "__main__":
    main()
