# Cycle 195 — Photovoltaic Composite, Plates and Energetic Module

Three stock SAG Mills grind lapis, coal and red sand. The fixture feeds them through vanilla hoppers and checks each energy balance at 2,400 FE per raw item. The survival player uses the original powdered lapis, powdered coal and silicon in six shapeless crafts with limited crafting enabled. A low red-sand roll can trigger one additional stock batch.

The six original composites enter an Alloy Smelter through its top hopper. The native plate recipe consumes two composites per plate and the three batches spend exactly 16,800 FE. The player takes the original plates from the workshop chest and uses them in the published Energetic Photovoltaic Module recipe. The client places that same module in survival. A wired charger accepts power underneath it. A measurement function reads game time and both energy buffers in the same tick. At midnight it verifies zero generation, and at noon it measures the published 4 FE per tick and actual charger delivery. A dedicated restart verifies the exact saved buffers and repeats the generation check.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_forge="$port_runtime/cycle-047-legacy-discover"
port_jar=enderio/build/libs/enderio-8.2.14.194-1.20.1+177abfed1a.jar
port_guarded="$port_runtime/cycle-194-infinity-rod-confirmed-client"
port_client="$port_runtime/cycle-195-photovoltaic-stock-client"

./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-195-photovoltaic-build.log 2>&1
mkdir -p "$port_client/config"
cp -cR "$port_guarded/libraries" "$port_guarded/versions" "$port_client/"
cp "$port_guarded/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name cycle-195-photovoltaic-stock-client \
  --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 -u scripts/porting/packaged_photovoltaic_progression.py --name cycle-195-photovoltaic-stock --port 26188 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --launch "$port_client/launch.json"
```

## Results

- The full build passes 79 unit tests with none skipped and 540 native runs, including all 213 discovered main gameplay cases. The production jar contains no client test classes. See `build/cycle-195-photovoltaic-build.log`.
- The packaged run passes 27 server assertions and 31 client checks. The three mills spend 86,400 FE and deliver six powdered lapis, six powdered coal and 15 silicon. The player uses six of each for the composites, and the final save keeps the nine surplus silicon. The smelter delivers all three plates for 16,800 FE. The player uses all three in the single module and places it in survival.
- The first measured noon window generates 48 FE over 12 ticks. The panel buffer stays at zero while the actual charger receives the power. Midnight preserves zero FE in the panel and 68 FE in the charger, and both exact balances survive restart. The reopened panel generates 44 FE over 11 measured noon ticks. Both dedicated sessions exit cleanly with zero runtime or registry errors.
- Both peers load production SHA-256 `c038aaeb4309ccbd2a0265c0534f12e9699dc5260258e5f82e85fc9cb84562fa`. All three captures show every volume category at zero and the narrator off. The native observer records zero activation, foreground or visible-window events across 3,000 samples. See `build/port-runtime/cycle-195-photovoltaic-stock/photovoltaic-progression-results.json` and `build/port-evidence/cycle-195-photovoltaic-stock-client/summary.json`.

## Next verification

Recover the original Energetic Module from the disposable save and use it in the stock Pulsating and Vibrant Module upgrades. Verify that both crafted tiers deliver their stock generation rates.
