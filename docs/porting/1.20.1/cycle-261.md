# Cycle 261 — VAT redstone modes and partial fermentation

The packaged Forge fixture uses four stock VAT recipes with alternate reagents. Vanilla hoppers supply the marked reagent stacks, the north tank feeds the input, and the south face exports the result to a separate tank. Redstone blocks above the vats change the actual neighbor signal without affecting the connected faces.

| Control mode | Stock reagents | Sided output |
| --- | --- | --- |
| Active without signal | Gunpowder and redstone | 250 mB Rocket Fuel |
| Active with signal | Snow and prismarine shard | 120 mB Cloud Seed |
| Always active | Cooked chicken and sugar cane | 125 mB Nutrient Distillation |
| Never active, then enabled | Sunflower and glow berries | 150 mB Liquid Sunshine |

The two signal-sensitive vats pause at their measured partial progress and keep both inputs, the original 1,000 mB of feedstock and the exact queued result. The test waits 60 loaded ticks, stops and restarts the server, then waits another 45 loaded ticks with the same signal states. Removing the two force-load tickets proves both chunks are unloaded before the test loads them again. Each VAT resumes on its next permitted signal edge. The early sample exceeds the number of ticks elapsed since release, so a reset to zero would fail.

The always-active VAT progresses while the signal is applied and after removal. The disabled VAT accepts the hopper and tank inputs and retains them with and without a signal. Changing its saved mode to always active releases that batch. A final restart verifies that all four receivers keep their exact outputs and all vats remain empty.

## Commands

```sh
export JAVA_HOME="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
export CI=true JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=4
./gradlew --no-daemon --no-build-cache --no-configuration-cache --max-workers=2 --console=plain :enderio:assemble

export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Djava.net.preferIPv4Stack=true'
port_forge=build/port-runtime/cycle-047-legacy-discover
port_jar=enderio/build/libs/enderio-8.2.14.260-1.20.1+785a1eb42b.jar
python3 -B scripts/porting/packaged_vat_redstone.py --name cycle-261-vat-redstone-final --port 26394 \
  --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" --jar "$port_jar"
python3 -B scripts/porting/packaged_vat_automation.py --name cycle-261-vat-stock-regression --port 26393 \
  --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" --jar "$port_jar"
```

## Results

- An initial assertion expected a disabled VAT to have no task at all. Inspecting the reopened block confirms it can resolve a matching recipe with `ProgressMade:0`, `HasDeterminedOutputs:0b`, both marked stacks and all 1,000 mB of water. The final fixture checks the actual progress and the untouched inputs. See `build/port-evidence/cycle-261-vat-redstone/idle-task.log`.
- The Java 17 package assembles from `785a1eb42`. The tested jar SHA-256 is `5aa0a23b8df73f604aa4a86089504ab1f5ee29904900bb5230adc20a01af564c`. See `build/port-evidence/cycle-261-vat-redstone/assemble.log`.
- All 39 assertions pass across three headless launches. The Rocket Fuel and Cloud Seed jobs retain exactly 90 and 85 of 400 ticks through restart and eviction. On the next permitted signal edge they reach 108 and 103 ticks within 20 server ticks. All four amounts in the table survive the final restart. See `build/port-runtime/cycle-261-vat-redstone-final/vat-redstone-results.json`.
- The existing full VAT automation fixture passes all 17 assertions on the same package. It exports all ten stock products and retains both separately tagged batches through restart. Both fixtures exit at zero across all six launches with no registry or runtime errors. See `build/port-runtime/cycle-261-vat-stock-regression/vat-automation-results.json`.

## Next verification

Exercise another capacitor-bank connected-texture combination in the packaged client. [Completion audit](completion-audit.md)
