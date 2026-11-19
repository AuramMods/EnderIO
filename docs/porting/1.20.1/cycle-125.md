# Cycle 125 — Preserve limited stock targets in the editor

The vanilla 1.20.1 inventory packet writes item stack counts as signed bytes. A limited filter's ghost sample is an inventory stock target and its saved count is an integer. The limited filter menu now synchronizes the exact sample using the existing counted stack NBT codec and displays it separately from the vanilla inventory slots. The ordinary cursor gesture still stops at one physical stack. It leaves a larger saved count intact.

The Ender IO network protocol is now `forge-3` because the limited filter menu uses new synchronized slots. Client and server use matching builds. The filter save format is unchanged.

## Commands

```sh
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-125-qa-before.log 2>&1
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-125-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-125-qa-verified.log 2>&1

run_stock_filter_client() {
  run_name=$1
  port_number=$2
  production_jar=$3
  port_profile="build/port-runtime/$run_name-client"
  port_source=build/port-runtime/cycle-121-fixed-client
  port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
  mkdir "$port_profile"
  cp -R "$port_source/libraries" "$port_source/versions" "$port_profile/"
  python3 scripts/porting/client/prepare_packaged.py --name "$run_name-client" --java "$port_java" \
    --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$production_jar" \
    --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
  python3 scripts/porting/packaged_stock_filter_menus.py --name "$run_name" --port "$port_number" \
    --java "$port_java" --forge-server build/port-runtime/cycle-047-legacy-discover \
    --jar "$port_profile/mods/enderio.jar" --launch "$port_profile/launch.json"
}

run_stock_filter_client cycle-125-before 25956 build/port-runtime/cycle-124-verified/server/mods/enderio.jar
run_stock_filter_client cycle-125-fixed 25957 enderio/build/libs/enderio-8.2.14.124-1.20.1+c68ba9611b.jar
run_stock_filter_client cycle-125-verified 25958 enderio/build/libs/enderio-8.2.14.124-1.20.1+c68ba9611b.jar
```

## Results

- The previous packaged jar starts a clean Forge world and opens the limited filter over a real TCP connection. The client sees zero items in the slot seeded with a 137-item target. See `build/port-evidence/cycle-125-before-edited/result.json`.
- The full build and QA packaging pass with 79 unit cases and 489 native Forge executions. All 167 discovered gameplay cases run.
- The final packaged run passes 17 client checks and 31 server assertions. The limited editor displays the saved 137-item sample and tag. Clicking it with matching cobblestone keeps the saved target and all four held blocks. Two clicks using a stack of 32 paper set an independent target of 64 and preserve the physical stack. The saved player inventory retains both targets and the tag toggle after disconnect and dedicated restart. A fresh client opens the same saved filter and displays both counts.
- Before and after restart, the insert route caps the destination at 137 matching items and leaves 85 matching items in the source. The extraction route leaves 137 matching items in the source and moves 55. The seven differently tagged items at each source and eleven at each destination stay in place. The ordinary large filter applies a type deny rule from slot 36. The large advanced filter uses slot 36 with tag matching and the up-to-25% damage threshold, accepting damage 45 and holding back damage 80 or a different tag.
- Both dedicated processes exit at zero with no runtime or registry errors. The guarded clients record zero activation, foreground or onscreen events across 2,717 observations. Capture metadata records the narrator off and all ten volumes at zero. See `build/port-runtime/cycle-125-verified/stock-filter-menu-results.json` and the `cycle-125-verified-edited` and `cycle-125-verified-reopened` evidence directories. Both clients and the server use production SHA-256 `db17ca70af4bc62ab91cb595cc9d9555d758c8649c2e1420181c7691da722a4b`.

## Remaining verification

This cycle exercises handheld editors and installs the exact edited items into the fixture using native server commands. Crafting these filter tiers and configuring them from conduit screens still need dedicated runtime coverage.
