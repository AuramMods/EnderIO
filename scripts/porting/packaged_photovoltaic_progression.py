#!/usr/bin/env python3
"""Run stock SAG Mill outputs through composite, plates and a working solar module."""

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import re
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_alloy_progression import verify_saved_inventory
from packaged_glass_progression import Smelt, finished_smelter, prepare_smelter, start_smelter, transfer
from packaged_sag_progression import charge, feed, inputs, mill, output, prepare_mill
from packaged_server_smoke import ROOT, digest
from packaged_solar_charging import check, sample
from packaged_soul_binder import item
from packaged_spawners import WORLD, prepare_profile, run_stage
from packaged_starter_progression import PLAYER, hopper_item, player_count, send_to_block


@dataclass(frozen=True)
class Grind:
    x: int
    material: str
    dust: str
    count: int


LAPIS = Grind(3555, "minecraft:lapis_lazuli", "enderio:powdered_lapis_lazuli", 6)
COAL = Grind(3558, "minecraft:coal", "enderio:powdered_coal", 6)
SAND = Grind(3561, "minecraft:red_sand", "enderio:silicon", 24)
GRINDS = (LAPIS, COAL, SAND)
COST, RESERVE, COMPOSITES = 2400, 137, 6
COMPOSITE = "enderio:photovoltaic_composite"
PLATES = Smelt(3565, 4, "photovoltaic_plate", 3, 5600)
PLATE = f"enderio:{PLATES.output}"
PANEL = "enderio:energetic_photovoltaic_module"
CHEST, TABLE = "3569 80 8", "3568 80 8"
PANEL_POS, RECEIVER = "3574 80 8", "3574 79 8"
CAPACITOR = "enderio:basic_capacitor"


def empty_mill(route):
    pos = mill(route.x)
    slots = " ".join(f"unless data block {pos} Items.Items[{{Slot:{slot}}}]" for slot in range(6))
    return (f"if data block {pos} {{EnergyStored:{RESERVE}}} unless data block {pos} Task "
            f"{slots} unless data block {feed(route.x)} Items[0]")


def batch_loaded(server, results, route, count):
    server.assert_state(f"{route.x}_receives_{count}_original_inputs_through_a_native_hopper",
                        f'if data block {mill(route.x)} Items.Items[{{Slot:0,id:"{route.material}",Count:{count}}}] '
                        f"unless data block {feed(route.x)} Items[0]", results, timeout=35)


def hopper_count(server, route):
    reply = server.command(f"data get block {output(route.x)} Items")
    matches = re.findall(r"Count: (\d+)b", reply)
    if len(matches) > 1 or (matches and f'id: "{route.dust}"' not in reply):
        raise AssertionError(f"Unexpected stock SAG Mill output: {reply}")
    if not matches and "block data: []" not in reply:
        raise AssertionError(reply)
    return int(matches[0]) if matches else 0


def grind(server, results, report):
    for route in GRINDS:
        prepare_mill(server, route.x)
        server.command(f'data merge block {mill(route.x)} {{Items:{{Items:[{item(6, "enderio:octadic_capacitor")}]}}}}')
        inputs(server, route.x, route.material, route.count)
    for route in GRINDS:
        batch_loaded(server, results, route, route.count)
        charge(server, route.x, route.count * COST + RESERVE)
    for route in GRINDS:
        server.assert_state(f"{route.x}_finishes_its_stock_grind_on_the_exact_fe_budget", empty_mill(route), results, timeout=90)
    yields = {route.dust: hopper_count(server, route) for route in GRINDS}
    sand = yields[SAND.dust]
    if sand < COMPOSITES:
        extra = 16
        inputs(server, SAND.x, SAND.material, extra)
        batch_loaded(server, results, SAND, extra)
        charge(server, SAND.x, extra * COST + RESERVE)
        server.assert_state("the_extra_red_sand_batch_finishes_on_its_exact_budget", empty_mill(SAND), results, timeout=85)
        yields[SAND.dust] = hopper_count(server, SAND)
        report["extraSand"] = extra
    check("the_lapis_mill_exports_exactly_six_powder", COMPOSITES, yields[LAPIS.dust], results)
    check("stock_coal_and_red_sand_rolls_supply_the_required_ingredients", True,
          COMPOSITES <= yields[COAL.dust] <= 2 * COAL.count
          and COMPOSITES <= yields[SAND.dust] <= SAND.count + report.get("extraSand", 0), results)
    report["grindYields"] = yields
    for slot, route in enumerate(GRINDS):
        transfer(server, output(route.x), 0, CHEST, slot)


def setup(server, results, report):
    server.command("forceload add 3552 0 3583 15")
    server.assert_state("the_workshop_sag_mills_and_solar_position_are_loaded", f"if loaded {mill(LAPIS.x)} if loaded {PANEL_POS}", results)
    for command in ("gamerule doMobSpawning false", "gamerule doDaylightCycle false", "gamerule spawnRadius 0",
                    "gamerule doLimitedCrafting true", "time set midnight", "weather clear 10000",
                    "fill 3566 79 8 3579 79 14 minecraft:stone", "setworldspawn 3568 80 12",
                    f"setblock {CHEST} minecraft:chest[facing=south]", f"setblock {TABLE} minecraft:crafting_table"):
        server.command(command)
    grind(server, results, report)
    prepare_smelter(server, PLATES)


def smelt(server, results, report):
    check("survival_makes_six_composites_from_original_ground_materials", COMPOSITES, player_count(server, COMPOSITE), results)
    for material, count in report["grindYields"].items():
        check(f"the_survival_crafting_spends_six_{material}", count - COMPOSITES, player_count(server, material), results)
    send_to_block(server, COMPOSITE, f"{PLATES.pos(81)} container.0")
    server.assert_state("the_original_composites_enter_the_native_plate_smelter_once",
                        f'if data block {PLATES.pos(80)} Items.Items[{{Slot:0,id:"{COMPOSITE}",Count:{COMPOSITES}}}] '
                        f'unless data entity {PLAYER} Inventory[{{id:"{COMPOSITE}"}}] unless data block {PLATES.pos(81)} Items[0]', results)
    start_smelter(server, PLATES)
    server.assert_state("three_stock_plate_batches_spend_exactly_16800_fe", finished_smelter(PLATES), results, timeout=40)
    transfer(server, PLATES.pos(79), 0, CHEST, 0)
    for slot, (ingredient, count) in enumerate(((CAPACITOR, 2), ("minecraft:gold_ingot", 2),
                                               ("minecraft:glass", 1), ("minecraft:redstone", 1)), 1):
        server.command(f"item replace block {CHEST} container.{slot} with {ingredient} {count}")


def stock_remains():
    balances = " ".join(f"{empty_mill(route)} unless data block {output(route.x)} Items[0]" for route in GRINDS)
    return (f"{balances} if data block {PLATES.pos(80)} {{EnergyStored:{RESERVE}}} "
            f"unless data block {PLATES.pos(80)} Task unless data block {PLATES.pos(79)} Items[0] "
            f"unless data block {CHEST} Items[0]")


def write_measurement(world):
    pack = world / "datapacks/port_photovoltaic"
    functions = pack / "data/port_power/functions"
    functions.mkdir(parents=True)
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 15, "description": "Solar tick balance fixture"}}) + "\n")
    commands = {"tick": "time query gametime", "panel": f"data get block {PANEL_POS} EnergyStored",
                "receiver": f"data get block {RECEIVER} EnergyStored"}
    (functions / "photovoltaic.mcfunction").write_text("\n".join([
        "data modify storage port_power:sample value set value {}",
        *(f"execute store result storage port_power:sample value.{key} int 1 run {command}" for key, command in commands.items()),
    ]) + "\n")


def measure(server, results, label, rate):
    before = sample(server, "photovoltaic")
    for _ in range(30):
        after = sample(server, "photovoltaic")
        ticks = after["tick"] - before["tick"]
        if ticks >= 10:
            break
    else:
        raise AssertionError("The solar fixture did not advance ten native game ticks")
    total = after["receiver"] + after["panel"] - before["receiver"] - before["panel"]
    check(f"{label}_conserves_published_generation_over_{ticks}_ticks", ticks * rate, total, results)
    if rate:
        check(f"{label}_delivers_energy_to_the_actual_wired_charger", True, after["receiver"] > before["receiver"], results)
    return after


def run_client(args, server, results, report):
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    summary = evidence / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "photovoltaic-progression", "--server", f"127.0.0.1:{args.port}", "--output", str(evidence), "--timeout", "190"]
    with (server.directory / "photovoltaic-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            server.command(f"tp {PLAYER} 3568.5 80 11.5 180 16")
            (evidence / "server-workshop").touch()
            wait_signal(client, evidence, "composites")
            smelt(server, results, report)
            (evidence / "server-plates").touch()
            wait_signal(client, evidence, "crafted")
            check("the_survival_player_uses_the_three_original_plates_in_one_module", 1, player_count(server, PANEL), results)
            check("there_are_no_leftover_plate_copies", 0, player_count(server, PLATE), results)
            server.command(f"tp {PLAYER} 3574.5 80 11.5 180 16")
            (evidence / "server-place").touch()
            wait_signal(client, evidence, "placed")
            server.assert_state("the_actual_survival_module_is_placed_once_at_midnight",
                                f'if block {PANEL_POS} {PANEL} if data block {PANEL_POS} {{EnergyStored:0}} '
                                f'unless data entity {PLAYER} Inventory[{{id:"{PANEL}"}}]', results)
            server.command(f'setblock {RECEIVER} enderio:wired_charger[facing=south]'
                           f'{{Items:{{Items:[{item(0, CAPACITOR)}]}},IOConfig:{{Modes:{{up:"pull"}}}}}}')
            measure(server, results, "fresh_midnight", 0)
            server.command("time set noon")
            report["daylight"] = measure(server, results, "fresh_noon", 4)
            server.command("time set midnight")
            report["saved"] = measure(server, results, "fresh_dark_after_generation", 0)
            (evidence / "server-done").touch()
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"Photovoltaic progression client failed; see {evidence}")
            for frame in json.loads(summary.read_text())["client"]["captures"].values():
                if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                    raise AssertionError("Photovoltaic client and server Ender IO jars differ")
        finally:
            stop_client(client)
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    for key in ("forge-server", "jar", "launch"):
        parser.add_argument(f"--{key}", type=Path, required=True)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    write_measurement(directory / WORLD)
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}

    def fresh(server, results):
        setup(server, results, report)
        run_client(args, server, results, report)

    def reopened(server, results):
        server.assert_state("the_crafted_panel_still_exists_with_its_original_machine_balances",
                            f"if block {PANEL_POS} {PANEL} {stock_remains()}", results)
        after = measure(server, results, "reopened_midnight", 0)
        check("the_saved_panel_and_charger_both_keep_their_exact_balance", [report["saved"][key] for key in ("panel", "receiver")],
              [after[key] for key in ("panel", "receiver")], results)
        server.command("time set noon")
        report["reopenedNoon"] = measure(server, results, "reopened_noon", 4)

    try:
        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", reopened)
        verify_saved_inventory(directory, report, expected={material: count - COMPOSITES for material, count in report["grindYields"].items()
                                                           if count > COMPOSITES})
        report["passed"] = True
    finally:
        result = directory / "photovoltaic-progression-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Photovoltaic progression passed={report['passed']}, {len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
