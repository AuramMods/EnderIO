#!/usr/bin/env python3
"""Craft and charge a stock Electromagnet, then measure its native orb attraction."""

import argparse
import json
from pathlib import Path
import re
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_curios import ENERGY, MAGNET
from packaged_electromagnet import TOGGLE
from packaged_server_smoke import ROOT, digest
from packaged_solar_charging import check
from packaged_soul_binder import item
from packaged_spawners import PLAYER, WORLD, prepare_profile, run_stage
from saved_world import read_data_file


TABLE, CHARGER = "4240 80 8", "4244 80 8"
ORB_POSITION = "4255.45 84.95 13.45"
ORB_STAND = "4250.5 80 8.5"
ORB = "@e[type=minecraft:experience_orb,tag=port_electromagnet_xp,limit=1]"
CHARGE, XP = 192, 7
MAGNET_STACK = f'Inventory[{{id:"{MAGNET}",Count:1b}}]'
INGREDIENTS = {"energetic_alloy_ingot": 4, "conductive_alloy_ingot": 2, "vibrant_crystal": 1}


def write_pack(directory):
    pack = directory / WORLD / "datapacks/port-magnet-xp"
    sampler = pack / "data/port_magnet_xp/functions/sample.mcfunction"
    sampler.parent.mkdir(parents=True)
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 15, "description": "Electromagnet orb accounting"}}) + "\n")
    sources = {"tick": "time query gametime", "xp": f"data get entity {PLAYER} XpTotal",
               "energy": f"data get entity {PLAYER} {MAGNET_STACK}{ENERGY}",
               "enabled": f"data get entity {PLAYER} {MAGNET_STACK}{TOGGLE}"}
    for axis, key in enumerate("xyz"):
        sources[key] = f"data get entity {ORB} Pos[{axis}] 1000"
    sampler.write_text("data modify storage port_magnet_xp:sample value set value {}\n" + "".join(
        f"execute store result storage port_magnet_xp:sample value.{key} int 1 run {command}\n" for key, command in sources.items()))


def sample(server):
    server.command("function port_magnet_xp:sample")
    output = server.command("data get storage port_magnet_xp:sample value")
    return {key: int(value) for key, value in re.findall(r"(\w+): (-?\d+)", output.split("storage", 1)[-1])}


def window(server, ticks):
    start = sample(server)
    for _ in range(ticks + 20):
        end = sample(server)
        if end["tick"] - start["tick"] >= ticks:
            return [start, end]
    raise AssertionError(f"The fixture did not advance {ticks} native ticks")


def orb_pose(value):
    return tuple(value[key] for key in "xyz")


def summon_orb(server, value):
    server.command(f'summon minecraft:experience_orb {ORB_POSITION} {{Value:{value}s,Count:1,NoGravity:1b,'
                   'Motion:[0.0d,0.0d,0.0d],Tags:["port_electromagnet_xp"]}')


def prepare(server, results):
    for command in ("gamerule doMobSpawning false", "gamerule doLimitedCrafting true", "gamerule spawnRadius 0",
                    "forceload add 4240 0 4255 15", "fill 4240 79 5 4255 79 14 minecraft:stone",
                    "setworldspawn 4240 80 12", "time set noon", f"setblock {TABLE} minecraft:crafting_table",
                    f'setblock {CHARGER} enderio:wired_charger[facing=south]'
                    f'{{Items:{{Items:[{item(0, "enderio:basic_capacitor")}]}},EnergyStored:{CHARGE}}}'):
        server.command(command)
    server.assert_state("the_stock_wired_charger_starts_with_only_the_192_fe_test_budget",
                        f"if data block {CHARGER} {{EnergyStored:{CHARGE}}}", results)


def charger_is_empty():
    return (f"if data block {CHARGER} {{EnergyStored:0}} unless data block {CHARGER} Items.Items[{{Slot:1}}] "
            f"unless data block {CHARGER} Items.Items[{{Slot:2}}]")


def prepare_magnet(server, results, client, evidence):
    wait_signal(client, evidence, "connected")
    for index, (ingredient, count) in enumerate(INGREDIENTS.items()):
        server.command(f"item replace entity {PLAYER} hotbar.{index} with enderio:{ingredient} {count}")
    server.command(f"tp {PLAYER} 4240.5 80 11.5 180 15")
    (evidence / "server-table").touch()
    wait_signal(client, evidence, "crafted")
    spent = " ".join(f'unless data entity {PLAYER} Inventory[{{id:"enderio:{ingredient}"}}]' for ingredient in INGREDIENTS)
    server.assert_state("limited_survival_crafting_consumes_the_seven_original_components",
                        f"if data entity {PLAYER} {MAGNET_STACK} {spent}", results)
    server.command(f"tp {PLAYER} 4244.5 80 11.5 180 15")
    (evidence / "server-charger").touch()
    wait_signal(client, evidence, "charged-in-slot")
    server.assert_state("the_input_slot_contains_the_original_magnet_with_the_entire_charger_budget",
                        f'if data block {CHARGER} {{EnergyStored:0}} if data block {CHARGER} '
                        f'Items.Items[{{Slot:1,id:"{MAGNET}",Count:1,tag:{{"enderio:data":{{"enderio:energy":{CHARGE}}}}}}}] '
                        f"unless data block {CHARGER} Items.Items[{{Slot:2}}] unless data entity {PLAYER} {MAGNET_STACK}", results)
    (evidence / "server-withdraw").touch()
    wait_signal(client, evidence, "charged")
    server.assert_state("the_same_charged_item_returns_to_survival_inventory", f"{charger_is_empty()} "
                        f'if data entity {PLAYER} {MAGNET_STACK}.tag."enderio:data"{{"enderio:energy":{CHARGE}}}', results)


def measure_orbs(server, results, report, client, evidence):
    server.command(f"tp {PLAYER} {ORB_STAND} -45 -25")
    summon_orb(server, XP)
    server.assert_state("the_first_stationary_orb_contains_exactly_seven_xp", f"if data entity {ORB} {{Value:{XP}s,Count:1}}", results)
    off = report["samples"]["off"] = window(server, 45)
    initial = orb_pose(off[0])
    check("the_orb_holds_its_spawn_location_for_more_than_two_vanilla_scan_periods", initial, orb_pose(off[1]), results)
    check("the_player_and_magnet_remain_idle_through_the_off_window", (0, CHARGE, 0),
          (off[1]["xp"], off[1]["energy"], off[1]["enabled"]), results)
    (evidence / "server-activate").touch()
    server.assert_state("the_native_key_activates_the_newly_crafted_magnet",
                        f'if data entity {PLAYER} {MAGNET_STACK}.tag."enderio:data"{{"enderio:toggled":1b}}', results)
    active = report["samples"]["active"] = window(server, 12)
    check("the_sampled_orb_leaves_its_original_position_while_the_magnet_runs", True,
          any(0 < value["x"] < initial[0] and 0 < value["z"] < initial[2] for value in active), results)
    check("the_active_magnet_spends_one_fe_per_game_tick", active[1]["tick"] - active[0]["tick"],
          active[0]["energy"] - active[1]["energy"], results)
    server.assert_state("the_player_receives_exactly_seven_xp_from_the_original_orb",
                        f"if data entity {PLAYER} {{XpTotal:{XP},XpLevel:1}} unless entity {ORB}", results, timeout=15)
    (evidence / "server-collected").touch()
    wait_signal(client, evidence, "depleted")
    server.assert_state("the_original_magnet_is_still_in_inventory_and_the_toggle_is_off", f"if data entity {PLAYER} {MAGNET_STACK} "
                        f'unless data entity {PLAYER} {MAGNET_STACK}.tag."enderio:data"{{"enderio:toggled":1b}}', results)
    summon_orb(server, 3)
    empty = report["samples"]["depleted"] = window(server, 45)
    for value in empty:
        check("the_uncharged_magnet_leaves_the_second_orb_untouched", (initial, XP, 0, 0),
              (orb_pose(value), value["xp"], value["energy"], value["enabled"]), results)
    (evidence / "server-finish").touch()


def exercise(args, server, results, report):
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "electromagnet-progression", "--server", f"127.0.0.1:{args.port}", "--output", str(evidence), "--timeout", "180"]
    summary = evidence / "summary.json"
    with (server.directory / "electromagnet-progression-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            prepare_magnet(server, results, client, evidence)
            measure_orbs(server, results, report, client, evidence)
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"Electromagnet progression client failed; see {evidence}")
            capture = json.loads(summary.read_text())["client"]["captures"]["electromagnet-recipe"]
            check("the_client_and_server_use_the_same_production_artifact", report["jarSha256"],
                  digest(Path(capture["loadedMods"]["enderio"]["file"])), results)
        finally:
            stop_client(client)
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def player_save(directory, results):
    file, = (directory / WORLD / "playerdata").glob("*.dat")
    player = read_data_file(file)
    products = [(stack["id"], stack["Count"]) for stack in player["Inventory"]]
    check("the_closed_player_save_retains_exactly_the_crafted_magnet", [(MAGNET, 1)], products, results)
    data = player["Inventory"][0].get("tag", {}).get("enderio:data", {})
    check("the_closed_player_save_retains_seven_xp_and_the_empty_disabled_magnet", (XP, 1, 0, 0),
          (player["XpTotal"], player["XpLevel"], data.get("enderio:energy", 0), data.get("enderio:toggled", 0)), results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for key in ("java", "forge-server", "jar", "launch"):
        parser.add_argument(f"--{key}", type=Path, required=True)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    write_pack(directory)
    report = {"passed": False, "assertions": [], "servers": [], "samples": {}, "jarSha256": digest(directory / "mods/enderio.jar")}

    def fresh(server, results):
        prepare(server, results)
        exercise(args, server, results, report)

    def reopened(server, results):
        server.assert_state("the_second_orb_and_spent_charger_survive_restart", f"{charger_is_empty()} if data entity {ORB} {{Value:3s,Count:1}}", results)
        check("the_remaining_orb_reopens_at_its_original_position", orb_pose(report["samples"]["depleted"][0]), orb_pose(sample(server)), results)

    try:
        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", reopened)
        player_save(directory, report["assertions"])
        report["passed"] = True
    finally:
        result = directory / "electromagnet-progression-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Electromagnet progression passed={report['passed']}, {len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
