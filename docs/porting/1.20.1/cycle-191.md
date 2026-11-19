# Cycle 191 — Ender Crystal and Weather Obelisk progression

A vanilla dispenser captures a live Enderman in a stock Soul Vial. Sided hoppers feed that captured vial and a Vibrant Crystal to the Soul Binder. A native tank supplies XP Juice. The job waits at 1,439 mB and starts after the tank brings the total to 1,477 mB. It saves at 12,300 of 76,800 FE with its original captured soul, crystal and both queued outputs. The next dedicated session restores the paid job, completes it and exports its original Ender Crystal and empty vial through a hopper.

The player takes both outputs from the workshop chest through a vanilla menu. With limited crafting enabled, the newly bound crystal goes into the stock Weather Crystal recipe with two Pulsating and two Vibrant Crystals. The player collects that result and spends it in the native Weather Obelisk recipe. The survival client places the crafted block and loads two Firework Rockets through the obelisk menu. A sided tank supplies 1,237 mB of Cloud Seed. A roof holds the rain job at zero ticks until the server removes it. The placed block then launches a firework, changes the world from clear to rain, and leaves one rocket and 237 mB of Cloud Seed. The final restart checks those remainders, the placed block and the rain. The closed player save checks the returned vial.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_forge="$port_runtime/cycle-047-legacy-discover"
port_jar=enderio/build/libs/enderio-8.2.14.190-1.20.1+7c26f600e0.jar
port_guarded="$port_runtime/cycle-190-crystal-verified-client"
port_client="$port_runtime/cycle-191-weather-stock-client"

./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-191-weather-build.log 2>&1
mkdir -p "$port_client/config"
cp -cR "$port_guarded/libraries" "$port_guarded/versions" "$port_client/"
cp "$port_guarded/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name cycle-191-weather-stock-client \
  --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 -u scripts/porting/packaged_weather_progression.py --name cycle-191-weather-stock --port 26181 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --launch "$port_client/launch.json"
```

## Results

- The full build passes 79 unit tests with none skipped and 540 native runs, including all 213 discovered main gameplay cases. The production jar contains no client test classes. See `build/cycle-191-weather-build.log`.
- The packaged run passes 15 server assertions and 19 client checks across three dedicated sessions. The Soul Binder retains 317 FE and 37 mB after the original crystal is used. Both survival recipes use the stock grid and exact ingredients. The crafted obelisk consumes one rocket and 1,000 mB of Cloud Seed, launches a real firework and starts rain. The placed block, rain and remaining rocket and fluid survive restart. The player save holds exactly one empty Soul Vial. All three server sessions exit cleanly with zero runtime or registry errors.
- Both peers load production SHA-256 `bfd309fdb01e0801c04581380086cfaef63e04fedc2fc87fcfc932e320af4bd3`. All four client captures show every volume category at zero and the narrator off. The native observer records zero activation, foreground or visible-window events across 3,621 samples. See `build/port-runtime/cycle-191-weather-stock/weather-progression-results.json` and `build/port-evidence/cycle-191-weather-stock-client/summary.json`.

## Next verification

Bind Shulker and Villager souls in the stock Prescient and Enticing Crystal recipes. Use an original Prescient Crystal and another freshly bound Ender Crystal in their stock SAG Mill powder recipes.
