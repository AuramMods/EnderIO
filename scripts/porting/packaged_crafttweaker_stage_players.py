#!/usr/bin/env python3
"""Exercise GameStages with two real guarded players on a disposable Forge server."""

import argparse
from contextlib import ExitStack
import hashlib
import json
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import time

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_crafttweaker import FIXTURES, check_script
from packaged_crafttweaker_stages import COMPANIONS
from packaged_crafter import CRAFT_COST, seed, stack
from packaged_glass_progression import Smelt, finished_smelter, prepare_smelter, supply
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage


OWNER_A, OWNER_B = "EnderIOQA", "EnderIOStageB"
FE = 1500
JOB_A = Smelt(3842, 3, "amethyst_shard", 1, FE)
JOB_B = Smelt(3846, 3, "amethyst_shard", 1, FE)
REVOKED = Smelt(3850, 3, "amethyst_shard", 1, FE)
OFFLINE = Smelt(3854, 3, "amethyst_shard", 1, FE)
QUEUED = Smelt(3850, 9, "amethyst_shard", 1, FE)
CRAFT_A, CRAFT_B = "3842 80 6", "3846 80 6"


def owner_tag(name):
    raw = bytearray(hashlib.md5(f"OfflinePlayer:{name}".encode()).digest())
    raw[6] = (raw[6] & 0x0f) | 0x30
    raw[8] = (raw[8] & 0x3f) | 0x80
    return "[I;" + ",".join(str(value) for value in struct.unpack(">4i", raw)) + "]"


def set_owner(server, pos, name):
    server.command(f"data merge block {pos} {{owner:{owner_tag(name)}}}")


def prepare_job(server, job, name, energy):
    prepare_smelter(server, job)
    set_owner(server, job.pos(80), name)
    supply(server, job, 0, "minecraft:lapis_lazuli", 2)
    supply(server, job, 1, "minecraft:quartz", 1)
    server.command(f"data merge block {job.pos(80)} {{EnergyStored:{energy}}}")


def waiting(job, energy, owner):
    return (f'if data block {job.pos(80)} {{EnergyStored:{energy},owner:{owner_tag(owner)},Items:{{Items:['
            '{Slot:0,id:"minecraft:lapis_lazuli",Count:2},{Slot:1,id:"minecraft:quartz",Count:1}]}} '
            f"unless data block {job.pos(79)} Items[0]")


def connect(stack_, server, args, specs):
    clients = []
    for name, launch in specs:
        evidence = ROOT / "build/port-evidence" / f"{args.name}-{name}"
        log = stack_.enter_context((server.directory / f"{name}-client.log").open("w"))
        process = subprocess.Popen([sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"),
                                    "--launch", str(launch.resolve()), "--scenario", "crafttweaker-stage-player",
                                    "--server", f"127.0.0.1:{args.port}", "--output", str(evidence), "--timeout", "240"],
                                   stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        stack_.callback(stop_client, process)
        clients.append((name, process, evidence))
        wait_signal(process, evidence, "connected")
    return clients


def stop_cleanly(server, client, report):
    name, process, evidence = client
    (evidence / "server-done").touch()
    if process.wait(timeout=35):
        raise AssertionError(f"Guarded stage client {name} failed; see {evidence}")
    server.wait(name + " left the game", timeout=20)
    report["clients"][name] = json.loads((evidence / "summary.json").read_text())


def check_placement(server, results, client):
    name, process, evidence = client
    server.command(f"item replace entity {name} hotbar.0 with enderio:alloy_smelter")
    server.command(f"tp {name} 3848.5 80 8.5 180 38")
    (evidence / "server-try-locked-placement").touch()
    wait_signal(process, evidence, "tried-locked-placement")
    time.sleep(0.35)
    server.assert_state("player_without_feature_stage_cannot_place_a_machine",
                        f'if block 3848 80 5 minecraft:air '
                        f'if data entity {name} Inventory[{{Slot:0b,id:"enderio:alloy_smelter",Count:1b}}]', results)
    server.command(f"gamestage add {name} placement")
    # Force a fresh held-item sync after the denied client's local placement prediction.
    server.command(f"item replace entity {name} hotbar.0 with minecraft:air")
    time.sleep(0.2)
    server.command(f"item replace entity {name} hotbar.0 with enderio:alloy_smelter")
    (evidence / "server-try-unlocked-placement").touch()
    wait_signal(process, evidence, "tried-unlocked-placement")
    server.assert_state("feature_stage_grant_places_one_machine_and_records_its_actual_owner",
                        f"if block 3848 80 5 enderio:alloy_smelter "
                        f"if data block 3848 80 5 {{owner:{owner_tag(name)}}} "
                        f"unless data entity {name} Inventory[{{Slot:0b}}]", results)


def check_paid_output(server, results):
    pos, hopper = QUEUED.pos(80), QUEUED.pos(79)
    prepare_job(server, QUEUED, OWNER_A, 0)
    server.command(f"setblock {hopper} minecraft:air")
    server.command(f'data modify block {pos} Items.Items append value {{Slot:3,id:"minecraft:amethyst_shard",Count:64}}')
    server.command(f"data merge block {pos} {{EnergyStored:{FE + 137}}}")
    paid = (f'if data block {pos} {{EnergyStored:137,Task:{{ProgressMade:{FE},ProgressRequired:{FE},'
            'Outputs:[{Item:{id:"minecraft:amethyst_shard",Count:1}}]}}')
    server.assert_state("full_output_keeps_the_paid_result_in_its_task", paid, results, timeout=25)
    server.command(f"gamestage remove {OWNER_A} parts")
    server.command(f"data remove block {pos} Items.Items[{{Slot:3}}]")
    server.command(f"setblock {hopper} minecraft:hopper[facing=down]")
    time.sleep(0.55)
    server.assert_state("revoked_stage_keeps_paid_output_after_the_output_slot_opens",
                        f"{paid} unless data block {hopper} Items[0]", results)
    server.command(f"gamestage add {OWNER_A} parts")
    server.assert_state("paid_output_is_delivered_once_after_grant",
                        finished_smelter(QUEUED, namespace="minecraft"), results, timeout=25)


def exercise(server, results, args, report):
    check_script(server, "PORT_CT_STAGES_CONFIGURED")
    server.command("forceload add 3840 0 3855 15")
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    server.command("fill 3840 79 0 3855 79 15 minecraft:stone")
    server.command("setworldspawn 3848 80 11")
    server.assert_state("stage_players_chunks_available",
                        f"if loaded {JOB_A.pos(80)} if loaded {OFFLINE.pos(80)}", results)
    with ExitStack() as stack_:
        client_b, client_a = connect(stack_, server, args, [(OWNER_B, args.launch_b), (OWNER_A, args.launch_a)])
        server.assert_state("both_guarded_clients_are_connected",
                            f"if entity {OWNER_A} if entity {OWNER_B}", results)
        for job, name in ((JOB_A, OWNER_A), (JOB_B, OWNER_B)):
            prepare_job(server, job, name, FE + 137)
            server.assert_state(f"unstaged_owner_{name}_keeps_resources",
                                waiting(job, FE + 137, name), results)
        for pos, name in ((CRAFT_A, OWNER_A), (CRAFT_B, OWNER_B)):
            seed(server, pos, CRAFT_COST + 137, [stack(1, "feather"), stack(11, "feather")])
            set_owner(server, pos, name)
        server.command(f"gamestage add {OWNER_A} parts")
        server.command(f"gamestage add {OWNER_A} machining")
        server.assert_state("one_owner_stage_unlocks_only_that_owners_alloy_recipe",
                            f'{finished_smelter(JOB_A, namespace="minecraft")} {waiting(JOB_B, FE + 137, OWNER_B)}',
                            results, timeout=30)
        server.assert_state("recipe_stages_crafts_only_for_the_staged_crafter_owner",
                            f'if data block {CRAFT_A} {{EnergyStored:137,Items:{{Items:[{stack(10, "emerald")}]}}}} '
                            f'unless data block {CRAFT_A} Items.Items[{{Slot:1}}] '
                            f'if data block {CRAFT_B} {{EnergyStored:{CRAFT_COST + 137},Items:{{Items:[{stack(1, "feather")}]}}}} '
                            f'unless data block {CRAFT_B} Items.Items[{{Slot:10}}]', results, timeout=30)

        prepare_job(server, REVOKED, OWNER_A, 320)
        paused = f'if data block {REVOKED.pos(80)} {{Task:{{ProgressMade:320,ProgressRequired:{FE}}}}}'
        server.assert_state("alloy_job_saves_its_partial_work_and_inputs",
                            f"{waiting(REVOKED, 0, OWNER_A)} {paused}", results, timeout=25)
        server.command(f"gamestage remove {OWNER_A} parts")
        server.command(f"data merge block {REVOKED.pos(80)} {{EnergyStored:{FE - 320 + 137}}}")
        time.sleep(1)
        server.assert_state("revoked_stage_preserves_already_paid_progress_without_further_charges",
                            f"{waiting(REVOKED, FE - 320 + 137, OWNER_A)} {paused}", results)
        server.command(f"gamestage add {OWNER_A} parts")
        server.assert_state("restored_stage_finishes_the_same_job_once",
                            finished_smelter(REVOKED, namespace="minecraft"), results, timeout=30)
        check_paid_output(server, results)

        server.command(f"gamestage add {OWNER_B} parts")
        server.assert_state("second_owner_can_work_when_individually_unlocked",
                            finished_smelter(JOB_B, namespace="minecraft"), results, timeout=30)
        stop_cleanly(server, client_b, report)
        prepare_job(server, OFFLINE, OWNER_B, FE + 137)
        time.sleep(1)
        server.assert_state("previously_staged_offline_owner_has_no_personal_processing_context",
                            f"unless entity {OWNER_B} {waiting(OFFLINE, FE + 137, OWNER_B)}", results)
        script = server.directory / "scripts/stage_processing.zs"
        with script.open("a") as handle:
            handle.write('\nEnderIOStages.clearRecipe(<resource:crafttweaker:qa_stage_alloy>);\n')
        server.command("reload")
        server.assert_state("clearing_the_exact_rule_on_reload_resumes_the_offline_owners_unrestricted_recipe",
                            finished_smelter(OFFLINE, namespace="minecraft"), results, timeout=30)
        check_placement(server, results, client_a)
        stop_cleanly(server, client_a, report)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    for key in ("forge-server", "jar", "mod-dir", "launch-a", "launch-b"):
        parser.add_argument(f"--{key}", type=Path, required=True)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    directory.parent.mkdir(parents=True, exist_ok=True)
    prepare_profile(args, directory, args.jar)
    for name in COMPANIONS:
        shutil.copyfile(args.mod_dir / name, directory / "mods" / name)
    scripts = directory / "scripts"
    scripts.mkdir()
    shutil.copyfile(FIXTURES / "stage_processing.zs", scripts / "stage_processing.zs")
    report = {"passed": False, "assertions": [], "servers": [], "clients": {},
              "jarSha256": digest(directory / "mods/enderio.jar")}
    try:
        run_stage(args, directory, report, "stage_players",
                  lambda server, results: exercise(server, results, args, report))
        report["passed"] = True
    finally:
        result = directory / "crafttweaker-stage-players-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"CraftTweaker multiplayer stages passed={report['passed']}, {result}", flush=True)


if __name__ == "__main__":
    main()
