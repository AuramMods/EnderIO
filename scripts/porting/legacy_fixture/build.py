#!/usr/bin/env python3
"""Build the historical fixture or a native probe for both packaged releases."""

import argparse
import os
from pathlib import Path
import shutil
import subprocess
from zipfile import ZipFile


HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java", type=Path, required=True)
    parser.add_argument("--forge-server", type=Path, required=True)
    parser.add_argument("--legacy-jar", type=Path, required=True)
    parser.add_argument("--art", type=Path, required=True, help="AutoRenamingTool 2.0.17 all jar")
    probes = parser.add_mutually_exclusive_group()
    probes.add_argument("--storage-probe", action="store_true", help="Build the release-independent storage probe")
    probes.add_argument("--mekanism-probe", action="store_true", help="Build the release-independent Mekanism probe")
    probes.add_argument("--item-upgrade-probe", action="store_true", help="Build the release-independent item upgrade probe")
    parser.add_argument("--mod", type=Path, action="append", default=[], help="Add a Forge mod dependency for compilation")
    parser.add_argument("--output", type=Path, required=True, help="New directory for the fixture jar and build files")
    args = parser.parse_args()

    build = args.output.resolve()
    classes = build / "classes"
    (classes / "META-INF").mkdir(parents=True)
    metadata, source, filename = "mods.toml", "Fixture.java", "fixture.jar"
    if args.storage_probe:
        metadata, source, filename = "storage_probe.toml", "StorageProbe.java", "storage-probe.jar"
    elif args.mekanism_probe:
        metadata, source, filename = "mekanism_probe.toml", "MekanismProbe.java", "mekanism-probe.jar"
    elif args.item_upgrade_probe:
        metadata, source, filename = "item_upgrade_probe.toml", "ItemUpgradeProbe.java", "item-upgrade-probe.jar"
    shutil.copyfile(HERE / metadata, classes / "META-INF/mods.toml")
    artifacts = ROOT / "enderio/build/moddev/artifacts"
    forge = artifacts / "forge-1.20.1-47.4.28.jar"
    dependencies = sorted(path for path in (args.forge_server / "libraries").rglob("*.jar")
                          if "/minecraftforge/forge/" not in path.as_posix() and "/minecraft/server/" not in path.as_posix())
    with ZipFile(args.legacy_jar) as archive:
        for path in archive.namelist():
            if path.startswith("META-INF/jarjar/") and path.endswith(".jar"):
                jar = build / Path(path).name
                jar.write_bytes(archive.read(path))
                dependencies.append(jar)

    classpath = os.pathsep.join(map(str, [forge, args.legacy_jar, *dependencies, *args.mod]))
    subprocess.run([args.java.with_name("javac"), "--release", "17", "-proc:none", "-cp", classpath,
                    "-d", classes, HERE / source], check=True)
    named = build / "named.jar"
    subprocess.run([args.java.with_name("jar"), "--create", "--file", named, "-C", classes, "."], check=True)
    output = build / filename
    with (build / "remap.log").open("w") as log:
        subprocess.run([args.java, "-jar", args.art, "--input", named, "--output", output,
                        "--map", artifacts / "namedToIntermediate.tsrg", "--lib", forge, "--lib", args.legacy_jar],
                       stdout=log, stderr=subprocess.STDOUT, check=True)
    print(output)


if __name__ == "__main__":
    main()
