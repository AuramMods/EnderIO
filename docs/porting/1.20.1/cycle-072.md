# Cycle 072 — XP and Weather Obelisks

## Scope

Verify exact experience conversion, Forge fluid transfer, the survival XP and Weather menus, historical XP Obelisk saves and all three Weather recipes across a native server restart.

## Changes

- Calculate how many whole XP points are available or fit before mutating the tank. Withdrawals leave sub-point fluid in the machine. Deposits preserve the stored Forge fluid tag and charge the player only for the points that fit.
- Convert carried Forge 6.2 XP Obelisk fluid, face modes and redstone into typed item data before placement. Resolve the old fluid ID through the existing alias table, including in a fresh world without a Forge world-remap entry.
- Load Weather Obelisk tank contents before world attachment. Defer the tank's block updates to an attached server level and mark the chunk dirty when the machine pays a weather tick.
- Add native regressions for partial XP withdrawal, near-capacity tagged deposits, a ten-level round trip, carried legacy data and detached Weather Obelisk loading. The packaged probe uses Forge fluid capabilities and checks the real weather, firework entity, ingredient remainders and progress through restart.

## Commands

```sh
./gradlew :enderio:verifyGameplayTests --console=plain
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_old=build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar
port_jar=enderio/build/libs/enderio-8.2.14.71-1.20.1+62dfdf3423.jar
python3 scripts/porting/packaged_obelisks.py --name cycle-072-baseline-legacy --port 25768 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_old" \
  --jar enderio/build/libs/enderio-8.2.14.70-1.20.1+730e07956d.jar
mkdir build/port-runtime/cycle-072-client-migration
cp -R build/port-runtime/cycle-072-client-calibrated/libraries build/port-runtime/cycle-072-client-calibrated/versions \
  build/port-runtime/cycle-072-client-migration/
python3 scripts/porting/client/prepare_packaged.py --name cycle-072-client-migration --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_obelisks.py --name cycle-072-headless --port 25771 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar"
python3 scripts/porting/packaged_obelisks.py --name cycle-072-migration --port 25773 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_old" --jar "$port_jar" \
  --launch build/port-runtime/cycle-072-client-migration/launch.json
```

## Results

- Both XP regressions failed on the original code. A 27 mB tank lost all 27 mB when the player received one point. A tagged tank with room for one point rejected the entire deposit. See `build/cycle-072-baseline-xp.log`.
- The first historical fixture used the port's fluid ID with the official 6.2 jar. The corrected fixture uses the native `enderio:xp_juice`, which the port resolves as XP juice and loads with its original tag. See `build/port-runtime/cycle-072-baseline/legacy/smoke-legacy.log` and `build/port-runtime/cycle-072-baseline-legacy/`.
- The previous production jar started a rain task and saved it with both fireworks and 1,237 mB of cloud seed. On reopening, tank deserialization tried to send a block update before Forge attached the weather machine to its level. The resulting exception discarded the block entity. See `build/port-runtime/cycle-072-baseline-legacy/port/smoke-reopened.log`.
- The first client run passed the partial and all-XP transfers. Its 210-point vanilla grant settled just below the level-12 threshold. The expanded fixture sets level 12 and zero progress explicitly and verifies all three native XP fields before sending the ten-level button clicks. See `build/port-evidence/cycle-072-fixed-client/summary.json`.
- The calibrated run preserved the exact XP balance through every menu action. Its survival placement exposed the legacy item loss. The freshly placed block's default fluid and I/O fields took precedence over the old fields while the original 6.2 block retained them. The new Forge GameTest then exposed that the original alias also needed explicit resolution in a fresh world. See `build/port-runtime/cycle-072-calibrated/port/smoke-inspect-replant.log` and `build/cycle-072-verified-build.log`.
- The core packaged run before the carried-item fix passed 15 assertions across three clean server exits. The XP tank accepted 27 tagged millibuckets and rejected water. The Weather Obelisk waited for a rocket and retained its paid ticks under a roof. After restart, it completed rain, thunder and clear in sequence. Each fired a native firework and spent 1,000 mB and one rocket from a 1,237 mB and two-rocket starting balance. The final balance survived reopening. See `build/port-runtime/cycle-072-headless/obelisk-results.json`.
- The final build passed 78 ordinary tests and 438 required Forge GameTests with zero failures or skips. The native profiles ran 171 core, seven Mekanism, 115 registry, 118 gameplay and 27 addon tests. See `build/cycle-072-final-build.log` and the modules' `build/test-results/test/` XML.
- The final packaged run passed 24 server assertions across four clean exits. The carried 6.2 obelisk placed with 1,573 mB, its fluid tag, its disabled face and its redstone setting. It retained the fluid on the final restart. The fresh XP Obelisk retained its 7 mB remainder and the Weather Obelisk resumed from 21 paid rain ticks. All three weather changes, their fireworks and the final save passed with the same production jar. Forge logged the existing unrelated missing item and enchantment mappings on initial 6.2 conversion. See `build/port-runtime/cycle-072-migration/obelisk-results.json`.
- The guarded client passed 22 checks across the actual XP and Weather screens. All six experience buttons conserve the balance. The two Weather captures show the paused and completed tank and rocket input, with native rain visible after completion. The native watcher recorded 3,381 samples with zero activation, foreground or visible-window events. All ten sound levels stayed at zero, with narrator and fullscreen off and the cursor uncaptured. See `build/port-evidence/cycle-072-migration-client/`.

| Artifact | SHA-256 |
| --- | --- |
| Bundled Ender IO production jar | `ab8e68bc64a068dc77ae7c80d47e9042f88c528e7c059618b850123d25a95a9e` |
| Core packaged run before the carried-item fix | `9bf3c015d9ca3ddccefe5dea264e7e6b757bc47825499252336b132fd868c842` |
| Reobfuscated client QA jar | `98102dff18d9dd4cf16fcd5e3cf5cbd4b6b02b41d0b5ee72a16427f2976a98f1` |
| Official Ender IO 6.2.18-beta | `9d1471e6d4562d0a50f0564bc46573b87fd738bd440fe19397d31847da20f172` |

## Remaining verification

Exercise the four entity obelisks with native spawn, movement and teleport events, including filter behavior, area settings and lifecycle across chunk unload and restart. Carried Weather Obelisk state, the XP erase recipe and the Weather JEI recipe and transfer remain open.
