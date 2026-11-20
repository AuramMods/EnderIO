#!/usr/bin/env python3
"""Exercise the native Jade soul provider on each soul-bearing machine."""

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_solar_charging import check
from packaged_soul_binder import item, xp_tank
from packaged_spawners import PLAYER, prepare_profile, run_stage


@dataclass(frozen=True)
class Machine:
    name: str
    x: int
    block: str
    soul: str = ""

    @property
    def pos(self):
        return f"{self.x} 80 8"


SPAWNER = Machine("spawner", 3310, "enderio:powered_spawner", "pig")
FARM = Machine("farm", 3313, "enderio:farming_station", "villager")
ENGINE = Machine("engine", 3316, "enderio:soul_engine", "enderman")
PANELS = tuple(Machine(tier, x, f"enderio:{tier}_photovoltaic_module", "phantom")
               for tier, x in (("energetic", 3319), ("pulsating", 3322), ("vibrant", 3325)))
UNBOUND_PANEL = Machine("unbound-panel", 3328, "enderio:vibrant_photovoltaic_module")
ALL = (SPAWNER, FARM, ENGINE, *PANELS, UNBOUND_PANEL)
VIAL = "enderio:soul_vial"


def soul_data(name):
    return f'{{entity_type:"minecraft:{name}",entity_tag:{{}}}}'


def soul_tag(name):
    return f'{{"enderio:data":{{"enderio:soul":{soul_data(name)}}}}}'


def machine_state(machine, soul):
    body = (f"if data block {machine.pos} {{EntityStorage:{soul_data(soul)}}}"
            if soul else f"unless data block {machine.pos} EntityStorage.entity_type")
    return f"if block {machine.pos} {machine.block} {body}"


def hopper(machine):
    return f"{machine.x} 79 3"


def prepare(server, results):
    for command in ("gamerule doMobSpawning false", "gamerule doDaylightCycle false", "gamerule spawnRadius 0",
                    "time set midnight", "forceload add 3296 0 3359 15", "fill 3306 79 2 3332 79 14 minecraft:stone",
                    f"setworldspawn {SPAWNER.x} 80 11"):
        server.command(command)
    for machine in (SPAWNER, FARM, ENGINE, UNBOUND_PANEL):
        server.command(f"setblock {machine.pos} {machine.block}")
    for machine, energy in zip(PANELS, (12800, 51200, 288000)):
        binder = f"{machine.x} 80 3"
        server.command(f"setblock {hopper(machine)} minecraft:hopper[facing=down]")
        server.command(f"setblock {binder} enderio:soul_binder")
        contents = ",".join((item(0, VIAL, "tag:" + soul_tag("phantom")), item(1, machine.block),
                             item(4, "enderio:octadic_capacitor")))
        server.command(f"data merge block {binder} {{EnergyStored:{energy},{xp_tank(6200)},Items:{{Size:5,Items:[{contents}]}}}}")
    for machine in PANELS:
        product = item(0, machine.block, "tag:" + soul_tag("phantom"), vanilla=True)
        empty = item(1, VIAL, vanilla=True)
        server.assert_state(f"{machine.name}_stock_binder_recipe_exports_a_bound_panel_and_empty_vial",
                            f"if data block {hopper(machine)} {{Items:[{product},{empty}]}} "
                            f"unless data block {hopper(machine)} Items[{{Slot:1b}}].tag.\"enderio:data\".\"enderio:soul\"",
                            results, timeout=90)
    server.command("save-all flush")


class Session:
    def __init__(self, server, results, output, client):
        self.server = server
        self.results = results
        self.output = output
        self.client = client
        self.index = 0
        self.target = None

    def inspect(self, name, machine, soul, action="LOOK", enabled=True):
        if action == "VIAL":
            stack = VIAL + (soul_tag(soul) if soul else "")
            self.server.command(f"item replace entity {PLAYER} hotbar.0 with {stack}")
        if action == "PLACE":
            self.server.command(f"item replace entity {PLAYER} hotbar.0 from block {hopper(machine)} container.0")
            self.server.assert_state(f"{machine.name}_player_receives_the_exact_bound_output",
                                     f"if data entity {PLAYER} Inventory[{item(0, machine.block, 'tag:' + soul_tag(soul), vanilla=True)}]",
                                     self.results)
            self.server.command(f"item replace block {hopper(machine)} container.0 with minecraft:air")
        if self.target != machine:
            pitch = 28.4 if "photovoltaic" in machine.block else 20.5
            self.server.command(f"tp {PLAYER} {machine.x + 0.5} 80 11.5 180 {pitch}")
            self.target = machine
        request = self.output / f"step-{self.index}.json"
        temp = request.with_suffix(".tmp")
        temp.write_text(json.dumps({"name": name, "x": machine.x, "block": machine.block,
                                    "soul": f"minecraft:{soul}" if soul else "", "enabled": enabled, "action": action}))
        temp.replace(request)
        state = machine_state(machine, soul)
        if action == "PLACE":
            state += f' unless data entity {PLAYER} Inventory[{{Slot:0b,id:"{machine.block}"}}]'
        if action in ("VIAL", "PLACE"):
            # Observe the server action before waiting for Jade's next data request.
            wait_signal(self.client, self.output, "acted-" + name)
        self.server.assert_state(name + "_matches_the_server_machine", state, self.results)
        wait_signal(self.client, self.output, name)
        self.index += 1


def native_binding(session):
    session.server.command(f"gamemode creative {PLAYER}")
    for machine, first in ((SPAWNER, "cow"), (FARM, "bee"), (ENGINE, "blaze")):
        session.inspect(machine.name + "-initially-unbound", machine, "")
        session.inspect(machine.name + "-first-soul", machine, first, "VIAL")
        session.inspect(machine.name + "-cleared", machine, "", "VIAL")
        session.inspect(machine.name + "-rebound", machine, machine.soul, "VIAL")
    session.server.command(f"gamemode survival {PLAYER}")
    for machine in PANELS:
        session.inspect(machine.name + "-placed", machine, machine.soul, "PLACE")
    vibrant = PANELS[-1]
    session.inspect("provider-disabled", vibrant, vibrant.soul, "CONFIGURE", enabled=False)
    session.inspect("provider-restored", vibrant, vibrant.soul, "CONFIGURE")
    session.inspect(UNBOUND_PANEL.name, UNBOUND_PANEL, "")


def inspect_saved(session):
    for machine in ALL:
        session.inspect(machine.name + "-reopened", machine, machine.soul)


def exercise_client(args, server, results, report, stage, scenario):
    output = ROOT / "build/port-evidence" / f"{args.name}-{stage}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "jade-souls", "--output", str(output), "--server", f"127.0.0.1:{args.port}", "--timeout", "225"]
    summary = output / "summary.json"
    with (server.directory / f"jade-souls-client-{stage}.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            scenario(Session(server, results, output, client))
            (output / "server-finish").touch()
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"Jade soul client failed; see {output}")
            captures = json.loads(summary.read_text())["client"]["captures"]
            mods = next(iter(captures.values()))["loadedMods"]
            check(f"{stage}_peers_load_identical_production_jars", report["jars"],
                  {name: digest(Path(mods[name]["file"])) for name in report["jars"]}, results)
        finally:
            stop_client(client)
            report["clients"][stage] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for option in ("java", "forge-server", "jar", "jade", "launch"):
        parser.add_argument(f"--{option}", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    shutil.copyfile(args.jade, directory / "mods/jade.jar")
    report = {"passed": False, "forge": FORGE, "assertions": [], "servers": [], "clients": {},
              "jars": {name: digest(directory / "mods" / f"{name}.jar") for name in ("enderio", "jade")}}

    def fresh(server, results):
        prepare(server, results)
        exercise_client(args, server, results, report, "fresh", native_binding)

    def reopened(server, results):
        exercise_client(args, server, results, report, "reopened", inspect_saved)

    try:
        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", reopened)
        report["passed"] = True
    finally:
        result = directory / "jade-souls-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Jade soul overlays passed={report['passed']}, {len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
