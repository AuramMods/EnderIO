#!/usr/bin/env python3
"""Exercise entity obelisk menus, chunk lifecycle and native gameplay on packaged Forge."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage


MACHINES = (("inhibitor", 1700), ("aversion", 1703), ("relocator", 1706), ("attractor", 1712))
PLAYER = "EnderIOQA"
CAPACITOR = 'id:"enderio:basic_capacitor",Count:1'
PEARL = "minecraft:ender_pearl"
FILTER = ('id:"enderio:basic_soul_filter",Count:1,tag:{"enderio:data":{"enderio:soul_filter":'
          '{entities:[{index:0,value:{entity_type:"minecraft:pig",entity_tag:{}}}],isInvert:0b,nbt:0b}}}')
FIELD = "1696 0 1727 15"
PIG = '@e[type=minecraft:pig,tag=port_attractor,limit=1]'


def machine_state(name, x, radius, visible):
    capacitor_slot = 0 if name == "inhibitor" else 1
    inventory = f'{{Slot:{capacitor_slot},{CAPACITOR}}}'
    if capacitor_slot:
        inventory += f',{{Slot:0,{FILTER}}}'
    return (f'if block {x} 80 8 enderio:{name}_obelisk '
            f'if data block {x} 80 8 {{ActionRange:{{range:{radius},isVisible:{int(visible)}b}},'
            f'Items:{{Items:[{inventory}]}}}}')


def prepare_scene(server, results):
    server.command(f"forceload add {FIELD}")
    server.assert_state("entity_obelisk_chunks_loaded", "if loaded 1700 80 8 if loaded 1712 80 8", results)
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    server.command("scoreboard objectives add port_pearl_use minecraft.used:minecraft.ender_pearl")
    server.command("scoreboard objectives add port_pearl_clock dummy")
    server.command("fill 1696 79 5 1724 79 15 minecraft:stone")
    server.command("fill 1709 80 11 1709 83 13 minecraft:stone")
    server.command("setworldspawn 1700 80 11")
    for name, x in MACHINES:
        server.command(f"setblock {x} 80 8 enderio:{name}_obelisk[facing=south]")
        count = 1 if name == "inhibitor" else 2
        inventory = f'{{Slot:{count - 1},{CAPACITOR}}}'
        if count == 2:
            inventory += f',{{Slot:0,{FILTER}}}'
        server.command(f'data merge block {x} 80 8 {{Items:{{Size:{count},Items:[{inventory}]}},EnergyStored:100000,'
                       'ActionRange:{range:4,isVisible:0b}}')
        server.command(f"data get block {x} 80 8")
        server.assert_state(f"{name}_has_capacitor_and_configured_inputs", machine_state(name, x, 4, False), results)


def send_marker(output, name):
    (output / name).touch()


def move_to(server, x, z, yaw=180):
    server.command(f"tp {PLAYER} {x} 80 {z} {yaw} 0")


def unload_area(server, results):
    server.command("setworldspawn 0 80 0")
    server.command("setblock 0 79 0 minecraft:stone")
    move_to(server, 0.5, 0.5)
    server.command(f"forceload remove {FIELD}")
    server.assert_state("the_original_obelisk_chunks_really_unload",
                        "unless loaded 1700 80 8 unless loaded 1712 80 8", results, timeout=40)
    server.command(f"forceload add {FIELD}")
    server.assert_state("entity_obelisk_chunks_reload", "if loaded 1700 80 8 if loaded 1712 80 8", results)
    for name, x in MACHINES:
        server.assert_state(f"{name}_keeps_range_and_items_through_chunk_reload",
                            machine_state(name, x, 5, True), results)


def verify_pearls(server, output, client, results):
    move_to(server, 1700.5, 11.5, -90)
    server.command(f"item replace entity {PLAYER} hotbar.0 with {PEARL} 2")
    send_marker(output, "server-fire-first")
    wait_signal(client, output, "pearl-first")
    server.assert_state("reloaded_active_inhibitor_cancels_real_pearl",
                        f'if score {PLAYER} port_pearl_use matches 1 '
                        f'if data entity {PLAYER} Inventory[{{Slot:0b,id:"{PEARL}",Count:1b}}] '
                        f'if entity @a[name={PLAYER},x=1700.5,y=80,z=11.5,distance=..0.35] '
                        'unless entity @e[type=minecraft:ender_pearl]', results)
    server.command('data merge block 1700 80 8 {RedstoneControl:"never_active"}')
    # The client's predicted cooldown can expire before the server accepts another use.
    server.command("execute store result score #release port_pearl_clock run time query gametime")
    server.command("scoreboard players add #release port_pearl_clock 20")
    for _ in range(30):
        server.command("execute store result score #now port_pearl_clock run time query gametime")
        if "[Server] PORT_READY_TO_THROW" in server.command(
                "execute if score #now port_pearl_clock >= #release port_pearl_clock run say PORT_READY_TO_THROW"):
            break
    else:
        raise AssertionError(f"Server did not run 20 ticks between pearl uses; see {server.log_path}")
    send_marker(output, "server-fire-second")
    wait_signal(client, output, "pearl-second")
    try:
        server.assert_state("redstone_disabled_inhibitor_allows_same_pearl",
                            f'if score {PLAYER} port_pearl_use matches 2 '
                            f'if entity @a[name={PLAYER},x=1708.5,y=81,z=11.5,distance=..2] '
                            f'unless data entity {PLAYER} Inventory[{{id:"{PEARL}"}}] '
                            'unless entity @e[type=minecraft:ender_pearl]', results, timeout=14)
    except AssertionError:
        server.command(f"scoreboard players get {PLAYER} port_pearl_use")
        for field in ("Pos", "Rotation", "Health", "Inventory"):
            server.command(f"data get entity {PLAYER} {field}")
        server.command("data get block 1700 80 8")
        raise
    send_marker(output, "server-pearl-verified")


def verify_attractor(server, results):
    move_to(server, 1711.5, 13.5)
    server.command("fill 1713 80 7 1718 81 7 minecraft:stone")
    server.command("fill 1713 80 9 1718 81 9 minecraft:stone")
    server.command("fill 1718 80 8 1718 81 8 minecraft:stone")
    server.command('summon minecraft:pig 1716.5 80 8.5 {Tags:["port_attractor"],PersistenceRequired:1b}')
    try:
        server.assert_state("loaded_attractor_draws_pig_via_native_pathfinding",
                            'if entity @e[type=minecraft:pig,tag=port_attractor,x=1712.5,y=80,z=8.5,distance=..2.75]',
                            results, timeout=12)
    except AssertionError:
        server.command("data get block 1712 80 8")
        raise
    finally:
        server.command(f"data get entity {PIG} Pos")


def exercise_client(args, server, report):
    results = report["assertions"]
    output = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "entity-obelisks", "--output", str(output), "--server", f"127.0.0.1:{args.port}", "--timeout", "225"]
    with (server.directory / "entity-obelisks-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            for name, x in MACHINES:
                move_to(server, x + 0.5, 11.5)
                send_marker(output, f"server-menu-{name}")
                wait_signal(client, output, f"menu-{name}")
                server.assert_state(f"{name}_menu_saves_range_and_visibility",
                                    machine_state(name, x, 5, True), results)

            unload_area(server, results)
            verify_attractor(server, results)
            move_to(server, 1701.5, 11.5, -90)
            server.assert_state("active_inhibitor_allows_console_teleport",
                                f"if entity @a[name={PLAYER},x=1701.5,y=80,z=11.5,distance=..0.35]", results)
            verify_pearls(server, output, client, results)
            if client.wait(timeout=20) != 0:
                raise AssertionError(f"Entity obelisk client failed; see {output}")
            summary = json.loads((output / "summary.json").read_text())
            frame = summary["client"]["captures"]["attractor-obelisk"]
            if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Client and server Ender IO jars differ")
        finally:
            stop_client(client)
            summary = output / "summary.json"
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--launch", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "jarSha256": digest(directory / "mods/enderio.jar"), "assertions": [], "servers": []}
    try:
        def fresh(server, results):
            prepare_scene(server, results)
            exercise_client(args, server, report)

        def reopened(server, results):
            for name, x in MACHINES:
                server.assert_state(f"{name}_settings_survive_server_restart", machine_state(name, x, 5, True), results)
            server.assert_state("inhibitor_redstone_setting_survives_server_restart",
                                'if data block 1700 80 8 {RedstoneControl:"never_active"}', results)

        run_stage(args, directory, report, "fresh", fresh, startup_timeout=240)
        run_stage(args, directory, report, "reopened", reopened, startup_timeout=240)
        report["passed"] = True
    finally:
        output = directory / "entity-obelisk-results.json"
        output.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Entity obelisks passed={report['passed']}, {len(report['assertions'])} assertions, {output}", flush=True)


if __name__ == "__main__":
    main()
