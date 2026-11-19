#!/usr/bin/env python3
"""Craft, fuel and use the Cold Fire Igniter on a packaged Forge server."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys
import time

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_levitation_staff import game_tick
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage

PLAYER = "EnderIOQA"
ITEM = "enderio:cold_fire_igniter"
VAPOR = "enderio:fluid_vapor_of_levity_still"
LEGACY_VAPOR = "enderio:vapor_of_levity"
TANK = "3188 80 9"
BARREL = "3194 80 10"
FIRST_FIRE = "3190 81 8"
SECOND_FIRE = "3192 81 8"
AGE = 260
LEGACY_NAME = '{"text":"Archive Igniter"}'
LEGACY_METADATA = f"PortLegacy:181,display:{{Name:'{LEGACY_NAME}'}}"


def amount(millibuckets, fluid=VAPOR):
    return f'{{FluidName:"{fluid}",Amount:{millibuckets},Tag:{{PortBatch:180}}}}'


def current_tag(millibuckets):
    return f'"enderio:data":{{"enderio:item_fluid_content":{amount(millibuckets)}}}'


def held(millibuckets, *, legacy=False):
    tag = current_tag(millibuckets)
    if legacy:
        tag += f",{LEGACY_METADATA}"
    return f'if data entity {PLAYER} Inventory[{{id:"{ITEM}",Count:1b,tag:{{{tag}}}}}]'


def grain(x, radius=1.3):
    return (f'@e[type=minecraft:item,x={x},y=81,z=8.5,distance=..{radius},sort=nearest,limit=1,'
            'nbt={Item:{id:"enderio:grains_of_infinity",Count:1b}}]')


def collected(count):
    return f'if data entity {PLAYER} Inventory[{{id:"enderio:grains_of_infinity",Count:{count}b}}]'


def wait_until_tick(server, target, timeout):
    deadline = time.monotonic() + timeout
    while game_tick(server) < target:
        if time.monotonic() >= deadline:
            raise AssertionError(f"Server time did not reach {target}; see {server.log_path}")
        time.sleep(0.2)


def collect_grain(server, results, x, count):
    # Let the native pickup delay expire and use the item's current position after it settles.
    wait_until_tick(server, game_tick(server) + 14, timeout=5)
    server.command(f"execute at {grain(x, radius=3)} run tp {PLAYER} ~ ~ ~")
    server.assert_state(f"survival_player_collects_grain_{count}", collected(count), results)


def add_recipe(directory):
    pack = directory / WORLD / "datapacks/port-cold-fire"
    recipe = pack / "data/port_cold_fire/recipes/granite.json"
    recipe.parent.mkdir(parents=True)
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 15, "description": "Exact cold-fire crafting result"}}))
    recipe.write_text(json.dumps({
        "type": "enderio:fire_crafting", "base_blocks": ["minecraft:granite"], "dimensions": ["minecraft:overworld"],
        "results": [{"chance": 1.0, "min_count": 1, "max_count": 1,
                     "result": {"id": "enderio:grains_of_infinity", "count": 1}}]
    }))


def setup(server, results, *, legacy):
    for command in ("gamerule doMobSpawning false", "gamerule spawnRadius 0", "gamerule doFireTick false", "time set noon",
                    "forceload add 3184 0 3199 15", "fill 3184 79 5 3196 79 15 minecraft:stone",
                    "setworldspawn 3190 80 14", "setblock 3190 80 10 minecraft:crafting_table",
                    "setblock 3189 82 9 minecraft:stone", "setblock 3191 80 9 minecraft:stone", "setblock 3191 81 9 minecraft:glass",
                    "setblock 3190 80 8 minecraft:granite", "setblock 3189 81 8 minecraft:oak_log",
                    "setblock 3192 80 8 minecraft:granite", "setblock 3193 81 8 minecraft:oak_log",
                    "setblock 3192 80 10 minecraft:stone"):
        server.command(command)
    if not legacy:
        server.command(f"setblock {TANK} enderio:fluid_tank{{Fluids:{{Tanks:[{{Index:0,Contents:{amount(25)}}}]}}}}")
        server.assert_state("tank_contains_exact_tagged_fuel",
                            f"if data block {TANK} Fluids.Tanks[{{Index:0,Contents:{amount(25)}}}]", results)


def legacy_slot(slot, tag):
    return f'if data block {BARREL} Items[{{Slot:{slot}b,id:"{ITEM}",Count:1b,tag:{{{tag}}}}}]'


def save_legacy_igniters(server, results):
    server.command("forceload add 3184 0 3199 15")
    server.assert_state("historical_barrel_chunk_loads", f"if loaded {BARREL}", results)
    server.command(f"setblock {BARREL} minecraft:barrel")
    stored = f"Fluid:{amount(25, LEGACY_VAPOR)},{LEGACY_METADATA}"
    unknown = f'Fluid:{amount(9, "example:removed_vapor")},PortLegacy:182'
    for slot, tag in enumerate((stored, unknown)):
        server.command(f"item replace block {BARREL} container.{slot} with {ITEM}{{{tag}}}")
    server.assert_state("forge6_saves_the_original_fuel_and_metadata",
                        f"{legacy_slot(0, stored)} {legacy_slot(1, unknown)}", results)


def verify_migrated_barrel(server, results, *, original_present):
    server.command(f"data get block {BARREL} Items")
    first = f"unless data block {BARREL} Items[{{Slot:0b}}]"
    if original_present:
        converted = legacy_slot(0, f"{current_tag(25)},{LEGACY_METADATA}")
        first = f"{converted} {first}.tag.Fluid"
    unknown = legacy_slot(1, f'Fluid:{amount(9, "example:removed_vapor")},PortLegacy:182')
    no_substitute = f'unless data block {BARREL} Items[{{Slot:1b}}].tag."enderio:data"."enderio:item_fluid_content"'
    server.assert_state("barrel_preserves_recoverable_and_unavailable_fuels",
                        f"{first} {unknown} {no_substitute}", results)


def trial(client, output, name):
    (output / f"server-{name}").touch()
    wait_signal(client, output, name)


def exercise(server, results, client, output, *, legacy):
    if legacy:
        server.command(f"item replace entity {PLAYER} hotbar.0 from block {BARREL} container.0")
        server.command(f"item replace block {BARREL} container.0 with minecraft:air")
        server.assert_state("survival_player_receives_the_original_igniter", held(25, legacy=True), results)
    else:
        server.command(f"give {PLAYER} enderio:dark_steel_ingot")
        server.command(f"give {PLAYER} minecraft:flint")
        server.assert_state("stock_igniter_recipe_unlocks_from_dark_steel",
                            f"if entity @a[name={PLAYER},advancements={{enderio:recipes/tools/cold_fire_igniter=true}}]", results)
    server.command(f"tp {PLAYER} 3190.5 80 11.5 180 4")
    if not legacy:
        (output / "server-craft").touch()
        wait_signal(client, output, "filled")
        server.assert_state("one_crafted_igniter_takes_all_25_mb_from_the_tank",
                            f"{held(25)} unless data block {TANK} Fluids.Tanks[{{Index:0}}].Contents.FluidName "
                            f'unless data entity {PLAYER} Inventory[{{id:"enderio:dark_steel_ingot"}}] '
                            f'unless data entity {PLAYER} Inventory[{{id:"minecraft:flint"}}]', results)
    trial(client, output, "unsupported")
    server.assert_state("unsupported_ignition_preserves_fuel", f"{held(25, legacy=legacy)} if block 3188 82 9 minecraft:air", results)
    trial(client, output, "occupied")
    server.assert_state("occupied_ignition_preserves_fuel_and_block", f"{held(25, legacy=legacy)} if block 3191 81 9 minecraft:glass", results)
    trial(client, output, "first")
    server.assert_state("first_success_consumes_exactly_ten_mb",
                        f"{held(15, legacy=legacy)} if block {FIRST_FIRE} enderio:cold_fire", results)
    server.assert_state("mature_fire_drops_one_grain_with_fire_tick_disabled",
                        f"if block {FIRST_FIRE} minecraft:air if block 3189 81 8 minecraft:oak_log if entity {grain(3190.5)}",
                        results, timeout=26)
    collect_grain(server, results, 3190.5, 1)
    server.command(f"tp {PLAYER} 3190.5 80 11.5 180 4")
    server.command("gamerule doFireTick true")


def reopened(server, results, client, output, *, legacy):
    server.assert_state("saved_igniter_keeps_fifteen_mb_after_restart",
                        f"{held(15, legacy=legacy)} if block {FIRST_FIRE} minecraft:air {collected(1)}", results)
    trial(client, output, "second")
    server.assert_state("second_success_leaves_five_mb", f"{held(5, legacy=legacy)} if block {SECOND_FIRE} enderio:cold_fire", results)
    mature_at = game_tick(server) + AGE + 1
    trial(client, output, "insufficient")
    server.assert_state("less_than_ten_mb_leaves_the_target_and_remainder_intact",
                        f"{held(5, legacy=legacy)} if block 3192 81 10 minecraft:air", results)
    wait_until_tick(server, mature_at, timeout=26)
    server.assert_state("cold_fire_remains_lit_and_does_not_burn_the_log",
                        f"if block {SECOND_FIRE} enderio:cold_fire if block 3193 81 8 minecraft:oak_log", results)
    (output / "server-extinguish").touch()
    wait_signal(client, output, "extinguished")
    server.assert_state("survival_extinguishing_crafts_the_second_grain",
                        f"if block {SECOND_FIRE} minecraft:air {held(5, legacy=legacy)} if entity {grain(3192.5)}", results)
    collect_grain(server, results, 3192.5, 2)


def run_client(args, server, results, report, *, restart):
    phase = "verify" if restart else "exercise"
    output = ROOT / "build/port-evidence" / f"{args.name}-{phase}-client"
    summary = output / "summary.json"
    legacy = args.legacy_jar is not None
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "legacy-cold-fire" if legacy else "cold-fire", "--multiplayer-phase", phase, "--output", str(output),
               "--server", f"127.0.0.1:{args.port}", "--timeout", "160"]
    with (server.directory / f"client-{phase}.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            scenario = reopened if restart else exercise
            scenario(server, results, client, output, legacy=legacy)
            (output / "server-done").touch()
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"Cold Fire client failed; see {output}")
            captures = json.loads(summary.read_text())["client"]["captures"]
            for capture in captures.values():
                if digest(Path(capture["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                    raise AssertionError("Cold Fire client and server Ender IO jars differ")
        finally:
            stop_client(client)
            report["clients"][phase] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    for key in ("forge-server", "jar"):
        parser.add_argument(f"--{key}", type=Path, required=True)
    parser.add_argument("--legacy-jar", type=Path)
    parser.add_argument("--launch", type=Path)
    args = parser.parse_args()
    if not args.legacy_jar and not args.launch:
        parser.error("a client launch profile is required for the modern survival route")
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "assertions": [], "servers": [], "clients": {},
              "jarSha256": digest(directory / "mods/enderio.jar")}

    def fresh(server, results):
        setup(server, results, legacy=bool(args.legacy_jar))
        if args.legacy_jar:
            verify_migrated_barrel(server, results, original_present=True)
        if args.launch:
            run_client(args, server, results, report, restart=False)

    def restart(server, results):
        if args.legacy_jar:
            verify_migrated_barrel(server, results, original_present=not args.launch)
        if args.launch:
            run_client(args, server, results, report, restart=True)

    try:
        if args.legacy_jar:
            legacy = directory / "legacy"
            prepare_profile(args, legacy, args.legacy_jar)
            report["legacyJarSha256"] = digest(legacy / "mods/enderio.jar")
            run_stage(args, legacy, report, "legacy", save_legacy_igniters)
            shutil.copytree(legacy / WORLD, directory / WORLD)
        add_recipe(directory)
        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", restart)
        report["passed"] = True
    finally:
        result = directory / "cold-fire-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Cold Fire passed={report['passed']}, {result}", flush=True)


if __name__ == "__main__":
    main()
