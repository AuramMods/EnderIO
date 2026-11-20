# Cycle 270 — Stock VAT reagent matrix

The fixture compares all ten packaged recipes with the fixed 1.21.1 baseline and matches all 15 reagent modifier entries against the live Forge data map. It expands the two active item tags in each recipe and tries every pair in both physical slot orders. Water recipes use source water. Fluid-specific recipes use their original Ender IO fluid. The stock Firework Rocket substitution supplies the concentrated Cloud Seed recipe on Forge 1.20.1.

The first hopper supplies a marked stack and the server verifies slot zero before placing the second hopper. The input tank receives the exact recipe quantity plus 137 mB. A native redstone signal starts fermentation and removing it pauses each job. The fixture checks the recipe ID, time requirement, saved progress, two marked reagent stacks and exact queued yield before and after cold restart. After resuming, each output travels through the VAT's south face to a pressurized tank. The Blue Ice batches each use three receiving tanks to export their 81,000 mB. The final cold restart checks all receiving tanks, the input reserves, and empty reagent slots and hoppers.

| Stock recipe output | Reagent orders | Yield per batch |
| --- | ---: | ---: |
| Concentrated Cloud Seed | 2 | 500 mB |
| Cloud Seed | 12 | 110–81,000 mB |
| Dew of the Void | 2 | 1,000 mB |
| Fire Water | 2 | 1,000 mB |
| Hootch | 100 | 125–250 mB |
| Liquid Darkness | 2 | 1,000 mB |
| Liquid Sunshine | 28 | 150–1,200 mB |
| Nutrient Distillation | 220 | 125–250 mB |
| Rocket Fuel | 10 | 250–1,000 mB |
| Vapor of Levity | 2 | 1,000 mB |

## Commands

```sh
export JAVA_HOME="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
export CI=true JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=4
./gradlew --no-daemon --no-build-cache --no-configuration-cache --max-workers=2 --console=plain :enderio:assemble

export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Djava.net.preferIPv4Stack=true'
port_runtime=build/port-runtime
port_forge="$port_runtime/cycle-047-legacy-discover"
port_jar=enderio/build/libs/enderio-8.2.14.269-1.20.1+24369b7eff.jar
port_probe="$port_runtime/cycle-269-current-probe/registry-probe.jar"
python3 -B scripts/porting/packaged_vat_stock.py --name cycle-270-vat-stock --port 26426 \
  --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" --jar "$port_jar" --probe "$port_probe"
python3 -B scripts/porting/packaged_vat_automation.py --name cycle-270-vat-automation --port 26427 \
  --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" --jar "$port_jar"
```

## Results

- Java 17 assembly passes. The runtime jar SHA-256 is `dd6aee4a878475842594d4bedc2222020b61370295d6e1e7b651d2d642870974`. The registry probe from [Cycle 269](cycle-269.md) has SHA-256 `07a9e7eab2cd50f3936c0f4f3a84fc4ba5243d3e536e627aa49ea25e3d8f9cbc`. See `build/port-evidence/cycle-270-vat-stock/build.log`.
- The stock fixture passes 2,697 assertions. All 380 jobs stop and reopen at exactly 62 ticks. They consume 385,000 mB of stock input and export 289,300 mB of product. Every VAT retains its original 137 mB of excess fluid and the second restart leaves all receiving balances intact. Each Blue Ice run fills two 32,000 mB tanks and one 17,000 mB tank. See `build/port-runtime/cycle-270-vat-stock/vat-stock-results.json` and `registry-snapshot.json`.
- The existing automation fixture passes all 17 assertions on the same jar. Its blocked authored batch resumes and the two receivers retain 70 mB of the previous marked Hootch and 450 mB of the new Hootch with its typed tag. All six server sessions run headlessly and exit at zero with zero runtime or registry errors. See `build/port-runtime/cycle-270-vat-automation/vat-automation-results.json`.

## Next verification

Repeat the final Java 17 distribution, datagen and baseline test reconciliation on a fresh candidate that includes the changes since cycle 255. [Completion audit](completion-audit.md)
