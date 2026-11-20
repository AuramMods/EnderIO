# Cycle 224 — Tank recipe transfers in JEI

JEI now checks the destination for the selected tank recipe. Fill recipes use the right input for items that consume stored fluid. Empty recipes use the left input for items that add stored fluid. Both tiers share the same menu. A matching item already in the destination satisfies the recipe, and a normal click leaves it in place. An occupied destination with an incompatible item highlights the JEI item input. Fluid errors highlight the fluid input and handle the interval before the tank's first menu update.

The packaged survival client opens both tiers through real Forge multiplayer and presses the JEI transfer button for these stock recipes. Redstone pauses each tank while the fixture checks the tagged input, the untouched fluid amount and the extra item left in the player's inventory. The server then enables the machine and verifies the result and exact fluid balance. JEI only runs on the client.

| Tank and recipe | Input | Output | Fluid before and after |
| --- | --- | --- | --- |
| Standard fill | Sponge | Wet sponge | Water 1,137 → 137 mB |
| Standard empty | Wet sponge | Sponge | Water 137 → 1,137 mB |
| Pressurized fill | Glass bottle | Bottle o’ enchanting | Experience 18,137 → 17,887 mB |
| Pressurized empty | Bottle o’ enchanting | Glass bottle | Experience 18,137 → 18,387 mB |

The diagnostic probe compares the actual JEI highlight targets with the item and fluid inputs in the recipe layout. It covers missing items for all four cases, wrong and one-millibucket-short water for the first case, and an incompatible item occupying the correct destination. The client repeats the handler check with a matching loaded input and no remaining player copy.

## Commands

```sh
./gradlew :enderio:assemble :enderio:reobfClientSmokeJar --console=plain > build/cycle-224-tank-fixed-build.log 2>&1
./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-224-tank-full-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_source="$port_runtime/cycle-223-alloy-jei-sync-client"
port_client="$port_runtime/cycle-224-tank-jei-fixed-client"
port_jar=enderio/build/libs/enderio-8.2.14.223-1.20.1+31587129a3.jar
port_qa=enderio/build/test-libraries/enderio-client-qa.jar
port_jei="$port_runtime/cycle-158-jei-final-client/mods/jei-9jqubC9n.jar"
export JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true

mkdir -p "$port_client/config"
cp -cR "$port_source/libraries" "$port_source/versions" "$port_client/"
cp "$port_source/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name cycle-224-tank-jei-fixed-client --java "$port_java" \
  --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar "$port_qa" --assets "$HOME/.gradle/caches/neoformruntime/assets" --mod "$port_jei"
python3 -u scripts/porting/packaged_tank_jei.py --name cycle-224-tank-jei-fixed --port 26280 \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar" \
  --jei "$port_jei" --launch "$port_client/launch.json"
python3 -u scripts/porting/packaged_tank_jei.py --name cycle-224-tank-core-fixed --port 26281 \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar"
```

## Results

- The first QA build reports an incorrect `FluidTankScreen` import. Correcting the import builds the separate test jar. The first baseline JEI client then disconnects during loopback login. The retry uses the same production and QA bytes in a fresh profile.
- The baseline Forge-only server passes all 13 checks and preserves the four recipes' outputs and exact fluid balances through a dedicated restart. See `build/port-runtime/cycle-224-tank-core-before/tank-jei-results.json`.
- The baseline JEI retry highlights the correct missing inputs and transfers one tagged sponge into the standard tank's fill slot. After the server removes the extra sponge from the player, JEI incorrectly reports a missing item even though the queued sponge remains. See `build/port-evidence/cycle-224-tank-jei-before-retry-client/result.json`.
- The fixed JEI client passes 41 checks and the dedicated server passes 18 assertions, including all four preloaded inputs and the exact highlights for wrong fluid, insufficient fluid, missing items and an occupied destination. Each button click transfers one tagged item to the recipe's designated input. All four jobs produce the listed balances, and the outputs and fluid totals survive restart. See `build/port-runtime/cycle-224-tank-jei-fixed/tank-jei-results.json`.
- The fixed Forge-only hopper control passes 13 assertions. It processes the same four recipes and restores the same outputs and balances after restart. See `build/port-runtime/cycle-224-tank-core-fixed/tank-jei-results.json`.
- The native observer records zero activations, foreground samples and onscreen windows over 2,630 samples. All eight client captures record ten volumes at zero and narrator `OFF`. See `build/port-evidence/cycle-224-tank-jei-fixed-client/summary.json`.
- The full Java 17 build passes 79 unit tests and 543 native runs, including all 215 discovered main gameplay cases. The build artifacts match those loaded by the packaged tests. The bundled production jar and its nested jars contain no test QA classes. See `build/cycle-224-tank-full-build.log`.

| Jar | SHA-256 |
| --- | --- |
| Baseline Ender IO | `f37b3f73b6e77dcbffd1227d01e346b093cfebb444c5fd5531aee93e86c09cbe` |
| Fixed Ender IO | `8e2dc5b58a1a507f1fa7ef53cfca6ad8eb59f6fb4af4f450d1a52620a4e434ba` |
| Fixed client QA | `b96bce8d3d620a2a05362db65600e9020b0d64ddf54c859758a0a85939a59ecd` |
| JEI `15.56.0.205` | `6b251e60a4719c874da89d750af97aa6498f6f4475e3ae79cb1288c7bf0cb65c` |

## Next verification

Exercise the VAT's JEI transfer in a packaged survival menu. Measure ordered reagent placement and exact fluid consumption. Verify the highlights for missing reagents and the distinction between matching fluid a millibucket short and enough fluid to begin the job.
