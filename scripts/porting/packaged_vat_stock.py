#!/usr/bin/env python3
"""Ferment every stock reagent pairing in both orders on packaged Forge."""

import argparse
from dataclasses import asdict, dataclass
from itertools import product
import json
from pathlib import Path
import shutil
import sys

sys.dont_write_bytecode = True
from packaged_nutritious_stick import advance
from packaged_registry_audit import BASELINE, baseline_resources, check_recipes, packaged_resources, snapshot
from packaged_rule_reload import stock_data
from packaged_sag_progression import data_read
from packaged_server_smoke import ROOT, digest
from packaged_solar_charging import check
from packaged_spawners import prepare_profile, run_stage
from packaged_vat_automation import WATER, drained, fluid, tank_fields


TYPE = "enderio:vat_fermenting"
REAGENT_MAP = "enderio:data_maps/item/vat_reagent.json"
CAPACITY, RESERVE = 32_000, 137
FIRST_X, FIRST_Z, COLUMNS = 6004, 8, 24


@dataclass(frozen=True)
class Trial:
    name: str
    recipe: str
    x: int
    z: int
    reagents: tuple[str, str]
    modifiers: tuple[float, float]
    source: str
    input_amount: int
    result: str
    output_amount: int
    ticks: int

    def at(self, dx=0, dz=0, y=80):
        return f"{self.x + dx} {y} {self.z + dz}"


def recipes_in(resources):
    return {key: value for key, value in resources.items() if value["type"] == TYPE}


def describe_stock(server, results, baseline, packaged, report):
    snapshot(server, results)
    runtime = json.loads((server.directory / "registry-snapshot.json").read_text())
    live = {key: kind for key, kind in runtime["recipes"].items() if kind == TYPE}
    audit = check_recipes(baseline, live, runtime, "neoforge:conditions")
    report["recipeAudit"] = audit
    for field, expected in (("missing", []), ("extra", []), ("unexpectedlyActive", []), ("serializerDifferences", {})):
        check(f"stock_vat_registry_{field}", expected, audit[field], results)

    stock_maps, _ = stock_data(runtime)
    modifiers = stock_maps[REAGENT_MAP]
    check("runtime_reagent_modifiers_match_the_fixed_baseline", modifiers, runtime["dataMaps"][REAGENT_MAP], results)
    tags = runtime["tags"]
    trials = []
    for key, recipe in sorted(baseline.items()):
        active = packaged[key]
        expected = {field: value if value != "c:dusts/redstone" else "forge:dusts/redstone" for field, value in recipe.items()}
        check(f"{key}_matches_the_fixed_baseline_with_forge_tags", expected, active, results)
        left, right = (active[field] for field in ("left_reagent", "right_reagent"))
        ingredients = tags["item"][left], tags["item"][right]
        check(f"{key}_has_two_distinct_active_reagent_groups", True,
              bool(ingredients[0] and ingredients[1]) and not set(ingredients[0]).intersection(ingredients[1]), results)
        source = recipe["input"].get("fluid", WATER)
        if "tag" in recipe["input"]:
            check(f"{key}_accepts_source_water", True, source in tags["fluid"][recipe["input"]["tag"]], results)
        for first, second in product(*ingredients):
            factors = (modifiers.get(first, {}).get(f"#{left}", 1.0), modifiers.get(second, {}).get(f"#{right}", 1.0))
            for reagents, order in (((first, second), factors), ((second, first), factors[::-1])):
                row, column = divmod(len(trials), COLUMNS)
                name = f"{key.split('/')[-1]}_{reagents[0]}_{reagents[1]}"
                trials.append(Trial(name, key, FIRST_X + column * 5, FIRST_Z + row * 6, reagents, order,
                                    source, recipe["input"]["amount"], recipe["output"]["id"],
                                    int(recipe["output"]["amount"] * (order[0] * order[1])), recipe["ticks"]))
    report["trials"] = {trial.name: asdict(trial) for trial in trials}
    return trials


def commands(server, lines):
    # One native console marker per group keeps the setup and redstone changes close in game time.
    for start in range(0, len(lines), 36):
        server.command("\n".join(lines[start:start + 36]))


def load(server, results, trials, stage):
    last_x, last_z = max(trial.x for trial in trials), max(trial.z for trial in trials)
    server.command(f"forceload add {FIRST_X - 1} {FIRST_Z - 1} {last_x + 1} {last_z + 2}")
    server.assert_state(f"stock_vat_chunks_loaded_{stage}",
                        f"if loaded {FIRST_X} 80 {FIRST_Z} if loaded {last_x} 80 {last_z}", results)


def hopper(trial, slot):
    return trial.at(dx=(-1, 1)[slot])


def hopper_command(trial, slot):
    facing = ("east", "west")[slot]
    return (f'setblock {hopper(trial, slot)} minecraft:hopper[facing={facing}]'
            f'{{Items:[{{Slot:0b,id:"{trial.reagents[slot]}",Count:1b,tag:{{VatStockSlot:{slot}}}}}]}}')


def reagent(trial, slot):
    return f'{{Slot:{slot},id:"{trial.reagents[slot]}",Count:1,tag:{{VatStockSlot:{slot}}}}}'


def tank(pos, kind, amount):
    return f"if data block {pos} {{{tank_fields(fluid(kind, amount))}}}"


def drained_feeders(trial):
    return (f'{drained(trial.at(dz=-1))} unless data block {hopper(trial, 0)} Items[0] '
            f'unless data block {hopper(trial, 1)} Items[0]')


def queued(trial, *, verify_feeders=True):
    vat = trial.at()
    feeders = f"{drained_feeders(trial)} " if verify_feeders else ""
    return (f'if data block {vat} {{RedstoneControl:"active_with_signal",IOConfig:{{Modes:{{north:"pull",south:"push"}}}},'
            f'{tank_fields(fluid(trial.source, trial.input_amount + RESERVE))},'
            f'Items:{{Items:[{reagent(trial, 0)},{reagent(trial, 1)}]}}}} '
            f'{feeders}{drained(vat, 1)} {drained(trial.at(dz=1))} '
            f'unless data block {vat} PendingOutput')


def paused(trial, ticks, *, verify_feeders=True):
    vat = trial.at()
    return (f'{queued(trial, verify_feeders=verify_feeders)} '
            f'if block {vat} enderio:vat[powered=false] if block {trial.at(y=81)} minecraft:air '
            f'if data block {vat} Task{{RecipeId:"{trial.recipe}",ProgressMade:{ticks},ProgressRequired:{trial.ticks},'
            f'HasDeterminedOutputs:1b,Outputs:[{{Fluid:{fluid(trial.result, trial.output_amount)}}}]}}')


def signal(server, trials, enabled):
    block = "minecraft:redstone_block" if enabled else "minecraft:air"
    commands(server, [f"setblock {trial.at(y=81)} {block}" for trial in trials])


def begin(server, results, trials, report):
    load(server, results, trials, "fresh")
    server.command("gamerule doMobSpawning false")
    setup = []
    for trial in trials:
        supply = tank_fields(fluid(trial.source, trial.input_amount + RESERVE))
        setup.extend((
            f'setblock {trial.at()} enderio:vat[facing=south]{{RedstoneControl:"active_with_signal",'
            'IOConfig:{Modes:{north:"pull",south:"push"}}}',
            f'setblock {trial.at(dz=-1)} enderio:fluid_tank[facing=south]{{{supply},IOConfig:{{Modes:{{south:"push"}}}}}}',
            f"setblock {trial.at(dz=1)} enderio:pressurized_fluid_tank[facing=south]",
            hopper_command(trial, 0),
        ))
    commands(server, setup)
    for trial in trials:
        server.assert_state(f"{trial.name}_first_hopper_selects_slot_zero",
                            f"if data block {trial.at()} Items.Items[{reagent(trial, 0)}] "
                            f"unless data block {trial.at()} Items.Items[{{Slot:1}}]", results)
    commands(server, [hopper_command(trial, 1) for trial in trials])
    for trial in trials:
        server.assert_state(f"{trial.name}_hoppers_and_sided_tank_finish_loading_before_power", queued(trial), results)
    signal(server, trials, True)
    advance(server, 50)
    signal(server, trials, False)
    for trial in trials:
        progress = int(data_read(server, trial.at(), "Task.ProgressMade"))
        check(f"{trial.name}_made_real_partial_progress", True, 0 < progress < trial.ticks, results)
        server.assert_state(f"{trial.name}_saves_the_recipe_yield_and_exact_reagent_order", paused(trial, progress), results)
        report["trials"][trial.name]["pausedTicks"] = progress


def receiving_tanks(trial):
    remaining = trial.output_amount
    for dx in (-1, 1):
        if remaining <= CAPACITY:
            break
        yield trial.at(dx=dx, dz=2), CAPACITY
        remaining -= CAPACITY
    yield trial.at(dz=1), remaining


def finish_large_batches(server, results, trials):
    for trial in trials:
        receiver = trial.at(dz=1)
        tanks = list(receiving_tanks(trial))
        for index, (archive, amount) in enumerate(tanks[:-1], 1):
            server.assert_state(f"{trial.name}_fills_pressure_receiver_{index}", tank(receiver, trial.result, amount),
                                results, timeout=45)
            server.command(f"clone {receiver} {receiver} {archive} replace move")
            server.command(f"setblock {receiver} enderio:pressurized_fluid_tank[facing=south]")


def completed(server, results, trials, stage, *, verify_feeders=True):
    for trial in trials:
        vat = trial.at()
        outputs = " ".join(tank(pos, trial.result, amount) for pos, amount in receiving_tanks(trial))
        feeders = f" {drained_feeders(trial)}" if verify_feeders else ""
        server.assert_state(f"{trial.name}_{stage}_exports_the_exact_batch_and_retains_only_the_fluid_reserve",
                            f"{outputs} {tank(vat, trial.source, RESERVE)} {drained(vat, 1)} "
                            f"unless data block {vat} Task unless data block {vat} PendingOutput "
                            f"unless data block {vat} Items.Items[0]{feeders}", results, timeout=40)


def resume(server, results, trials, report, *, verify_feeders=True):
    load(server, results, trials, "resume")
    for trial in trials:
        progress = report["trials"][trial.name]["pausedTicks"]
        server.assert_state(f"{trial.name}_restores_its_saved_progress_inputs_and_yield",
                            paused(trial, progress, verify_feeders=verify_feeders), results)
    signal(server, trials, True)
    finish_large_batches(server, results, trials)
    completed(server, results, trials, "resumed", verify_feeders=verify_feeders)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for path in ("java", "forge-server", "jar", "probe"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    shutil.copyfile(args.probe, directory / "mods" / args.probe.name)
    commit = json.loads(BASELINE.read_text())["baseline_commit"]
    baseline, _ = baseline_resources(commit)
    packaged, _, _ = packaged_resources(args.jar)
    report = {"passed": False, "jarSha256": digest(args.jar), "probeSha256": digest(args.probe),
              "assertions": [], "servers": [], "trials": {}}
    trials = []

    def fresh(server, results):
        trials.extend(describe_stock(server, results, recipes_in(baseline["recipe"]), recipes_in(packaged), report))
        begin(server, results, trials, report)

    def reopened(server, results):
        load(server, results, trials, "reopened")
        completed(server, results, trials, "reopened")

    try:
        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "resume", lambda server, results: resume(server, results, trials, report))
        run_stage(args, directory, report, "reopened", reopened)
        report["passed"] = True
    finally:
        path = directory / "vat-stock-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Stock VAT passed={report['passed']}, {len(report['trials'])} orders, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
