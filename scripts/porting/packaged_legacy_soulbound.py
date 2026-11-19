#!/usr/bin/env python3
"""Respawn with Soulbound gear from a real Ender IO 6.2 save on packaged Forge."""

import argparse
from contextlib import contextmanager
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
BARREL = "3630 80 8"
SPAWN = "3648 80 8"
DEATH = "3632.5 80 8.5"
SOULBOUND = '{id:"enderio:soulbound",lvl:1s}'
PICKAXE_ENCHANTMENTS = f'{SOULBOUND},{{id:"minecraft:efficiency",lvl:3s}}'
GEAR = (
    ("hotbar.0", "iron_pickaxe", f'{{Damage:37,PortFixture:"pickaxe",Enchantments:[{PICKAXE_ENCHANTMENTS}]}}'),
    ("armor.chest", "iron_chestplate", f'{{Damage:11,PortFixture:"chestplate",Enchantments:[{SOULBOUND}]}}'),
    ("weapon.offhand", "shield", f'{{Damage:17,PortFixture:"shield",Enchantments:[{SOULBOUND}]}}'),
)
BOOK = ("inventory.0", "enchanted_book", f'{{StoredEnchantments:[{SOULBOUND}]}}')
UNBOUND = '{PortFixture:"unbound"}'
PENDING = 'ForgeData.PlayerPersisted."enderio:soulbound_items"'
DROP_SELECTOR = '@e[type=minecraft:item,x=3632.5,y=80,z=8.5,distance=..6'


def saved_stack(item, tag, slot=None):
    location = f"Slot:{slot}b," if slot is not None else ""
    return f'{{{location}id:"minecraft:{item}",Count:1b,tag:{tag}}}'


def original_gear():
    stacks = ','.join(saved_stack(item, tag, slot) for slot, (_, item, tag) in enumerate((*GEAR, BOOK)))
    return f"if data block {BARREL} {{Items:[{stacks}]}} unless data block {BARREL} Items[4]"


def legacy_fixture(server, results):
    server.command("gamerule doMobSpawning false")
    server.command("gamerule spawnRadius 0")
    server.command(f"setworldspawn {SPAWN}")
    server.command("forceload add 3616 0 3663 15")
    server.assert_state("forge6_fixture_chunks_are_loaded", f"if loaded {BARREL} if loaded {SPAWN}", results)
    server.command("fill 3627 79 4 3652 79 12 minecraft:stone")
    server.command(f"setblock {BARREL} minecraft:barrel")
    for slot, (_, item, tag) in enumerate((*GEAR, BOOK)):
        server.command(f"item replace block {BARREL} container.{slot} with minecraft:{item}{tag}")
    server.assert_state("forge6_saves_all_enchanted_items_and_metadata", original_gear(), results)


def drop(item, tag, count=1):
    return f'{DROP_SELECTOR},nbt={{Item:{{id:"minecraft:{item}",Count:{count}b,tag:{tag}}}}}]'


def no_protected_drops():
    return " ".join(f"unless entity {drop(item, tag)}" for _, item, tag in GEAR)


def gear_at(path):
    return " ".join(f"if data entity {PLAYER} {path}[{saved_stack(item, tag)}]" for _, item, tag in GEAR)


def retained_on_death(server, results, name):
    server.command(f"data get entity {PLAYER} ForgeData")
    server.assert_state(name, f'if data entity {PLAYER} {{Health:0.0f}} {gear_at(PENDING)} '
                        f"unless data entity {PLAYER} {PENDING}[3] {no_protected_drops()}", results)


def recovered_gear(server, results, name):
    server.assert_state(name, f'if data entity {PLAYER} {{Health:20.0f}} {gear_at("Inventory")} '
                        f"unless data entity {PLAYER} Inventory[3] unless data entity {PLAYER} {PENDING} "
                        f"{no_protected_drops()}", results)


@contextmanager
def connect_client(args, server, report, phase):
    output = ROOT / "build/port-evidence" / f"{args.name}-{phase}-client"
    summary = output / "summary.json"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "legacy-soulbound", "--output", str(output),
               "--server", f"127.0.0.1:{args.port}", "--timeout", "145"]
    with (server.directory / f"client-{phase}.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            yield client, output
            if client.wait(timeout=35) != 0:
                raise AssertionError(f"Soulbound client failed, see {summary}")
            captures = json.loads(summary.read_text())["client"]["captures"]
            if any(digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"] for frame in captures.values()):
                raise AssertionError("Client and server Ender IO jars differ")
        finally:
            stop_client(client)
            report["clients"][phase] = json.loads(summary.read_text()) if summary.exists() else None


def kill_player(server, client, output):
    server.command(f"spawnpoint {PLAYER} {SPAWN}")
    server.command(f"tp {PLAYER} {DEATH}")
    server.command(f"kill {PLAYER}")
    wait_signal(client, output, "dead")


def respawn(server, results, client, output, name):
    (output / "server-respawn").touch()
    wait_signal(client, output, "respawned")
    recovered_gear(server, results, name)
    (output / "server-verified").touch()


def converted(args, server, results, report):
    server.assert_state("copied_world_keeps_original_gear_and_book", original_gear(), results)
    with connect_client(args, server, report, "first-death") as (client, output):
        for slot, (target, _, _) in enumerate((*GEAR, BOOK)):
            server.command(f"item replace entity {PLAYER} {target} from block {BARREL} container.{slot}")
            server.command(f"item replace block {BARREL} container.{slot} with minecraft:air")
        server.command(f"item replace entity {PLAYER} inventory.1 with minecraft:emerald{UNBOUND} 6")
        server.assert_state("original_gear_is_equipped_once", f"{gear_at('Inventory')} unless data block {BARREL} Items[0]", results)
        kill_player(server, client, output)
        server.assert_state("ordinary_items_and_stored_enchantment_book_drop",
                            f'if entity {drop("emerald", UNBOUND, 6)} if entity {drop(BOOK[1], BOOK[2])}', results)
        for _, item, tag in GEAR:
            server.command(f"data get entity {drop(item, tag)[:-1]},sort=nearest,limit=1] Item")
        retained_on_death(server, results, "death_saves_only_the_active_soulbound_items")
        respawn(server, results, client, output, "survival_respawn_restores_three_exact_stacks_once")


def disconnect_before_respawn(args, server, results, report):
    with connect_client(args, server, report, "disconnect") as (client, output):
        recovered_gear(server, results, "recovered_inventory_survives_full_server_restart")
        kill_player(server, client, output)
        retained_on_death(server, results, "second_death_still_preserves_gear_before_disconnection")
        (output / "server-disconnect").touch()
    server.wait(f"{PLAYER} left the game")


def restart_and_respawn(args, server, results, report):
    with connect_client(args, server, report, "restart-respawn") as (client, output):
        retained_on_death(server, results, "pending_gear_survives_logout_and_server_restart")
        wait_signal(client, output, "dead")
        respawn(server, results, client, output, "respawn_after_full_restart_restores_without_duplication")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--legacy-jar", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--launch", required=True, type=Path)
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
        run_stage(args, legacy, report, "legacy", legacy_fixture)
        shutil.copytree(legacy / WORLD, port / WORLD)
        run_stage(args, port, report, "converted", lambda server, results: converted(args, server, results, report))
        run_stage(args, port, report, "disconnect", lambda server, results: disconnect_before_respawn(args, server, results, report))
        run_stage(args, port, report, "recovered", lambda server, results: restart_and_respawn(args, server, results, report))
        report["passed"] = True
    finally:
        path = directory / "legacy-soulbound-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Soulbound migration passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
