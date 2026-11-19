# Cycle 159 — Verify Slice’N’Splice jobs and pause for missing tools

The slicer checked for an axe and shears when starting a job. An already-started job could still spend energy and reach completion after a tool was removed. The slicer now pauses its task and stops reporting itself active while either tool slot is empty. It keeps the same recipe, inputs, queued output and progress. The same task resumes when both tools return.

The packaged test loads all five stock recipes through native hoppers. It checks the stock ingredient order and one-item slots, including the Guardian Diode’s prismarine shard and crystals. Each machine saves 3,600 paid FE of a 20,000 FE job. After a dedicated restart, a guarded survival client opens the Z-Logic Controller job, takes the shears out with the real slot action, and sees the preserved job after the server supplies the remaining 16,400 FE. The client returns the same tagged shears and observes the final tool wear. The dedicated server also verifies exact outputs and tool state for Zombie Electrode, Skeletal Contractor, Guardian Diode and Ender Resonator. The Ender Resonator uses an axe and shears each one use from breaking.

## Commands

```sh
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-159-slicer-build.log 2>&1
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-159-slicer-final-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_before=enderio/build/libs/enderio-8.2.14.157-1.20.1+2e85579d37.jar
port_jar=enderio/build/libs/enderio-8.2.14.158-1.20.1+189b681a2f.jar
port_client=build/port-runtime/cycle-159-slicer-client
port_previous=build/port-runtime/cycle-157-sag-client
python3 scripts/porting/packaged_slicer.py --name cycle-159-slicer-before --port 26062 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_before" --probe-missing-tool \
  > build/cycle-159-slicer-before.log 2>&1
mkdir -p "$port_client/config"
cp -cR "$port_previous/libraries" "$port_previous/versions" "$port_client/"
cp "$port_previous/config/fml.toml" "$port_client/config/fml.toml"
python3 scripts/porting/client/prepare_packaged.py --name cycle-159-slicer-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_slicer.py --name cycle-159-slicer-final --port 26063 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --launch "$port_client/launch.json" \
  > build/cycle-159-slicer-final.log 2>&1
python3 scripts/porting/packaged_slicer.py --name cycle-159-slicer-headless --port 26064 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --probe-missing-tool \
  > build/cycle-159-slicer-headless.log 2>&1
```

## Results

- The previous production jar restores the paid Z-Logic Controller job after restart. Five seconds after removing the shears and adding 16,400 FE, consecutive server samples show the task at 6,630 FE paid and the buffer at 13,340 FE. The assertion catches the extra payment after the shears were removed. See `build/port-runtime/cycle-159-slicer-before/smoke-resume.log`.
- The final build passes 79 unit tests and 523 native runs, including 196 main gameplay cases. The new GameTest removes the axe and verifies that the installed shears stay unworn and the machine retains its exact balance. The first build exposed a fixture error because generic insertion placed the Basic Capacitor in a recipe slot. The final test installs it in the machine’s dedicated capacitor slot.
- The packaged multiplayer profile passes 33 server assertions and 10 client checks. The Z-Logic Controller stays at 3,600 FE paid while the full 16,400 FE remains available. After the shears return, the recipe delivers one controller and advances the tagged axe from 7 to 8 damage and the shears from 11 to 12. The dedicated server verifies the same one-use wear on the other reusable tools and expiration of both Ender Resonator tools. All five outputs and tool states survive a second restart. The headless profile passes nine assertions, including returning the shears using a native input hopper.
- All packaged servers and the guarded client exit cleanly. Both peers load production SHA-256 `92592a50e0e980fcd51ec88319d78e626d12bf6a56e6df653d4535f7f38cd374`. The observer records zero activation, foreground or visible-window events across 2,922 samples. All three captures show the narrator off and every volume at zero.

The server reports are under `build/port-runtime/cycle-159-slicer-final` and `build/port-runtime/cycle-159-slicer-headless`. The client report and captures are under `build/port-evidence/cycle-159-slicer-final-client`.

## Next verification

Convert active and carried Slice’N’Splice machines from a disposable Forge 6.2 save. Exercise the older configuration, inventory and task format, including exact tool wear on completion and after replanting.
