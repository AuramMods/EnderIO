# Cycle 214 — Stock fluid buckets, flow and saved sources

Exercise all eleven shipped filled buckets using the real packaged Forge client and a dedicated server. Each survival use faces the floor of a stone basin from its rim. The original filled bucket produces one source block and returns one empty bucket. A separate cell accepts one level of horizontal flow, with each basin isolated from the others. The client checks the source and flowing fluid IDs, baked bucket model, the two atlas sprites and emitted block light.

The first dedicated restart checks every saved source and flowing cell and the player's original empty buckets. The returning survival player first points an empty bucket at the flowing cell and checks that it stays empty. The same bucket then picks up the source, returning exactly 1,000 mB of the original fluid. With the source removed, its flow recedes. The second dedicated restart checks the empty basins and the full inventory on disk.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_jar=enderio/build/libs/enderio-8.2.14.213-1.20.1+c7ae08c7e3.jar
port_source="$port_runtime/cycle-213-stock-block-detector-confirmed-client"
port_run=cycle-214-stock-fluid-buckets
port_client="$port_runtime/$port_run-client"

./gradlew build --continue --console=plain > build/cycle-214-fluid-buckets-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-214-fluid-buckets-qa.log 2>&1
mkdir -p "$port_client/config"
cp -cR "$port_source/libraries" "$port_source/versions" "$port_client/"
cp "$port_source/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name "$port_run-client" \
  --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 -u scripts/porting/packaged_fluid_buckets.py --name "$port_run" --port 26232 \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar" \
  --launch "$port_client/launch.json"
```

## Results

- The full build passes 79 unit tests and 541 native Forge GameTest runs. The guarded client probe reobfuscates. See `build/cycle-214-fluid-buckets-build.log` and `build/cycle-214-fluid-buckets-qa.log`.
- The packaged run passes 58 assertions and 90 client checks on Forge 47.4.28. All eleven filled buckets produce the correct source and adjacent flow, then return one empty bucket. The first dedicated restart preserves all 22 fluid cells and eleven empty buckets. Flowing cells reject pickup and each reloaded source returns exactly 1,000 mB in its original bucket type. The second restart preserves eleven full buckets and 22 empty cells. All three server processes exit cleanly with no runtime or registry errors. See `build/port-runtime/cycle-214-stock-fluid-buckets/fluid-buckets-results.json`.
- The client verifies eleven baked bucket models and 22 atlas sprites. Block light reads 3 for Dew of the Void, 5 for Fire Water, 10 for XP Juice, 15 for Liquid Sunshine and zero for the other seven fluids. All eleven placement captures record every volume category at zero and the narrator off. The pickup session uses the same muted profile. The native watcher records 1,903 placement samples and 2,101 pickup samples with no activation, foreground or onscreen observations. See `build/port-evidence/cycle-214-stock-fluid-buckets-place-client/` and `build/port-evidence/cycle-214-stock-fluid-buckets-collect-client/`.
- The production jar has SHA-256 `e96a3a71d6287d69e5c1b16ac2a3fbf7c51dd620e18349eccafeddf991cc095e` and contains no client probe classes.

## Next verification

Run the stock Niard and XP Obelisk factory reset recipes against populated native drops.
