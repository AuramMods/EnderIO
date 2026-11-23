#!/usr/bin/env python3
"""Verify scripted painting, soul binding, fire crafting, and legacy grinding balls."""

import argparse
import json
from pathlib import Path
import shutil
import sys
import time

sys.dont_write_bytecode = True
from packaged_crafttweaker import FIXTURES, check_script
from packaged_levitation_staff import game_tick
from packaged_painting_machine import CAPACITOR, COST, inventory, paint_tag, palette
from packaged_sag_progression import charge, feed, inputs, mill, output, prepare_mill
from packaged_server_smoke import ROOT, digest
from packaged_soul_binder import item, xp_tank
from packaged_spawners import prepare_profile, run_stage
from packaged_starter_progression import hopper_item


PAINT_INPUT = '{Slot:1,id:"minecraft:brick",Count:1}'
PALETTE = palette("gold_block")
BINDER_INPUT_NBT = "tag:{PortCatalyst:12}"
VIAL_NBT = 'tag:{"enderio:data":{"enderio:soul":{entity_type:"minecraft:cow",entity_tag:{}}}}'
BINDER_VIAL = item(0, "enderio:soul_vial", VIAL_NBT)
BINDER_CAP = item(4, "enderio:octadic_capacitor")


def exercise(server, results, offset, phase):
    server.command("forceload add 3600 0 3647 15")
    paint_x, binder_x, fire_x, sag_x = (x + offset for x in (3602, 3605, 3608, 3611))
    painter, binder, base = mill(paint_x), mill(binder_x), mill(fire_x)
    fire = f"{fire_x} 81 8"
    server.assert_state("special_recipe_chunks_loaded", f"if loaded {painter} if loaded {mill(sag_x)}", results)
    server.command("gamerule doMobSpawning false")
    server.command("gamerule doFireTick false")
    server.command(f'setblock {painter} enderio:painting_machine[facing=south]'
                   f'{{EnergyStored:{COST + 137},Items:{{Items:[{CAPACITOR},{PAINT_INPUT},{PALETTE}]}}}}')
    binder_fe = 2400 if phase == "replaced" else 1600
    server.command(f"setblock {binder} enderio:soul_binder[facing=south]")
    server.command(f'data merge block {binder} {{EnergyStored:{binder_fe + 137},Items:{{Items:[{BINDER_CAP}]}}}}')
    experience_pos = f"{binder_x} 80 7"
    vial_feed, quartz_feed = mill(binder_x - 1), mill(binder_x + 1)
    server.command(f'setblock {experience_pos} enderio:fluid_tank[facing=south]'
                   f'{{{xp_tank(777)},IOConfig:{{Modes:{{south:"push"}}}}}}')
    server.command(f'setblock {vial_feed} minecraft:hopper[facing=east]'
                   f'{{Items:[{item(0, "enderio:soul_vial", VIAL_NBT, vanilla=True)}]}}')
    server.command(f'setblock {quartz_feed} minecraft:hopper[facing=west]'
                   f'{{Items:[{item(0, "minecraft:quartz", BINDER_INPUT_NBT, vanilla=True)}]}}')
    server.command(f"fill {fire_x - 2} 79 6 {fire_x + 2} 79 10 minecraft:stone")
    server.command(f"setblock {base} minecraft:emerald_block")
    server.command(f"setblock {fire} minecraft:fire")

    prepare_mill(server, sag_x)
    server.command(f"item replace block {feed(sag_x)} container.1 with minecraft:feather")
    if phase != "removed":
        server.assert_state("hopper_loads_the_legacy_grinding_ball_before_charging",
                            f'if data block {mill(sag_x)} Items.Items[{{Slot:5,id:"minecraft:feather",Count:1}}]', results)
    # A SAG Mill loads a ball as it completes the first eligible job.
    inputs(server, sag_x, "minecraft:stone_button", 2)
    sag_fe = {"added": 600, "replaced": 900, "removed": 1200}[phase]
    charge(server, sag_x, 1200 + sag_fe + 137)

    if phase == "removed":
        time.sleep(1)
        server.assert_state("removed_paint_keeps_template_paint_and_fe",
                            f"{inventory(painter, [PAINT_INPUT, PALETTE], COST + 137)} "
                            f"unless data block {painter} Task unless data block {painter} Items.Items[{{Slot:3}}]", results)
        server.assert_state("removed_binding_keeps_vial_catalyst_xp_and_fe",
                            f'if data block {binder} {{EnergyStored:{binder_fe + 137},{xp_tank(777)},'
                            f'Items:{{Items:[{BINDER_VIAL},{BINDER_CAP}]}}}} unless data block {binder} Task '
                            f'{hopper_item(quartz_feed, "minecraft:quartz", 1)}', results, timeout=15)
    else:
        painted = "painted_fence" if phase == "added" else "painted_sand"
        product = f'{{Slot:3,id:"enderio:{painted}",Count:1,{paint_tag("gold_block")}}}'
        server.assert_state("painter_uses_scripted_template_and_live_paint",
                            f"{inventory(painter, [PALETTE, product], 137)} unless data block {painter} Task "
                            f"unless data block {painter} Items.Items[{{Slot:1}}]", results, timeout=25)
        product_id = "amethyst_shard" if phase == "added" else "prismarine_shard"
        catalyst_marker = f",{BINDER_INPUT_NBT}" if phase == "added" else ""
        xp_remaining = 637 if phase == "added" else 457
        try:
            server.assert_state("soul_binder_matches_cow_spends_exact_xp_fe_and_copies_requested_nbt",
                                f'if data block {binder} {{EnergyStored:137,{xp_tank(xp_remaining)},'
                                f'Items:{{Items:[{{Slot:2,id:"minecraft:{product_id}",Count:1{catalyst_marker}}},'
                                f'{{Slot:3,id:"enderio:soul_vial",Count:1}}]}}}} '
                                f"unless data block {binder} Task unless data block {binder} Items.Items[{{Slot:0}}] "
                                f"unless data block {binder} Items.Items[{{Slot:1}}]", results, timeout=25)
        except AssertionError:
            for pos in (binder, experience_pos, vial_feed, quartz_feed):
                server.command(f"data get block {pos}")
            raise
        if phase == "replaced":
            server.assert_state("disabled_nbt_copy_produces_unmarked_output",
                                f"unless data block {binder} Items.Items[{{Slot:2}}].tag.PortCatalyst", results)

    sag_count = {"added": 3, "replaced": 4, "removed": 2}[phase]
    sag_state = f'if data block {mill(sag_x)} {{EnergyStored:137,GrindingBallDamage:{sag_fe}}}'
    if phase == "removed":
        sag_state = (f"if data block {mill(sag_x)} {{EnergyStored:137}} "
                     f"unless data block {mill(sag_x)} GrindingBallDamage "
                     f'if data block {feed(sag_x)} Items[{{id:"minecraft:feather",Count:1b}}]')
    server.assert_state("legacy_ball_changes_actual_sag_yield_fe_and_durability",
                        f"{sag_state} {hopper_item(output(sag_x), 'minecraft:glow_ink_sac', sag_count)} "
                        f"unless data block {mill(sag_x)} Task", results, timeout=25)

    if phase == "removed":
        finish = game_tick(server) + 280
        deadline = time.monotonic() + 40
        while game_tick(server) < finish:
            if time.monotonic() > deadline:
                raise AssertionError("The dedicated server did not tick past the fire crafting age")
        server.assert_state("removing_fire_recipe_preserves_base_and_prevents_drops",
                            f"if block {base} minecraft:emerald_block if block {fire} minecraft:fire "
                            f"unless entity @e[type=item,x={fire_x},y=81,z=8,distance=..6]", results)
    else:
        after, drop, count = (("diamond_block", "rabbit_hide", 2) if phase == "added"
                              else ("lapis_block", "quartz", 3))
        server.assert_state("fire_recipe_produces_scripted_drops_and_replacement_base",
                            f'if block {base} minecraft:{after} if block {fire} minecraft:air '
                            f'if entity @e[type=item,x={fire_x},y=81,z=8,distance=..6,'
                            f'nbt={{Item:{{id:"minecraft:{drop}",Count:{count}b}}}}]', results, timeout=30)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    for key in ("forge-server", "jar", "crafttweaker"):
        parser.add_argument(f"--{key}", type=Path, required=True)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    directory.parent.mkdir(parents=True, exist_ok=True)
    prepare_profile(args, directory, args.jar)
    shutil.copyfile(args.crafttweaker, directory / "mods" / args.crafttweaker.name)
    script = directory / "scripts/special_recipes.zs"
    script.parent.mkdir()
    original = (FIXTURES / script.name).read_text()
    phases = (
        ("added", ""),
        ("replaced", """
painter.replaceRecipe("crafttweaker:qa_paint", <item:minecraft:brick>, <item:enderio:painted_sand>);
enchanter.replaceRecipe("crafttweaker:qa_enchanter", <item:minecraft:amethyst_shard> * 3,
    <enchantment:minecraft:respiration>, 2);
binder.replaceRecipe("crafttweaker:qa_binder", <item:minecraft:quartz>, <item:minecraft:prismarine_shard>,
    2400, 2, SoulSelector.category("creature"), false);
fire.replaceRecipe("crafttweaker:qa_fire", [<block:minecraft:emerald_block>], [], [<resource:minecraft:overworld>],
    <block:minecraft:lapis_block>, FireOutput.item(<item:minecraft:quartz>, 3, 3, 1.0f));
balls.replaceRecipe("crafttweaker:qa_ball", <item:minecraft:feather>, 3.0f, 1.0f, 0.75f, 5000);
"""),
        ("removed", """
painter.removeByName("crafttweaker:qa_paint");
enchanter.removeByEnchantment(<enchantment:minecraft:aqua_affinity>);
binder.removeByName("crafttweaker:qa_binder");
fire.remove(<item:minecraft:rabbit_hide>);
balls.removeByName("crafttweaker:qa_ball");
"""),
    )
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}
    try:
        for index, (phase, extra) in enumerate(phases):
            script.write_text(original + extra)

            def scenario(server, results):
                check_script(server, "PORT_CT_SPECIAL_ADD_OK")
                exercise(server, results, 12 * index, phase)

            run_stage(args, directory, report, phase, scenario)
        report["passed"] = True
    finally:
        result = directory / "crafttweaker-special-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"CraftTweaker special recipes passed={report['passed']}, {result}", flush=True)


if __name__ == "__main__":
    main()
