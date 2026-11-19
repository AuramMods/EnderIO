#!/usr/bin/env python3
"""Verify migrated Withering weapons and Repellent armor on packaged Forge."""

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
BARREL = "3809 80 14"
MELEE = "3810.5 80 8.5"
OFFHAND = "3814.5 80 8.5"
BOW_TARGET = "3823.5 80 8.5"
ZOMBIE = "3804.5 80 8.75"
ZOMBIE_AT = "x=3804.5,y=80,z=8.75"
ARMOR = ("iron_chestplate", "repellent", 4)
SWORD = ("iron_sword", "withering", 1)
BOW = ("bow", "withering", 1)
WITHER = 'ActiveEffects[{"forge:id":"minecraft:wither",Amplifier:0b}]'
CONFIG = "[enchantments.repellent]\nchanceBase=0.0\nchancePerLevel=0.25\nrangeBase=0.0\nrangePerLevel=10.0\nnonPlayerChance=1.0\n"


def gear_tag(gear, damage=None):
    item, enchantment, level = gear
    wear = f"Damage:{damage}," if damage is not None else ""
    return f'{{{wear}PortFixture:"{item}",Enchantments:[{{id:"enderio:{enchantment}",lvl:{level}s}}]}}'


def stack(slot, gear, damage=None):
    return f'{{Slot:{slot}b,id:"minecraft:{gear[0]}",Count:1b,tag:{gear_tag(gear, damage)}}}'


def stored(slot, gear, damage=None):
    return f"if data block {BARREL} Items[{stack(slot, gear, damage)}]"


def equipped(slot, gear, damage=None):
    return f"if data entity {PLAYER} Inventory[{stack(slot, gear, damage)}]"


def target(tag, *, entity="cow", where=""):
    restriction = f",{where}" if where else ""
    return f"@e[type=minecraft:{entity},tag={tag}{restriction},limit=1]"


def legacy_fixture(server, results):
    server.command("gamerule doMobSpawning false")
    server.command("gamerule doDaylightCycle false")
    server.command("gamerule spawnRadius 0")
    server.command("difficulty normal")
    server.command("time set midnight")
    server.command("setworldspawn 3810 80 13")
    server.command("forceload add 3792 0 3855 63")
    server.assert_state("forge6_arena_and_equipment_chunks_are_loaded", f"if loaded {BARREL} if loaded 3845 80 48", results)
    server.command("fill 3792 79 0 3855 79 63 minecraft:stone")
    server.command(f"setblock {BARREL} minecraft:barrel")
    server.command("scoreboard objectives add port_combat dummy")
    for slot, (gear, damage) in enumerate(((SWORD, 34), (BOW, 17), (ARMOR, 4))):
        server.command(f"item replace block {BARREL} container.{slot} with minecraft:{gear[0]}{gear_tag(gear, damage)}")
    server.assert_state("forge6_saves_original_enchantments_and_wear", f"{stored(0, SWORD, 34)} {stored(1, BOW, 17)} {stored(2, ARMOR, 4)}", results)


def spawn_cow(server, pos, tag, name):
    server.command(f'summon minecraft:cow {pos} {{Tags:["{tag}"],CustomName:\'{{"text":"{name}"}}\',NoAI:1b,'
                   'Attributes:[{Name:"minecraft:generic.max_health",Base:40d}],Health:40.0f,PersistenceRequired:1b}')


def assert_wither(server, results, tag, name):
    cow = target(tag)
    server.command(f"data get entity {cow} ActiveEffects")
    server.assert_state(name, f'if entity {cow} unless data entity {cow} {{Health:40.0f}} if data entity {cow} {WITHER}', results)
    server.command(f"execute store result score WitherTicks port_combat run data get entity {cow} {WITHER}.Duration")
    server.assert_state(f"{tag}_uses_the_historical_200_tick_base_duration", "if score WitherTicks port_combat matches 170..200", results)


def take_gear(server, slot, destination):
    server.command(f"item replace entity {PLAYER} {destination} from block {BARREL} container.{slot}")
    server.command(f"item replace block {BARREL} container.{slot} with minecraft:air")


def return_gear(server, slot, source):
    server.command(f"item replace block {BARREL} container.{slot} from entity {PLAYER} {source}")
    server.command(f"item replace entity {PLAYER} {source} with minecraft:air")


def melee(server, results, client, output, damage):
    take_gear(server, 0, "hotbar.0")
    spawn_cow(server, MELEE, "wither_blade", "Withering target")
    server.command(f"tp {PLAYER} 3810.5 80 10.5 180 15")
    (output / "server-main-hand").touch()
    wait_signal(client, output, "main-hand-hit")
    assert_wither(server, results, "wither_blade", "native_survival_melee_applies_withering")
    server.assert_state("main_hand_hit_spends_one_sword_durability", equipped(0, SWORD, damage + 1), results)

    server.command(f"item replace entity {PLAYER} weapon.offhand from entity {PLAYER} hotbar.0")
    server.command(f"item replace entity {PLAYER} hotbar.0 with minecraft:stone_sword")
    spawn_cow(server, OFFHAND, "wither_offhand", "Offhand control")
    server.command(f"tp {PLAYER} 3814.5 80 10.5 180 15")
    (output / "server-off-hand").touch()
    wait_signal(client, output, "off-hand-hit")
    cow = target("wither_offhand")
    server.assert_state("offhand_only_enchantment_leaves_the_hit_unwithered",
                        f'if entity {cow} unless data entity {cow} {{Health:40.0f}} '
                        f'unless data entity {cow} ActiveEffects[{{"forge:id":"minecraft:wither"}}]', results)
    return_gear(server, 0, "weapon.offhand")
    server.command(f"kill {target('wither_blade')}")
    server.command(f"kill {cow}")


def projectile(server, results):
    take_gear(server, 1, "hotbar.0")
    server.command(f"tp {PLAYER} 3823.5 80 14.5 180 10")
    spawn_cow(server, BOW_TARGET, "wither_arrow", "Owned arrow target")
    arrow = target("port_arrow", entity="arrow")
    server.command('summon minecraft:arrow 3823.5 80.85 12.5 {Tags:["port_arrow"],NoGravity:1b,damage:2.0d,pickup:0b}')
    server.command(f"data modify entity {arrow} Owner set from entity {PLAYER} UUID")
    server.command(f"data modify entity {arrow} Motion set value [0.0d,0.0d,-2.0d]")
    assert_wither(server, results, "wither_arrow", "native_owned_arrow_applies_withering_from_held_bow")
    return_gear(server, 1, "hotbar.0")
    server.command(f"kill {target('wither_arrow')}")


def attacked_by_zombie(server, results, *, repel):
    server.command(f"effect give {PLAYER} minecraft:instant_health 1 10 true")
    server.assert_state("wearer_ready_for_a_native_hit", f'if data entity {PLAYER} {{Health:20.0f,HurtTime:0s}}', results)
    server.command(f"tp {PLAYER} 3804.5 80 9.9 180 10")
    server.command(f'summon minecraft:zombie {ZOMBIE} {{Tags:["legacy_attacker"],IsBaby:0b,PersistenceRequired:1b,NoGravity:1b,'
                   'Attributes:[{Name:"minecraft:generic.movement_speed",Base:0d}]}')
    zombie = target("legacy_attacker", entity="zombie")
    at_origin = target("legacy_attacker", entity="zombie", where=f"{ZOMBIE_AT},distance=..0.02")
    health = f'unless data entity {PLAYER} {{Health:20.0f}}'
    if repel:
        in_range = target("legacy_attacker", entity="zombie", where=f"{ZOMBIE_AT},dx=40,dy=1,dz=40")
        server.assert_state("repellent_teleports_the_actual_melee_attacker_within_the_configured_range",
                            f"{health} if entity {in_range} unless entity {at_origin}", results, timeout=20)
        server.command(f"data get entity {zombie} Pos")
    else:
        server.assert_state("ordinary_armor_takes_the_hit_without_teleporting_the_attacker",
                            f"{health} if entity {at_origin}", results, timeout=20)
    server.command(f"kill {zombie}")


def exercise(args, server, results, report, *, reopened):
    phase = "reopened" if reopened else "converted"
    output = ROOT / "build/port-evidence" / f"{args.name}-{phase}-client"
    summary = output / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "legacy-combat", "--output", str(output), "--server", f"127.0.0.1:{args.port}", "--timeout", "150"]
    with (server.directory / f"client-{phase}.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            if reopened:
                server.assert_state("saved_armor_and_weapons_survive_the_dedicated_restart",
                                    f"{equipped(102, ARMOR)} {stored(0, SWORD, 35)} {stored(1, BOW, 17)}", results)
                return_gear(server, 2, "armor.chest")

            melee(server, results, client, output, 35 if reopened else 34)
            projectile(server, results)
            server.command(f"item replace entity {PLAYER} armor.chest with minecraft:iron_chestplate")
            attacked_by_zombie(server, results, repel=False)
            take_gear(server, 2, "armor.chest")
            attacked_by_zombie(server, results, repel=True)
            server.assert_state("legacy_armor_stays_equipped_after_repelling_a_hit", equipped(102, ARMOR), results)
            (output / "server-verified").touch()
            if client.wait(timeout=35) != 0:
                raise AssertionError(f"Legacy combat client failed, see {summary}")
            capture = json.loads(summary.read_text())["client"]["captures"]["legacy-combat"]
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
    config = port / "config/enderio/base-common.toml"
    config.parent.mkdir(parents=True)
    config.write_text(CONFIG)
    report = {"passed": False, "forge": FORGE, "jarSha256": digest(port / "mods/enderio.jar"),
              "legacyJarSha256": digest(legacy / "mods/enderio.jar"), "assertions": [], "servers": [], "clients": {}}
    try:
        def converted(server, results):
            server.assert_state("migration_preserves_the_three_native_equipment_stacks",
                                f"{stored(0, SWORD, 34)} {stored(1, BOW, 17)} {stored(2, ARMOR, 4)}", results)
            exercise(args, server, results, report, reopened=False)

        run_stage(args, legacy, report, "legacy", legacy_fixture)
        shutil.copytree(legacy / WORLD, port / WORLD)
        run_stage(args, port, report, "converted", converted)
        run_stage(args, port, report, "reopened", lambda server, results: exercise(args, server, results, report, reopened=True))
        report["passed"] = True
    finally:
        path = directory / "legacy-combat-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Legacy combat migration passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
