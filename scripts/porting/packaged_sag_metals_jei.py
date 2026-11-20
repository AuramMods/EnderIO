#!/usr/bin/env python3
"""Compare live SAG Mill cards and grind every Mekanism and IE input on packaged Forge."""

import argparse
from dataclasses import asdict, replace
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_registry_audit import packaged_resources
from packaged_sag_progression import charge, data_read, feed, mill, prepare_mill
from packaged_sag_stock import FIRST_X, PAID, baseline_recipes, completed, describe_stock, finish, load, parse_roll, paused, rolls
from packaged_server_smoke import ROOT, digest
from packaged_solar_charging import check
from packaged_spawners import prepare_profile, run_stage
from packaged_starter_progression import PLAYER


METALS = ("mekanism:", "immersiveengineering:")


def prepare(server, results, cases):
    load(server, results, cases, "jei")
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    server.command(f"fill {FIRST_X - 1} 79 5 {cases[-1].x + 1} 79 13 minecraft:stone_bricks")
    server.command(f"setworldspawn {FIRST_X} 80 11")
    for case in cases:
        prepare_mill(server, case.x)


def record_batch(server, results, case, catalog, report):
    pos = mill(case.x)
    server.assert_state(f"{case.item}_transfers_one_tagged_item_and_leaves_its_extra_in_inventory",
                        f'if data block {pos} {{EnergyStored:0,Items:{{Items:[{case.slot}]}}}} '
                        f'unless data block {feed(case.x)} Items[0] '
                        f'if data entity {PLAYER} Inventory[{{Slot:1b,id:"{case.item}",Count:1b,tag:{{StockSagInput:{case.x}}}}}]',
                        results)
    charge(server, case.x, PAID)
    server.assert_state(f"{case.item}_spends_600_fe_and_saves_the_roll", paused(case), results)
    selected = json.loads(data_read(server, pos, "Task.RecipeId"))
    check(f"{case.item}_runs_the_same_recipe_the_player_chose_in_jei", case.candidates[0], selected, results)
    raw = data_read(server, pos, "Task.Outputs")
    observed = parse_roll(raw)
    outputs = ({"item": {"id": entry["item"], "count": entry["count"]}, "chance": entry["chance"]}
               for entry in catalog[selected]["outputs"] if entry["count"])
    allowed = rolls({"outputs": list(outputs)}, {})
    check(f"{case.item}_roll_uses_only_the_exact_jei_card_products", True, tuple(sorted(observed.items())) in allowed, results)
    report["grinds"][case.item] = {"recipe": selected, "position": pos, "energyCost": case.cost,
                                  "outputs": observed, "queued": raw}


def exercise(args, server, report, cases, catalog):
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "sag-jei-metals", "--output", str(evidence), "--server", f"127.0.0.1:{args.port}",
               "--timeout", "260"]
    results = report["assertions"]
    with (server.directory / "sag-metals-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            pending = evidence / "sag-metals.tmp"
            pending.write_text(json.dumps({"mills": [asdict(case) for case in cases], "recipes": catalog}) + "\n")
            pending.rename(evidence / "sag-metals.json")
            for index, case in enumerate(cases):
                server.command(f"clear {PLAYER}")
                server.command(f"tp {PLAYER} {case.x + 0.5} 80 10.5 180 15")
                server.command(f"item replace entity {PLAYER} hotbar.1 with {case.item}{{StockSagInput:{case.x}}} 2")
                (evidence / f"server-{index}-ready").touch()
                wait_signal(client, evidence, f"transferred-{index}")
                record_batch(server, results, case, catalog, report)
                (evidence / f"server-{index}-paused").touch()
                wait_signal(client, evidence, f"observed-{index}")
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"SAG Mill metals JEI client failed; see {evidence}")
            r = json.loads((evidence / "summary.json").read_text())
            mods = next(iter(r["client"]["captures"].values()))["loadedMods"]
            for mod_id in ("enderio", "jei", "mekanism", "immersiveengineering"):
                jar = Path(mods[mod_id]["file"])
                expected = report["sha256"]["enderio.jar" if mod_id == "enderio" else jar.name]
                check(f"{mod_id}_uses_identical_client_and_server_bytes", expected, digest(jar), results)
        finally:
            stop_client(client)
            summary = evidence / "summary.json"
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--mod", action="append", default=[], type=Path)
    for path in ("java", "forge-server", "jar", "probe", "launch"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    for mod in (args.probe, *args.mod):
        shutil.copyfile(mod, directory / "mods" / mod.name)
    report = {"passed": False, "assertions": [], "servers": [], "grinds": {},
              "sha256": {mod.name: digest(mod) for mod in sorted((directory / "mods").glob("*.jar"))}}
    baseline = baseline_recipes()
    packaged, _, _ = packaged_resources(args.jar)
    cases = []

    def fresh(server, results):
        stock, _ = describe_stock(server, results, baseline, packaged, report)
        runtime = json.loads((server.directory / "registry-snapshot.json").read_text())
        check("optional_forge_mods_load_on_the_server", True,
              {"jei", "mekanism", "immersiveengineering"} <= set(runtime["mods"]), results)
        catalog = runtime["sagRecipes"]
        check("all_baseline_sag_recipes_have_live_resolved_outputs", sorted(baseline), sorted(catalog), results)
        inputs = [case for case in stock if case.item.startswith(METALS)]
        cases.extend(replace(case, x=FIRST_X + index * 6) for index, case in enumerate(inputs))
        prepare(server, results, cases)
        exercise(args, server, report, cases, catalog)

    def saved(server, results):
        load(server, results, cases, "saved")
        completed(server, results, cases, report, "saved")

    try:
        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "finish", lambda server, results: finish(server, results, cases, report))
        run_stage(args, directory, report, "saved", saved)
        report["passed"] = True
    finally:
        path = directory / "sag-metals-jei-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"SAG Mill metals JEI passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
