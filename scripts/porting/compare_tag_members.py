#!/usr/bin/env python3
"""Compare shipped Ender IO tag contributions with members bound by a packaged server."""

import argparse
from functools import cache
from io import BytesIO
import json
from pathlib import Path
import subprocess
import sys
import tarfile
from zipfile import ZipFile

sys.dont_write_bytecode = True
from packaged_registry_audit import BASELINE, ROOT


# The baseline generated both entries for a fixture. Capture gives the whitelist precedence.
STALE_ZOMBIE_TAGS = {("entity_type", f"enderio:soul_vial_{name}") for name in ("blacklist", "whitelist")}
EXTERNAL_WRENCH_TAGS = ("forge:tools/wrench", "forge:tools/wrenches")
UNAVAILABLE_VANILLA = {("item", "minecraft:wind_charge"), ("block", "minecraft:crafter")} | {
    ("block", f"minecraft:{wax}{oxidation}{block}")
    for wax in ("", "waxed_")
    for oxidation in ("", "exposed_", "weathered_", "oxidized_")
    for block in ("copper_bulb", "copper_door", "copper_trapdoor")
}


def read_tags(files):
    tags = {}
    for path, read in files:
        if not path.startswith("data/") or not path.endswith(".json"):
            continue
        _, domain, kind, *parts = path.split("/")
        if kind != "tags":
            continue
        registry, *name = parts
        tags[registry, f"{domain}:{'/'.join(name)[:-5]}"] = json.loads(read())
    return tags


def jar_tags(archive):
    return read_tags((name, lambda: archive.read(name)) for name in archive.namelist() if not name.endswith("/"))


def baseline_tags(commit):
    archive = subprocess.check_output(["git", "archive", commit], cwd=ROOT)
    with tarfile.open(fileobj=BytesIO(archive)) as tree:
        return read_tags((member.name.split("/resources/", 1)[1], lambda: tree.extractfile(member).read())
                         for member in tree if member.isfile() and (
                             "/src/main/resources/" in member.name or "/src/generated/resources/" in member.name))


def merge_tag_layers(*layers):
    merged = {}
    for tags in layers:
        for key, data in tags.items():
            values = merged.setdefault(key, [])
            if data.get("replace"):
                values.clear()
            values.extend(data["values"])
    return merged


def compare_members(enderio, merged, runtime):
    unresolved = set()

    @cache
    def resolve(key):
        return expand(key, merged[key])

    def expand(key, entries):
        registry, _ = key
        members = set()
        for entry in entries:
            member = entry if isinstance(entry, str) else entry["id"]
            required = isinstance(entry, str) or entry.get("required", True)
            if member.startswith("#"):
                reference = registry, member[1:]
                if reference in merged:
                    members.update(resolve(reference))
                elif required:
                    unresolved.add(f"{registry}{member}")
            else:
                members.add(member)
        return members

    tag_registries = {key: set(values) for key, values in runtime["tagRegistryIds"].items()}
    membership = {}
    for (registry, tag), data in sorted(enderio.items()):
        key = registry, tag
        expected = expand(key, data["values"])
        if key in STALE_ZOMBIE_TAGS:
            expected.discard("minecraft:zombie")
        actual = set(runtime["tags"].get(registry, {}).get(tag, []))
        unavailable = expected - tag_registries[registry]
        membership[f"{registry}#{tag}"] = {"matched": len(expected & actual),
                                          "missing": sorted(expected - actual - unavailable),
                                          "unavailable": sorted(unavailable),
                                          "unexpectedlyUnavailable": sorted(member for member in unavailable
                                                                            if (registry, member) not in UNAVAILABLE_VANILLA),
                                          "extra": sorted(actual - expected)}
    return membership, sorted(unresolved)


def wrench_bridge(runtime):
    tags = runtime["tags"]["item"]
    forge_wrenches = set().union(*(tags.get(key, []) for key in EXTERNAL_WRENCH_TAGS))
    own_wrench = "enderio:yeta_wrench"
    return {"missingImports": sorted(forge_wrenches - set(tags.get("c:tools/wrench", []))),
            "missingExports": [key for key in EXTERNAL_WRENCH_TAGS if own_wrench not in tags.get(key, [])]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("server-jar", "neoforge-jar", "snapshot", "output"):
        parser.add_argument(f"--{name}", type=Path, required=True)
    args = parser.parse_args()

    with ZipFile(args.server_jar) as wrapper:
        name = next(name for name in wrapper.namelist() if name.startswith("META-INF/versions/") and name.endswith(".jar"))
        with ZipFile(BytesIO(wrapper.read(name))) as vanilla:
            vanilla_tags = jar_tags(vanilla)
    with ZipFile(args.neoforge_jar) as neoforge:
        neoforge_tags = jar_tags(neoforge)
    enderio = baseline_tags(json.loads(BASELINE.read_text())["baseline_commit"])
    runtime = json.loads(args.snapshot.read_text())
    tags, unresolved = compare_members(enderio, merge_tag_layers(vanilla_tags, neoforge_tags, enderio), runtime)
    wrenches = wrench_bridge(runtime)
    missing = sum(len(details["missing"]) for details in tags.values())
    unavailable = sum(len(details["unexpectedlyUnavailable"]) for details in tags.values())
    report = {"tags": tags, "checked": len(tags), "missingMembers": missing, "unresolvedReferences": unresolved,
              "unexpectedlyUnavailableMembers": unavailable, "wrenchBridge": wrenches}
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(f"Compared {len(tags)} tags. Missing members {missing}. Unresolved references {len(unresolved)}.")
    if missing or unavailable or unresolved or any(wrenches.values()):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
