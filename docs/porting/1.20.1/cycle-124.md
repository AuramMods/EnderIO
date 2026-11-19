# Cycle 124 — Edit migrated transfer filters over multiplayer

The shared handheld filter editor now reads and writes the hand that opened it. The server checks the opening stack and locks its original inventory slot during editing. A successful sneak-use consumes the interaction so ordinary item use stops at the hand that opened the menu.

The basic fluid filter now registers its configuration menu capability. A conduit can open the editor for an installed filter on either side of its inventory.

The packaged scenario copies the migrated world from [cycle 123](cycle-123.md). A survival client opens the advanced item filter from its offhand while carrying a separate advanced filter in its main hand. It uses a real stack as a ghost sample and changes the list mode. The same client uses the fluid conduit screen's configure button, copies a bucket's fluid into the installed filter, removes the original sample and returns to the same endpoint.

## Commands

```sh
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-124-qa-before.log 2>&1
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-124-build-final.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-124-qa-final.log 2>&1

run_transfer_filter_client() {
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
  python3 scripts/porting/packaged_transfer_filter_menus.py --name "$run_name" --port "$port_number" \
    --java "$port_java" --forge-server build/port-runtime/cycle-047-legacy-discover \
    --jar "$port_profile/mods/enderio.jar" --launch "$port_profile/launch.json" \
    --world build/port-runtime/cycle-123-fixed/port/port-spawners
}

run_transfer_filter_client cycle-124-before 25951 build/port-runtime/cycle-123-fixed/port/mods/enderio.jar
run_transfer_filter_client cycle-124-missing-provider 25954 build/cycle-124-hand-binding-only.jar
run_transfer_filter_client cycle-124-verified 25955 enderio/build/libs/enderio-8.2.14.123-1.20.1+db67916be9.jar
```

## Results

- The previous jar connects successfully. Its offhand editor cannot remain open when the filter in the main hand is a different stack. The guarded client reports the failed assertion and the native observer reports zero activation, foreground or onscreen events across 1,601 samples. See `build/port-evidence/cycle-124-before/summary.json`.
- The intermediate jar with the hand fix edits the offhand stack successfully. Its installed fluid filter leaves the configure button hidden. See the explicit failure and the captured conduit screen in `build/port-evidence/cycle-124-missing-provider`.
- The full build and QA jar remapping pass with 79 unit cases and 489 native test executions. All 167 discovered gameplay cases execute.
- The final run passes 11 client checks and 12 server assertions. The advanced item filter changes to allow mode and gets one tagged gold ghost sample. All seven real ingots and the separate main-hand filter stay intact. The installed fluid filter retains legacy matching and stores one untagged 1,000 mB sample while leaving the water bucket in the player's hotbar. Back reopens the exact west conduit endpoint.
- The server copies the edited offhand stack into the item route. Before and after a full restart, it transfers all five matching gold ingots and leaves all three differently tagged ingots at the source. The installed fluid route transfers all 880 mB of untagged water and leaves all 420 mB of tagged water in the source with an empty destination. Both dedicated processes exit at zero with zero runtime or registry errors. The client and server production SHA-256 is `12d5accf3013294437ed5cb61aedb4aa71a868fca6ee96e42b846cac462181fe`. See `build/port-runtime/cycle-124-verified/transfer-filter-menu-results.json`.
- Metadata for both framebuffer captures records the narrator off and every volume category at zero. The native observer reports zero activation, foreground and onscreen samples across 1,657 observations. See `build/port-evidence/cycle-124-verified` for the two images and `summary.json`.

## Remaining verification

Exercise the large and limited item filter menus, damage thresholds, stock limits, and the other fluid editor entry points. This cycle validates the edited routes after restart over the server console. Reopening the offhand editor from the player's saved inventory after reconnect remains open.
