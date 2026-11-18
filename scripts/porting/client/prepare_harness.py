#!/usr/bin/env python3
"""Compile the isolated JVM window guard, real GLFW probe and macOS observer."""
import argparse
import json
import platform
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[3]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--java", required=True, type=Path)
parser.add_argument("--classpath-file", type=Path, default=ROOT / "enderio/build/moddev/clientLegacyClasspath.txt")
args = parser.parse_args()
build = ROOT / "build/port-client-harness"
classes = build / "classes"
classes.mkdir(parents=True, exist_ok=True)
source = Path(__file__).resolve().parent
native = "natives-macos-arm64.jar" if platform.machine() == "arm64" else "natives-macos.jar"
lwjgl = []
for line in args.classpath_file.read_text().splitlines():
    path = Path(line)
    if any(path.name == f"{module}-3.3.1.jar" or path.name == f"{module}-3.3.1-{native}"
           for module in ("lwjgl", "lwjgl-glfw", "lwjgl-opengl")):
        lwjgl.append(str(path))
classpath = ":".join(lwjgl)
exports = "java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED"
subprocess.run([str(args.java.parent / "javac"), "--add-exports", exports,
                "-source", "17", "-target", "17", "-cp", classpath, "-d", str(classes),
                str(source / "WindowGuard.java"), str(source / "HiddenWindowProbe.java")], check=True)
manifest = build / "MANIFEST.MF"
manifest.write_text("Manifest-Version: 1.0\nPremain-Class: com.enderio.port.testing.WindowGuard\n\n")
agent = build / "window-guard.jar"
subprocess.run([str(args.java.parent / "jar"), "--create", "--file", str(agent),
                "--manifest", str(manifest), "-C", str(classes), "."], check=True)
watch = build / "watch-focus"
subprocess.run(["swiftc", str(source / "WatchFocus.swift"), "-o", str(watch)], check=True)
config = {"java": str(args.java.resolve()), "agent": str(agent), "watch": str(watch),
          "probeClasspath": str(agent) + ":" + classpath,
          "vmArguments": ["-XstartOnFirstThread", "-Djava.awt.headless=true", "--add-exports", exports,
                          "-javaagent:" + str(agent)]}
(build / "harness.json").write_text(json.dumps(config, indent=2) + "\n")
print(build / "harness.json")
