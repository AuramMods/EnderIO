# Cycle 190 — Pulsating and Vibrant Crystals and powders

The packaged fixture automates four stock Alloy Smelter recipes with vanilla hoppers. It produces two Pulsating Alloy ingots and uses the original Conductive and Energetic Alloy outputs to make eight Vibrant Alloy ingots. The survival player takes the Pulsating and Vibrant ingots, a diamond and an emerald from the workshop chest. Limited crafting stays enabled. Each nugget recipe unlocks from its corresponding ingot, and each crystal recipe unlocks from its own nuggets. The player crafts both nine-nugget stacks and spends eight of each around the appropriate gem in the native crafting grid.

The player shift-clicks each original crystal into its matching SAG Mill. Both jobs pause at 600 of 2,400 FE with their original crystal, exactly one queued powder and an unused flint. The client checks the two native menus, including the 25% progress and unconsumed input. The dedicated server reopens both saved jobs, supplies the remaining energy and checks native hopper extraction of exactly one powder per mill. A second restart checks both powders, the unused flint and the exact energy balances. The closed player save checks the leftover ingots and nuggets.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_forge="$port_runtime/cycle-047-legacy-discover"
port_jar=enderio/build/libs/enderio-8.2.14.189-1.20.1+1cb09f3396.jar
port_guarded="$port_runtime/cycle-189-legacy-skull-verified-client"
port_client="$port_runtime/cycle-190-crystal-verified-client"

./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-190-crystal-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-190-crystal-qa-rebuild.log 2>&1
mkdir -p "$port_client/config"
cp -cR "$port_guarded/libraries" "$port_guarded/versions" "$port_client/"
cp "$port_guarded/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name cycle-190-crystal-verified-client \
  --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 -u scripts/porting/packaged_crystal_progression.py --name cycle-190-crystal-verified --port 26180 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --launch "$port_client/launch.json"
```

## Results

- The full build passes 79 unit tests with none skipped and 540 native runs, including all 213 discovered main gameplay cases. The production jar contains zero client QA entries. See `build/cycle-190-crystal-build.log`.
- The first packaged run uses `cycle-190-crystal-stock` on port 26179. It verifies both survival recipes and reaches the 600 FE Pulsating job. Its client test incorrectly expects an empty input slot at partial progress. The server confirms the crystal remains in the input slot until the powder is ready. The corrected client checks for that retained crystal. See `build/port-runtime/cycle-190-crystal-stock/crystal-progression-results.json` and `build/port-evidence/cycle-190-crystal-stock-client/summary.json`.
- The retry passes 16 server assertions and 27 client checks across three dedicated sessions. Both jobs resume with their original crystal and one queued powder. Each mill spends the remaining 1,800 FE, exports one powder and retains 137 FE and one flint through the final restart. The player save holds one Pulsating ingot, seven Vibrant ingots and one nugget of each alloy. All three servers exit cleanly with zero runtime or registry errors.
- Both peers load production SHA-256 `2c6f145ed1522eed72fb43bdad9cf300267e4fad941bf1c9329675dadae2ed40`. All four captures record every volume category at zero and narrator off. The native observer records zero activation, foreground or visible-window events across 2,276 samples. See `build/port-runtime/cycle-190-crystal-verified/crystal-progression-results.json` and `build/port-evidence/cycle-190-crystal-verified-client/summary.json`.

## Next verification

Bind an Enderman soul to a Vibrant Crystal using the stock Soul Binder recipe. Use the resulting Ender Crystal in the survival Weather Crystal and Weather Obelisk recipes, then operate the crafted obelisk.
