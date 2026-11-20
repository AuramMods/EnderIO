#!/usr/bin/env python3
"""Transfer named captured souls through JEI and verify two stock Soul Binder jobs."""

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_legacy_painting import stack
from packaged_nutritious_stick import advance
from packaged_server_smoke import ROOT, digest
from packaged_soul_binder import VIAL, xp_tank
from packaged_spawners import prepare_profile, run_stage
from packaged_starter_progression import PLAYER


CAPACITOR = "enderio:octadic_capacitor"
MATERIAL_TAG = "{PortBinder:227}"
WRONG_SOUL = '{"enderio:data":{"enderio:soul":{entity_type:"minecraft:zombie",entity_tag:{}}}}'
INITIAL_FE = 400
REMAINING_FE = 317
REMAINING_XP = 37


@dataclass(frozen=True)
class Fixture:
    name: str
    x: int
    material: str
    product: str
    entity: str
    soul_name: str
    xp: int
    fe: int

    def pos(self, offset=0):
        return f"{self.x + offset} 80 8"

    def soul_tag(self):
        name = json.dumps({"text": self.soul_name}, separators=(",", ":"))
        return (f'{{"enderio:data":{{"enderio:soul":{{entity_type:"minecraft:{self.entity}",'
                f"entity_tag:{{CustomName:'{name}',Health:16.0f}}}}}}}}")

    def inputs(self):
        vial = stack(0, 1, VIAL, metadata=self.soul_tag())
        material = stack(1, 1, self.material, metadata=MATERIAL_TAG)
        return f"Items:{{Items:[{vial},{material}]}}"

    def outputs(self):
        product_tag = self.soul_tag() if self.product == self.material else None
        product = stack(2, 1, self.product, metadata=product_tag)
        return f"Items:{{Items:[{product},{stack(3, 1, VIAL)}]}}"


FIXTURES = (
    Fixture("crystal", 4834, "enderio:vibrant_crystal", "enderio:ender_crystal", "enderman", "West", 1440, 76800),
    Fixture("panel", 4842, "enderio:energetic_photovoltaic_module", "enderio:energetic_photovoltaic_module",
            "phantom", "Sky", 2240, 12800),
)


def vanilla_stack(slot, count, item, metadata=None):
    return stack(slot, count, item, metadata=metadata, slot_suffix="b", count_suffix="b")


def prepare(server, results):
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    server.command("forceload add 4832 0 4847 15")
    server.assert_state("binder_chunk_is_loaded", " ".join(f"if loaded {f.pos()}" for f in FIXTURES), results)
    server.command("fill 4832 79 5 4847 79 14 minecraft:stone_bricks")
    server.command("setworldspawn 4834 80 11")
    for fixture in FIXTURES:
        server.command(f"setblock {fixture.pos()} enderio:soul_binder[facing=south]")
        server.command(f"data merge block {fixture.pos()} {{EnergyStored:{INITIAL_FE},{xp_tank(fixture.xp - 1)},"
                       f"Items:{{Size:5,Items:[{stack(4, 1, CAPACITOR)}]}}}}")


def queued(fixture):
    pos = fixture.pos()
    return (f"if data block {pos} {{EnergyStored:{INITIAL_FE},{xp_tank(fixture.xp - 1)},{fixture.inputs()}}} "
            f"unless data block {pos} Task unless data block {pos} Items.Items[{{Slot:2}}] "
            f"unless data block {pos} Items.Items[{{Slot:3}}]")


def fill_from_hoppers(server, results):
    for fixture in FIXTURES:
        for offset, facing, item, tag in ((-1, "east", VIAL, fixture.soul_tag()),
                                         (1, "west", fixture.material, MATERIAL_TAG)):
            hopper = fixture.pos(offset)
            server.command(f"setblock {hopper} minecraft:hopper[facing={facing}]"
                           f"{{Items:[{vanilla_stack(0, 1, item, tag)}]}}")
            server.assert_state(f"{fixture.name}_{facing}_hopper_empties", f"unless data block {hopper} Items[0]", results)


def client_session(args, server, report):
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    summary_path = evidence / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "soul-binder-jei", "--server", f"127.0.0.1:{args.port}", "--output", str(evidence), "--timeout", "220"]
    with (server.directory / "soul-binder-jei-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            for fixture in FIXTURES:
                server.command(f"clear {PLAYER}")
                server.command(f"tp {PLAYER} {fixture.x + .5} 80 10.5 180 15")
                server.command(f"item replace entity {PLAYER} hotbar.1 with {VIAL}{WRONG_SOUL}")
                server.command(f"item replace entity {PLAYER} hotbar.2 with {VIAL}")
                server.command(f"item replace entity {PLAYER} hotbar.3 with {VIAL}{fixture.soul_tag()}")
                server.command(f"item replace entity {PLAYER} hotbar.5 with {fixture.material}{MATERIAL_TAG} 2")
                (evidence / f"server-{fixture.name}-initial").touch()
                wait_signal(client, evidence, f"{fixture.name}-queued")
                wrong = vanilla_stack(1, 1, VIAL, WRONG_SOUL)
                empty = vanilla_stack(2, 1, VIAL)
                extra = vanilla_stack(5, 1, fixture.material, MATERIAL_TAG)
                server.assert_state(f"{fixture.name}_transfer_preserves_captured_nbt_and_exact_inventory",
                                    f"{queued(fixture)} if data entity {PLAYER} Inventory[{wrong}] "
                                    f"if data entity {PLAYER} Inventory[{empty}] if data entity {PLAYER} Inventory[{extra}] "
                                    f"unless data entity {PLAYER} Inventory[{{Slot:3b}}] "
                                    f'unless data entity {PLAYER} Inventory[{{Slot:2b}}].tag."enderio:data"."enderio:soul"',
                                    report["assertions"])
                (evidence / f"server-{fixture.name}-verified").touch()
            if client.wait(timeout=35) != 0:
                raise AssertionError(f"Soul Binder JEI client failed; see {evidence}")
            mods = json.loads(summary_path.read_text())["client"]["captures"]["panel-queued"]["loadedMods"]
            matched = all(digest(Path(mods[mod]["file"])) == report[key]
                          for mod, key in (("enderio", "jarSha256"), ("jei", "jeiSha256")))
            report["assertions"].append({"assertion": "client_and_server_load_identical_enderio_and_jei_jars", "passed": matched})
            if not matched:
                raise AssertionError("Soul Binder JEI client loaded unexpected jars")
        finally:
            stop_client(client)
            report["client"] = json.loads(summary_path.read_text()) if summary_path.exists() else None


def pending(fixture):
    recipe = "enderio:soulbinding/" + fixture.product.split(":")[1]
    return (f'if data block {fixture.pos()} {{EnergyStored:0,{xp_tank(fixture.xp + REMAINING_XP)},{fixture.inputs()},'
            f'Task:{{RecipeId:"{recipe}",ProgressMade:{INITIAL_FE},ProgressRequired:{fixture.fe}}}}} '
            f"unless data block {fixture.pos()} Items.Items[{{Slot:2}}] "
            f"unless data block {fixture.pos()} Items.Items[{{Slot:3}}]")


def completed(fixture):
    pos = fixture.pos()
    return (f"if data block {pos} {{EnergyStored:{REMAINING_FE},{xp_tank(REMAINING_XP)},{fixture.outputs()}}} "
            f"unless data block {pos} Task unless data block {pos} Items.Items[{{Slot:0}}] "
            f"unless data block {pos} Items.Items[{{Slot:1}}] "
            f'unless data block {pos} Items.Items[{{Slot:3}}].tag."enderio:data"."enderio:soul"')


def verify_partial(server, results):
    advance(server)
    for fixture in FIXTURES:
        server.assert_state(f"{fixture.name}_holds_both_inputs_at_one_mb_below_cost", queued(fixture), results)
        server.command(f"data merge block {fixture.pos()} {{{xp_tank(fixture.xp + REMAINING_XP)}}}")
        server.assert_state(f"{fixture.name}_retains_inputs_and_fluid_during_partial_progress", pending(fixture), results)


def finish(server, results):
    for fixture in FIXTURES:
        server.assert_state(f"{fixture.name}_partial_job_survives_restart", pending(fixture), results)
        server.command(f"data merge block {fixture.pos()} {{EnergyStored:{fixture.fe - INITIAL_FE + REMAINING_FE}}}")
        server.assert_state(f"{fixture.name}_returns_an_empty_vial_and_spends_exact_fe_and_xp", completed(fixture), results, timeout=90)


def verify_final_save(server, results):
    for fixture in FIXTURES:
        server.assert_state(f"{fixture.name}_result_and_remaining_resources_survive_restart", completed(fixture), results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for path in ("java", "forge-server", "jar"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    parser.add_argument("--launch", type=Path)
    parser.add_argument("--jei", type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    if (args.launch is None) != (args.jei is None):
        parser.error("--launch and --jei must be supplied together")

    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}
    if args.jei:
        shutil.copyfile(args.jei, directory / "mods/jei.jar")
        report["jeiSha256"] = digest(directory / "mods/jei.jar")

    def fresh(server, results):
        prepare(server, results)
        if args.jei:
            client_session(args, server, report)
        else:
            fill_from_hoppers(server, results)
        verify_partial(server, results)

    try:
        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", finish)
        run_stage(args, directory, report, "finished", verify_final_save)
        report["passed"] = True
    finally:
        path = directory / "soul-binder-jei-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Soul Binder passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
