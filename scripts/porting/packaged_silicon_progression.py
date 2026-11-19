#!/usr/bin/env python3
"""Route the three stock silicon sources into native Slice'N'Splice jobs on Forge."""

import argparse
from collections import Counter
from dataclasses import dataclass
import json
from pathlib import Path
import re
import sys

sys.dont_write_bytecode = True
from packaged_sag_progression import charge, data_read, mill
from packaged_server_smoke import ROOT, digest
from packaged_slicer import (AXE, CASES, COST, PAID, SHEARS, SILICON, base_state, feed, finished, headless_missing_tool,
                             partial, prepare_area, prepare_machine, run_client, tools_for)
from packaged_spawners import prepare_profile, run_stage


@dataclass(frozen=True)
class Route:
    case: tuple
    input: str
    count: int
    silicon_per_roll: int
    byproducts: tuple

    @property
    def x(self):
        return self.case[0]

    @property
    def name(self):
        return self.case[1]

    def sag(self, y):
        return f"{self.x + 1} {y} 8"

    @property
    def lock(self):
        return f"{self.x + 1} 80 7"

    def storage(self):
        return f"if block {self.sag(80)} minecraft:hopper[facing=west,enabled=false]"


ROUTES = (
    Route(CASES[0], "minecraft:clay", 8, 2, (("minecraft:clay_ball", 2, 3),)),
    Route(CASES[1], "minecraft:red_sand", 16, 1, ()),
    Route(CASES[4], "minecraft:deepslate_redstone_ore", 6, 1,
          (("minecraft:redstone", 8, 9), ("minecraft:cobblestone", 0, 1))),
)
RETAINED_FE = 137
GRIND_COST = 2400


def prepare_route(server, results, route):
    prepare_machine(server, route.x)
    counts = Counter(item for item in route.case[2] if item != SILICON)
    axe_damage, shears_damage = tools_for(route.case)
    stock = list(counts.items()) + [(f"{AXE}{{Damage:{axe_damage},PortSlicer:159}}", 1),
                                   (f"{SHEARS}{{Damage:{shears_damage},PortSlicer:159}}", 1)]
    for slot, (item, count) in enumerate(stock):
        server.command(f"item replace block {feed(route.x)} container.{slot} with {item} {count}")
    server.command(f"setblock {route.lock} minecraft:redstone_block")
    server.command(f"setblock {route.sag(80)} minecraft:hopper[facing=west]")
    server.command(f'setblock {route.sag(81)} enderio:sag_mill[facing=south]{{IOConfig:{{Modes:{{up:"pull",down:"push"}}}},'
                   'Items:{Items:[{Slot:6,id:"enderio:octadic_capacitor",Count:1}]}}')
    server.command(f"setblock {route.sag(82)} minecraft:hopper[facing=down]")
    server.assert_state(f"{route.name}_starts_with_empty_silicon_slots_and_a_locked_delivery_hopper",
                        f"{route.storage()} unless data block {route.sag(80)} Items[0] "
                        f"unless data block {mill(route.x)} Items.Items[{{Slot:3}}] "
                        f"unless data block {mill(route.x)} Items.Items[{{Slot:5}}]", results)


def hopper_contents(server, route):
    # All stock outputs are ordinary item stacks without custom tags.
    stacks = re.findall(r"\{([^{}]*)\}", data_read(server, route.sag(80), "Items"))
    inventory = Counter()
    for stack in stacks:
        fields = dict(part.strip().split(":", 1) for part in stack.split(","))
        inventory[fields["id"].strip().strip('"')] += int(fields["Count"].strip().removesuffix("b"))
    return dict(inventory)


def start_grind(server, results, route, count):
    server.command(f"item replace block {route.sag(82)} container.0 with {route.input} {count}")
    server.assert_state(f"{route.name}_feeds_{count}_raw_blocks_into_the_stock_sag_mill",
                        f'if data block {route.sag(81)} Items.Items[{{Slot:0,id:"{route.input}",Count:{count}}}] '
                        f"unless data block {route.sag(82)} Items[0]", results)
    server.command(f"data merge block {route.sag(81)} {{EnergyStored:{count * GRIND_COST + RETAINED_FE}}}")


def read_grind(server, results, route, count):
    server.assert_state(f"{route.name}_spends_{GRIND_COST}_fe_per_block_and_exports_its_rolls",
                        f"if data block {route.sag(81)} {{EnergyStored:{RETAINED_FE}}} "
                        f"unless data block {route.sag(81)} Task "
                        + " ".join(f"unless data block {route.sag(81)} Items.Items[{{Slot:{slot}}}]" for slot in range(5)),
                        results, timeout=95)
    output = hopper_contents(server, route)
    silicon = output.get(SILICON, 0)
    if silicon > count * route.silicon_per_roll or silicon % route.silicon_per_roll:
        raise AssertionError(f"Unexpected silicon roll from {count} {route.input}: {output}")
    for item, low, high in route.byproducts:
        if not low * count <= output.get(item, 0) <= high * count:
            raise AssertionError(f"Unexpected byproduct from {count} {route.input}: {output}")
    if set(output) - {SILICON, *(item for item, _, _ in route.byproducts)}:
        raise AssertionError(f"Unexpected hopper contents from {route.input}: {output}")
    return output


def load_and_pause(server, results, route, output):
    server.command(f"setblock {route.lock} minecraft:air")
    server.assert_state(f"{route.name}_hoppers_load_the_original_silicon_and_all_other_stock_inputs",
                        f"{base_state(route.case)} unless data block {feed(route.x)} Items[0]", results)
    server.command(f"setblock {route.lock} minecraft:redstone_block")
    remaining = output.copy()
    remaining[SILICON] -= 2
    remaining = {item: count for item, count in remaining.items() if count}
    if hopper_contents(server, route) != remaining:
        raise AssertionError(f"{route.name} did not accept exactly two original silicon or moved other outputs")
    charge(server, route.x, PAID)
    server.assert_state(f"{route.name}_pauses_at_{PAID}_fe_with_original_inputs_and_unworn_tools",
                        partial(route.case), results, timeout=14)
    return remaining


def fresh(server, results, report):
    prepare_area(server, results, [route.case for route in ROUTES])
    for route in ROUTES:
        prepare_route(server, results, route)
    for route in ROUTES:
        start_grind(server, results, route, route.count)
    for route in ROUTES:
        count = route.count
        output = read_grind(server, results, route, count)
        if output.get(SILICON, 0) < 2:
            # Additional real rolls of the same stock recipe cover the rare low-yield batch.
            extra = 8
            start_grind(server, results, route, extra)
            count += extra
            output = read_grind(server, results, route, count)
        if output.get(SILICON, 0) < 2:
            raise AssertionError(f"Only {output.get(SILICON, 0)} silicon rolled after {count} {route.input}")
        report["routes"][route.name] = {"input": route.input, "count": count, "fe": count * GRIND_COST,
                                        "outputs": output, "retained": load_and_pause(server, results, route, output)}


def check_inventory(server, results, report, route):
    server.assert_state(f"{route.name}_keeps_the_delivery_hopper_locked_and_sag_balance_intact",
                        f"{route.storage()} if data block {route.sag(81)} {{EnergyStored:{RETAINED_FE}}}", results)
    remaining = hopper_contents(server, route)
    if remaining != report["routes"][route.name]["retained"]:
        raise AssertionError(f"{route.name} lost or duplicated silicon or byproducts: {remaining}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    for key in ("forge-server", "jar"):
        parser.add_argument(f"--{key}", type=Path, required=True)
    parser.add_argument("--launch", type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "assertions": [], "servers": [], "routes": {}, "jarSha256": digest(directory / "mods/enderio.jar")}

    def resume(server, results):
        for route in ROUTES:
            server.assert_state(f"{route.name}_resumes_the_original_silicon_job_after_restart", partial(route.case), results)
            check_inventory(server, results, report, route)
        for route in ROUTES[1:]:
            charge(server, route.x, COST - PAID)
        if args.launch:
            run_client(args, server, report)
        else:
            headless_missing_tool(server, results)
        for route in ROUTES[1:]:
            server.assert_state(f"{route.name}_completes_with_exact_fe_tool_wear_and_one_output", finished(route.case), results, timeout=38)

    def reopen(server, results):
        for route in ROUTES:
            server.assert_state(f"{route.name}_preserves_exact_output_and_tool_state_after_restart", finished(route.case), results)
            check_inventory(server, results, report, route)

    try:
        run_stage(args, directory, report, "fresh", lambda server, results: fresh(server, results, report))
        run_stage(args, directory, report, "resume", resume)
        run_stage(args, directory, report, "reopened", reopen)
        report["passed"] = True
    finally:
        result = directory / "silicon-progression-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Silicon progression passed={report['passed']}, {len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
