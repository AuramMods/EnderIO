#!/usr/bin/env python3
"""Verify paused tank inputs against the active recipe manager after a live reload."""

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import sys
import time

sys.dont_write_bytecode = True
from packaged_nutritious_stick import advance, liquid_at
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage
from packaged_vat_automation import WATER, fluid, tank_fields


STARTING_WATER = 2000
RELOAD_MARKER = "PORT_TANK_NEW_RECIPES_LOADED"


@dataclass(frozen=True)
class Recipe:
    name: str
    item: str
    count: int
    water: int
    tag: str


@dataclass(frozen=True)
class Case:
    name: str
    x: int
    tank: str
    mode: str
    ingredient: str
    original: Recipe | None
    active: Recipe | None

    @property
    def pos(self):
        return f"{self.x} 80 8"

    @property
    def signal(self):
        return f"{self.x} 80 7"

    @property
    def input_slot(self):
        return 2 if self.mode == "fill" else 0


CASES = (
    Case("replacement", 3763, "fluid_tank", "fill", "minecraft:paper",
         Recipe("paper", "minecraft:feather", 1, 100, "old_fill"),
         Recipe("paper", "minecraft:ink_sac", 2, 175, "new_fill")),
    Case("fallback", 3766, "pressurized_fluid_tank", "empty", "minecraft:rabbit_hide",
         Recipe("hide", "minecraft:glass_pane", 1, 125, "old_empty"),
         Recipe("hide_replacement", "minecraft:leather", 2, 350, "new_empty")),
    Case("removal", 3769, "fluid_tank", "fill", "minecraft:clay_ball",
         Recipe("clay", "minecraft:brick", 1, 250, "removed"), None),
    Case("addition", 3772, "pressurized_fluid_tank", "empty", "minecraft:amethyst_shard",
         None, Recipe("amethyst", "minecraft:echo_shard", 1, 50, "added")),
)


def write_pack(directory):
    pack = directory / WORLD / "datapacks/port-tank-reload"
    (pack / "data/port_tank/recipes").mkdir(parents=True)
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 15, "description": "Tank reload fixture"}}) + "\n")
    for case in CASES:
        if case.original:
            write_recipe(pack, case, case.original)
    return pack


def recipe_path(pack, recipe):
    return pack / "data/port_tank/recipes" / f"{recipe.name}.json"


def write_recipe(pack, case, recipe):
    document = {"type": "enderio:tank", "mode": case.mode, "input": {"item": case.ingredient},
                "output": {"id": recipe.item, "count": recipe.count, "tag": {"PortRecipe": recipe.tag}},
                "fluid": {"fluid": WATER, "amount": recipe.water}}
    recipe_path(pack, recipe).write_text(json.dumps(document) + "\n")


def input_stack(case):
    return f'{{Slot:{case.input_slot},id:"{case.ingredient}",Count:1}}'


def waiting(case):
    return (f"{liquid_at(case.pos, WATER, STARTING_WATER)} if data block {case.pos} Items.Items[{input_stack(case)}] "
            f"unless data block {case.pos} Items.Items[1]")


def outcome(case):
    if case.active is None:
        return waiting(case)
    recipe = case.active
    amount = STARTING_WATER - recipe.water if case.mode == "fill" else STARTING_WATER + recipe.water
    stack = f'{{Slot:{case.input_slot + 1},id:"{recipe.item}",Count:{recipe.count},tag:{{PortRecipe:"{recipe.tag}"}}}}'
    return f"{liquid_at(case.pos, WATER, amount)} if data block {case.pos} Items.Items[{stack}] unless data block {case.pos} Items.Items[1]"


def live_reload(server, pack):
    for case in CASES:
        if case.original and (not case.active or case.original.name != case.active.name):
            recipe_path(pack, case.original).unlink()
        if case.active:
            write_recipe(pack, case, case.active)
    marker = pack / "data/port_tank/functions/reloaded.mcfunction"
    marker.parent.mkdir()
    marker.write_text(f"say {RELOAD_MARKER}\n")
    server.command("reload")
    deadline = time.monotonic() + 40
    while RELOAD_MARKER not in server.command("function port_tank:reloaded"):
        if time.monotonic() >= deadline:
            raise AssertionError(f"The new datapack state did not become active; see {server.log_path}")
        time.sleep(0.2)


def verify(server, results, phase):
    failures = []
    for case in CASES:
        try:
            server.assert_state(f"{case.name}_{phase}_matches_the_loaded_recipe_set",
                                f"if block {case.signal} minecraft:redstone_block {outcome(case)}", results, timeout=3)
        except AssertionError as error:
            failures.append(str(error))
        finally:
            server.command(f"data get block {case.pos}")
    if failures:
        raise AssertionError("\n".join(failures))


def exercise(server, results, pack):
    server.command("forceload add 3760 0 3775 15")
    server.assert_state("all_four_tanks_are_in_the_loaded_chunk", f"if loaded {CASES[0].pos} if loaded {CASES[-1].pos}", results)
    for case in CASES:
        initial = tank_fields(fluid(WATER, STARTING_WATER))
        server.command(f"setblock {case.pos} enderio:{case.tank}[facing=south]"
                       f'{{{initial},Items:{{Items:[{input_stack(case)}]}},RedstoneControl:"active_with_signal"}}')
    advance(server)
    for case in CASES:
        server.assert_state(f"{case.name}_redstone_pause_preserves_the_original_input", waiting(case), results)
    live_reload(server, pack)
    for case in CASES:
        server.assert_state(f"{case.name}_active_reload_leaves_the_paused_tank_untouched", waiting(case), results)
        server.command(f"setblock {case.signal} minecraft:redstone_block")
    advance(server, 30)
    verify(server, results, "after_live_reload")


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
    pack = write_pack(directory)
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}
    try:
        run_stage(args, directory, report, "fresh", lambda server, results: exercise(server, results, pack))
        run_stage(args, directory, report, "reopened", lambda server, results: verify(server, results, "reopened"))
        report["passed"] = True
    finally:
        result = directory / "tank-recipe-reload-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Tank recipe reload passed={report['passed']}, {len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
