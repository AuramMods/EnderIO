# Cycle 131 — Render decorative redstone and resume saved lever pulses

The packaged Forge client checks every registered decorative redstone block and inventory model. It checks for textured geometry in every state and verifies a distinct pressed shape for each block. It renders the same physical arrays both at rest and activated. The client then operates all ten levers through normal survival block use over TCP.

A test datapack records the activation and reset ticks. Before reopening the world, the harness reads the asynchronous chunk snapshot and the separately saved world clock. The countdown on disk must agree with the original activation and the chunk save time. The lever must then reset on the deadline formed by adding the saved countdown to the reopened world's time.

The dedicated pressure plate fixture also supports creating its world under the official Forge 6.2.18 beta and opening a copy on the port.

## Commands

```sh
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-131-package-qa.log 2>&1
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-131-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_source=build/port-runtime/cycle-129-surviving-filter-client
port_profile=build/port-runtime/cycle-131-levers-client
production_jar=build/port-runtime/cycle-130-verified-packaged/server/mods/enderio.jar
mkdir "$port_profile"
cp -R "$port_source/libraries" "$port_source/versions" "$port_profile/"
python3 scripts/porting/client/prepare_packaged.py --name cycle-131-levers-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$production_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_lever_persistence.py --name cycle-131-verified --port 25986 \
  --java "$port_java" --forge-server "$port_forge" --jar "$production_jar" --launch "$port_profile/launch.json"
python3 scripts/porting/packaged_decorative_redstone.py --name cycle-131-legacy-verified --port 25985 \
  --java "$port_java" --forge-server "$port_forge" --jar "$production_jar" \
  --legacy-jar build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar
```

## Results

- The full Java 17 build passes 79 unit tests and 493 native Forge executions, including 171 main gameplay cases. The distributable Ender IO jar used for runtime checks is `3de350157e8d1a1eb5bf3087889604e35527ed3265235294aeca610f9a2f8676` by SHA-256.
- The packaged client passes 69 checks, including all 300 block states and 26 inventory models. Each activated shape differs from its resting shape. Both rows of pressure plates and all ten levers are visible in the paired frames. The client sampled both plate states through a fixture switch, then operated the levers as a survival player. All four screenshots confirm the narrator off and all ten sound categories set to zero. The native observer saw zero activation, foreground or onscreen events in 1,795 observations. See `build/port-evidence/cycle-131-verified/summary.json` and the four PNG files beside it.
- The initial restart kept all ten levers active. The raw world stopwatch made the first pair appear 37 ticks late. A separate disk probe explains the shift. The chunk snapshot was at tick 697, the pulse began at 696, and the shortest pair's saved countdown was 99. The world metadata had advanced to tick 730. On reopen, the pair reset at tick 829, exactly 99 ticks later. The scheduler rebases the saved countdown onto the world clock when reopening. The final fixture refreshes the lever chunk before shutdown and asserts both clocks directly. See `build/cycle-131-timer-probe.log` and `build/port-runtime/cycle-131-timer-probe/server/smoke-reopened.log`.
- The final persistence run passes 31 checks. All ten levers activate at tick 745 and are still active after restart. The chunk saves at tick 779 with 66, 166, 566, 1,166 and 5,966 ticks remaining for the five pairs. The world saves at tick 781, and the pairs reset exactly at 847, 947, 1,347, 1,947 and 6,747. Both dedicated sessions exit at zero with no runtime or registry errors. See `build/port-runtime/cycle-131-verified/lever-persistence-results.json`.
- The Forge 6.2.18 beta world passes 112 checks through conversion with no runtime or registry errors. All 26 registered blocks survive. Resting lever polarity, native loot in the historical install, occupied plate states and release on the port pass. The two historical silent stone plates follow the 6.2 detector and ignore dropped items before conversion. The port uses the target 1.21.1 baseline behavior, so those same persisted items activate the plates after conversion. See `build/port-runtime/cycle-131-legacy-verified/decorative-redstone-results.json`.

## Remaining verification

The Forge 6.2 conversion fixture uses resting levers. A world saved mid-pulse in the historical mod still needs its own conversion test.
