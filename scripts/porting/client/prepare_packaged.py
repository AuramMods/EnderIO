#!/usr/bin/env python3
"""Prepare an isolated official Forge client and distributable mod jars; never launch it."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[3]
MC = "1.20.1"
FORGE = "1.20.1-forge-47.4.10"


def download(url, target, sha1=None):
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.is_file() and sha1 and hashlib.sha1(target.read_bytes()).hexdigest() == sha1:
        return target
    temporary = target.with_name(target.name + ".download")
    subprocess.run(["curl", "--fail", "--silent", "--show-error", "--retry", "2",
                    url, "-o", str(temporary)], check=True)
    if sha1 and hashlib.sha1(temporary.read_bytes()).hexdigest() != sha1:
        raise ValueError(f"Downloaded content checksum mismatch: {url}")
    temporary.replace(target)
    return target


def allowed(rules):
    result = not rules
    for rule in rules:
        operating_system = rule.get("os", {})
        matches = all(
            re.search(value, {"name": "osx", "arch": platform.machine(), "version": platform.mac_ver()[0]}[key])
            for key, value in operating_system.items()
        ) and all(value == {"has_custom_resolution": True}.get(key, False)
                  for key, value in rule.get("features", {}).items())
        if matches:
            result = rule["action"] == "allow"
    return result


def arguments(entries, values):
    result = []
    for entry in entries:
        if isinstance(entry, dict):
            if not allowed(entry.get("rules", [])):
                continue
            entry = entry["value"]
        for argument in entry if isinstance(entry, list) else [entry]:
            result.append(re.sub(r"\$\{([^}]+)}", lambda m: str(values[m[1]]), argument))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--username", default="EnderIOQA", help="Offline player name for this isolated profile")
    parser.add_argument("--java", required=True, type=Path)
    parser.add_argument("--installer", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--qa-jar", required=True, type=Path)
    parser.add_argument("--mod", action="append", default=[], type=Path, help="Install an optional integration jar")
    parser.add_argument("--assets", required=True, type=Path)
    parser.add_argument("--install", action="store_true", help="Run the official installer, including on a fresh directory")
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9_-]+", args.name):
        parser.error("name must be a single directory name")
    directory = ROOT / "build/port-runtime" / args.name
    directory.mkdir(parents=True, exist_ok=True)
    # This launcher profile belongs solely to this disposable installation.
    profiles = directory / "launcher_profiles.json"
    if not profiles.exists():
        profiles.write_text(json.dumps({"profiles": {}, "settings": {}, "version": 3}) + "\n")
    if args.install:
        with (directory / "install-client.log").open("w") as log:
            subprocess.run([str(args.java.resolve()), "-Djava.awt.headless=true", "-jar",
                            str(args.installer.resolve()), "--installClient", str(directory)],
                           cwd=directory, stdout=log, stderr=subprocess.STDOUT, check=True)
    manifest = json.loads(download("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json",
                                   directory / "version-manifest.json").read_text())
    entry = next(version for version in manifest["versions"] if version["id"] == MC)
    vanilla = json.loads(download(entry["url"], directory / f"versions/{MC}/{MC}.json", entry["sha1"]).read_text())
    forge = json.loads((directory / f"versions/{FORGE}/{FORGE}.json").read_text())
    client = vanilla["downloads"]["client"]
    client_jar = download(client["url"], directory / f"versions/{MC}/{MC}.jar", client["sha1"])
    # Merge version inheritance by group/artifact/classifier; Forge overrides its libraries.
    libraries = {}
    for library in vanilla["libraries"] + forge["libraries"]:
        if not allowed(library.get("rules", [])):
            continue
        parts = library["name"].split(":")
        if len(parts) > 3 and parts[3].startswith("natives-macos"):
            target = "natives-macos-arm64" if platform.machine() == "arm64" else "natives-macos"
            if parts[3] != target:
                continue
        libraries[tuple(parts[:2] + parts[3:])] = library
    def fetch_library(library):
        artifact = library["downloads"]["artifact"]
        return download(artifact["url"], directory / "libraries" / artifact["path"], artifact["sha1"])
    with ThreadPoolExecutor(max_workers=8) as pool:
        classpath = list(pool.map(fetch_library, libraries.values()))
    assets = args.assets.resolve()
    index_info = vanilla["assetIndex"]
    index = json.loads(download(index_info["url"], assets / "indexes" / (index_info["id"] + ".json"), index_info["sha1"]).read_text())
    hashes = sorted({value["hash"] for value in index["objects"].values()})
    def fetch_asset(digest):
        return download(f"https://resources.download.minecraft.net/{digest[:2]}/{digest}",
                        assets / "objects" / digest[:2] / digest, digest)
    with ThreadPoolExecutor(max_workers=8) as pool:
        list(pool.map(fetch_asset, hashes))
    mods = directory / "mods"
    mods.mkdir(exist_ok=True)
    records = []
    artifacts = [(args.jar, "enderio.jar"), (args.qa_jar, "enderio-client-qa.jar")]
    artifacts.extend((mod, mod.name) for mod in args.mod)
    for source, name in artifacts:
        data = source.read_bytes()
        (mods / name).write_bytes(data)
        records.append({"source": str(source.resolve()), "installed": name,
                        "sha256": hashlib.sha256(data).hexdigest()})
    options = {"narrator": "0", "onboardAccessibility": "false", "fullscreen": "false",
               "pauseOnLostFocus": "false", "tutorialStep": "none", "maxFps": "60"}
    for category in ("master", "music", "record", "weather", "block", "hostile", "neutral", "player", "ambient", "voice"):
        options["soundCategory_" + category] = "0.0"
    (directory / "options.txt").write_text("".join(f"{key}:{value}\n" for key, value in options.items()))
    (directory / "eula.txt").write_text("eula=true\n")
    natives = directory / "natives"
    natives.mkdir(exist_ok=True)
    values = {"auth_player_name": args.username, "version_name": FORGE, "game_directory": directory,
              "assets_root": assets, "assets_index_name": index_info["id"],
              "auth_uuid": str(uuid.UUID(bytes=hashlib.md5(f"OfflinePlayer:{args.username}".encode()).digest(), version=3)),
              "auth_access_token": "0", "clientid": "", "auth_xuid": "", "user_type": "legacy",
              "version_type": "release", "resolution_width": "960", "resolution_height": "540",
              "natives_directory": natives, "launcher_name": "EnderIO-Packaged-QA", "launcher_version": "1",
              "library_directory": directory / "libraries", "classpath_separator": os.pathsep,
              "classpath": os.pathsep.join(map(str, [*classpath, client_jar]))}
    harness = json.loads((ROOT / "build/port-client-harness/harness.json").read_text())
    jvm = arguments(vanilla["arguments"]["jvm"] + forge["arguments"]["jvm"], values)
    # Forge loads its patched Minecraft jar itself. Its metadata ignores the selected
    # version's jar name; this launcher keeps the inherited vanilla jar's original name.
    # Exclude that unpatched parent too, or the module layer contains Minecraft twice.
    jvm = [argument + "," + client_jar.name if argument.startswith("-DignoreList=") else argument
           for argument in jvm]
    jvm += ["-Xmx3G", *harness["vmArguments"],
            "-Denderio.clientqa.requiredMods=endercore,enderio,enderio_modded_conduits"]
    game = arguments(vanilla["arguments"]["game"] + forge["arguments"]["game"], values)
    launch = {"java": str(args.java.resolve()), "directory": str(directory), "environment": {},
              "arguments": [*jvm, forge["mainClass"], *game]}
    (directory / "launch.json").write_text(json.dumps(launch, indent=2) + "\n")
    (directory / "installation.json").write_text(json.dumps({"mods": records, "assetObjects": len(hashes),
        "libraries": len(libraries), "vanillaClientSha1": client["sha1"], "forge": FORGE,
        "installerSha256": hashlib.sha256(args.installer.read_bytes()).hexdigest()}, indent=2) + "\n")
    print(directory / "launch.json")


if __name__ == "__main__":
    main()
