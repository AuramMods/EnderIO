#!/usr/bin/env python3
"""Check native item and fluid routing with LaserIO filter items and cards."""

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_cc_tweaked import face
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_spawners import prepare_profile, run_stage


@dataclass(frozen=True)
class Lane:
    kind: str
    z: int

    def pos(self, x):
        return f"{x} 80 {self.z}"

    @property
    def source(self):
        return self.pos(3278)

    @property
    def target(self):
        return self.pos(3281)


ITEM_FILTER = Lane("item", 4)
ITEM_CARD = Lane("item", 7)
FLUID_FILTER = Lane("fluid", 10)
FLUID_CARD = Lane("fluid", 13)
LANES = (ITEM_FILTER, ITEM_CARD, FLUID_FILTER, FLUID_CARD)
PAPERS = ('minecraft:paper{PortLaser:1}', 'minecraft:paper{PortLaser:2}', 'minecraft:iron_ingot')
COUNTS = (5, 7, 3)
FLUID_AMOUNT = 1000


def saved_item(item, tag=None, *, slot=False):
    tag_field = f",tag:{tag}" if tag else ""
    slot_field = "Slot:0," if slot else ""
    return f'{{{slot_field}id:"{item}",Count:1b{tag_field}}}'


def basic_filter(sample, *, allow, compare, nested=False):
    return saved_item("laserio:filter_basic", f'{{allowList:{int(allow)}b,compareNBT:{int(compare)}b,'
                      f'inv:{{Size:15,Items:[{sample}]}}}}', slot=nested)


def card(kind, inserted=None):
    tag = f'{{inv:{{Size:2,Items:[{inserted}]}}}}' if inserted else None
    return saved_item(f"laserio:card_{kind}", tag)


def fluid_stack(name, amount, marker):
    return f'{{FluidName:"minecraft:{name}",Amount:{amount},Tag:{{PortLaser:{marker}}}}}'


def portable_water_sample():
    return saved_item("enderio:fluid_tank", '{"enderio:data":{"enderio:item_fluid_content":'
                      + fluid_stack("water", 500, 1) + '}}', slot=True)


def filter_fixtures():
    paper = saved_item("minecraft:paper", "{PortLaser:1}", slot=True)
    return ((ITEM_FILTER, False, basic_filter(paper, allow=True, compare=True)),
            (ITEM_CARD, True, card("item", basic_filter(paper, allow=False, compare=True, nested=True))),
            (FLUID_FILTER, False, basic_filter(saved_item("minecraft:water_bucket", slot=True), allow=True, compare=False)),
            (FLUID_CARD, True, card("fluid", basic_filter(portable_water_sample(), allow=False, compare=True, nested=True))))


def connection(lane, insert):
    item_flags = ',is_round_robin:0b,is_self_feed:0b,priority:0' if lane.kind == "item" else ""
    return (f'{{type:"enderio:{lane.kind}",is_insert:{int(insert)}b,is_extract:{int(not insert)}b,'
            f'insert_channel:"green",extract_channel:"green",extract_redstone_channel:"red",'
            f'extract_redstone_control:"{"never_active" if insert else "always_active"}"{item_flags}}}')


def configure(server, lane, *, insert, filter_stack=None):
    pos, side = (lane.pos(3280), "east") if insert else (lane.pos(3279), "west")
    path = f'{pos} Connections[0][{{Side:"{side}"}}]'
    server.command(f"data modify block {path}.Config set value {connection(lane, insert)}")
    if filter_stack:
        inventory = f'[{{}},{filter_stack}]' if insert else f'[{filter_stack}]'
        server.command(f"data modify block {path}.Inventory set value {inventory}")


def filter_slot(lane, *, insert):
    pos, side = (lane.pos(3280), "east") if insert else (lane.pos(3279), "west")
    return f'{pos} Connections[0][{{Side:"{side}"}}].Inventory[{int(insert)}]'


def topology(lane):
    return " ".join((face(lane.pos(3279), "west"), face(lane.pos(3279), "east", "connected_conduit"),
                     face(lane.pos(3280), "west", "connected_conduit"), face(lane.pos(3280), "east")))


def prepare(server, results, lasers):
    server.command("gamerule doMobSpawning false")
    server.command("forceload add 3264 0 3295 15")
    server.assert_state("both_fixture_chunks_are_loaded", "if loaded 3279 80 4 if loaded 3280 80 13", results)
    for lane in LANES:
        endpoint = "enderio:fluid_tank" if lane.kind == "fluid" else "minecraft:barrel"
        server.command(f"setblock {lane.source} {endpoint}")
        server.command(f"setblock {lane.target} {endpoint}")
        for x in (3279, 3280):
            server.command(f'setblock {lane.pos(x)} enderio:conduit{{Conduits:["enderio:{lane.kind}"],Connections:[[]]}}')
        server.assert_state(f"cross_chunk_{lane.kind}_route_{lane.z}", topology(lane), results)
    for lane, insert, stack in filter_fixtures():
        configure(server, lane, insert=not insert)
        configure(server, lane, insert=insert, filter_stack=stack if lasers else None)


def exercise_menus(args, server, results, report):
    player = "EnderIOQA"
    server.command("fill 3277 79 2 3282 79 16 minecraft:stone")
    server.command("gamerule spawnRadius 0")
    server.command("setworldspawn 3278 80 3")
    output = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "laserio-filters", "--output", str(output), "--server", f"127.0.0.1:{args.port}",
               "--timeout", "185"]
    summary_path = output / "summary.json"
    with (server.directory / "laserio-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            for lane, insert, stack in filter_fixtures():
                x, side = (3280, "east") if insert else (3279, "west")
                server.command(f"tp {player} {x + 0.5} 80 {lane.z + 2.5} 180 22")
                (output / f"server-menu-{lane.z}").touch()
                slot = filter_slot(lane, insert=insert)
                wait_signal(client, output, f"removed-{lane.z}")
                server.assert_state(f"client_removes_laserio_stack_{lane.z}", f"unless data block {slot}.id", results)
                (output / f"server-removed-{lane.z}").touch()
                wait_signal(client, output, f"installed-{lane.z}")
                stored_stack = stack.replace(",Count:1b", ",Count:1", 1)
                server.assert_state(f"client_reinstalls_original_laserio_stack_{lane.z}",
                                    f'if data block {slot}.id if data block {lane.pos(x)} '
                                    f'Connections[0][{{Side:"{side}",Inventory:[{stored_stack}]}}]', results)
                (output / f"server-installed-{lane.z}").touch()
            if client.wait(timeout=35) != 0:
                raise AssertionError(f"LaserIO conduit client failed; see {output}")
            mods = json.loads(summary_path.read_text())["client"]["captures"]["laserio-card-in-fluid-conduit"]["loadedMods"]
            for name, sha256 in report["jars"].items():
                if digest(Path(mods[name]["file"])) != sha256:
                    raise AssertionError(f"Client and server {name} jars differ")
        finally:
            stop_client(client)
            report["client"] = json.loads(summary_path.read_text()) if summary_path.exists() else None


def feed_fluid(server, pos, name="water", marker=2):
    blob = fluid_stack(name, FLUID_AMOUNT, marker)
    server.command(f'data merge block {pos} {{Fluids:{{Tanks:[{{Index:0,Contents:{blob}}}]}}}}')


def saved_fluid(pos, name, amount, marker):
    return f'if data block {pos} Fluids.Tanks[{{Index:0,Contents:{fluid_stack(name, amount, marker)}}}]'


def empty_fluid(pos):
    return f"unless data block {pos} Fluids.Tanks[{{Index:0}}].Contents.FluidName"


def item_state(pos, counts):
    examples = ['id:"minecraft:paper",tag:{PortLaser:1}', 'id:"minecraft:paper",tag:{PortLaser:2}',
                'id:"minecraft:iron_ingot"']
    return " ".join(f'if data block {pos} Items[{{{item},Count:{count}b}}]' if count
                    else f'unless data block {pos} Items[{{{item}}}]' for item, count in zip(examples, counts))


def transfers(server, results, lasers, rounds):
    for lane in (ITEM_FILTER, ITEM_CARD):
        for i, (item, count) in enumerate(zip(PAPERS, COUNTS)):
            server.command(f"item replace block {lane.source} container.{i} with {item} {count}")
    for lane in (FLUID_FILTER, FLUID_CARD):
        feed_fluid(server, lane.source)
    failures = []
    for lane, keep, label in ((ITEM_FILTER, (0,), "loose_laser_filter_checks_exact_item_nbt"),
                              (ITEM_CARD, (1, 2), "laser_item_card_denies_only_the_exact_item_nbt"),
                              (FLUID_FILTER, None, "loose_laser_filter_allows_tagged_water_by_fluid_type"),
                              (FLUID_CARD, None, "laser_fluid_card_allows_differently_tagged_water")):
        if keep is not None:
            accepted = keep if lasers else (0, 1, 2)
            source = [0 if i in accepted else count for i, count in enumerate(COUNTS)]
            target = [count * rounds if i in accepted else 0 for i, count in enumerate(COUNTS)]
            expected = f"{item_state(lane.source, source)} {item_state(lane.target, target)}"
        else:
            expected = f"{empty_fluid(lane.source)} {saved_fluid(lane.target, 'water', FLUID_AMOUNT * rounds, 2)}"
        if not lasers:
            label = f"core_{lane.kind}_route_{lane.z}_works_without_laserio"
        try:
            server.assert_state(f"{label}_{rounds}", expected, results, timeout=9)
        except AssertionError as error:
            failures.append(str(error))
            for pos in (lane.source, lane.pos(3279), lane.pos(3280), lane.target):
                server.command(f"data get block {pos}")
    if failures:
        raise AssertionError("\n".join(failures))


def time_in_world(server):
    reply = server.command("time query gametime")
    match = re.search(r"The time is (\d+)", reply)
    if not match:
        raise AssertionError(f"No game time in {reply}")
    return int(match.group(1))


def exercise_rejections(server, results):
    # Empty both receivers so incompatibility with a nonempty tank cannot reject the inputs.
    for lane, name, marker in ((FLUID_FILTER, "lava", 4), (FLUID_CARD, "water", 1)):
        server.command(f'data merge block {lane.target} {{Fluids:{{Tanks:[]}}}}')
        feed_fluid(server, lane.source, name, marker)
    until = time_in_world(server) + 50
    deadline = time.monotonic() + 20
    while time_in_world(server) < until:
        if time.monotonic() > deadline:
            raise AssertionError("Server did not tick fifty times with both fluids available")
        time.sleep(0.2)
    server.assert_state("loose_water_filter_rejects_lava_with_an_empty_receiver",
                        f"{saved_fluid(FLUID_FILTER.source, 'lava', FLUID_AMOUNT, 4)} {empty_fluid(FLUID_FILTER.target)}",
                        results)
    server.assert_state("laser_fluid_card_denies_exact_water_nbt_with_an_empty_receiver",
                        f"{saved_fluid(FLUID_CARD.source, 'water', FLUID_AMOUNT, 1)} {empty_fluid(FLUID_CARD.target)}",
                        results)


def exercise_blank_cards(server, results):
    for lane in (ITEM_CARD, FLUID_CARD):
        server.command(f"data modify block {filter_slot(lane, insert=True)} set value {card(lane.kind)}")
    first, second, iron = COUNTS
    server.assert_state("item_card_without_a_filter_allows_the_previously_denied_paper",
                        f"{item_state(ITEM_CARD.source, (0, 0, 0))} {item_state(ITEM_CARD.target, (first, 2 * second, 2 * iron))}",
                        results)
    server.assert_state("fluid_card_without_a_filter_allows_the_previously_denied_water",
                        f"{empty_fluid(FLUID_CARD.source)} {saved_fluid(FLUID_CARD.target, 'water', FLUID_AMOUNT, 1)}",
                        results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--laserio", type=Path)
    parser.add_argument("--patchouli", type=Path)
    parser.add_argument("--launch", type=Path, help="Verify both kinds of conduit menu with an isolated LaserIO client")
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    if bool(args.laserio) != bool(args.patchouli):
        parser.error("install LaserIO and Patchouli together for the integration profile")
    if args.launch and not args.laserio:
        parser.error("--launch exercises the profile with LaserIO installed")

    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    jars = {"enderio": digest(directory / "mods/enderio.jar")}
    if args.laserio:
        for name, source in (("laserio", args.laserio), ("patchouli", args.patchouli)):
            target = directory / "mods" / f"{name}.jar"
            shutil.copyfile(source, target)
            jars[name] = digest(target)
    report = {"passed": False, "forge": FORGE, "jars": jars, "assertions": [], "servers": []}
    try:
        def fresh(server, results):
            prepare(server, results, args.laserio)
            if args.launch:
                exercise_menus(args, server, results, report)
            transfers(server, results, args.laserio, 1)

        def reopened(server, results):
            if args.laserio:
                server.assert_state("all_four_filter_items_survive_restart", " ".join(
                    f"if data block {filter_slot(lane, insert=lane in (ITEM_CARD, FLUID_CARD))}"
                    for lane in LANES), results)
            transfers(server, results, args.laserio, 2)
            if args.laserio:
                exercise_rejections(server, results)
                exercise_blank_cards(server, results)

        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", reopened)
        report["passed"] = True
    finally:
        path = directory / "laserio-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"LaserIO filters passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
