#!/usr/bin/env python3
"""Keep distinct historical material and icon stacks through Forge 6 migration."""

import argparse
import json
from pathlib import Path
import shutil
import sys
from zipfile import ZipFile

sys.dont_write_bytecode = True
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage


ITEMS = (
    "black_paper", "wood_gear", "stone_gear", "powdered_cobalt",
    "enderface_none", "enderface_items", "enderface_materials", "enderface_machines",
    "enderface_conduits", "enderface_mobs", "enderface_invpanel",
)
MATERIAL_TAGS = {"wood_gear": "gears/wood", "stone_gear": "gears/stone", "powdered_cobalt": "dusts/cobalt"}
BARREL = "3560 81 8"
SOURCE = "3562 81 8"
HOPPER = "3562 80 8"
DESTINATION = "3562 79 8"
STAND = "@e[type=minecraft:armor_stand,tag=legacy_item_tags,limit=1]"
COBALT_COUNT = 7


def metadata(item):
    return f'{{PortFixture:"original/{item}"}}'


def stack(slot, item, count):
    return f'{{Slot:{slot}b,id:"enderio:{item}",Count:{count}b,tag:{metadata(item)}}}'


def original_stacks():
    items = ','.join(stack(slot, item, slot + 2) for slot, item in enumerate(ITEMS))
    return (f"if data block {BARREL} {{Items:[{items}]}} "
            f"unless data block {BARREL} Items[{len(ITEMS)}]")


def cobalt_at(pos):
    return f"if data block {pos} Items[{stack(0, 'powdered_cobalt', COBALT_COUNT)}] unless data block {pos} Items[1]"


def write_predicates(directory, namespaces):
    pack = directory / WORLD / "datapacks/legacy-item-tags"
    folder = pack / "data/port_legacy_items/predicates"
    folder.mkdir(parents=True, exist_ok=True)
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 15, "description": "Historical item tag fixture"}}))
    for namespace in namespaces:
        for item, tag in MATERIAL_TAGS.items():
            predicate = {"condition": "minecraft:entity_properties", "entity": "this",
                         "predicate": {"equipment": {"mainhand": {"tag": f"{namespace}:{tag}"}}}}
            (folder / f"{namespace}_{item}.json").write_text(json.dumps(predicate))


def verify_tags(server, results, namespaces):
    server.command('summon minecraft:armor_stand 3560 80 10 {Tags:["legacy_item_tags"],NoGravity:1b}')
    server.assert_state("summoned_stand_is_visible_to_native_selectors", f"if entity {STAND}", results)
    for item in MATERIAL_TAGS:
        slot = ITEMS.index(item)
        server.command(f"data modify entity {STAND} HandItems[0] set from block {BARREL} Items[{{Slot:{slot}b}}]")
        for namespace in namespaces:
            predicate = f"port_legacy_items:{namespace}_{item}"
            server.command("scoreboard players set TagMatch port_legacy_items 0")
            server.command(f"execute as {STAND} if predicate {predicate} run scoreboard players set TagMatch port_legacy_items 1")
            server.assert_state(f"{namespace}_tag_recognizes_{item}",
                                "if score TagMatch port_legacy_items matches 1", results)
    # Remove the test stand without materializing its temporary copy as loot.
    server.command(f"data modify entity {STAND} HandItems[0] set value {{}}")
    server.command(f"kill {STAND}")


def legacy_fixture(server, results):
    server.command("gamerule doMobSpawning false")
    server.command("setworldspawn 3560 80 10")
    server.command("forceload add 3552 0 3567 15")
    server.command("scoreboard objectives add port_legacy_items dummy")
    server.assert_state("historical_fixture_chunk_is_loaded", f"if loaded {BARREL}", results)
    server.command(f"setblock {BARREL} minecraft:barrel")
    server.command(f"setblock {SOURCE} minecraft:barrel")
    for slot, item in enumerate(ITEMS):
        server.command(f"item replace block {BARREL} container.{slot} with enderio:{item}{metadata(item)} {slot + 2}")
    server.command(f"item replace block {SOURCE} container.0 with enderio:powdered_cobalt{metadata('powdered_cobalt')} {COBALT_COUNT}")
    server.assert_state("historical_items_have_distinct_ids_counts_and_tags", f"{original_stacks()} {cobalt_at(SOURCE)}", results)
    verify_tags(server, results, ("forge",))


def converted(server, results):
    server.command(f"data get block {BARREL} Items")
    server.assert_state("first_conversion_preserves_all_eleven_original_stacks", f"{original_stacks()} {cobalt_at(SOURCE)}", results)
    verify_tags(server, results, ("forge", "c"))


def reopened(server, results):
    server.assert_state("original_stacks_stay_intact_after_restart", f"{original_stacks()} {cobalt_at(SOURCE)}", results)
    server.command(f"setblock {DESTINATION} minecraft:barrel")
    server.command(f"setblock {HOPPER} minecraft:hopper[facing=down]")
    transferred(server, results)


def transferred(server, results):
    server.assert_state("native_hopper_moves_exact_cobalt_quantity_and_tag",
                        f"{original_stacks()} {cobalt_at(DESTINATION)} unless data block {SOURCE} Items[0] "
                        f"unless data block {HOPPER} Items[0]", results, timeout=15)


def inspect_models(jar, legacy_jar):
    verified_models = []
    with ZipFile(jar) as port, ZipFile(legacy_jar) as legacy:
        language = json.loads(port.read("assets/enderio/lang/en_us.json"))
        legacy_language = json.loads(legacy.read("assets/enderio/lang/en_us.json"))
        for item in ITEMS:
            model = json.loads(port.read(f"assets/enderio/models/item/{item}.json"))
            key = f"item.enderio.{item}"
            texture = f"assets/enderio/textures/item/{item}.png"
            if (
                model["textures"]["layer0"] != f"enderio:item/{item}"
                or language[key] != legacy_language[key]
                or port.read(texture) != legacy.read(texture)
            ):
                raise AssertionError(f"Historical model, texture or English name changed for {item}")
            verified_models.append(item)
    return verified_models


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--legacy-jar", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")

    directory = ROOT / "build/port-runtime" / args.name
    directory.mkdir()
    legacy = directory / "legacy"
    port = directory / "port"
    prepare_profile(args, legacy, args.legacy_jar)
    prepare_profile(args, port, args.jar)
    write_predicates(legacy, ("forge",))
    report = {"passed": False, "forge": FORGE, "jarSha256": digest(port / "mods/enderio.jar"),
              "legacyJarSha256": digest(legacy / "mods/enderio.jar"), "assertions": [], "servers": []}
    try:
        run_stage(args, legacy, report, "legacy", legacy_fixture)
        shutil.copytree(legacy / WORLD, port / WORLD)
        write_predicates(port, ("forge", "c"))
        run_stage(args, port, report, "converted", converted)
        run_stage(args, port, report, "reopened", reopened)
        run_stage(args, port, report, "transferred", transferred)
        report["models"] = inspect_models(port / "mods/enderio.jar", legacy / "mods/enderio.jar")
        report["passed"] = True
    finally:
        path = directory / "legacy-items-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Legacy items passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
