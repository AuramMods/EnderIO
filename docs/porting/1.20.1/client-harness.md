# Silent, non-activating client checks

This test-only harness runs an actual Forge 1.20.1 client on the current macOS host. It renders into a hidden GLFW window, captures the real framebuffer, and exits without global mouse/keyboard input. It does not test normal window focus behavior, and a title-screen pass does not establish gameplay, multiplayer or packaged-client compatibility.

## Mechanism and scope

`scripts/porting/client/WindowGuard.java` instruments the actual LWJGL GLFW entry points before they load. It forces hidden/unfocused creation, disables Cocoa menu-bar activation, blocks show/focus/attention requests, forces windowed mode and prevents cursor capture/warping. A transformer failure halts that JVM because Java otherwise ignores transformer exceptions and could load unguarded code. The agent uses Java 17's internal ASM solely in the isolated test launcher.

`HiddenWindowProbe.java` directly requests visible/focused/fullscreen/captured behavior and verifies the resulting native state over 100 rendered OpenGL frames, including framebuffer pixel reads. `WatchFocus.swift` starts its own child and independently observes macOS application-activation notifications, foreground PID and WindowServer visibility metadata. It only terminates its own child if the run violates those observations or exceeds the configured test deadline. It never restores focus or modifies another application.

The `clientSmoke` source set loads a separate QA mod. After the actual title screen finishes fading in, it checks narrator, all ten volume categories, native window/input state and all twelve enchanter/spawner baked block states. It captures `title-screen.png`, writes `result.json`, and stops the client. `run_smoke.py` combines that result with the native observer and ERROR/FATAL log lines. Inspect the image as well as the JSON.

The development smoke profile loads Ender IO and EnderCore without optional integrations. The separate packaged profile below loads the shipped main jar, its nested EnderCore/addon/GraphLib dependencies and a standalone remapped QA mod. The single-player and dedicated multiplayer tank scenarios below provide limited interaction/persistence evidence; other interactions and resource reload still need scenarios. The agent, QA mod, Swift observer and tests are excluded from distributable jars.

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

## Packaged single-player tanks

The `singleplayer` scenario creates a disposable flat survival world through native `WorldOpenFlows`, opens a tank using the normal block-use packet and moves a water bucket through the normal inventory-click packet. It verifies the actual menu, exact container counts and fluid synchronization. A real redstone picker scroll sends the menu update to the server; a server-side fixture then adds 500 mB. The client verifies both menu and block-entity fluid, captures the rendered world, closes/saves the integrated server and reopens that same disk world. It opens a fresh menu and verifies persisted fluid, mode and inventory.

After rebuilding and preparing the packaged installation with the current QA jar, run each variant separately:

```sh
python3 scripts/porting/client/run_smoke.py \
  --launch build/port-runtime/cycle-040-client/launch.json \
  --scenario singleplayer --tank standard --timeout 300 \
  --output build/port-evidence/singleplayer-standard-example
python3 scripts/porting/client/run_smoke.py \
  --launch build/port-runtime/cycle-040-client/launch.json \
  --scenario singleplayer --tank pressurized --timeout 300 \
  --output build/port-evidence/singleplayer-pressurized-example
```

Use unique output names; the output basename also names the owned world inside the isolated installation. Do not run these concurrently against the same installation. Preparation resets the quiet options before launches. No user worlds, OS input injection, visible window, client teleport command or development classpath is used. Test fixture setup places blocks and seeds the player's inventory on the actual server thread; it is not player-driven placement coverage.

Expected capacities are 16,000/32,000 mB. The bucket operation produces exactly one empty bucket and 1,000 mB, with no remaining water bucket. The later fixture addition produces 1,500 mB, visible to both the menu and renderer; `ACTIVE_WITH_SIGNAL`, that fluid amount and the single output bucket survive reopen. Fifteen checks and six actual framebuffer captures per variant are recorded in `result.json`, with independent native observation in `focus.json`.

[Cycle 040](cycle-040.md) verifies both variants. This is limited packaged gameplay and integrated-server persistence evidence. It does not test a dedicated TCP server, all tank operations, all machines, all rendering backends or full feature parity.

## Packaged dedicated multiplayer

`run_multiplayer.py` installs a fresh official Forge server, copies only the bundled Ender IO distributable into its `mods` directory, and starts it headlessly on loopback. It uses an isolated flat world and an offline local QA identity. The server has no QA mod or development classes. The separately installed, guarded client connects through native `ConnectScreen` and verifies that its connection is a real TCP socket with no integrated server.

After building the distributable and QA jar and preparing a quiet client installation, run:

```sh
python3 scripts/porting/client/run_multiplayer.py \
  --name cycle-041-multiplayer-fixed \
  --java /Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java \
  --installer build/port-runtime/forge-47.4.28-installer.jar \
  --jar enderio/build/libs/enderio-8.2.14.40-1.20.1+c3d60fb222.jar \
  --launch build/port-runtime/cycle-041-client/launch.json
```

Use a fresh `--name` for each run, current artifact paths, and an unused `--port` if the default 25641 is occupied. The runner performs the actual server launch rather than guessing whether a port is available. Do not run multiple clients against the same isolated installation concurrently.

The expected sequence is:

1. Connect to the real server. Console fixtures place a standard tank, pressurized tank and enchanter, seed one player water bucket and teleport the player into range.
2. Open the standard tank through the native block-use packet, shift-click the bucket and verify 1,000 mB, one empty output bucket and no remaining water bucket.
3. Scroll the real redstone widget. Independent server-console NBT assertions verify `active_with_signal`, fluid and inventory. A console fixture adds 500 mB; the client must receive both the menu and rendered block-entity state.
4. Disconnect and reconnect to the same server. A fresh menu must restore 1,500 mB, the selected redstone mode and exactly one empty bucket.
5. Stop/save the server, start a new server process on the same disk world and assert its saved NBT. Launch a new guarded client and verify the same state again through TCP and a fresh menu.

No OS input is injected. Each client run records native focus/visibility observations, quiet settings, actual framebuffer PNGs, checks and logs beneath `build/port-evidence/<name>-exercise` or `-verify`. The server's `multiplayer-results.json` combines client summaries, native-console assertions, exit statuses and client/server artifact hashes. A native disconnect screen is captured immediately as a failed scenario, including its actual reason in `failure.png`. Console setup and direct fixture updates are not player placement or complete machine gameplay coverage.
