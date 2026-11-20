#!/usr/bin/env python3
"""Run every stock 1.21.1 Enchanter recipe level on the packaged Forge port."""

import argparse
from dataclasses import asdict, dataclass, replace
import io
import json
from pathlib import Path
import subprocess
import sys
import tarfile
from zipfile import ZipFile

sys.dont_write_bytecode = True
from capture_baseline import BASELINE
from client.run_multiplayer import stop_client, wait_signal
from packaged_server_smoke import ROOT, digest
from packaged_solar_charging import check
from packaged_spawners import PLAYER, WORLD, prepare_profile, run_stage
from saved_world import read_chunk


BASELINE_RECIPES = "enderio/src/generated/resources/data/enderio/recipe/enchanting"
STATION = "4151 80 8"
START_XP = 100
ARCHIVE_SIZE = 27
TAG_ITEMS = {
    "c:dusts/grains_of_infinity": "enderio:grains_of_infinity",
    "c:dusts/redstone": "minecraft:redstone",
    "c:feathers": "minecraft:feather",
    "c:gems/emerald": "minecraft:emerald",
    "c:gems/lapis": "minecraft:lapis_lazuli",
    "c:gems/prismarine": "minecraft:prismarine_crystals",
    "c:gems/quartz": "minecraft:quartz",
    "c:ingots/dark_steel": "enderio:dark_steel_ingot",
    "c:ingots/iron": "minecraft:iron_ingot",
    "c:leathers": "minecraft:leather",
    "c:netherracks": "minecraft:netherrack",
    "c:obsidians": "minecraft:obsidian",
    "c:slimeballs": "minecraft:slime_ball",
    "c:storage_blocks/iron": "minecraft:iron_block",
    "c:strings": "minecraft:string",
    "minecraft:arrows": "minecraft:arrow",
    "minecraft:fishes": "minecraft:cod",
}


@dataclass(frozen=True)
class Trial:
    name: str
    recipe: str
    enchantment: str
    level: int
    xp: int
    catalyst: str
    catalystCount: int
    catalystLeft: int
    lapis: int
    capture: bool = False


def stock_trials(baseline_minecraft):
    # Read the fixed baseline recipes and Mojang's 1.21.1 enchantment data as an
    # oracle independent of the target's registered Forge recipes and Java code.
    archive = subprocess.check_output(["git", "archive", BASELINE, BASELINE_RECIPES], cwd=ROOT)
    trials = []
    with ZipFile(baseline_minecraft) as vanilla, tarfile.open(fileobj=io.BytesIO(archive)) as recipes:
        for member in recipes:
            if not member.isfile():
                continue
            recipe = json.load(recipes.extractfile(member))
            name = Path(member.name).stem
            enchantment = recipe["enchantment"].removeprefix("minecraft:")
            definition = json.loads(vanilla.read(f"data/minecraft/enchantment/{enchantment}.json"))
            levels = definition["max_level"]
            min_cost = definition["min_cost"]
            ingredient = recipe["input"]
            unit = ingredient["count"]
            item = ingredient["item"] if "item" in ingredient else TAG_ITEMS[ingredient["tag"]]
            costs = []
            for level in range(levels, 0, -1):
                base = min_cost["base"] + (level - 1) * min_cost["per_level_above_first"]
                # Default multiplier is 0.75, rounded to nearest with halves up, plus 2.
                raw_xp = (3 * max(1, base) * recipe["cost_multiplier"] + 2) // 4 + 2
                costs.append(max(raw_xp, costs[-1] // 2 if costs else 1))
            for level, xp in enumerate(reversed(costs), 1):
                consumed = unit * level
                extra = int((unit > 1 and consumed < 64) or (name == "fortune" and level == levels))
                forge_id = "sweeping" if enchantment == "sweeping_edge" else enchantment
                trials.append(Trial(f"{name}-{level}", f"enderio:enchanting/{name}", f"minecraft:{forge_id}",
                                    level, xp, item, consumed + extra, extra, 3 * (5 if levels == 1 else level)))
    return trials


def archive_pos(index):
    return f"{4144 + index // ARCHIVE_SIZE} 80 8"


def stack(slot, item, count):
    return f'{{Slot:{slot},id:"{item}",Count:{count}}}'


def book(trial):
    return f'id:"minecraft:enchanted_book",tag:{{StoredEnchantments:[{{id:"{trial.enchantment}",lvl:{trial.level}s}}]}}'


def load(server, trial, *, lapis=None, catalyst=None):
    items = [stack(0, "minecraft:writable_book", 1), stack(1, trial.catalyst, trial.catalystCount if catalyst is None else catalyst),
             stack(2, "minecraft:lapis_lazuli", trial.lapis + 1 if lapis is None else lapis)]
    server.command(f"data merge block {STATION} {{Items:{{Items:[{','.join(items)}]}}}}")


def preview(trial):
    return f"if data block {STATION} Items.Items[{{Slot:3,Count:1,{book(trial)}}}]"


def spent(trial):
    catalyst = (f"if data block {STATION} Items.Items[{stack(1, trial.catalyst, trial.catalystLeft)}]" if trial.catalystLeft
                else f"unless data block {STATION} Items.Items[{{Slot:1}}]")
    return (f"unless data block {STATION} Items.Items[{{Slot:0}}] {catalyst} "
            f"if data block {STATION} Items.Items[{stack(2, 'minecraft:lapis_lazuli', 1)}] "
            f"unless data block {STATION} Items.Items[{{Slot:3}}]")


def prepare(server, results, trials):
    for command in ("gamerule doMobSpawning false", "gamerule spawnRadius 0", "forceload add 4144 0 4159 15",
                    "fill 4144 79 4 4158 79 14 minecraft:stone", "setworldspawn 4151 80 11", f"setblock {STATION} enderio:enchanter"):
        server.command(command)
    for index in range(0, len(trials), ARCHIVE_SIZE):
        server.command(f"setblock {archive_pos(index)} minecraft:barrel")
    for name, field, value in (("aqua_affinity-1", "lapis", 14), ("sharpness-1", "catalyst", 11)):
        trial = next(entry for entry in trials if entry.name == name)
        load(server, trial, **{field: value})
        server.assert_state(f"{name}_{field}_shortage_has_no_native_preview", f"if block {STATION} enderio:enchanter "
                            f"unless data block {STATION} Items.Items[{{Slot:3}}]", results)


def exercise(args, server, results, report, trials, start, stop, phase):
    evidence = ROOT / "build/port-evidence" / f"{args.name}-{phase}-client"
    layout = server.directory / f"trials-{phase}.json"
    selected = [replace(trial, capture=index in (start, stop - 1)) for index, trial in enumerate(trials[start:stop], start)]
    layout.write_text(json.dumps([asdict(trial) for trial in selected]) + "\n")
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "enchanter-catalog", "--redstone-layout", str(layout), "--server", f"127.0.0.1:{args.port}",
               "--output", str(evidence), "--timeout", "245"]
    summary = evidence / "summary.json"
    with (server.directory / f"enchanter-{phase}-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            server.command(f"tp {PLAYER} 4151.5 80 10.5 180 23")
            (evidence / "server-open").touch()
            for index, trial in enumerate(selected, start):
                if phase != "reopened" or index != start:
                    load(server, trial)
                server.command(f"experience set {PLAYER} {START_XP} levels")
                server.assert_state(f"{trial.name}_native_station_previews_one_exact_book", preview(trial), results)
                (evidence / f"server-ready-{trial.name}").touch()
                wait_signal(client, evidence, f"took-{trial.name}")
                server.assert_state(f"{trial.name}_book_and_exact_native_payment", f"{spent(trial)} "
                                    f"if data entity {PLAYER} {{XpLevel:{START_XP - trial.xp}}} "
                                    f"if data entity {PLAYER} Inventory[{{Slot:8b,Count:1b,{book(trial)}}}]", results)
                server.command(f"item replace block {archive_pos(index)} container.{index % ARCHIVE_SIZE} from entity {PLAYER} hotbar.8")
                server.command(f"item replace entity {PLAYER} hotbar.8 with minecraft:air")
            (evidence / "server-complete").touch()
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"Stock Enchanter client failed, see {evidence}")
            captures = json.loads(summary.read_text())["client"]["captures"]
            for label, capture in captures.items():
                check(f"{label}_client_uses_the_tested_production_jar", report["jarSha256"],
                      digest(Path(capture["loadedMods"]["enderio"]["file"])), results)
        finally:
            stop_client(client)
            report["clients"][phase] = json.loads(summary.read_text()) if summary.exists() else None


def verify_archive(directory, results, trials, phase):
    entities = read_chunk(directory / WORLD, 259, 0)["block_entities"]
    actual = {}
    for index in range(0, len(trials), ARCHIVE_SIZE):
        x, y, z = map(int, archive_pos(index).split())
        barrel, = (entity for entity in entities if (entity["x"], entity["y"], entity["z"]) == (x, y, z))
        for stack in barrel["Items"]:
            actual[index + stack["Slot"]] = (stack["id"], stack["Count"], stack["tag"]["StoredEnchantments"])
    expected = {index: ("minecraft:enchanted_book", 1, [{"id": trial.enchantment, "lvl": trial.level}])
                for index, trial in enumerate(trials)}
    check(f"{phase}_closed_save_contains_every_original_book_in_order", expected, actual, results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for key in ("java", "forge-server", "jar", "launch", "baseline-minecraft"):
        parser.add_argument(f"--{key}", type=Path, required=True)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    trials = stock_trials(args.baseline_minecraft)
    midpoint = len(trials) // 2
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "catalog": [asdict(trial) for trial in trials], "assertions": [], "servers": [], "clients": {},
              "baselineMinecraftSha256": digest(args.baseline_minecraft), "jarSha256": digest(directory / "mods/enderio.jar")}

    def fresh(server, results):
        prepare(server, results, trials)
        exercise(args, server, results, report, trials, 0, midpoint, "fresh")
        load(server, trials[midpoint])
        server.assert_state("the_next_unpaid_preview_is_ready_before_restart", preview(trials[midpoint]), results)

    def reopened(server, results):
        server.assert_state("the_next_unpaid_preview_survives_restart", preview(trials[midpoint]), results)
        exercise(args, server, results, report, trials, midpoint, len(trials), "reopened")

    def final(server, results):
        server.assert_state("the_last_craft_stays_spent", spent(trials[-1]), results)

    try:
        run_stage(args, directory, report, "fresh", fresh)
        verify_archive(directory, report["assertions"], trials[:midpoint], "first")
        run_stage(args, directory, report, "reopened", reopened)
        run_stage(args, directory, report, "final", final)
        verify_archive(directory, report["assertions"], trials, "final")
        report["passed"] = True
    finally:
        result = directory / "enchanter-catalog-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Enchanter catalog passed={report['passed']}, {len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
