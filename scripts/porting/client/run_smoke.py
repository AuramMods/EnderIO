#!/usr/bin/env python3
"""Run the prepared real client under native observation and collect its result."""
import argparse
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[3]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--output", required=True, type=Path)
parser.add_argument("--timeout", default=180, type=int)
parser.add_argument("--launch", type=Path, default=ROOT / "build/port-client-harness/client-smoke-launch.json")
parser.add_argument("--scenario",
                    choices=("title", "singleplayer", "multiplayer", "travel", "carried-tanks", "extraction-upgrades", "legacy-lights",
                             "primitive-smelter", "stirling", "soul-binder", "soul-vials", "soul-engine", "soul-engine-fuels",
                             "powered-spawner", "spawner-rules", "obelisks", "entity-obelisks", "legacy-aversion", "natural-spawns",
                             "wireless-charge", "curios", "solar-wired", "solar-carry",
                             "capacitor-bank", "capacitor-bank-carry"),
                    default="title")
parser.add_argument("--tank", choices=("standard", "pressurized"), default="standard")
parser.add_argument("--server", default="127.0.0.1:25641")
parser.add_argument("--multiplayer-phase", choices=("exercise", "verify"), default="exercise")
parser.add_argument("--data-maps", action="store_true")
args = parser.parse_args()
output = args.output.resolve()
output.mkdir(parents=True, exist_ok=False)
harness = json.loads((ROOT / "build/port-client-harness/harness.json").read_text())
launch = json.loads(args.launch.read_text())
arguments = launch["arguments"]
arguments.insert(arguments.index("-cp"), "-Denderio.clientqa.output=" + str(output))
arguments.insert(arguments.index("-cp"), "-Denderio.clientqa.scenario=" + args.scenario)
arguments.insert(arguments.index("-cp"), "-Denderio.clientqa.worldName=" + output.name)
arguments.insert(arguments.index("-cp"), "-Denderio.clientqa.pressurized=" + str(args.tank == "pressurized").lower())
arguments.insert(arguments.index("-cp"), "-Denderio.clientqa.server=" + args.server)
arguments.insert(arguments.index("-cp"), "-Denderio.clientqa.multiplayerPhase=" + args.multiplayer_phase)
if args.data_maps:
    arguments.insert(arguments.index("-cp"), "-Denderio.clientqa.dataMaps=true")
environment = os.environ.copy()
environment.update(launch["environment"])
command = [harness["watch"], str(output / "focus.json"), str(args.timeout),
           launch["directory"], launch["java"], *arguments]
process = subprocess.run(command, env=environment)
focus = json.loads((output / "focus.json").read_text())
result_path = output / "result.json"
result = json.loads(result_path.read_text()) if result_path.exists() else {"passed": False, "missingResult": True}
log = (output / "focus.json.client.log").read_text()
errors = [line for line in log.splitlines() if "/ERROR]" in line or "/FATAL]" in line]
passed = process.returncode == 0 and focus["status"] == "pass" and result["passed"] and not errors
report = {"event": "client_smoke_result", "status": "pass" if passed else "fail",
          "focus": focus, "client": result, "errors": errors}
(output / "summary.json").write_text(json.dumps(report, indent=2) + "\n")
print("QA_EVT " + json.dumps({"event": report["event"], "status": report["status"],
                             "summary": str(output / "summary.json")}))
raise SystemExit(0 if passed else 1)
