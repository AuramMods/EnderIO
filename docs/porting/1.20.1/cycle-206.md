# Cycle 206 — Stock Powered Spawner crafting and soul erasure

A fresh Forge server uses native vanilla spawner loot to produce two Broken Spawners, one bound to a cow and one to a pig. A survival player works at an ordinary crafting table with limited crafting enabled and unlocks the stock recipe by obtaining an Ensouled Chassis. The complete grid initially holds an unbound Broken Spawner and produces no result. Replacing that one input with the original cow drop yields a cow-bound Powered Spawner. The second craft uses the pig drop and the same stock recipe. The player then uses the stock shapeless recipe to erase the pig soul.

The bound cow machine is placed using ordinary survival placement. The emptied pig spawner stays in the player's inventory. Closed player saves and a dedicated restart check both outcomes.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_jar=enderio/build/libs/enderio-8.2.14.205-1.20.1+d0b4249a62.jar
port_source="$port_runtime/cycle-205-creative-tab-stock"
port_run=cycle-206-stock-spawner
port_client="$port_runtime/$port_run-client"

./gradlew build --continue --console=plain > build/cycle-206-spawner-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-206-spawner-qa.log 2>&1
mkdir -p "$port_client/config"
cp -cR "$port_source/libraries" "$port_source/versions" "$port_client/"
cp "$port_source/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name "$port_run-client" \
  --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 -u scripts/porting/packaged_spawner_crafting.py --name "$port_run" --port 26212 \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar" --launch "$port_client/launch.json"
```

## Results

- The full build passes 79 unit tests and 540 native Forge runs. The 213 discovered main gameplay tests all execute and pass. The added client probe reobfuscates in its own test jar. See `build/cycle-206-spawner-build.log` and `build/cycle-206-spawner-qa.log`.
- The packaged scenario passes 14 dedicated multiplayer client checks and 13 runner assertions. The two native shaped crafts each consume four Soularium ingots, one Broken Spawner, one Ensouled Chassis, two Vibrant Crystals and one Z-Logic Controller. The cow binding survives placement and dedicated restart. The pig binding is present before erasure. The closed player inventory contains exactly one unbound Powered Spawner both before and after the server reopens. Both dedicated server processes exit zero with no runtime or registry errors. See `build/port-runtime/cycle-206-stock-spawner/spawner-crafting-results.json`.
- The three crafting captures show the original layouts and outputs. The packaged observer records 1,662 samples with zero activation, foreground or on-screen window events. Narrator is off and all ten volume categories are zero in all three captures. See `build/port-evidence/cycle-206-stock-spawner-client/summary.json`.
- The reobfuscated production jar used by both peers has SHA-256 `829b6e2c76cbde0d1f2d444acd0c685cb7952b38e8399d60d9187430452414de`. The production jar has no client probe classes.

## Next verification

Exercise the remaining stock Enchanter recipes through their native selector, including the supported enchantment and level ranges, exact ingredient and XP costs, and saved output. Previous packaged tests already cover ready-state restart and live recipe changes.
