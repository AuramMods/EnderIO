# Cycle 078 — Farming Station harvest conservation

## Scope

Make a powered Farming Station harvest and replant wheat on Forge, including through a full-output stall, a dedicated server restart and extraction by a vanilla hopper.

## Changes

- Stage one crop's seed allocation and output insertions together in a detached copy of the relevant inventory slots. Forge's item handler applies stack limits and tag compatibility to the entire batch. Publish the staged slot changes once the entire harvest fits.
- Leave occupied, non-replaceable plots to the harvest task. The planting task previously claimed a mature wheat plot and reported a missing hoe before the harvest task could run.
- Add four native Forge tests for unchanged state when a harvest stalls, cumulative capacity, tagged seed conservation and scheduled wheat harvest and replanting.
- Add a packaged dedicated-server fixture with the production mod as the only installed mod. Use a disposable flat world and restart the server twice to verify the full and harvested inventories on disk.

## Commands

```sh
./gradlew :enderio:verifyGameplayTests --console=plain
./gradlew build --continue --console=plain
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
python3 scripts/porting/packaged_farming_station.py --name cycle-078-farm-final --port 25796 \
  --java "$port_java" --forge-server build/port-runtime/cycle-047-legacy-discover \
  --jar enderio/build/libs/enderio-8.2.14.77-1.20.1+dfcf56c159.jar
```

## Results

- Before the changes, the Forge tests showed a failed harvest increase the seed reserve from eight to eleven. Two one-item drops also passed the capacity check with only one unit of space left, and differently tagged seeds failed to reach their own output stack. See `build/cycle-078-reproduction-results.xml`.
- After aligning the GameTest's crop with the station's working height, the original planting task returned `BLOCKED` on a mature wheat plot. See `build/cycle-078-mature-reproduction-results.xml`.
- The full Gradle build passed. Its standard profiles reported 79 unit tests and 457 Forge GameTests, with zero failures or skips. The native split was 171 core, seven Mekanism, 117 registry, 135 gameplay and 27 addon tests. The final gameplay profile includes all four new cases. See `build/cycle-078-build.log` and `enderio/build/test-results/gameTestServer/results.xml`.
- The initial packaged fixture expected an empty energy buffer. The reopened station showed the expected crop and eight seeds but still held 16,720 FE. The 1.21.1 scheduler requires ten ticks of energy before each scan step and deducts one tick of consumption through its capped storage API. The corrected fixture supplies 2,360 FE and checks the 360 FE balance after 50 scan steps. See `build/cycle-078-packaged-inspection.log`.
- The final packaged run passed eight assertions. The blocked plot kept its mature wheat, eight reserve seeds and all six full output stacks. After restart, freeing one slot let the station store one wheat and replant the plot while a crop outside the default range stayed mature. The next restart preserved the wheat output, and a vanilla hopper extracted exactly one wheat while leaving the seed slot and capacitor installed. All three headless servers stopped with exit code 0 and zero runtime or registry errors. See `build/port-runtime/cycle-078-farm-final/farming-station-results.json` and its three server logs.
- The packaged mod jar SHA-256 is `5a70daf473b61f21e271423e12a0c8b6e69d5eaaed9c1eb9fbca6bf86d128aff`. The packaged server runs used Forge 47.4.28 and Java 17 without optional mods.

## Remaining verification

Farming Station tool and fertilizer behavior, different crop families, soul binding and reload, client range and GUI, and chunk unload and replanting of the machine still need dedicated coverage.
