# Cycle 234 — Completed VAT batches after a reagent swap

Two stock VATs finish a 400-tick Cloud Seed fermentation behind full 8,000 mB output tanks. Each has 1,234 mB of tagged water and the original two reagents. One has Blue Ice in the right slot and queues 81,000 mB. The other has a snowball in the left slot and queues 110 mB. Both save their completed jobs and restart on the packaged dedicated server.

A survival player uses the native hotbar swap to exchange Blue Ice and snowballs in the two vats. The recipe stays the same and each job keeps its 400 ticks of progress. The queued yield follows the current reagents. Receiver tanks free exactly 109 mB of space in each vat. The new 110 mB batch waits for one more millibucket. The new 81,000 mB batch commits, fills the available space and queues its remaining 80,891 mB. Each job consumes its current reagent pair and 1,000 mB of water once. The queued remainder resumes delivery after the next restart.

The task checks the current recipe inputs and calls the same yield calculation as recipe crafting. When the amount changes, it updates the queued result without resetting the completed progress. Output already committed to the pending fluid queue continues to drain independently of any later ingredient changes.

## Commands

```sh
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew :enderio:reobfClientSmokeJar --max-workers=4 --console=plain
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew :enderio:assemble :enderio:reobfClientSmokeJar --max-workers=4 --console=plain
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew build :enderio:reobfClientSmokeJar --continue --max-workers=2 --console=plain

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_seed="$port_runtime/cycle-233-sag-fixed-retry-client"
export JAVA_TOOL_OPTIONS='-Djava.net.preferIPv4Stack=true -XX:ActiveProcessorCount=4'

run_vat_profile() {
  port_name="$1"
  port_number="$2"
  port_jar="$3"
  port_qa="$4"
  port_runner="${5:-packaged_vat_backpressure.py}"
  port_client="$port_runtime/$port_name-client"
  mkdir -p "$port_client/config"
  cp -cR "$port_seed/libraries" "$port_seed/versions" "$port_client/"
  cp "$port_seed/config/fml.toml" "$port_client/config/"
  python3 scripts/porting/client/prepare_packaged.py --name "$port_name-client" --java "$port_java" \
    --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" --qa-jar "$port_qa" \
    --assets "$HOME/.gradle/caches/neoformruntime/assets"
  python3 -Bu "scripts/porting/$port_runner" --name "$port_name" --port "$port_number" \
    --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar" \
    --launch "$port_client/launch.json"
}

run_vat_profile cycle-234-vat-before 26326 build/cycle-234-artifacts/before.jar build/cycle-234-artifacts/qa.jar
run_vat_profile cycle-234-vat-fixed 26327 build/cycle-234-artifacts/fixed.jar build/cycle-234-artifacts/fixed-qa.jar
python3 -Bu scripts/porting/packaged_vat_automation.py --name cycle-234-vat-stock-regression --port 26328 \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar build/cycle-234-artifacts/fixed.jar
run_vat_profile cycle-234-vat-controls-regression 26329 build/cycle-234-artifacts/fixed.jar \
  build/cycle-234-artifacts/fixed-qa.jar packaged_vat_controls.py
```

## Results

- The baseline keeps the old yield in both task records even though the open menu shows the new reagent multiplier. At 109 mB of space, the snowball vat spends the tagged water and both current items on an 81,000 mB batch and holds an 80,891 mB remainder. The reversed Blue Ice vat still queues 110 mB and leaves both reagents unspent. Native menu checks agree with the server's inventory and fluid balances. See `build/port-runtime/cycle-234-vat-before/smoke-reopened.log`, `build/port-runtime/cycle-234-vat-before/vat-backpressure-results.json` and `build/port-evidence/cycle-234-vat-before-client/summary.json`.
- The fixed run passes 16 server assertions and 13 client checks across three clean server launches. Both swaps preserve the original tagged item in the player's hotbar. The low-yield batch keeps both inputs at 109 mB free and consumes them once at 110 mB. The high-yield batch spends its inputs at 109 mB free and retains an exact 80,891 mB remainder. Each vat retains 234 mB of tagged water. After restart an empty pressurized receiver takes 32,000 mB from the saved batch and leaves 48,891 mB pending. See `build/port-runtime/cycle-234-vat-fixed/vat-backpressure-results.json`.
- The guarded client records zero activations, foreground samples and onscreen samples across 2,327 observations. All six captures record all volumes at zero and narrator off. Both peers use the same production jar. See `build/port-evidence/cycle-234-vat-fixed-client/summary.json`.
- The Forge-only stock regression passes 17 assertions across three clean server launches. All ten shipped fluid recipes conserve their exact batches through native sided transfers. The authored blocked job spends the correct input after restart, exports the previous output separately and retains its tagged scaled result through another restart. See `build/port-runtime/cycle-234-vat-stock-regression/vat-automation-results.json`.
- The packaged redstone and tooltip regression passes seven server assertions and 17 client checks across two clean launches. The open VAT preserves its 57,000 mB committed queue while paused. Physical redstone resumes delivery and leaves 25,000 mB waiting through restart. The hidden client records zero activations, foreground samples and onscreen samples across 1,840 observations. All four captures confirm zero volume and narrator off. See `build/port-runtime/cycle-234-vat-controls-regression/vat-controls-results.json` and `build/port-evidence/cycle-234-vat-controls-regression/summary.json`.
- The full Gradle build passes 79 unit cases and 543 required Forge test runs. The final build jar matches the tested SHA-256 `b7ff21f96258b5f8f4801b7f5dc5748605521a980835fdbaf2e330a37482c614`. Its outer and nested jars contain no client smoke classes. See `build/cycle-234-vat-full-build.log`.

## Next verification

Verify that a packaged multiplayer client renders a second player's Hang Glider in its deployed and stowed states. Check the remote view after a reconnect with the glider still equipped.
