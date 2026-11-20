#!/usr/bin/env python3
"""Craft the Ender IO guide in survival and tour its pages in the guarded client."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_laserio_guide import setup
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_spawners import PLAYER, prepare_profile, run_stage


GUIDE_STACK = '{id:"patchouli:guide_book",tag:{"patchouli:book":"enderio:guide"}}'


def inventory(server, results, label):
    server.command(f"execute store result score #guides port_guide run clear {PLAYER} patchouli:guide_book 0")
    server.assert_state(label, f"if score #guides port_guide matches 1 "
                        f"if data entity {PLAYER} Inventory[{GUIDE_STACK}] "
                        f'unless data entity {PLAYER} Inventory[{{id:"minecraft:redstone"}}] '
                        f'unless data entity {PLAYER} Inventory[{{id:"minecraft:book"}}]', results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for key in ("java", "forge-server", "jar", "patchouli", "launch"):
        parser.add_argument(f"--{key}", required=True, type=Path)
    parser.add_argument("--jei", type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")

    directory = ROOT / "build/port-runtime" / args.name
    output = ROOT / "build/port-evidence" / args.name
    prepare_profile(args, directory, args.jar)
    artifacts = {"enderio": args.jar, "patchouli": args.patchouli}
    if args.jei:
        artifacts["jei"] = args.jei
    for mod, source in artifacts.items():
        if mod != "enderio":
            shutil.copyfile(source, directory / "mods" / source.name)
    report = {"passed": False, "forge": FORGE, "assertions": [], "servers": [],
              "jars": {mod: digest(source) for mod, source in artifacts.items()}}
    summary = output / "summary.json"

    def exercise(server, results):
        setup(server)
        command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
                   "--launch", str(args.launch.resolve()), "--scenario", "enderio-guide",
                   "--server", f"127.0.0.1:{args.port}", "--output", str(output), "--timeout", "165"]
        with (directory / "client-launch.log").open("w") as log:
            client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
            try:
                wait_signal(client, output, "connected")
                server.assert_state("fresh_survival_player_does_not_receive_a_guide",
                                    f"unless data entity {PLAYER} Inventory[{GUIDE_STACK}]", results)
                server.command(f"item replace entity {PLAYER} hotbar.0 with minecraft:redstone")
                server.command(f"item replace entity {PLAYER} hotbar.1 with minecraft:book")
                server.command(f"tp {PLAYER} 4720.5 80 10.5 180 20")
                (output / "server-table").touch()
                wait_signal(client, output, "crafted")
                inventory(server, results, "survival_craft_spends_both_ingredients_and_yields_one_enderio_guide")
                (output / "server-guide").touch()
                wait_signal(client, output, "read")
                inventory(server, results, "tour_and_resource_reload_leave_the_original_guide_in_inventory")
                (output / "server-finish").touch()
                if client.wait(timeout=25) != 0:
                    raise AssertionError(f"Ender IO guide smoke failed; see {output}")
                loaded = json.loads(summary.read_text())["client"]["captures"]["landing-reloaded"]["loadedMods"]
                matched = {name: digest(Path(loaded[name]["file"])) for name in artifacts} == report["jars"]
                results.append({"assertion": "the_client_and_server_loaded_the_same_artifacts", "passed": matched})
                if not matched:
                    raise AssertionError("Ender IO guide client and server loaded different jars")
            finally:
                stop_client(client)

    try:
        run_stage(args, directory, report, "guide", exercise)
        report["passed"] = True
    finally:
        report["client"] = json.loads(summary.read_text()) if summary.exists() else None
        path = directory / "enderio-guide-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Ender IO guide passed={report['passed']}, {len(report['assertions'])} server assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
