#!/usr/bin/env python3
"""Craft, configure, mine and replant a Niard in the stock opt-in world."""

import argparse
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_niard import CAPACITOR, PACK, await_ticks, packs
from packaged_server_smoke import ROOT, digest
from packaged_solar_charging import check
from packaged_spawners import PLAYER, prepare_profile, run_stage


TABLE, SOURCE, DESTINATION = (f"{x} 80 8" for x in (4273, 4277, 4285))
TARGET, FLOOR, SIGNAL = "4286 79 8", "4285 79 8", "4285 80 9"
NIARD = "enderio:niard"
CHARGE, FLUID = 3677, 1400
CONTROLS = 'ActionRange:{range:1,isVisible:1b},RedstoneControl:"active_with_signal"'
BUCKET = '{Slot:2,id:"minecraft:bucket",Count:1}'
INVENTORY = f"Items:[{CAPACITOR},{BUCKET}]"
INGREDIENTS = {"minecraft:bucket": 2, "enderio:fluid_tank": 1, "minecraft:piston": 2, "enderio:void_chassis": 1,
               "enderio:dark_steel_ingot": 2, "enderio:dark_steel_bars": 1}


def lava(amount):
    return f'{{FluidName:"minecraft:lava",Amount:{amount}}}'


def tank(amount):
    return 'Fluids:{Tanks:[{Index:0,Contents:' + lava(amount) + '}]}'


def state(pos, energy, amount):
    return (f"if block {pos} {NIARD} if data block {pos} {{EnergyStored:{energy},{CONTROLS},"
            f"Items:{{{INVENTORY}}},{tank(amount)}}} "
            f"unless data block {pos} Items.Items[{{Slot:1}}]")


def item_data():
    return (f'{{"enderio:energy":{CHARGE},"enderio:item_fluid_content":{lava(FLUID)},'
            f'"enderio:container":{{{INVENTORY}}},"enderio:action_range":{{range:1,isVisible:1b}},'
            '"enderio:redstone_control":"active_with_signal"}')


def after_placement():
    return (f"{state(DESTINATION, CHARGE - 1500, FLUID - 1000)} if block {TARGET} minecraft:lava[level=0] "
            f"if block {FLOOR} minecraft:stone if block {SOURCE} minecraft:air")


def stand(server, output, marker, position):
    x, y, z = map(int, position.split())
    server.command(f"tp {PLAYER} {x + 0.5} {y} {z + 2.5} 180 15")
    (output / f"server-{marker}").touch()


def setup(server, results):
    packs(server, results, True)
    for command in ("gamerule doMobSpawning false", "gamerule doLimitedCrafting true", "gamerule spawnRadius 0",
                    "gamerule doDaylightCycle false", "time set noon", "forceload add 4256 0 4303 15",
                    "fill 4269 78 1 4292 79 15 minecraft:stone", "setworldspawn 4273 80 11", f"setblock {TABLE} minecraft:crafting_table"):
        server.command(command)


def craft_and_configure(server, results, client, output):
    wait_signal(client, output, "connected")
    for slot, (ingredient, count) in enumerate(INGREDIENTS.items()):
        server.command(f"item replace entity {PLAYER} hotbar.{slot} with {ingredient} {count}")
    stand(server, output, "table", TABLE)
    wait_signal(client, output, "crafted")
    consumed = " ".join(f'unless data entity {PLAYER} Inventory[{{id:"{ingredient}"}}]' for ingredient in INGREDIENTS)
    server.assert_state("limited_survival_crafting_consumes_all_nine_stock_ingredients",
                        f'if data entity {PLAYER} Inventory[{{id:"{NIARD}",Count:1b}}] {consumed}', results)
    stand(server, output, "place", SOURCE)
    wait_signal(client, output, "placed")
    server.assert_state("one_fresh_niard_places_with_default_controls_and_empty_storage",
                        f'if block {SOURCE} {NIARD} if data block {SOURCE} {{EnergyStored:0,RedstoneControl:"always_active"}} '
                        f"unless data block {SOURCE} ActionRange unless data block {SOURCE} Items.Items[0] "
                        f"unless data block {SOURCE} Fluids.Tanks[0].Contents.FluidName", results)
    server.command(f"data merge block {SOURCE} {{EnergyStored:{CHARGE},{tank(400)}}}")
    server.command(f"item replace entity {PLAYER} hotbar.1 with enderio:basic_capacitor")
    server.command(f"item replace entity {PLAYER} hotbar.2 with minecraft:lava_bucket")
    (output / "server-open").touch()
    wait_signal(client, output, "configured")
    server.assert_state("native_controls_install_the_capacitor_return_the_bucket_and_keep_1400_mb",
                        f"{state(SOURCE, CHARGE, FLUID)} "
                        f'unless data entity {PLAYER} Inventory[{{id:"minecraft:lava_bucket"}}] '
                        f'unless data entity {PLAYER} Inventory[{{id:"enderio:basic_capacitor"}}]', results)


def carry_and_place_lava(server, results, client, output):
    server.command(f"item replace entity {PLAYER} hotbar.0 with minecraft:diamond_pickaxe")
    stand(server, output, "mine", SOURCE)
    wait_signal(client, output, "mined")
    typed = f'{{id:"{NIARD}",Count:1b,tag:{{"enderio:data":' + item_data() + '}}'
    typed_drop = f"@e[type=minecraft:item,x=4277.5,y=80,z=8.5,distance=..2,nbt={{Item:{typed}}}]"
    server.assert_state("native_mining_drops_one_niard_with_the_complete_typed_state", f"if block {SOURCE} minecraft:air if entity {typed_drop}", results)
    server.command(f"tp {PLAYER} 4277.5 80 8.5")
    wait_signal(client, output, "picked-up")
    server.assert_state("survival_picks_up_the_original_complete_drop", f"if data entity {PLAYER} Inventory[{typed}]", results)
    stand(server, output, "replant", DESTINATION)
    wait_signal(client, output, "replanted")
    server.assert_state("survival_replant_consumes_the_item_and_restores_every_machine_field", f"{state(DESTINATION, CHARGE, FLUID)} "
                        f'unless data entity {PLAYER} Inventory[{{id:"{NIARD}"}}]', results)
    server.command(f"setblock {TARGET} minecraft:air")
    (output / "server-reopen").touch()
    wait_signal(client, output, "reopened-menu")
    await_ticks(server, 155)
    server.assert_state("the_replanted_redstone_gate_preserves_the_full_budget_and_empty_target",
                        f"{state(DESTINATION, CHARGE, FLUID)} if block {TARGET} minecraft:air if block {SIGNAL} minecraft:air", results)
    server.command(f"setblock {SIGNAL} minecraft:redstone_block")
    server.assert_state("a_real_signal_places_the_radius_one_lava_source_for_one_exact_payment", after_placement(), results, timeout=22)
    (output / "server-finish").touch()


def exercise(args, server, results, report):
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "niard", "--server", f"127.0.0.1:{args.port}", "--output", str(evidence), "--timeout", "215"]
    summary = evidence / "summary.json"
    with (server.directory / "niard-progression-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            craft_and_configure(server, results, client, evidence)
            carry_and_place_lava(server, results, client, evidence)
            if client.wait(timeout=35) != 0:
                raise AssertionError(f"Niard client failed; see {evidence}")
            capture = json.loads(summary.read_text())["client"]["captures"]["niard-replanted-state"]
            check("client_and_server_use_the_same_production_artifact", report["jarSha256"],
                  digest(Path(capture["loadedMods"]["enderio"]["file"])), results)
        finally:
            stop_client(client)
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


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
    with (directory / "server.properties").open("a") as properties:
        properties.write(f"initial-enabled-packs=vanilla,{PACK}\n")
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}

    def fresh(server, results):
        setup(server, results)
        exercise(args, server, results, report)

    def reopened(server, results):
        packs(server, results, True)
        server.assert_state("restart_preserves_the_replanted_niard_lava_source_and_exact_balances", after_placement(), results)

    try:
        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", reopened)
        report["passed"] = True
    finally:
        result = directory / "niard-progression-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Niard progression passed={report['passed']}, {len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
