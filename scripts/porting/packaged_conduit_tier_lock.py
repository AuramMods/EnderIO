#!/usr/bin/env python3
"""Verify fixed-tier conduit routes and the two live networks left by a middle upgrade."""

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import shutil
import sys

sys.dont_write_bytecode = True
from packaged_cc_tweaked import face
from packaged_item_progression import bundle
from packaged_laserio import connection
from packaged_legacy_transfer_filters import wait_ticks
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage


CHUNKS = "3344 0 3375 15"
JOINED = "connected_conduit"
UNJOINED = "none"


def pos(x, z):
    return f"{x} 80 {z}"


@dataclass(frozen=True)
class Route:
    kind: str
    z: int
    tiers: tuple[str, str, str]
    batch: int

    @property
    def source(self):
        return pos(3358, self.z)

    @property
    def target(self):
        return pos(3361, self.z)

    @property
    def pieces(self):
        return pos(3359, self.z), pos(3360, self.z)


ROUTES = (
    Route("energy", 1, ("energy", "enhanced_energy", "ender_energy"), 12_000),
    Route("fluid", 5, ("fluid", "pressurized_fluid", "ender_fluid"), 450),
    Route("chemical", 9, ("chemical", "pressurized_chemical", "ender_chemical"), 1_450),
)
ITEM_ROUTE = Route("item", 13, ("item", "enhanced_item", "ender_item"), 8)
ITEM_POSITIONS = tuple(pos(x, ITEM_ROUTE.z) for x in range(3358, 3363))
ITEM_PAIRS = ((pos(3358, 12), pos(3359, 14)), (pos(3361, 12), pos(3362, 14)))


def place(server, at, variant, previous=None):
    expected = f"Native conduit placed enderio:{variant}"
    if previous:
        expected += f" and returned enderio:{previous}"
    output = server.command(f"port_place_conduit {at} enderio:{variant}")
    if not any(line.rstrip().endswith(expected) for line in output.splitlines()):
        raise AssertionError(output)


def endpoint(server, at, side, route, insert):
    server.command(f'data modify block {at} Connections[0][{{Side:"{side}"}}].Config '
                   f'set value {connection(route, insert)}')


def create_endpoints(server, route):
    if route.kind == "energy":
        for at, block, slots, slot, side, mode in (
                (route.source, "stirling_generator", 2, 1, "east", "push"),
                (route.target, "alloy_smelter", 5, 4, "west", "pull")):
            server.command(f'setblock {at} enderio:{block}{{IOConfig:{{Modes:{{{side}:"{mode}"}}}},'
                           f'Items:{{Size:{slots},Items:[{{Slot:{slot},id:"enderio:basic_capacitor",Count:1b}}]}}}}')
    elif route.kind == "fluid":
        for at in (route.source, route.target):
            server.command(f"setblock {at} enderio:fluid_tank")
    else:
        for at, mode in ((route.source, 4), (route.target, 1)):
            server.command(f"setblock {at} mekanism:ultimate_chemical_tank")
            sides = ",".join(f"side{i}:{mode}" for i in range(6))
            server.command(f"data modify block {at} componentConfig.config2 set value {{{sides}}}")


def liquid(amount):
    return f'{{FluidName:"minecraft:water",Amount:{amount},Tag:{{PortTier:164}}}}'


def hydrogen(amount):
    return f'{{Tank:0b,stored:{{gasName:"mekanism:hydrogen",amount:{amount}L}}}}'


def supply(server, route):
    if route.kind == "energy":
        server.command(f"data merge block {route.source} {{EnergyStored:{route.batch}}}")
    elif route.kind == "fluid":
        server.command(f"data modify block {route.source} Fluids.Tanks set value [{{Index:0,Contents:{liquid(route.batch)}}}]")
    else:
        server.command(f"data modify block {route.source} GasTanks set value [{hydrogen(route.batch)}]")


def balance(route, source, target):
    checks = []
    for at, amount in ((route.source, source), (route.target, target)):
        if route.kind == "energy":
            checks.append(f"if data block {at} {{EnergyStored:{amount}}}")
        elif route.kind == "fluid":
            checks.append(f"if data block {at} Fluids.Tanks[{{Index:0,Contents:{liquid(amount)}}}]" if amount else
                          f"unless data block {at} Fluids.Tanks[0].Contents.FluidName")
        else:
            checks.append(f"if data block {at} GasTanks[{hydrogen(amount)}]" if amount else f"unless data block {at} GasTanks[0]")
    return " ".join(checks)


def topology(route, first, second):
    left, right = route.pieces
    status = JOINED if first == second else UNJOINED
    return " ".join((bundle(left, first), bundle(right, second), face(left, "west"), face(right, "east"),
                     face(left, "east", status), face(right, "west", status)))


def exercise_route(server, results, route):
    create_endpoints(server, route)
    left, right = route.pieces
    first = route.tiers[0]
    for at in route.pieces:
        place(server, at, first)
    state = topology(route, first, first)
    server.assert_state(f"{route.kind}_matching_basic_tiers_join", state, results, timeout=2)
    if route.kind != "energy":
        endpoint(server, left, "west", route, False)
        endpoint(server, right, "east", route, True)
    supply(server, route)
    server.assert_state(f"{route.kind}_basic_route_transfers_exact_batch", f"{state} {balance(route, 0, route.batch)}", results)

    for index, upper in enumerate(route.tiers[1:], 1):
        place(server, left, upper, first)
        supply(server, route)
        wait_ticks(server, 24)
        state = topology(route, upper, first)
        server.assert_state(f"{route.kind}_{upper}_split_retains_pending_batch",
                            f"{state} {balance(route, route.batch, route.batch * index)}", results)
        place(server, right, upper, first)
        state = topology(route, upper, upper)
        server.assert_state(f"{route.kind}_{upper}_matching_route_delivers_pending_batch",
                            f"{state} {balance(route, 0, route.batch * (index + 1))}", results)
        first = upper


def item_split_topology():
    tiers = ("item", "item", "enhanced_item", "item", "item")
    east = (JOINED, UNJOINED, UNJOINED, JOINED)
    conditions = [bundle(at, tier) for at, tier in zip(ITEM_POSITIONS, tiers)]
    for left, right, status in zip(ITEM_POSITIONS, ITEM_POSITIONS[1:], east):
        conditions.extend((face(left, "east", status), face(right, "west", status)))
    return " ".join(conditions)


def item_batches(server, results, name, total):
    checks = [item_split_topology()]
    for index, (source, target) in enumerate(ITEM_PAIRS):
        tag = f"{{SplitSide:{index}}}"
        server.command(f"item replace block {source} container.0 with minecraft:paper{tag} {ITEM_ROUTE.batch}")
        checks.extend((f"unless data block {source} Items[0]", f"unless data block {target} Items[1]",
                       f'if data block {target} Items[{{Slot:0b,id:"minecraft:paper",Count:{total}b,tag:{tag}}}]'))
    server.assert_state(name, " ".join(checks), results)


def split_items(server, results):
    for pair in ITEM_PAIRS:
        for at in pair:
            server.command(f"setblock {at} minecraft:barrel")
    for at in ITEM_POSITIONS:
        place(server, at, "item")
    for first, last in ((ITEM_POSITIONS[0], ITEM_POSITIONS[1]), (ITEM_POSITIONS[3], ITEM_POSITIONS[4])):
        endpoint(server, first, "north", ITEM_ROUTE, False)
        endpoint(server, last, "south", ITEM_ROUTE, True)
    place(server, ITEM_POSITIONS[2], "enhanced_item", "item")
    item_batches(server, results, "both_newly_separated_item_networks_keep_ticking", ITEM_ROUTE.batch)


def load(server, results):
    server.command(f"forceload add {CHUNKS}")
    server.assert_state("both_route_chunks_loaded", f"if loaded {pos(3359, 1)} if loaded {pos(3360, 1)}", results)


def exercise(server, results):
    load(server, results)
    server.command("fill 3357 79 0 3363 79 15 minecraft:stone")
    failures = []
    for route in ROUTES:
        try:
            exercise_route(server, results, route)
        except AssertionError as error:
            failures.append(f"{route.kind}: {error}")
    try:
        split_items(server, results)
    except AssertionError as error:
        failures.append(f"item: {error}")
    if failures:
        raise AssertionError("\n".join(failures))
    server.command(f"forceload remove {CHUNKS}")
    server.assert_state("both_route_chunks_evicted", f"unless loaded {pos(3359, 1)} unless loaded {pos(3360, 1)}",
                        results, timeout=35)
    load(server, results)
    item_batches(server, results, "both_item_networks_transfer_after_chunk_reload", 2 * ITEM_ROUTE.batch)


def reopen(server, results):
    load(server, results)
    for route in ROUTES:
        high = route.tiers[-1]
        state = topology(route, high, high)
        server.assert_state(f"reopened_{route.kind}_route_keeps_its_exact_balance",
                            f"{state} {balance(route, 0, 3 * route.batch)}", results)
        supply(server, route)
        server.assert_state(f"reopened_{route.kind}_route_transfers_a_fresh_batch",
                            f"{state} {balance(route, 0, 4 * route.batch)}", results)
    item_batches(server, results, "both_split_item_networks_transfer_after_restart", 3 * ITEM_ROUTE.batch)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for path in ("java", "forge-server", "jar", "mekanism", "probe"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")

    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    for mod in (args.mekanism, args.probe):
        shutil.copyfile(mod, directory / "mods" / mod.name)
    config = directory / "config/enderio"
    config.mkdir(parents=True)
    (config / "conduits-common.toml").write_text("[conduits]\n" + "".join(
        f"canMix{family}ConduitTiers = false\n" for family in ("Item", "Fluid", "Energy")))
    (config / "modded-conduits-common.toml").write_text("[ae2]\nnormalPowerUsagePerTick = 0.0\n"
        "densePowerUsagePerTick = 0.0\n[mekanism]\ncanMixChemicalConduitTiers = false\n")
    report = {"passed": False, "assertions": [], "servers": [],
              "jarHashes": {mod.name: digest(mod) for mod in sorted((directory / "mods").glob("*.jar"))}}
    try:
        run_stage(args, directory, report, "exercise", exercise)
        run_stage(args, directory, report, "reopened", reopen)
        report["passed"] = True
    finally:
        path = directory / "conduit-tier-lock-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Conduit tier lock passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
