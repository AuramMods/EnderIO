#!/usr/bin/env python3
"""Compare the live packaged model inventories with the fixed source baseline."""

import argparse
import json
from pathlib import Path
import sys

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from packaged_registry_audit import BASELINE, MODEL_ONLY, baseline_resources, compare


def audit(path, expected_blocks, baseline_models, *, mekanism, language):
    report = json.loads(path.read_text())
    client = report["client"]
    loaded_mods = client["capture"]["loadedMods"]
    optional = set() if mekanism else {"enderio:basic_chemical_filter"}
    expected_items = baseline_models - MODEL_ONLY - optional
    startup = client["startup"]
    blocks = compare(expected_blocks, startup["blocks"])
    items = compare(expected_items, startup["items"])
    unexpected = sorted(optional & startup["items"].keys())
    baked_states = sum(startup["blocks"].values()) - len(startup["invisibleStates"])
    passed = (report["status"] == "pass" and ("mekanism" in loaded_mods) == mekanism
              and startup["language"] == language and not unexpected and not blocks["missing"] and not items["missing"])
    return {
        "passed": passed,
        "source": str(path),
        "language": startup["language"],
        "loadedMods": sorted(loaded_mods),
        "baselineBlocks": blocks,
        "baselineItems": items,
        "unexpectedItems": unexpected,
        "bakedBlockStates": baked_states,
        "invisibleBlockStates": len(startup["invisibleStates"]),
        "blockStatesWithoutStaticParticle": len(startup["missingBlockParticles"]),
        "itemsWithoutStaticParticle": startup["missingItemParticles"],
        "checks": client["checks"],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--core", required=True, type=Path)
    parser.add_argument("--mekanism", required=True, type=Path)
    args = parser.parse_args()
    baseline = json.loads(BASELINE.read_text())
    blocks = {f"enderio:{name}" for name in baseline["resource_ids"]["enderio/blockstates"]}
    _, items = baseline_resources(baseline["baseline_commit"])
    reports = {
        "core": audit(args.core, blocks, items, mekanism=False, language="en_us"),
        "mekanism": audit(args.mekanism, blocks, items, mekanism=True, language="fr_fr"),
    }
    print(json.dumps({"baselineCommit": baseline["baseline_commit"], "profiles": reports}, indent=2))
    return 0 if all(report["passed"] for report in reports.values()) else 1


if __name__ == "__main__":
    raise SystemExit(main())
