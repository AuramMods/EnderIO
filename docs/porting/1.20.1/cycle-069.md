# Cycle 069 — Soul Vial replacement and Powered Spawner rules

## Scope

Verify the default and opt-in Soul Vial interactions with a vanilla spawner. Exercise the packaged Powered Spawner's tag restrictions, authored copy recipe, nearby-mob limit, persisted FE, and Mind Killer.

## Changes

- Forge 1.20.1's native spawner setter updates the next spawn entry. A separately loaded weighted pool can restore the old mob when the spawn delay resets. After replacing the type, reload the native spawner settings with the changed entry as its sole candidate. This retains the current spawn rules, position, delay, count and range.
- Extend the existing native Soul Vial GameTest to load a vanilla weighted spawn pool and inspect both the current entry and the next candidate after replacement.
- Add a packaged fixture with a disposable server-side datapack, a cow copy recipe with a 14,000 FE price, and pig and sheep tag overrides. The guarded client uses the vanilla spawner and actual mob interactions in survival over TCP.

## Commands

```sh
./gradlew :endercore:runGameTestServer --console=plain
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_jar=enderio/build/libs/enderio-8.2.14.68-1.20.1+5ea5902a49.jar
python3 scripts/porting/packaged_spawner_rules.py --name cycle-069-headless-confirmed --port 25743 \
  --java "$port_java" --forge-server "$port_forge" --jar enderio/build/libs/enderio-8.2.14.67-1.20.1+83c9729bd5.jar
python3 scripts/porting/packaged_spawner_rules.py --name cycle-069-headless-fixed --port 25746 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar"
./gradlew :enderio:reobfClientSmokeJar --console=plain
mkdir build/port-runtime/cycle-069-client-final
cp -R build/port-runtime/cycle-068-client/libraries build/port-runtime/cycle-068-client/versions \
  build/port-runtime/cycle-069-client-final/
python3 scripts/porting/client/prepare_packaged.py --name cycle-069-client-final --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_spawner_rules.py --name cycle-069-live-final --port 25747 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" \
  --launch build/port-runtime/cycle-069-client-final/launch.json
```

## Results

- With the previous source, the extended GameTest failed because the original pig remained in the weighted pool after the Soul Vial selected a cow. The other 170 native core tests passed. See `build/cycle-069-baseline-gametest.log` and `build/port-evidence/cycle-069-baseline/gametest.xml`.
- The first headless fixture confirmed the authored price, pig whitelist and Warden blacklist. Its restart probe attempted to remove saved cows while Forge was still loading entities. The repeat observes both the paid task and the reloaded cows before removing them. Reading the repeat's saved entity data also confirmed that vanilla rewrites the copied name to a JSON text object. The final fixture matches that format. See `build/port-runtime/cycle-069-headless-baseline/smoke-restart.log` and `build/port-runtime/cycle-069-headless-confirmed/port-spawners/entities/r.2.0.mca`.
- The headless profile on the fixed jar passed seven assertions. The whitelist allowed a pig that also appeared on the blacklist. The default Warden restriction kept the machine's 12,000 FE intact. The custom cow job waited behind the two-cow limit and retained all 14,000 FE of paid progress across restart. Clearing the crowd produced a copy with its saved name and 7.0 health. The Mind Killer disabled its AI and still allowed movement from applied velocity. Both servers exited zero without runtime errors. See `build/port-runtime/cycle-069-headless-fixed/spawner-rules-results.json`.
- The final TCP run passed 17 server assertions and eight client checks. With the default setting, the vial released the stored cow and the spawner retained its pig display and pool. The local datapack blocked capture of a sheep and let the pig's whitelist override its blacklist. With replacement enabled, the survival player received one empty vial and changed the spawner's type to cow. Two successive vanilla cycles spawned cows while preserving the original fixed position, range, count, delay and light rule. The server reopened with the cow type in both native spawn fields. All three servers and both guarded clients exited zero without runtime errors. See `build/port-runtime/cycle-069-live-final/spawner-rules-results.json`.
- The default and enabled captures show the pig display and the cow spawn with the empty vial. The watcher recorded 1,826 and 2,107 samples with zero activation, foreground or visible-window events. All ten volumes were zero, the narrator and fullscreen were off, and the cursor stayed uncaptured. See `build/port-evidence/cycle-069-live-final-default/` and `build/port-evidence/cycle-069-live-final-enabled/`.
- An earlier guarded probe reached its time limit without completing the vial interaction. The next probe completed the default vial use and exposed the fixture's malformed command. The final fixture uses the native integer-array light range and passes both setting values. See `build/port-evidence/cycle-069-live-default/summary.json` and `build/port-runtime/cycle-069-live-observed/smoke-initial-default.log`.
- The full build passed 78 unit tests and 430 Forge GameTests with zero failures, errors or skips. The final guarded QA jar reobfuscated successfully after adding interaction diagnostics. See `build/cycle-069-build-final.log`, `build/cycle-069-client-final.log`, and each module's `build/test-results/test/` XML.

| Artifact | SHA-256 |
| --- | --- |
| Packaged production jar | `2f03639ab89116cb37bc1b0b0f75013e49855573fa9e12ddbced27020a207c01` |
| Guarded QA jar | `49de6847dbec806c329494bfe972c73542fa1d1d9754840dfa29555f98bb27f6` |

## Remaining verification

Exercise the maximum-nearby-spawner efficiency setting and expand packaged verification to the other obelisks and Soul Engine.
