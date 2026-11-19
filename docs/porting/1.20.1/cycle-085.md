# Cycle 085 — Resume migrated Painting Machine jobs

## Scope

Resume a partially paid slab job in a real Forge 6.2.18-beta save with a matching painted slab already in the machine's output. Cover the same state in the old machine's native loot after a player places it in survival.

## Changes

- Convert either historical portable paint key into native item paint when a painted block item is loaded. Matching old and new products can stack in machines, containers and player inventories. Retain the item count and unrelated item and block entity tags. Direct legacy placement continues to work.
- Use the shared historical machine loader to restore carried Painting Machine energy, redstone and I/O to typed item data. Keep its inventory and recipe task in the carried block entity tag for native placement to load.
- Extend the painted slab GameTests and packaged conversion fixture to verify the new item representation. Add a packaged historical Painting Machine scenario with real survival placement, menu sync, native hopper extraction and a dedicated-server restart.

## Commands

```sh
./gradlew build --continue --console=plain
./gradlew :enderio:reobfClientSmokeJar --console=plain

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_legacy="$port_forge/mods/enderio.jar"
port_before=enderio/build/libs/enderio-8.2.14.83-1.20.1+f8e06f6631.jar
port_jar=enderio/build/libs/enderio-8.2.14.84-1.20.1+f6a9cebb06.jar

python3 scripts/porting/packaged_legacy_painting.py --name cycle-085-old-jar --port 25810 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" --jar "$port_before"
mkdir build/port-runtime/cycle-085-client
cp -R build/port-runtime/cycle-084-client/libraries build/port-runtime/cycle-084-client/versions \
  build/port-runtime/cycle-085-client/
python3 scripts/porting/client/prepare_packaged.py --name cycle-085-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_legacy_painting.py --name cycle-085-legacy-paint --port 25811 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" --jar "$port_jar" \
  --launch build/port-runtime/cycle-085-client/launch.json
python3 scripts/porting/packaged_painted_slabs.py --name cycle-085-slab-regression --port 25812 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" --jar "$port_jar" \
  --launch build/port-runtime/cycle-085-client/launch.json
```

## Results

- The previous build loaded a Forge 6 machine with 600 of 2,400 FE already paid. After an actual restart with the port, the machine spent the remaining 1,800 FE and stopped with one slab in output and one template still installed. The old output's `BlockEntityTag.Paint` prevented its new typed-paint result from stacking. See `build/port-runtime/cycle-085-old-jar/legacy-painting-results.json` and `build/port-runtime/cycle-085-old-jar/port/smoke-resumed.log`.
- The final full build passed 79 unit tests and 482 required native cases with zero failures or skips. The native counts were 171 core, seven Mekanism, 117 registry, 160 gameplay and 27 addon. The added Forge case loads old slabs carrying either paint key, proves tag equality with a modern stack of the same paint, and checks retention of other tags. See `build/cycle-085-build-final.log` and `build/cycle-085-client-build.log`.
- The packaged migration passed 15 server assertions. Both original machines kept 600 FE of recipe progress, the capacitor, template, reusable paint and output. The carried drop also restored 1,800 FE, redstone and east pull on survival placement. Each machine spent its remaining 1,800 FE and yielded an exact stack of two diamond-painted slabs. A vanilla hopper extracted one pair and the native menu transferred the other. Survival placement spent one of the carried pair and kept its paint. Both machines, the hopper and replanted slab were checked again after restart. See `build/port-runtime/cycle-085-legacy-paint/legacy-painting-results.json`.
- Eight guarded client checks passed, including synchronization of the native menu at 25% progress, its original charge and items, and the placed slab's diamond model. The two 960×540 captures used all ten sound volumes at zero and narrator off. The observer took 1,784 samples with zero foreground, onscreen or activation events. See `build/port-evidence/cycle-085-legacy-paint-client/summary.json`, `legacy-painting-menu.png` and `legacy-painting-slab.png`.
- The matched production jar has SHA-256 `d26e898a5191ca4f08821e735339e71c40e5a621d0b27855ccf2f2c092d05632`. All four dedicated server processes exited at 0 with no runtime errors. Conversion logged three Forge registry diagnostics for outstanding historical enchantments and items. The port resumed and reopened with zero registry errors.
- The packaged mixed-slab regression passed ten server assertions and 11 guarded client checks on the same jar. Both real Forge 6 drops used native item paint after loading and still placed in either order. Both colors survived rendering, loot and restart. The observer recorded zero foreground, onscreen and activation events in 1,625 samples. See `build/port-runtime/cycle-085-slab-regression/painted-slabs-results.json` and `build/port-evidence/cycle-085-slab-regression-client/summary.json`.

## Remaining verification

Painting recipes beyond the tested slab and fence families, remaining painted block and facade interactions, and older registry IDs need further coverage.
