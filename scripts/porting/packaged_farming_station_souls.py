#!/usr/bin/env python3
"""Run the Farming Station binding and reload paths with a packaged Forge jar."""

import argparse
import json
from pathlib import Path
import sys

sys.dont_write_bytecode = True
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage


STATION = "1640 80 8"
PLOT = "1635 80 3"
SOIL = "1635 79 3"
BINDER = "1642 80 8"
BARREL = "1644 80 8"
BEE = '{entity_type:"minecraft:bee"}'
BOUND = f'tag:{{"enderio:data":{{"enderio:soul":{BEE}}}}}'
CAPACITOR = '{Slot:0,id:"enderio:basic_capacitor",Count:1}'
RESERVE = '{Slot:7,id:"minecraft:stick",Count:1}'
AXE_TAG = 'Enchantments:[{id:"minecraft:fortune",lvl:1s}],PortFixture:"axe"'
AXE = f'{{Slot:1,id:"minecraft:diamond_axe",Count:1,tag:{{{AXE_TAG}}}}}'


def write_rule(directory, entity, fertilizer, fortune):
    path = directory / WORLD / "datapacks/farm-rules/data/enderio/eio_soul/farm/minecraft_bee.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps({"entity": entity, "bonemeal": fertilizer, "seeds": fortune}) + "\n")


def loot_pool(item, count, fortune=None, tool=None):
    entry = {"type": "minecraft:item", "name": item}
    if count != 1:
        entry["functions"] = [{"function": "minecraft:set_count", "count": count}]
    pool = {"rolls": 1, "entries": [entry]}
    if fortune is not None:
        predicate = {"enchantments": [{"enchantment": "minecraft:fortune", "levels": fortune}]}
        if tool is not None:
            predicate["items"] = [tool]
        pool["conditions"] = [{"condition": "minecraft:match_tool", "predicate": predicate}]
    return pool


def prepare_rules(directory):
    pack = directory / WORLD / "datapacks/farm-rules"
    write_rule(directory, "minecraft:bee", 0, 2)
    (pack / "pack.mcmeta").write_text('{"pack":{"pack_format":15,"description":"Farming Station rule fixture"}}\n')
    loot = pack / "data/minecraft/loot_tables/blocks"
    loot.mkdir(parents=True)
    tables = {
        "wheat": [loot_pool("minecraft:wheat", 1), loot_pool("minecraft:wheat_seeds", 3, 2)],
        "oak_log": [loot_pool("minecraft:oak_log", 2, 3, "minecraft:diamond_axe")],
    }
    for name, pools in tables.items():
        (loot / f"{name}.json").write_text(json.dumps({"type": "minecraft:block", "pools": pools}) + "\n")


def bind_station(server, results):
    server.command(f"setblock {BINDER} enderio:soul_binder")
    vial = '{Slot:0,id:"enderio:soul_vial",Count:1,tag:{"enderio:data":{"enderio:soul":' \
           '{entity_type:"minecraft:bee",entity_tag:{}}}}}'
    xp = 'Fluids:{Tanks:[{Index:0,Contents:{FluidName:"enderio:fluid_xp_juice_still",Amount:1127}}]}'
    server.command(f'data merge block {BINDER} {{EnergyStored:188000,{xp},Items:{{Items:[{vial},'
                   '{Slot:1,id:"enderio:farming_station",Count:1},{Slot:4,id:"enderio:octadic_capacitor",Count:1}]}}')
    server.assert_state("binder_spends_the_exact_inputs_and_returns_an_empty_vial",
                        f'if data block {BINDER} {{EnergyStored:0,Fluids:{{Tanks:[{{Index:0,Contents:{{Amount:27}}}}]}},'
                        'Items:{Items:[{Slot:2,id:"enderio:farming_station",Count:1},'
                        '{Slot:3,id:"enderio:soul_vial",Count:1}]}} '
                        f'unless data block {BINDER} Items.Items[{{Slot:0}}] unless data block {BINDER} Items.Items[{{Slot:1}}] '
                        f'unless data block {BINDER} Items.Items[{{Slot:3}}].tag."enderio:data"."enderio:soul"', results, timeout=60)
    server.command(f"data get block {BINDER} Items")
    server.assert_state("the_produced_station_item_contains_the_bee",
                        f'if data block {BINDER} Items.Items[{{Slot:2,id:"enderio:farming_station",{BOUND}}}]', results)
    server.command(f"setblock {STATION} enderio:farming_station")
    server.command(f'data modify block {STATION} EntityStorage set from block {BINDER} '
                   'Items.Items[{Slot:2}].tag."enderio:data"."enderio:soul"')


def stock_station(server, block, extra_items=()):
    items = ",".join((CAPACITOR, RESERVE, *extra_items))
    server.command(f'data merge block {STATION} {{RedstoneControl:"never_active",Items:{{Items:[{items}]}}}}')
    server.command(f"setblock {PLOT} minecraft:air")
    if block.startswith("minecraft:wheat"):
        server.command(f"setblock {SOIL} minecraft:farmland[moisture=7]")
    server.command(f"setblock {PLOT} {block}")
    server.command(f'data merge block {STATION} {{EnergyStored:4000,RedstoneControl:"always_active"}}')


def wheat_harvest(server, results, name, fertilizer, seeds):
    stock_station(server, "minecraft:wheat[age=6]", ('{Slot:8,id:"minecraft:bone_meal",Count:4}',))
    wheat = '{Slot:10,id:"minecraft:wheat",Count:1}'
    meal = f'{{Slot:8,id:"minecraft:bone_meal",Count:{fertilizer}}}'
    if seeds:
        seed_state = f'if data block {STATION} Items.Items[{{Slot:11,id:"minecraft:wheat_seeds",Count:{seeds}}}]'
    else:
        seed_state = f'unless data block {STATION} Items.Items[{{id:"minecraft:wheat_seeds"}}]'
    server.assert_state(name, f'if block {PLOT} minecraft:air if data block {STATION} '
                        f'{{EntityStorage:{BEE},Items:{{Items:[{meal},{wheat}]}}}} {seed_state}', results, timeout=35)


def reload_rule(server, entity, fertilizer, fortune):
    write_rule(server.directory, entity, fertilizer, fortune)
    start = len(server.lines)
    server.command("reload")
    server.wait("Data loader for eio_soul/farm loaded", start=start, timeout=35)


def fresh(server, results):
    server.command("forceload add 1632 0 1647 15")
    server.assert_state("farm_fixture_chunk_is_loaded", f"if loaded {STATION} if loaded {PLOT}", results)
    server.command("gamerule randomTickSpeed 0")
    server.command("gamerule doMobSpawning false")
    server.command("fill 1633 79 2 1645 79 10 minecraft:stone")
    server.command("setblock 1636 79 3 minecraft:water")
    bind_station(server, results)
    wheat_harvest(server, results, "bound_bee_grows_wheat_for_free_and_adds_three_seeds", 4, 3)

    stock_station(server, "minecraft:oak_log", (AXE,))
    server.assert_state("fortune_augments_the_real_axe_and_preserves_its_enchantment",
                        f'if block {PLOT} minecraft:air if data block {STATION} '
                        f'{{Items:{{Items:[{{Slot:10,id:"minecraft:oak_log",Count:2}},'
                        f'{{Slot:1,id:"minecraft:diamond_axe",Count:1,tag:{{{AXE_TAG},Damage:1}}}}]}}}} '
                        f'unless data block {STATION} Items.Items[{{Slot:1}}].tag.Enchantments[{{id:"minecraft:fortune",lvl:3s}}]',
                        results, timeout=25)

    reload_rule(server, "minecraft:bee", 1, 0)
    wheat_harvest(server, results, "live_reloaded_rule_consumes_fertilizer_and_stops_bonus_seeds", 3, 0)
    reload_rule(server, "minecraft:bee", 0, 2)
    wheat_harvest(server, results, "reinstated_bonus_takes_effect_without_replacing_the_station", 4, 3)

    server.command(f"setblock {BARREL} minecraft:barrel")
    server.command(f"loot replace block {BARREL} container.0 mine {STATION} minecraft:diamond_pickaxe")
    server.assert_state("native_station_loot_carries_the_same_soul",
                        f'if data block {BARREL} Items[{{Slot:0b,id:"enderio:farming_station",{BOUND}}}]', results)


def reopened(server, results):
    server.assert_state("placed_and_carried_souls_survive_restart",
                        f'if data block {STATION} {{EntityStorage:{BEE}}} '
                        f'if data block {BARREL} Items[{{Slot:0b,id:"enderio:farming_station",{BOUND}}}]', results)
    wheat_harvest(server, results, "restored_station_uses_both_bonuses", 4, 3)
    reload_rule(server, "minecraft:allay", 0, 2)
    wheat_harvest(server, results, "removing_the_bee_rule_preserves_the_binding_and_restores_defaults", 3, 0)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "forge": FORGE, "jarSha256": digest(directory / "mods/enderio.jar"),
              "assertions": [], "servers": []}
    try:
        prepare_rules(directory)
        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", reopened)
        report["passed"] = True
    finally:
        path = directory / "farming-station-souls-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Farming Station souls passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
