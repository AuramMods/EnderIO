#!/usr/bin/env python3
"""Measure Electromagnet pickup and per-tick FE in inventory and Curios."""

import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_curios import CURIO_INVENTORY, ENERGY, MAGNET, ORDINARY_MAGNET, equip, magnet
from packaged_server_smoke import ROOT, digest
from packaged_solar_charging import check
from packaged_spawners import PLAYER, WORLD, prepare_profile, run_stage
from saved_world import read_data_file


STAND = "4168.5 80 8.5"
CHARGE, RESERVE, DRAW = 50000, 34000, 1
CHARM = f'{CURIO_INVENTORY}[{{Identifier:"charm"}}].StacksHandler.Stacks.Items[{{id:"{MAGNET}"}}]'
TOGGLE = '.tag."enderio:data"."enderio:toggled"'
DROP = {
    "inside": ("4172.5 80.5 8.5", "minecraft:diamond", 7, 0, 4172500),
    "blacklisted": ("4168.5 80.5 12.5", "minecraft:apple", 3, 2, 12500),
    "outside": ("4175.5 80.5 8.5", "minecraft:copper_ingot", 5, 0, 4175500),
}


def selector(name):
    return f"@e[type=minecraft:item,tag=port_magnet_{name},limit=1]"


def write_pack(directory, curios):
    pack = directory / WORLD / "datapacks/port-electromagnet"
    tag = pack / "data/enderio/tags/items/blacklists/electromagnet.json"
    tag.parent.mkdir(parents=True)
    tag.write_text(json.dumps({"replace": False, "values": ["minecraft:apple"]}) + "\n")
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 15, "description": "Electromagnet accounting"}}) + "\n")
    sampler = pack / "data/port_magnet/functions/sample.mcfunction"
    sampler.parent.mkdir(parents=True)
    chosen = CHARM if curios else ORDINARY_MAGNET
    fields = {"tick": "time query gametime", "energy": f"data get entity {PLAYER} {chosen}{ENERGY}",
              "enabled": f"data get entity {PLAYER} {chosen}{TOGGLE}",
              "ordinary": f"data get entity {PLAYER} {ORDINARY_MAGNET}{ENERGY}"}
    for name, (_, _, _, axis, _) in DROP.items():
        fields[name] = f"data get entity {selector(name)} Pos[{axis}] 1000"
    sampler.write_text("data modify storage port_magnet:sample value set value {}\n" + "".join(
        f"execute store result storage port_magnet:sample value.{key} int 1 run {command}\n" for key, command in fields.items()))


def sample(server):
    server.command("function port_magnet:sample")
    output = server.command("data get storage port_magnet:sample value")
    return {key: int(value) for key, value in re.findall(r"(\w+): (-?\d+)", output.split("storage", 1)[-1])}


def window(server):
    start = sample(server)
    for _ in range(30):
        end = sample(server)
        if end["tick"] - start["tick"] >= 12:
            return [start, end]
    raise AssertionError("The fixture did not advance twelve native ticks")


def prepare(server):
    for command in ("gamerule doMobSpawning false", "gamerule spawnRadius 0", "forceload add 4160 0 4191 15",
                    "fill 4162 79 4 4179 79 14 minecraft:stone", "setworldspawn 4168 80 8", "time set noon"):
        server.command(command)


def set_drops(server):
    for name, (position, item, count, _, _) in DROP.items():
        server.command(f'summon minecraft:item {position} {{NoGravity:1b,Motion:[0.0d,0.0d,0.0d],'
                       f'Tags:["port_magnet_{name}"],Item:{{id:"{item}",Count:{count}b}}}}')


def exercise(server, results, report, client, evidence, curios):
    wait_signal(client, evidence, "connected")
    server.command(f"tp {PLAYER} {STAND} -90 8")
    server.command(f"item replace entity {PLAYER} hotbar.2 with {magnet(RESERVE if curios else CHARGE)}")
    chosen = CHARM if curios else ORDINARY_MAGNET
    if curios:
        equip(server, "charm", magnet(CHARGE))
    set_drops(server)
    dormant = report["samples"]["dormant"] = window(server)
    for value in dormant:
        check("disabled_magnet_keeps_charge_and_all_drops_in_place", [CHARGE, 0, *(drop[-1] for drop in DROP.values())],
              [value["energy"], value["enabled"], *(value[name] for name in DROP)], results)
    (evidence / "server-start").touch()
    server.assert_state("native_magnet_key_sets_the_selected_stack_active",
                        f'if data entity {PLAYER} {chosen}.tag."enderio:data"{{"enderio:toggled":1b}}', results)
    active = report["samples"]["active"] = window(server)
    ticks = active[1]["tick"] - active[0]["tick"]
    check("the_active_stack_spends_one_fe_per_server_tick", DRAW * ticks, active[0]["energy"] - active[1]["energy"], results)
    check("the_nearby_native_drop_moves_toward_the_player", True, 0 <= active[1]["inside"] < DROP["inside"][-1], results)
    server.assert_state("exactly_seven_original_diamonds_reach_the_survival_inventory",
                        f'if data entity {PLAYER} Inventory[{{id:"minecraft:diamond",Count:7b}}] unless entity {selector("inside")} '
                        f'unless data entity {PLAYER} Inventory[{{id:"minecraft:apple"}}] '
                        f'unless data entity {PLAYER} Inventory[{{id:"minecraft:copper_ingot"}}]', results, timeout=15)
    (evidence / "server-stop").touch()
    wait_signal(client, evidence, "stopped")
    stopped = report["samples"]["stopped"] = window(server)
    report["savedCharge"] = stopped[0]["energy"]
    check("toggle_off_stops_fe_draw", [stopped[0]["energy"], 0, 0],
          [stopped[1]["energy"], stopped[0]["enabled"], stopped[1]["enabled"]], results)
    check("blacklisted_and_out_of_range_drops_stay_in_place", [DROP["blacklisted"][-1], DROP["outside"][-1]],
          [stopped[1]["blacklisted"], stopped[1]["outside"]], results)
    if curios:
        check("curio_priority_preserves_the_competing_inventory_charge", [RESERVE] * 4,
              [value["ordinary"] for value in (*active, *stopped)], results)
    (evidence / "server-finish").touch()


def reconnect(server, results, report, client, evidence, curios):
    wait_signal(client, evidence, "connected")
    chosen = CHARM if curios else ORDINARY_MAGNET
    charge = report["savedCharge"]
    server.assert_state("the_reconnected_player_loads_the_exact_saved_charge_disabled_and_all_seven_diamonds",
                        f'if data entity {PLAYER} {chosen}.tag."enderio:data"{{"enderio:energy":{charge}}} '
                        f'unless data entity {PLAYER} {chosen}.tag."enderio:data"{{"enderio:toggled":1b}} '
                        f'if data entity {PLAYER} Inventory[{{id:"minecraft:diamond",Count:7b}}]', results)
    (evidence / "saved-charge.txt").write_text(str(charge))
    (evidence / "server-restored").touch()


def run_client(args, server, results, report, phase):
    evidence = ROOT / "build/port-evidence" / f"{args.name}-{phase}-client"
    summary = evidence / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "electromagnet", "--multiplayer-phase", phase, "--server", f"127.0.0.1:{args.port}",
               "--output", str(evidence), "--timeout", "185"]
    with (server.directory / f"electromagnet-{phase}-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            scenario = exercise if phase == "exercise" else reconnect
            scenario(server, results, report, client, evidence, bool(args.curios_jar))
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"Electromagnet client failed; see {evidence}")
            captures = json.loads(summary.read_text())["client"]["captures"]
            artifacts = next(iter(captures.values()))["loadedMods"]
            check(f"{phase}_client_loads_the_requested_curios_profile", bool(args.curios_jar), "curios" in artifacts, results)
            for mod, sha in report["mods"].items():
                check(f"{phase}_uses_the_same_{mod}_artifact_on_both_peers", sha, digest(Path(artifacts[mod]["file"])), results)
        finally:
            stop_client(client)
            report["clients"][phase] = json.loads(summary.read_text()) if summary.exists() else None


def verify_saved_player(directory, results, charge, curios):
    file, = (directory / WORLD / "playerdata").glob("*.dat")
    player = read_data_file(file)
    inventory = {item["id"]: item for item in player["Inventory"]}
    check("closed_survival_inventory_keeps_the_original_pickup", 7, inventory["minecraft:diamond"]["Count"], results)
    original = inventory[MAGNET]
    if curios:
        check("the_saved_inventory_spare_stays_disabled_and_charged", (RESERVE, 0), magnet_state(original), results)
        slot, = (entry for entry in player["ForgeCaps"]["curios:inventory"]["Curios"] if entry["Identifier"] == "charm")
        original, = (stack for stack in slot["StacksHandler"]["Stacks"]["Items"] if stack["id"] == MAGNET)
    check("the_used_magnet_saves_its_stopped_state_and_exact_charge", (charge, 0), magnet_state(original), results)


def magnet_state(stack):
    data = stack["tag"]["enderio:data"]
    # The item data codec omits the registered default when the toggle is off.
    return data["enderio:energy"], data.get("enderio:toggled", 0)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for key in ("java", "forge-server", "jar", "launch"):
        parser.add_argument(f"--{key}", type=Path, required=True)
    parser.add_argument("--curios-jar", type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    if args.curios_jar:
        shutil.copyfile(args.curios_jar, directory / "mods/curios.jar")
    write_pack(directory, bool(args.curios_jar))
    report = {"passed": False, "assertions": [], "servers": [], "samples": {}, "clients": {},
              "mods": {jar.stem: digest(jar) for jar in (directory / "mods").glob("*.jar")}}

    def fresh(server, results):
        prepare(server)
        run_client(args, server, results, report, "exercise")

    def reopen(server, results):
        server.assert_state("the_uncollected_drops_persist_and_the_collected_stack_stays_absent",
                            f'if entity {selector("blacklisted")} if entity {selector("outside")} unless entity {selector("inside")}', results)
        run_client(args, server, results, report, "verify")

    try:
        run_stage(args, directory, report, "fresh", fresh)
        verify_saved_player(directory, report["assertions"], report["savedCharge"], bool(args.curios_jar))
        run_stage(args, directory, report, "reopened", reopen)
        verify_saved_player(directory, report["assertions"], report["savedCharge"], bool(args.curios_jar))
        report["passed"] = True
    finally:
        result = directory / "electromagnet-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Electromagnet passed={report['passed']}, {len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
