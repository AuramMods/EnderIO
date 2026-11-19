#!/usr/bin/env python3
"""Verify native automated crafting, remainders and recipe reload on packaged Forge."""

import argparse
import json
from pathlib import Path
import sys

sys.dont_write_bytecode = True
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage


CAKE_CRAFTER = "3072 80 8"
BLOCKED = "3075 80 8"
AUTOMATED = "3078 80 8"
RELOAD = "3081 80 8"
CAKE = ("milk_bucket", "milk_bucket", "milk_bucket", "sugar", "egg", "sugar", "wheat", "wheat", "wheat")
CAPACITOR = '{Slot:0,id:"enderio:basic_capacitor",Count:1}'
CRAFT_COST = 3200


def stack(slot, item, count=1):
    return f'{{Slot:{slot},id:"minecraft:{item}",Count:{count}}}'


def grid(first, items):
    return [stack(first + index, item) for index, item in enumerate(items) if item]


def state(pos, energy, *items):
    return (f"if block {pos} enderio:crafter if data block {pos} "
            f"{{EnergyStored:{energy},Items:{{Items:[{','.join(items)}]}}}}")


def seed(server, pos, energy, items, redstone="always_active"):
    server.command(f"setblock {pos} enderio:crafter[facing=south]")
    server.command(f'data merge block {pos} {{EnergyStored:{energy},RedstoneControl:"{redstone}",'
                   f'Items:{{Items:[{",".join([CAPACITOR, *items])}]}}}}')


def write_recipe(directory, result):
    pack = directory / WORLD / "datapacks/crafter-reload"
    recipe = pack / "data/port_crafter/recipes/copper_token.json"
    recipe.parent.mkdir(parents=True, exist_ok=True)
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 15, "description": "Crafter reload fixture"}}) + "\n")
    recipe.write_text(json.dumps({"type": "minecraft:crafting_shapeless",
                                  "ingredients": [{"item": "minecraft:copper_ingot"}],
                                  "result": {"item": "minecraft:" + result}}) + "\n")


def output_lane(server, x):
    server.command(f"setblock {x} 78 8 minecraft:barrel")
    server.command(f"setblock {x} 79 8 minecraft:hopper[facing=down]")


def cake_waiting(server, results):
    physical_inputs = " ".join(f"unless data block {CAKE_CRAFTER} Items.Items[{{Slot:{slot}}}]" for slot in range(1, 10))
    blocked_bucket_queue = " ".join(f"if data block {CAKE_CRAFTER} OutputBuffer[{i}]" for i in range(3))
    server.assert_state("cake_is_paid_once_and_three_remainders_wait_behind_it",
                        f"{state(CAKE_CRAFTER, CRAFT_COST, CAPACITOR, stack(10, 'cake'), *grid(11, CAKE))} "
                        f"{physical_inputs} {blocked_bucket_queue} unless data block {CAKE_CRAFTER} OutputBuffer[3] "
                        f'if data block {CAKE_CRAFTER} {{OutputBuffer:[{{id:"minecraft:bucket",Count:1}}]}}',
                        results, timeout=18)


def cake_drained(server, results):
    server.assert_state("vanilla_hopper_recovers_one_cake_and_all_three_buckets_once",
                        f"{state(CAKE_CRAFTER, CRAFT_COST, CAPACITOR, *grid(11, CAKE))} "
                        'if data block 3072 78 8 {Items:[{Slot:0b,id:"minecraft:cake",Count:1b},'
                        '{Slot:1b,id:"minecraft:bucket",Count:3b}]} unless data block 3072 78 8 Items[2] '
                        f"unless data block {CAKE_CRAFTER} Items.Items[{{Slot:10}}] "
                        f"unless data block {CAKE_CRAFTER} OutputBuffer "
                        "unless data block 3072 79 8 Items[0]", results, timeout=16)


def blocked_cake(server, results):
    server.assert_state("existing_unstackable_cake_leaves_exact_energy_and_inputs",
                        f"{state(BLOCKED, 2 * CRAFT_COST, CAPACITOR, stack(10, 'cake'), *grid(1, CAKE), *grid(11, CAKE))} "
                        f"unless data block {BLOCKED} OutputBuffer", results)


def automated_sticks(server, results):
    server.assert_state("vanilla_hoppers_feed_two_planks_and_export_four_sticks",
                        f"{state(AUTOMATED, CRAFT_COST, CAPACITOR, stack(11, 'oak_planks'), stack(14, 'oak_planks'))} "
                        'if data block 3078 78 8 {Items:[{Slot:0b,id:"minecraft:stick",Count:4b}]} '
                        "unless data block 3078 78 8 Items[1] unless data block 3078 81 8 Items[0] "
                        f"unless data block {AUTOMATED} Items.Items[{{Slot:1}}] "
                        f"unless data block {AUTOMATED} Items.Items[{{Slot:4}}] "
                        f"unless data block {AUTOMATED} Items.Items[{{Slot:10}}]", results, timeout=18)


def reload_recipe(server, results):
    server.assert_state("original_datapack_recipe_has_the_brick_preview",
                        state(RELOAD, 2 * CRAFT_COST, CAPACITOR, stack(1, "copper_ingot"),
                              stack(11, "copper_ingot"), stack(20, "brick")), results)
    write_recipe(server.directory, "nether_brick")
    server.command("reload")
    server.assert_state("live_recipe_reload_replaces_preview_while_redstone_is_disabled",
                        state(RELOAD, 2 * CRAFT_COST, CAPACITOR, stack(1, "copper_ingot"),
                              stack(11, "copper_ingot"), stack(20, "nether_brick")), results, timeout=20)
    server.command(f'data modify block {RELOAD} RedstoneControl set value "always_active"')
    reloaded_output(server, results)


def reloaded_output(server, results):
    server.assert_state("reloaded_recipe_produces_only_the_new_result_and_charges_once",
                        f"{state(RELOAD, CRAFT_COST, CAPACITOR, stack(10, 'nether_brick'), stack(11, 'copper_ingot'), stack(20, 'nether_brick'))} "
                        f"unless data block {RELOAD} Items.Items[{{Slot:1}}] unless data block {RELOAD} OutputBuffer",
                        results, timeout=15)


def prepare(server, results):
    server.command("gamerule doMobSpawning false")
    server.command("forceload add 3072 0 3087 15")
    server.assert_state("fixture_chunk_is_loaded", f"if loaded {CAKE_CRAFTER} if loaded {RELOAD}", results)
    seed(server, CAKE_CRAFTER, 2 * CRAFT_COST, grid(1, CAKE) + grid(11, CAKE))
    seed(server, BLOCKED, 2 * CRAFT_COST, [stack(10, "cake"), *grid(1, CAKE), *grid(11, CAKE)])
    seed(server, AUTOMATED, 2 * CRAFT_COST, [stack(11, "oak_planks"), stack(14, "oak_planks")])
    output_lane(server, 3078)
    server.command('setblock 3078 81 8 minecraft:hopper[facing=down]{Items:[{Slot:0b,id:"minecraft:oak_planks",Count:2b}]}')
    seed(server, RELOAD, 2 * CRAFT_COST, [stack(1, "copper_ingot"), stack(11, "copper_ingot")], "never_active")
    reload_recipe(server, results)
    automated_sticks(server, results)
    cake_waiting(server, results)
    blocked_cake(server, results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")

    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    write_recipe(directory, "brick")
    report = {"passed": False, "forge": FORGE, "jarSha256": digest(directory / "mods/enderio.jar"),
              "assertions": [], "servers": []}
    try:
        def reopened(server, results):
            cake_waiting(server, results)
            blocked_cake(server, results)
            output_lane(server, 3072)
            cake_drained(server, results)

        def drained(server, results):
            cake_drained(server, results)
            automated_sticks(server, results)
            blocked_cake(server, results)
            reloaded_output(server, results)

        run_stage(args, directory, report, "fresh", prepare)
        run_stage(args, directory, report, "reopened", reopened)
        run_stage(args, directory, report, "drained", drained)
        report["passed"] = True
    finally:
        path = directory / "crafter-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Crafter passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
