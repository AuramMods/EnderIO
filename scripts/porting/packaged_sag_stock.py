#!/usr/bin/env python3
"""Grind every stock input bound by a packaged Forge profile and reopen the jobs."""

import argparse
from collections import Counter, defaultdict
from dataclasses import dataclass
from itertools import product
import json
from pathlib import Path
import re
import shutil
import sys

sys.dont_write_bytecode = True
from packaged_registry_audit import BASELINE, baseline_resources, check_recipes, packaged_resources, snapshot
from packaged_sag_progression import charge, data_read, feed, inputs, mill, output, prepare_mill
from packaged_server_smoke import ROOT, digest
from packaged_solar_charging import check
from packaged_spawners import prepare_profile, run_stage


TYPE = "enderio:sag_milling"
PAID, RESERVE = 600, 137
FIRST_X = 5600
STACK = re.compile(r'\{Item: \{id: "([a-z0-9:/_.-]+)", Count: (\d+)\}\}')


@dataclass(frozen=True)
class Grind:
    item: str
    x: int
    candidates: tuple[str, ...]
    cost: int

    @property
    def slot(self):
        return f'{{Slot:0,id:"{self.item}",Count:1,tag:{{StockSagInput:{self.x}}}}}'


def baseline_recipes():
    commit = json.loads(BASELINE.read_text())["baseline_commit"]
    resources, _ = baseline_resources(commit)
    return {key: recipe for key, recipe in resources["recipe"].items() if recipe["type"] == TYPE}


def members(key, tags):
    return [key["item"]] if "item" in key else tags.get(key["tag"], [])


def rolls(recipe, tags):
    choices = []
    for result in recipe["outputs"]:
        stack = result["item"]
        choices_for_result = [Counter({item: stack["count"]}) for item in members(
            {"item": stack["id"]} if "id" in stack else stack, tags)]
        if not choices_for_result or result.get("chance", 1) < 1:
            choices_for_result.append(Counter())
        choices.append(choices_for_result)
    return {tuple(sorted(sum(combo, Counter()).items())) for combo in product(*choices)}


def describe_stock(server, results, baseline, packaged, report):
    snapshot(server, results)
    runtime = json.loads((server.directory / "registry-snapshot.json").read_text())
    live = {key: kind for key, kind in runtime["recipes"].items() if kind == TYPE}
    audit = check_recipes(baseline, live, runtime, "neoforge:conditions")
    report["recipes"] = audit
    for field, expected in (("missing", []), ("extra", []), ("unexpectedlyActive", []), ("serializerDifferences", {})):
        check(f"stock_sag_recipe_registry_{field}", expected, audit[field], results)

    tags = runtime["tags"]["item"]
    candidates = defaultdict(list)
    for key, recipe in sorted(baseline.items()):
        for field in ("energy", "bonus", "outputs"):
            check(f"{key}_{field}_matches_the_fixed_baseline", recipe.get(field), packaged[key].get(field), results)
        if key not in live:
            continue
        ingredient = packaged[key]["input"]
        if "item" in recipe["input"]:
            check(f"{key}_keeps_the_baseline_item_input", recipe["input"], ingredient, results)
        observed = members(ingredient, tags)
        if not observed:
            raise AssertionError(f"Live recipe {key} has no input in {ingredient}")
        for item in observed:
            candidates[item].append(key)

    all_registered = set(runtime["tagRegistryIds"]["item"])
    check("every_grind_input_has_a_live_item_id", [], sorted(candidates.keys() - all_registered), results)
    report["overlappingInputs"] = {item: ids for item, ids in candidates.items() if len(ids) > 1}
    cases = []
    for index, (item, ids) in enumerate(sorted(candidates.items())):
        cost, = {baseline[key]["energy"] for key in ids}
        cases.append(Grind(item, FIRST_X + index * 2, tuple(ids), cost))
    return cases, tags


def paused(case):
    pos = mill(case.x)
    return (f'if data block {pos} {{EnergyStored:0,Items:{{Items:[{case.slot}]}},'
            f'Task:{{ProgressMade:{PAID},ProgressRequired:{case.cost},HasDeterminedOutputs:1b}}}} '
            f'unless data block {pos} GrindingBal unless data block {feed(case.x)} Items[0] '
            f'unless data block {output(case.x)} Items[0]')


def parse_roll(raw):
    stacks = list(STACK.finditer(raw))
    if raw != "[" + ", ".join(stack.group(0) for stack in stacks) + "]":
        raise AssertionError(f"Unexpected stock output stack format {raw}")
    return sum((Counter({stack[1]: int(stack[2])}) for stack in stacks), Counter())


def load(server, results, cases, phase):
    end = cases[-1].x
    server.command(f"forceload add {FIRST_X} 0 {end} 15")
    server.assert_state(f"stock_mill_chunks_loaded_{phase}", f"if loaded {mill(FIRST_X)} if loaded {mill(end)}", results)


def begin(server, results, cases, baseline, tags, report):
    load(server, results, cases, "fresh")
    server.command("gamerule doMobSpawning false")
    for case in cases:
        prepare_mill(server, case.x)
        inputs(server, case.x, f"{case.item}{{StockSagInput:{case.x}}}", 1)
        charge(server, case.x, PAID)
    for case in cases:
        server.assert_state(f"{case.item}_loads_from_its_hopper_and_saves_paid_progress", paused(case), results, timeout=20)
        selected = json.loads(data_read(server, mill(case.x), "Task.RecipeId"))
        check(f"{case.item}_selects_a_matching_stock_recipe", True, selected in case.candidates, results)
        raw = data_read(server, mill(case.x), "Task.Outputs")
        observed = parse_roll(raw)
        legal = rolls(baseline[selected], tags)
        check(f"{case.item}_roll_follows_the_baseline_outputs", True, tuple(sorted(observed.items())) in legal, results)
        report["grinds"][case.item] = {"recipe": selected, "position": mill(case.x), "energyCost": case.cost,
                                      "outputs": observed, "queued": raw}


def finish(server, results, cases, report):
    load(server, results, cases, "finish")
    for case in cases:
        saved = report["grinds"][case.item]
        pos = mill(case.x)
        conditions = (f'{paused(case)} if data block {pos} Task{{RecipeId:"{saved["recipe"]}",Outputs:{saved["queued"]}}} '
                      f'unless data block {pos} Task.Outputs[{len(saved["outputs"])}]')
        server.assert_state(f"{case.item}_reloads_its_original_input_cost_recipe_and_roll", conditions, results)
        charge(server, case.x, case.cost - PAID + RESERVE)
    completed(server, results, cases, report, "finished")


def completed(server, results, cases, report, phase):
    for case in cases:
        pos, hopper = mill(case.x), output(case.x)
        exported = report["grinds"][case.item]["outputs"]
        checks = [f"if data block {pos} {{EnergyStored:{RESERVE}}}", f"unless data block {pos} Task"]
        checks.extend(f"unless data block {pos} Items.Items[{{Slot:{slot}}}]" for slot in range(6))
        checks.extend(f'if data block {hopper} Items[{{id:"{item}",Count:{count}b}}]' for item, count in exported.items())
        checks.append(f"unless data block {hopper} Items[{len(exported)}]")
        server.assert_state(f"{case.item}_{phase}_exports_the_exact_roll_with_137_fe_left",
                            " ".join(checks), results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--mod", action="append", default=[], type=Path)
    for path in ("java", "forge-server", "jar", "probe"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")

    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    for mod in (args.probe, *args.mod):
        shutil.copyfile(mod, directory / "mods" / mod.name)
    baseline = baseline_recipes()
    packaged, _, _ = packaged_resources(args.jar)
    report = {"passed": False, "assertions": [], "servers": [], "grinds": {},
              "sha256": {mod.name: digest(mod) for mod in sorted((directory / "mods").glob("*.jar"))}}
    cases = []

    def fresh(server, results):
        stock, tags = describe_stock(server, results, baseline, packaged, report)
        cases.extend(stock)
        begin(server, results, cases, baseline, tags, report)

    def reopened(server, results):
        load(server, results, cases, "reopened")
        completed(server, results, cases, report, "reopened")

    try:
        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "finish", lambda server, results: finish(server, results, cases, report))
        run_stage(args, directory, report, "reopened", reopened)
        report["passed"] = True
    finally:
        result = directory / "sag-stock-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Stock SAG Mill passed={report['passed']}, {len(report['grinds'])} grinds, "
              f"{len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
