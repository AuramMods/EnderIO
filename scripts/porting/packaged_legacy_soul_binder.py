#!/usr/bin/env python3
"""Resume stock Soul Binder jobs created in the original Forge 6.2 release."""

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import shutil
import sys

sys.dont_write_bytecode = True
from packaged_nutritious_stick import advance
from packaged_server_smoke import ROOT, digest
from packaged_soul_binder import VIAL, xp_tank
from packaged_spawners import WORLD, prepare_profile, run_stage


PAID = 6000
XP_REMAINDER = 40
CAPACITOR = "enderio:octadic_capacitor"
OLD_FILLED = "enderio:filled_soul_vial"
OLD_EMPTY = "enderio:empty_soul_vial"
SOUL_PATH = '"enderio:data"."enderio:soul"'


@dataclass(frozen=True)
class Binding:
    x: int
    recipe: str
    entity: str
    material: str
    energy: int
    xp: int
    binds_soul: bool = True
    entity_data: str = "NoAI:1b"
    namespace: str = "enderio"
    output: str = ""

    @property
    def binder(self):
        return f"{self.x} 80 8"

    @property
    def body(self):
        name = json.dumps({"text": f"Forge 6 {self.recipe}"}, separators=(",", ":"))
        return f"CustomName:'{name}',{self.entity_data}"

    @property
    def old_soul(self):
        return f'BlockEntityTag:{{EntityStorage:{{Entity:{{id:"minecraft:{self.entity}",{self.body}}}}}}}'

    @property
    def soul(self):
        return f'"enderio:data":{{"enderio:soul":{{entity_type:"minecraft:{self.entity}",entity_tag:{{{self.body}}}}}}}'

    @property
    def task(self):
        return f"{self.namespace}:soulbinding/{self.recipe}"

    @property
    def product(self):
        return self.output or f"enderio:{self.recipe}"


CASES = (
    Binding(5058, "broken_spawner", "pig", "enderio:broken_spawner", 288000, 2240, entity_data="NoAI:1b,Saddle:1b"),
    Binding(5064, "soul_engine", "blaze", "enderio:soul_engine", 188000, 1100),
    Binding(5070, "prescient_crystal", "shulker", "enderio:vibrant_crystal", 100000, 2240,
            binds_soul=False, entity_data="NoAI:1b,Color:4b"),
)
CUSTOM = Binding(5076, "engine", "blaze", "minecraft:blaze_rod", 35600, 800,
                 namespace="port_legacy", output="enderio:soul_engine")


def stack(slot, item_id, *, old=False, vanilla=False, tag=""):
    value = f'id:"{item_id}",Count:1{"b" if old or vanilla else ""}'
    if slot is not None:
        value = f'Slot:{slot}{"b" if vanilla else ""},{value}'
    if tag:
        value += f",tag:{{{tag}}}"
    return "{" + value + "}"


def binder_tank(amount, old):
    if old:
        return ('Fluid:{Size:1,Tanks:[{Index:0,Capacity:10000,FluidName:"enderio:xp_juice",'
                f'Amount:{amount}}}]}}')
    return xp_tank(amount)


def source_job(server, results, case):
    dispenser = f"{case.x} 82 8"
    power = f"{case.x} 83 8"
    feed = f"{case.x} 81 8"
    mob = f"@e[type=minecraft:{case.entity},tag=port_legacy_binder_capture]"
    server.command(f'setblock {dispenser} minecraft:dispenser[facing=east]'
                   f'{{Items:[{stack(0, OLD_EMPTY, vanilla=True)}]}}')
    server.command(f"setblock {case.x + 1} 81 8 minecraft:stone")
    server.command(f'summon minecraft:{case.entity} {case.x + 1}.5 82 8.5 '
                   f'{{{case.body},Tags:["port_legacy_binder_capture"],PersistenceRequired:1b}}')
    server.assert_state(f"forge6_{case.recipe}_capture_target_is_live", f"if entity {mob}", results)
    server.command(f"setblock {power} minecraft:redstone_block")
    server.assert_state(f"forge6_{case.recipe}_native_dispenser_captures_its_named_soul",
                        f'unless entity {mob} if data block {dispenser} Items['
                        f'{stack(0, OLD_FILLED, vanilla=True, tag=case.old_soul)}]', results)
    server.command(f"setblock {power} minecraft:air")

    xp = binder_tank(case.xp + XP_REMAINDER, old=True)
    capacitor = stack(4, CAPACITOR, old=True)
    server.command(f'setblock {case.binder} enderio:soul_binder[facing=south]'
                   f'{{Energy:{{EnergyStored:{PAID}}},{xp},IoConfig:{{"0":1b,"1":2b}},'
                   f'Items:{{Size:5,Items:[{capacitor}]}}}}')
    server.command(f"setblock {feed} minecraft:hopper[facing=down]")
    if case.recipe == "broken_spawner":
        source = f"{case.x} 80 10"
        server.command(f'setblock {source} minecraft:spawner'
                       '{Delay:32767s,SpawnData:{entity:{id:"minecraft:cow"}}}')
        server.command(f"loot replace block {feed} container.1 mine {source} minecraft:diamond_pickaxe")
        server.command(f"setblock {source} minecraft:air")
    else:
        server.command(f"item replace block {feed} container.1 with {case.material}")
    verify_pending(server, results, case, PAID, "forge6", old=True)


def verify_pending(server, results, case, paid, phase, *, old=False):
    pos = case.binder
    tag = case.old_soul if old else case.soul
    vial = OLD_FILLED if old else VIAL
    material_tag = ""
    if case.recipe == "broken_spawner":
        material_tag = ('BlockEntityTag:{EntityStorage:{Entity:{id:"minecraft:cow"}}}' if old else
                        '"enderio:data":{"enderio:soul":{entity_type:"minecraft:cow"}}')
    material = stack(1, case.material, old=old, tag=material_tag)
    vial_item = stack(0, vial, old=old, tag=tag)
    energy = "Energy:{EnergyStored:0}" if old else "EnergyStored:0"
    tank = binder_tank(case.xp + XP_REMAINDER, old)
    product = stack(None, case.product, old=old, tag=tag if case.binds_soul else "")
    returned = stack(None, OLD_EMPTY if old else VIAL, old=old)
    server.command(f"data get block {pos}")
    server.assert_state(f"{phase}_{case.recipe}_preserves_paid_fe_xp_and_exact_queued_outputs",
                        f'if data block {pos} {{{energy},{tank},Items:{{Items:[{vial_item},{material}]}},'
                        f'Task:{{RecipeId:"{case.task}",ProgressMade:{paid},ProgressRequired:{case.energy},'
                        f'HasDeterminedOutputs:1b}}}} '
                        f'if data block {pos} Task.Outputs[{{Item:{product}}}] '
                        f'if data block {pos} Task.Outputs[{{Item:{returned}}}] '
                        f'unless data block {pos} Task.Outputs[2] '
                        f'unless data block {pos} Items.Items[{{Slot:2}}] unless data block {pos} Items.Items[{{Slot:3}}]',
                        results, timeout=85 if phase == "one_fe_short" else 15)


def verify_finished(server, results, case, phase):
    pos = case.binder
    collector = f"{case.x} 79 8"
    product = stack(None, case.product, vanilla=True, tag=case.soul if case.binds_soul else "")
    returned = stack(None, VIAL, vanilla=True)
    server.assert_state(f"{phase}_{case.recipe}_exports_exact_product_and_one_empty_vial",
                        f'if data block {pos} {{EnergyStored:0,{xp_tank(XP_REMAINDER)}}} '
                        f'unless data block {pos} Task unless data block {pos} Items.Items[{{Slot:0}}] '
                        f'unless data block {pos} Items.Items[{{Slot:1}}] unless data block {pos} Items.Items[{{Slot:2}}] '
                        f'unless data block {pos} Items.Items[{{Slot:3}}] '
                        f'if data block {collector} Items[{product}] if data block {collector} Items[{returned}] '
                        f'unless data block {collector} Items[2] '
                        f'unless data block {collector} Items[{{id:"{VIAL}"}}].tag.{SOUL_PATH}',
                        results, timeout=15)


def legacy(server, results, cases):
    server.command("gamerule doMobSpawning false")
    server.command("forceload add 5056 0 5087 15")
    server.assert_state("original_forge_chunks_are_loaded", "if loaded 5058 80 8 if loaded 5076 80 8", results)
    server.command("fill 5056 79 4 5087 79 13 minecraft:stone_bricks")
    for case in cases:
        source_job(server, results, case)


def converted(server, results, cases):
    for case in cases:
        verify_pending(server, results, case, PAID, "converted")
    if CUSTOM in cases:
        verify_wrong_soul(server, results)


def verify_wrong_soul(server, results):
    pos = f"{CUSTOM.x} 80 11"
    pig_vial = f'Items.Items[{stack(0, VIAL, tag=CASES[0].soul)}]'
    server.command(f"setblock {pos} enderio:soul_binder[facing=south]")
    server.command(f'data merge block {pos} {{EnergyStored:{PAID},{xp_tank(CUSTOM.xp + XP_REMAINDER)},'
                   f'Items:{{Size:5,Items:[{stack(1, CUSTOM.material)},{stack(4, CAPACITOR)}]}}}}')
    server.command(f"data modify block {pos} Items.Items append from block {CASES[0].binder} {pig_vial}")
    advance(server, 40)
    server.assert_state("original_engine_rule_rejects_the_migrated_pig_with_all_other_inputs_present",
                        f'if data block {pos} {pig_vial} '
                        f'if data block {pos} {{EnergyStored:{PAID},{xp_tank(CUSTOM.xp + XP_REMAINDER)},'
                        f'Items:{{Items:[{stack(1, CUSTOM.material)}]}}}} '
                        f'unless data block {pos} Task unless data block {pos} Items.Items[{{Slot:2}}]',
                        results)


def resume(server, results, cases):
    for case in cases:
        verify_pending(server, results, case, PAID, "reopened")
    for case in cases:
        server.command(f"data merge block {case.binder} {{EnergyStored:{case.energy - PAID - 1}}}")
    for case in cases:
        verify_pending(server, results, case, case.energy - 1, "one_fe_short")
        server.command(f"setblock {case.x} 79 8 minecraft:hopper[facing=down]")
        server.command(f"data merge block {case.binder} {{EnergyStored:1}}")
        verify_finished(server, results, case, "resumed")


def finished(server, results, cases):
    for case in cases:
        verify_finished(server, results, case, "restarted")


def write_original_datapack(source):
    pack = source / WORLD / "datapacks/port_legacy_soul_binding"
    recipe = pack / "data/port_legacy/recipes/soulbinding/engine.json"
    recipe.parent.mkdir(parents=True)
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {
        "pack_format": 15, "description": "Original Forge 6 Soul Binder recipe",
    }}) + "\n")
    recipe.write_text(json.dumps({
        "type": "enderio:soul_binding", "exp": 4, "souldata": "engine", "energy": CUSTOM.energy,
        "input": {"item": CUSTOM.material}, "output": {"item": CUSTOM.product},
    }) + "\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", type=int, required=True)
    for path in ("java", "forge-server", "legacy-jar", "jar"):
        parser.add_argument(f"--{path}", type=Path, required=True)
    parser.add_argument("--stock-only", action="store_true", help="Exercise the unmodified stock recipe set")
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")

    directory = ROOT / "build/port-runtime" / args.name
    directory.mkdir()
    source, port = directory / "legacy", directory / "port"
    prepare_profile(args, source, args.legacy_jar)
    prepare_profile(args, port, args.jar)
    cases = CASES if args.stock_only else (*CASES, CUSTOM)
    if not args.stock_only:
        write_original_datapack(source)
    report = {"passed": False, "legacySha256": digest(args.legacy_jar), "jarSha256": digest(args.jar),
              "assertions": [], "servers": []}
    try:
        run_stage(args, source, report, "legacy", lambda server, results: legacy(server, results, cases))
        shutil.copytree(source / WORLD, port / WORLD)
        run_stage(args, port, report, "converted", lambda server, results: converted(server, results, cases))
        run_stage(args, port, report, "reopened", lambda server, results: resume(server, results, cases))
        run_stage(args, port, report, "finished", lambda server, results: finished(server, results, cases))
        report["passed"] = True
    finally:
        output = directory / "legacy-soul-binder-results.json"
        output.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Legacy Soul Binder passed={report['passed']}, {len(report['assertions'])} assertions, {output}", flush=True)


if __name__ == "__main__":
    main()
