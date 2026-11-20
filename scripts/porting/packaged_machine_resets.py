#!/usr/bin/env python3
"""Reset six populated machines using their stock survival recipes and native drops."""

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_niard import PACK, packs
from packaged_niard_progression import CHARGE, CONTROLS, FLUID, INVENTORY, item_data, tank
from packaged_obelisk_carry import OBELISKS, block_data, carried_stack
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_solar_charging import check
from packaged_spawners import PLAYER, WORLD, prepare_profile, run_stage
from saved_world import read_data_file


TABLE = "2320 80 16"
NO_LOOSE_INPUTS = ("unless entity @e[type=minecraft:item,x=2300,y=78,z=5,dx=40,dy=8,dz=16] "
                   "unless entity @e[type=minecraft:experience_orb,x=2300,y=78,z=5,dx=40,dy=8,dz=16]")
NO_RETURNED_INPUTS = " ".join(
    f'unless data entity {PLAYER} Inventory[{{id:"{item}"}}]'
    for item in ("minecraft:bucket", "minecraft:lava_bucket", "enderio:xp_juice_bucket",
                 "enderio:basic_capacitor", "enderio:basic_soul_filter")
)


@dataclass(frozen=True)
class Machine:
    name: str
    x: int
    state: str
    drop: str
    powered: bool

    @property
    def id(self):
        return f"enderio:{self.name}"

    @property
    def source(self):
        return f"{self.x} 80 8"

    @property
    def destination(self):
        return f"{self.x} 80 12"

    def blank(self):
        pos = self.destination
        checks = [f"if block {pos} {self.id}", f'if data block {pos} {{RedstoneControl:"always_active"}}',
                  f"unless data block {pos} Fluids.Tanks[0].Contents.FluidName",
                  f"unless data block {pos} Items.Items[0]", f"unless data block {pos} ActionRange"]
        if self.powered:
            checks.append(f"if data block {pos} {{EnergyStored:0}}")
        if self.name == "xp_obelisk":
            checks.extend(f"unless data block {pos} IOConfig.Modes.{side}"
                          for side in ("up", "down", "north", "south", "east", "west"))
        return " ".join(checks)


NIARD = Machine("niard", 2304, f"{{EnergyStored:{CHARGE},{CONTROLS},{tank(FLUID)},Items:{{Size:3,{INVENTORY}}}}}",
                '{id:"enderio:niard",Count:1b,tag:{"enderio:data":' + item_data() + '}}', True)


def obelisk(spec):
    return Machine(spec.id.split(":")[1], spec.x, block_data(spec), carried_stack(spec), bool(spec.charge))


MACHINES = (NIARD, obelisk(OBELISKS[4]), *(obelisk(spec) for spec in OBELISKS[:4]))


def stand(server, output, machine, marker, coord):
    x, y, z = map(int, coord.split())
    server.command(f"tp {PLAYER} {x + 0.5} {y} {z + 2.5} 180 20")
    (output / f"server-{machine.name}-{marker}").touch()


def setup(server, results):
    packs(server, results, True)
    for command in ("gamerule doMobSpawning false", "gamerule doLimitedCrafting true", "gamerule spawnRadius 0",
                    "gamerule doDaylightCycle false", "time set noon", "forceload add 2288 0 2351 31",
                    "fill 2300 79 5 2339 79 20 minecraft:stone", "setworldspawn 2320 80 19",
                    f"setblock {TABLE} minecraft:crafting_table", "scoreboard objectives add port_reset dummy"):
        server.command(command)
    for machine in MACHINES:
        server.command(f"setblock {machine.source} {machine.id}")
        server.command(f"data merge block {machine.source} {machine.state}")
        server.assert_state(f"{machine.name}_source_starts_with_the_exact_state",
                            f"if data block {machine.source} {machine.state}", results)


def reset_machine(server, results, client, output, machine, last):
    name, source = machine.name, machine.source
    drop = (f"@e[type=minecraft:item,x={machine.x + 0.5},y=80,z=8.5,distance=..3,"
            f"nbt={{Item:{machine.drop}}},sort=nearest,limit=1]")
    stand(server, output, machine, "mine", source)
    wait_signal(client, output, f"{name}-mined")
    server.assert_state(f"{name}_survival_drop_keeps_the_complete_state",
                        f"if block {source} minecraft:air if entity {drop}", results)
    server.command(f"execute at {drop} run tp {PLAYER} ~ ~ ~")
    wait_signal(client, output, f"{name}-picked-up")
    server.assert_state(f"{name}_pickup_has_the_original_data_in_inventory",
                        f"if data entity {PLAYER} Inventory[{machine.drop}] {NO_LOOSE_INPUTS}", results)
    stand(server, output, machine, "table", TABLE)
    wait_signal(client, output, f"{name}-reset")
    server.command(f"execute store result score #machines port_reset run clear {PLAYER} {machine.id} 0")
    server.assert_state(f"{name}_reset_yields_one_untagged_machine",
                        f'if score #machines port_reset matches 1 unless data entity {PLAYER} Inventory[{{id:"{machine.id}"}}].tag '
                        f"{NO_RETURNED_INPUTS} {NO_LOOSE_INPUTS}", results)
    stand(server, output, machine, "place", machine.destination)
    wait_signal(client, output, f"{name}-placed")
    server.assert_state(f"{name}_replants_with_factory_defaults",
                        f'{machine.blank()} unless data entity {PLAYER} Inventory[{{id:"{machine.id}"}}] '
                        f"{NO_RETURNED_INPUTS} {NO_LOOSE_INPUTS}", results)
    (output / f"server-{name}-open").touch()
    if not last:
        wait_signal(client, output, f"{name}-defaults")


def exercise(args, server, results, report):
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "machine-resets", "--server", f"127.0.0.1:{args.port}", "--output", str(evidence),
               "--timeout", "205"]
    summary = evidence / "summary.json"
    with (server.directory / "machine-resets-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            server.command(f'item replace entity {PLAYER} hotbar.0 with minecraft:diamond_pickaxe'
                           '{Enchantments:[{id:"minecraft:efficiency",lvl:5s}]}')
            for index, machine in enumerate(MACHINES):
                reset_machine(server, results, client, evidence, machine, index + 1 == len(MACHINES))
            if client.wait(timeout=35) != 0:
                raise AssertionError(f"Factory reset client failed; see {evidence}")
            capture = json.loads(summary.read_text())["client"]["captures"]["niard-reset-menu"]
            check("client_and_server_use_the_same_production_artifact", report["jarSha256"],
                  digest(Path(capture["loadedMods"]["enderio"]["file"])), results)
        finally:
            stop_client(client)
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def reopened(server, results):
    packs(server, results, True)
    for machine in MACHINES:
        server.assert_state(f"{machine.name}_factory_defaults_survive_dedicated_restart",
                            f"{machine.blank()} if block {machine.source} minecraft:air {NO_LOOSE_INPUTS}", results)
    (player,) = (server.directory / WORLD / "playerdata").glob("*.dat")
    save = read_data_file(player)
    check("the_saved_player_keeps_only_the_original_pickaxe_and_no_experience",
          ([(0, "minecraft:diamond_pickaxe", 1)], 0),
          ([(stack["Slot"], stack["id"], stack["Count"]) for stack in save["Inventory"]], save["XpTotal"]), results)


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
    with (directory / "server.properties").open("a") as properties:
        properties.write(f"initial-enabled-packs=vanilla,{PACK}\n")
    report = {"passed": False, "forge": FORGE, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}

    def fresh(server, results):
        setup(server, results)
        exercise(args, server, results, report)

    try:
        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", reopened)
        report["passed"] = True
    finally:
        result = directory / "machine-resets-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Factory resets passed={report['passed']}, {len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
