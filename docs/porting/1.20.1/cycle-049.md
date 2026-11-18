# Cycle 049 — migrate historical travel anchors and verify dedicated travel

An official Ender IO 6.2.18-beta save reproduced a load error because `enderio_traveldata.dat` used a `name` and `data` envelope. The port now converts those anchor records to the current format. This preserves position, name, visibility and icon. Forge's saved registry aliases resolve renamed icons. Current records take precedence at the same position, and unsupported old records remain in the saved world. Login and dimension-change packets include decoded targets only.

Native chunk loading also restores a ghost icon before assigning the block entity a level. The anchor now lets the server's saved target supply its metadata during loading and client inventory sync. A live edit to the server's ghost slot still updates its travel icon.

## Reproduction

The QA fixture command in the old server places three anchors in the Overworld and one in the Nether using the real 6.2 block entities. It sets names, ghost icons and visibility through those entities. The old server writes the fixture to disk and exits before the runner copies the world. It includes a painted workshop with a copper alloy icon and a hidden lift above the hub. The Nether anchor shares the Overworld hub's coordinates and has its own name and icon.

```sh
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain
python3 scripts/porting/legacy_fixture/build.py \
  --java /Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java \
  --forge-server build/port-runtime/cycle-047-legacy-discover \
  --legacy-jar build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar \
  --art /Users/cyberpwn/.gradle/caches/modules-2/files-2.1/net.neoforged/AutoRenamingTool/2.0.17/9388df4c3589faf10c35d9154cfb2451c83bd717/AutoRenamingTool-2.0.17-all.jar \
  --output build/port-runtime/cycle-049-fixture
python3 scripts/porting/client/prepare_packaged.py \
  --name cycle-049-client \
  --java /Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java \
  --installer build/port-runtime/forge-47.4.28-installer.jar \
  --jar enderio/build/libs/enderio-8.2.14.48-1.20.1+b3c464fbbc.jar \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar \
  --assets /Users/cyberpwn/.gradle/caches/neoformruntime/assets
python3 scripts/porting/client/run_travel_migration.py \
  --name cycle-049-final --port 25650 \
  --java /Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java \
  --forge-server build/port-runtime/cycle-047-legacy-discover \
  --legacy-jar build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar \
  --legacy-fixture build/port-runtime/cycle-049-fixture/fixture.jar \
  --jar enderio/build/libs/enderio-8.2.14.48-1.20.1+b3c464fbbc.jar \
  --launch build/port-runtime/cycle-049-client/launch.json
```

The fresh disposable client directory reused official libraries and version metadata from `cycle-046-client`. You can also create it using `prepare_packaged.py --install`. Every launch uses Forge 47.4.28 and Temurin 17.0.18. The legacy fixture jar runs in the source server only, and the converted server loads only the bundled port jar. The QA client runs under the existing native observer with normal focus and window activation disabled, narrator off, and all ten volume categories muted.

## Evidence

The build passed **78 ordinary JUnit tests and 413 native GameTests**. The two added GameTests cover mixed native and legacy records, native precedence, opaque record retention through save, and restoring a populated ghost slot before assigning a level. All 95 discovered main gameplay tests passed. The full log is `build/port-evidence/cycle-049-final-build.log`.

The TCP scenario passed **30 client checks and 22 server assertions**. It checks the migrated names and icons, preserved paint, hidden lift, and painted anchor menu. It requests horizontal travel using the production target selector and packet, then requests an elevator ride to the hidden target. The dedicated server independently confirms both actual arrivals. After a real dimension change, the client receives the Nether relay's name and blaze-rod icon at the same coordinates, with the Overworld destinations absent from its view. The same checks repeat after a server restart and a fresh client connection. The native observer collected 1,111 samples during conversion and 1,103 on restart, with zero activation events, foreground samples or onscreen samples. Both clients and all three owned servers exited normally. The converted server reports the previously known four missing-registry error headers. All clients and servers have zero other ERROR/FATAL entries.

The complete report is `build/port-runtime/cycle-049-final/travel-migration-results.json`. Per-client checks, native focus observation and framebuffer images are under `build/port-evidence/cycle-049-final-{converted,restart}`. The tested bundled jar's SHA-256 is `9b0560c0fd06b96cb3a315dfda76558dacc7d498734312b28f543993458d1f40`. The separate client QA jar is `8bf5ee157311d28e916d8d633a248606d323c2a8f10f0444cce25671022bb6d2`.

Staff charge, blink and travel cooldowns still need runtime coverage. Unsupported historical addon targets remain data on disk until a converter recognizes their format. Broader 6.x migration and the full port remain open.
