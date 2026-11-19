# Cycle 082 — Farming Station crop lifecycle

## Scope

Verify hoe tilling, native planting items, nether wart and the two crops from sniffer seeds. Confirm that the station can grow a pitcher pod to maturity and collect exactly one plant, with both halves retained while the output is full.

## Changes

- Route pitcher pods through the existing crop planting task and Forge block-item interaction.
- Verify the mature lower crop and its upper partner before harvesting. Collect loot at the lower half and remove that half so the native neighbor update removes its partner. A damaged crop missing its partner leaves the unrelated block alone.
- Add seven native Forge scenarios covering pitcher planting, fertilizer, full-output retry, duplicate drops, incomplete halves, hoe tilling, torchflowers, wart and stalk planting.
- Add a packaged fixture that runs the actual station scheduler at radius one, verifies both flower harvests and a nether wart replant, and reopens the saved crops and outputs twice.

## Commands

```sh
./gradlew :enderio:verifyGameplayTests --console=plain
./gradlew build --continue --console=plain
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.81-1.20.1+7a1c408e22.jar
python3 scripts/porting/packaged_farming_station_crops.py --name cycle-082-crops --port 25804 \
  --java "$port_java" --forge-server build/port-runtime/cycle-047-legacy-discover --jar "$port_jar"
python3 scripts/porting/packaged_farming_station.py --name cycle-082-wheat --port 25805 \
  --java "$port_java" --forge-server build/port-runtime/cycle-047-legacy-discover --jar "$port_jar"
```

## Results

- Before the fixes, the planting task ignored a pitcher pod, a malformed lower half caused the station to harvest an unrelated block above it, and harvesting a valid pitcher crop produced one stored flower and one loose flower. The hoe, torchflower, nether wart and stalk planting tests already passed. See `build/cycle-082-duplicate-reproduction-results.xml`.
- The full build passed 79 unit tests and 473 required Forge GameTests with zero failures or skips. The native counts were 171 core, seven Mekanism, 117 registry, 151 gameplay and 27 addon. The seven new scenarios passed. See `build/cycle-082-build.log`.
- The packaged crop fixture passed all eight assertions. The last hoe use tilled dirt, and the station then planted wheat and nether wart. Six units of bone meal grew both flowers and both pitcher halves stayed present until outputs were freed after a restart. The station collected one pitcher plant and one torchflower with zero loose items nearby. Mature nether wart also waited on full outputs, then yielded three wart items and was replanted at age zero. The second restart preserved both flower outputs, the exact wart yield and the planted wheat and wart. The fixture set wart maturity explicitly to control random growth timing. See `build/port-runtime/cycle-082-crops/farming-station-crop-results.json`.
- All eight packaged wheat conservation assertions passed again, including output extraction through a vanilla hopper. Both profiles used the same Java 17 production jar on Forge 47.4.28. Its SHA-256 is `85390000c7d4511818e91e35cbdd9b18ed564b6553cf23f0cd21c5ed215120c8`. All six headless server instances exited with code 0 and zero runtime or registry errors. See `build/port-runtime/cycle-082-wheat/farming-station-results.json`.

## Remaining verification

The Farming Station's crafting and factory reset still need runtime evidence. The next machine cycle can cover Painting Machine inputs, placed paints and save migration. Other feature families and optional integrations remain tracked in the parity matrix.
