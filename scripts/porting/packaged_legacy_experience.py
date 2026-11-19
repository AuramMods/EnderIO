#!/usr/bin/env python3
"""Test migrated XP Boost equipment and the original Shimmer tooltip on packaged Forge."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage


PLAYER = "EnderIOQA"
BARREL = "3872 80 14"
SCULK = 3871
SILK = 3875
MELEE = 3879
ARROW_X = 3890
HOE = ("diamond_hoe", "xp_boost", 3, 12)
SWORD = ("iron_sword", "xp_boost", 1, 16)
BOW = ("bow", "xp_boost", 3, 18)
SHIMMER = ("iron_chestplate", "shimmer", 1, 7)
ARROW_AT_WALL = '@e[type=minecraft:arrow,x=3890.5,y=81,z=14.5,distance=..4,limit=1,sort=nearest]'
SAVED_ARROW = '@e[type=minecraft:arrow,tag=port_saved_boost_arrow,limit=1]'
ARROW_SNAPSHOT = '{ForgeData:{EnderIOXpBoostLevel:3},inGround:1b}'


def gear_tag(gear, wear=0, *, silk=False):
    item, enchantment, level, damage = gear
    enchants = [f'{{id:"enderio:{enchantment}",lvl:{level}s}}']
    if item == "diamond_hoe":
        enchants.append('{id:"minecraft:efficiency",lvl:5s}')
    if silk:
        enchants.append('{id:"minecraft:silk_touch",lvl:1s}')
    return f'{{Damage:{damage + wear},PortFixture:"{item}",Enchantments:[{",".join(enchants)}]}}'


def stack(slot, item, count, tag=None):
    metadata = f",tag:{tag}" if tag else ""
    return f'{{Slot:{slot}b,id:"minecraft:{item}",Count:{count}b{metadata}}}'


def stored(slot, gear, wear=0):
    return f"if data block {BARREL} Items[{stack(slot, gear[0], 1, gear_tag(gear, wear))}]"


def shimmer_equipped():
    return f"if data entity {PLAYER} Inventory[{stack(102, SHIMMER[0], 1, gear_tag(SHIMMER))}]"


def legacy_fixture(server, results):
    server.command("gamerule doMobSpawning false")
    server.command("gamerule doDaylightCycle false")
    server.command("gamerule spawnRadius 0")
    server.command("time set midnight")
    server.command("difficulty normal")
    server.command("setworldspawn 3872 80 13")
    server.command("forceload add 3856 0 3919 31")
    server.assert_state("forge6_mining_and_projectile_chunks_are_loaded", f"if loaded {SCULK} 80 8 if loaded {ARROW_X} 80 14", results)
    server.command("fill 3868 79 5 3893 79 23 minecraft:stone")
    server.command(f"setblock {BARREL} minecraft:barrel")
    server.command(f"setblock {SCULK} 80 8 minecraft:sculk")
    server.command(f"setblock {SILK} 80 8 minecraft:sculk")
    server.command("fill 3889 80 14 3891 83 14 minecraft:glass")
    for slot, gear in enumerate((HOE, SWORD, BOW, SHIMMER)):
        server.command(f"item replace block {BARREL} container.{slot} with minecraft:{gear[0]}{gear_tag(gear)}")
    server.assert_state("forge6_saves_all_four_original_gear_tags",
                        f"{stored(0, HOE)} {stored(1, SWORD)} {stored(2, BOW)} {stored(3, SHIMMER)}", results)


def take_gear(server, slot, destination):
    server.command(f"item replace entity {PLAYER} {destination} from block {BARREL} container.{slot}")
    server.command(f"item replace block {BARREL} container.{slot} with minecraft:air")


def stow_tool(server, slot):
    server.command(f"item replace block {BARREL} container.{slot} from entity {PLAYER} hotbar.0")
    server.command(f"item replace entity {PLAYER} hotbar.0 with minecraft:air")


def xp_total(amount):
    return f"if data entity {PLAYER} {{XpTotal:{amount}}}"


def collect_xp(server, results, x, amount, name, extra=""):
    server.command(f"tp {PLAYER} {x + 0.5} 80 8.5")
    no_orbs = f"unless entity @e[type=minecraft:experience_orb,x={x + 0.5},y=80,z=8.5,distance=..5]"
    try:
        server.assert_state(name, f"{xp_total(amount)} {no_orbs} {extra}".rstrip(), results, timeout=12)
    finally:
        server.command(f"data get entity {PLAYER} XpTotal")


def mine_sculk(server, results, client, output):
    take_gear(server, 0, "hotbar.0")
    server.command(f"tp {PLAYER} {SCULK + 0.5} 80 10.5 180 25")
    (output / "server-mine-boosted").touch()
    wait_signal(client, output, "mined-boosted")
    server.assert_state("native_survival_break_harvests_the_sculk", f"if block {SCULK} 80 8 minecraft:air", results)
    collect_xp(server, results, SCULK, 2, "rank_three_boosts_one_native_block_xp_to_two")
    stow_tool(server, 0)

    server.command(f"item replace entity {PLAYER} hotbar.0 with minecraft:diamond_hoe{gear_tag(HOE, silk=True)}")
    server.command(f"tp {PLAYER} {SILK + 0.5} 80 10.5 180 25")
    (output / "server-mine-silk").touch()
    wait_signal(client, output, "mined-silk")
    collect_xp(server, results, SILK, 2, "silk_touch_preserves_the_block_without_xp",
               f'if block {SILK} 80 8 minecraft:air if data entity {PLAYER} Inventory[{{id:"minecraft:sculk",Count:1b}}]')


def spawn_zombie(server, x, tag, name):
    server.command(f'summon minecraft:zombie {x + 0.5} 80 8.5 {{Tags:["{tag}"],CustomName:\'{{"text":"{name}"}}\','
                   'IsBaby:0b,NoAI:1b,Health:1.0f,PersistenceRequired:1b,CanPickUpLoot:0b,HandItems:[{},{}],ArmorItems:[{},{},{},{}]}')


def defeated(tag):
    return f'unless entity @e[type=minecraft:zombie,tag={tag},nbt={{Health:1.0f}}]'


def melee(server, results, client, output):
    take_gear(server, 1, "hotbar.0")
    spawn_zombie(server, MELEE, "xp_melee", "XP melee target")
    server.command(f"tp {PLAYER} {MELEE + 0.5} 80 10.5 180 15")
    (output / "server-melee").touch()
    wait_signal(client, output, "melee-hit")
    collect_xp(server, results, MELEE, 10, "rank_one_boosts_five_mob_xp_to_eight", defeated("xp_melee"))
    stow_tool(server, 1)


def launch_arrow(server, results, client, output):
    take_gear(server, 2, "hotbar.0")
    server.command(f"item replace entity {PLAYER} hotbar.8 with minecraft:arrow")
    server.command(f"tp {PLAYER} {ARROW_X + 0.5} 80 21.5 180 0")
    (output / "server-draw").touch()
    wait_signal(client, output, "released")
    server.assert_state("native_shot_snapshots_rank_three_before_it_hits_the_wall",
                        f"if data entity {ARROW_AT_WALL} {ARROW_SNAPSHOT} "
                        f'unless data entity {PLAYER} Inventory[{{id:"minecraft:arrow"}}]', results)
    server.command(f"tag {ARROW_AT_WALL} add port_saved_boost_arrow")
    stow_tool(server, 2)
    server.command(f"item replace entity {PLAYER} hotbar.0 with minecraft:stone_sword")
    server.assert_state("arrow_and_its_original_bow_can_save_separately",
                        f"{stored(2, BOW, 1)} {shimmer_equipped()} if data entity {SAVED_ARROW} {ARROW_SNAPSHOT}", results)


def release_saved_arrow(server, results):
    server.assert_state("restart_preserves_the_launch_rank_and_migrated_equipment",
                        f"if data entity {SAVED_ARROW} {ARROW_SNAPSHOT} {xp_total(10)} {shimmer_equipped()} "
                        f"{stored(0, HOE, 1)} {stored(1, SWORD, 1)} {stored(2, BOW, 1)} "
                        f'if data entity {PLAYER} Inventory[{stack(0, "stone_sword", 1)}]', results)
    spawn_zombie(server, ARROW_X, "xp_arrow", "XP arrow target")
    server.command(f"data merge entity {SAVED_ARROW} {{Pos:[3890.5d,80.85d,12.5d],Motion:[0.0d,0.0d,-2.0d],"
                   "inGround:0b,NoGravity:1b,damage:2.0d}")
    server.assert_state("the_saved_arrow_hits_its_target", defeated("xp_arrow"), results)
    collect_xp(server, results, ARROW_X, 21, "saved_arrow_boosts_five_mob_xp_to_eleven_with_an_ordinary_sword_held")


def exercise(args, server, results, report, *, reopened):
    phase = "verify" if reopened else "exercise"
    output = ROOT / "build/port-evidence" / f"{args.name}-{phase}-client"
    summary = output / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "legacy-experience", "--multiplayer-phase", phase, "--output", str(output),
               "--server", f"127.0.0.1:{args.port}", "--timeout", "160"]
    with (server.directory / f"client-{phase}.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            if reopened:
                release_saved_arrow(server, results)
            else:
                take_gear(server, 3, "armor.chest")
                server.assert_state("original_shimmer_chestplate_equips_with_saved_damage", shimmer_equipped(), results)
                mine_sculk(server, results, client, output)
                melee(server, results, client, output)
                launch_arrow(server, results, client, output)
            (output / "server-verified").touch()
            if client.wait(timeout=35) != 0:
                raise AssertionError(f"Legacy experience client failed, see {summary}")
            capture = json.loads(summary.read_text())["client"]["captures"]["shimmer-inventory"]
            if digest(Path(capture["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Client and server Ender IO jars differ")
        finally:
            stop_client(client)
            report["clients"][phase] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--legacy-jar", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--launch", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")

    directory = ROOT / "build/port-runtime" / args.name
    directory.mkdir()
    legacy = directory / "legacy"
    port = directory / "port"
    prepare_profile(args, legacy, args.legacy_jar)
    prepare_profile(args, port, args.jar)
    report = {"passed": False, "forge": FORGE, "jarSha256": digest(port / "mods/enderio.jar"),
              "legacyJarSha256": digest(legacy / "mods/enderio.jar"), "assertions": [], "servers": [], "clients": {}}
    try:
        def converted(server, results):
            server.assert_state("all_four_historical_stacks_survive_conversion",
                                f"{stored(0, HOE)} {stored(1, SWORD)} {stored(2, BOW)} {stored(3, SHIMMER)}", results)
            exercise(args, server, results, report, reopened=False)

        run_stage(args, legacy, report, "legacy", legacy_fixture)
        shutil.copytree(legacy / WORLD, port / WORLD)
        run_stage(args, port, report, "converted", converted)
        run_stage(args, port, report, "reopened", lambda server, results: exercise(args, server, results, report, reopened=True))
        if any(stage["registryErrors"] for stage in report["servers"]):
            raise AssertionError("The Forge 6 conversion reported registry errors")
        report["passed"] = True
    finally:
        path = directory / "legacy-experience-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Legacy experience migration passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
