#!/usr/bin/env python3
"""Check stock Soul Binder jobs, JEI lookup and transfer, and reloadable soul rules."""

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
from packaged_silverfish_binding import COST as SILVERFISH_FE, JOBS as SILVERFISH_JOBS, capture
from packaged_soul_binder import VIAL, xp_tank
from packaged_spawners import WORLD, prepare_profile, run_stage
from packaged_starter_progression import PLAYER


CAPACITOR = "enderio:octadic_capacitor"
MATERIAL_TAG = "{PortBinder:227}"
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
    decoy: str = "zombie"
    health: int = 16

    @property
    def initial_xp(self):
        return max(0, self.xp - 1)

    def pos(self, offset=0):
        return f"{self.x + offset} 80 8"

    def soul_tag(self):
        name = json.dumps({"text": self.soul_name}, separators=(",", ":"))
        return (f'{{"enderio:data":{{"enderio:soul":{{entity_type:"minecraft:{self.entity}",'
                f"entity_tag:{{CustomName:'{name}',Health:{self.health}.0f}}}}}}}}")

    def decoy_tag(self):
        return f'{{"enderio:data":{{"enderio:soul":{{entity_type:"minecraft:{self.decoy}",entity_tag:{{}}}}}}}}'

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
ENGINE = "enderio:soul_engine"
RULE_FIXTURES = (
    Fixture("animal", 4850, "enderio:soularium_grinding_ball", "enderio:animal_token", "cow", "Meadow", 140, 12800),
    Fixture("stock", 4858, ENGINE, ENGINE, "blaze", "Ember", 1100, 188000, "cow"),
    Fixture("reloaded", 4866, ENGINE, ENGINE, "cow", "Clover", 1100, 188000, "blaze"),
)
SILVERFISH_FIXTURES = tuple(Fixture(job.host, job.x, f"minecraft:{job.host}", job.result, job.mob, job.name,
                                   0, SILVERFISH_FE, "endermite", 8) for job in SILVERFISH_JOBS)
RULE_PACK = "soul-binder-rules"


def vanilla_stack(slot, count, item, metadata=None):
    return stack(slot, count, item, metadata=metadata, slot_suffix="b", count_suffix="b")


def prepare(server, results, fixtures):
    first = fixtures[0].x
    last = fixtures[-1].x
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    server.command(f"forceload add {first - 2} 0 {last + 2} 15")
    server.assert_state("binder_chunks_are_loaded", " ".join(f"if loaded {f.pos()}" for f in fixtures), results)
    server.command(f"fill {first - 2} 79 5 {last + 2} 79 14 minecraft:stone_bricks")
    server.command(f"setworldspawn {first} 80 11")
    for fixture in fixtures:
        server.command(f"setblock {fixture.pos()} enderio:soul_binder[facing=south]")
        fluid = xp_tank(fixture.initial_xp) + "," if fixture.initial_xp else ""
        server.command(f"data merge block {fixture.pos()} {{EnergyStored:{INITIAL_FE},{fluid}"
                       f"Items:{{Size:5,Items:[{stack(4, 1, CAPACITOR)}]}}}}")


def queued(fixture):
    if fixture.xp == 0:
        return pending(fixture, fluid=0)
    pos = fixture.pos()
    return (f"if data block {pos} {{EnergyStored:{INITIAL_FE},{xp_tank(fixture.initial_xp)},{fixture.inputs()}}} "
            f"unless data block {pos} Task unless data block {pos} Items.Items[{{Slot:2}}] "
            f"unless data block {pos} Items.Items[{{Slot:3}}]")


def fill_from_hoppers(server, results, fixtures):
    for fixture in fixtures:
        for offset, facing, item, tag in ((-1, "east", VIAL, fixture.soul_tag()),
                                         (1, "west", fixture.material, MATERIAL_TAG)):
            hopper = fixture.pos(offset)
            server.command(f"setblock {hopper} minecraft:hopper[facing={facing}]"
                           f"{{Items:[{vanilla_stack(0, 1, item, tag)}]}}")
            server.assert_state(f"{fixture.name}_{facing}_hopper_empties", f"unless data block {hopper} Items[0]", results)


def client_session(args, server, report, fixtures):
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    summary_path = evidence / "summary.json"
    scenario = "soul-binder-jei"
    if args.rules:
        scenario += "-rules"
    elif args.silverfish:
        scenario += "-silverfish"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", scenario, "--server", f"127.0.0.1:{args.port}", "--output", str(evidence), "--timeout", "240"]
    with (server.directory / "soul-binder-jei-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            if args.client_only:
                (evidence / "server-client-only").touch()
            for index, fixture in enumerate(fixtures):
                server.command(f"clear {PLAYER}")
                server.command(f"tp {PLAYER} {fixture.x + .5} 80 10.5 180 15")
                server.command(f"item replace entity {PLAYER} hotbar.1 with {VIAL}{fixture.decoy_tag()}")
                server.command(f"item replace entity {PLAYER} hotbar.2 with {VIAL}")
                if args.silverfish:
                    job = SILVERFISH_JOBS[index]
                    capture(server, report["assertions"], job)
                    dispenser = job.pos(82)
                    server.command(f"item replace entity {PLAYER} hotbar.3 from block {dispenser} container.0")
                    server.command(f"item replace block {dispenser} container.0 with minecraft:air")
                else:
                    server.command(f"item replace entity {PLAYER} hotbar.3 with {VIAL}{fixture.soul_tag()}")
                server.command(f"item replace entity {PLAYER} hotbar.5 with {fixture.material}{MATERIAL_TAG} 2")
                (evidence / f"server-{fixture.name}-initial").touch()
                wait_signal(client, evidence, f"{fixture.name}-queued")
                wrong = vanilla_stack(1, 1, VIAL, fixture.decoy_tag())
                empty = vanilla_stack(2, 1, VIAL)
                extra = vanilla_stack(5, 1, fixture.material, MATERIAL_TAG)
                server.assert_state(f"{fixture.name}_transfer_preserves_captured_nbt_and_exact_inventory",
                                    f"{queued(fixture)} if data entity {PLAYER} Inventory[{wrong}] "
                                    f"if data entity {PLAYER} Inventory[{empty}] if data entity {PLAYER} Inventory[{extra}] "
                                    f"unless data entity {PLAYER} Inventory[{{Slot:3b}}] "
                                    f'unless data entity {PLAYER} Inventory[{{Slot:2b}}].tag."enderio:data"."enderio:soul"',
                                    report["assertions"])
                (evidence / f"server-{fixture.name}-verified").touch()
                if fixture.name == "stock":
                    finish_before_reload(server, report["assertions"], fixture)
                    reload_rule(server)
            if client.wait(timeout=35) != 0:
                raise AssertionError(f"Soul Binder JEI client failed; see {evidence}")
            mods = json.loads(summary_path.read_text())["client"]["captures"][f"{fixtures[-1].name}-queued"]["loadedMods"]
            matched = all(digest(Path(mods[mod]["file"])) == report[key]
                          for mod, key in (("enderio", "jarSha256"), ("jei", "jeiSha256")))
            report["assertions"].append({"assertion": "client_loads_the_same_enderio_jar_and_pinned_jei", "passed": matched})
            if not matched:
                raise AssertionError("Soul Binder JEI client loaded unexpected jars")
        finally:
            stop_client(client)
            report["client"] = json.loads(summary_path.read_text()) if summary_path.exists() else None


def pending(fixture, *, fluid=None):
    if fluid is None:
        fluid = fixture.xp + REMAINING_XP
    pos = fixture.pos()
    tank = f'if data block {pos} {{{xp_tank(fluid)}}}' if fluid else f'unless data block {pos} Fluids.Tanks[0].Contents.Amount'
    recipe = "enderio:soulbinding/" + fixture.product.split(":")[1]
    return (f'{tank} if data block {pos} {{EnergyStored:0,{fixture.inputs()},'
            f'Task:{{RecipeId:"{recipe}",ProgressMade:{INITIAL_FE},ProgressRequired:{fixture.fe}}}}} '
            f"unless data block {fixture.pos()} Items.Items[{{Slot:2}}] "
            f"unless data block {fixture.pos()} Items.Items[{{Slot:3}}]")


def completed(fixture):
    pos = fixture.pos()
    return (f"if data block {pos} {{EnergyStored:{REMAINING_FE},{xp_tank(REMAINING_XP)},{fixture.outputs()}}} "
            f"unless data block {pos} Task unless data block {pos} Items.Items[{{Slot:0}}] "
            f"unless data block {pos} Items.Items[{{Slot:1}}] "
            f'unless data block {pos} Items.Items[{{Slot:3}}].tag."enderio:data"."enderio:soul"')


def verify_partial(server, results, fixtures):
    advance(server)
    for fixture in fixtures:
        if fixture.name == "stock":
            continue
        if fixture.xp == 0:
            check = f"{fixture.name}_starts_the_job_with_an_empty_xp_tank"
        else:
            check = f"{fixture.name}_holds_both_inputs_at_one_mb_below_cost"
        server.assert_state(check, queued(fixture), results)
        server.command(f"data merge block {fixture.pos()} {{{xp_tank(fixture.xp + REMAINING_XP)}}}")
        server.assert_state(f"{fixture.name}_retains_inputs_and_fluid_during_partial_progress", pending(fixture), results)


def finish_before_reload(server, results, fixture):
    server.assert_state("stock_waits_one_mb_below_xp_cost", queued(fixture), results)
    server.command(f"data merge block {fixture.pos()} {{EnergyStored:{fixture.fe + REMAINING_FE},"
                   f"{xp_tank(fixture.xp + REMAINING_XP)}}}")
    server.assert_state("stock_blaze_binds_before_rule_reload", completed(fixture), results, timeout=90)


def reload_rule(server):
    path = server.directory / WORLD / "datapacks" / RULE_PACK / "data/enderio/eio_soul/engine/minecraft_blaze.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps({"entity": "minecraft:cow", "fluid": "minecraft:water", "power/mb": 1800, "tick/mb": 4}) + "\n")
    start = len(server.lines)
    server.command("reload")
    server.wait("Data loader for eio_soul/engine loaded 6 jsons", start=start, timeout=30)


def reject_previous_rule(server, results, fixture):
    pos = fixture.pos()
    vial = stack(0, 1, VIAL, metadata=fixture.decoy_tag())
    material = stack(1, 1, fixture.material, metadata=MATERIAL_TAG)
    resources = f"EnergyStored:{INITIAL_FE},{xp_tank(fixture.xp + REMAINING_XP)}"
    server.command(f"data merge block {pos} {{{resources},Items:{{Size:5,Items:[{vial},{material},{stack(4, 1, CAPACITOR)}]}}}}")
    advance(server)
    server.assert_state("reloaded_machine_rejects_removed_blaze_even_with_enough_xp",
                        f"if data block {pos} {{{resources},Items:{{Items:[{vial},{material}]}}}} unless data block {pos} Task", results)
    server.command(f"data merge block {pos} {{{xp_tank(fixture.xp - 1)},Items:{{Size:5,Items:[{stack(4, 1, CAPACITOR)}]}}}}")


def finish(server, results, fixtures):
    for fixture in fixtures:
        if fixture.name == "stock":
            server.assert_state("stock_bound_blaze_survives_rule_reload_and_restart", completed(fixture), results)
            continue
        server.assert_state(f"{fixture.name}_partial_job_survives_restart", pending(fixture), results)
        server.command(f"data merge block {fixture.pos()} {{EnergyStored:{fixture.fe - INITIAL_FE + REMAINING_FE}}}")
        server.assert_state(f"{fixture.name}_returns_an_empty_vial_and_spends_exact_fe_and_xp", completed(fixture), results, timeout=90)


def verify_final_save(server, results, fixtures):
    for fixture in fixtures:
        server.assert_state(f"{fixture.name}_result_and_remaining_resources_survive_restart", completed(fixture), results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for path in ("java", "forge-server", "jar"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    parser.add_argument("--launch", type=Path)
    parser.add_argument("--jei", type=Path)
    parser.add_argument("--client-only", action="store_true", help="Install JEI only on the client and load the inputs by hand")
    catalogs = parser.add_mutually_exclusive_group()
    catalogs.add_argument("--rules", action="store_true", help="Use the stock Animal Token and reloadable Soul Engine rule")
    catalogs.add_argument("--silverfish", action="store_true", help="Use all seven zero-XP silverfish recipes")
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    if (args.launch is None) != (args.jei is None):
        parser.error("--launch and --jei must be supplied together")
    if args.client_only and args.launch is None:
        parser.error("--client-only requires --launch and --jei")

    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    fixtures = FIXTURES
    if args.rules:
        fixtures = RULE_FIXTURES
    elif args.silverfish:
        fixtures = SILVERFISH_FIXTURES
    if args.rules:
        pack = directory / WORLD / "datapacks" / RULE_PACK
        pack.mkdir(parents=True)
        (pack / "pack.mcmeta").write_text('{"pack":{"pack_format":15,"description":"Soul Binder rule fixture"}}\n')
    report = {"passed": False, "assertions": [], "servers": [], "jarSha256": digest(directory / "mods/enderio.jar")}
    if args.jei:
        report["jeiSha256"] = digest(args.jei)
        if not args.client_only:
            shutil.copyfile(args.jei, directory / "mods/jei.jar")
            report["jeiServerSha256"] = digest(directory / "mods/jei.jar")

    def fresh(server, results):
        prepare(server, results, fixtures)
        if args.jei:
            client_session(args, server, report, fixtures)
        elif args.rules:
            animal, stock, reloaded = fixtures
            fill_from_hoppers(server, results, (animal, stock))
            finish_before_reload(server, results, stock)
            reload_rule(server)
            reject_previous_rule(server, results, reloaded)
            fill_from_hoppers(server, results, (reloaded,))
        else:
            fill_from_hoppers(server, results, fixtures)
        verify_partial(server, results, fixtures)

    try:
        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", lambda server, results: finish(server, results, fixtures))
        run_stage(args, directory, report, "finished", lambda server, results: verify_final_save(server, results, fixtures))
        report["passed"] = True
    finally:
        path = directory / "soul-binder-jei-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Soul Binder passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
