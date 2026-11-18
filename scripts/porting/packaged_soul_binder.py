#!/usr/bin/env python3
"""Exercise the real Phantom solar recipe in a packaged Forge server."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_server_smoke import ROOT, Server, digest


WORLD = "port-soul-binder"
BINDER = "1240 80 8"
VIAL_HOPPER = "1239 80 8"
PANEL_HOPPER = "1241 80 8"
XP_TANK = "1240 80 7"
OUTPUT_HOPPER = "1240 79 8"
OUTPUT_LOCK = "1240 78 8"
PLACED_PANEL = "1242 80 10"
UNBOUND_PANEL = "1244 80 10"
PANEL = "enderio:vibrant_photovoltaic_module"
VIAL = "enderio:soul_vial"
RECIPE = "enderio:soulbinding/vibrant_photovoltaic_module"
PHANTOM = '{entity_type:"minecraft:phantom",entity_tag:{}}'
BOUND = f'tag:{{"enderio:data":{{"enderio:soul":{PHANTOM}}}}}'
XP_FLUID = "enderio:fluid_xp_juice_still"
XP_COST = 5600
XP_INPUT = 6237
ENERGY_COST = 288000
INITIAL_ENERGY = 36781
ENERGY_LEFTOVER = 317


def xp_tank(amount):
    return f'Fluids:{{Tanks:[{{Index:0,Contents:{{FluidName:"{XP_FLUID}",Amount:{amount}}}}}]}}'


def item(slot, item_id, extra="", vanilla=False):
    suffix = "b" if vanilla else ""
    tag = f",{extra}" if extra else ""
    slot_field = f"Slot:{slot}{suffix}," if slot is not None else ""
    return f'{{{slot_field}id:"{item_id}",Count:1{suffix}{tag}}}'


def pending_state():
    return (f'if data block {BINDER} {{EnergyStored:0,{xp_tank(XP_INPUT)},Task:{{RecipeId:"{RECIPE}",'
            f'ProgressMade:{INITIAL_ENERGY},ProgressRequired:{ENERGY_COST}}},'
            f'Items:{{Items:[{item(0, VIAL, BOUND)},{item(1, PANEL)},{item(4, "enderio:octadic_capacitor")}]}}}} '
            f'unless data block {BINDER} Items.Items[{{Slot:2}}] '
            f'unless data block {BINDER} Items.Items[{{Slot:3}}]')


def start_craft(server, results):
    server.command("forceload add 1232 0 1247 15")
    server.assert_state("binder_chunk_is_loaded", f"if loaded {BINDER}", results)
    server.command(f"setblock {BINDER} enderio:soul_binder[facing=south]")
    server.command(f"setblock {OUTPUT_LOCK} minecraft:redstone_block")
    server.command(f"setblock {OUTPUT_HOPPER} minecraft:hopper[facing=down]")
    server.command(f"data merge block {BINDER} {{EnergyStored:{INITIAL_ENERGY},"
                   f'Items:{{Size:5,Items:[{item(4, "enderio:octadic_capacitor")}]}}}}')
    server.command(f"setblock {XP_TANK} enderio:fluid_tank[facing=south]")
    server.command(f'data merge block {XP_TANK} {{{xp_tank(XP_COST - 1)},IOConfig:{{Modes:{{south:"push"}}}}}}')
    server.command(f"setblock {VIAL_HOPPER} minecraft:hopper[facing=east]"
                   f"{{Items:[{item(0, VIAL, BOUND, vanilla=True)}]}}")
    server.command(f"setblock {PANEL_HOPPER} minecraft:hopper[facing=west]"
                   f"{{Items:[{item(0, PANEL, vanilla=True)}]}}")
    server.assert_state("hoppers_and_tank_feed_inputs_but_xp_gate_holds",
                        f"unless data block {VIAL_HOPPER} Items[{{Slot:0b}}] "
                        f"unless data block {PANEL_HOPPER} Items[{{Slot:0b}}] "
                        f"unless data block {XP_TANK} Fluids.Tanks[0].Contents.Amount "
                        f"if data block {BINDER} {{EnergyStored:{INITIAL_ENERGY},{xp_tank(XP_COST - 1)},"
                        f"Items:{{Items:[{item(0, VIAL, BOUND)},{item(1, PANEL)}]}}}} "
                        f"unless data block {BINDER} Task", results)
    server.command(f"data merge block {XP_TANK} {{{xp_tank(XP_INPUT - XP_COST + 1)}}}")
    server.assert_state("partial_job_stops_at_exact_energy_budget", pending_state(), results, timeout=20)


def complete_recipe(server, results):
    final_budget = ENERGY_COST - INITIAL_ENERGY + ENERGY_LEFTOVER
    server.command(f"data merge block {BINDER} {{EnergyStored:{final_budget}}}")
    product = (f'if data block {BINDER} {{EnergyStored:{ENERGY_LEFTOVER},'
               f'Items:{{Items:[{item(2, PANEL, BOUND)},{item(3, VIAL)}]}}}} '
               f'unless data block {BINDER} Items.Items[{{Slot:0}}] '
               f'unless data block {BINDER} Items.Items[{{Slot:1}}] '
               f'unless data block {BINDER} Items.Items[{{Slot:3}}].tag."enderio:data"."enderio:soul"')
    server.assert_state("real_recipe_binds_phantom_returns_empty_vial_and_spends_288000_fe", product, results, timeout=120)
    server.command(f"data get block {BINDER}")
    server.assert_state("soul_binding_spends_only_5600_mb_xp", f"if data block {BINDER} {{{xp_tank(XP_INPUT - XP_COST)}}}", results)


def finish_craft(server, results):
    server.assert_state("pending_soul_recipe_and_resources_survive_restart", pending_state(), results)
    complete_recipe(server, results)
    server.command(f"setblock {OUTPUT_LOCK} minecraft:air")
    server.assert_state("hopper_extracts_both_outputs",
                        f'if data block {OUTPUT_HOPPER} {{Items:[{item(0, PANEL, BOUND, vanilla=True)},'
                        f'{item(1, VIAL, vanilla=True)}]}} unless data block {BINDER} Items.Items[{{Slot:2}}] '
                        f'unless data block {BINDER} Items.Items[{{Slot:3}}]', results)


def verify_placed_panel(server, results, name):
    server.assert_state(name + "_retains_bound_soul", f"if block {PLACED_PANEL} {PANEL} "
                        f"if data block {PLACED_PANEL} {{EntityStorage:{PHANTOM}}}", results)
    server.command(f"execute store result score #bound port_binder_fe run data get block {PLACED_PANEL} EnergyStored")
    server.command(f"execute store result score #control port_binder_fe run data get block {UNBOUND_PANEL} EnergyStored")
    server.assert_state(name + "_only_bound_panel_generates_at_midnight",
                        "if score #bound port_binder_fe matches 1.. if score #control port_binder_fe matches 0", results)


def finish_with_client(args, server, report):
    results = report["assertions"]
    server.assert_state("pending_soul_recipe_and_resources_survive_restart", pending_state(), results)
    server.command("fill 1237 79 9 1246 79 13 minecraft:stone")
    server.command("gamerule doDaylightCycle false")
    server.command("gamerule doMobSpawning false")
    server.command("time set midnight")
    server.command("weather clear")
    server.command("scoreboard objectives add port_binder_fe dummy")
    server.command(f"setblock {UNBOUND_PANEL} {PANEL}")
    output = ROOT / "build/port-evidence" / (args.name + "-client")
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
               "--launch", str(args.launch.resolve()), "--scenario", "soul-binder", "--output", str(output),
               "--server", f"127.0.0.1:{args.port}", "--timeout", "220"]
    with (server.directory / "binder-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            server.wait("EnderIOQA joined the game", timeout=150)
            server.command("tp EnderIOQA 1240.5 80 10.5 180 14")
            wait_signal(client, output, "ready")
            complete_recipe(server, results)
            (output / "server-complete").touch()
            wait_signal(client, output, "taken")
            product = item(None, PANEL, BOUND, vanilla=True)
            server.assert_state("real_quick_move_transfers_the_bound_panel",
                                f"if data entity EnderIOQA Inventory[{product}] "
                                f"unless data block {BINDER} Items.Items[{{Slot:2}}]", results)
            (output / "server-place").touch()
            wait_signal(client, output, "placed")
            verify_placed_panel(server, results, "survival_placement")
            server.assert_state("survival_placement_consumes_the_crafted_item",
                                f'unless data entity EnderIOQA Inventory[{{id:"{PANEL}"}}]', results)
            (output / "server-verified").touch()
            if client.wait(timeout=35) != 0:
                raise AssertionError(f"Soul Binder client failed; see {output}")
            report["client"] = json.loads((output / "summary.json").read_text())
            frame = report["client"]["client"]["captures"]["soul-binder-complete"]
            client_jar = Path(frame["loadedMods"]["enderio"]["file"])
            matched = digest(client_jar) == report["jarSha256"]
            results.append({"assertion": "client_and_server_used_the_same_jar", "passed": matched})
            if not matched:
                raise AssertionError("Soul Binder client and server jars differ")
        finally:
            stop_client(client)


def verify_crafted_panel_after_restart(server, results):
    verify_placed_panel(server, results, "second_restart")
    server.assert_state("binder_remainder_and_empty_vial_survive_second_restart",
                        f"if data block {BINDER} {{EnergyStored:{ENERGY_LEFTOVER},{xp_tank(XP_INPUT - XP_COST)},"
                        f"Items:{{Items:[{item(3, VIAL)}]}}}}", results)


def run_stage(args, directory, report, name, scenario):
    server = Server(directory, args.java, name)
    try:
        server.wait('For help, type "help"', timeout=150)
        scenario(server, report["assertions"])
        server.command("save-all flush")
    finally:
        exit_code = server.stop()
        errors = [line.strip() for line in server.lines if "/ERROR]" in line or "/FATAL]" in line]
        report["servers"].append({"stage": name, "exit": exit_code, "errors": errors, "log": str(server.log_path)})
    if exit_code or errors:
        raise RuntimeError(f"Forge errors in {name}; see {server.log_path}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--launch", type=Path, help="Prepared hidden client to use the Soul Binder menu and place the product")
    parser.add_argument("--port", type=int, default=25728)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")

    directory = ROOT / "build/port-runtime" / args.name
    directory.mkdir()
    shutil.copytree(args.forge_server / "libraries", directory / "libraries")
    (directory / "mods").mkdir()
    artifact = directory / "mods/enderio.jar"
    shutil.copyfile(args.jar, artifact)
    (directory / "eula.txt").write_text("eula=true\n")
    flat = '{"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:plains"}'
    connection = "online-mode=false\nenforce-secure-profile=false\n" if args.launch else ""
    (directory / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={args.port}\nlevel-name={WORLD}\n"
        f"level-type=minecraft:flat\ngenerator-settings={flat}\n"
        "generate-structures=false\nview-distance=3\nsimulation-distance=3\n" + connection)
    report = {"passed": False, "jarSha256": digest(artifact), "assertions": [], "servers": []}
    try:
        run_stage(args, directory, report, "partial", start_craft)
        if args.launch:
            run_stage(args, directory, report, "finish", lambda server, _: finish_with_client(args, server, report))
            run_stage(args, directory, report, "restart", verify_crafted_panel_after_restart)
        else:
            run_stage(args, directory, report, "finish", finish_craft)
        report["passed"] = True
    finally:
        output = directory / "soul-binder-results.json"
        output.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Soul Binder passed={report['passed']}, {len(report['assertions'])} assertions, {output}", flush=True)


if __name__ == "__main__":
    main()
