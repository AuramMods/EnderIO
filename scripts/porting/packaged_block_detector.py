#!/usr/bin/env python3
"""Craft and exercise six survival-placed Block Detectors over guarded Forge TCP."""

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_solar_charging import check
from packaged_spawners import PLAYER, prepare_profile, run_stage


TABLE = "4370 80 8"
DETECTOR = "enderio:block_detector"
LAMP = "minecraft:redstone_lamp"
AIR = "minecraft:cave_air"
WOOL = "minecraft:white_wool"
GLASS = "minecraft:glass"
STONE = "minecraft:stone"
DROP = '@e[type=minecraft:item,x=4376.5,y=80.5,z=8.5,distance=..3]'


@dataclass(frozen=True)
class Fixture:
    name: str
    x: int
    dx: int
    dy: int
    dz: int
    yaw: float
    pitch: float

    def at(self, distance=0):
        return f"{self.x + distance * self.dx} {80 + distance * self.dy} {8 + distance * self.dz}"

    def side(self):
        return f"{self.x - 1} 80 8" if self.dy else f"{self.x} 81 8"

    def stand(self):
        if self.dy:
            return f"{self.x + 0.5} {77 if self.dy > 0 else 82} 10.5 {self.yaw} {self.pitch}"
        return f"{self.x + 0.5 - 3 * self.dx - self.dz} 80 {8.5 - 3 * self.dz + self.dx} {self.yaw} {self.pitch}"

    def signal(self, powered, *, direct=False):
        power = str(powered).lower()
        lamp = self.at(-1 if direct else -2)
        return (f"if block {self.at()} {DETECTOR}[facing={self.name},powered={power}] "
                f"if block {lamp} {LAMP}[lit={power}] if block {self.side()} {LAMP}[lit=false]")


FIXTURES = (
    Fixture("north", 4376, 0, 0, -1, 162, 18),
    Fixture("south", 4384, 0, 0, 1, -18, 18),
    Fixture("west", 4392, -1, 0, 0, 72, 18),
    Fixture("east", 4400, 1, 0, 0, -108, 18),
    Fixture("up", 4408, 0, 1, 0, 180, -55),
    Fixture("down", 4416, 0, -1, 0, 180, 60),
)


def setup(server):
    for command in ("gamerule doMobSpawning false", "gamerule doLimitedCrafting true", "gamerule spawnRadius 0",
                    "gamerule doDaylightCycle false", "time set noon", "forceload add 4368 0 4431 15",
                    "fill 4367 79 2 4424 79 14 minecraft:stone", "setworldspawn 4370 80 10",
                    f"setblock {TABLE} minecraft:crafting_table", "scoreboard objectives add port_detector dummy",
                    "fill 4408 79 9 4408 79 10 minecraft:air", "setblock 4408 76 10 minecraft:stone",
                    "setblock 4416 81 10 minecraft:stone"):
        server.command(command)
    for fixture in FIXTURES:
        for coordinate, block in ((fixture.at(1), WOOL), (fixture.at(-1), STONE), (fixture.at(-2), LAMP), (fixture.side(), LAMP)):
            server.command(f"setblock {coordinate} {block}")
        if fixture.dy:
            server.command(f"setblock {fixture.x + 1} 80 8 {STONE}")


def stock_craft(server, results, client, output):
    wait_signal(client, output, "connected")
    server.command(f"item replace entity {PLAYER} hotbar.0 with minecraft:stone 36")
    server.command(f"item replace entity {PLAYER} hotbar.1 with minecraft:redstone 12")
    (output / "server-no-grains").touch()
    wait_signal(client, output, "locked")
    server.command(f"item replace entity {PLAYER} hotbar.2 with enderio:grains_of_infinity 6")
    server.command(f"tp {PLAYER} 4370.5 80 10.5 180 18")
    (output / "server-table").touch()
    wait_signal(client, output, "crafted")
    server.command(f"execute store result score #detectors port_detector run clear {PLAYER} {DETECTOR} 0")
    inputs = ("minecraft:stone", "minecraft:redstone", "enderio:grains_of_infinity")
    consumed = " ".join(f'unless data entity {PLAYER} Inventory[{{id:"{item}"}}]' for item in inputs)
    server.assert_state("stock_survival_crafting_yields_six_untagged_detectors_and_consumes_54_ingredients",
                        f'if score #detectors port_detector matches 6 unless data entity {PLAYER} Inventory[{{id:"{DETECTOR}"}}].tag {consumed}',
                        results)


def place_all(server, results, client, output):
    for index, fixture in enumerate(FIXTURES):
        name = fixture.name
        server.command(f"tp {PLAYER} {fixture.stand()}")
        (output / f"server-place-{name}").touch()
        wait_signal(client, output, f"placed-{name}")
        server.assert_state(f"{name}_placement_onto_preoccupied_scene_powers_lamp_through_stone",
                            f"{fixture.signal(True)} if block {fixture.at(1)} {WOOL}", results)
        (output / f"server-powered-{name}").touch()
        wait_signal(client, output, f"saw-powered-{name}")
        server.command(f"setblock {fixture.at(1)} {AIR}")
        server.assert_state(f"{name}_cave_air_clears_power_through_stone", fixture.signal(False), results)
        (output / f"server-unpowered-{name}").touch()
        wait_signal(client, output, f"saw-unpowered-{name}")
        server.command(f"setblock {fixture.at(-1)} {LAMP}")
        server.command(f"setblock {fixture.at(1)} {GLASS}")
        server.assert_state(f"{name}_transparent_neighbor_drives_direct_output_only", fixture.signal(True, direct=True), results)
        server.command(f"setblock {fixture.at(1)} {AIR}")
        server.assert_state(f"{name}_direct_lamp_shuts_off_when_target_is_removed", fixture.signal(False, direct=True), results)
        server.command(f"setblock {fixture.at(-1)} {STONE}")
        if index % 2:
            server.command(f"setblock {fixture.at(1)} {WOOL}")
        server.assert_state(f"{name}_saves_a_mixed_power_state", fixture.signal(bool(index % 2)), results)
    server.assert_state("placing_all_six_consumes_the_crafted_stack",
                        f'unless data entity {PLAYER} Inventory[{{id:"{DETECTOR}"}}]', results)


def recover_drop(server, results, client, output):
    north = FIXTURES[0]
    server.command(f"setblock {north.at(1)} {GLASS}")
    server.assert_state("powered_detector_lights_the_indirect_lamp_before_mining", north.signal(True), results)
    server.command(f'item replace entity {PLAYER} hotbar.0 with minecraft:diamond_pickaxe'
                   '{Enchantments:[{id:"minecraft:efficiency",lvl:5s}]}')
    server.command(f"tp {PLAYER} {north.stand()}")
    (output / "server-mine").touch()
    wait_signal(client, output, "mined")
    drop = DROP[:-1] + f',nbt={{Item:{{id:"{DETECTOR}",Count:1b}}}},sort=nearest,limit=1]'
    server.assert_state("mining_cuts_power_and_yields_one_detector_while_the_target_stays",
                        f"if block {north.at()} minecraft:air if entity {drop} if block {north.at(1)} {GLASS} "
                        f"if block {north.at(-2)} {LAMP}[lit=false]", results)
    server.command(f"execute at {drop} run tp {PLAYER} ~ ~ ~")
    wait_signal(client, output, "picked-up")
    server.assert_state("ordinary_pickup_keeps_the_original_untagged_drop",
                        f'if data entity {PLAYER} Inventory[{{id:"{DETECTOR}",Count:1b}}] '
                        f'unless data entity {PLAYER} Inventory[{{id:"{DETECTOR}"}}].tag unless entity {DROP}', results)
    server.command(f"setblock {north.at(1)} {AIR}")
    server.command(f"tp {PLAYER} {north.stand()}")
    (output / "server-replace").touch()
    wait_signal(client, output, "replaced")
    server.assert_state("replacement_consumes_the_drop_and_preserves_the_unpowered_circuit",
                        f'{north.signal(False)} unless data entity {PLAYER} Inventory[{{id:"{DETECTOR}"}}]', results)
    (output / "server-finish").touch()


def fresh(args, server, results, report):
    setup(server)
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "block-detector", "--server", f"127.0.0.1:{args.port}", "--output", str(evidence), "--timeout", "205"]
    summary = evidence / "summary.json"
    with (server.directory / "block-detector-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            stock_craft(server, results, client, evidence)
            place_all(server, results, client, evidence)
            recover_drop(server, results, client, evidence)
            if client.wait(timeout=35) != 0:
                raise AssertionError(f"Block Detector client failed; see {evidence}")
            capture = json.loads(summary.read_text())["client"]["captures"]["detector-north-powered"]
            check("client_and_server_use_the_same_production_artifact", report["jarSha256"],
                  digest(Path(capture["loadedMods"]["enderio"]["file"])), results)
        finally:
            stop_client(client)
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def reopened(server, results):
    for index, fixture in enumerate(FIXTURES):
        active = bool(index % 2)
        target = WOOL if active else AIR
        server.assert_state(f"{fixture.name}_retains_its_facing_target_and_power_state",
                            f"{fixture.signal(active)} if block {fixture.at(1)} {target}", results)
        server.command(f"setblock {fixture.at(1)} {AIR if active else GLASS}")
        server.assert_state(f"{fixture.name}_inverts_its_signal_after_restart", fixture.signal(not active), results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for key in ("java", "forge-server", "jar", "launch"):
        parser.add_argument(f"--{key}", type=Path, required=True)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "forge": FORGE, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}
    try:
        run_stage(args, directory, report, "fresh", lambda server, results: fresh(args, server, results, report))
        run_stage(args, directory, report, "reopened", reopened)
        report["passed"] = True
    finally:
        result = directory / "block-detector-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Block Detector passed={report['passed']}, {len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
