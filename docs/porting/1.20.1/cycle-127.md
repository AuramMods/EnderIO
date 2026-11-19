# Cycle 127 — Verify filter recovery and conduit menu dimension checks

Conduit menus now require the player and bundle to be in the same level. Forge 1.20.1's block-entity validity check compares coordinates and checks the block entity in its own level. It can accept a player from another dimension at the same coordinates. The installed filter editors inherit the corrected check.

The packaged recovery fixture opens the installed Limited Item Filter from a copy of the cycle 125 world. It destroys the endpoint while the player stays within reach. The player claims the native filter and conduit drops, places the recovered conduit, enables insertion, and reinstalls the exact filter through the conduit inventory. It exercises the route before and after actual chunk eviction and a dedicated restart.

## Commands

```sh
./gradlew :enderio:runGameTestServer --console=plain > build/cycle-127-dimension-before.log 2>&1
cp enderio/build/test-results/gameTestServer/results.xml build/cycle-127-dimension-before.xml
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-127-build.log 2>&1

run_filter_recovery() {
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
  python3 scripts/porting/packaged_filter_recovery.py --name "$run_name" --port "$port_number" \
    --java "$port_java" --forge-server build/port-runtime/cycle-047-legacy-discover \
    --jar "$port_profile/mods/enderio.jar" --launch "$port_profile/launch.json" \
    --world build/port-runtime/cycle-125-verified/server/port-spawners
}

run_filter_recovery cycle-127-break-before 25968 build/port-runtime/cycle-125-verified/server/mods/enderio.jar
run_filter_recovery cycle-127-recovery 25969 enderio/build/libs/enderio-8.2.14.126-1.20.1+402e60272a.jar
```

## Results

- The native regression fails against the previous implementation with a nearby Overworld player and a Nether player at the same position. The nearby player passes, and the cross-dimension player incorrectly passes too. The baseline XML contains that single failure among 168 native gameplay cases.
- The full fixed build and QA packaging pass with 79 unit cases and 490 native Forge test executions. All 168 discovered gameplay cases execute, including the new cross-dimension regression.
- The earlier packaged build also closes the editor when the whole block is destroyed. It passes the recovery scenario, so the block-removal test records compatibility across the dimension fix. The range check from cycle 126 remains a separate regression. See `build/port-runtime/cycle-127-break-before/filter-recovery-results.json`.
- The fixed packaged run passes 10 client checks and 18 server assertions. Destroying the displayed endpoint closes the installed editor while the player stays in reach. Exactly one Item Conduit and one Limited Item Filter drop. The player collects both, places the recovered conduit across the original chunk border, enables insertion, reinstalls the same filter and sees its 64- and 137-item samples again. The full filter stack matches the original and the site retains no extra drop.
- The restored route keeps 137 matching cobblestone in the destination and 85 in the source. All seven wrong-tag source blocks and eleven wrong-tag destination blocks stay put. That accounting passes after reinstallation, after both route chunks actually unload and reload, and after a dedicated restart.
- Both fixed server processes exit at zero with no runtime or registry errors. The guarded client records zero activation, foreground or onscreen events in 1,558 observations. Both captures record the narrator off and all ten volumes at zero. The production SHA-256 is `360449cfcb0183ff32f3636cb6595581cb5513581acd1a7d63daf8c03962dc9f` on both ends. See `build/port-runtime/cycle-127-recovery/filter-recovery-results.json` and `build/port-evidence/cycle-127-recovery`.

## Remaining verification

Selective wrench removal from a mixed bundle with an editor open is covered in [cycle 128](cycle-128.md). The dimension test exercises the native menu on the server. This cycle's packaged scenario exercises destruction within reach, physical recovery, and chunk eviction after the client disconnects.
