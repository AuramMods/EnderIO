#!/usr/bin/env python3
"""Exercise every stock silverfish Soul Binder recipe on packaged Forge."""

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import sys

sys.dont_write_bytecode = True
from packaged_nutritious_stick import advance
from packaged_server_smoke import ROOT, digest
from packaged_soul_binder import VIAL, item, xp_tank
from packaged_spawners import prepare_profile, run_stage


CHUNKS = "5376 0 5455 15"
CAPACITOR = "enderio:octadic_capacitor"
PAID = 3127
COST = 10000
FE_RESERVE = 137
XP_RESERVE = 137
SOUL = '"enderio:data"."enderio:soul"'
HOSTS = ("stone", "cobblestone", "deepslate", "stone_bricks", "mossy_stone_bricks",
         "cracked_stone_bricks", "chiseled_stone_bricks")


@dataclass(frozen=True)
class Binding:
    x: int
    host: str
    mob: str = "silverfish"

    def pos(self, y, dx=0):
        return f"{self.x + dx} {y} 8"

    @property
    def name(self):
        return f"{self.mob}_{self.host}"

    @property
    def soul_tag(self):
        return (f'tag:{{"enderio:data":{{"enderio:soul":{{entity_type:"minecraft:{self.mob}",'
                f'entity_tag:{{Tags:["{self.name}"]}}}}}}}}')

    def host_stack(self, slot, *, vanilla=False):
        return item(slot, f"minecraft:{self.host}", f"tag:{{PortHost:{self.x}}}", vanilla=vanilla)

    @property
    def result(self):
        return f"minecraft:infested_{self.host}"

    @property
    def recipe(self):
        return f"enderio:soulbinding/infested_{self.host}"


JOBS = tuple(Binding(5378 + 8 * i, host) for i, host in enumerate(HOSTS))
CONTROLS = (Binding(5434, "dirt"), Binding(5442, "stone", "endermite"))
ALL = (*JOBS, *CONTROLS)


def capture(server, results, job):
    dispenser = job.pos(82)
    power = job.pos(83)
    mob = f"@e[tag={job.name},type=minecraft:{job.mob}]"
    server.command(f'setblock {dispenser} minecraft:dispenser[facing=east]{{Items:[{item(0, VIAL, vanilla=True)}]}}')
    server.command(f"setblock {job.pos(81, 1)} minecraft:stone")
    name = json.dumps({"text": job.name}, separators=(",", ":"))
    server.command(f"summon minecraft:{job.mob} {job.x + 1}.5 82 8.5 "
                   f"""{{CustomName:'{name}',Tags:["{job.name}"],NoAI:1b,PersistenceRequired:1b}}""")
    server.assert_state(f"{job.name}_native_entity_is_present", f"if entity {mob}", results)
    server.command(f"setblock {power} minecraft:redstone_block")
    server.assert_state(f"{job.name}_dispenser_captures_its_original_soul",
                        f"unless entity {mob} "
                        f"if data block {dispenser} Items[{item(0, VIAL, job.soul_tag, vanilla=True)}]", results)
    server.command(f"setblock {power} minecraft:air")


def feed(server, results, job, energy):
    pos = job.pos(80)
    side = job.pos(80, -1)
    server.command(f'setblock {pos} enderio:soul_binder[facing=south]{{EnergyStored:{energy},'
                   f'Items:{{Items:[{item(4, CAPACITOR)}]}},IOConfig:{{Modes:{{down:"push"}}}}}}')
    server.command(f"setblock {side} minecraft:hopper[facing=east]")
    server.command(f"item replace block {side} container.0 with minecraft:{job.host}{{PortHost:{job.x}}}")
    capture(server, results, job)
    server.command(f"setblock {job.pos(81)} minecraft:hopper[facing=down]")


def xp_condition(pos, amount):
    if amount:
        return f"if data block {pos} {{{xp_tank(amount)}}}"
    return f"unless data block {pos} Fluids.Tanks[0].Contents.Amount"


def retained_inputs(job, energy, xp, *, rejected_host=False):
    pos = job.pos(80)
    feed_hopper = job.pos(80, -1)
    if rejected_host:
        material = (f'unless data block {pos} Items.Items[{{Slot:1}}] '
                    f'if data block {feed_hopper} Items[{job.host_stack(0, vanilla=True)}]')
    else:
        material = (f'if data block {pos} Items.Items[{job.host_stack(1)}] '
                    f'unless data block {feed_hopper} Items[0]')
    return (f'if data block {pos} {{EnergyStored:{energy},Items:{{Items:['
            f'{item(0, VIAL, job.soul_tag)},{item(4, CAPACITOR)}]}}}} '
            f'{xp_condition(pos, xp)} '
            f'unless data block {job.pos(82)} Items[0] unless data block {job.pos(81)} Items[0] '
            f'{material} '
            f'unless data block {pos} Items.Items[{{Slot:2}}] unless data block {pos} Items.Items[{{Slot:3}}]')


def pending(job, paid, xp):
    pos = job.pos(80)
    products = f'{{Item:{item(None, job.result)}}},{{Item:{item(None, VIAL)}}}'
    return (f"{retained_inputs(job, 0, xp)} "
            f'if data block {pos} {{Task:{{RecipeId:"{job.recipe}",ProgressMade:{paid},'
            f'ProgressRequired:{COST},HasDeterminedOutputs:1b,Outputs:[{products}]}}}} '
            f'unless data block {pos} Task.Outputs[2] '
            f'unless data block {pos} Task.Outputs[0].Item.tag unless data block {pos} Task.Outputs[1].Item.tag')


def verify_controls(server, results, phase):
    wrong_host, wrong_soul = CONTROLS
    server.assert_state(f"{phase}_the_invalid_host_stays_tagged_in_its_hopper",
                        f'{retained_inputs(wrong_host, COST, 0, rejected_host=True)} '
                        f'unless data block {wrong_host.pos(80)} Task', results)
    server.assert_state(f"{phase}_an_endermite_vial_and_stone_do_not_start_a_job",
                        f'{retained_inputs(wrong_soul, COST, 0)} unless data block {wrong_soul.pos(80)} Task', results)


def verify_pending(server, results, phase):
    for job in JOBS:
        xp = XP_RESERVE if job == JOBS[0] else 0
        server.assert_state(f"{phase}_{job.name}_retains_exact_paid_work_and_queued_outputs",
                            pending(job, PAID, xp), results)
    verify_controls(server, results, phase)


def load(server, results, phase):
    server.command(f"forceload add {CHUNKS}")
    available = " ".join(f"if loaded {job.pos(80)}" for job in ALL)
    server.assert_state(f"{phase}_all_fixture_chunks_are_loaded", available, results)


def start_jobs(server, results):
    server.command("gamerule doMobSpawning false")
    load(server, results, "initial")
    server.command("fill 5376 79 6 5455 79 10 minecraft:stone_bricks")
    for job in JOBS:
        feed(server, results, job, PAID)
        server.assert_state(f"{job.name}_starts_without_xp_and_queues_both_unbound_outputs",
                            pending(job, PAID, 0), results)
    for job in CONTROLS:
        feed(server, results, job, COST)
    advance(server, 40)
    verify_controls(server, results, "initial")
    server.command(f"data merge block {JOBS[0].pos(80)} {{{xp_tank(XP_RESERVE)}}}")
    server.command(f"forceload remove {CHUNKS}")
    unloaded = " ".join(f"unless loaded {job.pos(80)}" for job in ALL)
    server.assert_state("all_fixture_chunks_actually_unload", unloaded, results, timeout=45)
    load(server, results, "reloaded")
    verify_pending(server, results, "reloaded")


def verify_export(server, results, job, phase):
    pos, hopper = job.pos(80), job.pos(79)
    xp = XP_RESERVE if job == JOBS[0] else 0
    block = item(None, job.result, vanilla=True)
    vial = item(None, VIAL, vanilla=True)
    empty_slots = " ".join(f'unless data block {pos} Items.Items[{{Slot:{slot}}}]' for slot in range(4))
    server.assert_state(f"{phase}_{job.name}_exports_one_exact_block_and_empty_vial",
                        f'if data block {pos} {{EnergyStored:{FE_RESERVE}}} {xp_condition(pos, xp)} '
                        f'unless data block {pos} Task if data block {pos} Items.Items[{item(4, CAPACITOR)}] '
                        f'{empty_slots} if data block {hopper} {{Items:[{block},{vial}]}} unless data block {hopper} Items[2] '
                        f'unless data block {hopper} Items[{{id:"{job.result}"}}].tag '
                        f'unless data block {hopper} Items[{{id:"{VIAL}"}}].tag.{SOUL}',
                        results, timeout=15)


def finish_jobs(server, results):
    load(server, results, "reopened")
    verify_pending(server, results, "reopened")
    for job in JOBS:
        server.command(f"data merge block {job.pos(80)} {{EnergyStored:{COST - PAID - 1}}}")
    for job in JOBS:
        xp = XP_RESERVE if job == JOBS[0] else 0
        server.assert_state(f"{job.name}_cannot_release_either_output_one_fe_short",
                            pending(job, COST - 1, xp), results, timeout=30)
        server.command(f"setblock {job.pos(79)} minecraft:hopper[facing=down]")
        server.command(f"data merge block {job.pos(80)} {{EnergyStored:{FE_RESERVE + 1}}}")
        verify_export(server, results, job, "finished")


def reopened_exports(server, results):
    load(server, results, "saved_exports")
    for job in JOBS:
        verify_export(server, results, job, "saved_exports")
    verify_controls(server, results, "saved_exports")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", type=int, required=True)
    for key in ("java", "forge-server", "jar"):
        parser.add_argument(f"--{key}", type=Path, required=True)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("--name must name a single directory")

    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "jarSha256": digest(directory / "mods/enderio.jar"), "assertions": [], "servers": []}
    try:
        run_stage(args, directory, report, "fresh", start_jobs)
        run_stage(args, directory, report, "reopened", finish_jobs)
        run_stage(args, directory, report, "saved", reopened_exports)
        report["passed"] = True
    finally:
        output = directory / "silverfish-binding-results.json"
        output.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Silverfish binding passed={report['passed']}, {len(report['assertions'])} assertions, {output}", flush=True)


if __name__ == "__main__":
    main()
