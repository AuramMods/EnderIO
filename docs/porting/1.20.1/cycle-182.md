# Cycle 182 — Stock alloy production and survival crafting

Native hoppers load the Alloy Smelters and collect the outputs. The fixture supplies starting materials and an exact FE budget to each machine. The first group includes Redstone Alloy, Pulsating Alloy, Dark Steel, both Soularium ingredients, Conductive Alloy, Energetic Alloy and Vibrant Alloy. The End Steel job consumes ten original Dark Steel ingots from one of the other outputs. All ten jobs are configured to retain 137 FE when the original inputs have been consumed. The separate starter progression already tests the Conductive to Energetic to Vibrant dependency chain. [Cycle 155](cycle-155.md)

The survival client collects the actual alloys from one chest and uses the recipe book with `doLimitedCrafting` enabled. The previous jar left Redstone Alloy block crafting locked even with all nine ingots in the player's inventory. The shared generator used the finished block to unlock compression and the resulting ingot to unlock nugget consolidation. It now uses the ingot and nugget inputs respectively, and native datagen updates the 16 affected advancements.

The final scenario covers all eight materials. It packs a block, unpacks it, splits an ingot into nuggets, consolidates those nuggets, and crafts the block again. It checks that consolidation stays locked until the nuggets are obtained. The player places eight blocks. The server reopens the world and checks the placed blocks, machine FE and saved inventory.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_forge="$port_runtime/cycle-047-legacy-discover"
port_jar=enderio/build/libs/enderio-8.2.14.181-1.20.1+63b6ad792b.jar
port_guarded="$port_runtime/cycle-181-igniter-final-client"
port_client="$port_runtime/cycle-182-alloys-verified-client"

./gradlew :enderio:runData --console=plain > build/cycle-182-alloys-datagen.log 2>&1
./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-182-alloys-final-build.log 2>&1
mkdir -p "$port_client/config"
cp -cR "$port_guarded/libraries" "$port_guarded/versions" "$port_client/"
cp "$port_guarded/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name cycle-182-alloys-verified-client \
  --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 -u scripts/porting/packaged_alloy_progression.py --name cycle-182-alloys-verified --port 26157 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --launch "$port_client/launch.json"
```

## Results

- The initial headless check ran the producer directly on the previous production jar and passed nine assertions, including stock recipes for both Soularium inputs and the connection from Dark Steel to End Steel. See `build/cycle-182-alloys-headless.log`.
- The previous packaged client collected the seven-job fixture's exact outputs, opened the table and failed with `Recipe enderio:redstone_alloy_block stayed locked with 9 of redstone_alloy_ingot`. See `build/port-evidence/cycle-182-alloys-before-client/result.json`. The independent observer recorded zero activation or foreground events during that run.
- Native datagen completes and changes only the 16 applicable generated advancement criteria. All corresponding recipe IDs, input counts and results remain as shipped.
- The final full build passes 79 unit cases and 540 native runs, including all 213 discovered main gameplay cases.
- The final packaged run passes 15 server assertions and 144 client checks. Ten jobs spend 272,800 FE in total, with 137 FE left in each machine. The chest contains 89 smelted ingots. The survival client runs 40 crafting operations across 32 distinct stock recipe IDs. One placed block of each material and exactly 17 remaining ingots account for the input. The restarted world retains all eight placed blocks, all ten machine balances, and the exact saved player inventory. Both server stages exit with zero runtime or registry errors.
- Both peers load production SHA-256 `93aa5527021b257d97e02170e594c1ecbf069b2e8d5e11f3611ae26f01d96038`. The guarded client records no activation, foreground or visible-window events across 1,722 samples. Its three captures show all volume categories at zero and narrator off. See `build/port-runtime/cycle-182-alloys-verified/alloy-progression-results.json` and `build/port-evidence/cycle-182-alloys-verified-client/summary.json`.

## Next verification

Use the stock alloy grinding balls in the SAG Mill to check their modifiers and wear. The progression fixture now proves where the alloy ingredients come from.
