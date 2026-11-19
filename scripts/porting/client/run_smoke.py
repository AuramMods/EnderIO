#!/usr/bin/env python3
"""Run the prepared real client under native observation and collect its result."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[3]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--output", required=True, type=Path)
parser.add_argument("--timeout", default=180, type=int)
parser.add_argument("--launch", type=Path, default=ROOT / "build/port-client-harness/client-smoke-launch.json")
parser.add_argument("--scenario",
                    choices=("title", "singleplayer", "multiplayer", "travel", "carried-tanks", "extraction-upgrades", "legacy-lights",
                             "primitive-smelter", "stirling", "soul-binder", "soul-vials", "soul-engine", "soul-engine-fuels",
                             "powered-spawner", "spawner-rules", "obelisks", "entity-obelisks", "obelisk-carry", "farming-station",
                             "painting-machine", "legacy-painting", "painted-slabs", "legacy-aversion", "natural-spawns",
                             "facades", "facades-embeddium", "stained-facades", "stained-facades-embeddium", "stained-facades-oculus",
                             "stained-facades-oculus-shader",
                             "facade-dimensions", "facade-dimensions-embeddium",
                             "jade", "jade-client-only", "jade-absent",
                             "ultimine", "ultimine-absent",
                             "enderface",
                             "crafter", "crafter-jei", "crafter-carry",
                             "laserio-filters",
                             "inventory-sorter", "inventory-sorter-absent",
                             "enchanter", "enchanter-carry", "enchanter-reload", "legacy-enchanter", "legacy-auto-smelt",
                             "legacy-combat", "legacy-experience", "legacy-soulbound", "hang-glider", "levitation-staff",
                             "levitation-progression", "vat-controls", "redstone-filters", "conduit-filters", "transfer-filter-menus",
                             "stock-filter-menus", "filter-progression", "filter-recovery",
                             "filter-wrench-editor", "filter-wrench-operator",
                             "decorative-redstone", "steel-construction", "drain", "vacuums",
                             "facade-seams", "facade-seams-embeddium", "facade-seams-mixed", "facade-seams-mixed-embeddium",
                             "facade-ctm-core", "facade-ctm-athena", "facade-ctm-embeddium",
                             "wireless-charge", "curios", "solar-wired", "solar-carry",
                             "capacitor-bank", "capacitor-bank-carry"),
                    default="title")
parser.add_argument("--tank", choices=("standard", "pressurized"), default="standard")
parser.add_argument("--server", default="127.0.0.1:25641")
parser.add_argument("--multiplayer-phase", choices=("exercise", "verify"), default="exercise")
parser.add_argument("--data-maps", action="store_true")
parser.add_argument("--wrench-redstone", action="store_true")
parser.add_argument("--redstone-layout", type=Path)
args = parser.parse_args()
output = args.output.resolve()
output.mkdir(parents=True, exist_ok=False)
if args.redstone_layout:
    shutil.copyfile(args.redstone_layout, output / "layout.json")
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
if args.wrench_redstone:
    arguments.insert(arguments.index("-cp"), "-Denderio.clientqa.wrenchRedstone=true")
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
known_diagnostics = []
checks = result.get("checks", {})
shaders_disabled = (args.scenario == "stained-facades-oculus"
                    and " [Oculus/]: Shaders are disabled because enableShaders is set to false" in log)
shader_drew_frame = args.scenario == "stained-facades-oculus-shader" and checks.get("shader_pack_drew_facades-visible")
if checks.get("expected_oculus_installation") and (shaders_disabled or shader_drew_frame):
    # Embeddium emits this support-policy diagnostic when Oculus patches its renderer.
    notice = ("/ERROR] [Embeddium-MixinTaintDetector/]: Mod mixin into Embeddium internals detected. "
              "This instance is now tainted. The Embeddium team does not provide any guarantee of support "
              "for issues encountered while such mods are installed.")
    known_diagnostics = [line for line in errors if line.endswith(notice)]
    errors = [line for line in errors if not line.endswith(notice)]
passed = process.returncode == 0 and focus["status"] == "pass" and result["passed"] and not errors
report = {"event": "client_smoke_result", "status": "pass" if passed else "fail",
          "focus": focus, "client": result, "errors": errors, "knownDiagnostics": known_diagnostics}
(output / "summary.json").write_text(json.dumps(report, indent=2) + "\n")
print("QA_EVT " + json.dumps({"event": report["event"], "status": report["status"],
                             "summary": str(output / "summary.json")}))
raise SystemExit(0 if passed else 1)
