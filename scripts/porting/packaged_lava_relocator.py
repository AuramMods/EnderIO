#!/usr/bin/env python3
"""Test natural Nether strider spawns and Forge teleport redirection in packaged Ender IO."""

import argparse
import json
from math import floor
from pathlib import Path
import shutil
import sys

sys.dont_write_bytecode = True
from packaged_natural_spawns import PLAYER, sample_natural_spawns, spawn_client_session
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage


NETHER = "minecraft:the_nether"
IN_NETHER = f"execute in {NETHER} run "
STATION = "5399.5 2 8.5"
RELOCATOR = "5414 3 8"
BASIN = "5409 2 3 5419 4 13"
REDIRECT_CONTROL = "5408 2 0"
AIR_TARGET = [5410.5, 5.0, 8.5]
STRIDER = "minecraft:strider"
FILTER = ('{Slot:0,id:"enderio:basic_soul_filter",Count:1,tag:{"enderio:data":{"enderio:soul_filter":'
          '{entities:[{index:0,value:{entity_type:"minecraft:strider",entity_tag:{}}}],isInvert:0b,nbt:0b}}}}')


def flat_dimension(name, biome):
    return {"type": name, "generator": {"type": "minecraft:flat", "settings": {
        "biome": biome, "layers": [{"block": "minecraft:bedrock", "height": 1}],
        "features": False, "lakes": False, "structure_overrides": [],
    }}}


def write_preset(directory):
    pack = directory / WORLD / "datapacks/relocator_lava"
    preset = pack / "data/port_qa/worldgen/world_preset/relocator_lava.json"
    preset.parent.mkdir(parents=True)
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {
        "pack_format": 15, "description": "Isolated natural strider spawn fixture",
    }}) + "\n")
    preset.write_text(json.dumps({"dimensions": {
        "minecraft:overworld": flat_dimension("minecraft:overworld", "minecraft:plains"),
        NETHER: flat_dimension(NETHER, "minecraft:nether_wastes"),
        "minecraft:the_end": {"type": "minecraft:the_end", "generator": {
            "type": "minecraft:noise", "biome_source": {"type": "minecraft:the_end"}, "settings": "minecraft:end",
        }},
    }}) + "\n")
    properties = directory / "server.properties"
    text = properties.read_text().replace("level-type=minecraft:flat", "level-type=port_qa:relocator_lava")
    properties.write_text(text + "initial-enabled-packs=vanilla,file/relocator_lava\nlevel-seed=265\n")


def setup(server, results):
    for command in ("gamerule doMobSpawning false", "gamerule spawnRadius 0", "difficulty normal",
                    "forceload add -8 -8 8 8", "fill -2 79 -2 2 79 2 minecraft:stone", "setworldspawn 0 80 0"):
        server.command(command)
    server.assert_state("overworld_login_platform_is_present", "if block 0 79 0 minecraft:stone", results)
    server.command(IN_NETHER + "forceload add 5392 -16 5455 47")
    server.assert_state("real_nether_fixture_is_loaded_with_the_vanilla_spawn_biome",
                        f"in {NETHER} if loaded 5399 2 8 if loaded {RELOCATOR} if loaded 5440 4 17 "
                        "if biome 5440 4 17 minecraft:nether_wastes if block 5440 0 17 minecraft:bedrock", results)
    server.command(IN_NETHER + "fill 5396 1 5 5402 1 11 minecraft:glass")
    server.command(IN_NETHER + "fill 5408 1 2 5420 4 14 minecraft:glass hollow")
    server.command(IN_NETHER + "fill 5409 1 3 5419 1 13 minecraft:basalt")
    server.command(IN_NETHER + "fill 5409 4 3 5419 4 13 minecraft:air")
    server.command(IN_NETHER + "fill 5427 1 -1 5452 4 32 minecraft:glass hollow")
    server.command(IN_NETHER + "fill 5428 1 0 5451 4 31 minecraft:lava")
    server.command(IN_NETHER + f"setblock {RELOCATOR} enderio:relocator_obelisk")
    capacitor = '{Slot:1,id:"enderio:octadic_capacitor",Count:1}'
    server.command(IN_NETHER + f'data merge block {RELOCATOR} {{Items:{{Size:2,Items:[{FILTER},{capacitor}]}},'
                   'EnergyStored:540000,ActionRange:{range:42,isVisible:0b},RedstoneControl:"always_active"}')
    server.assert_state("strider_source_and_dry_basin_are_both_available",
                        f"in {NETHER} if block 5440 4 17 minecraft:lava if block 5440 5 17 minecraft:air "
                        f"if block 5410 2 8 minecraft:air if block 5410 1 8 minecraft:basalt "
                        f'if data block {RELOCATOR} {{ActionRange:{{range:42}},RedstoneControl:"always_active",'
                        f'Items:{{Items:[{FILTER},{capacitor}]}}}}', results)


def in_source(pos):
    x, y, z = pos
    return 5428 <= x < 5452 and 1 <= y < 5 and 0 <= z < 32


def in_basin(pos):
    x, y, z = pos
    return 5409 <= x < 5420 and 2 <= y < 5 and 3 <= z < 14


def outcome(sample):
    attempt, join, teleport = (sample[name] for name in ("finalize", "join", "teleport"))
    if join is None:
        return "insertion missing"
    if any(record["dimension"] != NETHER or record["placement"] != "IN_LAVA" or record["canceled"]
           or not record["inLava"] for record in (attempt, join)):
        return "inconsistent"
    if attempt["tick"] != join["tick"] or attempt["position"] != join["position"]:
        return "inconsistent"
    at_source = attempt["origin"] == join["position"]
    if teleport is None and at_source:
        return "original"
    if teleport is None or teleport["canceled"] or teleport["origin"] != attempt["origin"] or teleport["tick"] != join["tick"]:
        return "inconsistent"
    if at_source and teleport["position"] == AIR_TARGET and not teleport["inLava"]:
        return "redirect rejected"
    if teleport["inLava"] and teleport["position"] == join["position"] and in_basin(join["position"]):
        return "relocated"
    return "inconsistent"


def clear_striders(server):
    server.command(IN_NETHER + f"kill @e[type={STRIDER},x=5408,y=0,z=-1,dx=45,dy=15,dz=33]")


def phase(server, results, name, expected):
    samples = sample_natural_spawns(server, results, name, {STRIDER: expected},
                                   in_source, outcome, clear_striders, timeout=60)
    last = samples[-1]["join"]
    pos = " ".join(str(floor(value)) for value in last["position"])
    server.assert_state(f"{name}_verifies_live_strider_and_lava_with_native_commands",
                        f'in {NETHER} if entity {last["uuid"]} if block {pos} minecraft:lava', results)


def exercise(server, results, reopened):
    server.command(IN_NETHER + f"tp {PLAYER} {STATION} -90 0")
    server.assert_state("survival_player_joins_the_fixture_in_the_nether",
                        f"in {NETHER} if entity @a[name={PLAYER},x=5399,y=2,z=8,distance=..2,gamemode=survival]", results)
    if reopened:
        server.assert_state("saved_nether_basin_range_and_filter_are_present",
                            f'in {NETHER} if block 5410 3 8 minecraft:lava if data block {RELOCATOR} '
                            f'{{ActionRange:{{range:42}},RedstoneControl:"always_active",Items:{{Items:[{FILTER}]}}}}', results)
        phase(server, results, "striders_relocate_into_saved_lava_after_restart", "relocated")
        return

    phase(server, results, "striders_keep_the_original_spawn_when_basin_is_dry", "original")
    server.command(IN_NETHER + f"fill {BASIN} minecraft:lava replace minecraft:air")
    server.assert_state("destination_basin_contains_lava_and_the_obelisk",
                        f"in {NETHER} if block 5410 3 8 minecraft:lava if block {RELOCATOR} enderio:relocator_obelisk", results)
    phase(server, results, "striders_relocate_into_the_filled_basin", "relocated")
    server.command(IN_NETHER + f"setblock {REDIRECT_CONTROL} minecraft:gold_block")
    server.assert_state("forge_listener_redirects_to_open_air_above_lava",
                       f"in {NETHER} if block {REDIRECT_CONTROL} minecraft:gold_block if block 5410 5 8 minecraft:air", results)
    phase(server, results, "forge_redirect_into_air_keeps_the_original_lava_spawn", "redirect rejected")
    server.command(IN_NETHER + f"setblock {REDIRECT_CONTROL} minecraft:air")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for path in ("java", "forge-server", "jar", "qa-jar", "launch"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")

    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    write_preset(directory)
    shutil.copyfile(args.qa_jar, directory / "mods/enderio-client-qa.jar")
    report = {"passed": False, "jarSha256": digest(directory / "mods/enderio.jar"),
              "assertions": [], "clients": {}, "servers": []}

    def first(server, results):
        setup(server, results)
        spawn_client_session(args, server, report, "exercise", lambda: exercise(server, results, False))

    try:
        run_stage(args, directory, report, "exercise", first)
        run_stage(args, directory, report, "reopened", lambda server, results:
                  spawn_client_session(args, server, report, "reopened", lambda: exercise(server, results, True)))
        report["passed"] = True
    finally:
        path = directory / "lava-relocator-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Lava Relocator passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
