# Cycle 128 — Recover a filter after selective wrench removal

The packaged recovery fixture now supports two isolated Forge clients on the same dedicated server. One player opens the installed Limited Item Filter while the other crouches and uses a Yeta Wrench on the item part of a mixed item and redstone bundle. The bundle spans a chunk border and the redstone conduit connects a lever to a vanilla lamp. The editor player claims the native drops, re-adds the item conduit to the remaining bundle, and reinstalls the recovered filter through its normal menu.

## Commands

```sh
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-128-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_source=build/port-runtime/cycle-121-fixed-client
production_jar=build/port-runtime/cycle-127-recovery/server/mods/enderio.jar
prepare_profile() {
  profile_name=$1
  profile_user=$2
  profile="build/port-runtime/$profile_name"
  mkdir "$profile"
  cp -R "$port_source/libraries" "$port_source/versions" "$profile/"
  python3 scripts/porting/client/prepare_packaged.py --name "$profile_name" --username "$profile_user" \
    --java "$port_java" --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$production_jar" \
    --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
}
prepare_profile cycle-128-wrench-client EnderIOQA
prepare_profile cycle-128-wrench-tool EnderIOWrench
python3 scripts/porting/packaged_filter_recovery.py --name cycle-128-verified --port 25974 \
  --java "$port_java" --forge-server build/port-runtime/cycle-047-legacy-discover --jar "$production_jar" \
  --launch build/port-runtime/cycle-128-wrench-client/launch.json \
  --operator-launch build/port-runtime/cycle-128-wrench-tool/launch.json \
  --world build/port-runtime/cycle-125-verified/server/port-spawners

prepare_profile cycle-128-block-control-client EnderIOQA
python3 scripts/porting/packaged_filter_recovery.py --name cycle-128-break-recheck --port 25975 \
  --java "$port_java" --forge-server build/port-runtime/cycle-047-legacy-discover --jar "$production_jar" \
  --launch build/port-runtime/cycle-128-block-control-client/launch.json \
  --world build/port-runtime/cycle-125-verified/server/port-spawners
```

## Results

- The full build exits zero with 79 unit tests and 490 native Forge test executions, including all 168 main gameplay cases. The changes in this cycle are confined to the test harness and its documentation.
- The two packaged clients pass 16 checks over TCP. The wrench removes the item conduit and its internal link. Both clients see the remaining redstone conduit, and the installed filter editor closes while its player stays in reach. The server sees exactly two item entities, the item conduit and the configured filter. The editor player picks up both, re-adds the recovered conduit to the same bundle, and reinstalls the same filter. Its 64-paper and 137-cobblestone samples are intact.
- The dedicated server passes 31 assertions. The redstone conduit still switches the lamp on and off with the item conduit absent. After reinstallation, after both route chunks actually unload and reload, and after a dedicated restart, the lamp still follows the lever and the item route keeps exactly 137 matching cobblestone in the destination and 85 in the source. Seven wrong-tag source blocks and eleven wrong-tag destination blocks stay put. Both server sessions exit zero with no runtime or registry errors. See `build/port-runtime/cycle-128-verified/filter-recovery-results.json`.
- The first live trial placed the wrench player close enough to collect both drops immediately. The saved test inventory contained the conduit and tagged filter. The verified fixture puts the wrench player outside pickup range. It also follows the game's sorted conduit order when checking the reinstalled item route.
- The isolated profiles use the same production jar as the dedicated server, with SHA-256 `360449cfcb0183ff32f3636cb6595581cb5513581acd1a7d63daf8c03962dc9f`. The two native observers record zero activation, foreground or onscreen events across 4,410 total samples. Both profiles have all ten volumes at zero and the narrator off. The editor captures also confirm those runtime settings. See `build/port-evidence/cycle-128-verified` and `build/port-evidence/cycle-128-verified-operator`.
- The single-client destruction control passes 10 client checks and 18 server assertions, with zero activation, foreground or onscreen events in 1,478 observations. See `build/port-runtime/cycle-128-break-recheck/filter-recovery-results.json`.

## Remaining verification

Verify that removing a different conduit keeps an otherwise valid editor open. The first single-client destruction control disconnected during Forge login before gameplay. Its retry passed. The intermittent login failure remains open. See `build/port-evidence/cycle-128-block-control/summary.json`.
