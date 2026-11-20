#!/usr/bin/env python3
"""Follow native dungeon Loot Capacitors into stock powered machines."""

import argparse
from dataclasses import dataclass
import json
import math
from pathlib import Path
import re
import struct
import sys
import time

sys.dont_write_bytecode = True
from packaged_glass_progression import transfer
from packaged_sag_progression import charge, feed, inputs, mill, output
from packaged_server_smoke import ROOT, digest
from packaged_solar_charging import check
from packaged_spawners import WORLD, prepare_profile, run_stage
from packaged_starter_progression import hopper_item
from saved_world import read_chunk


CAPACITOR = "enderio:loot_capacitor"
DATA_PATH = 'tag."enderio:data"."enderio:capacitor_data"'
DUNGEON = "minecraft:chests/simple_dungeon"
VILLAGE = "minecraft:chests/village/village_plains_house"
CONTROL_X = 3894
SAG_DEPOSIT, SAG_COST, RESERVE = 1700, 2400, 137
LAPIS, POWDER = "minecraft:lapis_lazuli", "enderio:powdered_lapis_lazuli"


@dataclass(frozen=True)
class Route:
    seed: int
    x: int
    machine: str
    slot: int
    modifiers: tuple[str, ...]

    @property
    def loot_x(self):
        return 3780 + 3 * self.seed

    @property
    def pos(self):
        return mill(self.x)


GENERATORS = (
    Route(4, 3898, "stirling_generator", 1, ("fuel_efficiency",)),
    Route(30, 3902, "stirling_generator", 1, ("energy_capacity", "burning_energy_generation")),
)
SAG = Route(2, 3906, "sag_mill", 6, ("energy_use",))
ROUTES = (*GENERATORS, SAG)


def write_sampler(directory):
    pack = directory / WORLD / "datapacks/port-capacitor"
    functions = pack / "data/port_cap/functions"
    functions.mkdir(parents=True)
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 15, "description": "SAG Mill tick sample"}}) + "\n")
    (functions / "begin.mcfunction").write_text(
        f"data merge block {SAG.pos} {{EnergyStored:{SAG_DEPOSIT}}}\n"
        "data modify storage port_cap:sag value set value {}\n"
        "execute store result storage port_cap:sag value.initialTick int 1 run time query gametime\n"
        f"execute store result storage port_cap:sag value.initialEnergy int 1 run data get block {SAG.pos} EnergyStored\n"
        "schedule function port_cap:end 3t\n")
    (functions / "end.mcfunction").write_text(
        "execute store result storage port_cap:sag value.tick int 1 run time query gametime\n"
        f"execute store result storage port_cap:sag value.remaining int 1 run data get block {SAG.pos} EnergyStored\n"
        f"execute store result storage port_cap:sag value.paid int 1 run data get block {SAG.pos} Task.ProgressMade\n")


def generate_chest(server, x, table, seed):
    server.command(f'setblock {x} 80 6 minecraft:chest{{LootTable:"{table}",LootTableSeed:{seed}L}}')
    server.command(f"setblock {x} 79 6 minecraft:hopper[facing=down]")


def chest_contents(directory, x):
    chunk = read_chunk(directory / WORLD, x // 16, 0)
    return [(f"{x} {entity['y']} 6", stack) for entity in chunk["block_entities"]
            if entity["x"] == x and entity["y"] in (79, 80) and entity["z"] == 6 for stack in entity.get("Items", [])]


def float32(value):
    return struct.unpack(">f", struct.pack(">f", value))[0]


def modifiers(data):
    ranks = data["specializations"]
    capacity = ranks.get("energy_capacity", data["base"])
    fuel = ranks.get("fuel_efficiency", data["base"])
    generation = ranks.get("burning_energy_generation", data["base"])
    load = ranks.get("energy_use", data["base"])
    efficiency = math.floor(float32(80 + float32(20 * fuel)) + 0.5)
    return {
        "capacity": math.floor(float32(float32(64000 * capacity) * capacity) + 0.5),
        "burnDuration": int(float32(37.5 * float32(efficiency / 100))),
        "generationPerTick": math.floor(float32(40 * generation) + 0.5),
        "usagePerTick": math.floor(float32(float32(20 * load) * load) + 0.5),
    }


def rolled_chests(server, results, report):
    server.command("forceload add 3776 0 3967 15")
    server.assert_state("dungeon_chests_and_machine_fixtures_are_loaded", f"if loaded {ROUTES[0].loot_x} 80 6 if loaded {SAG.pos}", results)
    for route in ROUTES:
        generate_chest(server, route.loot_x, DUNGEON, route.seed)
    generate_chest(server, CONTROL_X, VILLAGE, 30)
    for x in (CONTROL_X, *(route.loot_x for route in ROUTES)):
        server.assert_state(f"native_hopper_opens_seeded_chest_{x}", f"unless data block {x} 80 6 LootTable", results)
        server.command(f"setblock {x + 1} 79 6 minecraft:redstone_block")
    server.command("save-all flush")
    control = chest_contents(server.directory, CONTROL_X)
    check("untargeted_village_chest_contains_vanilla_loot_but_no_loot_capacitor", True,
          bool(control) and all(stack["id"] != CAPACITOR for _, stack in control), results)
    for route in ROUTES:
        rolled = [(pos, stack) for pos, stack in chest_contents(server.directory, route.loot_x) if stack["id"] == CAPACITOR]
        check(f"dungeon_seed_{route.seed}_adds_one_original_single_capacitor", 1, sum(stack["Count"] for _, stack in rolled), results)
        (pos, stack), = rolled
        data = stack["tag"]["enderio:data"]["enderio:capacitor_data"]
        check(f"dungeon_seed_{route.seed}_has_the_expected_typed_data_and_roll_bounds", True,
              set(data["specializations"]) == set(route.modifiers) and 1 <= data["base"] <= 4
              and all(1 <= rank <= 4 for rank in data["specializations"].values()), results)
        report["rolls"][str(route.seed)] = {"source": pos, "stack": stack, **modifiers(data)}


def installed(route):
    return f'if data block {route.pos} Items.Items[{{Slot:{route.slot},id:"{CAPACITOR}",Count:1}}].{DATA_PATH}'


def install(server, results, route, record):
    modes = 'up:"pull",down:"push"' if route == SAG else 'up:"pull"'
    server.command(f"setblock {route.pos} enderio:{route.machine}[facing=south]{{IOConfig:{{Modes:{{{modes}}}}}}}")
    server.command(f"setblock {feed(route.x)} minecraft:hopper[facing=down]")
    source, slot = record["source"], record["stack"]["Slot"]
    transfer(server, source, slot, feed(route.x), 0)
    server.assert_state(f"seed_{route.seed}_original_stack_reaches_the_machine_capacitor_slot",
                        f'{installed(route)} unless data block {feed(route.x)} Items[0] '
                        f'unless data block {source} Items[{{Slot:{slot}b,id:"{CAPACITOR}"}}]', results)


def stopped_generator(route, energy, duration):
    return (f'{installed(route)} if data block {route.pos} {{EnergyStored:{energy},BurnTime:0,BurnDuration:{duration}}} '
            f'unless data block {route.pos} Items.Items[{{Slot:0}}] unless data block {feed(route.x)} Items[0]')


def burn_stick(server, route, start):
    charge(server, route.x, start)
    inputs(server, route.x, "minecraft:stick", 1)


def partial_mill():
    return (f'{installed(SAG)} if data block {SAG.pos} {{EnergyStored:0,Task:{{RecipeId:"enderio:sag_milling/lapis",'
            f'ProgressMade:{SAG_DEPOSIT},ProgressRequired:{SAG_COST}}}}} '
            f'if data block {SAG.pos} Items.Items[{{Slot:0,id:"{LAPIS}",Count:1}}] unless data block {output(SAG.x)} Items[0]')


def measure_mill(server, results, report):
    server.command(f"setblock {output(SAG.x)} minecraft:hopper[facing=down]")
    inputs(server, SAG.x, LAPIS, 1)
    server.assert_state("the_mill_accepts_one_original_lapis_before_power_is_supplied",
                        f'if data block {SAG.pos} Items.Items[{{Slot:0,id:"{LAPIS}",Count:1}}]', results)
    server.command("schedule function port_cap:begin 1t")
    deadline = time.monotonic() + 10
    while True:
        reply = server.command("data get storage port_cap:sag value")
        fields = {key: int(value) for key, value in re.findall(r"\b(initialTick|initialEnergy|tick|remaining|paid): (\d+)", reply)}
        if len(fields) == 5:
            break
        if time.monotonic() >= deadline:
            raise AssertionError(f"The scheduled SAG Mill sample did not complete: {reply}")
    report["sagWindow"] = fields
    check("the_native_sag_sample_spans_exactly_three_ticks", 3, fields["tick"] - fields["initialTick"], results)
    rate = report["rolls"][str(SAG.seed)]["usagePerTick"]
    check("the_loot_energy_use_modifier_sets_the_exact_paid_fe_over_three_ticks", [SAG_DEPOSIT, 3 * rate, 3 * rate],
          [fields["initialEnergy"], fields["initialEnergy"] - fields["remaining"], fields["paid"]], results)
    server.assert_state("the_partly_paid_lapis_job_stops_after_using_its_entire_deposit", partial_mill(), results)


def fresh(server, results, report):
    rolled_chests(server, results, report)
    for route in ROUTES:
        install(server, results, route, report["rolls"][str(route.seed)])
    for route in GENERATORS:
        record = report["rolls"][str(route.seed)]
        burn_stick(server, route, record["capacity"] - 1)
    measure_mill(server, results, report)
    for route in GENERATORS:
        record = report["rolls"][str(route.seed)]
        server.assert_state(f"seed_{route.seed}_sets_the_exact_generator_capacity_and_burn_duration",
                            stopped_generator(route, record["capacity"], record["burnDuration"]), results)


def finished_mill():
    return (f'{installed(SAG)} if data block {SAG.pos} {{EnergyStored:{RESERVE}}} unless data block {SAG.pos} Task '
            f'unless data block {SAG.pos} Items.Items[{{Slot:0}}] {hopper_item(output(SAG.x), POWDER, 1)} '
            f'unless data block {output(SAG.x)} Items[1]')


def resume(server, results, report):
    for route in GENERATORS:
        record = report["rolls"][str(route.seed)]
        server.assert_state(f"seed_{route.seed}_restores_its_original_capacity_and_burn_duration_after_restart",
                            stopped_generator(route, record["capacity"], record["burnDuration"]), results)
        burn_stick(server, route, RESERVE)
    server.assert_state("the_original_lapis_job_and_paid_progress_survive_restart", partial_mill(), results)
    charge(server, SAG.x, SAG_COST - SAG_DEPOSIT + RESERVE)
    server.assert_state("the_original_mill_finishes_on_the_exact_remaining_payment", finished_mill(), results)
    for route in GENERATORS:
        record = report["rolls"][str(route.seed)]
        generated = record["generationPerTick"] * record["burnDuration"]
        record["generated"] = generated
        server.assert_state(f"seed_{route.seed}_produces_exact_fe_at_its_rolled_rate_after_restart",
                            stopped_generator(route, RESERVE + generated, record["burnDuration"]), results)


def reopened(server, results, report):
    for route in GENERATORS:
        record = report["rolls"][str(route.seed)]
        server.assert_state(f"seed_{route.seed}_keeps_its_exact_measured_generator_result",
                            stopped_generator(route, RESERVE + record["generated"], record["burnDuration"]), results)
    server.assert_state("the_final_mill_job_and_exact_original_output_survive_restart", finished_mill(), results)


def saved_modifiers(directory, report, phase):
    for route in ROUTES:
        chunk = read_chunk(directory / WORLD, route.x // 16, 0)
        entity, = (e for e in chunk["block_entities"] if (e["x"], e["y"], e["z"]) == (route.x, 80, 8))
        item, = (stack for stack in entity["Items"]["Items"] if stack["Slot"] == route.slot)
        rolled = {**report["rolls"][str(route.seed)]["stack"], "Slot": route.slot}
        check(f"seed_{route.seed}_saved_{phase}_keeps_the_entire_original_loot_stack", rolled, item, report["assertions"])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for key in ("java", "forge-server", "jar"):
        parser.add_argument(f"--{key}", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    write_sampler(directory)
    report = {"passed": False, "assertions": [], "servers": [], "rolls": {}, "jarSha256": digest(directory / "mods/enderio.jar")}
    try:
        run_stage(args, directory, report, "fresh", lambda server, results: fresh(server, results, report))
        saved_modifiers(directory, report, "fresh")
        run_stage(args, directory, report, "resume", lambda server, results: resume(server, results, report))
        run_stage(args, directory, report, "reopened", lambda server, results: reopened(server, results, report))
        saved_modifiers(directory, report, "reopened")
        report["passed"] = True
    finally:
        result = directory / "loot-capacitor-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Loot Capacitors passed={report['passed']}, {len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
