#!/usr/bin/env python3
"""Run CraftTweaker edits through tank, VAT, and Weather Obelisk gameplay."""

import argparse
import json
from pathlib import Path
import shutil
import sys
import time

sys.dont_write_bytecode = True
from packaged_crafttweaker import FIXTURES, check_script
from packaged_obelisks import ROCKETS, weather_state, write_predicates
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage
from packaged_vat_automation import drained, fluid, tank_fields


def exercise(server, results, offset, phase):
    server.command("forceload add 3552 0 3583 15")
    fill_pos, empty_pos, vat_pos, weather_pos = (f"{x + offset} 80 8" for x in (3553, 3556, 3559, 3562))
    server.assert_state("fluid_machine_chunks_loaded", f"if loaded {fill_pos} if loaded {weather_pos}", results)
    server.command("weather clear 10000")
    server.command("gamerule doWeatherCycle false")
    tanks = [(fill_pos, 2, "paper", "feather"), (empty_pos, 0, "rabbit_hide", "leather")]
    if phase == "replaced":
        tanks = [(fill_pos, 2, "paper", "ink_sac"), (empty_pos, 0, "rabbit_hide", "glass_pane")]
    for pos, slot, item, _ in tanks:
        initial = tank_fields(fluid("minecraft:water", 2000))
        server.command(f'setblock {pos} enderio:pressurized_fluid_tank[facing=south]'
                       f'{{{initial},Items:{{Items:[{{Slot:{slot},id:"minecraft:{item}",Count:1}}]}}}}')
    vat_items = '{Items:[{Slot:0,id:"minecraft:copper_ingot",Count:1},{Slot:1,id:"minecraft:gold_ingot",Count:1}]}'
    server.command(f'setblock {vat_pos} enderio:vat[facing=south]'
                   f'{{{tank_fields(fluid("minecraft:water", 400))},Items:{vat_items}}}')
    server.command(f'setblock {weather_pos} enderio:weather_obelisk[facing=south]'
                   f'{{{tank_fields(fluid("minecraft:lava", 337))},{ROCKETS}}}')

    if phase == "removed":
        time.sleep(1)
        for pos, slot, item, _ in tanks:
            start = tank_fields(fluid("minecraft:water", 2000))
            server.assert_state(f"tank_{item}_removal_preserves_fluid_and_item",
                                f'if data block {pos} {{{start},Items:{{Items:[{{Slot:{slot},id:"minecraft:{item}",Count:1}}]}}}} '
                                f"unless data block {pos} Items.Items[{{Slot:{slot + 1}}}]", results)
        server.assert_state("vat_removal_preserves_reagents_and_fluid",
                            f'if data block {vat_pos} {{{tank_fields(fluid("minecraft:water", 400))},Items:{vat_items}}} '
                            f"{drained(vat_pos, 1)} unless data block {vat_pos} Task", results)
        server.assert_state("weather_removal_preserves_rocket_and_fluid",
                            f'{weather_state("minecraft:lava", 337, 2, pos=weather_pos)} if predicate port_qa:clear '
                            f"unless data block {weather_pos} Task", results)
        return

    server.assert_state("scripted_weather_uses_the_native_600_tick_job",
                        f'if data block {weather_pos} {{Task:{{RecipeId:"crafttweaker:qa_weather",ProgressRequired:600}}}}',
                        results)
    final_tanks = (1900, 2225) if phase == "added" else (1825, 2350)
    for (pos, slot, item, output), amount in zip(tanks, final_tanks):
        count = 2 if slot == 2 else 1
        server.assert_state(f"tank_{item}_makes_exact_item_and_fluid_change",
                            f'if data block {pos} {{{tank_fields(fluid("minecraft:water", amount))},'
                            f'Items:{{Items:[{{Slot:{slot + 1},id:"minecraft:{output}",Count:{count}}}]}}}} '
                            f"unless data block {pos} Items.Items[{{Slot:{slot}}}]", results)

    input_left, vat_out = (150, 425) if phase == "added" else (100, 475)
    server.assert_state("vat_consumes_exact_reagents_and_produces_scripted_fluid",
                        f'if data block {vat_pos} {{{tank_fields(fluid("minecraft:water", input_left))}}} '
                        f'if data block {vat_pos} {{{tank_fields(fluid("minecraft:lava", vat_out), index=1)}}} '
                        f"unless data block {vat_pos} Items.Items[0] unless data block {vat_pos} Task", results, timeout=15)
    weather, weather_amount = ("rain", 137) if phase == "added" else ("lightning", 37)
    server.assert_state("weather_obelisk_resolves_scripted_fluid_and_rocket_cost",
                        f'{weather_state("minecraft:lava", weather_amount, 1, pos=weather_pos)} '
                        f"unless data block {weather_pos} Task if predicate port_qa:{weather}", results, timeout=90)


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
    write_predicates(directory)
    shutil.copyfile(args.crafttweaker, directory / "mods" / args.crafttweaker.name)
    script = directory / "scripts/fluids.zs"
    script.parent.mkdir()
    original = (FIXTURES / script.name).read_text()
    phases = (
        ("added", ""),
        ("replaced", """
vat.replaceRecipe("crafttweaker:qa_vat", <fluid:minecraft:water> * 300, <tag:items:forge:ingots/gold>,
    <tag:items:forge:ingots/copper>, <fluid:minecraft:lava> * 475, 40);
tank.replaceRecipe("crafttweaker:qa_tank_fill", "fill", <item:minecraft:paper>, <item:minecraft:ink_sac> * 2,
    <fluid:minecraft:water> * 175);
tank.replaceRecipe("crafttweaker:qa_tank_empty", "empty", <item:minecraft:rabbit_hide>, <item:minecraft:glass_pane>,
    <tag:fluids:minecraft:water> * 350);
weather.replaceRecipe("crafttweaker:qa_weather", <fluid:minecraft:lava> * 300, "lightning");
"""),
        ("removed", """
vat.removeFluidOutput(<fluid:minecraft:lava> * 425);
tank.removeByName("crafttweaker:qa_tank_fill");
tank.removeFluidOutput(<fluid:minecraft:water> * 225);
weather.removeByName("crafttweaker:qa_weather");
"""),
    )
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}
    try:
        for index, (phase, extra) in enumerate(phases):
            script.write_text(original + extra)

            def scenario(server, results):
                check_script(server, "PORT_CT_FLUID_ADD_OK")
                exercise(server, results, 8 * index, phase)

            run_stage(args, directory, report, phase, scenario)
        report["passed"] = True
    finally:
        result = directory / "crafttweaker-fluids-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"CraftTweaker fluids passed={report['passed']}, {result}", flush=True)


if __name__ == "__main__":
    main()
