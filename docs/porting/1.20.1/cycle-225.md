# Cycle 225 — VAT recipe transfers in JEI

JEI uses the VAT recipe's sized fluid predicate. It accepts a source at the exact threshold and highlights the fluid input when the source is wrong or even one millibucket short. The transfer handler checks both reagent orders that the machine supports. It keeps a matching preloaded reagent in place and queues the complement in the other slot. Missing highlights refer to the recipe's displayed inputs even when the machine uses the reverse order.

The category now cycles fluid alternatives inside one gauge. A flowing variant appears when the recipe accepts it on its own. When the source is also accepted, the gauge shows the source. The output gauge retains the recipe's original fluid tag.

The packaged client uses the native JEI transfer button for the stock Nutrient Distillation recipe and an authored recipe with two accepted fluids. The stock test supplies exactly 1,000 mB of water and transfers tagged beef and wheat into the displayed order. The authored test starts with 537 mB of lava and preloaded gold in the left slot. JEI transfers tagged copper into the right slot. The server keeps each machine paused while the runner checks the player's surplus items and the queued inputs. Redstone starts the jobs and the runner checks the exact batch outputs and saved state after restart. JEI runs only on the multiplayer client.

| Fixture | Consumed fluid | Remaining fluid | Produced |
| --- | --- | --- | --- |
| Stock Nutrient Distillation | 1,000 mB water | Empty input tank | 250 mB Nutrient Distillation |
| Reversed authored batch | 500 mB lava | 37 mB lava | 540 mB tagged Hootch from a 480 mB base and multipliers of 1.5 and 0.75 |

## Commands

```sh
./gradlew :enderio:assemble :enderio:reobfClientSmokeJar --console=plain > build/cycle-225-vat-fixed-build.log 2>&1
./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-225-vat-full-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_source="$port_runtime/cycle-224-tank-jei-fixed-client"
port_client="$port_runtime/cycle-225-vat-jei-retry-client"
port_jar=enderio/build/libs/enderio-8.2.14.224-1.20.1+23598edd9d.jar
port_qa=enderio/build/test-libraries/enderio-client-qa.jar
port_jei="$port_runtime/cycle-158-jei-final-client/mods/jei-9jqubC9n.jar"
export JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true

mkdir -p "$port_client/config"
cp -cR "$port_source/libraries" "$port_source/versions" "$port_client/"
cp "$port_source/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name cycle-225-vat-jei-retry-client --java "$port_java" \
  --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar "$port_qa" --assets "$HOME/.gradle/caches/neoformruntime/assets" --mod "$port_jei"
python3 -u scripts/porting/packaged_vat_jei.py --name cycle-225-vat-jei-retry --port 26286 \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar" \
  --jei "$port_jei" --launch "$port_client/launch.json"
python3 -u scripts/porting/packaged_vat_jei.py --name cycle-225-vat-core-fixed --port 26285 \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar"
```

## Results

- The baseline client opens the native VAT and finds the two fixture recipes and all ten stock entries. At 999 mB water with both reagents missing, the transfer error fails the exact three-input highlight check. See `build/port-evidence/cycle-225-vat-jei-before-client/result.json`.
- The baseline Forge-only control passes all seven assertions. Both stock and authored recipes process at the expected amounts and survive restart. See `build/port-runtime/cycle-225-vat-core-before/vat-jei-results.json`.
- The fixed Forge-only control passes all seven assertions and repeats both exact outputs through restart. The first fixed JEI client disconnects before joining. The retry launches a fresh profile with the same production and QA bytes.
- The fixed retry passes 26 client checks and nine server assertions. JEI highlights precisely the missing inputs, transfers both tagged stock reagents, and adds only tagged copper next to the preloaded gold. The authored recipe shows two fluids in one slot, retains its typed output tag and displays multipliers of 1.5 and 0.75. Both jobs keep their exact outputs and saved fluid balances through restart. See `build/port-runtime/cycle-225-vat-jei-retry/vat-jei-results.json` and `build/port-runtime/cycle-225-vat-core-fixed/vat-jei-results.json`.
- The native observer records zero activations, foreground samples and onscreen windows across 3,584 samples. All four client captures record ten volumes at zero and the narrator `OFF`. The bundled production jar and its nested jars contain no test QA classes. See `build/port-evidence/cycle-225-vat-jei-retry-client/summary.json`.
- The Java 17 full build passes 79 unit tests and 543 native runs, including all 215 discovered main gameplay cases. Its production and QA artifacts match the pinned packaged test bytes. See `build/cycle-225-vat-full-build.log`.

| Jar | SHA-256 |
| --- | --- |
| Baseline Ender IO | `3c77155a70396f7c11b1f430e5e40fb9ea70769648f82f70b5b6a53bf71cff1c` |
| Fixed Ender IO | `252604a82656f64c4ad483e8fb118a61ac41b8d1782000c1b68e00fc14fa90d1` |
| Client QA | `269a022afcf74dfb4b4680a20b425e0949366ee986d68bea87626de91198b36c` |
| JEI `15.56.0.205` | `6b251e60a4719c874da89d750af97aa6498f6f4475e3ae79cb1288c7bf0cb65c` |

## Next verification

Exercise recipes with overlapping reagent tags and finite shared item stocks. Check that one item is insufficient for two empty slots, that a different valid candidate is selected when both reagents can use the first stack, and that shift transfer leaves enough stock in each slot for complete batches.
