#!/usr/bin/env python3
"""Mine real directional shapes and cancel selected Forge breaks in packaged multiplayer."""

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_facades import PLAYER, paint_data
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_solar_charging import check
from packaged_spawners import prepare_profile, run_stage
from packaged_ultimine import ENERGY, ORIGIN, REDSTONE, bundle, check_drops


@dataclass(frozen=True)
class Node:
    offset: tuple
    selected: bool = True
    paint: str | None = None
    types: tuple = (ENERGY,)
    protected: bool = False


@dataclass(frozen=True)
class Shape:
    name: str
    mode: str
    row: int
    nodes: tuple
    ghost: bool = False

    def pos(self, node):
        x, y, z = node.offset
        return ORIGIN + x, 80 + y, self.row + z


OAK = "oak_planks"
BIRCH = "birch_planks"
BASE = (0, 0, 0)
EAST = (1, 0, 0)
WEST = (-1, 0, 0)
NORTH = (0, 0, -1)
TRIALS = (
    Shape("small-tunnel", "small_tunnel", 40, (
        Node(BASE, paint=OAK), Node(NORTH, paint=OAK), Node((0, 0, -2), paint=OAK),
        Node((0, 0, -3), False, BIRCH), Node((0, 0, -4), False, OAK), Node(EAST, False, OAK))),
    Shape("ghost-square", "small_square", 54, (
        Node(BASE, paint=OAK), Node(EAST, paint=BIRCH, types=(ENERGY, REDSTONE)),
        Node(WEST, False, BIRCH, (REDSTONE,)), Node((-1, 1, 0), False, OAK), Node(NORTH, False, OAK)), True),
    Shape("protected-large-tunnel", "large_tunnel", 68, (
        Node(BASE), Node(EAST), Node(NORTH), Node((1, 0, -1), types=(ENERGY, REDSTONE), protected=True),
        Node((0, 0, -2)), Node(WEST, False, BIRCH, (REDSTONE,)), Node((2, 0, -1), False))),
    Shape("mining-tunnel", "mining_tunnel", 82, (
        Node(BASE, paint=OAK), Node((0, -1, -1), paint=OAK), Node((0, -2, -2), paint=OAK),
        Node((0, 1, -1), False, OAK), Node(NORTH, False, OAK))),
    Shape("escape-tunnel", "escape_tunnel", 96, (
        Node(BASE, paint=OAK), Node((0, 1, -1), paint=OAK), Node((0, 2, -2), paint=OAK),
        Node((0, -1, -1), False, OAK), Node(NORTH, False, OAK))),
    Shape("protected-origin", "small_square", 110, (
        Node(BASE, paint=OAK, types=(ENERGY, REDSTONE), protected=True), Node(EAST, paint=OAK))),
)


def coords(pos):
    return " ".join(map(str, pos))


def state(pos, types, paint):
    if not types and not paint:
        return f"if block {pos} minecraft:air"
    conduits = f"if block {pos} enderio:conduit"
    if types:
        conduits += f" if data block {pos} {{Conduits:{json.dumps(types)}}}"
    length = f"unless data block {pos} Conduits[{len(types)}]"
    facade = (f'if data block {pos} {{FacadeProvider:{{id:"enderio:conduit_facade",{paint_data(paint)}}}}}'
              if paint else f"unless data block {pos} FacadeProvider.id")
    return f"{conduits} {length} {facade}"


def verify(server, results, trial, with_ultimine, stage):
    visible_facade = trial.nodes[0].paint and not trial.ghost
    drops = 0
    conditions = []
    for node in trial.nodes:
        types, paint = node.types, node.paint
        selected = node.selected if with_ultimine else node.offset == BASE
        if stage != "initial" and selected and not node.protected and not trial.nodes[0].protected:
            drops += 1
            if visible_facade:
                paint = None
            else:
                types = tuple(kind for kind in types if kind != ENERGY)
        conditions.append(state(coords(trial.pos(node)), types, paint))
    try:
        server.assert_state(f"{stage}_{trial.name}_exact_layout", " ".join(conditions), results)
    except AssertionError:
        for node in trial.nodes:
            server.command(f"data get block {coords(trial.pos(node))}")
        raise
    if stage != "initial":
        item = (f'{{id:"enderio:conduit_facade",{paint_data(OAK)}}}' if visible_facade else
                '{id:"enderio:conduit",tag:{"enderio:data":{"enderio:conduit":"enderio:energy"}}}')
        check_drops(server, results, trial.row, item, drops, f"{stage}_{trial.name}")


def prepare(server, results):
    for command in ("gamerule doMobSpawning false", "gamerule doDaylightCycle false", "gamerule spawnRadius 0",
                    "time set noon", "forceload add 2864 32 2895 127", f"setworldspawn {ORIGIN} 80 43",
                    "scoreboard objectives add port_ultimine dummy"):
        server.command(command)
    for trial in TRIALS:
        server.command(f"fill 2876 77 {trial.row - 5} 2883 79 {trial.row + 6} minecraft:stone")
        for node in trial.nodes:
            server.command(f"setblock {coords(trial.pos(node))} {bundle(node.types, node.paint)}")
        verify(server, results, trial, False, "initial")


def exercise_client(args, server, results, report):
    output = ROOT / "build/port-evidence" / f"{args.name}-client"
    with_ultimine = bool(args.ultimine)
    scenario = "ultimine-shapes" if with_ultimine else "ultimine-shapes-absent"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", scenario, "--output", str(output), "--server", f"127.0.0.1:{args.port}", "--timeout", "240"]
    summary_path = output / "summary.json"
    with (server.directory / "ultimine-shapes-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            server.command(f"tag {PLAYER} add port_keep_ultimine_drops")
            server.command(f"item replace entity {PLAYER} hotbar.0 with minecraft:diamond_pickaxe"
                           '{Enchantments:[{id:"minecraft:efficiency",lvl:5s}]}')
            server.command(f"effect give {PLAYER} minecraft:haste 180 1 true")
            for index, trial in enumerate(TRIALS):
                for node in trial.nodes:
                    if node.protected:
                        tag = "_".join(map(str, trial.pos(node)))
                        server.command(f"tag {PLAYER} add port_protect_{tag}")
                offhand = "enderio:yeta_wrench" if trial.ghost else "minecraft:air"
                server.command(f"item replace entity {PLAYER} weapon.offhand with {offhand}")
                server.command(f"tp {PLAYER} {ORIGIN + 0.5} 80 {trial.row + 3.5} 180 20")
                request = output / f"server-{index}.json"
                temp = request.with_suffix(".tmp")
                temp.write_text(json.dumps({"name": trial.name, "origin": trial.pos(trial.nodes[0]),
                                            "preview": [trial.pos(n) for n in trial.nodes if n.selected],
                                            "shape": trial.mode, "ghost": trial.ghost,
                                            "protectOrigin": trial.nodes[0].protected, "last": index + 1 == len(TRIALS)}))
                start = len(server.lines)
                temp.replace(request)
                wait_signal(client, output, trial.name)
                verify(server, results, trial, with_ultimine, "mined")
                protected = [coords(trial.pos(n)) for n in trial.nodes
                             if n.protected and (with_ultimine or n.offset == BASE)]
                marker = "PORT_ULTIMINE_CANCEL "
                observed = [line.split(marker, 1)[1].strip() for line in server.lines[start:] if marker in line]
                check(f"{trial.name}_exact_forge_cancellations", sorted(protected), sorted(observed), results)
            if client.wait(timeout=45) != 0:
                raise AssertionError(f"Ultimine shape client failed; see {output}")
            frame = json.loads(summary_path.read_text())["client"]["captures"][TRIALS[0].name]
            loaded = frame["loadedMods"]
            check("production_and_observer_jars_match_on_both_peers", report["jars"],
                  {mod: digest(Path(loaded[mod]["file"])) for mod in report["jars"]}, results)
        finally:
            stop_client(client)
            report["client"] = json.loads(summary_path.read_text()) if summary_path.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for option in ("java", "forge-server", "jar", "qa-jar", "launch"):
        parser.add_argument(f"--{option}", required=True, type=Path)
    parser.add_argument("--ultimine", nargs=3, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")

    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    artifacts = {"enderio": args.jar, "enderio_client_qa": args.qa_jar}
    artifacts.update(zip(("ftbultimine", "ftblibrary", "architectury"), args.ultimine or ()))
    for name, artifact in artifacts.items():
        if name != "enderio":
            shutil.copyfile(artifact, directory / "mods" / (name + ".jar"))
    report = {"passed": False, "forge": FORGE, "jars": {name: digest(path) for name, path in artifacts.items()},
              "assertions": [], "servers": []}

    def fresh(server, results):
        prepare(server, results)
        exercise_client(args, server, results, report)

    def reopened(server, results):
        for trial in TRIALS:
            verify(server, results, trial, bool(args.ultimine), "reopened")

    try:
        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", reopened)
        report["passed"] = True
    finally:
        result = directory / "ultimine-shapes-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Ultimine shapes passed={report['passed']}, {len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
