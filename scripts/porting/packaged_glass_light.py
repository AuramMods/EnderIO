#!/usr/bin/env python3
"""Measure glass lighting in the packaged Forge server before and after world reload."""

import argparse
import json
from pathlib import Path
import sys

sys.dont_write_bytecode = True
from packaged_server_smoke import ROOT, digest
from packaged_spawners import WORLD, prepare_profile, run_stage

SHAFTS = (
    (2754, "clear_glass_p_blue", "clear_glass_dp_blue", "clear_glass_ep_blue"),
    (2786, "fused_quartz_nm", "fused_quartz_dnm", "fused_quartz_enm"),
)
FORCELOAD = "2753 0 2787 15"


def install_predicates(directory):
    pack = directory / WORLD / "datapacks/glass_light"
    predicates = pack / "data/glass_light/predicates"
    predicates.mkdir(parents=True)
    (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 15, "description": "Glass light fixture"}}))
    for name, light in (("zero", 0), ("fourteen", 14), ("fifteen", 15), ("night", {"max": 7})):
        (predicates / f"{name}.json").write_text(json.dumps({
            "condition": "minecraft:location_check", "predicate": {"light": {"light": light}},
        }))


def set_glass(server, x, block):
    server.command(f"setblock {x} 83 7 enderio:{block}")


def light_at(x, y, name):
    return f"positioned {x} {y} 7 if predicate glass_light:{name}"


def verify_saved_layout(server, results, stage):
    clear_x, _, dark_clear, _ = SHAFTS[0]
    quartz_x, _, _, emitting_quartz = SHAFTS[1]
    server.assert_state(f"{stage}_glass_variants_load",
                        f"if block {clear_x} 83 7 enderio:{dark_clear} "
                        f"if block {quartz_x} 83 7 enderio:{emitting_quartz}", results)
    server.command("time set midnight")
    server.assert_state(f"{stage}_light_levels",
                        f"{light_at(clear_x, 82, 'zero')} {light_at(quartz_x, 83, 'fifteen')} "
                        f"{light_at(quartz_x, 82, 'fourteen')}", results)


def exercise(server, results):
    server.command(f"forceload add {FORCELOAD}")
    server.assert_state("shaft_chunks_loaded", "if loaded 2754 83 7 if loaded 2786 83 7", results)
    server.command("gamerule doDaylightCycle false")
    server.command("gamerule doMobSpawning false")
    server.command("weather clear 100000")
    for x, plain, _, _ in SHAFTS:
        server.command(f"fill {x - 1} 79 6 {x + 1} 85 8 minecraft:stone")
        server.command(f"fill {x} 80 7 {x} 85 7 minecraft:air")
        set_glass(server, x, plain)

    server.command("time set noon")
    server.assert_state("both_clear_shafts_admit_daylight", " ".join(light_at(x, 82, "fifteen") for x, *_ in SHAFTS), results)
    for x, _, dark, _ in SHAFTS:
        set_glass(server, x, dark)
    server.assert_state("both_dark_shafts_block_daylight", " ".join(light_at(x, 82, "zero") for x, *_ in SHAFTS), results)

    server.command("time set midnight")
    for x, _, _, emitting in SHAFTS:
        set_glass(server, x, emitting)
    server.assert_state("both_enlightened_shafts_emit_and_spread_light",
                        " ".join(f"{light_at(x, 83, 'fifteen')} {light_at(x, 82, 'fourteen')}" for x, *_ in SHAFTS), results)
    for x, plain, _, _ in SHAFTS:
        set_glass(server, x, plain)
    server.assert_state("light_fades_after_switching_back_to_plain_glass",
                        " ".join(light_at(x, 82, "night") for x, *_ in SHAFTS), results)

    clear_x, _, dark_clear, _ = SHAFTS[0]
    quartz_x, _, _, emitting_quartz = SHAFTS[1]
    set_glass(server, clear_x, dark_clear)
    set_glass(server, quartz_x, emitting_quartz)
    server.command(f"forceload remove {FORCELOAD}")
    server.assert_state("both_shaft_chunks_evict", "unless loaded 2754 83 7 unless loaded 2786 83 7", results, timeout=25)
    server.command(f"forceload add {FORCELOAD}")
    verify_saved_layout(server, results, "chunk_reload")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--forge-server", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--port", type=int, default=26120)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")

    directory = ROOT / "build/port-runtime" / args.name
    prepare_profile(args, directory, args.jar)
    install_predicates(directory)
    report = {"passed": False, "jarSha256": digest(directory / "mods/enderio.jar"), "assertions": [], "servers": []}
    try:
        run_stage(args, directory, report, "fresh", exercise)
        run_stage(args, directory, report, "reopened", lambda server, results:
                  verify_saved_layout(server, results, "restart"))
        report["passed"] = True
    finally:
        result = directory / "glass-light-results.json"
        result.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Glass lighting passed={report['passed']}, {len(report['assertions'])} assertions, {result}", flush=True)


if __name__ == "__main__":
    main()
