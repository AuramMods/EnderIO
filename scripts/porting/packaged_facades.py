#!/usr/bin/env python3
"""Paint, install and render all four conduit facade variants on packaged Forge."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_spawners import prepare_profile, run_stage


VARIANTS = (
    (2652, "conduit_facade", "gold_block"),
    (2654, "hardened_conduit_facade", "diamond_block"),
    (2656, "transparent_conduit_facade", "redstone_block"),
    (2658, "transparent_hardened_conduit_facade", "emerald_block"),
)
STAINED_PAINTS = ("red_stained_glass", "blue_stained_glass", "red_stained_glass", "blue_stained_glass")
STANDALONE = 2656
PLAYER = "EnderIOQA"
BARREL = "2655 80 7"
ENERGY_CONDUIT = '{Conduits:["enderio:energy"],Connections:[[]]}'


def paint_data(paint):
    return f'tag:{{"enderio:data":{{"enderio:block_paint":{{paint:"minecraft:{paint}"}}}}}}'


def variants(stained_glass):
    if stained_glass:
        return tuple((x, blank, paint) for (x, blank, _), paint in zip(VARIANTS, STAINED_PAINTS))
    return VARIANTS


def painting_state(x, blank, paint):
    return (f'if data block {x} 80 7 {{EnergyStored:0,Items:{{Items:[{{Slot:2,id:"minecraft:{paint}",Count:1}},'
            f'{{Slot:3,id:"enderio:{blank}",Count:1,{paint_data(paint)}}}]}}}} '
            f'unless data block {x} 80 7 Items.Items[{{Slot:1}}] unless data block {x} 80 7 Task')


def hopper_state(x, facade, paint):
    return f'if data block {x} 79 7 Items[{{Slot:0b,id:"enderio:{facade}",Count:1b,{paint_data(paint)}}}]'


def bundle_state(x, facade, paint, conduit=True):
    bundle = f'if block {x} 80 13 enderio:conduit if data block {x} 80 13'
    provider = f'FacadeProvider:{{id:"enderio:{facade}",Count:1,{paint_data(paint)}}}'
    if conduit:
        return f'{bundle} {{Conduits:["enderio:energy"],{provider}}}'
    return f"{bundle} {{{provider}}} unless data block {x} 80 13 Conduits[0]"


def produce(server, results, stained_glass):
    server.command("forceload add 2640 0 2671 31")
    server.assert_state("both_facade_sections_are_loaded", "if loaded 2652 80 13 if loaded 2658 80 13", results)
    server.command("gamerule doMobSpawning false")
    server.command("gamerule doDaylightCycle false")
    server.command("gamerule spawnRadius 0")
    server.command("time set noon")
    server.command("fill 2648 79 5 2662 79 22 minecraft:stone")
    server.command("setworldspawn 2655 80 19")
    for x, blank, paint in variants(stained_glass):
        if not stained_glass or x != STANDALONE:
            server.command(f"setblock {x} 80 13 enderio:conduit{ENERGY_CONDUIT}")
        server.command(f"setblock {x} 80 7 enderio:painting_machine[facing=south]")
        server.command(f'data merge block {x} 80 7 {{EnergyStored:2400,Items:{{Items:['
                       f'{{Slot:0,id:"enderio:basic_capacitor",Count:1}},{{Slot:1,id:"enderio:{blank}",Count:1}},'
                       f'{{Slot:2,id:"minecraft:{paint}",Count:1}}]}}}}')
    for x, blank, paint in variants(stained_glass):
        server.assert_state(f"painting_{blank}_consumes_the_template_and_exact_charge",
                            painting_state(x, blank, paint), results, timeout=15)
        server.command(f"setblock {x} 79 7 minecraft:hopper[facing=down]")
        server.assert_state(f"hopper_recovers_{blank}_with_its_paint",
                            f"{hopper_state(x, blank, paint)} unless data block {x} 80 7 Items.Items[{{Slot:3}}] "
                            f'if data block {x} 80 7 Items.Items[{{Slot:2,id:"minecraft:{paint}",Count:1}}]', results)


def exercise_client(args, server, report):
    results = report["assertions"]
    output = ROOT / "build/port-evidence" / f"{args.name}-client"
    scenario = "stained-facades" if args.stained_glass else "facades"
    if args.oculus:
        scenario += "-oculus"
    elif args.embeddium:
        scenario += "-embeddium"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", scenario, "--output", str(output), "--server", f"127.0.0.1:{args.port}", "--timeout", "180"]
    with (server.directory / "facade-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            for slot, (x, facade, paint) in enumerate(variants(args.stained_glass)):
                standalone = args.stained_glass and x == STANDALONE
                pitch = 35 if standalone else 17
                server.command(f"tp {PLAYER} {x + 0.5} 80 15.5 180 {pitch}")
                server.command(f"item replace entity {PLAYER} hotbar.{slot} from block {x} 79 7 container.0")
                server.command(f"item replace block {x} 79 7 container.0 with minecraft:air")
                (output / f"server-place-{slot}").touch()
                wait_signal(client, output, f"placed-{slot}")
                server.assert_state(f"native_placement_consumes_{facade}_and_keeps_its_paint",
                                    f"{bundle_state(x, facade, paint, conduit=not standalone)} "
                                    f"unless data entity {PLAYER} Inventory[{{Slot:{slot}b}}]",
                                    results)
                if standalone:
                    server.command(f"tp {PLAYER} {x + 0.5} 80 15.5 180 38")
                    server.command(f'item replace entity {PLAYER} hotbar.4 with enderio:conduit'
                                   '{"enderio:data":{"enderio:conduit":"enderio:energy"}}')
                    (output / "server-add-conduit").touch()
                    wait_signal(client, output, "added-conduit")
                    server.assert_state("the_standalone_facade_accepts_a_survival_conduit",
                                        f"{bundle_state(x, facade, paint)} "
                                        f"unless data entity {PLAYER} Inventory[{{Slot:4b}}]", results)
            server.command(f"item replace entity {PLAYER} hotbar.8 with enderio:yeta_wrench")
            server.command(f"tp {PLAYER} 2655.5 80 19.5 180 9")
            (output / "server-inspect").touch()
            if client.wait(timeout=90) != 0:
                raise AssertionError(f"Facade client failed; see {output}")
            summary = json.loads((output / "summary.json").read_text())
            frame = summary["client"]["captures"]["facades-visible"]
            if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Client and server Ender IO jars differ")
        finally:
            stop_client(client)
            summary = output / "summary.json"
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def reopened(server, results, stained_glass):
    server.assert_state("all_facades_keep_their_items_paint_and_conduits_after_restart",
                        " ".join(bundle_state(*variant) for variant in variants(stained_glass)), results)
    server.command(f"setblock {BARREL} minecraft:barrel")
    for slot, (x, facade, paint) in enumerate(variants(stained_glass)):
        server.command(f"loot replace block {BARREL} container.{slot * 2} 2 mine {x} 80 13 minecraft:diamond_pickaxe")
        server.assert_state(f"native_loot_retains_the_{facade}_paint_and_exact_counts",
                            f'if data block {BARREL} Items[{{Slot:{slot * 2}b,id:"enderio:conduit",Count:1b}}] '
                            f'if data block {BARREL} Items[{{Slot:{slot * 2 + 1}b,id:"enderio:{facade}",Count:1b,{paint_data(paint)}}}]',
                            results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--launch", required=True, type=Path)
    parser.add_argument("--embeddium", action="store_true")
    parser.add_argument("--oculus", action="store_true", help="Use the stained-glass profile with Oculus installed and shaders disabled")
    parser.add_argument("--stained-glass", action="store_true")
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    args.stained_glass = args.stained_glass or args.oculus

    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "forge": FORGE, "jarSha256": digest(directory / "mods/enderio.jar"),
              "assertions": [], "servers": []}
    try:
        def fresh(server, results):
            produce(server, results, args.stained_glass)
            exercise_client(args, server, report)

        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", lambda server, results: reopened(server, results, args.stained_glass))
        report["passed"] = True
    finally:
        path = directory / "facade-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Facades passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
