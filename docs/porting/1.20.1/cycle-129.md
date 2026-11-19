# Cycle 129 — Keep an installed filter editor open during sibling removal

The packaged mixed-bundle fixture now lets the wrench operator target the redstone connector while the other player has the item filter editor open. The redstone route is driving a lamp when the operator clicks. The editor must retain the same screen and menu, clear the original ghost sample, apply a tagged 37-item sample from the player's inventory, and return to the item conduit screen through Back. The dedicated server checks the new stock rule while the editor is still open, then checks persistence across chunk eviction and restart.

## Commands

```sh
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-129-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_source=build/port-runtime/cycle-128-wrench-client
production_jar=build/port-runtime/cycle-128-verified/server/mods/enderio.jar
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
prepare_profile cycle-129-surviving-filter-client EnderIOQA
prepare_profile cycle-129-surviving-filter-tool EnderIOWrench
python3 scripts/porting/packaged_filter_recovery.py --name cycle-129-surviving-filter --port 25976 \
  --java "$port_java" --forge-server build/port-runtime/cycle-047-legacy-discover --jar "$production_jar" \
  --launch build/port-runtime/cycle-129-surviving-filter-client/launch.json \
  --operator-launch build/port-runtime/cycle-129-surviving-filter-tool/launch.json \
  --world build/port-runtime/cycle-125-verified/server/port-spawners --wrench-redstone
python3 scripts/porting/packaged_filter_recovery.py --name cycle-129-verified --port 25977 \
  --java "$port_java" --forge-server build/port-runtime/cycle-047-legacy-discover --jar "$production_jar" \
  --launch build/port-runtime/cycle-129-surviving-filter-client/launch.json \
  --operator-launch build/port-runtime/cycle-129-surviving-filter-tool/launch.json \
  --world build/port-runtime/cycle-125-verified/server/port-spawners --wrench-redstone
python3 scripts/porting/packaged_filter_recovery.py --name cycle-129-item-control --port 25978 \
  --java "$port_java" --forge-server build/port-runtime/cycle-047-legacy-discover --jar "$production_jar" \
  --launch build/port-runtime/cycle-129-surviving-filter-client/launch.json \
  --operator-launch build/port-runtime/cycle-129-surviving-filter-tool/launch.json \
  --world build/port-runtime/cycle-125-verified/server/port-spawners
```

## Results

- The full build passes all 79 unit cases and 490 native Forge runs, including 168 main gameplay cases, with zero failures or skips. The cycle adds packaged QA coverage.
- The successful run passes 15 checks across two guarded clients and 28 dedicated-server assertions. The redstone lamp goes dark while its lever stays powered. The only drop is one redstone conduit. The remaining item conduit and its installed filter stay connected.
- The same editor accepts the new tagged sample without consuming the player's physical stack. It stays open while the live item route sends exactly enough to reach 37 matching cobblestone and leaves 185 matching cobblestone in the source. The seven wrong-tag source blocks and eleven wrong-tag destination blocks stay put. Back returns to the existing item endpoint. Both route chunks actually unload, and the new stock rule and dark lamp survive chunk reload and a dedicated restart.
- The two successful client watchers report zero activation, foreground or onscreen events in 4,951 observations. Both isolated profiles use all-zero volumes with the narrator off, also confirmed in both editor captures. The production jar matches on both clients and the dedicated server at SHA-256 `360449cfcb0183ff32f3636cb6595581cb5513581acd1a7d63daf8c03962dc9f`. Both server processes exit zero with no runtime or registry errors. See `build/port-runtime/cycle-129-verified/filter-recovery-results.json` and `build/port-evidence/cycle-129-verified*`.
- The item-removal control repeats cycle 128 on the extended harness. It passes 16 client checks and 31 server assertions, including exact filter recovery and both routes after restart. See `build/port-runtime/cycle-129-item-control/filter-recovery-results.json`.

## Remaining verification

The first wrench client timed out before gameplay. The Forge server debug log shows 29 negotiation messages queued and no indexed reply for that connection. The client debug log stops before receiving any registry packet. The retry completed against the same two profiles and artifact. The cause remains unresolved. See `build/port-evidence/cycle-129-surviving-filter-operator/summary.json`, its companion `forge-debug.log`, and `build/port-runtime/cycle-129-surviving-filter/server/logs/debug.log`.
