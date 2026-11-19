# Cycle 193 — Enticing Crystal and Attractor Obelisk progression

The packaged test copies the disposable world from cycle 192. It verifies that the cycle's original Enticing Crystal and returned empty vial are both in their saved barrel. The crystal moves to the workshop chest, and the original vial stays in the barrel. The survival player takes the crystal through the chest menu, unlocks the stock Attractor Obelisk recipe under limited crafting, and uses the original crystal in the native grid. The player places the crafted block and opens its empty menu.

The player installs a Basic Capacitor and a pig-only Soul Filter through the menu. The range controls lower the stock eight-block range to seven and make it visible, and the redstone picker selects active with signal. The configured obelisk holds 12,000 FE without a signal. When the fixture applies redstone power, the obelisk draws a live pig from four blocks away through a vanilla path. The fixture checks energy use in whole ticks at the displayed 18 FE per tick and removes the signal. A dedicated restart restores the settings and exact remaining FE. The same saved block draws another pig when powered again.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_forge="$port_runtime/cycle-047-legacy-discover"
port_jar=enderio/build/libs/enderio-8.2.14.192-1.20.1+fe95ede713.jar
port_guarded="$port_runtime/cycle-191-weather-stock-client"
port_client="$port_runtime/cycle-193-attractor-stock-client"
port_world="$port_runtime/cycle-192-bound-crystals-stock/port-spawners"

./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-193-attractor-build.log 2>&1
mkdir -p "$port_client/config"
cp -cR "$port_guarded/libraries" "$port_guarded/versions" "$port_client/"
cp "$port_guarded/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name cycle-193-attractor-stock-client \
  --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 -u scripts/porting/packaged_attractor_progression.py --name cycle-193-attractor-stock --port 26183 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --launch "$port_client/launch.json" \
  --source-world "$port_world"
```

## Results

- The full build passes 79 unit tests with none skipped and 540 native runs, including all 213 discovered main gameplay cases. The production jar contains no client test classes. See `build/cycle-193-attractor-build.log`.
- The packaged run passes 10 server assertions and 20 client checks. Both pigs start four blocks away and move within 2.75 blocks after the redstone signal. The first run spends 252 FE in 14 active ticks. The restart preserves the 11,748 FE balance and all menu settings. The second run spends 270 FE in 15 active ticks and leaves 11,478 FE. The original empty vial remains in its barrel, and the closed player save has no unused recipe inputs or duplicate machines. Both server sessions exit cleanly with zero runtime or registry errors.
- Both peers load production SHA-256 `ff8940b9b8b8365dd5b08388c49011d60b019294d5f7640666757aa841b31ee2`. All three captures show every volume category at zero and the narrator off. The native observer records zero activation, foreground or visible-window events across 2,462 samples. See `build/port-runtime/cycle-193-attractor-stock/attractor-progression-results.json` and `build/port-evidence/cycle-193-attractor-stock-client/summary.json`.

## Next verification

Craft two Infinity Rods with stock Dark Steel Nuggets and fire-crafted Grains of Infinity. Use the original rods and a Shulker-bound Prescient Crystal to craft a Staff of Levity in survival.
