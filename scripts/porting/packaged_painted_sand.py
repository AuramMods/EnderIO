#!/usr/bin/env python3
"""Exercise painting, falling, landing and native loot in packaged Forge."""

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


MACHINE = "1804 80 4"
BARREL = "1804 80 10"
LAND = 1804
DROP = 1810
PAINT = '{Paint:"minecraft:diamond_block"}'
PAINTED_ITEM = f'id:"enderio:painted_sand",{paint_tag("diamond_block")}'
FALLING_NBT = f'BlockState:{{Name:"enderio:painted_sand"}},TileEntityData:{PAINT}'


def falling(x, nbt=""):
    return (f"@e[type=enderio:painted_sand,x={x + 0.5},y=88,z=8.5,distance=..4,"
            f"sort=nearest,limit=1{nbt}]")


def painted_fall(x, frozen=False):
    flag = ",NoGravity:1b" if frozen else ""
    return falling(x, f",nbt={{{FALLING_NBT}{flag}}}")


def dropped():
    return f"@e[type=item,x={DROP + 0.5},y=80.5,z=8.5,distance=..2,nbt={{Item:{{{PAINTED_ITEM},Count:1b}}}}]"


def load(server, results, label):
    server.command("forceload add 1792 0 1823 15")
    server.assert_state(label, f"if loaded {MACHINE} if loaded {DROP} 88 8", results)


def finished_machine():
    return (f"{inventory(MACHINE, [palette('diamond_block')], 0)} "
            f"unless data block {MACHINE} Items.Items[{{Slot:1}}] unless data block {MACHINE} Items.Items[{{Slot:3}}]")


def setup(server, results):
    load(server, results, "both_falling_sand_chunks_are_loaded")
    server.command("gamerule doMobSpawning false")
    server.command("gamerule doDaylightCycle false")
    server.command("gamerule spawnRadius 0")
    server.command("time set noon")
    server.command("fill 1800 79 3 1813 79 15 minecraft:stone_bricks")
    server.command("fill 1801 87 10 1812 87 13 minecraft:stone_bricks")
    server.command("fill 1801 83 6 1812 91 6 minecraft:stone_bricks")
    server.command("setworldspawn 1804 80 6")
    for x in (LAND, DROP):
        server.command(f"setblock {x} 87 8 minecraft:stone_bricks")
    server.command(f"setblock {DROP} 80 8 minecraft:torch")
    server.command(f"setblock {MACHINE} enderio:painting_machine[facing=south]")
    inputs = [CAPACITOR, '{Slot:1,id:"minecraft:sand",Count:2}', palette("diamond_block")]
    server.command(f'data merge block {MACHINE} {{EnergyStored:{COST * 2},Items:{{Items:[{",".join(inputs)}]}}}}')
    server.assert_state("machine_paints_two_sand_templates_for_exact_charge",
                        f"{inventory(MACHINE, [palette('diamond_block'), f'{{Slot:3,Count:2,{PAINTED_ITEM}}}'], 0)} "
                        f"unless data block {MACHINE} Items.Items[{{Slot:1}}]", results, timeout=25)


def freeze(server, results, x):
    server.command(f"setblock {x} 87 8 minecraft:air")
    server.assert_state(f"sand_at_{x}_falls_with_its_original_state_and_paint",
                        f"if block {x} 88 8 minecraft:air if entity {painted_fall(x)}", results)
    # Pause an already spawned entity long enough for a deterministic frame and save.
    server.command(f"data merge entity {falling(x)} {{NoGravity:1b,Motion:[0.0d,0.0d,0.0d]}}")
    server.assert_state(f"sand_at_{x}_is_saved_as_a_falling_entity", f"if entity {painted_fall(x, frozen=True)}", results)


def exercise(args, server, report):
    results = report["assertions"]
    setup(server, results)
    output = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
               "--launch", str(args.launch.resolve()), "--scenario", "painted-sand", "--output", str(output),
               "--server", f"127.0.0.1:{args.port}", "--timeout", "165"]
    with (server.directory / "painted-sand-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            server.command(f"tp {PLAYER} 1804.5 80 6.5 180 18")
            (output / "server-menu").touch()
            wait_signal(client, output, "taken")
            server.assert_state("survival_menu_transfers_the_exact_two_painted_items",
                                f"{finished_machine()} if data entity {PLAYER} Inventory[{{{PAINTED_ITEM},Count:2b}}]", results)
            for x, step, remainder in ((LAND, "first", 1), (DROP, "second", 0)):
                server.command(f"tp {PLAYER} {x + 0.5} 88 11.5 180 26")
                (output / f"server-{step}").touch()
                wait_signal(client, output, f"placed-{step}")
                carried = (f'if data entity {PLAYER} Inventory[{{{PAINTED_ITEM},Count:1b}}]' if remainder
                           else f'unless data entity {PLAYER} Inventory[{{id:"enderio:painted_sand"}}]')
                server.assert_state(f"{step}_survival_placement_preserves_the_original_paint",
                                    f"if data block {x} 88 8 {PAINT} {carried}", results)
            server.command(f"tp {PLAYER} {LAND + 0.5} 88 11.5 180 34")
            freeze(server, results, LAND)
            freeze(server, results, DROP)
            (output / "server-airborne").touch()
            wait_signal(client, output, "rendered")
            if client.wait(timeout=25) != 0:
                raise AssertionError(f"Painted sand client failed; see {output}")
            summary = json.loads((output / "summary.json").read_text())
            frame = summary["client"]["captures"]["painted-sand-airborne"]
            if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Client and server loaded different Ender IO jars")
        finally:
            stop_client(client)
            summary = output / "summary.json"
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def landed():
    return (f"if block {LAND} 80 8 enderio:painted_sand if data block {LAND} 80 8 {PAINT} "
            f"if block {DROP} 80 8 minecraft:torch if entity {dropped()} "
            f"unless entity {falling(LAND)} unless entity {falling(DROP)}")


def finish_fall(server, results):
    load(server, results, "both_falling_sand_chunks_are_loaded_after_restart")
    for x in (LAND, DROP):
        server.assert_state(f"restart_restores_airborne_paint_at_{x}",
                            f"if block {x} 88 8 minecraft:air if entity {painted_fall(x, frozen=True)}", results)
        server.command(f"data merge entity {falling(x)} {{NoGravity:0b}}")
    server.assert_state("resumed_sand_lands_or_drops_with_the_original_paint", landed(), results)
    server.command(f"setblock {BARREL} minecraft:barrel")
    server.command(f"loot replace block {BARREL} container.0 mine {LAND} 80 8 minecraft:diamond_shovel")
    server.assert_state("native_block_loot_preserves_the_landings_paint",
                        f"if data block {BARREL} Items[{{Slot:0b,Count:1b,{PAINTED_ITEM}}}]", results)


def reopen(server, results):
    load(server, results, "both_landing_chunks_are_loaded")
    server.assert_state("landed_block_both_item_results_and_machine_balance_survive_restart",
                        f"{landed()} {finished_machine()} if data block {BARREL} Items[{{Slot:0b,Count:1b,{PAINTED_ITEM}}}]", results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for path in ("java", "forge-server", "jar", "launch"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(args.jar)}
    try:
        run_stage(args, directory, report, "exercise", lambda server, results: exercise(args, server, report))
        run_stage(args, directory, report, "finish-fall", finish_fall)
        run_stage(args, directory, report, "reopened", reopen)
        report["passed"] = True
    finally:
        (directory / "painted-sand-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
