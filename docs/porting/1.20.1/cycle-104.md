# Cycle 104 — Restore ready Enchanter recipes after restart

## Scope

Use the real Enchanter menu over survival TCP on packaged Forge. A fresh Sharpness II recipe must reject normal click and shift-click at 10 XP levels, then charge the full cost after the player reaches 30 levels. A second ready Enchanter is saved before the first restart. Confirm that a full inventory keeps its recipe intact, free one slot, and collect the same result by shift-click. Check both paid machines after a second restart.

## Changes

- Rebuild the cached recipe and derived preview when the Enchanter attaches to a level. The saved inventory remains the source for the ingredients.
- Refresh the same state after collection so the preview and cost reflect the consumed book and remaining catalyst and lapis. Keep the existing cost formula and menu actions.

## Commands

```sh
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-104-qa.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-104-baseline-qa.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_prior=build/port-runtime/cycle-103-core-client
port_qa=enderio/build/test-libraries/enderio-client-qa.jar

prepare_client() {
  python3 scripts/porting/client/prepare_packaged.py --name "$1" --java "$port_java" \
    --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$2" \
    --qa-jar "$port_qa" --assets "$HOME/.gradle/caches/neoformruntime/assets"
}

run_enchanter() {
  python3 scripts/porting/packaged_enchanter.py --name "$1" --port "$2" \
    --java "$port_java" --forge-server "$port_forge" --jar "$port_client/mods/enderio.jar" \
    --launch "$port_client/launch.json"
}

port_client=build/port-runtime/cycle-104-before-client
mkdir -p "$port_client"
cp -R "$port_prior/libraries" "$port_prior/versions" "$port_client/"
prepare_client cycle-104-before-client "$port_prior/mods/enderio.jar"
run_enchanter cycle-104-before 25895

./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-104-diagnostic-qa.log 2>&1
prepare_client cycle-104-before-client "$port_prior/mods/enderio.jar"
run_enchanter cycle-104-diagnostic 25896

./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-104-build.log 2>&1
port_jar=enderio/build/libs/enderio-8.2.14.103-1.20.1+9f0c026860.jar
port_client=build/port-runtime/cycle-104-fixed-client
mkdir -p "$port_client"
cp -R "$port_prior/libraries" "$port_prior/versions" "$port_client/"
prepare_client cycle-104-fixed-client "$port_jar"
run_enchanter cycle-104-fixed 25897
```

## Results

- The first baseline joined but timed out waiting for the prepared menu state. The diagnostic run used the same production jar with better timeout reporting and reached both menus. See `build/port-runtime/cycle-104-before/enchanter-results.json`.
- The baseline diagnostic fresh menu correctly rejects both clicks at 10 levels and spends 11 of 30 levels, one writable book, 24 of 25 quartz, and six of 10 lapis to collect one Sharpness II book. The server saves 19 levels and the original book in the player inventory.
- After restart the baseline shows the saved Sharpness II preview and cost. With a free destination slot, the server still refuses to release the book. The four saved slots remain intact, including all ingredients and the preview. See `build/port-runtime/cycle-104-diagnostic/enchanter-results.json` and `smoke-reopened.log` in the same directory.
- The full build passes 79 JUnit and 483 Forge GameTest runs, including all 161 discovered gameplay tests. The Forge 47.4.28 production jar SHA-256 is `9ddfc2af798cab1e37cec595ecc6b40816971a5df9cd55993be6a880c870cfae`. See `build/cycle-104-build.log`.
- The fixed run passes nine server assertions and 17 client checks. The first client collects a Sharpness II book by normal click. After the first restart, the player still has that book and 19 levels. The full inventory blocks shift-click with the second recipe intact. Freeing one slot allows the second book to be collected for exactly 11 levels, one writable book, 24 quartz and six lapis. The player ends with two books and eight levels. Both Enchanters keep exactly one quartz and four lapis, with empty book and output slots after the second restart. All three dedicated servers exit at zero without runtime or registry errors. See `build/port-runtime/cycle-104-fixed/enchanter-results.json`.
- The guarded clients recorded zero activation, foreground or on-screen events in 2,050 native samples. The four captures verify narrator off and all volume categories at zero. See `build/port-evidence/cycle-104-fixed-exercise-client` and `build/port-evidence/cycle-104-fixed-verify-client`.

## Remaining verification

Other enchantment recipes, live recipe replacement, customized cost display, and survival break, pickup and replanting still need packaged coverage.
