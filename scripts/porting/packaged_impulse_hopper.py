#!/usr/bin/env python3
"""Verify complete Impulse Hopper batches, Forge automation and 6.2 save conversion."""

import argparse
import json
from pathlib import Path
import shutil
import sys

sys.dont_write_bytecode = True
from packaged_legacy_transfer_filters import wait_ticks
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage


MACHINE = "2820 80 8"
FEED = "2820 81 8"
BLOCKED_FEED = "2819 80 8"
EXPORT = "2820 79 8"
RECEIVER = "2821 79 8"
SIGNAL = "2820 80 7"
ARCHIVE = "2824 80 8"
BLOCK = "enderio:impulse_hopper"
BLOCKED_IO = '{Modes:{down:"disable",east:"disable"}}'
OPEN_IO = '{Modes:{down:"none",east:"disable"}}'
OLD_IO = '{"0":4b,"5":4b}'


def stack(slot, item, count=1, marker=None, *, legacy=False, vanilla=False):
    label = f",tag:{{PortFixture:{marker}}}" if marker is not None else ""
    return f'{{Slot:{slot}{"b" if vanilla else ""},id:"{item}",Count:{count}{"b" if legacy or vanilla else ""}{label}}}'


def iron(slot, count, marker=137, **kwargs):
    return stack(slot, "minecraft:iron_ingot", count, marker, **kwargs)


def redstone(slot, count=1, **kwargs):
    return stack(slot, "minecraft:redstone", count, **kwargs)


def cap(**kwargs):
    return stack(18, "enderio:basic_capacitor", **kwargs)


def machine_data(charge, *contents, legacy=False, io=BLOCKED_IO):
    items = "Items:{Size:19,Items:[" + ",".join(contents) + "]}"
    if legacy:
        return f"{{Energy:{{EnergyStored:{charge}}},IoConfig:{OLD_IO},RedstoneControl:1,{items}}}"
    return f'{{EnergyStored:{charge},IOConfig:{io},RedstoneControl:"active_with_signal",{items}}}'


def missing_machine_slots(*slots):
    return " ".join(f"unless data block {MACHINE} Items.Items[{{Slot:{slot}}}]" for slot in slots)


def assert_machine(server, results, name, data, *, extra=""):
    server.assert_state(name, f"if block {MACHINE} {BLOCK} if data block {MACHINE} {data} {extra}".strip(), results)


def prepare(server, results):
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    server.command("forceload add 2816 0 2831 15")
    server.assert_state("impulse_hopper_fixture_chunk_is_loaded", f"if loaded {MACHINE}", results)
    server.command(f"setblock {MACHINE} {BLOCK}[facing=south]")


def fill_from_vanilla_hopper(server, *items):
    server.command(f"setblock {FEED} minecraft:hopper[facing=down]")
    server.command(f"data merge block {FEED} {{Items:[{','.join(items)}]}}")


def wait_underfunded(server, results):
    prepare(server, results)
    patterns = (iron(12, 2), redstone(13), cap())
    server.command(f"data merge block {MACHINE} {machine_data(29, *patterns)}")
    # On a south-facing machine, the saved east port maps to the world west side.
    server.command(f"setblock {BLOCKED_FEED} minecraft:hopper[facing=east]")
    server.command(f"data merge block {BLOCKED_FEED} {{Items:[{iron(0, 3, vanilla=True)}]}}")
    fill_from_vanilla_hopper(server, iron(0, 2, vanilla=True), redstone(1, vanilla=True))
    queued = machine_data(29, iron(0, 2), redstone(1), *patterns)
    assert_machine(server, results, "vanilla_hopper_feeds_both_lanes_and_the_disabled_side_rejects_input", queued,
                   extra=f"unless data block {FEED} Items[0] if data block {BLOCKED_FEED} Items[{iron(0, 3, vanilla=True)}] "
                         f"{missing_machine_slots(6, 7)} if block {SIGNAL} minecraft:air")
    server.command(f"setblock {SIGNAL} minecraft:redstone_block")
    wait_ticks(server, 45)
    assert_machine(server, results, "a_29_fe_budget_cannot_release_any_part_of_a_30_fe_batch", queued,
                   extra=missing_machine_slots(6, 7))
    server.command(f"data modify block {MACHINE} Items.Items append value {iron(6, 1, 138)}")
    assert_machine(server, results, "the_pending_batch_and_conflicting_output_are_ready_to_save",
                   machine_data(29, iron(0, 2), redstone(1), iron(6, 1, 138), *patterns), extra=missing_machine_slots(7))


def resume_and_export(server, results):
    patterns = (iron(12, 2), redstone(13), cap())
    blocked = (iron(0, 2), redstone(1), iron(6, 1, 138), *patterns)
    assert_machine(server, results, "both_inputs_ghost_patterns_and_wrong_output_survive_restart",
                   machine_data(29, *blocked), extra=missing_machine_slots(7))
    server.command(f"data merge block {MACHINE} {{EnergyStored:30}}")
    wait_ticks(server, 45)
    assert_machine(server, results, "a_funded_batch_waits_until_the_incompatible_output_is_removed",
                   machine_data(30, *blocked), extra=missing_machine_slots(7))
    server.command(f"data remove block {MACHINE} Items.Items[{{Slot:6}}]")
    assert_machine(server, results, "exactly_one_complete_batch_is_delivered_for_30_fe",
                   machine_data(0, iron(6, 2), redstone(7), *patterns), extra=missing_machine_slots(0, 1))
    server.command(f"setblock {RECEIVER} minecraft:barrel")
    server.command(f"data modify block {MACHINE} IOConfig set value {OPEN_IO}")
    server.command(f"setblock {EXPORT} minecraft:hopper[facing=east]")
    verify_export(server, results, "vanilla_hopper_exports_only_the_tagged_output_and_redstone")


def verify_export(server, results, name):
    expected = machine_data(0, iron(12, 2), redstone(13), cap(), io=OPEN_IO)
    assert_machine(server, results, name, expected, extra=f"{missing_machine_slots(0, 1, 6, 7)} "
                   f"if data block {RECEIVER} Items[{iron(0, 2, vanilla=True)}] "
                   f"if data block {RECEIVER} Items[{redstone(1, vanilla=True)}] "
                   f"unless data block {RECEIVER} Items[2] unless data block {EXPORT} Items[0] "
                   f"if data block {BLOCKED_FEED} Items[{iron(0, 3, vanilla=True)}]")


def legacy_prepare(server, results):
    prepare(server, results)
    original = machine_data(1000, iron(0, 2, legacy=True), redstone(1, legacy=True),
                            iron(12, 1, legacy=True), redstone(13, legacy=True), cap(legacy=True), legacy=True)
    server.command(f"data merge block {MACHINE} {original}")
    server.command(f"setblock {SIGNAL} minecraft:redstone_block")
    saved = machine_data(980, iron(0, 1, legacy=True), iron(6, 1, legacy=True), redstone(7, legacy=True),
                         iron(12, 1, legacy=True), redstone(13, legacy=True), cap(legacy=True), legacy=True)
    assert_machine(server, results, "official_forge_6_runs_one_batch_and_saves_the_second_batch_input", saved,
                   extra=missing_machine_slots(1))
    server.command(f"setblock {ARCHIVE} minecraft:barrel")
    server.command(f"loot replace block {ARCHIVE} container.0 mine {MACHINE} minecraft:diamond_pickaxe")
    server.assert_state("official_forge_6_drop_contains_the_charge_pattern_input_and_outputs",
                        f'if data block {ARCHIVE} Items[{{Slot:0b,id:"{BLOCK}",Count:1b}}].tag.BlockEntityTag{saved}', results)


def check_archived(server, results, name):
    path = f'Items[{{Slot:0b,id:"{BLOCK}",Count:1b}}].tag'
    portable = ('{"enderio:energy":980,"enderio:io_config":' + BLOCKED_IO
                + ',"enderio:redstone_control":"active_with_signal"}')
    contents = ",".join((iron(0, 1, legacy=True), iron(6, 1, legacy=True), redstone(7, legacy=True),
                         iron(12, 1, legacy=True), redstone(13, legacy=True), cap(legacy=True)))
    server.assert_state(name, f'if data block {ARCHIVE} {path}."enderio:data"{portable} '
                        f"if data block {ARCHIVE} {path}.BlockEntityTag{{Items:{{Items:[{contents}]}}}}", results)


def saved_second_batch(server, results, name):
    assert_machine(server, results, name, machine_data(960, iron(6, 2), redstone(7, 2), iron(12, 1), redstone(13), cap()),
                   extra=missing_machine_slots(0, 1))


def convert(server, results):
    assert_machine(server, results, "converted_machine_retains_exact_charge_controls_ghosts_and_partial_batch",
                   machine_data(980, iron(0, 1), iron(6, 1), redstone(7), iron(12, 1), redstone(13), cap()),
                   extra=missing_machine_slots(1))
    check_archived(server, results, "converted_drop_promotes_charge_and_controls_keeps_inventory")
    fill_from_vanilla_hopper(server, redstone(0, vanilla=True))
    saved_second_batch(server, results, "converted_machine_finishes_the_second_batch_for_20_fe")


def final_restart(server, results):
    verify_export(server, results, "exported_batch_and_exact_charge_survive_another_restart")


def converted_restart(server, results):
    saved_second_batch(server, results, "completed_migrated_batch_stays_exact_after_restart")
    check_archived(server, results, "converted_drop_and_its_saved_inventory_survive_restart")


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
            run_stage(args, port, report, "blocked", wait_underfunded)
            run_stage(args, port, report, "resumed", resume_and_export)
            run_stage(args, port, report, "reopened", final_restart)
        else:
            old = root / "legacy"
            prepare_profile(args, old, args.legacy_jar)
            report["legacySha256"] = digest(old / "mods/enderio.jar")
            run_stage(args, old, report, "legacy", legacy_prepare)
            shutil.copytree(old / WORLD, port / WORLD)
            run_stage(args, port, report, "converted", convert)
            run_stage(args, port, report, "reopened", converted_restart)
        report["passed"] = True
    finally:
        (root / "impulse-hopper-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
