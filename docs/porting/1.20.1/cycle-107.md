# Cycle 107 — Upgrade Forge 6 Enchanters

Create a disposable Forge 6.2.18-beta world containing an Enchanter with saved inputs and no cached preview, a native Enchanter drop in a barrel, and a machine stocked for an original-format datapack recipe. The native loot table writes `BlockEntityTag.Items` with byte stack counts. The test copies the world before starting the port and uses dedicated multiplayer to place the old drop and collect both Sharpness II books.

The fixture plants the original-format Mending recipe as a datapack with a nested `ingredient` and a count of six amethyst shards. The two other machines use the stock quartz recipe. The port must rebuild the placed preview, accept the old recipe format, preserve each writable book's page in its machine, and retain the paid state through further restarts.

## Commands

```sh
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-107-qa.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_prior=build/port-runtime/cycle-106-final-client
port_client=build/port-runtime/cycle-107-legacy-client
mkdir -p "$port_client"
cp -R "$port_prior/libraries" "$port_prior/versions" "$port_client/"
python3 scripts/porting/client/prepare_packaged.py --name cycle-107-legacy-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_prior/mods/enderio.jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_legacy_enchanter.py --name cycle-107-legacy --port 25903 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_forge/mods/enderio.jar" \
  --jar "$port_client/mods/enderio.jar" --launch "$port_client/launch.json"
```

## Results

- The QA jar builds successfully. Four dedicated Forge 47.4.28 servers exit at zero. The scenario passes 13 server assertions on Ender IO 6.2.18-beta (`9d1471e6d4562d0a50f0564bc46573b87fd738bd440fe19397d31847da20f172`) and the cycle 105 production jar (`0630a8393de70f55866dc0548bf6038f86b7c14303a984de15fd6c7892d8f90f`). See `build/cycle-107-qa.log` and `build/port-runtime/cycle-107-legacy/legacy-enchanter-results.json`.
- The placed machine rebuilds its Sharpness II preview. The old native drop keeps its `BlockEntityTag` in the barrel across conversion and restart. Survival placement consumes the carried item and restores the original page, 25 quartz, 10 lapis and the Sharpness II preview. The copied datapack's amethyst Mending recipe generates a preview on conversion and still does after the final restart.
- The placed machine refuses both collection gestures at 10 levels. The replanted machine refuses shift-click with a full inventory. Collecting each book costs 11 levels, one writable book, 24 quartz and six lapis. Both paid machines still have one quartz, four lapis and an empty output after restart.
- The two clients pass 18 checks and record 2,587 native window samples with zero activation, foreground or on-screen events. All four captures have every volume at zero and narrator off. See `build/port-evidence/cycle-107-legacy-exercise-client/summary.json` and `build/port-evidence/cycle-107-legacy-verify-client/summary.json`.
- First conversion reports 17 outstanding historical registry IDs across six enchantments and 11 items. The reopen and paid stages have zero registry diagnostics. See `build/port-runtime/cycle-107-legacy/port/smoke-converted.log` for the exact IDs.

## Remaining verification

Exercise additional built-in recipes and live cost edits. The historical registry IDs still need a compatibility decision and evidence for any supported migration path.
