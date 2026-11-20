# Cycle 210 — Stock Electromagnet crafting and experience orbs

The survival player discovers the stock recipe by obtaining a Vibrant Crystal with limited crafting enabled. The native recipe book fills the grid with four Energetic Alloy ingots, two Conductive Alloy ingots and that crystal. The player creates one empty Electromagnet and puts it in a stock Wired Charger. The charger has a 192 FE budget. The player takes the partially charged magnet from the input slot after the entire budget has transferred.

The stock magnet checks a five-block box around the player. The test places a stationary native experience orb 4.95 blocks away on each axis. It lies inside that box and outside both the vanilla eight-block player search and the vanilla pull radius measured from the player's mid-eye height. The off control spans at least 45 game ticks so the orb can run multiple vanilla search cycles. The registered key activates the original crafted item. The server measures the orb's position, the player's XP, and the active FE cost. A second orb at the same coordinates checks automatic shutdown after the magnet spends its charge. The final server reopens the world and checks both the remaining orb and the spent charger.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_jar=enderio/build/libs/enderio-8.2.14.209-1.20.1+43bebcf74f.jar
port_source="$port_runtime/cycle-209-mind-killer-persistence-client"
port_run=cycle-210-stock-magnet-orb
port_client="$port_runtime/$port_run-client"

./gradlew build --continue --console=plain > build/cycle-210-xp-magnet-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-210-xp-magnet-qa.log 2>&1
mkdir -p "$port_client/config"
cp -cR "$port_source/libraries" "$port_source/versions" "$port_client/"
cp "$port_source/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name "$port_run-client" \
  --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 -u scripts/porting/packaged_electromagnet_progression.py --name "$port_run" --port 26225 \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar" \
  --launch "$port_client/launch.json"
```

## Results

- The full build passes 79 unit tests and 540 native Forge runs, including all 213 discovered main gameplay tests. The guarded probe reobfuscates separately. See `build/cycle-210-xp-magnet-build.log` and `build/cycle-210-xp-magnet-qa.log`.
- The packaged test passes 19 runner assertions and 12 client checks. The original magnet receives all 192 FE from the Wired Charger. The first orb holds its exact spawn coordinates for 45 native ticks while the magnet is off. During a 12-tick active sample it moves 1.780 blocks closer on each horizontal axis and the magnet spends 12 FE. The original orb yields seven XP. The empty magnet turns itself off. The second orb stays at its original coordinates for 46 ticks and through the dedicated restart. The closed player save retains one uncharged, disabled magnet and seven XP. Both server sessions exit zero without runtime or registry errors. See `build/port-runtime/cycle-210-stock-magnet-orb/electromagnet-progression-results.json`.
- The three captures show the native crafting grid, the stock charger menu and the collected experience. The background observer records 2,256 samples with zero activation, foreground or visible-window events. Narrator is off and all ten sound categories are zero in all three captures. See `build/port-evidence/cycle-210-stock-magnet-orb-client/`.
- The reobfuscated production jar SHA-256 is `eec4f4815d8819e4ffecb6b389bfa581a9589ee1575ce4222e0ed0e9b067360d`. Both peers use those bytes. The production artifact contains no client probe classes.

## Next verification

Exercise stock Niard crafting, its native controls, and the saved state of the carried machine in an opted-in world. Existing packaged tests cover default feature availability, hopper input and water placement.
