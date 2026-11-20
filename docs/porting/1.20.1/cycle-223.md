# Cycle 223 — Sized alloy recipes in JEI

The packaged client opens the powered Alloy Smelter in survival and uses the JEI transfer button. The stock red-dye recipe displays and requires one beetroot, three clay balls and six eggs. The fixture gives the player tagged inputs in reverse order, with one extra of each item. It checks the displayed counts, the three machine slots and the player's surplus before powering the job. The stock job consumes 1,600 FE and sends twelve red dye to the output hopper.

The same session reloads a datapack that changes a recipe's ingredients, quantities, cost and result. The test checks that the JEI ingredient and output lookups follow the replacement. The server measures the new task after 500 FE, supplies the remaining 1,300 FE and checks the tagged quartz output. A Forge-only server feeds the same recipes from a vanilla hopper. Both profiles check saved results and the capacitor after a dedicated restart.

## Commands

```sh
./gradlew :enderio:assemble :enderio:reobfClientSmokeJar --console=plain > build/cycle-223-alloy-baseline-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-223-alloy-qa.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-223-alloy-final-qa.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_source="$port_runtime/cycle-222-ultimine-core-retry-client"
port_client="$port_runtime/cycle-223-alloy-jei-sync-client"
port_jar=enderio/build/libs/enderio-8.2.14.222-1.20.1+8d4aab78d0.jar
port_qa=enderio/build/test-libraries/enderio-client-qa.jar
port_jei="$port_runtime/cycle-158-jei-final-client/mods/jei-9jqubC9n.jar"
export JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true

mkdir -p "$port_client/config"
cp -cR "$port_source/libraries" "$port_source/versions" "$port_client/"
cp "$port_source/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name cycle-223-alloy-jei-sync-client --java "$port_java" \
  --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar "$port_qa" --assets "$HOME/.gradle/caches/neoformruntime/assets" --mod "$port_jei"
python3 -u scripts/porting/packaged_alloy_jei.py --name cycle-223-alloy-jei-sync --port 26276 \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar" \
  --jei "$port_jei" --launch "$port_client/launch.json"
python3 -u scripts/porting/packaged_alloy_jei.py --name cycle-223-alloy-core-hopper --port 26274 \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar"
```

## Results

- The first Forge-only fixture directs `/item replace` at the machine. Forge reports that the target is not a vanilla container. The corrected fixture uses a vanilla input hopper and verifies that it empties into the machine.
- The first JEI run passes the stock display, transfer and processing checks. It also finds the reloaded inputs and sees the replacement in JEI’s lookup. Its QA jar was compiled before the custom output assertion switched from a boolean to the actual string tag. The client stops at that stale assertion. The fresh QA build validates the string tag from the recipe fixture.
- The Forge-only hopper control passes seven server assertions. The server exports twelve red dye and two tagged quartz for exactly 1,600 and 1,800 FE respectively. Both output stacks and the Octadic Capacitor remain after restart. See `build/port-runtime/cycle-223-alloy-core-hopper/alloy-jei-results.json`.
- The second JEI run transfers both recipes and passes all six pre-restart server assertions. The test reads the menu on the first render frame after the server writes its completion marker, and its slot check fails. The final test allows up to 100 client ticks for slots, energy and progress to reach the expected values and captures the observed state.
- The final JEI run passes 32 client checks and all seven server assertions. The stock job preserves one tagged item of each type in the player's inventory. The replacement displays and transfers four paper, two kelp and three amethyst shards without losing their tags. The client observes the empty inputs and output after processing. Both server sessions exit cleanly, and the original output, replacement output and capacitor survive dedicated restart. See `build/port-runtime/cycle-223-alloy-jei-sync/alloy-jei-results.json`.
- The native observer records zero activations, foreground samples and onscreen windows across 2,216 samples. The five client captures record all ten volumes at zero and the narrator `OFF`. The client and server use matching Ender IO and JEI jars. The bundled production jar and its nested jars contain no QA classes. See `build/port-evidence/cycle-223-alloy-jei-sync-client/summary.json`.

| Jar | SHA-256 |
| --- | --- |
| Ender IO `8.2.14.222-1.20.1+8d4aab78d0` | `98568157a80a4f788ffe416f6547db7053ca6c2de0bd0d6907bc9d0f850e5877` |
| JEI `15.56.0.205` | `6b251e60a4719c874da89d750af97aa6498f6f4475e3ae79cb1288c7bf0cb65c` |
| Client QA | `a5e5ae81494468f71b18bbc29aea5a201a49513b70814b54a9d2fe478573e9c3` |

## Next verification

Exercise the native JEI transfer button for both fill and empty Tank recipes on the standard and pressurized machines. Cover already-loaded inputs and the correct slot highlights for missing item or fluid.
