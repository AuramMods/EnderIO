# Cycle 098 — Restore the Enderface remote preview

## Scope

Connect the shipped Enderface screen to the Staff of Travelling. Use a packaged Forge client over TCP to open a vanilla chest and an Ender IO fluid tank from outside normal reach. Exercise two visible targets five blocks deep into the preview on opposite sides. Remove the Enderface with the tank open, then replace it and reopen the saved world.

## Changes

- Open the preview from the staff's client-side travel action. The screen lives behind a client-only bridge so the common travel target can run on a dedicated server. Validate the enabled experiment, loaded block and existing Enderface range when using that action.
- Cast through the full preview volume and choose the nearest hit. Use the matching world coordinates for the block interaction. Only targets within the Enderface's reach can be selected.
- Build a quiet packaged regression that executes the real staff action, preview drag, zoom and right-click, and inventory shift-click. The disposable server independently checks exact remote storage and player inventory.

## Commands

```sh
./gradlew :enderio:assemble --console=plain > build/cycle-098-baseline-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-098-qa-baseline.log 2>&1
./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-098-build-final.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.97-1.20.1+4df36ce62d.jar
port_qa=enderio/build/test-libraries/enderio-client-qa.jar
port_forge=build/port-runtime/cycle-047-legacy-discover
port_client=build/port-runtime/cycle-098-fixed-client
port_prior=build/port-runtime/cycle-096-absent-client
mkdir -p "$port_client"
cp -R "$port_prior/libraries" "$port_prior/versions" "$port_client/"
python3 scripts/porting/client/prepare_packaged.py --name cycle-098-fixed-client \
  --java "$port_java" --installer build/port-runtime/forge-47.4.28-installer.jar \
  --jar "$port_jar" --qa-jar "$port_qa" --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_enderface.py --name cycle-098-fixed-detailed --port 25875 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --launch "$port_client/launch.json"
```

The original jar and original QA run are preserved in `build/port-runtime/cycle-098-before-client` and `build/port-runtime/cycle-098-before-staff`.

## Results

- The original packaged client selected the Enderface correctly and the staff charge fell from 9,000 to 8,000 FE. The preview timed out with no screen. See `build/port-evidence/cycle-098-before-staff-exercise-client/summary.json`.
- The corrected staff action opened the actual preview and charged 1,000 FE per use. Right-clicking the rendered chest opened its vanilla menu from more than eight blocks away. Shift-click transferred exactly seven copper ingots and emptied the chest. A second preview click opened the Ender IO tank and showed its 1,750 mB. The tank menu closed when the Enderface was removed, and the player remained at the original position. The reopened server kept both containers and the replacement Enderface. A fresh client reopened the empty chest and still held the seven ingots.
- Eight server assertions and 29 client checks passed. The two server processes exited at 0 with empty registry and runtime error lists. See `build/port-runtime/cycle-098-fixed-detailed/enderface-results.json` for the logs and both client reports.
- The six framebuffer captures record the preview or corresponding menu. Their metadata records narrator off and all sound categories at zero. The native observer recorded zero activation, foreground and onscreen events across 1,017 exercise samples and 863 verification samples. The captures are in `build/port-evidence/cycle-098-fixed-detailed-exercise-client/` and `build/port-evidence/cycle-098-fixed-detailed-verify-client/`.
- The build and test-only client JAR passed. Unit reports list 79 tests with zero failures or skips. All 483 native GameTests passed, including all 161 discovered gameplay tests. See `build/cycle-098-build-final.log`. The production JAR SHA-256 is `8a143f9ce0289501403e097d407ee0c8aa916c184a948491973c2d37f15e811b`.

## Remaining verification

The preview still needs coverage for live scene updates, more camera angles and blocked or out-of-range interactions.
