#!/usr/bin/env python3
"""Exercise authored spawner rules and the optional Soul Vial interaction in Forge."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_server_smoke import ROOT, digest
from packaged_spawners import CAPACITOR, SPAWNER, WORLD, prepare_profile, run_stage


COPY_MACHINE = "1356 80 8"
DENIED_MACHINE = "1372 80 8"
ALLOWED_MACHINE = "1388 80 8"
VANILLA_SPAWNER = "1412 80 8"
CROWD = '@e[type=minecraft:cow,tag=port_crowd]'
COPIES = '@e[type=minecraft:cow,tag=port_copy]'
SPAWNED_PIG = '@e[type=minecraft:pig,x=1388.5,y=80,z=8.5,distance=..7]'
VANILLA_COWS = '@e[type=minecraft:cow,x=1412.5,y=80,z=6.5,distance=..1.5]'
RELEASED_COW = '@e[type=minecraft:cow,tag=port_vial_release]'
COPY_BODY = 'CustomName:\'{"text":"Copied by the spawner"}\',Health:7.0f,Tags:["port_copy"],PersistenceRequired:1b'
VIAL_SOUL = '{entity_type:"minecraft:cow",entity_tag:{Health:7.0f,Tags:["port_vial_release"],NoAI:1b}}'
VIAL = "enderio:soul_vial"
PLAYER = "EnderIOQA"


def install_rules(directory):
    pack = directory / WORLD / "datapacks/spawner-rules"

    def write(path, data):
        file = pack / path
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(json.dumps(data) + "\n")

    write("pack.mcmeta", {"pack": {"pack_format": 15, "description": "Ender IO spawner test rules"}})
    tag_root = "data/enderio/tags/entity_types"
    write(f"{tag_root}/spawner_blacklist.json", {"values": ["minecraft:pig"]})
    write(f"{tag_root}/spawner_whitelist.json", {"values": ["minecraft:pig"]})
    write(f"{tag_root}/soul_vial_blacklist.json", {"values": ["minecraft:pig", "minecraft:sheep"]})
    write(f"{tag_root}/soul_vial_whitelist.json", {"values": ["minecraft:pig"]})
    write("data/enderio/eio_soul/spawner/minecraft_cow.json", {"entity": "minecraft:cow", "power": 14000, "type": "copy"})


def place_powered(server, where, mob, energy, entity_tag="{}"):
    server.command(f"setblock {where} {SPAWNER}[facing=south]")
    server.command(f'data merge block {where} {{EnergyStored:{energy},'
                   f'EntityStorage:{{entity_type:"minecraft:{mob}",entity_tag:{entity_tag}}},'
                   f'Items:{{Size:3,Items:[{{Slot:0,{CAPACITOR},Count:1}}]}}}}')


def paid_copy_job():
    return (f'if data block {COPY_MACHINE} {{EnergyStored:0,Task:{{EnergyCost:14000,EnergyConsumed:14000,'
            'SpawnMode:"copy",EntityType:"minecraft:cow"}} '
            f'unless entity {COPIES}')


def prepare_machines(server, results):
    server.command("gamerule doMobSpawning false")
    server.command("forceload add 1344 0 1423 15")
    server.assert_state("spawner_scenes_are_loaded", f"if loaded {COPY_MACHINE} if loaded {VANILLA_SPAWNER}", results)
    server.command("fill 1345 78 3 1395 78 14 minecraft:stone")
    for x in (1354.5, 1358.5):
        server.command(f'summon minecraft:cow {x} 79 11.5 {{NoAI:1b,PersistenceRequired:1b,Tags:["port_crowd"]}}')
    server.command(f"setblock 1356 81 8 enderio:mind_killer")
    place_powered(server, COPY_MACHINE, "cow", 14000, "{" + COPY_BODY + "}")
    place_powered(server, DENIED_MACHINE, "warden", 12000)
    place_powered(server, ALLOWED_MACHINE, "pig", 12000)
    server.assert_state("whitelist_allows_pig_over_matching_blacklist",
                        f"if entity {SPAWNED_PIG} if data block {ALLOWED_MACHINE} {{EnergyStored:0}}", results, timeout=20)
    server.assert_state("shipped_warden_blacklist_preserves_charge_and_prevents_job",
                        f"unless entity @e[type=minecraft:warden] if data block {DENIED_MACHINE} {{EnergyStored:12000}} "
                        f"unless data block {DENIED_MACHINE} Task", results)
    server.command(f"execute store result score #crowd port_spawners if entity {CROWD}")
    server.assert_state("two_nearby_cows_hold_fully_paid_custom_copy_job", paid_copy_job() +
                        " if score #crowd port_spawners matches 2", results, timeout=20)


def clone_condition():
    nbt = "{" + COPY_BODY + ',NoAI:1b,ForgeData:{"enderio:movable":1b}}'
    return f"if entity {COPIES[:-1]},limit=1,nbt={nbt}]"


def finish_copy(server, results):
    server.assert_state("fully_paid_copy_and_blocking_cows_survive_restart",
                        f"{paid_copy_job()} if entity {CROWD}", results)
    server.command(f"kill {CROWD}")
    server.assert_state("clearing_crowd_spawns_named_copy_with_health_and_mind_killer_flag",
                        clone_condition() + f" if data block {COPY_MACHINE} {{EnergyStored:0}}", results, timeout=15)
    moved = COPIES[:-1] + ",sort=nearest,limit=1,tag=!port_moved]"
    server.command(f"tag {moved} add port_moved")
    moved = '@e[type=minecraft:cow,tag=port_moved,limit=1]'
    server.command(f"tp {moved} 1365.5 79 5.5")
    server.command(f"data merge entity {moved} {{Motion:[0.6d,0d,0d]}}")
    server.assert_state("mind_killer_copy_keeps_no_ai_and_accepts_motion",
                        'if entity @e[type=minecraft:cow,tag=port_moved,x=1365.5,y=79,z=5.5,distance=0.1..8,'
                        'nbt={NoAI:1b,ForgeData:{"enderio:movable":1b}}]', results)


def prepare_vial_scene(server):
    server.command("fill 1405 79 4 1418 79 13 minecraft:grass_block")
    spawn = ('entity:{id:"minecraft:pig",Pos:[1412.5d,80.0d,6.5d]},'
             'custom_spawn_rules:{block_light_limit:{min_inclusive:0,max_inclusive:12}}')
    server.command(f"setblock {VANILLA_SPAWNER} minecraft:spawner"
                   f'{{Delay:30000s,MinSpawnDelay:30s,MaxSpawnDelay:30s,SpawnCount:1s,SpawnRange:3s,'
                   f'SpawnData:{{{spawn}}},SpawnPotentials:[{{weight:1,data:{{{spawn}}}}}]}}')


def verify_vanilla_pool(server, results, mob, name):
    server.command(f"data get block {VANILLA_SPAWNER}")
    server.assert_state(name, f'if data block {VANILLA_SPAWNER} {{SpawnData:{{entity:{{id:"minecraft:{mob}",'
                        'Pos:[1412.5d,80.0d,6.5d]},custom_spawn_rules:{block_light_limit:[I;0,12]}},'
                        'MinSpawnDelay:30s,MaxSpawnDelay:30s,SpawnCount:1s,SpawnRange:3s} '
                        f'if data block {VANILLA_SPAWNER} SpawnPotentials[{{weight:1,data:{{entity:{{id:"minecraft:{mob}"}}}}}}]', results)


def verify_filled_vial_used(server, results):
    server.command(f"execute store result score #vials port_spawners run clear {PLAYER} {VIAL} 0")
    server.assert_state("one_filled_vial_returns_exactly_one_empty_vial", f'if score #vials port_spawners matches 1 '
                        f'if data entity {PLAYER} Inventory[{{id:"{VIAL}",Count:1b}}] '
                        f'unless data entity {PLAYER} Inventory[].tag."enderio:data"."enderio:soul"', results)


def test_client(args, server, report, allowed):
    phase = "enabled" if allowed else "default"
    results = report["assertions"]
    if not allowed:
        prepare_vial_scene(server)
    output = ROOT / "build/port-evidence" / f"{args.name}-{phase}"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
               "--launch", str(args.launch.resolve()), "--scenario", "spawner-rules", "--output", str(output),
               "--server", f"127.0.0.1:{args.port}", "--timeout", "185", "--multiplayer-phase",
               "verify" if allowed else "exercise"]
    with (server.directory / f"vial-spawner-{phase}.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            server.command(f"clear {PLAYER}")
            server.command(f'item replace entity {PLAYER} hotbar.0 with {VIAL}{{"enderio:data":{{"enderio:soul":{VIAL_SOUL}}}}}')
            server.command(f"data get entity {PLAYER} Inventory")
            server.command(f"data get block {VANILLA_SPAWNER}")
            server.command(f"tp {PLAYER} 1412.5 80 11.5 180 14")
            wait_signal(client, output, "used-vial")
            server.command(f"data get entity {PLAYER} Inventory")
            wait_signal(client, output, "changed")
            verify_filled_vial_used(server, results)
            if allowed:
                server.assert_state("enabled_replacement_does_not_release_the_stored_entity", f"unless entity {RELEASED_COW}", results)
                server.command(f"data merge block {VANILLA_SPAWNER} {{Delay:0s}}")
                server.assert_state("enabled_replacement_spawns_a_new_cow_at_the_saved_position", f"if entity {VANILLA_COWS}", results, timeout=15)
                verify_vanilla_pool(server, results, "cow", "delay_reset_keeps_the_replacement_type_and_settings")
                server.command(f"kill {VANILLA_COWS}")
                server.assert_state("a_second_native_spawn_cycle_also_produces_cow", f"if entity {VANILLA_COWS}", results, timeout=15)
            else:
                verify_vanilla_pool(server, results, "pig", "default_disabled_keeps_vanilla_spawn_type_and_settings")
                server.assert_state("default_disabled_releases_the_filled_cow_instead", f"if entity {RELEASED_COW}", results)
                server.command(f"kill {RELEASED_COW}")
                server.command(f"item replace entity {PLAYER} hotbar.0 with {VIAL} 2")
                for mob, x in (("sheep", 1410.5), ("pig", 1414.5)):
                    server.command(f'summon minecraft:{mob} {x} 80 10.5 {{NoAI:1b,Tags:["port_capture"],PersistenceRequired:1b}}')
                (output / "server-targets").touch()
                wait_signal(client, output, "captured")
                pig_vial = '{id:"enderio:soul_vial",Count:1b,tag:{"enderio:data":{"enderio:soul":{entity_type:"minecraft:pig"}}}}'
                server.assert_state("vial_blacklist_blocks_sheep_but_whitelist_permits_pig",
                                    'if entity @e[type=minecraft:sheep,tag=port_capture] '
                                    'unless entity @e[type=minecraft:pig,tag=port_capture] '
                                    f'if data entity {PLAYER} Inventory[{pig_vial}]', results)
            (output / "server-verified").touch()
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"Spawner rules client {phase} failed; see {output}")
            summary = json.loads((output / "summary.json").read_text())
            if digest(Path(summary["client"]["capture"]["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Client and server production jars differ")
        finally:
            stop_client(client)
            summary_file = output / "summary.json"
            report["clients"][phase] = json.loads(summary_file.read_text()) if summary_file.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--launch", type=Path, help="Exercise the opt-in and default vanilla spawner interactions")
    parser.add_argument("--port", type=int, default=25742)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")

    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    install_rules(directory)
    report = {"passed": False, "jarSha256": digest(directory / "mods/enderio.jar"),
              "assertions": [], "servers": [], "clients": {}}
    try:
        def start(server, results):
            server.command("scoreboard objectives add port_spawners dummy")
            prepare_machines(server, results)
            if args.launch:
                test_client(args, server, report, False)

        run_stage(args, directory, report, "initial-default", start)
        if args.launch:
            config = directory / "config/enderio/base-common.toml"
            config.write_text(config.read_text().replace("allowSpawners = false", "allowSpawners = true"))

        def restart(server, results):
            finish_copy(server, results)
            if args.launch:
                test_client(args, server, report, True)

        run_stage(args, directory, report, "restart", restart)
        if args.launch:
            run_stage(args, directory, report, "reopened", lambda server, results: verify_vanilla_pool(
                server, results, "cow", "replaced_vanilla_spawner_survives_reopen"))
        report["passed"] = True
    finally:
        output = directory / "spawner-rules-results.json"
        output.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Spawner rules passed={report['passed']}, {len(report['assertions'])} assertions, {output}", flush=True)


if __name__ == "__main__":
    main()
