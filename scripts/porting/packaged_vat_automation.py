#!/usr/bin/env python3
"""Exercise stock and datapack VAT automation in the packaged 1.20.1 Forge server."""

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import sys
import time

sys.dont_write_bytecode = True
from packaged_levitation_staff import game_tick
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage


def enderio_fluid(name):
    return f"enderio:fluid_{name}_still"


@dataclass(frozen=True)
class Batch:
    x: int
    name: str
    source: str
    input_amount: int
    reagents: tuple[str, str]
    output_amount: int

    @property
    def vat(self):
        return f"{self.x} 80 8"

    @property
    def supply(self):
        return f"{self.x} 80 7"

    @property
    def receiver(self):
        return f"{self.x} 80 9"

    @property
    def output(self):
        return enderio_fluid(self.name)


WATER = "minecraft:water"
HOOTCH = enderio_fluid("hootch")
NUTRIENT = enderio_fluid("nutrient_distillation")
PURE_DEW = enderio_fluid("dew_of_the_void")
STOCK = (
    Batch(4322, "nutrient_distillation", WATER, 1000, ("minecraft:beef", "minecraft:wheat"), 250),
    Batch(4327, "hootch", WATER, 1000, ("minecraft:wheat_seeds", "minecraft:wheat"), 250),
    Batch(4332, "rocket_fuel", HOOTCH, 1000, ("minecraft:redstone", "minecraft:fire_charge"), 500),
    Batch(4337, "fire_water", HOOTCH, 1000, ("minecraft:blaze_powder", "minecraft:redstone"), 1000),
    Batch(4342, "liquid_sunshine", WATER, 1000, ("minecraft:torchflower", "minecraft:glowstone_dust"), 300),
    Batch(4347, "liquid_darkness", enderio_fluid("liquid_sunshine"), 500, ("enderio:powdered_obsidian", "minecraft:amethyst_shard"), 1000),
    Batch(4352, "cloud_seed", WATER, 1000, ("minecraft:snowball", "minecraft:prismarine_shard"), 110),
    Batch(4357, "cloud_seed_concentrated", enderio_fluid("cloud_seed"), 1000, ("minecraft:lightning_rod", "minecraft:firework_rocket"), 500),
    Batch(4362, "dew_of_the_void", NUTRIENT, 4000, ("enderio:pulsating_powder", "enderio:ender_crystal_powder"), 1000),
    Batch(4367, "vapor_of_levity", PURE_DEW, 1000, ("enderio:prescient_powder", "enderio:pulsating_powder"), 1000),
)
TAGGED = Batch(4372, "hootch", WATER, 1000, ("minecraft:gold_ingot", "minecraft:copper_ingot"), 450)
TAGGED_RECIPE = "port_qa:tagged_batch"
NEW_TAG = '{Batch:"tagged",Readings:[I;3,5,7],Proof:1b}'
OLD_TAG = '{Batch:"prior"}'


def fluid(name, amount, tag=None):
    metadata = f",Tag:{tag}" if tag else ""
    return f'{{FluidName:"{name}",Amount:{amount}{metadata}}}'


def tank_fields(contents, index=0):
    return f"Fluids:{{Tanks:[{{Index:{index},Contents:{contents}}}]}}"


def drained(pos, index=0):
    return f"unless data block {pos} Fluids.Tanks[{{Index:{index}}}].Contents.FluidName"


def set_mode(server, pos, side, mode):
    server.command(f'data merge block {pos} {{IOConfig:{{Modes:{{{side}:"{mode}"}}}}}}')


def configure(server, batch, *, tagged=False, reagent_count=1):
    server.command(f"setblock {batch.supply} enderio:fluid_tank[facing=south]")
    server.command(f"setblock {batch.receiver} enderio:fluid_tank[facing=south]")
    server.command(f"setblock {batch.vat} enderio:vat[facing=south]")
    supply = tank_fields(fluid(batch.source, batch.input_amount))
    server.command(f"data merge block {batch.supply} {{{supply}}}")
    north = "disable" if tagged else "pull"
    south = "disable" if tagged else "push"
    server.command(f'data merge block {batch.vat} {{IOConfig:{{Modes:{{north:"{north}",south:"{south}"}}}}}}')
    if tagged:
        prior = tank_fields(fluid(batch.output, 70, OLD_TAG), index=1)
        server.command(f"data merge block {batch.vat} {{{prior}}}")
        set_mode(server, batch.supply, "south", "push")
    for offset, heading, reagent in zip((-1, 1), ("east", "west"), batch.reagents):
        server.command(f'setblock {batch.x + offset} 80 8 minecraft:hopper[facing={heading}]'
                       f'{{Items:[{{Slot:0b,id:"{reagent}",Count:{reagent_count}b}}]}}')


def write_pack(directory):
    root = directory / WORLD / "datapacks/port-vat"

    def write(path, contents):
        file = root / path
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(json.dumps(contents) + "\n")

    write("pack.mcmeta", {"pack": {"pack_format": 15, "description": "Port VAT test"}})
    write("data/port_qa/tags/items/copper.json", {"values": ["minecraft:copper_ingot"]})
    write("data/port_qa/tags/items/gold.json", {"values": ["minecraft:gold_ingot"]})
    write("data/port_qa/recipes/tagged_batch.json", {
        "type": "enderio:vat_fermenting", "input": {"fluid": WATER, "amount": 1000},
        "left_reagent": "port_qa:copper", "right_reagent": "port_qa:gold", "ticks": 160,
        "output": {"id": TAGGED.output, "amount": 400, "Tag": NEW_TAG},
    })
    write("data/enderio/data_maps/item/vat_reagent.json", {"values": {
        "minecraft:copper_ingot": {"#port_qa:copper": 1.5},
        "minecraft:gold_ingot": {"#port_qa:gold": 0.75},
    }})


def blocked_state():
    return (f'if data block {TAGGED.vat} Task{{RecipeId:"{TAGGED_RECIPE}",ProgressMade:160,ProgressRequired:160}} '
            f'if data block {TAGGED.vat} {{{tank_fields(fluid(WATER, 1000))},Items:{{Items:['
            '{id:"minecraft:copper_ingot",Count:1},{id:"minecraft:gold_ingot",Count:1}]}} '
            f'if data block {TAGGED.vat} {{{tank_fields(fluid(HOOTCH, 70, OLD_TAG), index=1)}}} {drained(TAGGED.receiver)}')


def setup(server, results):
    server.command("forceload add 4320 0 4383 15")
    server.assert_state("vat_rows_loaded", "if loaded 4320 80 8 if loaded 4383 80 8", results, timeout=25)
    server.command("gamerule doMobSpawning false")
    for batch in STOCK:
        configure(server, batch)
    configure(server, TAGGED, tagged=True)
    initial = game_tick(server)
    # Multiple actual work cycles must run with the supply pushing against a closed north face.
    deadline = time.monotonic() + 12
    while game_tick(server) < initial + 12:
        if time.monotonic() >= deadline:
            raise AssertionError("The VAT fixture did not advance its game ticks")
    server.assert_state("disabled_north_face_blocks_the_supply_push",
                        f'if data block {TAGGED.supply} {{{tank_fields(fluid(WATER, 1000))}}} '
                        f'if data block {TAGGED.vat} {{IOConfig:{{Modes:{{north:"disable",south:"disable"}}}}}} '
                        f'{drained(TAGGED.vat)}', results)
    set_mode(server, TAGGED.vat, "north", "pull")
    try:
        server.assert_state("blocked_tagged_batch_retains_inputs_and_pending_recipe", blocked_state(), results, timeout=35)
    finally:
        for pos in (TAGGED.vat, TAGGED.supply, TAGGED.receiver):
            server.command(f"data get block {pos}")


def stock_complete(server, results):
    failures = []
    for batch in STOCK:
        empty_hoppers = f"unless data block {batch.x - 1} 80 8 Items[0] unless data block {batch.x + 1} 80 8 Items[0]"
        expected = tank_fields(fluid(batch.output, batch.output_amount))
        try:
            server.assert_state(f"{batch.name}_native_hoppers_vat_and_receiving_tank_conserve_batch",
                                f'if data block {batch.receiver} {{{expected}}} {empty_hoppers} '
                                f'{drained(batch.supply)} {drained(batch.vat)} {drained(batch.vat, 1)} '
                                f'unless data block {batch.vat} Items.Items[0] unless data block {batch.vat} Task',
                                results, timeout=40)
        except AssertionError as failure:
            for pos in (batch.vat, batch.supply, batch.receiver, f"{batch.x + 1} 80 8"):
                server.command(f"data get block {pos}")
            failures.append(str(failure))
    return failures


def release_tagged(server, results):
    server.assert_state("completed_but_blocked_task_survives_restart", blocked_state(), results)
    set_mode(server, TAGGED.vat, "south", "push")
    prior_receiver = tank_fields(fluid(HOOTCH, 70, OLD_TAG))
    new_output = tank_fields(fluid(HOOTCH, TAGGED.output_amount, NEW_TAG), index=1)
    try:
        server.assert_state("unblocking_exports_prior_batch_and_separates_new_tagged_output",
                            f'if data block {TAGGED.receiver} {{{prior_receiver}}} '
                            f'if data block {TAGGED.vat} {{{new_output}}} {drained(TAGGED.supply)} {drained(TAGGED.vat)} '
                            f'unless data block {TAGGED.vat} Items.Items[0] unless data block {TAGGED.vat} Task',
                            results, timeout=10)
    finally:
        server.command(f"data get block {TAGGED.vat}")
        server.command(f"data get block {TAGGED.receiver}")
    # Move the prior batch to an isolated position before connecting an empty receiver.
    server.command(f"clone {TAGGED.receiver} {TAGGED.receiver} {TAGGED.x + 2} 80 9 replace move")
    server.command(f"setblock {TAGGED.receiver} enderio:fluid_tank[facing=south]")
    final_receiver = tank_fields(fluid(HOOTCH, TAGGED.output_amount, NEW_TAG))
    server.assert_state("new_receiver_accepts_exact_scaled_batch_and_typed_tag_once",
                        f'if data block {TAGGED.receiver} {{{final_receiver}}} {drained(TAGGED.vat, 1)} '
                        f'if data block {TAGGED.x + 2} 80 9 {{{prior_receiver}}}', results)


def verify(server, results):
    failures = stock_complete(server, results)
    try:
        release_tagged(server, results)
    except AssertionError as failure:
        failures.append(str(failure))
    if failures:
        raise AssertionError("\n".join(failures))


def check_reopened(server, results):
    final_receiver = tank_fields(fluid(HOOTCH, TAGGED.output_amount, NEW_TAG))
    prior_receiver = tank_fields(fluid(HOOTCH, 70, OLD_TAG))
    server.assert_state("both_vat_output_batches_survive_second_restart_without_replay",
                        f'if data block {TAGGED.receiver} {{{final_receiver}}} if data block {TAGGED.x + 2} 80 9 {{{prior_receiver}}} '
                        f'{drained(TAGGED.vat)} {drained(TAGGED.vat, 1)} '
                        f'unless data block {TAGGED.vat} Items.Items[0] unless data block {TAGGED.vat} Task', results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    args = parser.parse_args()
    root = ROOT / "build/port-runtime" / args.name
    root.mkdir()
    profile = root / "server"
    results = {"passed": False, "forge": FORGE, "jarSha256": digest(args.jar), "assertions": [], "servers": []}
    try:
        prepare_profile(args, profile, args.jar)
        write_pack(profile)
        run_stage(args, profile, results, "setup", setup)
        run_stage(args, profile, results, "restart", verify)
        run_stage(args, profile, results, "persisted", check_reopened)
        results["passed"] = True
    finally:
        (root / "vat-automation-results.json").write_text(json.dumps(results, indent=2) + "\n")


if __name__ == "__main__":
    main()
