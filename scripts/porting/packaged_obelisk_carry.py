#!/usr/bin/env python3
"""Verify survival mining, carriage and replanting of six packaged Forge obelisks."""

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import re
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_obelisks import RAIN, XP_FLUID, write_predicates
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage


PLAYER = "EnderIOQA"
REDSTONE = 'RedstoneControl:"never_active"'
XP_IO = '{Modes:{north:"disable",south:"pull"}}'
ROCKET = '{Slot:0,id:"minecraft:firework_rocket",Count:%d,tag:{Fireworks:{Flight:1b},PortFixture:77}}'
SOUL_FILTER = ('{Slot:0,id:"enderio:basic_soul_filter",Count:1,tag:{"enderio:data":{"enderio:soul_filter":'
               '{entities:[{index:4,value:{entity_type:"minecraft:pig",entity_tag:{PortFixture:77}}}],isInvert:1b,nbt:1b}}}}')


@dataclass(frozen=True)
class Obelisk:
    name: str
    x: int
    charge: int = 0
    radius: int = 0
    visible: bool = False
    fluid: str = ""
    amount: int = 0

    @property
    def id(self):
        return f"enderio:{self.name}_obelisk"

    @property
    def source(self):
        return f"{self.x} 80 8"

    @property
    def destination(self):
        return f"{self.x} 80 12"


OBELISKS = (
    Obelisk("inhibitor", 2310, 6101, 5, True),
    Obelisk("aversion", 2316, 7302, 4),
    Obelisk("relocator", 2322, 8503, 0, True),
    Obelisk("attractor", 2328, 9704, 3, True),
    Obelisk("xp", 2334, fluid=XP_FLUID, amount=1573),
    Obelisk("weather", 2340, fluid=RAIN, amount=1237),
)
WEATHER = OBELISKS[-1]


def contents(spec, rockets=2):
    if spec.name == "weather":
        return f'{{Size:1,Items:[{ROCKET % rockets}]}}'
    if spec.charge:
        filtered = spec.name != "inhibitor"
        capacitor = f'{{Slot:{int(filtered)},id:"enderio:basic_capacitor",Count:1}}'
        ingredients = f"{SOUL_FILTER},{capacitor}" if filtered else capacitor
        return f'{{Size:{1 + int(filtered)},Items:[{ingredients}]}}'
    return None


def range_data(spec):
    return f'{{range:{spec.radius},isVisible:{int(spec.visible)}b}}'


def fluid_data(spec, amount):
    return f'{{FluidName:"{spec.fluid}",Amount:{amount},Tag:{{PortFixture:77}}}}'


def block_data(spec, *, amount=None, rockets=2, redstone=REDSTONE):
    fields = [redstone]
    if (inventory := contents(spec, rockets)) is not None:
        fields.append(f"Items:{inventory}")
    if spec.charge:
        fields += [f"EnergyStored:{spec.charge}", f"ActionRange:{range_data(spec)}"]
    if spec.fluid:
        fluid = fluid_data(spec, spec.amount if amount is None else amount)
        fields.append(f"Fluids:{{Tanks:[{{Index:0,Contents:{fluid}}}]}}")
    if spec.name == "xp":
        fields.append(f"IOConfig:{XP_IO}")
    return "{" + ",".join(fields) + "}"


def carried_stack(spec):
    fields = ['"enderio:redstone_control":"never_active"']
    if (inventory := contents(spec)) is not None:
        fields.append(f'"enderio:container":{inventory}')
    if spec.charge:
        fields += [f'"enderio:energy":{spec.charge}', f'"enderio:action_range":{range_data(spec)}']
    if spec.fluid:
        fields.append(f'"enderio:item_fluid_content":{fluid_data(spec, spec.amount)}')
    if spec.name == "xp":
        fields.append(f'"enderio:io_config":{XP_IO}')
    return f'{{id:"{spec.id}",Count:1b,tag:{{"enderio:data":{{{",".join(fields)}}}}}}}'


def setup(server, results):
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    server.command("forceload add 2304 0 2351 15")
    server.assert_state("all_obelisk_fixture_chunks_loaded", f"if loaded {OBELISKS[0].source} if loaded {WEATHER.destination}", results)
    server.command("fill 2307 79 5 2343 79 15 minecraft:stone")
    server.command("setworldspawn 2310 80 11")
    server.command("weather clear 10000")
    for spec in OBELISKS:
        server.command(f"setblock {spec.source} {spec.id}[facing=south]")
        server.command(f"data merge block {spec.source} {block_data(spec)}")
        server.assert_state(f"{spec.name}_source_has_exact_inputs", f"if data block {spec.source} {block_data(spec)}", results)

    server.command(f'data merge block {WEATHER.source} {{RedstoneControl:"always_active"}}')
    for _ in range(40):
        reply = server.command(f"data get block {WEATHER.source} Task.ProgressMade")
        progress = re.search(r"following block data: (\d+)", reply)
        if progress and int(progress.group(1)) >= 12:
            break
    else:
        raise AssertionError(f"The source Weather Obelisk did not begin its rain job; see {server.log_path}")
    server.command(f"data merge block {WEATHER.source} {{{REDSTONE}}}")
    server.assert_state("weather_source_has_an_in_flight_rain_job",
                        f'if data block {WEATHER.source} {{Task:{{RecipeId:"enderio:rain",ProgressRequired:600}}}} '
                        f'if data block {WEATHER.source} {block_data(WEATHER)}', results)


def replant(server, client, output, spec, results):
    drop = f'@e[type=minecraft:item,x={spec.x + 0.5},y=80,z=8.5,distance=..3,nbt={{Item:{{id:"{spec.id}"}}}}]'
    nearest = drop[:-1] + ",sort=nearest,limit=1]"
    server.command(f"tp {PLAYER} {spec.x + 0.5} 80 11.5 180 22")
    (output / f"server-mine-{spec.name}").touch()
    wait_signal(client, output, f"mined-{spec.name}")
    server.command(f"data get entity {nearest} Item")
    server.assert_state(f"{spec.name}_native_drop_exports_exact_state",
                        f"if block {spec.source} minecraft:air if data entity {nearest} {{Item:{carried_stack(spec)}}}", results)
    if spec.name == "weather":
        server.assert_state("weather_drop_keeps_reagents_with_the_normal_fresh_job_policy",
                            f"unless data entity {nearest} Item.tag.BlockEntityTag.Task", results)
    server.command(f"execute at {nearest} run tp {PLAYER} ~ ~ ~")
    floor_items = f"@e[type=minecraft:item,x={spec.x + 0.5},y=80,z=8.5,distance=..3]"
    server.assert_state(f"{spec.name}_pickup_moves_one_machine_and_leaves_no_loose_inputs",
                        f"unless entity {floor_items} if data entity {PLAYER} Inventory[{carried_stack(spec)}]", results)
    server.command(f"tp {PLAYER} {spec.x + 0.5} 80 14.5 180 22")
    (output / f"server-place-{spec.name}").touch()
    wait_signal(client, output, f"placed-{spec.name}")
    server.command(f"data get block {spec.destination}")
    server.assert_state(f"{spec.name}_native_placement_consumes_item_and_restores_state",
                        f'if block {spec.destination} {spec.id} if block {spec.source} minecraft:air '
                        f'if data block {spec.destination} {block_data(spec)} '
                        f'unless data entity {PLAYER} Inventory[{{id:"{spec.id}"}}]', results)
    (output / f"server-verified-{spec.name}").touch()


def exercise_client(args, server, report):
    output = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "obelisk-carry", "--output", str(output), "--server", f"127.0.0.1:{args.port}", "--timeout", "220"]
    with (server.directory / "obelisk-carry-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            server.command(f'item replace entity {PLAYER} hotbar.0 with minecraft:diamond_pickaxe'
                           '{Enchantments:[{id:"minecraft:efficiency",lvl:5s}]}')
            for spec in OBELISKS:
                replant(server, client, output, spec, report["assertions"])
            if client.wait(timeout=25) != 0:
                raise AssertionError(f"Obelisk carry client failed; see {output}")
            summary = json.loads((output / "summary.json").read_text())
            frame = summary["client"]["captures"]["replanted-weather-obelisk"]
            if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Client and server Ender IO jars differ")
        finally:
            stop_client(client)
            summary = output / "summary.json"
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def reopened(server, results):
    for spec in OBELISKS:
        server.assert_state(f"{spec.name}_replant_survives_server_restart",
                            f"if block {spec.source} minecraft:air if data block {spec.destination} {block_data(spec)}", results)
    server.command(f"data get block {WEATHER.destination} Task")
    server.command("scoreboard objectives add port_weather dummy")
    server.command(f"execute store result score #ticks port_weather run data get block {WEATHER.destination} Task.ProgressMade")
    server.assert_state("weather_replanted_job_starts_fresh",
                        "if score #ticks port_weather matches 0 if predicate port_qa:clear", results)
    server.command(f'data merge block {WEATHER.destination} {{RedstoneControl:"always_active"}}')
    end = block_data(WEATHER, amount=237, rockets=1, redstone='RedstoneControl:"always_active"')
    server.assert_state("replanted_weather_spends_exactly_one_tagged_bucket_and_one_rocket",
                        f"if data block {WEATHER.destination} {end} if predicate port_qa:rain "
                        f"unless data block {WEATHER.destination} Task "
                        "if entity @e[type=minecraft:firework_rocket,x=2340.5,y=81,z=12.5,distance=..25]", results, timeout=57)


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
    write_predicates(directory)
    report = {"passed": False, "jarSha256": digest(directory / "mods/enderio.jar"), "assertions": [], "servers": []}
    try:
        def fresh(server, results):
            setup(server, results)
            exercise_client(args, server, report)

        run_stage(args, directory, report, "fresh", fresh, startup_timeout=240)
        run_stage(args, directory, report, "reopened", reopened, startup_timeout=240)
        report["passed"] = True
    finally:
        path = directory / "obelisk-carry-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Obelisk carry passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
