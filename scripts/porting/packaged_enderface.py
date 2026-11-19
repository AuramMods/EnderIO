#!/usr/bin/env python3
"""Exercise the actual Enderface preview and remote menus on packaged Forge."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_spawners import prepare_profile, run_stage


PLAYER = "EnderIOQA"
PACK = "mod/enderio:data/enderio/datapacks/enderface"
FACE = "3040 81 8"
CHEST = "3042 81 3"
TANK = "3038 81 13"
WATER = '{Fluids:{Tanks:[{Index:0,Contents:{FluidName:"minecraft:water",Amount:1750}}]}}'
COPPER = '{id:"minecraft:copper_ingot",Count:7b}'


def assert_pack(server, results):
    output = server.command("datapack list enabled")
    passed = f"[{PACK} (feature)]" in output
    results.append({"assertion": "enderface_experiment_selected", "passed": passed, "output": output.strip()})
    if not passed:
        raise AssertionError(f"Enderface experiment did not load; see {server.log_path}")


def staff_charge(fe):
    return f'if data entity {PLAYER} Inventory[{{Slot:0b,id:"enderio:staff_of_travelling",tag:{{"enderio:data":{{"enderio:energy":{fe}}}}}}}]'


def setup(server, results):
    server.command("gamerule doMobSpawning false")
    server.command("gamerule doDaylightCycle false")
    server.command("gamerule spawnRadius 0")
    server.command("time set noon")
    server.command("forceload add 3024 0 3055 31")
    server.assert_state("preview_and_player_chunks_loaded", f"if loaded {FACE} if loaded 3040 80 24", results)
    server.command("fill 3034 79 4 3046 79 28 minecraft:stone")
    server.command("setworldspawn 3040 80 24")
    server.command(f"setblock {FACE} enderio:enderface")
    server.command(f"setblock {CHEST} minecraft:chest[facing=south]{{Items:[{{Slot:0b,id:\"minecraft:copper_ingot\",Count:7b}}]}}")
    server.command(f"setblock {TANK} enderio:fluid_tank")
    server.command(f"data merge block {TANK} {WATER}")
    server.command("setblock 3035 81 8 minecraft:bookshelf")
    server.command("setblock 3045 81 8 minecraft:crafting_table")
    server.command("setblock 3037 83 8 minecraft:light_blue_glazed_terracotta")
    server.command("setblock 3043 83 8 minecraft:orange_glazed_terracotta")
    server.assert_state("separate_remote_chest_and_tank_have_the_exact_fixtures",
                        f"if block {FACE} enderio:enderface if data block {CHEST} Items[{COPPER}] "
                        f"if data block {TANK} {WATER}", results)


def client_scenario(args, server, results, report, phase):
    output = ROOT / "build/port-evidence" / f"{args.name}-{phase}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "enderface", "--multiplayer-phase", phase, "--output", str(output),
               "--server", f"127.0.0.1:{args.port}", "--timeout", "150"]
    summary_path = output / "summary.json"
    with (server.directory / f"enderface-{phase}-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            if phase == "exercise":
                server.command(f'item replace entity {PLAYER} hotbar.0 with '
                               'enderio:staff_of_travelling{"enderio:data":{"enderio:energy":9000}}')
            server.command(f"tp {PLAYER} 3040.5 80 24.5 180 0.5")
            (output / "server-ready").touch()
            if phase == "exercise":
                wait_signal(client, output, "chest-transferred")
                server.assert_state("remote_shift_click_moves_all_seven_items_once",
                                    f"unless data block {CHEST} Items[0] if data entity {PLAYER} Inventory[{COPPER}] "
                                    f"{staff_charge(8000)}", results)
                wait_signal(client, output, "tank-opened")
                server.assert_state("remote_tank_has_exact_fluid_and_staff_cost",
                                    f"if data block {TANK} {WATER} {staff_charge(7000)}", results)
                server.command(f"setblock {FACE} minecraft:air")
                (output / "server-removed").touch()
            if client.wait(timeout=35) != 0:
                raise AssertionError(f"Enderface client failed; see {output}")
            frame = json.loads(summary_path.read_text())["client"]["captures"]["remote-chest"]
            if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Client and server Ender IO jars differ")
        except (RuntimeError, AssertionError, subprocess.TimeoutExpired):
            server.command(f"data get entity {PLAYER} Inventory")
            server.command(f"data get block {CHEST}")
            raise
        finally:
            stop_client(client)
            report["clients"][phase] = json.loads(summary_path.read_text()) if summary_path.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--launch", required=True, type=Path)
    parser.add_argument("--mod", action="append", default=[], type=Path, help="Install an optional mod on the server")
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")

    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    for mod in args.mod:
        shutil.copyfile(mod, directory / "mods" / mod.name)
    with (directory / "server.properties").open("a") as properties:
        properties.write(f"initial-enabled-packs=vanilla,{PACK}\n")
    report = {"passed": False, "forge": FORGE, "jarSha256": digest(directory / "mods/enderio.jar"),
              "assertions": [], "servers": [], "clients": {}}
    try:
        def fresh(server, results):
            assert_pack(server, results)
            setup(server, results)
            client_scenario(args, server, results, report, "exercise")
            server.assert_state("removing_the_enderface_keeps_both_remote_containers",
                                f"if block {FACE} minecraft:air if block {CHEST} minecraft:chest "
                                f"unless data block {CHEST} Items[0] if data block {TANK} {WATER}", results)
            server.command(f"setblock {FACE} enderio:enderface")

        def reopened(server, results):
            assert_pack(server, results)
            server.assert_state("reopened_world_keeps_the_replaced_enderface_and_container_state",
                                f"if block {FACE} enderio:enderface if block {CHEST} minecraft:chest "
                                f"unless data block {CHEST} Items[0] if data block {TANK} {WATER}", results)
            client_scenario(args, server, results, report, "verify")

        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", reopened)
        report["passed"] = True
    finally:
        path = directory / "enderface-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Enderface passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
