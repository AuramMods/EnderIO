# Cycle 196 — Pulsating and Vibrant Photovoltaic Module upgrades

The packaged test copies the disposable cycle 195 world, including the surviving Energetic Photovoltaic Module, its wired charger and the survival player's nine silicon. The player mines the saved module with an iron pickaxe and picks up its native drop. Limited crafting stays enabled. The workshop supplies the stock quartz, plates, alloy, capacitors and other ingredients for each upgrade.

The Pulsating recipe consumes the original Energetic drop. The player places and operates that same Pulsating module, then mines and recovers it for the Vibrant recipe. Both recipes and placements use native survival actions. The charger stays below the same position for both tiers. Tick measurements check zero generation at midnight and the stock daylight rates of 16 and 64 FE per tick. A dedicated restart verifies the final Vibrant module, the exact buffers and renewed daylight output. The closed player save checks both tool uses and the original silicon balance.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_forge="$port_runtime/cycle-047-legacy-discover"
port_jar=enderio/build/libs/enderio-8.2.14.195-1.20.1+9cc8fbfff0.jar
port_guarded="$port_runtime/cycle-195-photovoltaic-stock-client"
port_client="$port_runtime/cycle-196-photovoltaic-upgrades-stock-client"
port_world="$port_runtime/cycle-195-photovoltaic-stock/port-spawners"

./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-196-photovoltaic-upgrades-build.log 2>&1
mkdir -p "$port_client/config"
cp -cR "$port_guarded/libraries" "$port_guarded/versions" "$port_client/"
cp "$port_guarded/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name cycle-196-photovoltaic-upgrades-stock-client \
  --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 -u scripts/porting/packaged_photovoltaic_upgrades.py --name cycle-196-photovoltaic-upgrades-stock --port 26189 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --launch "$port_client/launch.json" \
  --source-world "$port_world"
```

## Results

- The full build passes 79 unit tests with none skipped and 540 native runs, including 213 main gameplay cases. The reobfuscated test jar contains the new client scenario. The production jar contains zero client test classes. See `build/cycle-196-photovoltaic-upgrades-build.log`.
- The packaged run passes 24 server assertions and 21 client checks. The player mines and collects both original modules, fills both stock recipes from the recipe book, consumes all the correct parts and places both crafted upgrades in survival. The final player save contains nine original silicon and one iron pickaxe with exactly two uses of durability.
- Pulsating delivers 160 FE over 10 noon ticks and Vibrant delivers 640 FE over 10 noon ticks. The module produces zero FE in each midnight window. The restarted Vibrant preserves the exact midnight balances of 0 FE in the module and 1,164 FE in the wired charger, then delivers another 704 FE over 11 measured noon ticks. Both dedicated sessions exit cleanly with zero runtime or registry errors.
- Both peers load production SHA-256 `3b945b106730a7f0063778ea5c2b8c7f6ede3e97128e946c9a19374195499c2b`. All four client captures show every volume at zero and the narrator off. The native observer records zero activation, foreground or visible-window events across 3,323 samples. See `build/port-runtime/cycle-196-photovoltaic-upgrades-stock/photovoltaic-upgrades-results.json` and `build/port-evidence/cycle-196-photovoltaic-upgrades-stock-client/summary.json`.

## Next verification

Verify that the stock SAG Mill turns iron, gold and copper ore or raw metal into their corresponding powders and that those original outputs smelt or blast back to ingots. Exercise the stock energy costs, native transfer and saved inventories across a dedicated restart.
