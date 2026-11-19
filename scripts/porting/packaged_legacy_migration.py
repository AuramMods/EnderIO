#!/usr/bin/env python3
"""Copy and migrate an actual Ender IO 6.2.18-beta Forge 1.20.1 fixture."""

import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time

sys.dont_write_bytecode = True
from packaged_capacitor_banks import read_saved_networks
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
SOLAR_PANELS = ((1183, "energetic", 3000), (1184, "energetic", 3500),
                (1185, "pulsating", 12000), (1188, "vibrant", 50000))
WIRED_BLOCKED = "1190 80 8"
WIRED_COMPLETION = "1194 80 8"
SOLAR_CHUNKS = "1168 0 1199 15"
BANK_RETRY_X = 1543
BANKS = {1230: ("basic", 500000, 123456, 1), 1231: ("basic", 500000, 123456, 1),
         1232: ("advanced", 2000000, 456789, 1), BANK_RETRY_X: ("vibrant", 4000000, 2345678, 2)}
BANK_BARREL = "1227 80 6"
BANK_DROP_ORIGIN = "1227 80 8"
BANK_CHARGER = "1228 80 6"
BANK_ITEM_ENERGY = 78912
BANK_CHARGE_INPUT = 10001


def tank_state(x, legacy):
    _, capacity, old_fluid, new_fluid, amount = TANKS[x]
    fluid_id = old_fluid if legacy else new_fluid
    stack = f'FluidName:"{fluid_id}",Amount:{amount},Tag:{{PortLegacy:42}}'
    entry = f"{stack},Capacity:{capacity},Index:0" if legacy else f"Index:0,Contents:{{{stack}}}"
    key = "Fluid" if legacy else "Fluids"
    count = "7b" if legacy else "7"
    return (f'{{{key}:{{Tanks:[{{{entry}}}]}},Items:{{Items:[{{Slot:1,id:"minecraft:diamond",Count:{count}}}]}}}}')


def exercise(server, stage, results, conduit_fixture, loose_conduits, placed_storage_conduits,
             placed_mekanism_conduits, extraction_upgrades, legacy_lights, primitive_silicon, stirling_generator,
             solar_charging, capacitor_banks):
    server.wait('For help, type "help"', timeout=150)
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
    if primitive_silicon:
        exercise_primitive_smelter(server, stage, results, primitive_silicon)
    if stirling_generator:
        exercise_stirling_generator(server, stage, results)
    if solar_charging:
        exercise_solar_charging(server, stage, results)
    if capacitor_banks:
        exercise_capacitor_banks(server, stage, results)
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
        assert_storage_items(server, stage, 41, results)
        server.command(f"forceload remove {chunks}")
        server.assert_state("storage_chunks_unloaded", "unless loaded 1087 80 4 unless loaded 1088 80 4", results, timeout=40)
        server.command(f"forceload add {chunks}")
        server.assert_state("storage_chunks_reloaded", "if loaded 1087 80 4 if loaded 1088 80 4", results)
        stage = "chunk_reload"
    assert_storage_routes(server, stage, results)
    if legacy:
        storage_operation(server, "mount_native_storage_in_legacy_world", "mount_items", "Native item storage mounted", results)
        assert_storage_items(server, "legacy_empty", 0, results)
        storage_operation(server, "write_native_items_in_legacy_world", "write_items 41",
                          "Native item storage accepted the write", results)
        assert_storage_items(server, stage, 41, results)
    elif stage == "chunk_reload":
        assert_storage_items(server, stage, 41, results)
        storage_operation(server, "write_native_items_after_conversion", "write_items 7",
                          "Native item storage accepted the write", results)
        assert_storage_items(server, "after_write", 48, results)
    else:
        assert_storage_items(server, stage, 48, results)
        storage_operation(server, "take_native_items_after_restart", "take_items 11",
                          "Native item storage delivered the requested items", results)
        assert_storage_items(server, "after_extraction", 37, results)


def assert_storage_items(server, stage, count, results):
    assert_probe(server, f"native_storage_item_counts_and_tags_{stage}", f"port_storage_native check_items {count}",
                 "Native item storage counts verified", results)


def storage_operation(server, assertion, operation, message, results):
    command = f"port_storage_native {operation}"
    output = server.command(command)
    passed = message in output
    results.append({"assertion": assertion, "passed": passed, "command": command})
    if not passed:
        raise AssertionError(f"{assertion} failed: {output}")


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


def observe_int(server, pos, path):
    output = server.command(f"data get block {pos} {path}")
    match = re.search(r"has the following block data: (-?\d+)\s*$", output, re.MULTILINE)
    if not match:
        raise AssertionError(output)
    return int(match[1])


def exercise_primitive_smelter(server, stage, results, silicon):
    machine = "1127 85 5"
    barrel = "1127 85 9"
    chunks = "1120 0 1135 15"
    legacy = stage == "legacy"
    server.command(f"forceload add {chunks}")
    if legacy:
        server.command(f"setblock {machine} enderio:primitive_alloy_smelter[facing=east]")
        server.command(f'data merge block {machine} {{RedstoneControl:3,Items:{{Size:5,Items:['
                       '{Slot:0,id:"minecraft:coal",Count:2b,tag:{PortLegacyFuel:57}},'
                       f'{{Slot:1,id:"minecraft:copper_ingot",Count:1b}},{{Slot:3,id:"{silicon}",Count:1b}}]}}}}')
        server.command(f'setblock {barrel} minecraft:barrel{{Items:[{{Slot:0b,id:"enderio:primitive_alloy_smelter",'
                       'Count:2b,tag:{PortLegacyPrimitive:57}}]}')
        server.command(f"data merge block {machine} {{RedstoneControl:0}}")
        server.assert_state("native_primitive_job_started", f"if block {machine} enderio:primitive_alloy_smelter[powered=true] "
                            f'if data block {machine} {{BurnDuration:1600,Task:{{RecipeId:"enderio:alloy_smelting/copper_alloy_ingot",'
                            'HasDeterminedOutputs:1b,ProgressRequired:3200}}', results)
        time.sleep(0.4)
        server.command(f"data merge block {machine} {{RedstoneControl:3}}")
        server.command("setblock 1128 85 5 minecraft:stone")
        server.assert_state("legacy_primitive_job_paused", f"if block {machine} enderio:primitive_alloy_smelter[powered=false]", results)
        progress = observe_int(server, machine, "Task.ProgressMade")
        if not 0 < progress < 3200:
            raise AssertionError(f"Expected a partially completed legacy copper alloy job, got {progress}")
        results.append({"assertion": "native_primitive_job_mid_craft", "passed": True, "progress": progress})
        server.command(f"data get block {machine}")

    server.assert_state(f"placed_primitive_smelter_{stage}",
                        f"if block {machine} enderio:primitive_alloy_smelter[facing=east,powered=false]", results)
    server.assert_state(f"tagged_primitive_stack_{stage}", f'if data block {barrel} Items[{{Slot:0b,'
                        'id:"enderio:primitive_alloy_smelter",Count:2b,tag:{PortLegacyPrimitive:57}}]', results)
    expected = next(entry["progress"] for entry in results if entry["assertion"] == "native_primitive_job_mid_craft")
    if stage == "converted":
        assert_primitive_work(server, machine, silicon, expected, "converted", results)
        server.command(f"forceload remove {chunks}")
        server.assert_state("primitive_chunk_unloaded", f"unless loaded {machine}", results, timeout=40)
        server.command(f"forceload add {chunks}")
        server.assert_state("primitive_chunk_loaded", f"if loaded {machine}", results)
        assert_primitive_work(server, machine, silicon, expected, "chunk_reload", results)
    if stage == "restart":
        assert_primitive_work(server, machine, silicon, expected, "restart", results)
        server.command(f'data merge block {machine} {{RedstoneControl:"always_active"}}')
        server.assert_state("primitive_saved_job_completes_once", f'if data block {machine} Items.Items'
                            '[{Slot:4,id:"enderio:conductive_alloy_ingot",Count:1}] '
                            f'unless data block {machine} Items.Items[{{Slot:1}}] '
                            f'unless data block {machine} Items.Items[{{Slot:3}}] '
                            f'if data block {machine} Items.Items[{{Slot:0,id:"minecraft:coal",Count:1,tag:{{PortLegacyFuel:57}}}}]',
                            results, timeout=18)
        server.command(f"data get block {machine}")


def assert_primitive_work(server, machine, silicon, progress, stage, results):
    server.assert_state(f"primitive_paused_job_{stage}",
                        f'if data block {machine} {{RedstoneControl:"never_active",BurnDuration:1600,'
                        f'Task:{{RecipeId:"enderio:primitive_alloy_smelting/copper_alloy_ingot",ProgressMade:{progress},'
                        'ProgressRequired:3200,Outputs:[{Item:{id:"enderio:conductive_alloy_ingot",Count:1}}]}} '
                        f'if data block {machine} Items.Items[{{Slot:0,id:"minecraft:coal",Count:1,tag:{{PortLegacyFuel:57}}}}] '
                        f'if data block {machine} Items.Items[{{Slot:1,id:"minecraft:copper_ingot",Count:1}}] '
                        f'if data block {machine} Items.Items[{{Slot:3,id:"{silicon}",Count:1}}] '
                        f'unless data block {machine} Items.Items[{{Slot:4}}]', results)
    burn_time = observe_int(server, machine, "BurnTime")
    if not 0 < burn_time < 1600:
        raise AssertionError(f"Expected original coal to be burning after {stage}, got {burn_time} ticks")
    results.append({"assertion": f"primitive_retains_burn_{stage}", "passed": True, "burnTime": burn_time})


def exercise_stirling_generator(server, stage, results):
    machine = "1160 80 8"
    server.command("forceload add 1160 8")
    legacy = stage == "legacy"
    if legacy:
        server.command(f"setblock {machine} enderio:stirling_generator[facing=east]")
        server.command(f'data merge block {machine} {{Energy:{{EnergyStored:63999}},IoConfig:{{"5":1b}},'
                       'Items:{Size:2,Items:[{Slot:0,id:"minecraft:coal",Count:2b,tag:{PortStirling:58}},'
                       '{Slot:1,id:"enderio:basic_capacitor",Count:1b}]}}')
    energy_config = ('Energy:{EnergyStored:64000},IoConfig:{"5":1b},RedstoneControl:0' if legacy else
                     'EnergyStored:64000,IOConfig:{Modes:{east:"push"}},RedstoneControl:"always_active"')
    count = "1b" if legacy else "1"
    server.assert_state(f"stirling_burning_and_configured_{stage}",
                        f'if block {machine} enderio:stirling_generator[facing=east,powered=true] '
                        f'if data block {machine} {{{energy_config},BurnDuration:600,Items:{{Size:2,Items:'
                        f'[{{Slot:0,id:"minecraft:coal",Count:{count},tag:{{PortStirling:58}}}},'
                        f'{{Slot:1,id:"enderio:basic_capacitor",Count:{count}}}]}}}}', results)
    remaining = observe_int(server, machine, "BurnTime")
    prior = [result["burnTime"] for result in results if result["assertion"].startswith("stirling_keeps_running_burn_")]
    maximum = prior[-1] if prior else 600
    passed = 0 < remaining <= maximum
    results.append({"assertion": f"stirling_keeps_running_burn_{stage}", "passed": passed, "burnTime": remaining, "maximum": maximum})
    if not passed:
        raise AssertionError(f"Expected ongoing burn with at most {maximum} ticks left, got {remaining}")
    server.command(f"data get block {machine}")


def assert_panels(server, stage, results):
    energy_path = "Energy" if stage == "legacy" else "EnergyStored"
    for x, tier, energy in SOLAR_PANELS:
        tag = f"{{Energy:{{EnergyStored:{energy}}}}}" if stage == "legacy" else f"{{EnergyStored:{energy}}}"
        pos = f"{x} 80 8"
        server.command(f"data get block {pos} {energy_path}")
        server.assert_state(f"solar_{x}_{stage}",
                            f'if block {pos} enderio:{tier}_photovoltaic_module '
                            f'if data block {pos} {{id:"enderio:{tier}_photovoltaic_cell"}} if data block {pos} {tag}', results)


def saved_staff(slot, charge, label, legacy_stack, legacy_energy=True):
    count = "1b" if legacy_stack else "1"
    energy = (f"Energy:{{EnergyStored:{charge}}}" if legacy_energy
              else f'"enderio:data":{{"enderio:energy":{charge}}}')
    return f'{{Slot:{slot},id:"enderio:staff_of_travelling",Count:{count},tag:{{PortWired:{label},{energy}}}}}'


def assert_wired_completion(server, stage, results):
    charged = saved_staff(2, 100000, 2, False, legacy_energy=False)
    server.assert_state(f"wired_finishes_from_saved_balance_{stage}",
                        f'if data block {WIRED_COMPLETION} {{EnergyStored:0}} '
                        f'if data block {WIRED_COMPLETION} Items.Items[{charged}] '
                        f'unless data block {WIRED_COMPLETION} Items.Items[{{Slot:1}}]', results)


def exercise_solar_charging(server, stage, results):
    legacy = stage == "legacy"
    server.command(f"forceload add {SOLAR_CHUNKS}")
    server.assert_state(f"power_fixture_loaded_{stage}", "if loaded 1183 80 8 if loaded 1194 80 8", results)
    server.command("gamerule doDaylightCycle false")
    server.command("time set midnight")
    legacy_capacitor = '{Slot:0,id:"enderio:basic_capacitor",Count:1b,tag:{PortCapacitor:61}}'
    if legacy:
        for x, tier, energy in SOLAR_PANELS:
            server.command(f"setblock {x} 80 8 enderio:{tier}_photovoltaic_module")
            server.command(f"data merge block {x} 80 8 {{Energy:{{EnergyStored:{energy}}}}}")
        for pos, energy, items in (
            (WIRED_BLOCKED, 12345, [saved_staff(1, 40000, 1, True), saved_staff(2, 100000, 3, True)]),
            (WIRED_COMPLETION, 180, [saved_staff(1, 99820, 2, True)]),
        ):
            server.command(f"setblock {pos} enderio:wired_charger[facing=south]")
            server.command(f'data merge block {pos} {{Energy:{{EnergyStored:{energy}}},RedstoneControl:1,'
                           f'IoConfig:{{"1":2b,"2":1b,"5":4b}},Items:{{Size:3,Items:[{legacy_capacitor},{",".join(items)}]}}}}')

    assert_panels(server, stage, results)
    cap = legacy_capacitor if legacy else legacy_capacitor.replace("1b", "1")
    # For a south-facing machine the persisted east entry controls the world-west face.
    config = ('RedstoneControl:1,IoConfig:{"1":2b,"2":1b,"5":4b}' if legacy
              else 'RedstoneControl:"active_with_signal",IOConfig:{Modes:{up:"pull",north:"push",east:"disable"}}')
    for pos, energy, tag, stacks in (
        (WIRED_BLOCKED, 12345, "blocked", [saved_staff(1, 40000, 1, legacy), saved_staff(2, 100000, 3, legacy)]),
        (WIRED_COMPLETION, 180, "pending", [saved_staff(1, 99820, 2, legacy)]),
    ):
        if stage == "restart" and tag == "pending":
            assert_wired_completion(server, stage, results)
            continue
        energy_tag = f"Energy:{{EnergyStored:{energy}}}" if legacy else f"EnergyStored:{energy}"
        server.command(f"data get block {pos}")
        server.assert_state(f"wired_{tag}_{stage}", f'if block {pos} enderio:wired_charger[facing=south] '
                            f'if data block {pos} {{{config},{energy_tag},'
                            f'Items:{{Size:3,Items:[{cap},{",".join(stacks)}]}}}}', results)

    if stage == "converted":
        server.command("setblock 1195 80 8 minecraft:redstone_block")
        assert_wired_completion(server, stage, results)
        server.command(f"forceload remove {SOLAR_CHUNKS}")
        server.assert_state("power_fixture_evicted", "unless loaded 1183 80 8 unless loaded 1194 80 8", results, timeout=40)
        server.command(f"forceload add {SOLAR_CHUNKS}")
        server.assert_state("power_fixture_reloaded", "if loaded 1183 80 8 if loaded 1194 80 8", results)
        assert_panels(server, "reloaded", results)
        assert_wired_completion(server, "reloaded", results)
    elif stage == "restart":
        sink = "1182 80 8"
        server.command(f"setblock {sink} enderio:wired_charger")
        server.command(f'data merge block {sink} {{IOConfig:{{Modes:{{east:"pull"}}}},'
                       'Items:{Size:3,Items:[{Slot:0,id:"enderio:basic_capacitor",Count:1}]}}')
        drained = " ".join(f"if data block {x} 80 8 {{EnergyStored:0}}" for x, _, _ in SOLAR_PANELS[:3])
        server.assert_state("migrated_array_routes_all_saved_energy_on_restart",
                            f"if data block {sink} {{EnergyStored:18500}} {drained} "
                            "if data block 1188 80 8 {EnergyStored:50000}", results)


def solar_client(args, server, report):
    from client.run_multiplayer import stop_client

    server.command("fill 1187 79 6 1193 79 13 minecraft:stone")
    server.command("setblock 1187 80 11 minecraft:torch")
    server.command("setblock 1193 80 11 minecraft:torch")
    server.command("gamerule doMobSpawning false")
    output = ROOT / "build/port-evidence" / f"{args.name}-tcp"
    output.parent.mkdir(parents=True, exist_ok=True)
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
               "--launch", str(args.solar_client.resolve()), "--scenario", "solar-wired",
               "--server", f"127.0.0.1:{args.port}", "--output", str(output), "--timeout", "195"]
    with (server.directory / "solar-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            server.wait("EnderIOQA joined the game", timeout=150)
            server.command("item replace entity EnderIOQA hotbar.0 with enderio:vibrant_photovoltaic_module")
            server.command("tp EnderIOQA 1190.5 80 11.5 158 25")
            if client.wait(timeout=200) != 0:
                raise AssertionError(f"Solar and wired client failed; see {output}")
            report["client"] = json.loads((output / "summary.json").read_text())
        finally:
            stop_client(client)
    client_jar = report["client"]["client"]["captures"]["wired-migrated"]["loadedMods"]["enderio"]["file"]
    if digest(Path(client_jar)) != report["portSha256"]:
        raise AssertionError("Client and server used different packaged Ender IO jars")
    results = report["assertions"]
    server.assert_state("client_placement_updates_both_solar_states",
                        "if block 1188 80 8 enderio:vibrant_photovoltaic_module[east=false] "
                        "if block 1189 80 8 enderio:vibrant_photovoltaic_module[west=false] "
                        "if data block 1188 80 8 {EnergyStored:50000} "
                        "if data block 1189 80 8 {EnergyStored:0}", results)
    charged = saved_staff(1, 52345, 1, False, legacy_energy=False)
    server.assert_state("client_redstone_change_debits_only_saved_balance",
                        f'if data block {WIRED_BLOCKED} {{EnergyStored:0,RedstoneControl:"always_active"}} '
                        f'if data block {WIRED_BLOCKED} Items.Items[{charged}] '
                        f'unless data block {WIRED_BLOCKED} Items.Items[{{Slot:2}}]', results)
    server.command("save-all flush")


def exercise_capacitor_banks(server, stage, results):
    old_settings = 'IoConfig:{"2":4b,"3":1b,"4":4b,"5":4b},displaymodes:{north:2,south:1,west:0,east:0}'
    new_settings = ('IOConfig:{Modes:{north:"disable",south:"push",west:"disable",east:"disable"}},'
                    'displaymodes:{north:2,south:1,west:0,east:0}')
    redstone_names = {1: "active_with_signal", 2: "active_without_signal"}
    server.command("forceload add 1216 0 1247 15")
    server.command(f"forceload add {BANK_RETRY_X} 8")
    server.assert_state(f"bank_chunks_loaded_{stage}",
                        f"if loaded 1230 80 8 if loaded 1232 80 8 if loaded {BANK_RETRY_X} 80 8", results)
    for x, (tier, _, energy, mode) in BANKS.items():
        pos = f"{x} 80 8"
        block = f"enderio:{tier}_capacitor_bank"
        if stage == "legacy":
            state = f"{{Energy:{{EnergyStored:{energy}}},RedstoneControl:{mode},{old_settings}}}"
            server.command(f"setblock {pos} {block}")
            server.command(f"data merge block {pos} {state}")
        else:
            state = f'{{RedstoneControl:"{redstone_names[mode]}",{new_settings}}}'
        server.command(f"data get block {pos}")
        server.assert_state(f"{tier}_bank_{x}_{stage}", f"if block {pos} {block} if data block {pos} {state}", results)

    if stage == "legacy":
        server.command(f"setblock {BANK_BARREL} minecraft:barrel")
        server.command(f"setblock {BANK_DROP_ORIGIN} enderio:basic_capacitor_bank")
        server.command(f"data merge block {BANK_DROP_ORIGIN} {{Energy:{{EnergyStored:{BANK_ITEM_ENERGY}}},{old_settings}}}")
        server.command(f"loot replace block {BANK_BARREL} container.1 mine {BANK_DROP_ORIGIN} minecraft:diamond_pickaxe")
        server.command(f"setblock {BANK_DROP_ORIGIN} minecraft:air")
    server.assert_state(f"historical_bank_item_{stage}",
                        f'if data block {BANK_BARREL} Items[{{Slot:1b,id:"enderio:basic_capacitor_bank",Count:1b,'
                        f'tag:{{BlockEntityTag:{{Energy:{{EnergyStored:{BANK_ITEM_ENERGY}}},{old_settings}}}}}}}]', results)

    if stage != "legacy":
        if stage == "converted":
            server.command(f"setblock {BANK_CHARGER} enderio:wired_charger")
            server.command(f"data modify block {BANK_CHARGER} Items.Items set from block {BANK_BARREL} Items")
            server.command(f'data modify block {BANK_CHARGER} Items.Items append value '
                           '{Slot:0,id:"enderio:basic_capacitor",Count:1}')
            server.command(f"data merge block {BANK_CHARGER} {{EnergyStored:{BANK_CHARGE_INPUT}}}")
        server.command(f"data get block {BANK_CHARGER}")
        charged = (f'{{Slot:1,id:"enderio:basic_capacitor_bank",Count:1,tag:{{"enderio:data":'
                   f'{{"enderio:energy":{BANK_ITEM_ENERGY + BANK_CHARGE_INPUT}}},BlockEntityTag:{{{old_settings}}}}}}}')
        server.assert_state(f"historical_bank_item_recharge_{stage}",
                            f"if data block {BANK_CHARGER} {{EnergyStored:0}} "
                            f"if data block {BANK_CHARGER} Items.Items[{charged}] "
                            f"unless data block {BANK_CHARGER} Items.Items[{{Slot:1}}].tag.BlockEntityTag.Energy.EnergyStored",
                            results, timeout=25)
        server.command("save-all flush")
        path = server.directory / WORLD / "data/enderio_capacitor_bank_network.dat"
        network_state = []
        for network in read_saved_networks(path):
            nodes = network["nodes"]
            xs = [node["pos"][0] for node in nodes]
            edges = [tuple(sorted((xs[edge["first"]], xs[edge["second"]]))) for edge in network["edges"]]
            network_state.append((sorted((node["pos"][0], node["capacity"]) for node in nodes), sorted(edges),
                                  network["energy"], network["redstone"]))
        mixed = [(x, BANKS[x][1]) for x in (1230, 1231, 1232)]
        expected = [(mixed, [(1230, 1231), (1231, 1232)], 703701, "active_with_signal"),
                    ([(BANK_RETRY_X, BANKS[BANK_RETRY_X][1])], [], 2345678, "active_without_signal")]
        observed = sorted(network_state)
        results.append({"assertion": f"exact_bank_graphs_{stage}", "passed": expected == observed,
                        "expected": expected, "observed": observed})
        if expected != observed:
            raise AssertionError(f"{stage} capacitor-bank graphs differ: {observed}")


def run_server(directory, args, stage, report, *fixtures):
    server = Server(directory, args.java, stage)
    try:
        exercise(server, stage, report["assertions"], *fixtures)
        final_stage = "bank-conversion-retry" if args.capacitor_banks else "restart"
        if stage == final_stage and args.solar_client:
            solar_client(args, server, report)
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
    parser.add_argument("--primitive-smelter", nargs="?", const="enderio:silicon", choices=("enderio:silicon", "ae2:silicon"),
                        help="Migrate a saved primitive alloy smelter job using the selected silicon item")
    parser.add_argument("--stirling-generator", action="store_true", help="Migrate a burning Stirling generator")
    parser.add_argument("--solar-charging", action="store_true", help="Migrate photovoltaic arrays and wired charger state")
    parser.add_argument("--solar-client", type=Path, help="Run the migration and check its solar models and charger GUI over TCP")
    parser.add_argument("--capacitor-banks", action="store_true", help="Migrate placed and carried capacitor banks")
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
            connection = "online-mode=false\nenforce-secure-profile=false\n" if args.solar_client and profile == converted else ""
            (profile / "server.properties").write_text(
                f"server-ip=127.0.0.1\nserver-port={args.port}\nlevel-name={WORLD}\n"
                f"level-type=minecraft:flat\ngenerator-settings={flat}\n"
                "generate-structures=false\nview-distance=3\nsimulation-distance=3\n" + connection)
        if args.conduit_fixture:
            shutil.copyfile(args.conduit_fixture, legacy / "mods" / "port-legacy-fixture.jar")
            report["fixtureSha256"] = digest(args.conduit_fixture)
        fixtures = (args.conduit_fixture, loose_conduits, args.placed_storage_conduits,
                    args.placed_mekanism_conduits, args.extraction_upgrades, args.legacy_lights, args.primitive_smelter,
                    args.stirling_generator, bool(args.solar_charging or args.solar_client), args.capacitor_banks)
        run_server(legacy, args, "legacy", report, *fixtures)
        shutil.copytree(legacy / WORLD, converted / WORLD)
        run_server(converted, args, "converted", report, *fixtures)
        run_server(converted, args, "restart", report, *fixtures)
        if args.capacitor_banks:
            # Keep the committed graph and restore only the isolated bank's pristine legacy region.
            region = Path("region") / f"r.{BANK_RETRY_X // 512}.0.mca"
            shutil.copyfile(legacy / WORLD / region, converted / WORLD / region)
            run_server(converted, args, "bank-conversion-retry", report, *fixtures)
        report["passed"] = True
    finally:
        output = directory / "legacy-migration-results.json"
        output.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Legacy migration passed={report['passed']}, {len(report['assertions'])} assertions, {output}", flush=True)


if __name__ == "__main__":
    main()
