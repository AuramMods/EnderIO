# Cycle 188 — Dark Steel Sword and Enderman Head progression

The fixture runs the stock Dark Steel Alloy Smelter recipe. Native hoppers supply the input and collect the two original ingots. The survival player takes them from the workshop chest and uses the recipe book with limited crafting enabled. The client checks that the sword recipe consumes both smelted ingots and a stick.

The player attacks live Endermen through the native multiplayer action. The disposable arena supplies low-health, stationary targets and uses a vanilla team to suppress collision and sweep damage to other test targets. Direct hits keep the stock 7% roll. The client stops at the first observed head, and the server checks that one original item was picked up and that sword damage matches the direct attacks. The fixture allows up to 192 direct attacks in six batches.

The player places the same head on the floor, mines and picks up the actual block drop, and mounts it on a wall. The client verifies the mod's renderer on both variants. After a dedicated restart, it opens the saved wall display, mines the head again, and shift-clicks the original item into the Slice’N’Splice recipe slot. The fixture supplies the other recipe ingredients and near-worn tools through a hopper. The job pauses at 3,600 FE and exports one Ender Resonator for 20,000 FE total. A second restart checks the finished product and spent tools.

The 1.21.1 baseline and the initial Forge port registered the head as a simple block item. The separately registered wall block and the existing `EnderSkullBlockItem` were present, but native item placement could only select the floor block. The port now registers that existing wall-aware item under the same `enderio:enderman_head` ID. This follows the historical Forge registration and keeps the original floor and wall block IDs and their shared loot.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_forge="$port_runtime/cycle-047-legacy-discover"
port_jar=enderio/build/libs/enderio-8.2.14.187-1.20.1+1b46f8be5f.jar
port_guarded="$port_runtime/cycle-187-antenna-synced-client"
port_client="$port_runtime/cycle-188-ender-skull-verified-client"

./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-188-ender-skull-build.log 2>&1
./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-188-ender-skull-fixed-build.log 2>&1
mkdir -p "$port_client/config"
cp -cR "$port_guarded/libraries" "$port_guarded/versions" "$port_client/"
cp "$port_guarded/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name cycle-188-ender-skull-verified-client \
  --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 -u scripts/porting/packaged_ender_skull.py --name cycle-188-ender-skull-verified --port 26175 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --launch "$port_client/launch.json"
```

## Results

- The initial package produces one stock head after 27 direct attacks and passes floor placement and loot. The wall click places another floor head at the same position. The saved region palette records `enderio:enderman_head` with `rotation=8`. The guarded client times out waiting for the wall variant. See `build/port-runtime/cycle-188-ender-skull-first/ender-skull-results.json` and the saved world in that profile.
- The corrected build passes 79 ordinary unit tests and 540 native runs, including all 213 discovered main gameplay cases. A first TCP attempt on the corrected package disconnects during Forge login. The native server verifies the Dark Steel smelt before that connection. See `build/cycle-188-ender-skull-fixed-build.log` and `build/port-runtime/cycle-188-ender-skull-fixed/logs/debug.log`.
- The fresh retry passes 17 server assertions and 27 client checks across three dedicated sessions. The sword drops exactly one head on the fifth direct attack, and the server records five durability spent. The floor head has rotation 8. The wall head faces south and keeps that state after restart. Both forms render through the mod's skull renderer and drop one original item when mined. The wall drop enters the native slicer slot, completes for exactly 20,000 FE and produces one Ender Resonator. The result and the absence of the spent tools survive the second restart.
- Both peers load production SHA-256 `2d49e6c7ae7bf4985d1b0be1ba0df4ae0d5f30e0b03d876082ee60b6f878ad81`. All five captures confirm all volume categories at zero and narrator off. The native observers record zero activation, foreground or visible-window events across 6,022 samples. See `build/port-runtime/cycle-188-ender-skull-verified/ender-skull-results.json` and the two `build/port-evidence/cycle-188-ender-skull-verified-*-client/summary.json` reports.

## Next verification

Use an authentic Forge 6.2 fixture to check placed standing and wall Enderman Heads, the original item stacks, and native placement and loot after conversion.
