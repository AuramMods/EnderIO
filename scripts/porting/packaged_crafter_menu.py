#!/usr/bin/env python3
"""Exercise a real Crafter menu, JEI transfer, and native crafting on packaged Forge."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage


PLAYER = "EnderIOQA"
CRAFTER = "3104 80 8"
PAPER_TAG = "{PortCrafterToken:7}"


def stack(slot, item, count=1, tag=None):
    payload = f",tag:{tag}" if tag else ""
    return f'{{Slot:{slot},id:"minecraft:{item}",Count:{count}{payload}}}'


def crafter_state(energy, *slots):
    return (f"if block {CRAFTER} enderio:crafter if data block {CRAFTER} "
            f'{{EnergyStored:{energy},Items:{{Items:[{{Slot:0,id:"enderio:basic_capacitor",Count:1}},{",".join(slots)}]}}}}')


def tagged_template(energy, *extra):
    return crafter_state(energy, stack(11, "paper", tag=PAPER_TAG), stack(12, "redstone"),
                         stack(20, "amethyst_shard"), *extra)


def write_recipe(directory):
    pack = directory / WORLD / "datapacks/crafter-menu"
    path = pack / "data/port_crafter/recipes/tagged_paper.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 15, "description": "Crafter menu fixture"}}) + "\n")
    path.write_text(json.dumps({
        "type": "minecraft:crafting_shapeless",
        "ingredients": [{"type": "forge:nbt", "item": "minecraft:paper", "nbt": PAPER_TAG},
                        {"item": "minecraft:redstone"}],
        "result": {"item": "minecraft:amethyst_shard"},
    }) + "\n")


def setup(server, results):
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    server.command("forceload add 3104 0 3119 15")
    server.assert_state("crafter_chunk_is_loaded", f"if loaded {CRAFTER}", results)
    server.command("fill 3104 79 4 3111 79 15 minecraft:stone")
    server.command("setworldspawn 3104 80 11")
    server.command(f"setblock {CRAFTER} enderio:crafter[facing=south]")
    server.command(f'data merge block {CRAFTER} {{EnergyStored:6400,RedstoneControl:"never_active",'
                   'Items:{Items:[{Slot:0,id:"enderio:basic_capacitor",Count:1}]}}')


def completed_craft(server, results):
    server.assert_state("one_craft_spends_3200_fe_and_keeps_only_the_template",
                        f"{tagged_template(3200)} "
                        f"unless data block {CRAFTER} Items.Items[{{Slot:1}}] "
                        f"unless data block {CRAFTER} Items.Items[{{Slot:2}}] "
                        f"unless data block {CRAFTER} Items.Items[{{Slot:10}}] "
                        f"unless data block {CRAFTER} Items.Items[{{Slot:14}}] "
                        f"unless data block {CRAFTER} OutputBuffer", results)


def exercise(args, server, results, report):
    output = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "crafter-jei" if args.jei else "crafter", "--output", str(output),
               "--server", f"127.0.0.1:{args.port}", "--timeout", "175"]
    summary_path = output / "summary.json"
    with (server.directory / "crafter-menu-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            for index, item in enumerate(("birch_planks 2", "paper", f"paper{PAPER_TAG} 2", "redstone 2")):
                server.command(f"item replace entity {PLAYER} hotbar.{index} with minecraft:{item}")
            server.command(f"tp {PLAYER} 3104.5 80 11.5 180 20")
            (output / "server-ready").touch()
            if args.jei:
                wait_signal(client, output, "shaped-template")
                server.assert_state("jei_transfers_the_shaped_birch_grid_without_spending_items",
                                    f"{crafter_state(6400, stack(11, 'birch_planks'), stack(14, 'birch_planks'), stack(20, 'stick', 4))} "
                                    f'if data entity {PLAYER} Inventory[{{Slot:0b,id:"minecraft:birch_planks",Count:2b}}]', results)
                (output / "server-shaped").touch()
            wait_signal(client, output, "inputs-ready")
            server.assert_state("server_has_exact_tagged_ghost_and_physical_slots",
                                f"{tagged_template(6400, stack(1, 'paper', tag=PAPER_TAG), stack(2, 'redstone'))} "
                                f"unless data block {CRAFTER} Items.Items[{{Slot:14}}]", results)
            server.command(f'data modify block {CRAFTER} RedstoneControl set value "always_active"')
            wait_signal(client, output, "output-collected")
            completed_craft(server, results)
            server.assert_state("player_receives_only_the_output_and_keeps_the_other_ingredients",
                                f'if data entity {PLAYER} Inventory[{{id:"minecraft:amethyst_shard",Count:1b}}] '
                                f'if data entity {PLAYER} Inventory[{{Slot:1b,id:"minecraft:paper",Count:1b}}] '
                                f'unless data entity {PLAYER} Inventory[{{Slot:1b}}].tag '
                                f'if data entity {PLAYER} Inventory[{{Slot:2b,id:"minecraft:paper",Count:1b,tag:{PAPER_TAG}}}] '
                                f'if data entity {PLAYER} Inventory[{{Slot:3b,id:"minecraft:redstone",Count:1b}}]', results)
            (output / "server-confirmed").touch()
            if client.wait(timeout=35) != 0:
                raise AssertionError(f"Crafter client failed; see {output}")
            frame = json.loads(summary_path.read_text())["client"]["captures"]["crafted-output"]
            if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Client and server Ender IO jars differ")
        finally:
            server.command(f"data get block {CRAFTER}")
            stop_client(client)
            report["client"] = json.loads(summary_path.read_text()) if summary_path.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--launch", required=True, type=Path)
    parser.add_argument("--jei", action="store_true", help="Use JEI in the prepared client")
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")

    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    write_recipe(directory)
    report = {"passed": False, "forge": FORGE, "jarSha256": digest(directory / "mods/enderio.jar"),
              "withJei": args.jei, "assertions": [], "servers": []}
    try:
        def fresh(server, results):
            setup(server, results)
            exercise(args, server, results, report)

        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", completed_craft)
        report["passed"] = True
    finally:
        path = directory / "crafter-menu-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Crafter menu passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
