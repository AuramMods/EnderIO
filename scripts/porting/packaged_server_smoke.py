#!/usr/bin/env python3
"""Exercise a distributable Ender IO jar in a fresh, headless Forge installation.

Uses native console commands and real disk-backed server restarts. No development
classpath, injected test mod, client window, external service or real user world.
Supply the official Forge installer and the main reobfuscated (bundled) mod jar.
"""

import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import threading
import time


ROOT = Path(__file__).resolve().parents[2]
FORGE = "1.20.1-47.4.28"


class Server:
    def __init__(self, directory, java, stage):
        self.directory = directory
        self.lines = []
        self.condition = threading.Condition()
        self.sequence = 0
        self.log_path = directory / f"smoke-{stage}.log"
        self.log = self.log_path.open("w")
        self.process = subprocess.Popen(
            [java, "-Djava.awt.headless=true", "-Xms512M", "-Xmx3G",
             f"@libraries/net/minecraftforge/forge/{FORGE}/unix_args.txt", "nogui"],
            cwd=directory, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT, text=True, bufsize=1,
        )
        self.reader = threading.Thread(target=self.read, daemon=True)
        self.reader.start()

    def read(self):
        for line in self.process.stdout:
            self.log.write(line)
            self.log.flush()
            with self.condition:
                self.lines.append(line)
                self.condition.notify_all()

    def wait(self, text, start=0, timeout=90):
        deadline = time.monotonic() + timeout
        with self.condition:
            while True:
                if any(text in line for line in self.lines[start:]):
                    return
                if self.process.poll() is not None:
                    raise RuntimeError(f"Server exited {self.process.returncode}; see {self.log_path}")
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    raise RuntimeError(f"Timed out waiting for {text!r}; see {self.log_path}")
                self.condition.wait(min(remaining, 1))

    def command(self, command):
        self.sequence += 1
        marker = f"PORT_COMMAND_{self.sequence}"
        with self.condition:
            start = len(self.lines)
        self.process.stdin.write(command + "\nsay " + marker + "\n")
        self.process.stdin.flush()
        self.wait("[Server] " + marker, start, 20)
        return "".join(self.lines[start:])

    def assert_state(self, name, conditions, results, timeout=8):
        # Run the actual command first; only its observed result decides success.
        deadline = time.monotonic() + timeout
        marker = "PORT_ASSERT_" + name
        while True:
            output = self.command(f"execute {conditions} run say {marker}")
            if "[Server] " + marker in output:
                results.append({"assertion": name, "passed": True, "conditions": conditions})
                return
            if time.monotonic() >= deadline:
                results.append({"assertion": name, "passed": False, "conditions": conditions})
                for x in (0, 2, 4, 6):
                    self.command(f"data get block {x} 80 0")
                raise AssertionError(f"{name} failed; see {self.log_path}")
            time.sleep(0.2)

    def stop(self):
        if self.process.poll() is None:
            try:
                self.process.stdin.write("stop\n")
                self.process.stdin.flush()
                self.process.wait(timeout=30)
            except (BrokenPipeError, subprocess.TimeoutExpired):
                # Only the child created by this object can be terminated.
                self.process.terminate()
                try:
                    self.process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    self.process.kill()
                    self.process.wait()
        self.reader.join(timeout=5)
        self.log.close()
        return self.process.returncode


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def stored_state(x):
    return (f'if data block {x} 80 0 '
            '{Fluids:{Tanks:[{Index:0,Contents:{FluidName:"minecraft:water",Amount:1500,Tag:{PortSmoke:23}}}]},'
            'Items:{Size:4,Items:[{Slot:1,id:"minecraft:diamond",Count:7}]}}')


def bucket_state(x):
    return (f'if data block {x} 80 0 '
            '{Fluids:{Tanks:[{Index:0,Contents:{FluidName:"minecraft:water",Amount:1000}}]},'
            'Items:{Items:[{Slot:1,id:"minecraft:bucket",Count:1}]}} '
            f'unless data block {x} 80 0 Items.Items[{{Slot:0}}]')


def concrete_state(x):
    return (f'if data block {x} 80 0 '
            '{Items:{Items:[{Slot:3,id:"minecraft:white_concrete",Count:1}]}} '
            f'unless data block {x} 80 0 Items.Items[{{Slot:2}}] '
            f'unless data block {x} 80 0 Fluids.Tanks[0].Contents.FluidName')


def exercise(directory, java, results, stages):
    for stage in range(1, 4):
        server = Server(directory, java, stage)
        try:
            server.wait('For help, type "help"')
            server.command("forceload add 0 0 15 15")
            if stage == 1:
                for x, block in ((0, "fluid_tank"), (2, "pressurized_fluid_tank"),
                                 (4, "fluid_tank"), (6, "pressurized_fluid_tank")):
                    server.command(f"setblock {x} 80 0 enderio:{block}")
                for x in (0, 2):
                    server.command(f'data merge block {x} 80 0 '
                                   '{Fluids:{Tanks:[{Index:0,Contents:{FluidName:"minecraft:water",Amount:1500,Tag:{PortSmoke:23}}}]},'
                                   'Items:{Size:4,Items:[{Slot:1,id:"minecraft:diamond",Count:7}]}}')
                for x in (4, 6):
                    server.command(f'data merge block {x} 80 0 '
                                   '{Items:{Size:4,Items:[{Slot:0,id:"minecraft:water_bucket",Count:1}]}}')
            for x in (0, 2):
                server.assert_state(f"stored_tank_{x}_stage_{stage}", stored_state(x), results)
            for x in (4, 6):
                state = concrete_state(x) if stage == 3 else bucket_state(x)
                server.assert_state(f"processed_tank_{x}_stage_{stage}", state, results)
            if stage == 2:
                for x in (4, 6):
                    server.command(f'data merge block {x} 80 0 '
                                   '{Items:{Size:4,Items:[{Slot:2,id:"minecraft:white_concrete_powder",Count:1}]}}')
                    server.assert_state(f"recipe_after_restart_{x}", concrete_state(x), results)
                reload_start = len(server.lines)
                server.command("reload")
                server.wait("[minecraft/RecipeManager]: Loaded ", timeout=30, start=reload_start)
                for x in (0, 2):
                    server.assert_state(f"stored_tank_after_reload_{x}", stored_state(x), results)
            for x in (0, 2, 4, 6):
                server.command(f"data get block {x} 80 0")
            server.command("save-all flush")
        finally:
            code = server.stop()
            errors = [line.strip() for line in server.lines if "/ERROR]" in line or "/FATAL]" in line]
            stages.append({"stage": stage, "pid": server.process.pid, "exit": code,
                           "log": str(server.log_path), "errors": errors})
        if code != 0 or errors:
            raise RuntimeError(f"Packaged server reported an error in stage {stage}; see {server.log_path}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java", required=True)
    parser.add_argument("--installer", required=True, type=Path)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--name", required=True, help="New directory name under build/port-runtime")
    parser.add_argument("--port", type=int, default=25636)
    args = parser.parse_args()
    if not args.name or Path(args.name).name != args.name or args.name in (".", ".."):
        parser.error("--name must be one directory name")
    directory = ROOT / "build/port-runtime" / args.name
    # Never replace an existing installation or world.
    directory.mkdir(parents=True, exist_ok=False)
    installer, jar = args.installer.resolve(), args.jar.resolve()
    # Snapshot once: a concurrent build may replace the source jar while Forge installs.
    artifact = jar.read_bytes()
    report = {"forge": FORGE, "java": args.java, "jar": str(jar), "sha256": hashlib.sha256(artifact).hexdigest(),
              "installer_sha256": digest(installer), "assertions": [], "stages": [], "passed": False}
    try:
        with (directory / "installer.log").open("w") as log:
            subprocess.run([args.java, "-Djava.awt.headless=true", "-jar", str(installer),
                            "--installServer", str(directory)], cwd=directory,
                           stdout=log, stderr=subprocess.STDOUT, check=True)
        (directory / "mods").mkdir(exist_ok=True)
        (directory / "mods" / jar.name).write_bytes(artifact)
        (directory / "eula.txt").write_text("eula=true\n")
        flat = json.dumps({"layers": [{"block": "minecraft:bedrock", "height": 1},
                                      {"block": "minecraft:dirt", "height": 2},
                                      {"block": "minecraft:grass_block", "height": 1}],
                           "biome": "minecraft:plains"}, separators=(",", ":"))
        (directory / "server.properties").write_text(
            f"server-ip=127.0.0.1\nserver-port={args.port}\nonline-mode=true\n"
            f"level-name=port-smoke\nlevel-type=minecraft:flat\ngenerator-settings={flat}\n"
            "generate-structures=false\nview-distance=3\nsimulation-distance=3\nmax-players=2\nspawn-protection=0\n")
        options = {"narrator": "0", "onboardAccessibility": "false", "fullscreen": "false",
                   "pauseOnLostFocus": "false", "tutorialStep": "none"}
        for category in ("master", "music", "record", "weather", "block", "hostile", "neutral", "player", "ambient", "voice"):
            options["soundCategory_" + category] = "0.0"
        (directory / "options.txt").write_text("".join(f"{k}:{v}\n" for k, v in options.items()))
        exercise(directory, args.java, report["assertions"], report["stages"])
        report["passed"] = True
    finally:
        path = directory / "smoke-results.json"
        path.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Packaged server smoke: passed={report['passed']}; {len(report['assertions'])} assertions; {path}", flush=True)


if __name__ == "__main__":
    main()
