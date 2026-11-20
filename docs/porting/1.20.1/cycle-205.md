# Cycle 205 — Creative Power and the Ender IO creative tab

The headless fixture places the registered Creative Power block and two basic energy conduits across a chunk boundary. A wired charger receives energy through the stock conduit path. A vanilla redstone signal enables the source. The sampler reads the charger, source NBT and game time in one function. The rate measurement starts after the server observes the first change in actual charge and spans at least ten game ticks. Removing the signal stops delivery. The first dedicated restart restores the closed route and exact balance, verifies that the route remains off, and applies a new signal. A final restart checks the second saved balance.

The guarded client enters a fresh integrated creative world. It selects the actual Ender IO tab using the Forge page controls and native screen clicks. Its inventory shows Creative Power and the real search field finds it. The Ender IO icon keeps its stock texture and glint. The icon item and seven carried legacy icons stay outside the display and search rosters. The integrated server also places a Creative Power block and exercises its Forge Energy capability directly.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_jar=enderio/build/libs/enderio-8.2.14.204-1.20.1+e5e3cab3bd.jar
port_source="$port_runtime/cycle-198-pearl-powder-stock-client"
port_client="$port_runtime/cycle-205-creative-tab-stock"

./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-205-creative-power-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-205-creative-qa-rebuild.log 2>&1
python3 -u scripts/porting/packaged_creative_power.py --name cycle-205-creative-power-final --port 26211 \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar"
mkdir -p "$port_client/config"
cp -cR "$port_source/libraries" "$port_source/versions" "$port_client/"
cp "$port_source/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name cycle-205-creative-tab-stock \
  --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/client/run_smoke.py --launch "$port_client/launch.json" --scenario creative-tab \
  --output build/port-evidence/cycle-205-creative-tab-stock --timeout 180
```

## Results

- The full build passes 79 unit tests with none skipped and 540 native runs, including all 213 main gameplay cases. The final rebuilt client probe is separate from the production jar. See `build/cycle-205-creative-power-build.log` and `build/cycle-205-creative-qa-rebuild.log`.
- All ten guarded client checks pass. The Ender IO tab is on the second Forge page and a search for `creative power` returns only the registered block. The direct Forge capability advertises a capacity of 2,147,483,647 FE and a steady 1,073,741,823 FE available. A simulated maximum extraction leaves that charge stable. Real extractions of 1,073,741,823 FE and another 8,192 FE both succeed. The capability refuses input. See `build/port-evidence/cycle-205-creative-tab-stock/summary.json` and its two captures.
- Both client captures have all volume categories at zero and the narrator off. The native observer records zero activation, foreground or visible-window events across 1,159 samples.
- The final dedicated run passes 13 assertions over three clean Forge server sessions with no server or registry errors. Both ten-tick active windows deliver 80,000 FE. The first restart retains 112,000 FE and the route remains disabled until the new signal. The second restart retains 240,000 FE. The inactive windows transfer zero and the source's serialized counter stays at zero. The report is `build/port-runtime/cycle-205-creative-power-final/creative-power-results.json`. The tested production jar SHA-256 is `5c9244e1cec8d3855106f096109734d018b34e56e3b4e4e9ca4a938593ebbeaa`.
- The first headless run (`cycle-205-creative-power-stock`, port 26208) used the root field in the restart assertion. Creative Power stores that counter under `Energy.EnergyStored`. The save contained the exact sink balance and intact topology. The fixture now uses the nested path in both the sampler and the restart assertion.
- The second run (`cycle-205-creative-power-verified`, port 26209) applied the signal immediately after reopening the route and observed zero delivery during its first ten-tick sample. A fresh server opened a copy of that save with the original signal still present. The charger rose from 104,000 FE to 264,000 FE without changing the route or signal. The fixture now proves that the receiver gains charge before measuring the active rate. The diagnostic snapshots are in `build/port-runtime/cycle-205-creative-power-diagnostic/creative-power-diagnostic-results.json`.

## Next verification

Verify the remaining recipe evidence for the stock entity-bearing Powered Spawner craft. Exercise its native limited-crafting match, the stored input and output soul, and the result across restart. Reconcile that evidence with the baseline recipe serializer.
