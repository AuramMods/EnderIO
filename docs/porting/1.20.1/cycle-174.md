# Cycle 174 — Carry stock glass from machines through survival crafting

The packaged test supplies stock ingredients to six Alloy Smelters through vanilla hoppers. One produces plain clear glass and another produces plain fused quartz from quartz blocks. Their outputs feed the lower-cost dark clear glass and enlightened quartz recipes with amethyst shards and glowstone blocks. Two other machines produce dark quartz and enlightened clear glass directly. The hoppers collect the expected outputs, each machine spends the exact listed energy, and its unspent 137 FE remains.

The packaged Soul Binders take disposable villager, cow and zombie soul-vial fixtures. Each stock token recipe spends 12,800 FE and 140 mB of XP Juice and leaves one empty vial. Vanilla hoppers extract the resulting player, animal and monster tokens. The test moves those three results and the three smelted glass batches to a chest with the redstone torches and dyes.

The packaged TCP survival client takes the materials from the native chest screen. With limited crafting enabled, it unlocks each recipe by obtaining its ingredients and uses the recipe book to fill all nine crafting slots. It takes eight blocks per recipe and verifies all nine inputs are spent. Three collision-token recipes lead to three inversions and four dye recipes, including re-dyeing blue glass to cyan through the shared glass-family tag. The client checks the exact names and tooltips for the final items, places one of each, and leaves seven of each in its inventory. The server reopens the same world and checks the three placed variants and the remaining enlightened clear glass output.

The same quartz block is a valid input for both the stock fused-quartz recipe and the inherited vanilla Smooth Quartz recipe. The old lookup picked the first recipe and rejected it if the mode excluded it, leaving a Furnace-mode smelter with its input and full energy. Furnace and Alloys modes now filter candidate recipes before matching. The new native regression and packaged fixture both use the overlapping stock input.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_jar=enderio/build/libs/enderio-8.2.14.173-1.20.1+a24c63d046.jar
port_client="$port_runtime/cycle-174-fixed-client"

./gradlew :enderio:verifyGameplayTests --console=plain > build/cycle-174-mode-before.log 2>&1
./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-174-fixed-build.log 2>&1
mkdir -p "$port_client/config"
cp -cR "$port_runtime/cycle-172-fixed-core-client/libraries" \
  "$port_runtime/cycle-172-fixed-core-client/versions" "$port_client/"
cp "$port_runtime/cycle-172-fixed-core-client/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name cycle-174-fixed-client --java "$port_java" \
  --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 -u scripts/porting/packaged_glass_progression.py --name cycle-174-verified \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" \
  --jar "$port_jar" --launch "$port_client/launch.json" --port 26121 > build/cycle-174-verified.log 2>&1
```

## Results

- The first machine fixture supplied a partial block entity tag and implicitly selected Furnace mode. The saved machine and hopper inspection exposed that setup error. The recipe progression now configures the machines to Alloys and includes a dedicated Furnace-mode control. See `build/cycle-174-input-diagnostics.log`.
- The focused Furnace-mode test on the original jar accepts a quartz block through the hopper. It retains the block and all 1,637 FE with no active task or output. The native regression also finds no Smooth Quartz. See `build/cycle-174-furnace-repro.log` and `build/port-evidence/cycle-174-mode-before.xml`.
- The patched native test uses 1,500 FE for Smooth Quartz in Furnace mode and 6,400 FE for Fused Quartz in Alloys mode. The full build passes 79 unit tests and 534 native runs, including all 207 discovered main gameplay tests. See `build/cycle-174-fixed-build.log` and `enderio/build/test-results/gameTestServer/results.xml`.
- The first patched packaged attempt exposed a syntax error in the smelter setup command. Fixing the fixture's extra brace lets the server place all seven smelters. See `build/port-runtime/cycle-174-fixed/smoke-fresh.log`.
- The final packaged run passes 19 server assertions and 44 client checks. Furnace mode exports one Smooth Quartz with 137 FE left from its 1,637 FE supply. The survival player uses real machine outputs for all ten crafts, places the three named variants, and retains seven blocks from each batch. All three blocks and the hopper's direct enlightened clear glass output survive the server restart. Both server sessions and the client exit cleanly with zero runtime or registry errors. See `build/port-runtime/cycle-174-verified/glass-progression-results.json`.
- Six captures record all volume categories at zero and the narrator off. The native observer records zero activation, foreground or onscreen samples across 1,379 observations. The packaged client and server load the same jar with SHA-256 `816809cbcc3320a43e3a36a054688925d4d84a61641229db227214fe282c0689`. See `build/port-evidence/cycle-174-verified-client/`.

## Next verification

Exercise the authored translucent and shadow shader passes. Continue closing the unverified progression and lifecycle paths in the feature matrix.
