#!/usr/bin/env python3
"""Check vacuum conservation, chunk eviction and migration on headless Forge."""

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
BLOCKED_TANK = "2751 80 8"
XP_EXPORT = "2759 80 8"
TANK = "2760 80 8"
BARREL = "2740 80 7"
ARCHIVE = "2743 80 13"
CHUNKS = "2736 0 2767 15"
ENTITY_AREA = "x=2737,y=79,z=4,dx=26,dy=5,dz=10"
SWITCHES = ("2740 80 9", "2750 80 9", "2759 80 9")
CHEST_ID = "enderio:vacuum_chest"
XP_ID = "enderio:xp_vacuum"
JUICE = "enderio:fluid_xp_juice_still"
OLD_JUICE = "enderio:xp_juice"
CAPACITY = 2147483647
TANK_CAPACITY = 16000
STARTING_XP_JUICE = CAPACITY - 47
BLOCKED_WATER = f'Fluids:{{Tanks:[{{Index:0,Contents:{{FluidName:"minecraft:water",Amount:{TANK_CAPACITY}}}}}]}}'
LIMIT_DATA = ('{"enderio:data":{"enderio:limited_item_filter":{isNbt:1b,items:[{index:0,'
              'value:{id:"minecraft:iron_ingot",Count:4,tag:{PortFixture:135}}}]}}}')
OLD_FILTER_DATA = '{IsInverted:0b,IsNbt:1b,ItemEntries:[{id:"minecraft:iron_ingot",Count:1b,tag:{PortFixture:135}}]}'


def stack(item, count, *, slot=None, tag=None, byte=False):
    fields = [f'id:"{item}"', f"Count:{count}{'b' if byte else ''}"]
    if slot is not None:
        fields.insert(0, f"Slot:{slot}{'b' if byte else ''}")
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
    server.command(f"forceload add {CHUNKS}")
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


def blocked_barrel():
    items = ",".join(stack("minecraft:cobblestone", 64, slot=slot, byte=True) for slot in range(27))
    return f"{{Items:[{items}]}}"


def limited_filter():
    return stack("enderio:limited_item_filter", 1, slot=27, tag=LIMIT_DATA)


def active_controls():
    return (f"if block {SWITCHES[0]} minecraft:redstone_block if block {SWITCHES[1]} minecraft:redstone_block "
            f"if data block {CHEST} {{{radius(2)},Items:{{Items:[{limited_filter()}]}}}} "
            f"if data block {XP} {{{radius(1)}}}")


def blocked_outputs_and_pending_inputs(server, results, stage):
    stock_remainder(server, results, f"stock_limit_keeps_exact_tagged_items_{stage}")
    split_orbs(server, results, f"whole_and_partial_orbs_keep_exact_xp_{stage}")
    for kind, amount in (("item", 3), ("experience_orb", 2)):
        server.command(f"execute store result score #{kind} port_vacuum if entity @e[type=minecraft:{kind},{ENTITY_AREA}]")
        server.assert_state(f"keeps_exact_number_of_{kind}_entities_{stage}",
                            f"if score #{kind} port_vacuum matches {amount}", results)
    server.assert_state(f"outputs_stay_blocked_with_both_vacuums_active_{stage}",
                        f"{active_controls()} if data block {BARREL} {blocked_barrel()} "
                        f"if data block {BLOCKED_TANK} {{{BLOCKED_WATER}}}", results)


def collect(server, results):
    base(server, results)
    server.command("scoreboard objectives add port_vacuum dummy")
    server.command(f"setblock {BARREL} minecraft:barrel")
    server.command(f"data merge block {BARREL} {blocked_barrel()}")
    server.command(f"setblock {BLOCKED_TANK} enderio:fluid_tank")
    server.command(f"data merge block {BLOCKED_TANK} {{{BLOCKED_WATER}}}")
    server.command(f"data merge block {CHEST} {{{radius(2)},Items:{{Size:28,Items:[{iron(1, slot=0)},{limited_filter()}]}}}}")
    server.command(f"data merge block {XP} {{{radius(1)},{fluid(STARTING_XP_JUICE, tagged=True)}}}")
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
                        f"if data block {XP} {{{fluid(STARTING_XP_JUICE, tagged=True)}}} "
                        f"if entity {entity('experience_orb', 'tag=vacuum_merged,nbt={Value:7s,Count:3}')}", results)
    for pos in SWITCHES:
        server.command(f"setblock {pos} minecraft:redstone_block")
    xp_export(server, results, "sided_tank_receives_all_three_merged_orbs_as_420_mb")
    blocked_outputs_and_pending_inputs(server, results, "before_eviction")

    server.command(f"forceload remove {CHUNKS}")
    server.assert_state("both_fixture_chunks_and_pending_entities_are_evicted",
                        f"unless loaded {CHEST} unless loaded {XP} unless loaded {XP_EXPORT} "
                        f"unless entity @e[type=minecraft:item,{ENTITY_AREA}] "
                        f"unless entity @e[type=minecraft:experience_orb,{ENTITY_AREA}]", results, timeout=45)
    server.command(f"forceload add {CHUNKS}")
    server.assert_state("both_fixture_chunks_reload",
                        f"if loaded {CHEST} if loaded {XP} if loaded {XP_EXPORT}", results, timeout=20)
    blocked_outputs_and_pending_inputs(server, results, "after_chunk_reload")
    xp_export(server, results, "the_control_tank_keeps_its_exported_xp_through_chunk_reload")


def reopen(server, results):
    blocked_outputs_and_pending_inputs(server, results, "after_restart")
    xp_export(server, results, "the_sided_tank_persists_all_exported_xp")
    server.command(f"data remove block {BARREL} Items")
    server.command(f"data remove block {BLOCKED_TANK} Fluids")
    resumed_outputs(server, results, "when_outputs_are_freed")


def resumed_outputs(server, results, stage):
    server.assert_state(f"sided_output_delivers_all_eleven_tagged_items_{stage}",
                        f"if data block {BARREL} Items[{iron(11, slot=0, byte=True)}] "
                        f'unless data block {CHEST} Items.Items[{{id:"minecraft:iron_ingot"}}] '
                        f"unless entity {entity('item', 'tag=vacuum_stock')} "
                        f"if entity {item_on_ground('vacuum_mismatch', stack('minecraft:iron_ingot', 2, byte=True))} "
                        f"if entity {item_on_ground('vacuum_outside', iron(3, byte=True))} {active_controls()}",
                        results, timeout=25)
    remaining = STARTING_XP_JUICE + 21 * 20 - TANK_CAPACITY
    server.assert_state(f"both_tanks_account_for_all_twenty_one_converted_points_{stage}",
                        f"if data block {XP} {{{fluid(remaining, tagged=True)}}} "
                        f"if data block {BLOCKED_TANK} {{{fluid(TANK_CAPACITY, tagged=True)}}} "
                        f"unless entity @e[type=minecraft:experience_orb,{ENTITY_AREA}]", results, timeout=25)


def exercise_installed_filter(server, results, x, item, *, index, settings, sample, accepted, rejected):
    chest = f"{x} 80 13"
    data = f'{{"enderio:data":{{"enderio:item_filter":{{{settings},items:[{{index:{index},value:{sample}}}]}}}}}}'
    installed = stack(f"enderio:{item}", 1, slot=27, tag=data)
    server.command(f"setblock {chest} {CHEST_ID}")
    server.command(f"data merge block {chest} {{{radius(1)},Items:{{Size:28,Items:[{installed}]}}}}")
    for number, (item_id, count, tag) in enumerate((accepted, *rejected)):
        summon_item(server, f"{x}.5 81.1 13.5", f"{item}_{number}", stack(item_id, count, tag=tag, byte=True))
    server.command(f"setblock {x} 80 14 minecraft:redstone_block")
    item_id, count, tag = accepted
    stored = stack(item_id, count, tag=tag, slot=0)
    excludes = " ".join(
        f"if entity {item_on_ground(f'{item}_{number}', stack(item_id, count, tag=tag, byte=True))}"
        for number, (item_id, count, tag) in enumerate(rejected, 1)
    )
    server.assert_state(f"{item}_collects_only_its_allowed_stack",
                        f"if data block {chest} {{Items:{{Items:[{installed},{stored}]}}}} "
                        f"unless data block {chest} Items.Items[{{Slot:1}}] "
                        f"unless entity {entity('item', f'tag={item}_0')} {excludes}", results, timeout=20)


def recovered(server, results):
    resumed_outputs(server, results, "after_recovery_restart")
    exercise_installed_filter(server, results, 2740, "big_item_filter",
                            index=35, settings="isInvert:1b", sample=stack("minecraft:paper", 1),
                            accepted=("minecraft:gold_ingot", 3, "{Large:1}"),
                            rejected=(("minecraft:paper", 7, "{Large:2}"),))
    exercise_installed_filter(server, results, 2748, "advanced_item_filter",
                            index=17, settings='isNbt:1b,damageMode:"only_damaged"',
                            sample=stack("minecraft:iron_pickaxe", 1, tag="{Damage:17,Advanced:1}"),
                            accepted=("minecraft:iron_pickaxe", 1, "{Damage:52,Advanced:1}"),
                            rejected=(("minecraft:iron_pickaxe", 1, "{Damage:52,Advanced:2}"),
                                      ("minecraft:iron_pickaxe", 1, "{Advanced:1}")))
    exercise_installed_filter(server, results, 2759, "big_advanced_item_filter",
                            index=35, settings='isNbt:1b,damageMode:"up_to_25"',
                            sample=stack("minecraft:diamond_pickaxe", 1, tag="{Damage:27,LargeAdvanced:1}"),
                            accepted=("minecraft:diamond_pickaxe", 1, "{Damage:100,LargeAdvanced:1}"),
                            rejected=(("minecraft:diamond_pickaxe", 1, "{Damage:100,LargeAdvanced:2}"),
                                      ("minecraft:diamond_pickaxe", 1, "{Damage:500,LargeAdvanced:1}")))


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
            run_stage(args, port, report, "recovered", recovered)
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
