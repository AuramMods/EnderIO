#!/usr/bin/env python3
"""Exercise the four physical redstone modes with stock VAT recipes on packaged Forge."""

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import sys

sys.dont_write_bytecode = True
from packaged_levitation_staff import game_tick
from packaged_nutritious_stick import advance
from packaged_sag_progression import data_read
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage
from packaged_vat_automation import Batch, HOOTCH, WATER, drained, fluid, tank_fields


CHUNKS = "5120 0 5151 15"


@dataclass(frozen=True)
class Trial:
    batch: Batch
    mode: str
    ticks: int

    @property
    def power(self):
        return f"{self.batch.x} 81 8"

    @property
    def recipe(self):
        return f"enderio:fermenting/fluid_{self.batch.name}_still"


INVERTED = Trial(Batch(5124, "rocket_fuel", HOOTCH, 1000, ("minecraft:redstone", "minecraft:gunpowder"), 250),
                 "active_without_signal", 400)
SIGNAL = Trial(Batch(5132, "cloud_seed", WATER, 1000, ("minecraft:prismarine_shard", "minecraft:snow"), 120),
               "active_with_signal", 400)
ALWAYS = Trial(Batch(5140, "nutrient_distillation", WATER, 1000, ("minecraft:cooked_chicken", "minecraft:sugar_cane"), 125),
               "always_active", 200)
NEVER = Trial(Batch(5148, "liquid_sunshine", WATER, 1000, ("minecraft:sunflower", "minecraft:glow_berries"), 150),
              "never_active", 200)
TRIALS = (INVERTED, SIGNAL, ALWAYS, NEVER)
PARTIAL = (INVERTED, SIGNAL)


def set_signal(server, trial, powered):
    block = "minecraft:redstone_block" if powered else "minecraft:air"
    server.command(f"setblock {trial.power} {block}")


def signal_state(trial, powered):
    block = "minecraft:redstone_block" if powered else "minecraft:air"
    return f"if block {trial.power} {block}"


def fill(server, trial, powered):
    batch = trial.batch
    server.command(f'setblock {batch.vat} enderio:vat[facing=south]{{RedstoneControl:"{trial.mode}",'
                   'IOConfig:{Modes:{north:"pull",south:"push"}}}')
    if powered:
        set_signal(server, trial, True)
    server.command(f'setblock {batch.supply} enderio:fluid_tank[facing=south]'
                   f'{{{tank_fields(fluid(batch.source, batch.input_amount))},IOConfig:{{Modes:{{south:"push"}}}}}}')
    server.command(f"setblock {batch.receiver} enderio:fluid_tank[facing=south]")
    for slot, offset, direction in ((0, -1, "east"), (1, 1, "west")):
        server.command(f'setblock {batch.x + offset} 80 8 minecraft:hopper[facing={direction}]'
                       f'{{Items:[{{Slot:0b,id:"{batch.reagents[slot]}",Count:1b,tag:{{PortVAT:{slot + 1}}}}}]}}')


def inventory(trial):
    return ",".join(f'{{id:"{reagent}",Count:1,tag:{{PortVAT:{index + 1}}}}}'
                    for index, reagent in enumerate(trial.batch.reagents))


def queued(trial):
    batch = trial.batch
    return (f'if data block {batch.vat} {{RedstoneControl:"{trial.mode}",'
            f'{tank_fields(fluid(batch.source, batch.input_amount))},Items:{{Items:[{inventory(trial)}]}},'
            f'IOConfig:{{Modes:{{north:"pull",south:"push"}}}}}} '
            f'{drained(batch.supply)} {drained(batch.vat, 1)} {drained(batch.receiver)} '
            f'unless data block {batch.vat} PendingOutput '
            f'unless data block {batch.x - 1} 80 8 Items[0] unless data block {batch.x + 1} 80 8 Items[0]')


def partial(trial, progress):
    vat = trial.batch.vat
    output = fluid(trial.batch.output, trial.batch.output_amount)
    return (f'{queued(trial)} if data block {vat} '
            f'Task{{RecipeId:"{trial.recipe}",ProgressMade:{progress},ProgressRequired:{trial.ticks},'
            f'HasDeterminedOutputs:1b,Outputs:[{{Fluid:{output}}}]}}')


def inactive(trial):
    return f"if block {trial.batch.vat} enderio:vat[powered=false]"


def done(trial):
    batch = trial.batch
    output = tank_fields(fluid(batch.output, batch.output_amount))
    return (f'if data block {batch.receiver} {{{output}}} {drained(batch.supply)} '
            f'{drained(batch.vat)} {drained(batch.vat, 1)} '
            f'unless data block {batch.vat} Task unless data block {batch.vat} PendingOutput '
            f'unless data block {batch.vat} Items.Items[0]')


def progress_at(server, trial):
    return int(data_read(server, trial.batch.vat, "Task.ProgressMade"))


def require_delta(server, report, trial, name, previous, previous_tick):
    observed = progress_at(server, trial)
    elapsed = game_tick(server) - previous_tick
    passed = 0 < observed - previous <= elapsed < observed < trial.ticks
    report["assertions"].append({"assertion": name, "passed": passed, "before": previous,
                                 "after": observed, "elapsed": elapsed})
    if not passed:
        raise AssertionError(f"{trial.batch.name} advanced from {previous} to {observed} over {elapsed} game ticks")


def assert_disabled(server, results, name, powered):
    vat = NEVER.batch.vat
    # Loading a matching recipe can create an idle task before the VAT is allowed to tick.
    server.command("scoreboard players set #disabled port_vat 0")
    server.command(f"execute if data block {vat} Task.ProgressMade store result score #disabled port_vat "
                   f"run data get block {vat} Task.ProgressMade")
    server.assert_state(name, f"{signal_state(NEVER, powered)} {inactive(NEVER)} {queued(NEVER)} "
                        f"if score #disabled port_vat matches 0 "
                        f"unless data block {vat} Task{{HasDeterminedOutputs:1b}}", results)


def assert_paused(server, report, phase):
    results = report["assertions"]
    for trial in PARTIAL:
        progress = report["pausedTicks"][trial.batch.name]
        server.assert_state(f"{phase}_{trial.mode}_keeps_its_partial_fermentation",
                            f"{signal_state(trial, trial is INVERTED)} {inactive(trial)} {partial(trial, progress)}", results)
    assert_disabled(server, results, f"{phase}_disabled_vat_keeps_all_inputs_with_signal", True)


def suspend(server, report):
    results = report["assertions"]
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    server.command("scoreboard objectives add port_vat dummy")
    server.command(f"forceload add {CHUNKS}")
    server.assert_state("vat_chunks_are_loaded", loaded(), results)
    for trial in TRIALS:
        fill(server, trial, trial in (SIGNAL, ALWAYS))
    for trial in TRIALS:
        server.assert_state(f"{trial.mode}_accepts_real_hopper_and_sided_tank_inputs", queued(trial), results)

    advance(server, 40)
    for trial in (INVERTED, SIGNAL, ALWAYS):
        server.assert_state(f"{trial.mode}_ferments_in_its_initial_signal_state",
                            f"if block {trial.batch.vat} enderio:vat[powered=true] "
                            f"{signal_state(trial, trial in (SIGNAL, ALWAYS))}", results)
    assert_disabled(server, results, "disabled_vat_is_idle_with_no_signal", False)
    tick = game_tick(server)
    before = progress_at(server, ALWAYS)
    set_signal(server, ALWAYS, False)
    advance(server, 16)
    require_delta(server, report, ALWAYS, "always_active_does_not_reset_when_signal_is_removed", before, tick)

    set_signal(server, INVERTED, True)
    set_signal(server, SIGNAL, False)
    set_signal(server, NEVER, True)
    advance(server, 10)
    for trial in PARTIAL:
        progress = progress_at(server, trial)
        if not 40 <= progress < trial.ticks:
            raise AssertionError(f"{trial.mode} paused outside the partial batch: {progress}/{trial.ticks}")
        report["pausedTicks"][trial.batch.name] = progress
    assert_paused(server, report, "paused")
    advance(server, 60)
    assert_paused(server, report, "after_60_loaded_ticks")
    server.assert_state("always_active_completes_its_scaled_batch", done(ALWAYS), results, timeout=30)


def loaded(qualifier="if"):
    return " ".join(f"{qualifier} loaded {trial.batch.vat}" for trial in TRIALS)


def resume(server, report):
    results = report["assertions"]
    assert_paused(server, report, "reopened")
    advance(server, 45)
    assert_paused(server, report, "after_45_reopened_ticks")
    server.command(f"forceload remove {CHUNKS}")
    server.assert_state("both_vat_chunks_are_really_evicted", loaded("unless"), results, timeout=45)
    server.command(f"forceload add {CHUNKS}")
    server.assert_state("both_vat_chunks_return", loaded(), results)
    assert_paused(server, report, "after_chunk_reload")

    for trial in PARTIAL:
        tick = game_tick(server)
        set_signal(server, trial, trial is SIGNAL)
        advance(server, 16)
        require_delta(server, report, trial, f"{trial.mode}_resumes_saved_ticks_on_signal_edge",
                      report["pausedTicks"][trial.batch.name], tick)
    server.command(f'data merge block {NEVER.batch.vat} {{RedstoneControl:"always_active"}}')
    server.assert_state("formerly_disabled_vat_starts_with_its_original_queued_inputs",
                        f"if block {NEVER.batch.vat} enderio:vat[powered=true] if data block {NEVER.batch.vat} Task",
                        results)
    for trial in TRIALS:
        server.assert_state(f"{trial.batch.name}_exports_one_exact_batch", done(trial), results, timeout=45)


def finished(server, report):
    advance(server, 40)
    for trial in TRIALS:
        server.assert_state(f"restart_keeps_only_the_expected_{trial.batch.name}_output", done(trial), report["assertions"])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", type=int, required=True)
    for path in ("java", "forge-server", "jar"):
        parser.add_argument(f"--{path}", type=Path, required=True)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    root = ROOT / "build/port-runtime" / args.name
    root.mkdir()
    profile = root / "server"
    report = {"passed": False, "jarSha256": digest(args.jar), "assertions": [], "servers": [], "pausedTicks": {}}
    try:
        prepare_profile(args, profile, args.jar)
        for name, scenario in (("suspend", suspend), ("reopened", resume), ("finished", finished)):
            run_stage(args, profile, report, name, lambda server, _: scenario(server, report))
        report["passed"] = True
    finally:
        output = root / "vat-redstone-results.json"
        output.write_text(json.dumps(report, indent=2) + "\n")
        print(f"VAT redstone passed={report['passed']}, {len(report['assertions'])} assertions, {output}", flush=True)


if __name__ == "__main__":
    main()
