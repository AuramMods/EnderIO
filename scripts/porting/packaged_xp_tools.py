#!/usr/bin/env python3
"""Exercise XP orbs, the Void Vial and the Experience Rod over packaged Forge TCP."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage
from packaged_vacuums import JUICE, fluid, summon_orb


PLAYER = "EnderIOQA"
TANK = "0 80 0"
PLAYER_POS = "0.5 80 3.5"
VIAL = "enderio:void_vial"
ROD = "enderio:experience_rod"
ITEM_FLUID = '"enderio:data"."enderio:item_fluid_content"'


def vial(slot, amount):
    item = f'Inventory[{{Slot:{slot}b,id:"{VIAL}",Count:1b}}]'
    key = f"{item}.tag.{ITEM_FLUID}"
    if amount == 0:
        return f"if data entity {PLAYER} {item} unless data entity {PLAYER} {key}"
    return f'if data entity {PLAYER} {key}{{FluidName:"{JUICE}",Amount:{amount}}}'


def player_xp(amount):
    return f"if data entity {PLAYER} {{XpTotal:{amount}}}"


def tank(amount):
    if amount:
        return f"if data block {TANK} {{{fluid(amount)}}}"
    return f"unless data block {TANK} Fluids.Tanks[0].Contents.FluidName"


def setup(server, results):
    for operation in (
        "forceload add -16 -16 15 15",
        "gamerule doMobSpawning false",
        "gamerule doDaylightCycle false",
        "gamerule spawnRadius 0",
        "time set noon",
        "fill -4 79 -3 4 79 7 minecraft:stone",
        "setworldspawn 0 80 3",
        f"setblock {TANK} enderio:fluid_tank",
    ):
        server.command(operation)
    server.assert_state("test_tank_is_loaded", f"if loaded {TANK} if block {TANK} enderio:fluid_tank", results)


class ClientSession:
    def __init__(self, output, process):
        self.output = output
        self.process = process
        self.index = 0

    def step(self, name, action, held, *, amount=-1, offhand=-1, xp=-1):
        path = self.output / f"server-operation-{self.index}.json"
        temporary = path.with_suffix(".tmp")
        temporary.write_text(json.dumps({"name": name, "action": action, "held": held, "fluid": amount,
                                         "offhand": offhand, "xp": xp}))
        temporary.replace(path)
        self.index += 1
        if action != "inspect":
            wait_signal(self.process, self.output, name)


def set_contents(server, amount):
    server.command(f"data merge block {TANK} {{{fluid(amount)}}}")


def collect(server, results, marker, value, count, expected, *, keep_going=False):
    summon_orb(server, PLAYER_POS, marker, value, count)
    try:
        server.assert_state(marker, f"{expected} unless entity @e[type=minecraft:experience_orb,tag={marker}]", results,
                            timeout=3 if keep_going else 8)
    except AssertionError:
        if not keep_going:
            raise
    finally:
        server.command(f"data get entity {PLAYER} Inventory")
        server.command(f"data get entity {PLAYER} XpTotal")


def equip_tools(server):
    server.command(f"clear {PLAYER}")
    server.command(f"item replace entity {PLAYER} hotbar.0 with {VIAL}")
    server.command(f"item replace entity {PLAYER} hotbar.1 with {ROD}")
    server.command(f"tp {PLAYER} {PLAYER_POS} 180 15")


def reproduce(server, results, client):
    equip_tools(server)
    collect(server, results, "merged_main_hand_orb_keeps_all_twenty_one_points",
            7, 3, f"{player_xp(0)} {vial(0, 420)}", keep_going=True)
    set_contents(server, 159)
    server.command(f"item replace entity {PLAYER} hotbar.0 with {ROD}")
    client.step("rod_withdrawal", "transfer", ROD, xp=0)
    try:
        server.assert_state("rod_grants_seven_points_and_keeps_nineteen_mb",
                            f"{tank(19)} {player_xp(7)}", results, timeout=3)
    except AssertionError:
        pass
    finally:
        server.command(f"data get entity {PLAYER} XpTotal")
        server.command(f"data get block {TANK} Fluids")
    server.command(f"item replace entity {PLAYER} weapon.offhand with {VIAL}")
    client.step("record_before", "inspect", ROD)


def exercise(server, results, client):
    equip_tools(server)
    collect(server, results, "merged_main_hand_orb_keeps_all_twenty_one_points",
            7, 3, f"{player_xp(0)} {vial(0, 420)}")

    server.command(f'item replace entity {PLAYER} weapon.offhand with {VIAL}'
                   f'{{"enderio:data":{{"enderio:item_fluid_content":{{FluidName:"{JUICE}",Amount:3150}}}}}}')
    server.command(f'item replace entity {PLAYER} armor.feet with minecraft:leather_boots'
                   '{Damage:12,Enchantments:[{id:"minecraft:mending",lvl:1s}]}')
    repaired = f'if data entity {PLAYER} Inventory[{{Slot:100b,id:"minecraft:leather_boots",tag:{{Damage:0}}}}]'
    collect(server, results, "offhand_captures_two_and_vanilla_spends_six_on_mending_and_six_on_player",
            7, 2, f"{player_xp(6)} {vial(0, 420)} {vial(-106, 3190)} {repaired}")

    client.step("pour_vial", "transfer", VIAL, amount=420, offhand=3190, xp=6)
    server.assert_state("real_tank_click_moves_all_420_mb_out_of_vial",
                        f"{tank(420)} {vial(0, 0)} {player_xp(6)}", results)
    set_contents(server, 455)
    client.step("refill_vial", "transfer", VIAL, amount=0, xp=6)
    server.assert_state("real_tank_click_transfers_455_mb_into_vial",
                        f"{tank(0)} {vial(0, 455)} {player_xp(6)}", results)
    client.step("drink_vial", "drink", VIAL, amount=455, xp=6)
    server.assert_state("drinking_converts_440_mb_to_22_points_and_retains_15_mb",
                        f"{vial(0, 15)} {vial(-106, 3190)} {player_xp(28)}", results)
    client.step("reject_fractional_drink", "drink-remainder", VIAL, amount=15, xp=28)
    server.assert_state("a_vial_with_less_than_one_point_does_not_drain",
                        f"{vial(0, 15)} {player_xp(28)}", results)

    server.command(f"item replace entity {PLAYER} hotbar.2 from entity {PLAYER} hotbar.0")
    server.command(f"item replace entity {PLAYER} hotbar.0 from entity {PLAYER} hotbar.1")
    server.command(f"item replace entity {PLAYER} hotbar.1 with minecraft:air")
    set_contents(server, 319)
    client.step("withdraw_level_budget", "transfer", ROD, xp=28)
    server.assert_state("rod_withdraws_13_points_and_keeps_59_mb", f"{tank(59)} {player_xp(41)}", results)
    client.step("withdraw_partial_budget", "transfer", ROD, xp=41)
    server.assert_state("rod_withdraws_two_whole_points_and_keeps_19_mb", f"{tank(19)} {player_xp(43)}", results)
    set_contents(server, 15971)
    client.step("deposit_with_29_mb_free", "deposit", ROD, xp=43)
    server.assert_state("rod_deposits_one_point_and_keeps_nine_mb_free",
                        f"{tank(15991)} {player_xp(42)}", results)
    server.command(f"item replace entity {PLAYER} hotbar.1 from entity {PLAYER} hotbar.0")
    server.command(f"item replace entity {PLAYER} hotbar.0 from entity {PLAYER} hotbar.2")
    server.command(f"item replace entity {PLAYER} hotbar.2 with minecraft:air")
    verify_saved(server, results, client, name="inspect_after_use")


def verify_saved(server, results, client, *, name="inspect_after_restart"):
    server.assert_state(name, f"{tank(15991)} {vial(0, 15)} {vial(-106, 3190)} {player_xp(42)} "
                        f'if data entity {PLAYER} Inventory[{{Slot:1b,id:"{ROD}",Count:1b}}]', results)
    client.step(name, "inspect", VIAL, amount=15, offhand=3190, xp=42)


def run_client(args, server, report, results, phase, scenario):
    output = ROOT / "build/port-evidence" / f"{args.name}-{phase}-client"
    summary = output / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
               "--launch", str(args.launch.resolve()), "--scenario", "xp-tools", "--output", str(output),
               "--server", f"127.0.0.1:{args.port}", "--timeout", "165"]
    with (server.directory / f"{phase}-client.log").open("w") as log:
        process = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(process, output, "connected")
            scenario(server, results, ClientSession(output, process))
            if process.wait(timeout=25) != 0:
                raise AssertionError(f"XP tools client failed; see {output}")
            loaded = json.loads(summary.read_text())["client"]["capture"]["loadedMods"]["enderio"]["file"]
            if digest(Path(loaded)) != report["jarSha256"]:
                raise AssertionError("The client and server are running different Ender IO jars")
        finally:
            stop_client(process)
            report["clients"][phase] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--launch", required=True, type=Path)
    parser.add_argument("--reproduce", action="store_true", help="Record independent failures on the original jar")
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "jarSha256": digest(args.jar), "assertions": [], "clients": {}, "servers": []}
    try:
        def first(server, results):
            setup(server, results)
            run_client(args, server, report, results, "initial", reproduce if args.reproduce else exercise)

        run_stage(args, directory, report, "initial", first)
        if not args.reproduce:
            run_stage(args, directory, report, "restarted", lambda server, results:
                      run_client(args, server, report, results, "restarted", verify_saved))
        report["passed"] = all(check["passed"] for check in report["assertions"])
    finally:
        path = directory / "xp-tools-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Packaged XP tools passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
