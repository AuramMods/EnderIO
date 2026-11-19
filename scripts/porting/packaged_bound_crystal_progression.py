#!/usr/bin/env python3
"""Bind captured souls and route the original crystals through native hoppers."""

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import sys

sys.dont_write_bytecode = True
from packaged_sag_progression import charge, mill, output, prepare_mill
from packaged_server_smoke import ROOT, digest
from packaged_soul_binder import VIAL, item, xp_tank
from packaged_spawners import prepare_profile, run_stage
from packaged_starter_progression import hopper_item


CAPACITOR = "enderio:octadic_capacitor"
VIBRANT = "enderio:vibrant_crystal"
BIND_PAID, BIND_RESERVE, XP_RESERVE = 12300, 317, 37
GRIND_COST, GRIND_PAID, GRIND_RESERVE = 2400, 600, 137
VILLAGER = 'VillagerData:{type:"minecraft:plains",profession:"minecraft:librarian",level:2}'


@dataclass(frozen=True)
class Route:
    x: int
    mob: str
    crystal: str
    ingredient: str
    energy: int
    xp: int
    entity_data: str = ""
    powder: str = ""
    grind_recipe: str = ""

    def pos(self, y, *, dx=0, z=8):
        return f"{self.x + dx} {y} {z}"

    @property
    def soul(self):
        data = f'Health:7.0f,Tags:["port_crystal_{self.mob}"]{self.entity_data}'
        return f'tag:{{"enderio:data":{{"enderio:soul":{{entity_type:"minecraft:{self.mob}",entity_tag:{{{data}}}}}}}}}'


ENTICING = Route(3495, "villager", "enticing_crystal", "minecraft:emerald", 51200, 800, f",{VILLAGER}")
ROUTES = (
    Route(3489, "shulker", "prescient_crystal", VIBRANT, 100000, 2240, ",Color:11b", "prescient_powder", "precient_crystal"),
    ENTICING,
    Route(3501, "enderman", "ender_crystal", VIBRANT, 76800, 1440, powder="ender_crystal_powder", grind_recipe="ender_crystal"),
)
MILLS = tuple(route for route in ROUTES if route.powder)


def crystal(route):
    return f"enderio:{route.crystal}"


def capture(server, results, route):
    dispenser = route.pos(83, dx=-1)
    power = route.pos(83, dx=-1, z=7)
    target = f"@e[type=minecraft:{route.mob},tag=port_crystal_{route.mob}]"
    server.command(f'setblock {dispenser} minecraft:dispenser[facing=east]{{Items:[{item(0, VIAL, vanilla=True)}]}}')
    server.command(f'summon minecraft:{route.mob} {route.x + 0.5} 83 8.5 '
                   f'{{Health:7.0f,NoAI:1b,Silent:1b,PersistenceRequired:1b,Tags:["port_crystal_{route.mob}"]{route.entity_data}}}')
    server.assert_state(f"the_live_{route.mob}_stands_at_the_dispenser", f"if entity {target}", results)
    server.command(f"setblock {power} minecraft:redstone_block")
    server.assert_state(f"the_dispenser_captures_the_{route.mob}_with_its_original_entity_data",
                        f"unless entity {target} if data block {dispenser} Items[{item(0, VIAL, route.soul, vanilla=True)}]", results)
    server.command(f"setblock {power} minecraft:air")
    server.command(f"setblock {route.pos(82, dx=-1)} minecraft:hopper[facing=east]")


def binding_inputs(route, energy, xp):
    inputs = ",".join((item(0, VIAL, route.soul), item(1, route.ingredient), item(4, CAPACITOR)))
    return (f"if data block {route.pos(82)} {{EnergyStored:{energy},{xp_tank(xp)},Items:{{Items:[{inputs}]}}}} "
            f"unless data block {route.pos(81)} Items[0]")


def paused_binding(route):
    pos = route.pos(82)
    outputs = f"{{Item:{item(None, crystal(route))}}},{{Item:{item(None, VIAL)}}}"
    return (f'{binding_inputs(route, 0, route.xp + XP_RESERVE)} if data block {pos} {{Task:{{RecipeId:"enderio:soulbinding/{route.crystal}",'
            f'ProgressRequired:{route.energy},ProgressMade:{BIND_PAID},HasDeterminedOutputs:1b,Outputs:[{outputs}]}}}} '
            f"unless data block {pos} Task.Outputs[2]")


def spent_binder(route):
    pos = route.pos(82)
    slots_empty = " ".join(f"unless data block {pos} Items.Items[{{Slot:{n}}}]" for n in range(4))
    return f"if data block {pos} {{EnergyStored:{BIND_RESERVE},{xp_tank(XP_RESERVE)}}} unless data block {pos} Task {slots_empty}"


def empty_vial(pos):
    return (f'if data block {pos} Items[{{id:"{VIAL}",Count:1b}}] '
            f'unless data block {pos} Items[{{id:"{VIAL}"}}].tag."enderio:data"."enderio:soul"')


def mill_delivery(route):
    hopper = route.pos(81)
    return (f"if data block {mill(route.x)} Items.Items[{item(0, crystal(route))}] "
            f"{empty_vial(hopper)} unless data block {hopper} Items[1]")


def enticing_delivery():
    pos = ENTICING.pos(80)
    return (f"{hopper_item(pos, crystal(ENTICING), 1)} {empty_vial(pos)} unless data block {pos} Items[2] "
            f"unless data block {ENTICING.pos(81)} Items[0]")


def paused_mill(route):
    pos = mill(route.x)
    return (f'{mill_delivery(route)} if data block {pos} {{EnergyStored:0,Task:{{RecipeId:"enderio:sag_milling/{route.grind_recipe}",'
            f'ProgressRequired:{GRIND_COST},ProgressMade:{GRIND_PAID},HasDeterminedOutputs:1b,Outputs:[{{Item:{item(None, "enderio:" + route.powder)}}}]}}}} '
            f"unless data block {pos} Task.Outputs[1] unless data block {output(route.x)} Items[0]")


def finished_mill(route):
    pos, hopper = mill(route.x), output(route.x)
    slots_empty = " ".join(f"unless data block {pos} Items.Items[{{Slot:{n}}}]" for n in range(6))
    return (f"if data block {pos} {{EnergyStored:{GRIND_RESERVE}}} unless data block {pos} Task {slots_empty} "
            f'{hopper_item(hopper, "enderio:" + route.powder, 1)} unless data block {hopper} Items[1] '
            f"{empty_vial(route.pos(81))} unless data block {route.pos(81)} Items[1]")


def prepare(server, results):
    server.command("forceload add 3488 0 3519 15")
    server.assert_state("all_three_soul_binder_routes_are_loaded", " ".join(f"if loaded {route.pos(82)}" for route in ROUTES), results)
    for command in ("gamerule doMobSpawning false", "gamerule doDaylightCycle false", "time set midnight"):
        server.command(command)
    for route in ROUTES:
        if route.powder:
            prepare_mill(server, route.x)
        else:
            server.command(f"setblock {route.pos(80)} minecraft:barrel")
            server.command(f"setblock {route.pos(81)} minecraft:hopper[facing=down]")
        server.command(f'setblock {route.pos(82)} enderio:soul_binder[facing=south]'
                       f'{{EnergyStored:{BIND_PAID},Items:{{Items:[{item(4, CAPACITOR)}]}},IOConfig:{{Modes:{{down:"push"}}}}}}')
        server.command(f"setblock {route.pos(82, dx=1)} minecraft:hopper[facing=west]{{Items:[{item(0, route.ingredient, vanilla=True)}]}}")
        tank = route.pos(82, z=7)
        server.command(f"setblock {tank} enderio:fluid_tank[facing=south]")
        server.command(f'data merge block {tank} {{{xp_tank(route.xp - 1)},IOConfig:{{Modes:{{south:"push"}}}}}}')
        capture(server, results, route)
        server.assert_state(f"{route.crystal}_holds_its_original_inputs_at_one_mb_below_cost",
                            f"{binding_inputs(route, BIND_PAID, route.xp - 1)} unless data block {route.pos(82)} Task "
                            f"unless data block {route.pos(83, dx=-1)} Items[0] unless data block {route.pos(82, dx=-1)} Items[0] "
                            f"unless data block {route.pos(82, dx=1)} Items[0]", results)
        server.command(f"data merge block {tank} {{{xp_tank(XP_RESERVE + 1)}}}")
        server.assert_state(f"{route.crystal}_pauses_with_paid_fe_and_two_queued_outputs", paused_binding(route), results)


def resume_binding(server, results):
    for route in ROUTES:
        server.assert_state(f"{route.crystal}_restores_the_original_queued_outputs_and_paid_state", paused_binding(route), results)
        server.command(f"data merge block {route.pos(82)} {{EnergyStored:{route.energy - BIND_PAID + BIND_RESERVE}}}")
    for route in ROUTES:
        destination = mill_delivery(route) if route.powder else enticing_delivery()
        server.assert_state(f"{route.crystal}_spends_exact_fe_and_xp_and_routes_its_two_outputs", f"{spent_binder(route)} {destination}",
                            results, timeout=35)
    for route in MILLS:
        charge(server, route.x, GRIND_PAID)
    for route in MILLS:
        server.assert_state(f"{route.powder}_pauses_at_600_fe_with_the_original_crystal_and_queued_powder", paused_mill(route), results)


def resume_grinding(server, results):
    for route in MILLS:
        server.assert_state(f"{route.powder}_restores_its_original_paid_task_and_queued_powder", paused_mill(route), results)
        charge(server, route.x, GRIND_COST - GRIND_PAID + GRIND_RESERVE)
    for route in MILLS:
        server.assert_state(f"{route.powder}_exports_once_on_the_exact_remaining_budget", finished_mill(route), results)


def reopened(server, results):
    for route in ROUTES:
        destination = finished_mill(route) if route.powder else enticing_delivery()
        server.assert_state(f"{route.crystal}_keeps_the_exact_outputs_and_reserves_after_the_final_restart",
                            f"{spent_binder(route)} {destination}", results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    for key in ("forge-server", "jar"):
        parser.add_argument(f"--{key}", type=Path, required=True)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}
    try:
        run_stage(args, directory, report, "fresh", prepare)
        run_stage(args, directory, report, "binding", resume_binding)
        run_stage(args, directory, report, "grinding", resume_grinding)
        run_stage(args, directory, report, "reopened", reopened)
        report["passed"] = True
    finally:
        result = directory / "bound-crystal-progression-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Bound crystal progression passed={report['passed']}, {len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
