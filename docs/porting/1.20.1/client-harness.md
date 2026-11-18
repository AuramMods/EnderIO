# Silent, non-activating client checks

This test-only harness runs an actual Forge 1.20.1 client on the current macOS host. It renders into a hidden GLFW window, captures the real framebuffer, and exits without global mouse/keyboard input. It does not test normal window focus behavior, and a title-screen pass does not establish gameplay, multiplayer or packaged-client compatibility.

## Mechanism and scope

`scripts/porting/client/WindowGuard.java` instruments the actual LWJGL GLFW entry points before they load. It forces hidden/unfocused creation, disables Cocoa menu-bar activation, blocks show/focus/attention requests, forces windowed mode and prevents cursor capture/warping. A transformer failure halts that JVM because Java otherwise ignores transformer exceptions and could load unguarded code. The agent uses Java 17's internal ASM solely in the isolated test launcher.

`HiddenWindowProbe.java` directly requests visible/focused/fullscreen/captured behavior and verifies the resulting native state over 100 rendered OpenGL frames, including framebuffer pixel reads. `WatchFocus.swift` starts its own child and independently observes macOS application-activation notifications, foreground PID and WindowServer visibility metadata. It only terminates its own child if the run violates those observations or exceeds the configured test deadline. It never restores focus or modifies another application.

The `clientSmoke` source set loads a separate QA mod. After the actual title screen finishes fading in, it checks narrator, all ten volume categories, native window/input state and all twelve enchanter/spawner baked block states. It captures `title-screen.png`, writes `result.json`, and stops the client. `run_smoke.py` combines that result with the native observer and ERROR/FATAL log lines. Inspect the image as well as the JSON.

The development smoke profile loads Ender IO and EnderCore without optional integrations. The separate packaged profile below loads the shipped main jar, its nested EnderCore/addon/GraphLib dependencies and a standalone remapped QA mod. Integrated server, actual interactions, resource reload and multiplayer still need dedicated scenarios. The agent, QA mod, Swift observer and tests are excluded from distributable jars.

## Commands

Run from the repository root on macOS, with Swift command-line tools and the configured Java 17 installation. First prepare actual launch dependencies without starting a game:

```sh
./gradlew :enderio:prepareClientRun --console=plain
python3 scripts/porting/client/prepare_harness.py \
  --java /Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java
```

After changing the guard, native dependencies or host, exercise the actual probe and inspect its result before using that setup for Minecraft:

```sh
python3 - <<'PY'
import json, subprocess
from pathlib import Path
root = Path.cwd()
c = json.loads((root / 'build/port-client-harness/harness.json').read_text())
report = root / 'build/port-evidence/client-probe-focus.json'
report.parent.mkdir(parents=True, exist_ok=True)
subprocess.run([c['watch'], str(report), '45', str(root), c['java'],
               *c['vmArguments'], '-cp', c['probeClasspath'],
               'com.enderio.port.testing.HiddenWindowProbe'], check=True)
PY
```

Export the prepared Java invocation, then run it through the observer:

```sh
./gradlew :enderio:exportClientSmokeLaunch --no-configuration-cache --console=plain
python3 scripts/porting/client/run_smoke.py \
  --output build/port-evidence/client-smoke-example
```

Use a new output directory for each run. Exporting does **not** execute `runClientSmoke`; it prepares classes, assets, arguments and isolated settings. The exporter includes each JVM argument provider exactly once and exports only explicitly configured environment entries. The runner inherits the current environment without recording it.

The isolated game directory is `enderio/run/client-smoke`. Export preparation resets master/category volumes to zero, narrator to `0`, accessibility onboarding to false, fullscreen to false and pause-on-lost-focus to false. Keep these settings for subsequent launches. Ordinary `runClient` is not the guarded workflow.

## Recorded evidence and limits

[Cycle 038](cycle-038.md) records the first verified probe and client runs. The final client rendered a 960×540 title screen with all twelve tested model states present, no ERROR/FATAL lines, narrator off and every sound category at zero. Native observation recorded zero activation/foreground/onscreen samples. OpenAL initializes normally; silent volume settings, not disabling the sound engine, provide muting.

The mechanisms were checked against the [GLFW window documentation](https://www.glfw.org/docs/3.3/window_guide.html), [context documentation](https://www.glfw.org/docs/3.3.3/context_guide.html) and actual Cocoa initialization code ([GLFW 3.4](https://github.com/glfw/glfw/blob/3.4/src/cocoa_init.m)). The loaded LWJGL 3.3.1 native reports GLFW 3.4.0; the actual probe result is the host-specific evidence. Other hosts/backends require their own direct observations.

## Packaged client

Build the normal distributable and the separate reobfuscated QA jar:

```sh
./gradlew :enderio:reobfClientSmokeJar build --continue --console=plain
```

`clientSmokeJar` contains only the test source-set output; `reobfClientSmokeJar` applies the configured named-to-SRG mappings and writes `enderio/build/test-libraries/enderio-client-qa.jar`. It does not alter the production component's publication variants or bundle itself in Ender IO.

Prepare the actual [Forge installer](https://github.com/MinecraftForge/Installer) and the freshly built main jar, then run:

```sh
python3 scripts/porting/client/prepare_packaged.py --install \
  --name cycle-039-client \
  --java /Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java \
  --installer build/port-runtime/forge-47.4.28-installer.jar \
  --jar enderio/build/libs/enderio-8.2.14.38-1.20.1+9cee1d53c9.jar \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar \
  --assets /Users/cyberpwn/.gradle/caches/neoformruntime/assets
python3 scripts/porting/client/run_smoke.py \
  --launch build/port-runtime/cycle-039-client/launch.json \
  --output build/port-evidence/packaged-client-example
```

Use the current artifact filename and a new owned installation name for a fresh profile. On an existing disposable installation, omit `--install` to refresh prepared arguments, mod snapshots and quiet settings. The installer runs headlessly; preparation never launches Minecraft. The separate `run_smoke.py` call always runs through the verified native observer and guard.

Preparation reads the official Mojang version manifest and installed Forge version metadata, resolves version inheritance, downloads/verifies actual SHA-1 content and selects the matching macOS native artifacts. The shared assets directory contains ordinary official Minecraft assets, not development classes. Runtime libraries live under the installed profile; no mapped development jar or Gradle class directory is included. Forge supplies its patched Minecraft jar. The inherited unpatched vanilla jar is excluded from Forge's module scan by its actual filename to prevent a duplicate Minecraft module.

The isolated `mods` directory receives only the main distributable and the separate QA jar during this no-optional-mod scenario. `installation.json` records both source hashes, installer hash, official client hash and resolved library/asset counts. The QA result records actual loaded mod IDs, versions and file paths (Forge's nested virtual jars report an empty file path here), and requires `endercore`, `enderio` and `enderio_modded_conduits`.

[Cycle 039](cycle-039.md) verifies this installed profile's actual title-screen renderer and model loading, with zero observed native activation/visibility events and no ERROR/FATAL log lines. It does not establish gameplay or complete client parity. Use a disposable offline identity for local tests; Realms authentication is outside this harness.
