#!/usr/bin/env python3
"""Verify glass facade seams and live transfers through the covered conduits."""

import argparse
import json
from pathlib import Path
import subprocess
import sys
import time

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_facades import ENERGY_CONDUIT, PLAYER, paint_data
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_spawners import prepare_profile, run_stage


WEST = "2703 80 9"
EAST = "2704 80 9"
DIFFERENT_WEST = "2703 80 19"
DIFFERENT_EAST = "2704 80 19"
VANILLA_CONTROL = "2703 80 24"
VANILLA_GLASS = "2704 80 24"
RED = "red_stained_glass"
BLUE = "blue_stained_glass"
FACADE = "enderio:transparent_conduit_facade"
INPUT, OUTPUT = "2702 80 9", "2705 80 9"
ITEM_SOURCE, ITEM_TARGET = "2701 80 9", "2706 80 9"
FLUID_SOURCE, FLUID_TARGET = "2702 80 8", "2705 80 8"
POWER_SOURCE, POWER_TARGET = "2702 79 9", "2705 79 9"
TRANSFER_TYPES = '["enderio:item","enderio:fluid","enderio:energy"]'
MIXED_BUNDLE = f'{{Conduits:{TRANSFER_TYPES},Connections:[[],[],[]]}}'
BATCHES = ((8, 750, 8_000), (64, 7_000, 96_000), (12, 1_250, 4_000), (20, 1_000, 16_000))


def cover(paint):
    return f'FacadeProvider:{{id:"{FACADE}",Count:1,{paint_data(paint)}}}'


def installed(pos, paint):
    return f"if block {pos} enderio:conduit if data block {pos} {{{cover(paint)},Conduits:[\"enderio:energy\"]}}"


def matching_pair(mixed):
    east = f"if block {EAST} minecraft:{RED}" if mixed else installed(EAST, RED)
    return f"{installed(WEST, RED)} {east}"


def prepare(server, results, mixed, transfers):
    server.command("forceload add 2688 0 2719 31")
    server.assert_state("both_facade_sections_are_loaded", f"if loaded {WEST} if loaded {EAST}", results)
    server.command("gamerule doMobSpawning false")
    server.command("gamerule doDaylightCycle false")
    server.command("gamerule spawnRadius 0")
    server.command("time set noon")
    server.command("fill 2696 79 5 2711 79 25 minecraft:stone")
    server.command("setworldspawn 2701 80 14")
    covers = [(WEST, RED), (DIFFERENT_WEST, RED), (DIFFERENT_EAST, BLUE), (VANILLA_CONTROL, RED)]
    if mixed:
        server.command(f"setblock {EAST} minecraft:{RED}")
    else:
        covers.append((EAST, RED))
    for pos, paint in covers:
        bundle = MIXED_BUNDLE if transfers and pos in (WEST, EAST) else ENERGY_CONDUIT
        server.command(f"setblock {pos} enderio:conduit{bundle}")
        server.command(f"data merge block {pos} {{{cover(paint)}}}")
    server.command(f"setblock {VANILLA_GLASS} minecraft:{RED}")
    server.assert_state("matching_glass_spans_two_chunks", matching_pair(mixed), results)
    server.assert_state("both_control_pairs_keep_their_original_paints",
                        f"{installed(DIFFERENT_WEST, RED)} {installed(DIFFERENT_EAST, BLUE)} "
                        f"{installed(VANILLA_CONTROL, RED)} if block {VANILLA_GLASS} minecraft:{RED}", results)
    if transfers:
        prepare_transfers(server, results)


def transfer_ports():
    for pos, item_face, insert in ((INPUT, "west", False), (OUTPUT, "east", True)):
        for index, (kind, side) in enumerate((("item", item_face), ("fluid", "north"))):
            item_flags = ',is_round_robin:0b,is_self_feed:0b,priority:0' if kind == "item" else ""
            config = (f'{{type:"enderio:{kind}",is_insert:{int(insert)}b,is_extract:{int(not insert)}b,'
                      'insert_channel:"green",extract_channel:"green",extract_redstone_channel:"red",'
                      f'extract_redstone_control:"always_active"{item_flags}}}')
            yield f'{pos} Connections[{index}][{{Side:"{side}",Status:"connected_block"}}]', config


def topology():
    checks = [f"if data block {pos} {{Conduits:{TRANSFER_TYPES}}} unless data block {pos} Conduits[3]"
              for pos in (INPUT, WEST, EAST, OUTPUT)]
    for left, right in ((INPUT, WEST), (WEST, EAST), (EAST, OUTPUT)):
        checks.extend(f'if data block {pos} Connections[{index}][{{Side:"{side}",Status:"connected_conduit"}}]'
                      for index in range(3) for pos, side in ((left, "east"), (right, "west")))
    checks.extend(f'if data block {pos} Connections[2][{{Side:"down",Status:"connected_block"}}]'
                  for pos in (INPUT, OUTPUT))
    checks.extend(f"if data block {path}.Config{config}" for path, config in transfer_ports())
    return " ".join(checks)


def prepare_transfers(server, results):
    for pos in (ITEM_SOURCE, ITEM_TARGET):
        server.command(f"setblock {pos} minecraft:barrel")
    for pos in (FLUID_SOURCE, FLUID_TARGET):
        server.command(f"setblock {pos} enderio:fluid_tank")
    for pos, block, mode, slots, slot in ((POWER_SOURCE, "stirling_generator", "push", 2, 1),
                                         (POWER_TARGET, "alloy_smelter", "pull", 5, 4)):
        server.command(f'setblock {pos} enderio:{block}{{IOConfig:{{Modes:{{up:"{mode}"}}}},'
                       f'Items:{{Size:{slots},Items:[{{Slot:{slot},id:"enderio:octadic_capacitor",Count:1}}]}}}}')
    for pos in (INPUT, OUTPUT):
        server.command(f"setblock {pos} enderio:conduit{MIXED_BUNDLE}")
    for path, config in transfer_ports():
        server.command(f"data modify block {path}.Config set value {config}")
    server.assert_state("three_routes_and_configured_ports_cross_the_chunk_boundary", topology(), results)


def item_at(pos, slot, batch, count):
    return f'if data block {pos} Items[{{Slot:{slot}b,id:"minecraft:paper",Count:{count}b,tag:{{FacadeBatch:{batch}}}}}]'


def water(amount):
    return f'{{FluidName:"minecraft:water",Amount:{amount},Tag:{{FacadeWater:266}}}}'


def supply_item(server, batch):
    server.command(f"item replace block {ITEM_SOURCE} container.0 with minecraft:paper{{FacadeBatch:{batch}}} {BATCHES[batch][0]}")


def supply_fluid_and_power(server, batch):
    _, fluid, energy = BATCHES[batch]
    server.command(f"data modify block {FLUID_SOURCE} Fluids.Tanks set value [{{Index:0,Contents:{water(fluid)}}}]")
    server.command(f"data merge block {POWER_SOURCE} {{EnergyStored:{energy}}}")


def exact_balance(batch):
    settled = BATCHES[:batch + 1]
    _, fluid, energy = map(sum, zip(*settled))
    slots = [item_at(ITEM_TARGET, index, index, count) for index, (count, _, _) in enumerate(settled)]
    return " ".join((*slots, f"unless data block {ITEM_SOURCE} Items[0] unless data block {ITEM_TARGET} Items[{len(slots)}]",
                     f"unless data block {FLUID_SOURCE} Fluids.Tanks[0].Contents.FluidName",
                     f"if data block {FLUID_TARGET} Fluids.Tanks[{{Index:0,Contents:{water(fluid)}}}]",
                     f"if data block {POWER_SOURCE} {{EnergyStored:0}} if data block {POWER_TARGET} {{EnergyStored:{energy}}}"))


def deliver(server, results, batch, phase):
    supply_item(server, batch)
    supply_fluid_and_power(server, batch)
    server.assert_state(f"{phase}_delivers_the_exact_batch_on_all_three_routes",
                        f"{exact_balance(batch)} {topology()}", results)


def repaint_in_flight(server, results):
    supply_item(server, 1)
    moving_items = f"{item_at(ITEM_SOURCE, 0, 1, 32)} {item_at(ITEM_TARGET, 1, 1, 32)}"
    server.assert_state("the_repaint_batch_starts_crossing_before_the_paint_change", moving_items, results)
    supply_fluid_and_power(server, 1)
    _, old_fluid, old_energy = BATCHES[0]
    moving = (f"{moving_items} if data block {FLUID_SOURCE} Fluids.Tanks[0].Contents.FluidName "
              f"unless data block {FLUID_TARGET} Fluids.Tanks[{{Index:0,Contents:{water(old_fluid)}}}] "
              f"unless data block {POWER_SOURCE} {{EnergyStored:0}} unless data block {POWER_TARGET} {{EnergyStored:{old_energy}}}")
    command = f"execute {moving} run data merge block {EAST} {{{cover(BLUE)}}}"
    deadline = time.monotonic() + 2
    while time.monotonic() < deadline:
        if "Modified block data" in server.command(command):
            results.append({"assertion": "vanilla_repaint_executes_while_all_three_transfers_are_in_flight",
                            "passed": True, "conditions": moving})
            break
    else:
        results.append({"assertion": "vanilla_repaint_executes_while_all_three_transfers_are_in_flight", "passed": False,
                        "conditions": moving})
        raise AssertionError(f"Repaint could not find all three transfers in flight. See {server.log_path}")
    server.assert_state("the_in_flight_batches_settle_exactly_after_repaint",
                        f"{installed(EAST, BLUE)} {exact_balance(1)} {topology()}", results)


def exercise_client(args, server, report):
    output = ROOT / "build/port-evidence" / f"{args.name}-client"
    scenario = "facade-seams-mixed" if args.mixed else "facade-seams"
    if args.embeddium:
        scenario += "-embeddium"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", scenario, "--output", str(output), "--server", f"127.0.0.1:{args.port}", "--timeout", "165"]
    with (server.directory / "facade-seam-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            server.command(f"tp {PLAYER} 2700.5 80 12.5 224 12")
            server.command(f"item replace entity {PLAYER} hotbar.8 with enderio:yeta_wrench")
            (output / "server-original").touch()
            wait_signal(client, output, "original")
            repaint = WEST if args.mixed else EAST
            if args.transfers:
                deliver(server, report["assertions"], 0, "before_repaint")
                repaint_in_flight(server, report["assertions"])
            else:
                server.command(f"data merge block {repaint} {{{cover(BLUE)}}}")
            server.assert_state("neighbor_repaint_preserves_the_conduit", installed(repaint, BLUE), report["assertions"])
            (output / "server-repainted").touch()
            wait_signal(client, output, "repainted")
            server.command(f"data merge block {repaint} {{{cover(RED)}}}")
            server.assert_state("restoring_the_original_paint_preserves_both_neighbors",
                                matching_pair(args.mixed), report["assertions"])
            if args.transfers:
                deliver(server, report["assertions"], 2, "after_restoring_paint")
            (output / "server-restored").touch()
            if client.wait(timeout=45) != 0:
                raise AssertionError(f"Facade seam client failed; see {output}")
            summary = json.loads((output / "summary.json").read_text())
            frame = summary["client"]["captures"]["matching-facades"]
            if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Client and server Ender IO jars differ")
        finally:
            stop_client(client)
            summary = output / "summary.json"
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--launch", required=True, type=Path)
    parser.add_argument("--embeddium", action="store_true")
    layout = parser.add_mutually_exclusive_group()
    layout.add_argument("--mixed", action="store_true", help="Join a facade to an ordinary glass block")
    layout.add_argument("--transfers", action="store_true", help="Transfer items, water and power through both facades")
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")

    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "forge": FORGE, "jarSha256": digest(directory / "mods/enderio.jar"),
              "assertions": [], "servers": []}
    try:
        def fresh(server, results):
            prepare(server, results, args.mixed, args.transfers)
            exercise_client(args, server, report)

        def reopened(server, results):
            server.assert_state("the_matching_join_reopens_exactly",
                                matching_pair(args.mixed), results)
            if args.transfers:
                server.assert_state("all_three_balances_and_port_settings_reopen_exactly",
                                    f"{exact_balance(2)} {topology()}", results)
                deliver(server, results, 3, "after_cold_restart")

        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", reopened)
        report["passed"] = True
    finally:
        path = directory / "facade-seams-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Facade seams passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
