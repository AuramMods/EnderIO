#!/usr/bin/env python3
"""Check all stock VAT cards and ferment the JEI-transferred batches on packaged Forge."""

import argparse
from dataclasses import asdict, replace
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_legacy_painting import stack
from packaged_nutritious_stick import advance
from packaged_registry_audit import BASELINE, baseline_resources, packaged_resources
from packaged_sag_progression import data_read
from packaged_server_smoke import ROOT, digest
from packaged_solar_charging import check
from packaged_spawners import prepare_profile, run_stage
from packaged_starter_progression import PLAYER
from packaged_vat_automation import fluid, tank_fields
from packaged_vat_stock import (FIRST_X, FIRST_Z, REAGENT_MAP, RESERVE, completed, describe_stock, load, paused, queued,
                                recipes_in, resume)


def select_cards(server, results, args, report):
    commit = json.loads(BASELINE.read_text())["baseline_commit"]
    resources, _ = baseline_resources(commit)
    packed, _, _ = packaged_resources(args.jar)
    baseline, packaged = recipes_in(resources["recipe"]), recipes_in(packed)
    audit = {}
    all_trials = describe_stock(server, results, baseline, packaged, audit)
    report["recipeAudit"] = audit["recipeAudit"]
    snapshot = json.loads((server.directory / "registry-snapshot.json").read_text())
    tags = snapshot["tags"]["item"]
    modifiers = snapshot["dataMaps"][REAGENT_MAP]
    cards, trials = [], []
    for index, (key, recipe) in enumerate(sorted(packaged.items())):
        candidates = (trial for trial in all_trials if trial.recipe == key)
        # On a tie, prefer the reversed Blue Ice batch to exercise an occupied left slot.
        selected = max(candidates, key=lambda trial: (trial.output_amount, trial.reagents[0] == "minecraft:blue_ice"))
        trial = replace(selected, name=key.rsplit("/", 1)[1], x=FIRST_X + 8 * index, z=FIRST_Z)
        groups = [recipe["left_reagent"], recipe["right_reagent"]]
        reverse = trial.reagents[0] not in tags[groups[0]]
        cards.append({**asdict(trial), "base_amount": recipe["output"]["amount"], "reversed": reverse,
                      "groups": [{item: modifiers.get(item, {}).get(f"#{group}", 1.0) for item in tags[group]}
                                 for group in groups]})
        trials.append(trial)
    return trials, cards


def prepare(server, results, trials, cards):
    load(server, results, trials, "menu")
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    server.command(f"fill {FIRST_X - 1} 79 5 {trials[-1].x + 1} 79 12 minecraft:stone_bricks")
    server.command(f"setworldspawn {FIRST_X} 80 11")
    for trial, card in zip(trials, cards, strict=True):
        input_tank = tank_fields(fluid(trial.source, trial.input_amount + RESERVE))
        seed = ""
        if card["reversed"]:
            seed = f',Items:{{Items:[{stack(0, 1, trial.reagents[0], metadata="{VatStockSlot:0}")}]}}'
        server.command(f'setblock {trial.at()} enderio:vat[facing=south]{{RedstoneControl:"active_with_signal",'
                       f'IOConfig:{{Modes:{{north:"pull",south:"push"}}}},{input_tank}{seed}}}')
        server.command(f"setblock {trial.at(dz=1)} enderio:pressurized_fluid_tank[facing=south]")


def exercise(args, server, report, trials, cards):
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "vat-jei-stock", "--output", str(evidence), "--server", f"127.0.0.1:{args.port}",
               "--timeout", "240"]
    results = report["assertions"]
    with (server.directory / "vat-jei-stock-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            pending = evidence / "stock-vat-cards.tmp"
            pending.write_text(json.dumps(cards) + "\n")
            pending.rename(evidence / "stock-vat-cards.json")
            for trial, card in zip(trials, cards, strict=True):
                server.command(f"clear {PLAYER}")
                server.command(f"tp {PLAYER} {trial.x + 0.5} 80 10.5 180 15")
                for slot, item in enumerate(trial.reagents):
                    server.command(f"item replace entity {PLAYER} hotbar.{slot + 1} with {item}{{VatStockSlot:{slot}}} 2")
                (evidence / f"server-{trial.name}-ready").touch()
                wait_signal(client, evidence, f"{trial.name}-transferred")
                held = [stack(slot + 1, 2 if card["reversed"] and slot == 0 else 1, item, metadata=f"{{VatStockSlot:{slot}}}",
                              slot_suffix="b", count_suffix="b") for slot, item in enumerate(trial.reagents)]
                server.assert_state(f"{trial.name}_transfers_the_exact_slots_and_keeps_player_surplus",
                                    f"{queued(trial, verify_feeders=False)} "
                                    f"if data entity {PLAYER} {{Inventory:[{','.join(held)}]}}", results)
                server.command(f"setblock {trial.at(y=81)} minecraft:redstone_block")
                advance(server, 20)
                server.command(f"setblock {trial.at(y=81)} minecraft:air")
                progress = int(data_read(server, trial.at(), "Task.ProgressMade"))
                check(f"{trial.name}_stops_within_the_recipe_duration", True, 0 < progress < trial.ticks, results)
                server.assert_state(f"{trial.name}_queues_the_exact_modifier_adjusted_yield",
                                    paused(trial, progress, verify_feeders=False), results)
                report["trials"][trial.name]["pausedTicks"] = progress
                pending.write_text(str(progress))
                pending.rename(evidence / f"server-{trial.name}-paused")
                wait_signal(client, evidence, f"{trial.name}-checked")
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"Stock VAT JEI client failed; see {evidence}")
            mods = json.loads((evidence / "summary.json").read_text())["client"]["captures"][f"{trials[-1].name}-recipe"]["loadedMods"]
            check("client_loads_the_tested_runtime_and_pinned_jei", (report["jarSha256"], report["jeiSha256"]),
                  (digest(Path(mods["enderio"]["file"])), digest(Path(mods["jei"]["file"]))), results)
        finally:
            stop_client(client)
            summary = evidence / "summary.json"
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for path in ("java", "forge-server", "jar", "probe", "jei", "launch"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    shutil.copyfile(args.probe, directory / "mods" / args.probe.name)
    report = {"passed": False, "jarSha256": digest(args.jar), "jeiSha256": digest(args.jei),
              "assertions": [], "servers": [], "trials": {}}
    trials = []

    def fresh(server, results):
        selected, cards = select_cards(server, results, args, report)
        trials.extend(selected)
        report["trials"] = {card["name"]: card.copy() for card in cards}
        prepare(server, results, trials, cards)
        exercise(args, server, report, trials, cards)

    try:
        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "resume",
                  lambda server, results: resume(server, results, trials, report, verify_feeders=False))
        run_stage(args, directory, report, "saved",
                  lambda server, results: completed(server, results, trials, "saved", verify_feeders=False))
        report["passed"] = True
    finally:
        path = directory / "vat-jei-stock-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Stock VAT JEI passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
