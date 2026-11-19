#!/usr/bin/env python3
"""Verify painted redstone recipes, survival placement and powered state on Forge."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_painting_machine import CAPACITOR, COST, PLAYER, inventory, paint_tag, palette
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage


SAMPLES = (
    (2200, "redstone_block", "redstone_block", "diamond_block"),
    (2207, "wooden_pressure_plate", "birch_pressure_plate", "gold_block"),
    (2214, "fence_gate", "spruce_fence_gate", "emerald_block"),
    (2221, "trapdoor", "acacia_trapdoor", "lapis_block"),
)
BARREL = "2200 80 13"
TRIGGER = "@e[type=item,tag=painted_plate_trigger]"


def item(kind, paint):
    return f'id:"enderio:painted_{kind}",{paint_tag(paint)}'


def block(sample, state="", z=8):
    x, kind, _, paint = sample
    pos = f"{x} 80 {z}"
    return f'if block {pos} enderio:painted_{kind}{state} if data block {pos} {{Paint:"minecraft:{paint}"}}'


def lamp(sample, lit, z=8):
    return f"if block {sample[0] + 1} 80 {z} minecraft:redstone_lamp[lit={lit}]"


def load(server, results, name):
    server.command("forceload add 2192 0 2239 15")
    server.assert_state(name, "if loaded 2200 80 8 if loaded 2221 80 8", results)


def build_scene(server):
    server.command("gamerule doMobSpawning false")
    server.command("gamerule doDaylightCycle false")
    server.command("gamerule spawnRadius 0")
    server.command("time set noon")
    server.command("fill 2197 79 2 2225 79 14 minecraft:stone_bricks")
    server.command("setworldspawn 2200 80 12")
    server.command(f"setblock {BARREL} minecraft:barrel")


def place_receivers(server, z=8):
    for sample in SAMPLES[:2]:
        server.command(f"setblock {sample[0] + 1} 80 {z} minecraft:redstone_lamp")
    server.command(f"setblock 2199 80 {z} minecraft:redstone_wire")
    for x in (2213, 2215):
        server.command(f"setblock {x} 80 {z} minecraft:cobblestone_wall")


def setup(server, results):
    load(server, results, "fixture_chunks_loaded")
    build_scene(server)
    for x, kind, template, paint in SAMPLES:
        machine = f"{x} 80 3"
        hopper = f"{x} 79 3"
        server.command(f"setblock {machine} enderio:painting_machine[facing=south]")
        inputs = [CAPACITOR, f'{{Slot:1,id:"minecraft:{template}",Count:1}}', palette(paint)]
        server.command(f'data merge block {machine} {{EnergyStored:{COST},Items:{{Items:[{",".join(inputs)}]}}}}')
        output = item(kind, paint)
        server.assert_state(f"native_recipe_paints_{kind}",
                            f"{inventory(machine, [palette(paint), f'{{Slot:3,Count:1,{output}}}'], 0)} "
                            f"unless data block {machine} Items.Items[{{Slot:1}}]", results, timeout=20)
        server.command(f"setblock {hopper} minecraft:hopper[facing=down]")
        server.assert_state(f"native_hopper_extracts_only_the_painted_{kind}",
                            f'if data block {hopper} Items[{{Slot:0b,Count:1b,{output}}}] '
                            f"{inventory(machine, [palette(paint)], 0)} unless data block {machine} Items.Items[{{Slot:3}}]", results)
    place_receivers(server)


def placed_state(sample, open_=False, powered=False, waterlogged=False, z=8):
    _, kind, _, _ = sample
    if kind == "redstone_block":
        return f"{block(sample, z=z)} {lamp(sample, 'true', z)} if block 2199 80 {z} minecraft:redstone_wire[power=15]"
    if kind == "wooden_pressure_plate":
        lit = str(powered).lower()
        return f"{block(sample, f'[powered={lit}]', z)} {lamp(sample, lit, z)}"
    properties = [f"open={str(open_).lower()}", f"powered={str(powered).lower()}"]
    properties.append("in_wall=true" if kind == "fence_gate" else f"waterlogged={str(waterlogged).lower()}")
    return block(sample, f"[{','.join(properties)}]", z)


def loot_state():
    return " ".join(f'if data block {BARREL} Items[{{Slot:{i}b,Count:1b,{item(kind, paint)}}}]'
                    for i, (_, kind, _, paint) in enumerate(SAMPLES))


def exercise(args, server, report):
    setup(server, report["assertions"])
    exercise_client(args, server, report)


def exercise_client(args, server, report, legacy_loot=None):
    results = report["assertions"]
    wet = legacy_loot is not None
    output = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "painted-redstone", "--output", str(output), "--server", f"127.0.0.1:{args.port}",
               "--multiplayer-phase", "verify" if wet else "exercise", "--timeout", "185"]
    with (server.directory / "painted-redstone-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            for slot, sample in enumerate(SAMPLES):
                x, kind, _, _ = sample
                source = f"{legacy_loot} container.{slot}" if wet else f"{x} 79 3 container.0"
                server.command(f"item replace entity {PLAYER} hotbar.{slot} from block {source}")
                server.command(f"item replace block {source} with minecraft:air")
                server.command(f"tp {PLAYER} {x + 0.5} 80 11.5 180 {31 if slot == 1 else 24}")
                (output / f"server-place-{slot}").touch()
                wait_signal(client, output, f"placed-{slot}")
                server.assert_state(f"survival_{kind}_places_with_expected_paint_and_state",
                                    f"{placed_state(sample, waterlogged=wet)} "
                                    f"unless data entity {PLAYER} Inventory[{{Slot:{slot}b}}]", results)
                (output / f"server-view-{slot}").touch()
                wait_signal(client, output, f"viewed-{slot}")
                if slot == 1:
                    server.command(f'summon minecraft:item {x + 0.5} 80 8.5 {{Tags:["painted_plate_trigger"],NoGravity:1b,'
                                   'PickupDelay:32767s,Item:{id:"minecraft:cobblestone",Count:1b}}')
                    server.assert_state("dropped_item_presses_the_wooden_plate_and_lights_its_lamp",
                                        placed_state(sample, powered=True), results)
                    (output / f"server-active-{slot}").touch()
                    wait_signal(client, output, f"active-{slot}")
                if slot >= 2:
                    (output / f"server-active-{slot}").touch()
                    wait_signal(client, output, f"active-{slot}")
                    server.assert_state(f"hand_use_opens_unpowered_{kind}", placed_state(sample, open_=True, waterlogged=wet), results)
                    (output / f"server-close-{slot}").touch()
                    wait_signal(client, output, f"closed-{slot}")
                    server.assert_state(f"second_hand_use_closes_{kind}", placed_state(sample, waterlogged=wet), results)
                    server.command(f"setblock {x} 80 7 minecraft:redstone_block")
                    server.assert_state(f"redstone_power_opens_{kind}",
                                        placed_state(sample, open_=True, powered=True, waterlogged=wet), results)
                tool = "pickaxe" if slot == 0 else "axe"
                server.command(f"loot replace block {BARREL} container.{slot} mine {x} 80 8 minecraft:diamond_{tool}")
            server.assert_state("native_loot_preserves_all_four_paint_materials", loot_state(), results)
            (output / "server-finish").touch()
            if client.wait(timeout=25) != 0:
                raise AssertionError(f"Painted redstone client failed; see {output}")
            summary = json.loads((output / "summary.json").read_text())
            frame = summary["client"]["captures"]["trapdoor-powered"]
            if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Client and server loaded different Ender IO jars")
        finally:
            stop_client(client)
            summary = output / "summary.json"
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def reopen(server, results, *, wet=False):
    load(server, results, "painted_redstone_chunks_loaded_after_restart")
    active = [placed_state(sample, open_=slot >= 2, powered=slot >= 1, waterlogged=wet) for slot, sample in enumerate(SAMPLES)]
    server.assert_state("all_paints_outputs_and_powered_states_survive_restart", f'{" ".join(active)} {loot_state()}', results)
    server.command(f"kill {TRIGGER}")
    for x, _, _, _ in SAMPLES[2:]:
        replacement = "minecraft:stone_bricks" if wet and x == SAMPLES[-1][0] else "minecraft:air"
        server.command(f"setblock {x} 80 7 {replacement}")
    for sample in SAMPLES[1:]:
        server.assert_state(f"releasing_power_restores_the_original_{sample[1]}_state",
                            placed_state(sample, waterlogged=wet), results)
    server.command("setblock 2200 80 8 minecraft:air")
    server.assert_state("removing_the_painted_redstone_block_clears_its_full_output",
                        f"{lamp(SAMPLES[0], 'false')} if block 2199 80 8 minecraft:redstone_wire[power=0]", results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for path in ("java", "forge-server", "jar", "launch"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a single directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(args.jar)}
    try:
        run_stage(args, directory, report, "exercise", lambda server, results: exercise(args, server, report))
        run_stage(args, directory, report, "reopened", reopen)
        report["passed"] = True
    finally:
        (directory / "painted-redstone-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
