#!/usr/bin/env python3
"""Mine with an original Auto Smelt pickaxe and exercise native Forge loot recipes."""

import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage


PLAYER = "EnderIOQA"
ORE = "3730 80 8"
BARREL = "3730 80 13"
FIXTURE_ORE = "3736 80 8"
OUTPUT = "3737 80 8"
AUTO_SMELT = '{id:"enderio:auto_smelt",lvl:1s}'
EFFICIENCY = '{id:"minecraft:efficiency",lvl:5s}'
FORTUNE = '{id:"minecraft:fortune",lvl:3s}'
SCRAP = '@e[type=minecraft:item,x=3730.5,y=80,z=8.5,distance=..2,nbt={Item:{id:"minecraft:netherite_scrap",Count:1b}}]'
DEBRIS = '@e[type=minecraft:item,x=3730.5,y=80,z=8.5,distance=..2,nbt={Item:{id:"minecraft:ancient_debris"}}]'
UNSMELTABLE_TAG = '{Damage:17,PortFixture:"unsmeltable"}'


def tool(damage=37, *, fortune=False):
    enchantments = [AUTO_SMELT, EFFICIENCY]
    if fortune:
        enchantments.append(FORTUNE)
    return f'{{Damage:{damage},PortFixture:"historical_tool",Enchantments:[{",".join(enchantments)}]}}'


def stack(slot, item, count=1, tag=None):
    metadata = f",tag:{tag}" if tag else ""
    return f'{{Slot:{slot}b,id:"minecraft:{item}",Count:{count}b{metadata}}}'


def loot_state(*stacks):
    return f'if data block {OUTPUT} {{Items:[{",".join(stacks)}]}} unless data block {OUTPUT} Items[{len(stacks)}]'


def mine_loot(server, source, tool_arg):
    server.command(f"data merge block {OUTPUT} {{Items:[]}}")
    server.command(f"loot replace block {OUTPUT} container.0 3 mine {source} {tool_arg}")
    server.command(f"data get block {OUTPUT} Items")


def original_tool():
    return f'if data block {BARREL} Items[{stack(0, "diamond_pickaxe", tag=tool())}] unless data block {BARREL} Items[1]'


def legacy_fixture(server, results):
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    server.command("setworldspawn 3730 80 11")
    server.command("forceload add 3728 0 3743 15")
    server.assert_state("forge6_fixture_chunk_is_loaded", f"if loaded {ORE} if loaded {OUTPUT}", results)
    server.command("fill 3727 79 4 3740 79 14 minecraft:stone")
    server.command(f"setblock {BARREL} minecraft:barrel")
    server.command(f"setblock {OUTPUT} minecraft:barrel")
    server.command(f"setblock {ORE} minecraft:ancient_debris")
    server.command(f"setblock {FIXTURE_ORE} minecraft:raw_copper_block")
    server.command(f"item replace block {BARREL} container.0 with minecraft:diamond_pickaxe{tool()}")
    server.assert_state("forge6_saves_the_exact_enchanted_pickaxe", original_tool(), results)
    mine_loot(server, ORE, f"minecraft:diamond_pickaxe{tool()}")
    server.assert_state("forge6_modifier_converts_ancient_debris_to_scrap", loot_state(stack(0, "netherite_scrap")), results)


def write_recipe(directory, result, count):
    pack = directory / WORLD / "datapacks/auto-smelt-fixture"
    recipe = pack / "data/minecraft/recipes/copper_ingot_from_smelting_raw_copper.json"
    recipe.parent.mkdir(parents=True, exist_ok=True)
    recipe.write_text(json.dumps({"type": "minecraft:smelting", "ingredient": {"item": "minecraft:raw_copper"},
                                  "result": {"item": f"minecraft:{result}", "count": count, "nbt": {"PortFixture": result}},
                                  "experience": 0.0, "cookingtime": 200}) + "\n")


def write_fixture_pack(directory):
    write_recipe(directory, "copper_ingot", 4)
    pack = directory / WORLD / "datapacks/auto-smelt-fixture"
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 15, "description": "Auto Smelt loot fixture"}}) + "\n")
    table = pack / "data/minecraft/loot_tables/blocks/raw_copper_block.json"
    table.parent.mkdir(parents=True)
    entries = [
        {"type": "minecraft:item", "name": "minecraft:raw_copper",
         "functions": [{"function": "minecraft:set_count", "count": 20}]},
        {"type": "minecraft:item", "name": "minecraft:diamond_pickaxe",
         "functions": [{"function": "minecraft:set_nbt", "tag": UNSMELTABLE_TAG}]},
    ]
    table.write_text(json.dumps({"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [entry]} for entry in entries]}) + "\n")


def reloaded_loot():
    return loot_state(stack(0, "gold_ingot", 60, '{PortFixture:"gold_ingot"}'),
                      stack(1, "diamond_pickaxe", tag=UNSMELTABLE_TAG))


def verify_drop_rules(server, results):
    mine_loot(server, ORE, f"minecraft:diamond_pickaxe{tool()}")
    server.assert_state("converted_world_restores_the_native_auto_smelt_modifier", loot_state(stack(0, "netherite_scrap")), results)
    mine_loot(server, ORE, 'minecraft:diamond_pickaxe{Enchantments:[{id:"minecraft:silk_touch",lvl:1s}]}')
    server.assert_state("silk_touch_without_auto_smelt_keeps_the_block", loot_state(stack(0, "ancient_debris")), results)

    mine_loot(server, FIXTURE_ORE, "minecraft:diamond_pickaxe")
    server.assert_state("ordinary_tool_leaves_raw_and_tagged_drops_unchanged",
                        loot_state(stack(0, "raw_copper", 20), stack(1, "diamond_pickaxe", tag=UNSMELTABLE_TAG)), results)
    mine_loot(server, FIXTURE_ORE, f"minecraft:diamond_pickaxe{tool()}")
    server.assert_state("native_custom_recipe_multiplies_and_splits_tagged_output_without_touching_other_loot",
                        loot_state(stack(0, "copper_ingot", 64, '{PortFixture:"copper_ingot"}'),
                                   stack(1, "copper_ingot", 16, '{PortFixture:"copper_ingot"}'),
                                   stack(2, "diamond_pickaxe", tag=UNSMELTABLE_TAG)), results)
    write_recipe(server.directory, "gold_ingot", 3)
    checkpoint = len(server.lines)
    server.command("reload")
    server.wait("[minecraft/AdvancementList]: Loaded", start=checkpoint)
    mine_loot(server, FIXTURE_ORE, f"minecraft:diamond_pickaxe{tool()}")
    server.assert_state("live_reload_changes_the_result_and_exact_quantity", reloaded_loot(), results)


def inventory(damage, scrap):
    return (f'if data entity {PLAYER} Inventory[{stack(0, "diamond_pickaxe", tag=tool(damage, fortune=True))}] '
            f'if data entity {PLAYER} Inventory[{{id:"minecraft:netherite_scrap",Count:{scrap}b}}] '
            f'if data entity {PLAYER} {{SelectedItemSlot:0}} unless data entity {PLAYER} Inventory[2]')


def exercise(args, server, results, report, *, reopened):
    phase = "verify" if reopened else "exercise"
    output = ROOT / "build/port-evidence" / f"{args.name}-{phase}-client"
    summary_path = output / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "legacy-auto-smelt", "--multiplayer-phase", phase, "--output", str(output),
               "--server", f"127.0.0.1:{args.port}", "--timeout", "145"]
    with (server.directory / f"client-{phase}.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            if reopened:
                server.assert_state("worn_pickaxe_and_first_scrap_return_on_reconnect", inventory(38, 1), results)
                server.command(f"setblock {ORE} minecraft:ancient_debris")
            else:
                server.command(f"item replace entity {PLAYER} hotbar.0 from block {BARREL} container.0")
                server.command(f"item replace block {BARREL} container.0 with minecraft:air")
                server.command(f"enchant {PLAYER} minecraft:silk_touch")
                server.assert_state("native_enchant_command_rejects_silk_touch_on_migrated_tool",
                                    f'if data entity {PLAYER} Inventory[{stack(0, "diamond_pickaxe", tag=tool())}] '
                                    f'unless data entity {PLAYER} Inventory[{{Slot:0b}}].tag.Enchantments[{{id:"minecraft:silk_touch"}}]', results)
                server.command(f"enchant {PLAYER} minecraft:fortune 3")
                server.assert_state("native_enchant_command_accepts_fortune_with_auto_smelt",
                                    f'if data entity {PLAYER} Inventory[{stack(0, "diamond_pickaxe", tag=tool(fortune=True))}]', results)

            server.command(f"tp {PLAYER} 3730.5 80 10.5 180 22")
            (output / "server-mine").touch()
            wait_signal(client, output, "mined")
            server.assert_state(f"{phase}_survival_mining_drops_one_scrap",
                                f"if block {ORE} minecraft:air if entity {SCRAP} unless entity {DEBRIS}", results)
            server.command(f'execute at {SCRAP[:-1]},sort=nearest,limit=1] run tp {PLAYER} ~ ~ ~')
            scrap = 2 if reopened else 1
            server.assert_state(f"{phase}_pickup_preserves_exact_output_and_tool_damage",
                                f"{inventory(37 + scrap, scrap)} unless entity {SCRAP} unless entity {DEBRIS}", results)
            (output / "server-verified").touch()
            if client.wait(timeout=35) != 0:
                raise AssertionError(f"Auto Smelt client failed, see {summary_path}")
            frame = json.loads(summary_path.read_text())["client"]["captures"]["auto-smelt-result"]
            if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Client and server Ender IO jars differ")
        finally:
            stop_client(client)
            report["clients"][phase] = json.loads(summary_path.read_text()) if summary_path.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--legacy-jar", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--launch", type=Path)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")

    directory = ROOT / "build/port-runtime" / args.name
    directory.mkdir()
    legacy = directory / "legacy"
    port = directory / "port"
    prepare_profile(args, legacy, args.legacy_jar)
    prepare_profile(args, port, args.jar)
    report = {"passed": False, "forge": FORGE, "jarSha256": digest(port / "mods/enderio.jar"),
              "legacyJarSha256": digest(legacy / "mods/enderio.jar"), "assertions": [], "servers": [], "clients": {}}
    try:
        def converted(server, results):
            server.assert_state("old_pickaxe_preserves_enchantments_and_damage_on_conversion", original_tool(), results)
            verify_drop_rules(server, results)
            if args.launch:
                exercise(args, server, results, report, reopened=False)

        def reopened(server, results):
            server.assert_state("custom_outputs_and_no_recipe_loot_survive_restart", reloaded_loot(), results)
            mine_loot(server, FIXTURE_ORE, f"minecraft:diamond_pickaxe{tool()}")
            server.assert_state("native_recipe_still_matches_after_restart", reloaded_loot(), results)
            if args.launch:
                exercise(args, server, results, report, reopened=True)

        run_stage(args, legacy, report, "legacy", legacy_fixture)
        shutil.copytree(legacy / WORLD, port / WORLD)
        write_fixture_pack(port)
        run_stage(args, port, report, "converted", converted)
        run_stage(args, port, report, "reopened", reopened)
        report["passed"] = True
    finally:
        path = directory / "legacy-auto-smelt-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Auto Smelt migration passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
