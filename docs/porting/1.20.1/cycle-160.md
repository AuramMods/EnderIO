# Cycle 160 — Preserve carried Slice’N’Splice jobs

The released Forge 6.2.18-beta mod pays 3,600 FE toward each of three stock 20,000 FE recipes. The slicers save all six inputs, a Basic Capacitor, tagged tools, the queued output, top pull, bottom push and signal-controlled operation. The Ender Resonator job is mined into its original `BlockEntityTag` item. The Zombie Electrode remains placed for conversion and is then mined with the port’s own loot table.

The item loader now converts the old carried slicer’s energy, redstone mode and sided I/O through the existing Forge 6 machine migration. Modern slicer drops also carry their pending task. Placement restores that task after the carried inventory and energy have been applied. The remaining historical NBT is read for original Forge 6 items. The normal block save format remains compatible with already placed machines.

The packaged scenario copies only the old fixture world into a separate Forge installation. After restart, a guarded survival client opens the original placed slicer and places both carried machines. The test supplies the remaining FE to the placed machine and activates each slicer on its own redstone signal. Hoppers collect exactly one output per machine. A final dedicated restart checks the exact tools, saved controls and completed outputs.

## Commands

```sh
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-160-slicer-probe-build.log 2>&1
./gradlew :enderio:verifyGameplayTests --console=plain > build/cycle-160-slicer-native-before.log 2>&1
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-160-slicer-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-160-slicer-client-final-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_legacy=build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar
port_before=enderio/build/libs/enderio-8.2.14.158-1.20.1+189b681a2f.jar
port_jar=enderio/build/libs/enderio-8.2.14.159-1.20.1+dbbc92c082.jar
port_before_client=build/port-runtime/cycle-160-slicer-before-client
port_client=build/port-runtime/cycle-160-slicer-client
port_previous=build/port-runtime/cycle-159-slicer-client
python3 scripts/porting/packaged_legacy_slicer.py --name cycle-160-slicer-headless-before --port 26065 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" --jar "$port_before" \
  > build/cycle-160-slicer-headless-before.log 2>&1
mkdir -p "$port_client/config"
cp -cR "$port_previous/libraries" "$port_previous/versions" "$port_client/"
cp "$port_previous/config/fml.toml" "$port_client/config/fml.toml"
mkdir -p "$port_before_client/config"
cp -cR "$port_previous/libraries" "$port_previous/versions" "$port_before_client/"
cp "$port_previous/config/fml.toml" "$port_before_client/config/fml.toml"
python3 scripts/porting/client/prepare_packaged.py --name cycle-160-slicer-before-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_before" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/client/prepare_packaged.py --name cycle-160-slicer-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_legacy_slicer.py --name cycle-160-slicer-placement-repro --port 26069 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" --jar "$port_before" \
  --launch "$port_before_client/launch.json" > build/cycle-160-slicer-placement-repro.log 2>&1
python3 scripts/porting/packaged_legacy_slicer.py --name cycle-160-slicer-disk --port 26071 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" --jar "$port_jar" \
  --launch "$port_client/launch.json" > build/cycle-160-slicer-disk.log 2>&1
```

## Results

- The old production port already converts the placed Z-Logic Controller and Zombie Electrode jobs. A headless control passes 18 assertions and both jobs finish on the original remaining FE. The source jar is Ender IO 6.2.18-beta with SHA-256 `9d1471e6d4562d0a50f0564bc46573b87fd738bd440fe19397d31847da20f172`.
- In the first portable probe, a newly dropped slicer contains the normalized inventory, energy, redstone mode and sides. Its native drop omits the 3,600 FE task. With the regression added to the original production source, this is the sole native gameplay failure. See `build/cycle-160-slicer-native-before.log` and the dropped item in `build/port-runtime/cycle-160-slicer-before/port/port-spawners`.
- Earlier production bytes place the original Ender Resonator item with its 3,600 FE task, tools, redstone mode and complete inventory. The newly placed block has zero stored FE and empty sided I/O instead of the carried 16,400 FE reserve and two configured faces. See `build/port-runtime/cycle-160-slicer-placement-repro/port/smoke-reopened.log`.
- Two early client attempts exited at mod startup because the test scenario looked up mod items during construction. The corrected harness resolves its fixtures after login. The full build passes 79 unit cases and 524 native runs, including all 197 main gameplay cases and the new native drop-and-place regression.
- The first fixed multiplayer profile passed all placement, completion and wear assertions. The last fixture assertion tried to read a disconnected player using a live entity command. The corrected probe reads the saved player inventory from disk after stopping the server and checks the barrel and both original drop sites in the reopened world.
- The final packaged profile passes 24 server assertions, 18 client checks and the offline inventory check. Survival placement restores 3,600 paid FE and 16,400 stored FE in each carried machine. All three original jobs consume only the remaining 16,400 FE, export one output each, and advance the tagged tools from 17 to 18 and 23 to 24 damage. After restart, both drop sites, the barrel and the saved player inventory contain no unused machine copy.
- Both peers load production SHA-256 `37f20d670a0523ef69a9f0de9f49991c8584758cb5da63c9c6a53b2873da0b48`. The four server stages and client exit without runtime or registry errors. The observer records zero activation, foreground or visible-window events across 2,785 samples. All four captures show the narrator off and every volume at zero.

The final server and saved-player report is in `build/port-runtime/cycle-160-slicer-disk`. The client report and captures are in `build/port-evidence/cycle-160-slicer-disk-client`.

## Next verification

Exercise the Slice’N’Splice JEI category, ordered slot transfer and custom recipe updates on packaged Forge. Compare the tool candidates in the viewer with the machine’s valid tools.
