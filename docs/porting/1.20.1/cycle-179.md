# Cycle 179 — Restore Forge 6.2 Location Printouts

The released Ender IO 6.2.18-beta jar reads the original selection from the item's `CoordinateSelection` compound, a `Level` string and `BlockPos` with native integer `X`, `Y` and `Z` fields. Its setter only writes when the stack already has a selection. The fixture therefore seeds three stacks using the released format, then lets the historical server save them in a barrel. The records include a named Overworld location, a named location in a dimension absent from the test world, and an incomplete location missing Z.

The port now converts a complete original selection when Forge loads the stack. It saves the dimension key and exact signed coordinates in the current item data, removes the migrated tag and preserves the name and unrelated metadata. An existing modern selection takes precedence. An incomplete record stays intact with no active selection. A historical printout that was originally saved without coordinates has no location to recover.

The packaged scenario copies the historical world to a new server profile. After migration, a survival player opens the named Overworld printout from the Nether, renames it and restarts. A fresh client clears the label and reopens the same printout. The second and third items remain in the barrel through both port stages.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_forge="$port_runtime/cycle-047-legacy-discover"
port_legacy="$port_runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar"
port_before="$port_runtime/cycle-178-coordinate-complete/mods/enderio.jar"
port_jar=enderio/build/libs/enderio-8.2.14.178-1.20.1+d156b95a9d.jar
port_client="$port_runtime/cycle-179-printout-verified-client"
port_guarded="$port_runtime/cycle-178-coordinate-complete-client"

./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-179-printout-build.log 2>&1
python3 -u scripts/porting/packaged_legacy_printout.py --name cycle-179-printout-before --port 26144 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" --jar "$port_before"
mkdir -p "$port_client/config"
cp -cR "$port_guarded/libraries" "$port_guarded/versions" "$port_client/"
cp "$port_guarded/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name cycle-179-printout-verified-client \
  --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 -u scripts/porting/packaged_legacy_printout.py --name cycle-179-printout-verified --port 26145 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" --jar "$port_jar" \
  --launch "$port_client/launch.json"
```

The local wrapper prepares the same guarded Forge client profile as [cycle 178](cycle-178.md) and launches the packaged legacy scenario with no optional mods.

## Results

- The historical save source jar is Ender IO 6.2.18-beta with SHA-256 `9d1471e6d4562d0a50f0564bc46573b87fd738bd440fe19397d31847da20f172`. Its saved barrel contains all three original records and their custom names. The previous port loads the copied world and retains the legacy fields without an active modern selection. See `build/port-runtime/cycle-179-printout-before/port/smoke-converted.log`.
- The full build passes 79 unit tests and 537 native runs, including all 210 discovered main gameplay cases. The new native load test checks signed coordinates, an absent dimension, exact name and extra tag data, modern data precedence and preservation of an incomplete record.
- The packaged scenario passes nine server assertions and 32 client checks. The migrated item shows and opens Overworld coordinates `-3161, 43, -12` while its holder is in the Nether. Its original marker and renamed label survive restart. Clearing and reopening changes only the label. The barrel still holds the absent dimension's exact location and the untouched incomplete record. The three server stages and both client sessions report no runtime or registry errors.
- The tested port jar has SHA-256 `4d755252e71665d9abc49f6ee0eb9c4100691d3dd402cec74e8a6effeca620cb`. The matching client captured the rename and clear actions with all sound categories muted and narrator off. The independent observer records zero activations, foreground samples or visible windows across 3,508 observations. See `build/port-runtime/cycle-179-printout-verified/legacy-printout-results.json` and `build/port-evidence/cycle-179-printout-verified-{exercise,verify}-client/summary.json`.

## Next verification

Exercise Cold Fire Igniter fuel use and cold-fire crafting in packaged Forge. The original cold-fire crafting case remains disabled as flaky, and the current evidence covers extinguishing.
