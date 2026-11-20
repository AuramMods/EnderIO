#!/usr/bin/env python3
"""Migrate original Ender IO 6.2 item and fluid filters and exercise their routes."""

import argparse
import json
from pathlib import Path
import shutil
import sys

sys.dont_write_bytecode = True
from packaged_levitation_staff import game_tick
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage


CHUNKS = "4624 0 4655 15"
BASIC = "basic_item_filter"
ADVANCED = "advanced_item_filter"
FLUID = "basic_fluid_filter"
XP = ("enderio:xp_juice", "enderio:fluid_xp_juice_still")


def pos(x, z):
    return f"{x} 80 {z}"


def load(server, results, stage):
    server.command(f"forceload add {CHUNKS}")
    conditions = " ".join(f"if loaded {pos(x, 8)}" for x in (4639, 4640))
    server.assert_state(f"both_chunks_loaded_{stage}", conditions, results)


def stock(item, slot, count=1, sample=None, damage=None):
    properties = []
    if sample is not None:
        properties.append(f"Sample:{sample}")
    if damage is not None:
        properties.append(f"Damage:{damage}")
    tag = ",tag:{" + ",".join(properties) + "}" if properties else ""
    return f'{{Slot:{slot}b,id:"{item}",Count:{count}b{tag}}}'


def items(server, x, z, *stacks):
    server.command(f"data merge block {pos(x, z)} {{Items:[{','.join(stacks)}]}}")


def inventory(x, z, *stacks):
    return f"if data block {pos(x, z)} {{Items:[{','.join(stacks)}]}}"


def liquid(fluid, amount, sample):
    tag = f",Tag:{{Sample:{sample}}}" if sample is not None else ""
    return f'{{FluidName:"{fluid}",Amount:{amount}{tag}}}'


def tank(x, z, legacy, fluid=None, amount=0, sample=None):
    if not fluid:
        if legacy:
            return f'if data block {pos(x, z)} Fluid.Tanks[{{FluidName:"minecraft:empty",Amount:0}}]'
        return f"unless data block {pos(x, z)} Fluids.Tanks[0].Contents.FluidName"
    key = "Fluid" if legacy else "Fluids"
    stack = liquid(fluid, amount, sample)
    entry = f"Index:0,Capacity:16000,{stack[1:-1]}" if legacy else f"Index:0,Contents:{stack}"
    return f"if data block {pos(x, z)} {key}.Tanks[{{{entry}}}]"


def fill(server, z, legacy, fluid, amount, sample):
    key = "Fluid" if legacy else "Fluids"
    stack = liquid(fluid, amount, sample)
    entry = f"Index:0,Capacity:16000,{stack[1:-1]}" if legacy else f"Index:0,Contents:{stack}"
    server.command(f"data modify block {pos(4638, z)} {key}.Tanks set value [{{{entry}}}]")


def wait_ticks(server, ticks):
    end = game_tick(server) + ticks
    while game_tick(server) < end:
        pass


def empty_destination(server, z, legacy):
    p = pos(4641, z)
    if legacy:
        server.command(f'data modify block {p} Fluid.Tanks set value '
                       '[{Index:0,Capacity:16000,FluidName:"minecraft:empty",Amount:0}]')
    else:
        server.command(f"data modify block {p} Fluids.Tanks set value [{{Index:0,Contents:{{}}}}]")


def route_items(server, results, stage, legacy):
    paper = stock("minecraft:paper", 0, 5, 41)
    other = stock("minecraft:paper", 1, 7, 51)
    iron = stock("minecraft:iron_ingot", 2, 3)
    items(server, 4641, 2)
    items(server, 4638, 2, paper, other, iron)
    server.assert_state(f"basic_filter_moves_both_paper_tags_{stage}",
                        f"{inventory(4638, 2, iron)} {inventory(4641, 2, paper, other)}", results)
    server.assert_state(f"basic_filter_rejects_iron_{stage}",
                        f'unless data block {pos(4641, 2)} Items[{{id:"minecraft:iron_ingot"}}]', results)

    denied = stock("minecraft:iron_pickaxe", 0, sample=42, damage=27)
    different_damage = stock("minecraft:iron_pickaxe", 1, sample=42, damage=33)
    different_tag = stock("minecraft:iron_pickaxe", 2, sample=52, damage=27)
    alias = "copper_alloy_ingot" if legacy else "conductive_alloy_ingot"
    ingot = stock(f"enderio:{alias}", 3, 4)
    items(server, 4641, 5)
    items(server, 4638, 5, denied, different_damage, different_tag, ingot)
    destination = pos(4641, 5)
    server.assert_state(f"advanced_filter_distinguishes_damage_and_tags_{stage}",
                        f"{inventory(4638, 5, denied, ingot)} "
                        f'if data block {destination} Items[{{id:"minecraft:iron_pickaxe",Count:1b,tag:{{Damage:33,Sample:42}}}}] '
                        f'if data block {destination} Items[{{id:"minecraft:iron_pickaxe",Count:1b,tag:{{Damage:27,Sample:52}}}}]',
                        results)
    server.assert_state(f"advanced_filter_rejects_exact_sample_and_aliased_ingot_{stage}",
                        f'unless data block {destination} Items[{{id:"enderio:{alias}"}}] '
                        f'unless data block {destination} Items[{{id:"minecraft:iron_pickaxe",tag:{{Damage:27,Sample:42}}}}]', results)


def route_fluids(server, results, stage, legacy, *, partial_tagged_water=False):
    water = "minecraft:water"
    for z in (8, 11):
        empty_destination(server, z, legacy)
    if legacy:
        fill(server, 8, True, water, 625, 43)
        fill(server, 11, True, "minecraft:lava", 575, None)
        wait_ticks(server, 96)
        if partial_tagged_water:
            server.assert_state("early_ticker_saves_a_partial_tagged_water_transfer",
                                f"{tank(4638, 8, True, water, 575, 43)} {tank(4641, 8, True, water, 50, 43)}", results)
        else:
            # 6.2.18 drains using a tagless FluidStack before invoking a filter.
            server.assert_state("original_ticker_leaves_tagged_water_in_its_source",
                                f"{tank(4638, 8, True, water, 625, 43)} {tank(4641, 8, True)}", results)
        server.assert_state("original_inverted_filter_rejects_untagged_lava",
                            f'{tank(4638, 11, True, "minecraft:lava", 575)} {tank(4641, 11, True)}', results)
        untagged_routes = ((11, 575),) if partial_tagged_water else ((8, 625), (11, 575))
        for z, amount in untagged_routes:
            fill(server, z, True, water, amount, None)
            server.assert_state(f"original_filter_transfers_allowed_untagged_water_in_lane_{z}",
                                f"{tank(4641, z, True, water, amount)} {tank(4638, z, True)}", results)
        return

    fill(server, 8, legacy, water, 625, 43)
    fill(server, 11, legacy, water, 575, 54)
    for z, amount, sample in ((8, 625, 43), (11, 575, 54)):
        server.assert_state(f"fluid_filter_transfers_all_allowed_fluid_in_lane_{z}_{stage}",
                            f"{tank(4641, z, legacy, water, amount, sample)} {tank(4638, z, legacy)}", results)

    for z in (8, 11):
        empty_destination(server, z, legacy)
    fill(server, 8, legacy, water, 625, 53)
    fill(server, 11, legacy, water, 575, 44)
    wait_ticks(server, 24)
    for z, amount, sample in ((8, 625, 53), (11, 575, 44)):
        server.assert_state(f"fluid_filter_leaves_rejected_contents_in_lane_{z}_{stage}",
                            f"{tank(4638, z, legacy, water, amount, sample)} {tank(4641, z, legacy)}", results)


def installed(z, side, legacy):
    x = 4639 if side == "west" else 4640
    if legacy:
        key = "ExtractFilter" if side == "west" else "InsertFilter"
        return f'{pos(x, z)} ConduitBundle.Connections.{side}."0".{key}'
    index = 0 if side == "west" else 1
    return f'{pos(x, z)} Connections[0][{{Side:"{side}",Status:"connected_block"}}].Inventory[{index}]'


def snapshot(server, results, stage, legacy):
    slots = ((2, "west", 0, BASIC, "basic", 5, 4), (5, "east", 3, ADVANCED, "advanced", 10, 9),
             (8, "west", 8, FLUID, "fluid", 5, 4), (11, "east", 15, FLUID, "inverted-fluid", 5, 1))
    values = ('{id:"minecraft:paper",tag:{Sample:41}}', '{id:"minecraft:iron_pickaxe",tag:{Damage:27,Sample:42}}',
              liquid("minecraft:water", 37, 43), liquid(XP[0 if legacy else 1], 11, 44))
    for (z, side, slot, name, marker, length, index), value in zip(slots, values):
        old_key = "FluidEntries" if name == FLUID else "ItemEntries"
        for location, path in (("loose", f'{pos(4638, 14)} Items[{{Slot:{slot}b,id:"enderio:{name}"}}]'),
                               ("installed", installed(z, side, legacy))):
            conditions = [f'if data block {path}.tag{{PortFilter:"{marker}"}}']
            if legacy:
                entries = f"{path}.tag.{old_key}"
                flags = f"IsInverted:{int(z in (5, 11))}b,IsNbt:{int(z in (5, 8))}b"
                tag = ".Tag" if name == FLUID else ".tag"
                conditions.extend((f"if data block {entries}[{value}]", f"if data block {entries}[{index}]{tag}",
                                   f"if data block {path}.tag{{{flags}}}"))
                if z == 5:
                    conditions.append(f'if data block {entries}[{{id:"enderio:copper_alloy_ingot"}}]')
                if z == 11:
                    conditions.append(f'if data block {entries}[{liquid("minecraft:water", 19, 44)}]')
                    conditions.append(f'if data block {entries}[{liquid("minecraft:lava", 23, None)}]')
            else:
                key = "fluid_filter" if name == FLUID else "item_filter"
                data = f'{path}.tag."enderio:data"."enderio:{key}"'
                entries = data + (".fluids" if name == FLUID else ".items")
                conditions.extend((f"if data block {entries}[{index}].value{value}",
                                   f"unless data block {path}.tag.{old_key}",
                                   f"unless data block {path}.tag.IsInverted",
                                   f"unless data block {path}.tag.IsNbt"))
                for field, enabled in (("isInvert", z in (5, 11)), ("isNbt", z in (5, 8))):
                    if enabled or name == FLUID:
                        conditions.append(f"if data block {data}{{{field}:{int(enabled)}b}}")
                    else:
                        conditions.append(f"unless data block {data}.{field}")
                if z == 5:
                    conditions.extend((f'if data block {entries}[2].value{{id:"enderio:conductive_alloy_ingot"}}',
                                       f"if data block {data}{{compareLegacyTags:1b}}"))
                if name == FLUID:
                    conditions.append(f"if data block {data}{{legacyMatching:1b}}")
                if z == 11:
                    conditions.append(f'if data block {entries}[4].value{liquid("minecraft:water", 19, 44)}')
                    conditions.append(f'if data block {entries}[3].value{liquid("minecraft:lava", 23, None)}')
            conditions.extend((f"if data block {entries}[{length - 1}]", f"unless data block {entries}[{length}]"))
            server.assert_state(f"{location}_{marker}_keeps_sparse_samples_and_flags_{stage}", " ".join(conditions), results)


def legacy_world(server, results, *, partial_tagged_water):
    load(server, results, "legacy")
    server.command("gamerule doMobSpawning false")
    server.command("fill 4637 79 1 4642 79 15 minecraft:stone")
    for z in (2, 5, 8, 11):
        block = "minecraft:barrel" if z in (2, 5) else "enderio:fluid_tank"
        for x in (4638, 4641):
            server.command(f"setblock {pos(x, z)} {block}")
    server.command(f"setblock {pos(4638, 14)} minecraft:barrel")
    output = server.command("port_legacy_transfer_filters")
    if "Legacy transfer filter routes created" not in output:
        raise AssertionError(output)
    snapshot(server, results, "legacy", True)
    route_items(server, results, "legacy", True)
    route_fluids(server, results, "legacy", True, partial_tagged_water=partial_tagged_water)


def converted_world(server, results, *, partial_tagged_water):
    load(server, results, "converted")
    snapshot(server, results, "converted", False)
    if partial_tagged_water:
        server.assert_state("converted_route_finishes_the_saved_partial_tagged_water_transfer",
                            f'{tank(4638, 8, False)} {tank(4641, 8, False, "minecraft:water", 625, 43)}', results)
    route_items(server, results, "converted", False)
    route_fluids(server, results, "converted", False)
    server.command(f"forceload remove {CHUNKS}")
    server.assert_state("both_filter_chunks_unloaded",
                        " ".join(f"unless loaded {pos(x, 8)}" for x in (4639, 4640)), results, timeout=40)
    load(server, results, "chunk_reload")
    snapshot(server, results, "chunk_reload", False)


def reopened_world(server, results):
    load(server, results, "restart")
    snapshot(server, results, "restart", False)
    route_items(server, results, "restart", False)
    route_fluids(server, results, "restart", False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--forge-server", type=Path, required=True)
    parser.add_argument("--legacy-jar", type=Path, required=True)
    parser.add_argument("--legacy-partial-tagged-water", action="store_true",
                        help="Preserve the partial tagged-water transfer produced by the 6.2.0-beta ticker")
    parser.add_argument("--fixture", type=Path, required=True)
    parser.add_argument("--jar", type=Path, required=True)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")
    root = ROOT / "build/port-runtime" / args.name
    root.mkdir()
    legacy, port = root / "legacy", root / "port"
    report = {"passed": False, "jarSha256": digest(args.jar), "legacyJarSha256": digest(args.legacy_jar),
              "assertions": [], "servers": []}
    try:
        prepare_profile(args, legacy, args.legacy_jar)
        shutil.copyfile(args.fixture, legacy / "mods/fixture.jar")
        run_stage(args, legacy, report, "legacy", lambda server, results:
                  legacy_world(server, results, partial_tagged_water=args.legacy_partial_tagged_water))
        prepare_profile(args, port, args.jar)
        shutil.copytree(legacy / WORLD, port / WORLD)
        run_stage(args, port, report, "converted", lambda server, results:
                  converted_world(server, results, partial_tagged_water=args.legacy_partial_tagged_water))
        run_stage(args, port, report, "reopened", reopened_world)
        if any(stage["registryErrors"] for stage in report["servers"]):
            raise RuntimeError("Unexpected Forge registry errors")
        report["passed"] = True
    finally:
        (root / "legacy-transfer-filter-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
