#!/usr/bin/env python3
"""Verify real Soul Vial capture, release and disk persistence in packaged Forge."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_server_smoke import ROOT, Server, digest


WORLD = "port-soul-vials"
DISPENSER = "1216 80 6"
POWER = "1216 80 5"
BARREL = "1215 80 6"
VIAL = "enderio:soul_vial"
DISPENSER_COW = '@e[type=minecraft:cow,tag=port_vial_dispenser]'
ORIGINAL_UUID = "[I;670,671,672,673]"
BODY = """Health:7.0f,NoAI:1b,Tags:["port_vial_dispenser"],CustomName:'{"text":"Dispenser cow"}'"""
MODERN_PAYLOAD = (f'"enderio:soul":{{entity_type:"minecraft:cow",entity_tag:{{{BODY}}}}},'
                  '"enderio:entity_max_health":10.0f')
PLAYER_COW = '@e[type=minecraft:cow,tag=port_vial_cow]'
PLAYER_VILLAGER = '@e[type=minecraft:villager,tag=port_vial_villager]'
COW_UUID = "[I;674,675,676,677]"
VILLAGER_UUID = "[I;678,679,680,681]"
COW_STATE = """Health:7.0f,NoAI:1b,Tags:["port_vial_cow"],CustomName:'{"text":"Capture cow"}'"""
VILLAGER_STATE = ("""Health:11.0f,NoAI:1b,Tags:["port_vial_villager"],CustomName:'{"text":"Capture librarian"}',Xp:13,"""
                  'VillagerData:{type:"minecraft:snow",profession:"minecraft:librarian",level:2},'
                  'Offers:{Recipes:[{buy:{id:"minecraft:paper",Count:7b},sell:{id:"minecraft:emerald",Count:1b},uses:2,maxUses:12}]}')


def pulse(server):
    server.command(f"setblock {POWER} minecraft:redstone_block")


def capture_dispenser(server, results, legacy):
    empty = "enderio:empty_soul_vial" if legacy else VIAL
    server.command("forceload add 1200 0 1231 15")
    server.assert_state("facility_chunks_loaded", f"if loaded {DISPENSER} if loaded {BARREL}", results)
    server.command("gamerule doMobSpawning false")
    server.command("fill 1214 79 4 1220 79 10 minecraft:stone")
    server.command(f'setblock {BARREL} minecraft:barrel{{Items:[{{Slot:0b,id:"{empty}",Count:2b}}]}}')
    server.command(f'setblock {DISPENSER} minecraft:dispenser[facing=east]{{Items:[{{Slot:0b,id:"{empty}",Count:1b}}]}}')
    server.command(f"summon minecraft:cow 1217.5 80 6.5 {{{BODY},UUID:{ORIGINAL_UUID},PersistenceRequired:1b}}")
    server.assert_state("dispenser_target_is_live", f"if entity {DISPENSER_COW}", results)
    pulse(server)
    if legacy:
        tag = f'BlockEntityTag:{{EntityStorage:{{Entity:{{id:"minecraft:cow",{BODY}}},MaxHealth:10.0f}}}}'
        server.assert_state("official_6_2_dispenser_writes_filled_vial",
                            f'unless entity {DISPENSER_COW} if data block {DISPENSER} Items['
                            f'{{Slot:0b,id:"enderio:filled_soul_vial",Count:1b,tag:{{{tag}}}}}]', results)
        server.command(f"data modify block {DISPENSER} Items[0].tag.PortKeep set value 67")
        server.command(f"data modify block {DISPENSER} Items[0].tag.BlockEntityTag.PortNestedKeep set value 68")
    else:
        assert_captured_dispenser(server, results, "fresh_dispenser_captures_real_live_cow", False)
    server.command(f"setblock {POWER} minecraft:air")
    server.command(f"data get block {DISPENSER} Items")


def assert_captured_dispenser(server, results, name, migrated):
    custom = ",PortKeep:67,BlockEntityTag:{PortNestedKeep:68}" if migrated else ""
    server.command(f"data get block {DISPENSER} Items")
    server.assert_state(name, f'unless entity {DISPENSER_COW} if data block {DISPENSER} Items['
                        f'{{Slot:0b,id:"{VIAL}",Count:1b,tag:{{"enderio:data":{{{MODERN_PAYLOAD}}}{custom}}}}}] '
                        f'unless data block {DISPENSER} Items[0].tag.BlockEntityTag.EntityStorage '
                        f'unless data block {DISPENSER} Items[0].tag."enderio:data"."enderio:soul".entity_tag.UUID', results)
    server.assert_state(name + "_leaves_empty_stack_intact",
                        f'if data block {BARREL} Items[{{Slot:0b,id:"{VIAL}",Count:2b}}] '
                        f'unless data block {BARREL} Items[0].tag."enderio:data"."enderio:soul"', results)


def release_dispenser(server, results, migrated):
    name = "converted_6_2_vial_has_its_original_soul" if migrated else "fresh_vial_survives_restart"
    assert_captured_dispenser(server, results, name, migrated)
    pulse(server)
    target = DISPENSER_COW[:-1] + ',x=1217.5,y=80,z=6.5,distance=..1,limit=1]'
    server.assert_state("loaded_dispenser_releases_cow_with_its_health_and_fresh_uuid",
                        f'if entity {target} if data entity {target} {{{BODY}}} unless data entity {target} {{UUID:{ORIGINAL_UUID}}} '
                        f'if data block {DISPENSER} Items[{{Slot:0b,id:"{VIAL}",Count:1b}}] '
                        f'unless data block {DISPENSER} Items[0].tag."enderio:data"."enderio:soul"', results)
    server.command(f"data get entity {target}")


def player_vial(entity, state, max_health):
    return (f'{{id:"{VIAL}",Count:1b,tag:{{"enderio:data":{{"enderio:soul":{{entity_type:"minecraft:{entity}",'
            f'entity_tag:{{{state}}}}},"enderio:entity_max_health":{max_health}.0f}}}}}}')


def prepare_player_capture(server):
    server.command("fill 1204 79 8 1212 79 14 minecraft:stone")
    server.command(f"summon minecraft:cow 1207.5 80 10.5 {{{COW_STATE},UUID:{COW_UUID},PersistenceRequired:1b}}")
    server.command(f"summon minecraft:villager 1209.5 80 10.5 {{{VILLAGER_STATE},UUID:{VILLAGER_UUID},PersistenceRequired:1b}}")


def assert_player_vials(server, results, name):
    cow = player_vial("cow", COW_STATE, 10)
    villager = player_vial("villager", VILLAGER_STATE, 20)
    server.command("execute store result score #vials port_vials run clear EnderIOQA enderio:soul_vial 0")
    server.assert_state(name, "if score #vials port_vials matches 2 "
                        f"unless entity {PLAYER_COW} unless entity {PLAYER_VILLAGER} "
                        f"if data entity EnderIOQA Inventory[{cow}] if data entity EnderIOQA Inventory[{villager}]", results)


def test_player(args, server, report, capture):
    results = report["assertions"]
    phase = "capture" if capture else "release"
    output = ROOT / "build/port-evidence" / (args.name + "-" + phase)
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
               "--launch", str(args.launch.resolve()), "--scenario", "soul-vials", "--output", str(output),
               "--server", f"127.0.0.1:{args.port}", "--timeout", "190", "--multiplayer-phase",
               "exercise" if capture else "verify"]
    if capture:
        prepare_player_capture(server)
        server.command("scoreboard objectives add port_vials dummy")
    with (server.directory / f"player-{phase}.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            server.wait("EnderIOQA joined the game", timeout=150)
            if capture:
                server.command("item replace entity EnderIOQA hotbar.0 with enderio:soul_vial 2")
            else:
                assert_player_vials(server, results, "both_occupied_player_vials_survive_restart")
            server.command("tp EnderIOQA 1208.5 80 12.5 180 18")
            wait_signal(client, output, "interacted")
            if capture:
                assert_player_vials(server, results, "native_survival_interactions_store_both_named_entities")
            else:
                cow = PLAYER_COW[:-1] + ',x=1206.5,y=80,z=13.5,distance=..1,limit=1]'
                villager = PLAYER_VILLAGER[:-1] + ',x=1210.5,y=80,z=13.5,distance=..1,limit=1]'
                server.assert_state("player_release_preserves_cow_state_with_fresh_uuid",
                                    f"if data entity {cow} {{{COW_STATE}}} unless data entity {cow} {{UUID:{COW_UUID}}}", results)
                server.assert_state("player_release_preserves_villager_trade_and_fresh_uuid",
                                    f"if data entity {villager} {{{VILLAGER_STATE}}} unless data entity {villager} {{UUID:{VILLAGER_UUID}}}", results)
                server.command(f"execute store result score #cows port_vials if entity {PLAYER_COW}")
                server.command(f"execute store result score #villagers port_vials if entity {PLAYER_VILLAGER}")
                server.assert_state("each_player_release_spawns_exactly_one_mob",
                                    "if score #cows port_vials matches 1 if score #villagers port_vials matches 1", results)
                server.assert_state("player_release_returns_two_empty_vials",
                                    f'if data entity EnderIOQA Inventory[{{id:"{VIAL}",Count:2b}}] '
                                    'unless data entity EnderIOQA Inventory[].tag."enderio:data"."enderio:soul"', results)
            (output / "server-verified").touch()
            if client.wait(timeout=35) != 0:
                raise AssertionError(f"Soul Vial client failed; see {output}")
            observation = json.loads((output / "summary.json").read_text())
            report.setdefault("clients", {})[phase] = observation
            frame = observation["client"]["capture"]
            if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Soul Vial client and server jars differ")
        finally:
            stop_client(client)


def prepare_profile(args, directory, jar):
    directory.mkdir()
    shutil.copytree(args.forge_server / "libraries", directory / "libraries")
    (directory / "mods").mkdir()
    shutil.copyfile(jar, directory / "mods/enderio.jar")
    (directory / "eula.txt").write_text("eula=true\n")
    flat = '{"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:plains"}'
    connection = "online-mode=false\nenforce-secure-profile=false\n" if args.launch else ""
    (directory / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={args.port}\nlevel-name={WORLD}\n"
        f"level-type=minecraft:flat\ngenerator-settings={flat}\n"
        "generate-structures=false\nview-distance=3\nsimulation-distance=3\n" + connection)


def run_stage(args, directory, report, name, scenario):
    server = Server(directory, args.java, name)
    try:
        server.wait('For help, type "help"', timeout=150)
        scenario(server, report["assertions"])
        if args.launch:
            test_player(args, server, report, name == "capture")
        server.command("save-all flush")
    finally:
        exit_code = server.stop()
        errors = [line.strip() for line in server.lines if "/ERROR]" in line or "/FATAL]" in line]
        registry_errors = [line for line in errors if "[ne.mi.re.GameData/REGISTRIES]" in line]
        runtime_errors = [line for line in errors if line not in registry_errors]
        report["servers"].append({"stage": name, "exit": exit_code, "errors": runtime_errors,
                                  "registryErrors": registry_errors, "log": str(server.log_path)})
    if exit_code or runtime_errors or (registry_errors and name != "converted"):
        raise RuntimeError(f"Forge errors in {name}; see {server.log_path}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--legacy-jar", type=Path)
    mode.add_argument("--launch", type=Path, help="Prepared guarded client for survival capture and its restart")
    parser.add_argument("--port", type=int, default=25731)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")

    directory = ROOT / "build/port-runtime" / args.name
    directory.mkdir()
    port = directory / "port"
    prepare_profile(args, port, args.jar)
    report = {"passed": False, "jarSha256": digest(port / "mods/enderio.jar"), "assertions": [], "servers": []}
    try:
        if args.legacy_jar:
            legacy = directory / "legacy"
            prepare_profile(args, legacy, args.legacy_jar)
            report["legacySha256"] = digest(legacy / "mods/enderio.jar")
            run_stage(args, legacy, report, "legacy", lambda server, results: capture_dispenser(server, results, True))
            shutil.copytree(legacy / WORLD, port / WORLD)
            run_stage(args, port, report, "converted", lambda server, results: assert_captured_dispenser(
                server, results, "converted_vial_has_soul_and_unrelated_item_data", True))
            run_stage(args, port, report, "restart", lambda server, results: release_dispenser(server, results, True))
        else:
            run_stage(args, port, report, "capture", lambda server, results: capture_dispenser(server, results, False))
            run_stage(args, port, report, "restart", lambda server, results: release_dispenser(server, results, False))
        report["passed"] = True
    finally:
        output = directory / "soul-vial-results.json"
        output.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Soul Vials passed={report['passed']}, {len(report['assertions'])} assertions, {output}", flush=True)


if __name__ == "__main__":
    main()
