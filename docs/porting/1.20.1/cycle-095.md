# Cycle 095 — Forge materials and Almost Unified in the SAG Mill

## Scope

Run the stock SAG Mill iron, tin and osmium recipes on a packaged Forge server. Check Mekanism materials, Almost Unified's configured Forge tag priority, and the fallback with optional mods absent.

## Changes

- Make the common material tags used by the stock SAG Mill recipes include the corresponding native Forge tags. The optional metal inputs cover aluminum, lead, osmium, tin and uranium for ingots, ore and raw materials. Ender IO's powdered copper, gold, iron and tin also join native Forge dust tags.
- Keep the stock recipe IDs and common tag names. Almost Unified can resolve either the recipe's common tag or its native Forge equivalent. A preferred item from the Forge tag must also belong to the recipe's output tag.
- Add a headless packaged regression that checks ordinary iron production, Mekanism tin and osmium, and the existing save after restart. The Almost Unified fixture changes priority between launches and reloads a datapack that restricts the tin output.

## Commands

```sh
./gradlew :enderio:assemble --console=plain > build/cycle-095-baseline-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.94-1.20.1+f2e8c8c73d.jar
forge_server=build/port-runtime/cycle-047-legacy-discover
port_mek="$HOME/.gradle/caches/modules-2/files-2.1/maven.modrinth/mekanism/uxe1WQp4/b7f12ae710888b01746507a0aa8cb375f5897158/mekanism-uxe1WQp4.jar"
port_au="$HOME/.gradle/caches/modules-2/files-2.1/com.almostreliable.mods/almostunified-forge/1.20.1-0.10.1/f76f2beb2f08d5730d72fcda80cd82191bd45e82/almostunified-forge-1.20.1-0.10.1.jar"

python3 scripts/porting/packaged_sag_unification.py --name cycle-095-before --port 25858 \
  --java "$port_java" --forge-server "$forge_server" --jar "$port_jar" \
  --mekanism-jar "$port_mek" --almost-unified-jar "$port_au"

./gradlew build --continue --console=plain > build/cycle-095-build-fixed.log 2>&1
./gradlew :enderio:runData :enderio-modded-conduits:runData --console=plain > build/cycle-095-datagen-fixed.log 2>&1

python3 scripts/porting/packaged_sag_unification.py --name cycle-095-unified --port 25859 \
  --java "$port_java" --forge-server "$forge_server" --jar "$port_jar" \
  --mekanism-jar "$port_mek" --almost-unified-jar "$port_au"
python3 scripts/porting/packaged_sag_unification.py --name cycle-095-mekanism --port 25860 \
  --java "$port_java" --forge-server "$forge_server" --jar "$port_jar" --mekanism-jar "$port_mek"
python3 scripts/porting/packaged_sag_unification.py --name cycle-095-core --port 25861 \
  --java "$port_java" --forge-server "$forge_server" --jar "$port_jar"
```

The baseline command ran after assembling commit `f2e8c8c73` and before rebuilding the jar at the same path. The original jar is preserved under `build/port-runtime/cycle-095-before/mods/enderio.jar`.

## Results

- On the original jar, the iron control produced two dusts with 1,200 FE remaining. The tin mill still held both Mekanism ingots and all 6,000 FE. See `build/port-runtime/cycle-095-before/sag-unification-results.json` and its `smoke-fresh.log`.
- The initial build with the change found a duplicate Forge silicon tag during resource processing. Datagen now leaves the existing silicon definition in place. The final build and both datagen tasks passed. The unit reports contain 79 tests with zero failures or skips. All 483 native tests passed across the core, Mekanism, registry, gameplay and addon profiles. See `build/cycle-095-build-fixed.log` and `build/cycle-095-datagen-fixed.log`.
- Forge 47.4.28 with Mekanism 10.4.16.80 and Almost Unified 1.20.1-0.10.1 passed nine assertions. With Mekanism first in the Forge tin-tag priority, two tin ingots produced two Mekanism dusts. A live datapack reload restricted the recipe's common output tag and a new mill produced Ender IO dust. After restart with Ender IO first, a fresh job produced Ender IO dust and the first mill kept its Mekanism output.
- Mekanism without Almost Unified passed eight assertions, including Ender IO tin and Mekanism osmium outputs across restart. The core-only profile passed three checks, including exact iron output and energy balance across restart. Every job checked two items produced for 4,800 FE consumed. All six headless server processes exited at 0 without logged errors. Each profile's `build/port-runtime/cycle-095-*/sag-unification-results.json` records the assertion conditions and both server logs.
- Packaged Ender IO jar SHA-256 `14b0d99140fa318617c7dd84578a1b53c5505880b8a013f1982eec3d17b6138f`. Almost Unified jar SHA-256 `227dca84c5b20a725e60ccb3068612d748ee2f7d76e240f3076468620fdc7ac2`. Mekanism jar SHA-256 `3b0d191fa4a45e662f9725991d76a30192f30186abb2ba694b873b5f7b9358d2`.

## Remaining verification

The packaged fixture covers the stock ingot paths for tin and osmium. The other optional ores and raw materials, grinding balls and bonus outputs, hopper automation, the SAG Mill menu and JEI output display remain open.
