# Cycle 081 — Farming Station survival carriage and controls

## Scope

Exercise normal mining, pickup and placement of a stocked Farming Station on a packaged Forge server. Verify that its carried radius controls work before opening the range display, and exercise the actual menu, settings synchronization and range renderer through a guarded client connection.

## Changes

- Rebuild the working plot when a loaded save or carried item changes the station's radius. Route server controls through the same range normalization. A visibility-only change keeps the current scan position.
- Add two native regressions that exercise the working plot after restoring a smaller radius from an item or a saved tag.
- Add a packaged survival fixture and a standalone client QA scenario. The server fixture uses a neighbor redstone signal to test the restored working radius without rewriting the newly placed block data.

## Commands

```sh
./gradlew :enderio:verifyGameplayTests --console=plain
./gradlew build --continue --console=plain
./gradlew :enderio:reobfClientSmokeJar --console=plain
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.80-1.20.1+feb2ee8832.jar
mkdir build/port-runtime/cycle-081-client-verified
cp -R build/port-runtime/cycle-077-client-verified/libraries build/port-runtime/cycle-077-client-verified/versions \
  build/port-runtime/cycle-081-client-verified/
python3 scripts/porting/client/prepare_packaged.py --name cycle-081-client-verified --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_farming_station_carry.py --name cycle-081-carry-verified --port 25803 \
  --java "$port_java" --forge-server build/port-runtime/cycle-047-legacy-discover --jar "$port_jar" \
  --launch build/port-runtime/cycle-081-client-verified/launch.json
python3 scripts/porting/packaged_farming_station_harvest.py --name cycle-081-harvest-regression --port 25802 \
  --java "$port_java" --forge-server build/port-runtime/cycle-047-legacy-discover --jar "$port_jar"
```

## Results

- Before the fix, both restore paths reported the smaller radius while harvesting the outside pumpkin. See `build/cycle-081-range-reproduction-results.xml`.
- The full build passed 79 unit tests and 466 required Forge GameTests with zero failures or skips. The native counts were 171 core, seven Mekanism, 117 registry, 144 gameplay and 27 addon. The QA mod built separately. See `build/cycle-081-build.log` and `build/cycle-081-client-final-build.log`.
- The first client check waited for redstone mode on the client block entity and timed out after the other interactions. The restarted server had `never_active`, visible radius two and the correct remaining inventory. Redstone mode travels through a menu sync slot, so the revised test verifies the server mode and opens a fresh menu to confirm it on the client. See `build/port-evidence/cycle-081-carry-client/summary.json` and `build/cycle-081-control-inspection.log`.
- The final dedicated TCP run passed eight server assertions and 16 client checks. Survival mining dropped one station, automatic pickup moved it to the player's inventory and ordinary placement consumed it. The replanted station received exactly 6,177 FE, a tagged bee soul, the four seed reserves, three tagged tools, fertilizer, outputs and both I/O modes. A redstone neighbor powered a real pumpkin harvest two blocks away while the pumpkin three blocks away stayed planted. The player shift-clicked seven wheat, enabled range visibility and changed the redstone mode. A fresh menu showed the changes, and the restarted server retained the results and the remaining 5,857 FE. See `build/port-runtime/cycle-081-carry-verified/farming-station-carry-results.json`.
- The guarded client observed a range particle five blocks wide at the expected anchor. Both 960×540 captures rendered with all ten volumes at zero, narrator off and the cursor uncaptured. The native observer sampled 1,655 times with zero activation, foreground or visible-window observations. See `build/port-evidence/cycle-081-carry-verified-client/summary.json`, `replanted-farming-station.png` and `farming-station-range.png` in the same directory.
- All nine packaged tree and tall crop regression assertions still passed on this production jar. See `build/port-runtime/cycle-081-harvest-regression/farming-station-harvest-results.json`.
- Client and server used the same Java 17 production jar on Forge 47.4.28. Its SHA-256 is `222db9cc29e29f9e219faf37734851d8a09c7b4aab742c4d692f5c0af85804f4`. The client QA probe is in a separate jar. All four dedicated server instances in the final profiles exited with code 0 and zero runtime or registry errors.

## Remaining verification

Verify the other Farming Station task families, including pitcher crops, flowers, nether wart and tilling dirt with a hoe.
