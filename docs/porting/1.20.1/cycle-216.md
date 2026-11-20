# Cycle 216 — Weather Obelisk transfers through packaged JEI

The isolated Forge client loads the pinned JEI jar and joins an Ender IO server with the core jar alone. The client checks that the Weather Obelisk is a recipe catalyst, a rocket finds all three stock recipes, and the three fluids find their respective rain, lightning and clear recipes. Each native JEI layout shows one firework rocket and 1,000 mB of the published fluid.

The fixture starts the rain obelisk with 999 mB and no rockets, then tries the wrong fluid with rockets available, then the right fluid one millibucket short. The registered transfer handler reports the missing inputs while the server keeps the original slots and fluid unchanged. Once the tanks are ready, the player clicks the native JEI transfer button for each weather. Each roofed obelisk receives one tagged rocket and waits at zero of its published 600 ticks.

The dedicated server restarts with the three queued jobs. It opens their roofs one at a time so the jobs change clear weather to rain, rain to thunder, and thunder to clear. Each launches a real firework and spends one rocket and 1,000 mB. A second restart verifies the state on disk.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_jar=enderio/build/libs/enderio-8.2.14.215-1.20.1+c48f93dabf.jar
port_jei="$port_runtime/cycle-161-slicer-jei-client/mods/jei-9jqubC9n.jar"
port_source="$port_runtime/cycle-215-stock-machine-resets-client"
port_run=cycle-216-stock-weather-jei
port_client="$port_runtime/$port_run-client"

./gradlew build --continue --console=plain > build/cycle-216-weather-jei-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-216-weather-jei-qa.log 2>&1
mkdir -p "$port_client/config"
cp -cR "$port_source/libraries" "$port_source/versions" "$port_client/"
cp "$port_source/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name "$port_run-client" \
  --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets" --mod "$port_jei"
python3 -u scripts/porting/packaged_weather_jei.py --name "$port_run" --port 26234 \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar" \
  --jei "$port_jei" --launch "$port_client/launch.json"
```

## Results

- The full build passes 79 unit tests and 541 native Forge GameTest runs. The guarded client probe reobfuscates. See `build/cycle-216-weather-jei-build.log` and `build/cycle-216-weather-jei-qa.log`.
- The packaged run passes 26 server assertions and 37 client checks on Forge 47.4.28 with JEI 15.56.0.205 on the client. The native transfer buttons move one tagged rocket each. The dedicated restart preserves all three staged jobs. Each job launches a firework, changes the weather and spends precisely one rocket and 1,000 mB. The final clear weather and tagged remainders of 237, 321 and 459 mB survive the second restart. The unused tagged rocket and paper are the only two stacks in the saved player inventory. All three server boots exit cleanly with no runtime or registry errors. See `build/port-runtime/cycle-216-stock-weather-jei/weather-jei-results.json`.
- All six captures record the narrator off and every volume at zero. The native watcher reports 1,820 samples with zero activation, foreground or onscreen events. See `build/port-evidence/cycle-216-stock-weather-jei-client/`.
- The Ender IO production jar has SHA-256 `d5bd6865e5c3e907842a8f6f72ba98bae409e6e811802b9289b238e0e5feb7c8` and contains no client probe classes. The pinned JEI file has SHA-256 `6b251e60a4719c874da89d750af97aa6498f6f4475e3ae79cb1288c7bf0cb65c`.

## Next verification

Craft and open the LaserIO guide with the pinned Patchouli version in a packaged survival client. Check its saved book identity and reopen the guide after a dedicated server restart.
