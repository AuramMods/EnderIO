#!/usr/bin/env python3
"""Smelt stock glass, bind collision tokens and craft their variants over TCP."""

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_server_smoke import ROOT, digest
from packaged_soul_binder import item as binder_item, xp_tank
from packaged_spawners import prepare_profile, run_stage
from packaged_starter_progression import PLAYER, hopper_item

CHEST = "2840 80 8"
PLACED = (
    ("2839 80 9", "clear_glass_dnp_cyan"),
    ("2841 80 8", "fused_quartz_ena_yellow"),
    ("2843 80 9", "fused_quartz_dnm_red"),
)
TOKENS = (
    (2820, "player_token", "villager", "dark_steel_grinding_ball"),
    (2824, "animal_token", "cow", "soularium_grinding_ball"),
    (2828, "monster_token", "zombie", "soularium_grinding_ball"),
)


@dataclass(frozen=True)
class Smelt:
    x: int
    z: int
    output: str
    count: int
    cost: int

    def pos(self, height):
        return f"{self.x} {height} {self.z}"


PLAIN_GLASS = Smelt(2820, 3, "clear_glass", 8, 3200)
PLAIN_QUARTZ = Smelt(2824, 3, "fused_quartz", 8, 6400)
SMOOTH_QUARTZ = Smelt(2832, 3, "smooth_quartz", 1, 1500)
DARK_GLASS = Smelt(2820, 7, "clear_glass_d", 8, 1600)
GLOWING_QUARTZ = Smelt(2824, 7, "fused_quartz_e", 8, 3200)
DARK_QUARTZ = Smelt(2828, 7, "fused_quartz_d", 8, 6400)
GLOWING_GLASS = Smelt(2832, 7, "clear_glass_e", 1, 3200)


def load(server, results):
    server.command("forceload add 2816 0 2847 15")
    server.assert_state("glass_shop_chunks_are_loaded", f"if loaded {PLAIN_GLASS.pos(80)} if loaded {CHEST}", results)


def prepare_smelter(server, job, *, mode=2):
    server.command(f'setblock {job.pos(80)} enderio:alloy_smelter[facing=south]'
                   f'{{Mode:{mode},' 'IOConfig:{Modes:{up:"pull",down:"push"}},Items:{Size:5,'
                   'Items:[{Slot:4,id:"enderio:octadic_capacitor",Count:1}]}}')
    server.command(f"setblock {job.pos(81)} minecraft:hopper[facing=down]")
    server.command(f"setblock {job.pos(79)} minecraft:hopper[facing=down]")


def supply(server, job, slot, item, count):
    server.command(f"item replace block {job.pos(81)} container.{slot} with {item} {count}")


def transfer(server, source, source_slot, target, target_slot):
    server.command(f"item replace block {target} container.{target_slot} from block {source} container.{source_slot}")
    server.command(f"item replace block {source} container.{source_slot} with minecraft:air")


def start_smelter(server, job):
    server.command(f"data merge block {job.pos(80)} {{EnergyStored:{job.cost * job.count + 137}}}")


def finished_smelter(job, *, namespace="enderio"):
    pos = job.pos(80)
    inputs_empty = " ".join(f"unless data block {pos} Items.Items[{{Slot:{i}}}]" for i in range(4))
    return (f'if data block {pos} {{EnergyStored:137}} unless data block {pos} Task {inputs_empty} '
            f"unless data block {job.pos(81)} Items[0] {hopper_item(job.pos(79), namespace + ':' + job.output, job.count)}")


def token_pos(x, height):
    return f"{x} {height} 12"


def start_binder(server, x, soul, ball):
    soul_tag = f'tag:{{"enderio:data":{{"enderio:soul":{{entity_type:"minecraft:{soul}",entity_tag:{{}}}}}}}}'
    inputs = ",".join((binder_item(0, "enderio:soul_vial", soul_tag), binder_item(1, "enderio:" + ball),
                       binder_item(4, "enderio:octadic_capacitor")))
    server.command(f'setblock {token_pos(x, 80)} enderio:soul_binder[facing=south]'
                   f'{{EnergyStored:12937,{xp_tank(177)},Items:{{Size:5,Items:[{inputs}]}}}}')


def bound_token(x, token):
    pos = token_pos(x, 80)
    return (f'if data block {pos} {{EnergyStored:137,{xp_tank(37)},Items:{{Items:['
            f'{binder_item(2, "enderio:" + token)},{binder_item(3, "enderio:soul_vial")}]}}}} '
            f"unless data block {pos} Task unless data block {pos} Items.Items[{{Slot:0}}] "
            f'unless data block {pos} Items.Items[{{Slot:1}}] unless data block {pos} Items.Items[{{Slot:3}}].tag')


def make_materials(server, results):
    load(server, results)
    for command in ("gamerule doMobSpawning false", "gamerule spawnRadius 0", "gamerule doLimitedCrafting true",
                    "gamerule doDaylightCycle false", "time set noon", "weather clear 100000",
                    "fill 2818 78 2 2845 78 13 minecraft:stone", "fill 2838 79 5 2845 79 13 minecraft:stone",
                    "setworldspawn 2841 80 11", f"setblock {CHEST} minecraft:chest[facing=south]",
                    "setblock 2842 80 8 minecraft:crafting_table"):
        server.command(command)
    for job in (PLAIN_GLASS, PLAIN_QUARTZ, DARK_GLASS, GLOWING_QUARTZ, DARK_QUARTZ, GLOWING_GLASS):
        prepare_smelter(server, job)
    prepare_smelter(server, SMOOTH_QUARTZ, mode=0)
    supply(server, SMOOTH_QUARTZ, 0, "minecraft:quartz_block", 1)
    supply(server, PLAIN_GLASS, 0, "minecraft:glass", 8)
    supply(server, PLAIN_QUARTZ, 0, "minecraft:quartz_block", 8)
    supply(server, DARK_QUARTZ, 0, "minecraft:quartz", 32)
    supply(server, DARK_QUARTZ, 1, "minecraft:amethyst_block", 8)
    supply(server, GLOWING_GLASS, 0, "minecraft:glass", 1)
    supply(server, GLOWING_GLASS, 1, "minecraft:glowstone_dust", 4)
    initial = (PLAIN_GLASS, PLAIN_QUARTZ, DARK_QUARTZ, GLOWING_GLASS)
    server.assert_state("native_hoppers_insert_both_ingredients_before_the_smelters_receive_power",
                        " ".join(f"unless data block {job.pos(81)} Items[0]" for job in initial), results, timeout=25)
    for job in initial:
        start_smelter(server, job)
    start_smelter(server, SMOOTH_QUARTZ)
    for x, _, soul, ball in TOKENS:
        start_binder(server, x, soul, ball)
    server.assert_state("furnace_mode_uses_the_inherited_recipe_for_the_same_quartz_input",
                        finished_smelter(SMOOTH_QUARTZ, namespace="minecraft"), results)
    for job in initial:
        server.assert_state(job.output + "_native_smelting_consumes_the_stock_inputs_and_energy",
                            finished_smelter(job), results, timeout=95)
    for source, target, reagent, count in (
        (PLAIN_GLASS, DARK_GLASS, "minecraft:amethyst_shard", 32),
        (PLAIN_QUARTZ, GLOWING_QUARTZ, "minecraft:glowstone", 8),
    ):
        transfer(server, source.pos(79), 0, target.pos(81), 0)
        supply(server, target, 1, reagent, count)
        start_smelter(server, target)
    for job in (DARK_GLASS, GLOWING_QUARTZ):
        server.assert_state(job.output + "_upgrade_consumes_the_real_intermediate_and_energy",
                            finished_smelter(job), results, timeout=75)
    for slot, job in enumerate((DARK_GLASS, GLOWING_QUARTZ, DARK_QUARTZ)):
        transfer(server, job.pos(79), 0, CHEST, slot)
    for slot, (x, token, _, _) in enumerate(TOKENS, 3):
        server.assert_state(token + "_binds_the_stock_soul_and_returns_one_empty_vial",
                            bound_token(x, token), results)
        hopper = token_pos(x, 79)
        server.command(f"setblock {hopper} minecraft:hopper[facing=down]")
        server.assert_state(token + "_extracts_into_a_native_hopper",
                            hopper_item(hopper, "enderio:" + token, 1), results)
        transfer(server, hopper, 0, CHEST, slot)
    for slot, (item, count) in enumerate((("redstone_torch", 3), ("blue_dye", 1), ("cyan_dye", 1),
                                         ("yellow_dye", 1), ("red_dye", 1)), 6):
        server.command(f"item replace block {CHEST} container.{slot} with minecraft:{item} {count}")


def placed_glass():
    return " ".join(f"if block {pos} enderio:{block}" for pos, block in PLACED)


def exercise_client(args, server, results, report):
    output = ROOT / "build/port-evidence" / (args.name + "-client")
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "glass-progression", "--output", str(output), "--server", f"127.0.0.1:{args.port}",
               "--timeout", "175"]
    with (server.directory / "glass-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            server.command(f"tp {PLAYER} 2841.5 80 11.5 180 20")
            (output / "server-ready").touch()
            wait_signal(client, output, "placed")
            remaining = " ".join(f'if data entity {PLAYER} Inventory[{{id:"enderio:{block}",Count:7b}}]'
                                 for _, block in PLACED)
            server.assert_state("survival_placement_consumes_one_of_each_crafted_stack",
                                f"{placed_glass()} {remaining} unless data block {CHEST} Items[0]", results)
            (output / "server-done").touch()
            if client.wait(timeout=35) != 0:
                raise AssertionError(f"Glass progression client failed; see {output}")
            summary = json.loads((output / "summary.json").read_text())
            loaded = summary["client"]["captures"]["placed-glass"]["loadedMods"]["enderio"]["file"]
            if digest(Path(loaded)) != report["jarSha256"]:
                raise AssertionError("Client and server loaded different Ender IO jars")
        finally:
            stop_client(client)
            summary = output / "summary.json"
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def reopen(server, results):
    load(server, results)
    server.assert_state("the_crafted_blocks_keep_their_exact_variants_after_restart", placed_glass(), results)
    server.assert_state("the_direct_enlightened_glass_output_survives_restart", finished_smelter(GLOWING_GLASS), results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", type=int, default=26121)
    parser.add_argument("--java", required=True)
    for key in ("forge-server", "jar", "launch"):
        parser.add_argument(f"--{key}", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}
    try:
        def fresh(server, results):
            make_materials(server, results)
            exercise_client(args, server, results, report)

        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", reopen)
        report["passed"] = True
    finally:
        result = directory / "glass-progression-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Glass progression passed={report['passed']}, {len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
