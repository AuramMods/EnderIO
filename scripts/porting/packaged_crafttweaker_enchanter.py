#!/usr/bin/env python3
"""Verify CraftTweaker Enchanter recipes through the guarded client and real XP."""

import argparse
import json
from pathlib import Path
import shutil
import sys
import time

sys.dont_write_bytecode = True
from packaged_crafttweaker import FIXTURES, check_script
from packaged_crafttweaker_stages import COMPANIONS
from packaged_enchanter import FRESH, INPUTS, SAVED, exercise, prepare, ready, spent
from packaged_server_smoke import ROOT, digest
from packaged_spawners import prepare_profile, run_stage


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--port", required=True, type=int)
    parser.add_argument("--java", required=True)
    for key in ("forge-server", "jar", "mod-dir", "launch"):
        parser.add_argument(f"--{key}", type=Path, required=True)
    args = parser.parse_args()
    if Path(args.name).name != args.name or args.name in ("", ".", ".."):
        parser.error("name must be a directory name")
    directory = ROOT / "build/port-runtime" / args.name
    directory.parent.mkdir(parents=True, exist_ok=True)
    prepare_profile(args, directory, args.jar)
    for name in COMPANIONS:
        shutil.copyfile(args.mod_dir / name, directory / "mods" / name)
    script = directory / "scripts/enchanter.zs"
    script.parent.mkdir()
    source = (FIXTURES / script.name).read_text()
    script.write_text(source)
    report = {"passed": False, "assertions": [], "servers": [], "clients": {},
              "jarSha256": digest(directory / "mods/enderio.jar")}

    def added(server, results):
        check_script(server, "PORT_CT_ENCHANTER_CONFIGURED")
        prepare(server, results)
        exercise(args, server, results, report, restarted=False)

    def replaced(server, results):
        check_script(server, "PORT_CT_ENCHANTER_CONFIGURED")
        server.assert_state("script_replacement_builds_the_expected_preview_after_restart",
                            f"{ready(SAVED)} {spent(FRESH)}", results)
        exercise(args, server, results, report, restarted=True)

    def removed(server, results):
        check_script(server, "PORT_CT_ENCHANTER_CONFIGURED")
        server.command("forceload add 3440 0 3455 15")
        server.assert_state("enchanter_recipe_removal_chunk_loaded", f"if loaded {FRESH}", results)
        server.command(f"setblock {FRESH} minecraft:air")
        server.command(f"setblock {FRESH} enderio:enchanter")
        server.command(f"data merge block {FRESH} {{Items:{{Items:[{INPUTS}]}}}}")
        time.sleep(1)
        server.assert_state("removal_leaves_book_catalyst_and_lapis_with_no_result",
                            f"if data block {FRESH} {{Items:{{Items:[{INPUTS}]}}}} "
                            f"unless data block {FRESH} Items.Items[{{Slot:3}}]", results)

    try:
        run_stage(args, directory, report, "added", added)
        script.write_text(source.replace("<enchantment:minecraft:sharpness>", "<enchantment:minecraft:smite>") + """
enchanter.replaceRecipe("crafttweaker:qa_sharpness", <tag:items:forge:gems/quartz> * 12,
    <enchantment:minecraft:sharpness>, 1);
""")
        run_stage(args, directory, report, "replaced", replaced)
        script.write_text(source + '\nenchanter.removeByName("crafttweaker:qa_sharpness");\n')
        run_stage(args, directory, report, "removed", removed)
        report["passed"] = True
    finally:
        path = directory / "crafttweaker-enchanter-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"CraftTweaker Enchanter passed={report['passed']}, {path}", flush=True)


if __name__ == "__main__":
    main()
