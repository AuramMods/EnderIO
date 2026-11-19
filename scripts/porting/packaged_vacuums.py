#!/usr/bin/env python3
"""Check Vacuum Chest and XP Vacuum conservation and migration on headless Forge."""

import argparse
import json
from pathlib import Path
import shutil
import sys

sys.dont_write_bytecode = True
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage


CHEST = "2740 80 8"
XP = "2750 80 8"
XP_EXPORT = "2759 80 8"
TANK = "2760 80 8"
BARREL = "2740 80 7"
ARCHIVE = "2743 80 13"
SWITCHES = ("2740 80 9", "2750 80 9", "2759 80 9")
CHEST_ID = "enderio:vacuum_chest"
XP_ID = "enderio:xp_vacuum"
JUICE = "enderio:fluid_xp_juice_still"
OLD_JUICE = "enderio:xp_juice"
CAPACITY = 2147483647
LIMIT_DATA = ('{"enderio:data":{"enderio:limited_item_filter":{isNbt:1b,items:[{index:0,'
              'value:{id:"minecraft:iron_ingot",Count:4,tag:{PortFixture:135}}}]}}}')
OLD_FILTER_DATA = '{IsInverted:0b,IsNbt:1b,ItemEntries:[{id:"minecraft:iron_ingot",Count:1b,tag:{PortFixture:135}}]}'


def stack(item, count, *, slot=None, tag=None, byte=False):
    fields = [f'id:"{item}"', f"Count:{count}{'b' if byte else ''}"]
    if slot is not None:
        fields.insert(0, f"Slot:{slot}{'b' if byte and slot == 0 else ''}")
    if tag is not None:
        fields.append(f"tag:{tag}")
    return "{" + ",".join(fields) + "}"


def iron(count, **kwargs):
    return stack("minecraft:iron_ingot", count, tag="{PortFixture:135}", **kwargs)


def fluid(amount, *, tagged=False, legacy=False):
    name = OLD_JUICE if legacy else JUICE
    contents = f'FluidName:"{name}",Amount:{amount}' + (",Tag:{PortFixture:135}" if tagged else "")
    if legacy:
        return f"Fluid:{{Tanks:[{{Index:0,{contents}}}]}}"
    return f"Fluids:{{Tanks:[{{Index:0,Contents:{{{contents}}}}}]}}"


def radius(size):
    return f'ActionRange:{{range:{size},isVisible:1b}},RedstoneControl:"active_with_signal"'


def summon_item(server, position, marker, item):
    server.command(f'summon minecraft:item {position} {{NoGravity:1b,PickupDelay:32767s,Motion:[0.0d,0.0d,0.0d],'
                   f'Tags:["{marker}"],Item:{item}}}')


def summon_orb(server, position, marker, value, count):
    server.command(f'summon minecraft:experience_orb {position} {{NoGravity:1b,Motion:[0.0d,0.0d,0.0d],'
                   f'Tags:["{marker}"],Value:{value}s,Count:{count}}}')


def entity(kind, condition):
    return f"@e[type=minecraft:{kind},{condition},limit=1]"


def item_on_ground(marker, item):
    return entity("item", f"tag={marker},nbt={{Item:{item}}}")


def base(server, results):
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    server.command("forceload add 2736 0 2767 15")
    server.assert_state("vacuum_fixture_chunks_are_loaded", f"if loaded {CHEST} if loaded {XP} if loaded {XP_EXPORT}", results)
    server.command("fill 2737 79 4 2763 79 14 minecraft:stone")
    server.command(f"setblock {CHEST} {CHEST_ID}")
    server.command(f"setblock {XP} {XP_ID}")


def stored_iron(pos, count):
    return f"if data block {pos} Items.Items[{iron(count, slot=0)}]"


def stock_remainder(server, results, assertion):
    server.assert_state(assertion, f"{stored_iron(CHEST, 4)} "
                        f"if entity {item_on_ground('vacuum_stock', iron(7, byte=True))} "
                        f"if entity {item_on_ground('vacuum_mismatch', stack('minecraft:iron_ingot', 2, byte=True))} "
                        f"if entity {item_on_ground('vacuum_outside', iron(3, byte=True))}", results, timeout=25)


def split_orbs(server, results, assertion):
    whole = entity("experience_orb", 'tag=vacuum_merged,nbt={Value:7s,Count:2}')
    partial = entity("experience_orb", 'x=2750.5,y=81.1,z=8.5,distance=..2,nbt={Value:5s,Count:1}')
    server.assert_state(assertion, f"if data block {XP} {{{fluid(CAPACITY - 7, tagged=True)}}} "
                        f"if entity {whole} if entity {partial}", results, timeout=20)


def xp_export(server, results, assertion):
    server.assert_state(assertion, f"if data block {TANK} {{{fluid(420)}}} "
                        f"unless data block {XP_EXPORT} Fluids.Tanks[0].Contents.FluidName "
                        f"unless entity {entity('experience_orb', 'tag=vacuum_export')}", results, timeout=20)


def collect(server, results):
    base(server, results)
    limited = stack("enderio:limited_item_filter", 1, slot=27, tag=LIMIT_DATA)
    server.command(f"data merge block {CHEST} {{{radius(2)},Items:{{Size:28,Items:[{iron(1, slot=0)},{limited}]}}}}")
    server.command(f"data merge block {XP} {{{radius(1)},{fluid(CAPACITY - 47, tagged=True)}}}")
    server.command(f"setblock {XP_EXPORT} {XP_ID}")
    server.command(f"data merge block {XP_EXPORT} {{{radius(1)}}}")
    server.command(f"setblock {TANK} enderio:fluid_tank")
    summon_item(server, "2742.5 80.9 8.5", "vacuum_stock", iron(10, byte=True))
    summon_item(server, "2741.5 80.9 8.5", "vacuum_mismatch", stack("minecraft:iron_ingot", 2, byte=True))
    summon_item(server, "2745.5 80.9 8.5", "vacuum_outside", iron(3, byte=True))
    summon_orb(server, "2750.5 81.1 8.5", "vacuum_merged", 7, 3)
    summon_orb(server, "2759.5 81.1 8.5", "vacuum_export", 7, 3)
    server.assert_state("unpowered_redstone_keeps_the_item_and_orbs_uncollected",
                        f"{stored_iron(CHEST,1)} if entity {item_on_ground('vacuum_stock', iron(10, byte=True))} "
                        f"if data block {XP} {{{fluid(CAPACITY - 47, tagged=True)}}} "
                        f"if entity {entity('experience_orb', 'tag=vacuum_merged,nbt={Value:7s,Count:3}')}", results)
    for pos in SWITCHES:
        server.command(f"setblock {pos} minecraft:redstone_block")
    stock_remainder(server, results, "stock_limit_keeps_the_exact_tagged_remainder_and_rejects_other_entities")
    split_orbs(server, results, "xp_vacuum_deposits_only_two_points_and_splits_the_partial_orb")
    xp_export(server, results, "sided_tank_receives_all_three_merged_orbs_as_420_mb")


def reopen(server, results):
    stock_remainder(server, results, "stock_and_unaccepted_items_survive_a_dedicated_restart")
    split_orbs(server, results, "whole_and_partial_orbs_and_tagged_tank_survive_restart")
    xp_export(server, results, "the_sided_tank_persists_all_exported_xp")
    server.command(f"setblock {BARREL} minecraft:barrel")
    server.assert_state("sided_output_frees_stock_space_and_all_eleven_items_are_delivered",
                        f"if data block {BARREL} Items[{iron(11, slot=0, byte=True)}] "
                        f"unless data block {CHEST} Items.Items[{{Slot:0}}] "
                        f"unless entity {entity('item', 'tag=vacuum_stock')} "
                        f"if entity {entity('item', 'tag=vacuum_mismatch')} if entity {entity('item', 'tag=vacuum_outside')} "
                        f'if data block {CHEST} Items.Items[{{Slot:27,id:"enderio:limited_item_filter"}}]', results, timeout=25)


def old_settings(size):
    return f"Range:{size},RangeVisible:1b,RedstoneControl:1"


def legacy(server, results):
    base(server, results)
    original = '{Slot:27,id:"enderio:basic_item_filter",Count:1b,tag:' + OLD_FILTER_DATA + "}"
    server.command(f"data merge block {CHEST} {{{old_settings(1)},Items:{{Size:28,Items:[{original}]}}}}")
    server.command(f"data merge block {XP} {{{old_settings(2)}}}")
    for pos in SWITCHES[:2]:
        server.command(f"setblock {pos} minecraft:redstone_block")
    summon_item(server, "2740.5 81.1 8.5", "legacy_allowed", iron(2, byte=True))
    summon_orb(server, "2750.5 81.1 8.5", "legacy_xp", 7, 1)
    old_chest = f"{{{old_settings(1)},Items:{{Items:[{iron(2, byte=True)},{original}]}}}}"
    old_xp = f"{{{old_settings(2)},{fluid(140, legacy=True)}}}"
    server.assert_state("official_forge_6_collects_filtered_items_and_xp",
                        f"if data block {CHEST} {old_chest} if data block {XP} {old_xp}", results, timeout=18)
    server.command(f"setblock {ARCHIVE} minecraft:barrel")
    for index, location, name, old in ((0, CHEST, CHEST_ID, old_chest), (1, XP, XP_ID, old_xp)):
        server.command(f"loot replace block {ARCHIVE} container.{index} mine {location} minecraft:diamond_pickaxe")
        server.assert_state(f"official_forge_6_loot_retains_{name.split(':')[1]}_state",
                            f'if data block {ARCHIVE} Items[{{Slot:{index}b,id:"{name}",Count:1b}}].tag.BlockEntityTag{old}', results)


def migrated_states(server, results, phase, inventory, amount):
    filter_path = f'Items.Items[{{Slot:27,id:"enderio:basic_item_filter"}}].tag."enderio:data"."enderio:item_filter"'
    server.assert_state(f"migrated_vacuums_keep_ranges_controls_and_balances_{phase}",
                        f"if data block {CHEST} {{{radius(1)}}} {stored_iron(CHEST, inventory)} "
                        f"if data block {CHEST} {filter_path}.items[0].value{iron(1)} "
                        f"if data block {XP} {{{radius(2)},{fluid(amount)}}}", results)
    for index, name, size in ((0, CHEST_ID, 1), (1, XP_ID, 2)):
        archived = f'Items[{{Slot:{index}b,id:"{name}"}}].tag'
        expected = f'{{"enderio:action_range":{{range:{size},isVisible:1b}},"enderio:redstone_control":"active_with_signal"}}'
        server.assert_state(f"migrated_{name.split(':')[1]}_drop_keeps_portable_state_{phase}",
                            f'if data block {ARCHIVE} {archived}."enderio:data"{expected}', results)
    server.assert_state(f"migrated_xp_vacuum_drop_promotes_xp_juice_{phase}",
                        f'if data block {ARCHIVE} Items[{{Slot:1b}}].tag."enderio:data"."enderio:item_fluid_content"'
                        f'{{FluidName:"{JUICE}",Amount:140}}', results)


def convert(server, results):
    migrated_states(server, results, "on_load", 2, 140)
    summon_item(server, "2740.5 81.1 8.5", "migrated_allowed", iron(3, byte=True))
    summon_item(server, "2740.5 81.1 8.5", "migrated_denied", stack("minecraft:iron_ingot", 5, byte=True))
    summon_orb(server, "2750.5 81.1 8.5", "migrated_orbs", 7, 2)
    server.assert_state("converted_vacuums_collect_exact_allowed_items_and_merged_xp", f"{stored_iron(CHEST, 5)} "
                        f"if data block {XP} {{{fluid(420)}}} unless entity {entity('item','tag=migrated_allowed')} "
                        f"if entity {entity('item','tag=migrated_denied')} "
                        f"unless entity {entity('experience_orb','tag=migrated_orbs')}", results, timeout=20)


def reopened_conversion(server, results):
    migrated_states(server, results, "after_restart", 5, 420)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--legacy-jar", type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")
    root = ROOT / "build/port-runtime" / args.name
    root.mkdir()
    port = root / "port"
    prepare_profile(args, port, args.jar)
    report = {"passed": False, "jarSha256": digest(port / "mods/enderio.jar"), "assertions": [], "servers": []}
    try:
        if args.legacy_jar is None:
            run_stage(args, port, report, "collect", collect)
            run_stage(args, port, report, "reopened", reopen)
        else:
            old = root / "legacy"
            prepare_profile(args, old, args.legacy_jar)
            report["legacySha256"] = digest(old / "mods/enderio.jar")
            run_stage(args, old, report, "legacy", legacy)
            shutil.copytree(old / WORLD, port / WORLD)
            run_stage(args, port, report, "converted", convert)
            run_stage(args, port, report, "reopened", reopened_conversion)
        report["passed"] = True
    finally:
        (root / "vacuum-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
