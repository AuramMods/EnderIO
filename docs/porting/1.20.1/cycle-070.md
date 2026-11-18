# Cycle 070 — Soul Engine generation and historical saves

## Scope

Verify the Soul Engine's normal binding recipe, Forge fluid input and FE output, server-side rule reload, native menu, and migration of placed and carried Forge 6.2 engines.

## Changes

- Resolve the active fuel rule from the server's current soul data. Require a compatible fuel, capacitor, permitted redstone state and enough room for the full output. Clearing or changing the bound mob preserves the previous fuel for extraction.
- Consume one millibucket when its advertised tick cost is paid. Save partial progress and honor the configured generation multiplier independently of burn speed. Synchronize both rates to the native menu.
- Recover the old entity payload, charge, capacitor, tagged fluid, redstone and side modes. The carried item's typed fuel state applies after native `BlockEntityTag` merging and preserves the contents during ordinary placement.

## Commands

```sh
./gradlew :enderio:assemble :enderio:verifyGameplayTests --console=plain
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain
./gradlew :enderio:reobfClientSmokeJar --console=plain
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_legacy=build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar
port_jar=enderio/build/libs/enderio-8.2.14.69-1.20.1+90a9c28100.jar
python3 scripts/porting/packaged_soul_engine.py --name cycle-070-baseline --port 25748 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" \
  --jar enderio/build/libs/enderio-8.2.14.68-1.20.1+5ea5902a49.jar
mkdir build/port-runtime/cycle-070-client
cp -R build/port-runtime/cycle-069-client-final/libraries build/port-runtime/cycle-069-client-final/versions \
  build/port-runtime/cycle-070-client/
python3 scripts/porting/client/prepare_packaged.py --name cycle-070-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
mkdir build/port-runtime/cycle-070-client-final
cp -R build/port-runtime/cycle-070-client/libraries build/port-runtime/cycle-070-client/versions \
  build/port-runtime/cycle-070-client-final/
python3 scripts/porting/client/prepare_packaged.py --name cycle-070-client-final --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_soul_engine.py --name cycle-070-verified --port 25753 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" \
  --legacy-world build/port-runtime/cycle-070-baseline/legacy/port-spawners \
  --launch build/port-runtime/cycle-070-client-final/launch.json
```

## Results

- The official 6.2 jar produced the native Blaze-tagged block and loot. The placed engine retained 11 paid ticks, 12,345 FE and 3 mB of tagged lava. The drop retained 12 paid ticks and 12,346 FE. The previous production jar rejected both placed Blaze payloads during conversion. See `build/port-runtime/cycle-070-baseline/soul-engine-results.json` and that profile's server logs.
- The targeted gameplay suite passed 113 required tests. The three new cases exercise the 18-tick and 800-FE Blaze rule, fuel preservation when the soul changes or clears, and redstone, capacitor and full-output gating. See `build/cycle-070-engine-build.log`.
- The full build passed 78 unit tests and 433 Forge GameTests with zero failures, errors or skips. The final guarded probe also reobfuscated. The shipped Soul Engine bytecode has class version 61, and the QA classes load from their separate jar. See `build/cycle-070-build-final.log`, `build/cycle-070-client-placement.log`, and the modules' `build/test-results/test/` XML.
- The native world conversion preserved both sets of fields. A survival player placed the recovered drop, and the resulting engine retained its Blaze, capacitor, 12,346 FE, 3 mB of tagged lava, redstone setting, disabled side and 12 paid ticks through reopening. The original engine spent all 3 mB and generated exactly 3,600 FE at the server's 1.5 generation multiplier. A Forge tank could feed 2 mB of water once the live datapack changed the fuel rule, generating another 2,700 FE. The wired charger received all 18,645 FE including the migrated charge and retained it after reopening. The real Soul Binder recipe spent 188,000 FE and 1,100 mB of XP fluid, produced a newly bound engine, and returned one empty vial. See `build/port-runtime/cycle-070-verified/soul-engine-results.json`.
- The guarded client passed nine checks. The open menu updated from 18 ticks and 1,200 FE per mB to four ticks and 1,350 FE per mB. Both captures confirmed all ten volumes at zero, narrator and fullscreen off, and an uncaptured cursor. The native watcher recorded 1,829 samples with zero activation, foreground or visible-window events. See `build/port-evidence/cycle-070-verified-client/`.
- Earlier probes exposed fixture issues. Networked machine statuses require comparison by type and text, a long initial teleport could race placement, and the tank's world-west output must be set on its local east face. The binding fixture needed the full serialized soul payload. The final run uses the corrected probes. See `build/port-runtime/cycle-070-{live,live-final,headless-final}/`.

| Artifact | SHA-256 |
| --- | --- |
| Previous production jar | `2f03639ab89116cb37bc1b0b0f75013e49855573fa9e12ddbced27020a207c01` |
| Fixed production jar | `3bf90f192f4e1a00943c8874dbced025cc335af6812b11c41e5ae0b2f53bcec9` |
| Official Ender IO 6.2.18-beta | `9d1471e6d4562d0a50f0564bc46573b87fd738bd440fe19397d31847da20f172` |
| Final guarded QA jar | `8daeef4a8d4bd7a67cbcaf827a089012bed0e0cae039590ab128b37e29a6175c` |

## Remaining verification

Exercise the other built-in soul fuels, the crafting-grid erase recipe, the Soul Engine recipe display with packaged JEI, and the remaining obelisks.
