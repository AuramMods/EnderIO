# Cycle 084 — Preserve both colors on painted slabs

## Scope

Test stacking two differently painted slabs in both placement orders and carrying the loose slabs from the actual Forge 6.2.18-beta release into the port.

## Changes

- Register the specialized painted-slab item. Use the shape before and after native placement to write the added half's paint. Preserve the existing half even if it is unpainted.
- Read typed item paint and both historical loose-slab keys for placement, item rendering, tint and sound. Preserve the vanilla placement path for other block entity tags.
- Choose the top paint when picking a top slab from below. Keep selecting the pointed half on a double slab.
- Exercise actual item placement, pick-block and loot in the native suite. Add a packaged fixture that generates both legacy items with the old loot table, moves a copy of the world, and places them through normal survival use over TCP.

## Commands

```sh
./gradlew :enderio:verifyGameplayTests --console=plain
./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.83-1.20.1+f8e06f6631.jar
mkdir build/port-runtime/cycle-084-client
cp -R build/port-runtime/cycle-083-client/libraries build/port-runtime/cycle-083-client/versions \
  build/port-runtime/cycle-084-client/
python3 scripts/porting/client/prepare_packaged.py --name cycle-084-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_painted_slabs.py --name cycle-084-painted-slabs --port 25808 \
  --java "$port_java" --forge-server build/port-runtime/cycle-047-legacy-discover \
  --legacy-jar build/port-runtime/cycle-047-legacy-discover/mods/enderio.jar --jar "$port_jar" \
  --launch build/port-runtime/cycle-084-client/launch.json
python3 scripts/porting/packaged_painting_machine.py --name cycle-084-paint-regression --port 25809 \
  --java "$port_java" --forge-server build/port-runtime/cycle-047-legacy-discover --jar "$port_jar" \
  --launch build/port-runtime/cycle-084-client/launch.json
```

## Results

- The original Forge reproduction failed three of five new cases. Ordinary painted-block placement wrote the top slab's color into the lower slot and could overwrite the first color when slabs were stacked. See `build/cycle-084-slab-reproduction.log` and `build/cycle-084-slab-reproduction-results.xml`.
- The first full build surfaced a restricted EnderCore test-source dependency on the initial location of the new paint reader. Moving the reader into the existing paint utilities restored that profile. See `build/cycle-084-first-build.log`.
- The full build and the separate client QA jar passed. Test results were 79 unit and 481 required Forge GameTests with zero failures or skips. The native counts were 171 core, seven Mekanism, 117 registry, 159 gameplay and 27 addon. See `build/cycle-084-build.log`.
- The packaged fixture passed ten server assertions and 11 guarded client checks. Forge 6's native loot produced one gold slab tagged `BlockEntityTag.Paint` and one diamond slab tagged `BlockEntityTag.Paint2`. The converted world retained the original block and items. Survival placement stacked the original items top first and modern items bottom first, consumed one slab per action, and preserved both paint IDs. Native loot returned one modern stack of each color for each double slab. All three mixed blocks and eight barrel items survived a packaged server restart. See `build/port-runtime/cycle-084-painted-slabs/painted-slabs-results.json`.
- The 960×540 framebuffer capture shows the gold and diamond bands on the original and both replanted slabs. All ten sound volumes were zero and narrator was off. The native observer took 1,266 samples with zero foreground samples, onscreen samples or activation events. See `build/port-evidence/cycle-084-painted-slabs-client/summary.json` and `mixed-painted-slabs.png` in the same directory.
- The client and both port server instances loaded the same production jar, SHA-256 `fe6134e9d47ab6382a932e1c64f97220434001869e12b08f827870e7bd4c1220`. The Forge 6.2.18-beta input jar SHA-256 is `9d1471e6d4562d0a50f0564bc46573b87fd738bd440fe19397d31847da20f172`. All three servers exited at 0. The conversion log reported unresolved legacy enchantment and item registry mappings, including `enderio:auto_smelt` and former Enderface items. The reopened save had no registry errors.
- The packaged Painting Machine and fence regressions passed on the same port jar with 11 server assertions and seven guarded client checks. Both servers exited at 0 with no runtime or registry errors. The observer again recorded zero activation, foreground and onscreen events. See `build/port-runtime/cycle-084-paint-regression/painting-machine-results.json` and `build/port-evidence/cycle-084-paint-regression-client/summary.json`.

## Remaining verification

Slab production through the Painting Machine, historical Painting Machine migration, and placement, interactions and rendering for the remaining painted block families are open. The legacy item and enchantment mappings need their own parity and migration audit.
