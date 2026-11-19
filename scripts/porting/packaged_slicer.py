#!/usr/bin/env python3
"""Exercise stock Slice'N'Splice jobs, tool wear, and persistence on packaged Forge."""

import argparse
import json
from pathlib import Path
import subprocess
import sys
import time

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_sag_progression import charge, mill, output, ready_at
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage
from packaged_starter_progression import PLAYER, hopper_item


BLOCK = "enderio:slice_and_splice"
AXE, SHEARS, CAP = "minecraft:iron_axe", "minecraft:shears", "enderio:basic_capacitor"
SOULARIUM, ENERGETIC, SILICON = "enderio:soularium_ingot", "enderio:energetic_alloy_ingot", "enderio:silicon"
PAID, COST = 3600, 20000
CASES = (
    (3163, "z_logic_controller", (SOULARIUM, "minecraft:zombie_head", SOULARIUM, SILICON, "minecraft:redstone", SILICON)),
    (3171, "zombie_electrode", (ENERGETIC, "minecraft:zombie_head", ENERGETIC, SILICON, CAP, SILICON)),
    (3179, "skeletal_contractor", (SOULARIUM, "minecraft:skeleton_skull", SOULARIUM, "minecraft:rotten_flesh", CAP,
                                 "minecraft:rotten_flesh")),
    (3187, "guardian_diode", (ENERGETIC, "minecraft:prismarine_shard", ENERGETIC, "minecraft:prismarine_crystals", CAP,
                            "minecraft:prismarine_crystals")),
    (3195, "ender_resonator", (SOULARIUM, "enderio:enderman_head", SOULARIUM, SILICON, "enderio:vibrant_alloy_ingot", SILICON)),
)
MAIN = CASES[0]


def feed(x):
    return f"{x} 81 8"


def entry(slot, item, damage=None):
    tagged_tool = f',tag:{{Damage:{damage},PortSlicer:159}}' if damage is not None else ""
    return f'{{Slot:{slot},id:"{item}",Count:1{tagged_tool}}}'


def tools_for(case):
    return (249, 237) if case[1] == "ender_resonator" else (7, 11)


def base_state(case, *, shears=True):
    x, _, ingredients = case
    axe_damage, shears_damage = tools_for(case)
    items = [entry(slot, item) for slot, item in enumerate(ingredients)] + [entry(6, AXE, axe_damage), entry(9, CAP)]
    if shears:
        items.append(entry(7, SHEARS, shears_damage))
    return (f'if data block {mill(x)} {{Items:{{Items:[{",".join(items)}]}},'
            'IOConfig:{Modes:{up:"pull",down:"push"}}}')


def partial(case, *, shears=True, energy=0):
    x, name, _ = case
    conditions = (f'{base_state(case, shears=shears)} if data block {mill(x)} {{EnergyStored:{energy},'
                  f'Task:{{RecipeId:"enderio:slicing/{name}",ProgressMade:{PAID},ProgressRequired:{COST},'
                  f'HasDeterminedOutputs:1b,Outputs:[{{Item:{{id:"enderio:{name}",Count:1}}}}]}}}} '
                  f'unless data block {mill(x)} Items.Items[{{Slot:8}}] unless data block {output(x)} Items[0]')
    if not shears:
        conditions += f' unless data block {mill(x)} Items.Items[{{Slot:7}}]'
    return conditions


def finished(case):
    x, name, _ = case
    absent = [0, 1, 2, 3, 4, 5, 8]
    items = [entry(9, CAP)]
    axe_damage, shears_damage = tools_for(case)
    if case[1] == "ender_resonator":
        absent.extend([6, 7])
    else:
        items.extend([entry(6, AXE, axe_damage + 1), entry(7, SHEARS, shears_damage + 1)])
    inventory_checks = " ".join(f'unless data block {mill(x)} Items.Items[{{Slot:{slot}}}]' for slot in absent)
    return (f'if data block {mill(x)} {{EnergyStored:0,Items:{{Items:[{",".join(items)}]}}}} '
            f'unless data block {mill(x)} Task {inventory_checks} {hopper_item(output(x), "enderio:" + name, 1)}')


def setup_machine(server, results, case):
    x, name, ingredients = case
    server.command(f'setblock {mill(x)} {BLOCK}[facing=south]{{IOConfig:{{Modes:{{up:"pull",down:"push"}}}},'
                   f'Items:{{Items:[{entry(9, CAP)}]}}}}')
    server.command(f"setblock {feed(x)} minecraft:hopper[facing=down]")
    server.command(f"setblock {output(x)} minecraft:hopper[facing=down]")
    counts = dict.fromkeys(ingredients, 0)
    for stack in ingredients:
        counts[stack] += 1
    for slot, (stack, count) in enumerate(counts.items()):
        server.command(f"item replace block {feed(x)} container.{slot} with {stack} {count}")
    axe_damage, shears_damage = tools_for(case)
    server.command(f"item replace block {feed(x)} container.{len(counts)} with {AXE}{{Damage:{axe_damage},PortSlicer:159}}")
    server.assert_state(f"{name}_hopper_fills_the_six_filtered_input_slots_and_axe_slot",
                        f'{base_state(case, shears=False)} unless data block {feed(x)} Items[0]', results, timeout=12)
    server.command(f"item replace block {feed(x)} container.0 with {SHEARS}{{Damage:{shears_damage},PortSlicer:159}}")
    server.assert_state(f"{name}_loads_the_tagged_shears_by_hopper", f'{base_state(case)} unless data block {feed(x)} Items[0]', results)
    charge(server, x, PAID)


def setup(server, results, cases):
    server.command("gamerule doMobSpawning false")
    server.command("gamerule doDaylightCycle false")
    server.command("gamerule spawnRadius 0")
    server.command("time set noon")
    server.command("forceload add 3152 0 3199 15")
    server.assert_state("fixture_chunks_are_loaded", f"if loaded {mill(MAIN[0])} if loaded {mill(cases[-1][0])}", results)
    server.command("fill 3157 79 5 3198 79 15 minecraft:stone_bricks")
    server.command("setworldspawn 3163 80 12")
    for case in cases:
        setup_machine(server, results, case)
    for case in cases:
        server.assert_state(f"{case[1]}_saves_exact_paid_energy_output_and_unworn_tools", partial(case), results, timeout=14)


def check_missing_tool(server, results):
    x = MAIN[0]
    server.assert_state("paid_job_survives_shears_removal", partial(MAIN, shears=False), results)
    charge(server, x, COST - PAID)
    # The real Forge server continues ticking with stored FE and one required tool absent.
    time.sleep(5)
    server.command(f"data get block {mill(x)} Task.ProgressMade")
    server.command(f"data get block {mill(x)} EnergyStored")
    server.assert_state("paid_job_waits_without_spending_more_fe_when_shears_are_missing",
                        partial(MAIN, shears=False, energy=COST - PAID), results, timeout=2)


def run_client(args, server, report):
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "slicer", "--server", f"127.0.0.1:{args.port}", "--output", str(evidence), "--timeout", "165"]
    with (server.directory / "slicer-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            ready_at(server, MAIN[0], "slicer", evidence)
            wait_signal(client, evidence, "shears-removed")
            check_missing_tool(server, report["assertions"])
            (evidence / "server-checked").touch()
            wait_signal(client, evidence, "shears-returned")
            server.assert_state("the_paid_recipe_resumes_and_charges_one_use_to_each_tool", finished(MAIN),
                                report["assertions"], timeout=38)
            (evidence / "server-finished").touch()
            if client.wait(timeout=25) != 0:
                raise AssertionError(f"Slice'N'Splice client failed; see {evidence}")
            summary = json.loads((evidence / "summary.json").read_text())
            frame = summary["client"]["captures"]["saved-slicer-progress"]
            if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Client and server loaded different Ender IO jars")
        finally:
            stop_client(client)
            summary = evidence / "summary.json"
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def headless_missing_tool(server, results):
    x = MAIN[0]
    server.command(f'data remove block {mill(x)} Items.Items[{{Slot:7}}]')
    check_missing_tool(server, results)
    server.command(f'item replace block {feed(x)} container.0 with {SHEARS}{{Damage:11,PortSlicer:159}}')
    server.assert_state("hopper_reinstalls_the_shears_and_the_paid_recipe_resumes", finished(MAIN), results, timeout=38)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for path in ("java", "forge-server", "jar"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--launch", type=Path, help="Operate the real multiplayer menu using the guarded client")
    group.add_argument("--probe-missing-tool", action="store_true", help="Run only the missing-shears regression")
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}
    cases = (MAIN,) if args.probe_missing_tool else CASES

    def resume(server, results):
        for case in cases:
            server.assert_state(f"{case[1]}_retains_all_paid_job_state_after_restart", partial(case), results)
        for case in cases[1:]:
            charge(server, case[0], COST - PAID)
        if args.launch:
            run_client(args, server, report)
        else:
            headless_missing_tool(server, results)
        for case in cases[1:]:
            server.assert_state(f"{case[1]}_uses_exact_fe_and_tool_wear_and_exports_one_output", finished(case), results, timeout=38)

    def reopen(server, results):
        for case in cases:
            server.assert_state(f"{case[1]}_completion_and_exact_output_survive_restart", finished(case), results)

    try:
        run_stage(args, directory, report, "fresh", lambda server, results: setup(server, results, cases))
        run_stage(args, directory, report, "resume", resume)
        run_stage(args, directory, report, "reopened", reopen)
        report["passed"] = True
    finally:
        path = directory / "slicer-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Slice'N'Splice passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
