# Cycle 198 — stock Ender Pearl and Obsidian powders

The packaged dedicated server sends one Ender Pearl and one Obsidian through native hoppers into two stock SAG Mills. Each operation spends exactly 2,400 FE. The output hoppers export nine powdered pearl and four powdered obsidian. The fixture moves the two original stacks into a workshop chest, then fills three other slots with a bowl, milk and wheat.

The guarded survival client joins over TCP with the Enderios recipe locked. It takes the chest contents through the native menu, unlocks the recipe with wheat and fills the grid using the recipe book with limited crafting enabled. A normal result pickup spends one of the original pearl powders and leaves an empty bucket in the crafting grid. Closing the menu moves the bucket to the survival inventory. The final dedicated restart checks both mill buffers and the empty chest. The closed player save independently verifies every remaining stack, the craft statistic and the recipe advancement.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_jar=enderio/build/libs/enderio-8.2.14.197-1.20.1+fc0ab1a4ca.jar
port_source="$port_runtime/cycle-196-photovoltaic-upgrades-stock-client"
port_name=cycle-198-pearl-powder-stock
port_client="$port_runtime/$port_name-client"

./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-198-pearl-powder-build.log 2>&1
mkdir -p "$port_client/config"
cp -cR "$port_source/libraries" "$port_source/versions" "$port_client/"
cp "$port_source/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name "$port_name-client" \
  --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 -u scripts/porting/packaged_pearl_powder.py --name "$port_name" --port 26193 \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar" --launch "$port_client/launch.json"
```

## Results

- The full build passes 79 unit tests with none skipped and 540 native runs, including all 213 main gameplay cases. The new scenario is in the reobfuscated test jar. The production jar contains zero client test classes. See `build/cycle-198-pearl-powder-build.log`.
- The packaged run passes 15 server and saved-state assertions and 10 client checks. The exact original outputs are nine pearl powders and four obsidian powders. Both mill jobs retain 137 FE after grinding and restart. The player consumes one pearl powder, one bowl, one milk bucket and one wheat. The final player NBT contains one Enderios, one empty bucket, eight pearl powders and four obsidian powders. The saved vanilla statistics record one craft, and the saved advancement records the wheat criterion. Both powder counts also match their live common tags.
- The client and server load production SHA-256 `52dfd68c0116b20f685a7617f7db353e12f13e342580276ec5c094c375904b3a`. Both headless dedicated sessions exit cleanly with zero runtime or registry errors. The crafting capture shows the four stock ingredients and one result. All volume categories are zero and the narrator is off. The native observer records zero activation, foreground or visible-window events across 2,387 samples. See `build/port-runtime/cycle-198-pearl-powder-stock/pearl-powder-results.json` and `build/port-evidence/cycle-198-pearl-powder-stock-client/summary.json`.

## Next verification

Grind plant matter from stock large ferns and dead bushes, then use the original green and brown outputs in the stock egg and slimeball dye recipes. Verify the actual yields, energy costs and output conservation after restart.
