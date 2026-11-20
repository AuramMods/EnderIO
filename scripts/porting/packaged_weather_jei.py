#!/usr/bin/env python3
"""Use stock Weather Obelisk JEI transfers and run all three saved jobs."""

import argparse
from dataclasses import dataclass
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
from client.run_multiplayer import stop_client, wait_signal
from packaged_obelisks import CLEAR, RAIN, THUNDER, fluid_contents, task_state, write_predicates
from packaged_server_smoke import FORGE, ROOT, digest
from packaged_solar_charging import check
from packaged_spawners import PLAYER, WORLD, prepare_profile, run_stage
from saved_world import read_data_file


ROCKET = "minecraft:firework_rocket"
ROCKET_TAG = "{PortWeather216:216,Fireworks:{Flight:1b}}"
PAPER_TAG = "{KeepWeather216:1b}"
BLOCK = "enderio:weather_obelisk"


@dataclass(frozen=True)
class Site:
    name: str
    previous_weather: str
    fluid: str
    amount: int
    x: int

    @property
    def position(self):
        return f"{self.x} 80 8"

    @property
    def roof(self):
        return f"{self.x} 83 8"

    @property
    def firework(self):
        return f"@e[type=minecraft:firework_rocket,x={self.x + 0.5},y=81,z=8.5,distance=..8]"

    def fluid_data(self, amount, *, fluid=None):
        return fluid_contents(fluid or self.fluid, amount, f",Tag:{{PortWeather216:{self.x}}}")

    def tank(self, amount, *, fluid=None):
        return f"if data block {self.position} {{{self.fluid_data(amount, fluid=fluid)}}}"

    def queued(self):
        return (f"{self.tank(self.amount)} {task_state(self.name, 0, pos=self.position)} "
                f'if data block {self.position} Items.Items[{{Slot:0,id:"{ROCKET}",Count:1,tag:{ROCKET_TAG}}}] '
                f"if block {self.roof} minecraft:stone")

    def complete(self):
        return (f"{self.tank(self.amount - 1000)} unless data block {self.position} Items.Items[0] "
                f"unless data block {self.position} Task")


SITES = (
    Site("rain", "clear", RAIN, 1237, 4674),
    Site("lightning", "rain", THUNDER, 1321, 4682),
    Site("clear", "lightning", CLEAR, 1459, 4690),
)


def stock_inputs(server, site, amount, *, fluid=None):
    server.command(f"data merge block {site.position} {{{site.fluid_data(amount, fluid=fluid)}}}")


def setup(server, results):
    for command in ("gamerule doMobSpawning false", "gamerule spawnRadius 0", "gamerule doDaylightCycle false",
                    "time set noon", "forceload add 4672 0 4703 15", "fill 4670 79 5 4696 79 15 minecraft:stone",
                    "setworldspawn 4674 80 11", "weather clear 30000"):
        server.command(command)
    for site in SITES:
        server.command(f"setblock {site.roof} minecraft:stone")
        server.command(f"setblock {site.position} {BLOCK}[facing=south]")
        stock_inputs(server, site, 999 if site.name == "rain" else site.amount)
    check("server_runs_with_only_the_core_jar", ["enderio.jar"], sorted(p.name for p in (server.directory / "mods").glob("*.jar")), results)


def player_rockets(count):
    return f'if data entity {PLAYER} Inventory[{{Slot:0b,id:"{ROCKET}",Count:{count}b,tag:{ROCKET_TAG}}}]'


def check_rejections(server, results, client, output):
    rain = SITES[0]
    empty_slot = f"unless data block {rain.position} Items.Items[0]"
    wait_signal(client, output, "short-no-rocket")
    server.assert_state("initial_999_mb_and_rocket_slot_are_unchanged",
                        f'{rain.tank(999)} {empty_slot} unless data entity {PLAYER} Inventory[{{id:"{ROCKET}"}}]', results)
    server.command(f"item replace entity {PLAYER} hotbar.0 with {ROCKET}{ROCKET_TAG} 4")
    server.command(f"item replace entity {PLAYER} hotbar.7 with minecraft:paper{PAPER_TAG}")
    stock_inputs(server, rain, 1237, fluid="minecraft:water")
    (output / "server-wrong-fluid").touch()
    wait_signal(client, output, "wrong-fluid")
    server.assert_state("wrong_fluid_does_not_move_any_rockets",
                        f'{rain.tank(1237, fluid="minecraft:water")} {empty_slot} {player_rockets(4)}', results)
    stock_inputs(server, rain, 999)
    (output / "server-short-fluid").touch()
    wait_signal(client, output, "short-fluid")
    server.assert_state("one_millibucket_short_does_not_move_any_rockets",
                        f"{rain.tank(999)} {empty_slot} {player_rockets(4)}", results)
    stock_inputs(server, rain, rain.amount)


def exercise(args, server, results, report):
    evidence = ROOT / "build/port-evidence" / f"{args.name}-client"
    command = [sys.executable, str(ROOT / "scripts/porting/client/run_smoke.py"), "--launch", str(args.launch.resolve()),
               "--scenario", "weather-jei", "--server", f"127.0.0.1:{args.port}", "--output", str(evidence), "--timeout", "185"]
    summary = evidence / "summary.json"
    with (server.directory / "weather-jei-client.log").open("w") as log:
        client = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            wait_signal(client, evidence, "connected")
            for index, site in enumerate(SITES):
                server.command(f"tp {PLAYER} {site.x + 0.5} 80 10.5 180 18")
                (evidence / f"server-{site.name}").touch()
                if index == 0:
                    check_rejections(server, results, client, evidence)
                wait_signal(client, evidence, f"{site.name}-transferred")
                server.assert_state(f"{site.name}_jei_moves_one_tagged_rocket_and_queues_the_exact_600_tick_job",
                                    f"{site.queued()} {player_rockets(3 - index)} if predicate port_qa:clear", results)
            (evidence / "server-finish").touch()
            if client.wait(timeout=35) != 0:
                raise AssertionError(f"Weather JEI client failed; see {evidence}")
            loaded = json.loads(summary.read_text())["client"]["captures"]["rain-weather-jei"]["loadedMods"]
            check("client_and_server_use_the_same_production_artifact", report["jarSha256"],
                  digest(Path(loaded["enderio"]["file"])), results)
            check("client_uses_the_pinned_jei_artifact", report["jeiSha256"], digest(Path(loaded["jei"]["file"])), results)
        finally:
            stop_client(client)
            report["client"] = json.loads(summary.read_text()) if summary.exists() else None


def restored_jobs(server, results):
    for site in SITES:
        server.assert_state(f"{site.name}_queued_job_and_tagged_inputs_survive_restart", site.queued(), results)
    for site in SITES:
        server.assert_state(f"{site.name}_starts_with_the_previous_weather_and_zero_progress",
                            f"{site.queued()} if predicate port_qa:{site.previous_weather}", results)
        server.command(f"setblock {site.roof} minecraft:air")
        server.assert_state(f"{site.name}_launches_a_real_firework_and_spends_one_rocket_and_1000_mb",
                            f"{site.complete()} if entity {site.firework}", results, timeout=65)
        server.assert_state(f"{site.name}_applies_its_published_weather", f"{site.complete()} if predicate port_qa:{site.name}", results, timeout=12)


def verified(server, results):
    for site in SITES:
        server.assert_state(f"{site.name}_exact_tagged_remainder_survives_the_second_restart", site.complete(), results)
    server.assert_state("the_final_clear_job_survives_the_second_restart", "if predicate port_qa:clear", results)
    (player,) = (server.directory / WORLD / "playerdata").glob("*.dat")
    contents = sorted(read_data_file(player)["Inventory"], key=lambda stack: stack["Slot"])
    expected = [(0, ROCKET, 1, {"PortWeather216": 216, "Fireworks": {"Flight": 1}}),
                (7, "minecraft:paper", 1, {"KeepWeather216": 1})]
    observed = [(s["Slot"], s["id"], s["Count"], s.get("tag", {})) for s in contents]
    check("only_the_unused_original_rocket_and_paper_remain_in_the_saved_inventory", expected, observed, results)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    for key in ("java", "forge-server", "jar", "jei", "launch"):
        parser.add_argument(f"--{key}", type=Path, required=True)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    write_predicates(directory)
    report = {"passed": False, "forge": FORGE, "assertions": [], "servers": [],
              "jarSha256": digest(directory / "mods/enderio.jar"), "jeiSha256": digest(args.jei)}

    def fresh(server, results):
        setup(server, results)
        exercise(args, server, results, report)

    try:
        run_stage(args, directory, report, "fresh", fresh)
        run_stage(args, directory, report, "reopened", restored_jobs)
        run_stage(args, directory, report, "verified", verified)
        report["passed"] = True
    finally:
        path = directory / "weather-jei-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Weather JEI passed={report['passed']}, {len(report['assertions'])} assertions, {path}", flush=True)


if __name__ == "__main__":
    main()
