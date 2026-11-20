# Cycle 212 — Stock Farming Station crafting and factory reset

The packaged dedicated profile creates a default world and keeps all three experimental packs disabled. The survival player has limited crafting enabled and sees both stock recipes locked. The fixture supplies the five other ingredient types first, then two Soularium Ingots to unlock the shipped machine recipe. The native recipe book places all nine ingredients. The player collects one blank Farming Station and places it normally.

The fixture loads the placed machine with 6,177 FE, a tagged bee soul, a hidden radius of two, two configured faces, the redstone gate, a Basic Capacitor, three worn tools, seeds, fertilizer and outputs. Native mining and pickup carry that state. The survival player uses the shipped shapeless recipe to turn the populated item into one blank station. The server checks the actual inventories and item entities for retained or duplicated contents. The client opens the replanted machine's native menu and checks the defaults it receives. The dedicated restart checks the replanted block and the inventory saved to disk.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_jar=enderio/build/libs/enderio-8.2.14.211-1.20.1+e59f5a9eb7.jar
port_source="$port_runtime/cycle-211-stock-niard-client"
port_run=cycle-212-stock-farming
port_client="$port_runtime/$port_run-client"

./gradlew build --continue --console=plain > build/cycle-212-farming-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-212-farming-qa.log 2>&1
mkdir -p "$port_client/config"
cp -cR "$port_source/libraries" "$port_source/versions" "$port_client/"
cp "$port_source/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name "$port_run-client" \
  --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 -u scripts/porting/packaged_farming_station_crafting.py --name "$port_run" --port 26228 \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar" \
  --launch "$port_client/launch.json"
```

## Results

- The full build passes 79 unit cases and 540 native Forge GameTest runs. The guarded client probe reobfuscates successfully. See `build/cycle-212-farming-build.log` and `build/cycle-212-farming-qa.log`.
- The packaged profile passes 16 server assertions and 19 native client checks on Forge 47.4.28. Mining and pickup preserve the entire known state. The reset recipe returns one untagged machine and produces no loose items. Replanting restores zero FE, an empty inventory, no soul, six default faces, a hidden radius of five and Always Active redstone mode. These defaults survive restart. The saved player inventory contains only the pickaxe. Both server processes exit with code zero and have no runtime or registry errors. See `build/port-runtime/cycle-212-stock-farming/farming-station-crafting-results.json`.
- The three captures record narrator off and every volume category at zero. The macOS watcher records 1,706 samples with no activation, foreground or onscreen observations. See `build/port-evidence/cycle-212-stock-farming-client/`.
- The server and guarded client use the same production jar with SHA-256 `c1094c28e5dbec5cb9d70ed381ebc9c01410f024d2c500eb88b227ba1f2b44e1`. The production jar contains no client probe classes.

## Next verification

Exercise the Block Detector's stock survival recipe, placement, redstone output and behavior after a dedicated restart.
