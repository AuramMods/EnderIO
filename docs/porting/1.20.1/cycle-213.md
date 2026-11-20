# Cycle 213 — Stock Block Detector placement and redstone

A survival player discovers the shipped Block Detector recipe from Grains of Infinity with limited crafting enabled. The native recipe book fills the nine ingredient slots for six crafts. The server checks the exact number of machines and the original ingredient consumption.

The player places one detector facing each cardinal and vertical direction. The scene starts with wool in the detected cell and a vanilla lamp on the opposite side behind stone. A second lamp observes the non-output side. The fixture removes the wool, tests a direct lamp with a glass target and restores the original circuit. The guarded multiplayer client captures each facing in both powered states and measures the weak and direct signals on each face. The player mines the powered north-facing detector with its glass target still present, waits for the circuit to clear, then picks up and puts back the original drop.

The dedicated server reopens the saved world with three powered and three unpowered detectors. It verifies each facing and circuit, then reverses each signal using transparent glass or cave air.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_jar=enderio/build/libs/enderio-8.2.14.212-1.20.1+0e98a18cf8.jar
port_source="$port_runtime/cycle-212-stock-farming-client"
port_run=cycle-213-stock-block-detector-confirmed
port_client="$port_runtime/$port_run-client"

./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-213-block-detector-fixed-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-213-block-detector-render-qa.log 2>&1
mkdir -p "$port_client/config"
cp -cR "$port_source/libraries" "$port_source/versions" "$port_client/"
cp "$port_source/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name "$port_run-client" \
  --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 -u scripts/porting/packaged_block_detector.py --name "$port_run" --port 26231 \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar" \
  --launch "$port_client/launch.json"
```

## Results

- The initial full build passes 79 unit tests and 540 native Forge runs. The new packaged probe on port 26229 crafts and consumes the exact 54 ingredients. Its first occupied placement fails. The saved world contains the original wool, a north-facing detector with `powered=false`, the output stone and an unlit lamp. The unfixed jar has SHA-256 `ae78b820085ed4420775d4a6efa4615f234ae498789375ac9dc2d0db216b002a` and remains beside the saved world in the `mods` directory. See `build/cycle-213-block-detector-build.log`, `build/cycle-213-block-detector-qa.log` and `build/port-runtime/cycle-213-stock-block-detector/block-detector-results.json`.
- The fixed full build passes 79 unit cases and 541 native Forge runs, including both Block Detector regressions among 214 main gameplay cases. See `build/cycle-213-block-detector-fixed-build.log` and `enderio/build/test-results/gameTestServer/results.xml`.
- The first fixed multiplayer run verifies all six placements and both lamp routes, then confirms breaking the powered detector clears the circuit. The client exits before the server can query its inventory after replanting. The probe now waits for the server and client to observe the replant and consumed item before disconnecting. See `build/port-runtime/cycle-213-stock-block-detector-fixed/block-detector-results.json`.
- The confirmed profile passes 49 server assertions and 120 client checks on Forge 47.4.28. All six placed directions power a lamp through stone immediately, accept a transparent target, stop for cave air and leave their side lamp off. Native mining of the powered detector clears the circuit, returns exactly one item and allows it to be replanted. All six circuits retain their facing and mixed power states across a dedicated restart, then switch power in response to changed targets. Both servers exit cleanly with no runtime or registry errors. See `build/port-runtime/cycle-213-stock-block-detector-confirmed/block-detector-results.json`.
- The client verifies the front and back textures in all 12 baked variants. It checks weak and direct output at level 15 on the output face and zero on the other faces when powered, and zero everywhere when off. All 13 captures have every volume at zero and narrator off. The native watcher records 2,419 samples with no activation, foreground or onscreen events. The production jar has SHA-256 `10a438207d6c87c1180ea47827cec9e1b8bf547ed48c3dfd2f25a68656b2a604` and contains no client probe classes. See `build/port-evidence/cycle-213-stock-block-detector-confirmed-client/` and `build/cycle-213-block-detector-render-qa.log`.

## Changes

Initialize the placed state from the detected block and notify the output side and its neighbors on powered insertion and removal. A native regression places the default detector into an occupied scene and checks that removing it clears an indirect lamp with the target still present.

## Next verification

Exercise the shipped Vapor of Levity bucket and other stock fluid containers through live placement, collection and restart.
