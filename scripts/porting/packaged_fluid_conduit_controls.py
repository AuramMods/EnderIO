#!/usr/bin/env python3
"""Configure a stock mixed-tier fluid or chemical route through the packaged TCP menus."""

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_cc_tweaked import face, set_lever
from packaged_fluid_progression import CHUNKS, LANE, LEFT, RIGHT, supply as supply_fluid, tank_state
from packaged_laserio_editor import gas
from packaged_legacy_transfer_filters import wait_ticks
from packaged_server_smoke import ROOT, digest
from packaged_spawners import PLAYER, prepare_profile, run_stage


LEVER = "3280 80 7"
REDSTONE_INPUT = ('{type:"enderio:redstone",is_insert:0b,is_extract:1b,insert_channel:"green",'
                  'extract_channel:"blue",is_strong_output_signal:0b}')


def config(kind, pos, side, *, insert=False, insert_channel="green", extract_channel="green", mode="never_active", redstone="red", priority=0):
    extra = f",insert_priority:{priority}" if priority else ""
    return (f'if data block {pos} Connections[0][{{Side:"{side}",Config:{{type:"enderio:{kind}",'
            f'is_insert:{int(insert)}b,is_extract:{int(not insert)}b,insert_channel:"{insert_channel}",'
            f'extract_channel:"{extract_channel}",extract_redstone_channel:"{redstone}",'
            f'extract_redstone_control:"{mode}"{extra}}}}}]')


@dataclass(frozen=True)
class Route:
    kind: str
    batches: tuple[int, int, int, int, int]

    def inlet(self, mode, redstone="blue"):
        return config(self.kind, LEFT, "west", extract_channel="blue", mode=mode, redstone=redstone)

    def outlet(self, color="blue"):
        return config(self.kind, RIGHT, "east", insert=True, insert_channel=color, priority=2 if self.kind == "fluid" else 0)

    def balance(self, source, target):
        def endpoint(pos, amount):
            if self.kind == "fluid":
                return tank_state(pos, amount)
            if amount:
                return f"if data block {pos} GasTanks[{gas('oxygen', amount)}]"
            return f"unless data block {pos} GasTanks[0]"

        return f"{endpoint(LANE.source, source)} {endpoint(LANE.target, target)}"

    def supply(self, server, amount):
        if self.kind == "chemical":
            server.command(f"data modify block {LANE.source} GasTanks set value [{gas('oxygen', amount)}]")
        else:
            supply_fluid(server, amount)


def stable(server, results, name, conditions):
    wait_ticks(server, 25)
    server.assert_state(name, conditions, results)


def prepare(server, results, route):
    for command in ("gamerule doMobSpawning false", "gamerule spawnRadius 0", "gamerule doDaylightCycle false", "time set noon",
                    f"forceload add {CHUNKS}", "fill 3276 79 6 3283 79 12 minecraft:stone_bricks", "setworldspawn 3279 80 10"):
        server.command(command)
    for pos, mode in ((LANE.source, 4), (LANE.target, 1)):
        if route.kind == "chemical":
            server.command(f"setblock {pos} mekanism:ultimate_chemical_tank")
            sides = ",".join(f"side{i}:{mode}" for i in range(6))
            server.command(f"data modify block {pos} componentConfig.config2 set value {{{sides}}}")
        else:
            server.command(f"setblock {pos} enderio:pressurized_fluid_tank")
    set_lever(server, LEVER, False)
    for pos, tier in ((LEFT, "ender"), (RIGHT, "pressurized")):
        server.command(f'setblock {pos} enderio:conduit{{Conduits:["enderio:{tier}_{route.kind}","enderio:redstone"],Connections:[[],[]]}}')
    server.assert_state("both_bundled_routes_cross_the_chunk_boundary", " ".join(
        f'if data block {pos} Connections[{index}][{{Side:"{side}",Status:"connected_conduit"}}]'
        for pos, side in ((LEFT, "east"), (RIGHT, "west")) for index in (0, 1)), results)
    server.command(f'data modify block {RIGHT} Connections[1][{{Side:"north"}}].Config set value {REDSTONE_INPUT}')
    server.assert_state("the_stock_ports_begin_at_default_and_the_lever_feeds_blue",
                        f'{face(LEFT, "west")} {face(RIGHT, "east")} '
                        f'unless data block {LEFT} Connections[0][{{Side:"west"}}].Config '
                        f'unless data block {RIGHT} Connections[0][{{Side:"east"}}].Config '
                        f'if data block {RIGHT} Connections[1][{{Side:"north",Status:"connected_block",Config:{REDSTONE_INPUT}}}]', results)


def action(server, client, output, label, x):
    server.command(f"tp {PLAYER} {x + 0.5} 80 10.5 180 17")
    (output / f"server-{label}").touch()
    wait_signal(client, output, label)


def exercise(server, results, client, output, route):
    first, second, positive_toggle, inverted, pending = route.batches
    action(server, client, output, "outlet-blue", 3280)
    server.assert_state("outlet_widgets_set_blue_insertion", route.outlet(), results)

    action(server, client, output, "gate-red", 3279)
    route.supply(server, first)
    stable(server, results, "matching_transfer_channels_require_a_signal_on_the_selected_channel",
           f"{route.inlet('active_with_signal', 'red')} {route.outlet()} {route.balance(first, 0)}")
    set_lever(server, LEVER, True)
    stable(server, results, "a_blue_signal_does_not_open_the_red_gate",
           f"{route.inlet('active_with_signal', 'red')} {route.balance(first, 0)}")

    action(server, client, output, "gate-blue", 3279)
    server.assert_state("changing_the_gate_picker_to_blue_releases_the_full_batch",
                        f"{route.inlet('active_with_signal')} {route.outlet()} {route.balance(0, first)}", results)

    action(server, client, output, "outlet-green", 3280)
    route.supply(server, second)
    stable(server, results, "mismatched_transfer_channels_pause_even_while_blue_redstone_is_active",
           f"{route.inlet('active_with_signal')} {route.outlet('green')} {route.balance(second, first)}")
    action(server, client, output, "outlet-matched", 3280)
    transferred = first + second
    server.assert_state("restoring_both_blue_transfer_channels_releases_the_second_batch",
                        f"{route.outlet()} {route.balance(0, transferred)}", results)

    if positive_toggle:
        set_lever(server, LEVER, False)
        route.supply(server, positive_toggle)
        stable(server, results, "removing_power_closes_the_positive_gate", route.balance(positive_toggle, transferred))
        set_lever(server, LEVER, True)
        transferred += positive_toggle
        server.assert_state("restoring_power_releases_the_positive_batch_once", route.balance(0, transferred), results)

    action(server, client, output, "gate-inverted", 3279)
    route.supply(server, inverted)
    stable(server, results, "inverted_control_stops_flow_while_the_lever_is_on",
           f"{route.inlet('active_without_signal')} {route.outlet()} {route.balance(inverted, transferred)}")
    set_lever(server, LEVER, False)
    transferred += inverted
    server.assert_state("inverted_control_delivers_on_removal_of_the_signal", route.balance(0, transferred), results)
    set_lever(server, LEVER, True)
    route.supply(server, pending)
    stable(server, results, "the_world_saves_a_powered_gate_with_resources_waiting_at_source",
           f"{route.inlet('active_without_signal')} {route.outlet()} {route.balance(pending, transferred)}")


def reopen(server, results, client, output, route):
    pending = route.batches[-1]
    transferred = sum(route.batches[:-1])
    action(server, client, output, "saved-inlet", 3279)
    action(server, client, output, "saved-outlet", 3280)
    stable(server, results, "reopened_menus_and_powered_route_keep_the_original_settings_and_balances",
           f"{route.inlet('active_without_signal')} {route.outlet()} {route.balance(pending, transferred)}")
    set_lever(server, LEVER, False)
    stable(server, results, "the_reopened_line_delivers_the_saved_batch_once", route.balance(0, transferred + pending))


def run_client(args, server, report, phase, scenario, route):
    output = ROOT / "build/port-evidence" / f"{args.name}-{phase}"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", f"{route.kind}-conduit-controls", "--output", str(output), "--server", f"127.0.0.1:{args.port}",
               "--multiplayer-phase", phase, "--timeout", "195"]
    with (server.directory / f"client-{phase}.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, output, "connected")
            scenario(server, report["assertions"], client, output, route)
            (output / "server-done").touch()
            if client.wait(timeout=30) != 0:
                raise AssertionError(f"Conduit controls client failed; see {output}")
            summary = json.loads((output / "summary.json").read_text())
            frame = next(iter(summary["client"]["captures"].values()))
            if digest(Path(frame["loadedMods"]["enderio"]["file"])) != report["jarSha256"]:
                raise AssertionError("Conduit controls client and server loaded different Ender IO jars")
            if args.mekanism and digest(Path(frame["loadedMods"]["mekanism"]["file"])) != report["mekanismSha256"]:
                raise AssertionError("Conduit controls client and server loaded different Mekanism jars")
        finally:
            stop_client(client)
            summary = output / "summary.json"
            report["clients"][phase] = json.loads(summary.read_text()) if summary.exists() else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for path in ("java", "forge-server", "jar", "launch"):
        parser.add_argument(f"--{path}", required=True, type=Path)
    parser.add_argument("--mekanism", type=Path, help="Exercise chemical conduits with this Mekanism jar")
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be one directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    report = {"passed": False, "jarSha256": digest(directory / "mods/enderio.jar"), "assertions": [], "servers": [], "clients": {}}
    if args.mekanism:
        mekanism = directory / "mods/mekanism.jar"
        shutil.copyfile(args.mekanism, mekanism)
        report["mekanismSha256"] = digest(mekanism)
    route = Route("chemical", (11440, 21920, 3375, 32100, 40730)) if args.mekanism else Route("fluid", (1440, 1920, 0, 2100, 730))

    def first(server, results):
        prepare(server, results, route)
        run_client(args, server, report, "exercise", exercise, route)

    def restarted(server, results):
        server.command(f"forceload add {CHUNKS}")
        balance = route.balance(route.batches[-1], sum(route.batches[:-1]))
        server.assert_state("the_offline_save_reopens_with_original_gate_settings_and_balances",
                            f"{route.inlet('active_without_signal')} {route.outlet()} {balance} "
                            f"if block {LEVER} minecraft:lever[powered=true]", results)
        run_client(args, server, report, "verify", reopen, route)

    try:
        run_stage(args, directory, report, "exercise", first)
        run_stage(args, directory, report, "reopened", restarted)
        report["passed"] = True
    finally:
        (directory / f"{route.kind}-conduit-controls-results.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
