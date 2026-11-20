# Cycle 226 — Shared reagent stocks in VAT transfers

JEI now plans both VAT reagent slots together. One item cannot satisfy two empty slots. When one reagent has an alternative, the transfer can reserve the common item for the other reagent. Shift transfer chooses the largest number of complete batches that the available stock and slot limits allow. Existing reagents stay in place, and the transfer keeps different item tags in separate stacks.

The client sends the menu ID, recipe ID and transfer mode. The server resolves the current recipe and checks the open menu and its actual fluid before applying its own allocation to the real inventory. The main channel advances to `forge-4`, so the client and server use matching builds.

| Fixture | Starting items | Expected queued items |
| --- | --- | --- |
| Choice | One copper ingot accepted by both tags, then one gold ingot accepted by the first tag | Reject the single item. After gold is supplied, place one gold in the first slot and one copper in the second |
| Shared stack | Five identical tagged copper ingots | Two per slot. Leave one in the player inventory |
| Different tags | Two stacks of three copper ingots with different tags | Three per slot. Preserve both tags |
| Preloaded | Three tagged copper ingots in the first slot and seven with a different tag in the player inventory | Keep the preloaded stack, move three into the second slot and leave four with the player |
| Capacity | 129 identical tagged copper ingots across three inventory slots | Cap both slots at 64 and leave one with the player |

The guarded client presses the recipe button for the normal transfer and calls the native JEI operation that the button uses when Shift is held. The dedicated server verifies the actual paused inventory and fluid balances. Four sites then process the stocked batches, and the capacity site retains its full queue. The runner rechecks all five sites after a dedicated restart.

## Commands

```sh
./gradlew :enderio:assemble :enderio:reobfClientSmokeJar --console=plain > build/cycle-226-vat-fixed-capacity-build.log 2>&1
./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-226-vat-full-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-226-vat-verified-qa.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-226-vat-synced-qa.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_source="$port_runtime/cycle-225-vat-jei-retry-client"
port_jar=enderio/build/libs/enderio-8.2.14.225-1.20.1+0ee68691b8.jar
port_qa=enderio/build/test-libraries/enderio-client-qa.jar
port_jei="$port_runtime/cycle-158-jei-final-client/mods/jei-9jqubC9n.jar"
export JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true

run_vat_profile() {
  port_name="$1"
  port_number="$2"
  port_script="$3"
  port_client="$port_runtime/$port_name-client"
  mkdir -p "$port_client/config"
  cp -cR "$port_source/libraries" "$port_source/versions" "$port_client/"
  cp "$port_source/config/fml.toml" "$port_client/config/"
  python3 scripts/porting/client/prepare_packaged.py --name "$port_name-client" --java "$port_java" \
    --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
    --qa-jar "$port_qa" --assets "$HOME/.gradle/caches/neoformruntime/assets" --mod "$port_jei"
  python3 -u "scripts/porting/$port_script" --name "$port_name" --port "$port_number" \
    --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar" \
    --jei "$port_jei" --launch "$port_client/launch.json"
}
run_vat_profile cycle-226-vat-shared-synced 26292 packaged_vat_jei_shared.py
run_vat_profile cycle-226-vat-regression 26290 packaged_vat_jei.py
python3 -u scripts/porting/packaged_vat_jei.py --name cycle-226-vat-core --port 26289 \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar"
```

## Results

- The baseline opens the packaged survival menu and fails the single-item highlight check. Both reagent tags independently see the same copper ingot. See `build/port-evidence/cycle-226-vat-before-client/result.json`.
- The first fixed client passes every queue check. The server's last live inventory selector runs after the client disconnects. The saved player inventory contains one tagged copper ingot in the original slot, and the saved capacity VAT contains two tagged stacks of 64 and the original 937 mB of water. The corrected QA client waits for the server to confirm the live inventory before closing. See `build/port-runtime/cycle-226-vat-shared-fixed`.
- The first acknowledgment run checks the shortage as soon as the menu exposes a tank object. The menu initially exposes an empty placeholder. The final QA client waits for the fixture's synchronized 937 mB of water before assessing which reagent JEI highlights. See `build/port-evidence/cycle-226-vat-shared-retry-client/result.json`.
- The full Java 17 build passes 79 unit tests and 543 native runs, including all 215 discovered main gameplay cases. The production jar matches the bytes used in the packaged profiles. See `build/cycle-226-vat-full-build.log`.
- The Forge-only hopper control passes seven assertions across processing and restart. It produces the same stock and reversed authored VAT results verified in cycle 225. See `build/port-runtime/cycle-226-vat-core/vat-jei-results.json`.
- The packaged cycle 225 regression passes 26 client checks and nine server assertions on the updated channel. The stock and reversed JEI transfers keep their amount checks, reagent tags, exact outputs and restart behavior. See `build/port-runtime/cycle-226-vat-regression/vat-jei-results.json`.
- The final shared-stock run passes 38 client checks and 17 server assertions. Each world state and player remainder matches the table. The four released vats consume 100, 200, 300 and 300 mB of water respectively and produce the same amounts of Hootch. The capacity VAT restores both full tagged stacks and its 937 mB of water after restart. See `build/port-runtime/cycle-226-vat-shared-synced/vat-shared-jei-results.json`.
- The two successful clients record zero activations, foreground samples and onscreen windows across 4,765 samples. All 14 captures record ten volumes at zero and narrator `OFF`. The production jar and its nested jars contain no QA classes. See `build/port-evidence/cycle-226-vat-shared-synced-client/summary.json` and `build/port-evidence/cycle-226-vat-regression-client/summary.json`.

| Jar | SHA-256 |
| --- | --- |
| Baseline Ender IO | `bbe79987e19ec0f046be09eb359cba70ead551ff90348605e137172cb0474cb7` |
| Fixed Ender IO | `ae515aff1a332b31a6fae19248d7e36ab9c1bf7d3fdba42fedd37fc3b1f31689` |
| Final client QA | `14b1f5ede19a1e9195d764bf086e4521ea8f1b1061e70195a0b6eeab178e287b` |
| JEI `15.56.0.205` | `6b251e60a4719c874da89d750af97aa6498f6f4475e3ae79cb1288c7bf0cb65c` |

## Next verification

Exercise Soul Binder recipe lookup and transfer in a packaged survival menu. Use filled vials with different souls and confirm that JEI selects the correct vial and that the bound result, exact resource costs and returned vial survive restart.
