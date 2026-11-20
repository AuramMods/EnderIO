#!/usr/bin/env python3
"""Verify headless Forge Drain collection, restart, and Forge 6.2 migration."""

import argparse
import json
from pathlib import Path
import shutil
import sys

sys.dont_write_bytecode = True
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage


DRAIN = "2631 80 8"
INTAKE = "2631 79 8"
FIRST_SOURCE = "2630 77 7"
SECOND_SOURCE = "2632 77 7"
SWITCH = "2630 80 8"
TANK = "2632 80 8"
BARREL = "2634 80 8"
SOURCE = "minecraft:lava[level=0]"
CAPACITOR = '{Slot:0,id:"enderio:basic_capacitor",Count:1}'
OLD_CAPACITOR = '{Slot:0,id:"enderio:basic_capacitor",Count:1b}'
ITEM_DATA = 'tag."enderio:data"'


def tank_contents(amount, *, legacy=False, fluid="minecraft:lava"):
    stack = f'FluidName:"{fluid}",Amount:{amount}'
    if legacy:
        return f'Fluid:{{Tanks:[{{Index:0,{stack}}}]}}'
    return f'Fluids:{{Tanks:[{{Index:0,Contents:{{{stack}}}}}]}}'


def machine(energy, paid, radius, *, redstone="active_with_signal", lava=0):
    fields = [f"EnergyStored:{energy}", f"Consumed:{paid}", f'RedstoneControl:"{redstone}"',
              f"ActionRange:{{range:{radius},isVisible:1b}}", f"Items:{{Items:[{CAPACITOR}]}}"]
    if lava:
        fields.append(tank_contents(lava))
    return "{" + ",".join(fields) + "}"


def prepare_world(server, results):
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    server.command("forceload add 2624 0 2639 15")
    server.assert_state("drain_chunk_loaded", f"if loaded {DRAIN}", results)
    server.command("fill 2629 76 6 2633 79 10 minecraft:stone")
    server.command(f"setblock {DRAIN} enderio:drain[facing=south]")
    server.command(f"setblock {INTAKE} {SOURCE}")
    server.command(f"setblock {SECOND_SOURCE} {SOURCE}")


def partial_job(server, results):
    prepare_world(server, results)
    server.command(f"data merge block {DRAIN} {machine(600, 0, 0)}")
    server.assert_state("redstone_gates_paid_operation", f"if data block {DRAIN} {machine(600, 0, 0)} "
                        f"if block {INTAKE} {SOURCE} if block {DRAIN} enderio:drain[powered=false]", results)
    server.command(f"setblock {SWITCH} minecraft:redstone_block")
    server.assert_state("drain_pays_600_fe_and_leaves_both_sources_when_underfunded",
                        f"if data block {DRAIN} {machine(0, 600, 0)} if block {INTAKE} {SOURCE} "
                        f"if block {SECOND_SOURCE} {SOURCE}", results, timeout=20)


def resume_job(server, results):
    server.assert_state("range_controls_capacitor_and_partial_payment_survive_restart",
                        f"if data block {DRAIN} {machine(0, 600, 0)} if block {INTAKE} {SOURCE}", results)
    server.command(f"data merge block {DRAIN} {{EnergyStored:1400}}")
    server.assert_state("range_zero_resumes_for_exactly_the_outstanding_900_fe",
                        f"if data block {DRAIN} {machine(500, 0, 0, lava=1000)} if block {INTAKE} minecraft:air "
                        f"if block {SECOND_SOURCE} {SOURCE}", results, timeout=20)
    server.command(f"setblock {TANK} enderio:fluid_tank")
    server.assert_state("sided_push_exports_exactly_one_bucket_without_extra_energy",
                        f"if data block {TANK} {{{tank_contents(1000)}}} if data block {DRAIN} {machine(500, 0, 0)} "
                        f"unless data block {DRAIN} Fluids.Tanks[0].Contents.FluidName", results, timeout=15)


def reopen_export(server, results):
    server.assert_state("restart_preserves_exported_bucket_and_empty_drain",
                        f"if data block {TANK} {{{tank_contents(1000)}}} if data block {DRAIN} {machine(500, 0, 0)} "
                        f"unless data block {DRAIN} Fluids.Tanks[0].Contents.FluidName "
                        f"if block {INTAKE} minecraft:air if block {SECOND_SOURCE} {SOURCE}", results)


def prepare_legacy_job(server, results):
    prepare_world(server, results)
    server.command(f"setblock {FIRST_SOURCE} {SOURCE}")
    server.command(f"data merge block {DRAIN} {{Range:1,RangeVisible:1b,RedstoneControl:1,"
                   f"Energy:{{EnergyStored:2827}},Items:{{Size:1,Items:[{OLD_CAPACITOR}]}}}}")
    server.assert_state("official_forge_6_saves_range_one_before_activation",
                        f"if data block {DRAIN} {{Range:1,RangeVisible:1b,RedstoneControl:1,"
                        f"Energy:{{EnergyStored:2827}},Items:{{Items:[{OLD_CAPACITOR}]}}}}", results)


def legacy_job(server, results):
    # Forge 6 builds its scan on load. Reopen the configured range before starting the real job.
    server.command(f"setblock {SWITCH} minecraft:redstone_block")
    old = (f"{{Range:1,RangeVisible:1b,Energy:{{EnergyStored:0}},Consumed:1327,{tank_contents(1000, legacy=True)},"
           f"Items:{{Items:[{OLD_CAPACITOR}]}}}}")
    server.assert_state("official_forge_6_collects_one_bucket_and_pays_toward_the_second",
                        f"if data block {DRAIN} {old} if block {FIRST_SOURCE} minecraft:air "
                        f"if block {SECOND_SOURCE} {SOURCE} if block {INTAKE} {SOURCE}", results, timeout=35)
    server.command(f"setblock {BARREL} minecraft:barrel")
    server.command(f"loot replace block {BARREL} container.0 mine {DRAIN} minecraft:diamond_pickaxe")
    server.assert_state("official_forge_6_drop_includes_range_lava_capacitor_and_partial_payment",
                        f'if data block {BARREL} Items[{{Slot:0b,id:"enderio:drain",tag:{{BlockEntityTag:{old}}}}}]', results)


def check_converted_stack(server, results):
    stack = f"Items[{{Slot:0b}}].{ITEM_DATA}"
    server.assert_state("forge_6_loot_moves_range_lava_and_controls_into_item_data",
                        f'if data block {BARREL} {stack}{{"enderio:action_range":{{range:1,isVisible:1b}},'
                        '"enderio:item_fluid_content":{FluidName:"minecraft:lava",Amount:1000},'
                        '"enderio:redstone_control":"active_with_signal","enderio:energy":0} '
                        f"if data block {BARREL} Items[{{Slot:0b}}].tag.BlockEntityTag{{Consumed:1327,"
                        f"Items:{{Items:[{OLD_CAPACITOR}]}}}} "
                        f"unless data block {BARREL} Items[{{Slot:0b}}].tag.BlockEntityTag.Fluid", results)


def convert_job(server, results):
    server.assert_state("forge_6_placed_drain_keeps_its_range_lava_and_prepaid_energy",
                        f"if data block {DRAIN} {machine(0, 1327, 1, lava=1000)} "
                        f"if block {FIRST_SOURCE} minecraft:air if block {SECOND_SOURCE} {SOURCE}", results)
    check_converted_stack(server, results)
    server.command(f"data merge block {DRAIN} {{EnergyStored:173}}")
    check_converted_job(server, results, "converted_drain_collects_second_source_for_the_remaining_173_fe")


def check_converted_job(server, results, assertion):
    server.assert_state(assertion,
                        f"if data block {DRAIN} {machine(0, 0, 1, lava=2000)} "
                        f"if block {SECOND_SOURCE} minecraft:air if block {INTAKE} {SOURCE}", results, timeout=20)


def reopen_converted(server, results):
    check_converted_job(server, results, "converted_second_bucket_and_empty_payment_survive_restart")
    check_converted_stack(server, results)


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
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    directory.mkdir()
    port = directory / "port"
    prepare_profile(args, port, args.jar)
    report = {"passed": False, "jarSha256": digest(port / "mods/enderio.jar"), "assertions": [], "servers": []}
    try:
        if args.legacy_jar is None:
            run_stage(args, port, report, "partial", partial_job)
            run_stage(args, port, report, "resumed", resume_job)
            run_stage(args, port, report, "reopened", reopen_export)
        else:
            legacy = directory / "legacy"
            prepare_profile(args, legacy, args.legacy_jar)
            report["legacySha256"] = digest(legacy / "mods/enderio.jar")
            run_stage(args, legacy, report, "legacy_setup", prepare_legacy_job)
            run_stage(args, legacy, report, "legacy", legacy_job)
            shutil.copytree(legacy / WORLD, port / WORLD)
            run_stage(args, port, report, "converted", convert_job)
            run_stage(args, port, report, "reopened", reopen_converted)
        report["passed"] = True
    finally:
        (directory / "drain-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
