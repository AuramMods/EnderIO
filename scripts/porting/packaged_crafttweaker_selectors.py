#!/usr/bin/env python3
"""Test typed ingredients, registry tags, and modern processing data in Forge."""

import argparse
import json
from pathlib import Path
import shutil
import sys
import time

sys.dont_write_bytecode = True
from packaged_crafttweaker import FIXTURES, check_script
from packaged_levitation_staff import game_tick
from packaged_sag_progression import charge, feed, mill, output, prepare_mill
from packaged_server_smoke import ROOT, digest
from packaged_soul_binder import item, xp_tank
from packaged_spawners import prepare_profile, run_stage
from packaged_starter_progression import hopper_item
from packaged_vat_automation import fluid, tank_fields


def conduit(variant):
    return f'enderio:conduit{{"enderio:data":{{"enderio:conduit":"enderio:{variant}"}}}}'


def vial(entity):
    return f'enderio:soul_vial{{"enderio:data":{{"enderio:soul":{{entity_type:"minecraft:{entity}",entity_tag:{{}}}}}}}}'


def exercise(server, results, *, changed):
    server.command("forceload add 3696 0 3743 15")
    server.assert_state("selector_test_chunks_loaded", "if loaded 3696 80 8 if loaded 3743 80 8", results)
    server.command("gamerule doMobSpawning false")
    server.command("gamerule doFireTick false")
    z_offset = 6 if changed else 0
    # Rows share their x coordinates across runs, but every test starts with fresh blocks.
    def clear_columns(xs):
        for x in xs:
            server.command(f"fill {x} 79 8 {x} 81 8 minecraft:air")

    rows = ((3700, conduit("energy"), None if changed else "rabbit_hide"),
            (3703, conduit("fluid"), "rabbit_hide" if changed else None),
            (3706, conduit("enhanced_energy"), "glow_ink_sac"),
            (3709, "enderio:soul_vial", "feather"),
            (3712, vial("cow"), "blaze_powder"))
    clear_columns(x for x, _, _ in rows)
    for x, source, _ in rows:
        prepare_mill(server, x)
        server.command(f"item replace block {feed(x)} container.0 with {source}")
        charge(server, x, 537)

    vat = f"3717 80 {8 + z_offset}"
    reagents = 'Items:{Items:[{Slot:0,id:"minecraft:gold_ingot",Count:1},{Slot:1,id:"minecraft:copper_ingot",Count:1}]}'
    server.command(f'setblock {vat} enderio:vat[facing=south]'
                   f'{{{tank_fields(fluid("minecraft:water", 350))},{reagents}}}')

    binder_x = 3721 + (6 if changed else 0)
    binder, vial_hopper, catalyst_hopper = mill(binder_x), mill(binder_x - 1), mill(binder_x + 1)
    tank = f"{binder_x} 80 7"
    server.command(f"setblock {binder} enderio:soul_binder[facing=south]")
    capacitor = item(4, "enderio:octadic_capacitor")
    server.command(f'data merge block {binder} {{EnergyStored:1737,Items:{{Items:[{capacitor}]}}}}')
    cow = vial("cow").removeprefix("enderio:soul_vial")
    server.command(f'setblock {vial_hopper} minecraft:hopper[facing=east]'
                   f'{{Items:[{item(0, "enderio:soul_vial", "tag:" + cow, vanilla=True)}]}}')
    server.command(f'setblock {catalyst_hopper} minecraft:hopper[facing=west]'
                   f'{{Items:[{item(0, "minecraft:quartz", vanilla=True)}]}}')
    server.command(f'setblock {tank} enderio:fluid_tank[facing=south]'
                   f'{{{xp_tank(777)},IOConfig:{{Modes:{{south:"push"}}}}}}')

    fire_x = 3732 + (6 if changed else 0)
    base, fire = mill(fire_x), feed(fire_x)
    server.command(f"fill {fire_x - 2} 79 6 {fire_x + 2} 79 10 minecraft:stone")
    server.command(f"setblock {base} minecraft:emerald_block")
    server.command(f"setblock {fire} minecraft:fire")
    deadline_tick = game_tick(server) + 270
    for x, source, result in rows:
        if result is None:
            item_id = source.partition("{")[0]
            server.assert_state(f"nonmember_variant_is_not_ground_{x}",
                                f"if data block {mill(x)} {{EnergyStored:537}} unless data block {mill(x)} Task "
                                f"{hopper_item(feed(x), item_id, 1)} unless data block {output(x)} Items[0]", results)
        else:
            server.assert_state(f"ingredient_matches_the_exact_variant_or_storage_state_{x}",
                                f"if data block {mill(x)} {{EnergyStored:137}} unless data block {mill(x)} Task "
                                f"{hopper_item(output(x), 'minecraft:' + result, 1)}", results, timeout=20)

    expected = 425 if changed else 1275
    server.assert_state("vat_yield_data_changes_the_native_output_amount",
                        f'if data block {vat} {{{tank_fields(fluid("minecraft:lava", expected), 1)}}} '
                        f"unless data block {vat} Items.Items[0]", results, timeout=20)
    if changed:
        server.assert_state("edited_entity_type_tag_rejects_cow_without_cost",
                            f'if data block {binder} {{EnergyStored:1737,{xp_tank(777)}}} '
                            f"unless data block {binder} Task", results)
    else:
        server.assert_state("entity_type_tag_allows_bound_cow",
                            f'if data block {binder} {{EnergyStored:137,{xp_tank(637)},'
                            'Items:{Items:[{Slot:2,id:"minecraft:emerald",Count:1}]}}', results, timeout=20)
    deadline = time.monotonic() + 35
    while game_tick(server) < deadline_tick:
        if time.monotonic() > deadline:
            raise AssertionError("The fire crafting test did not advance past the configured age")
    if changed:
        server.assert_state("changed_dimension_group_disallows_overworld_fire",
                            f"if block {base} minecraft:emerald_block if block {fire} minecraft:fire", results)
    else:
        server.assert_state("dimension_group_matches_overworld_fire", f"if block {base} minecraft:diamond_block", results)


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
    scripts = directory / "scripts"
    scripts.mkdir()
    source = (FIXTURES / "selectors.zs").read_text()
    tags = (FIXTURES / "selectors_tags.zs").read_text()
    (scripts / "selectors.zs").write_text(source)
    (scripts / "selectors_tags.zs").write_text(tags)

    def original(server, results):
        check_script(server, "PORT_CT_SELECTORS_OK")
        exercise(server, results, changed=False)

    def edited(server, results):
        check_script(server, "PORT_CT_SELECTORS_OK")
        check_script(server, "PORT_CT_SELECTORS_EDITED_OK")
        exercise(server, results, changed=True)

    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}
    try:
        run_stage(args, directory, report, "original", original)
        (scripts / "selectors_tags.zs").write_text(tags.replace("minecraft:cow", "minecraft:sheep"))
        (scripts / "selectors.zs").write_text(source + """
variants.replace(<resource:enderio:fluid>);
dimensions.replace(<resource:minecraft:the_nether>);
sag.replaceRecipe("crafttweaker:qa_conduit_group", variants.asIngredient(), 400, "none",
    SagOutput.item(<item:minecraft:rabbit_hide>, 1.0f, false));
<recipetype:enderio:fire_crafting>.replaceRecipe("crafttweaker:qa_fire_group",
    [<block:minecraft:emerald_block>], [], dimensions.members, <block:minecraft:diamond_block>,
    FireOutput.item(<item:minecraft:rabbit_hide>, 2, 2, 1.0f));
ProcessingData.removeVatYield(<item:minecraft:gold_ingot>, <tag:items:forge:ingots/gold>);
ProcessingData.removeGrindingBall(<item:minecraft:feather>);
ProcessingData.removeRangeExtension(<block:minecraft:amethyst_block>, <tag:blocks:enderio:range_extender>);
if ProcessingData.getGrindingBall(<item:minecraft:feather>) == null
        && ProcessingData.getVatYield(<item:minecraft:gold_ingot>, <tag:items:forge:ingots/gold>) == 1.0
        && ProcessingData.getRangeExtension(<block:minecraft:amethyst_block>, <tag:blocks:enderio:range_extender>) == 0 {
    println("PORT_CT_SELECTORS_EDITED_OK");
}
""")
        run_stage(args, directory, report, "edited", edited)
        report["passed"] = True
    finally:
        result = directory / "crafttweaker-selectors-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"CraftTweaker selectors passed={report['passed']}, {result}", flush=True)


if __name__ == "__main__":
    main()
