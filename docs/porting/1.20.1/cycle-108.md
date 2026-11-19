# Cycle 108 — Keep historical materials and icons in saved inventories

Forge reported 17 missing Ender IO entries when opening the copied 6.2.18-beta Enchanter world. Eleven were inventory items. Restore black paper, wooden and stone compound gears, powdered cobalt, and the seven historical Enderface creative tab icons as distinct items under their original registry IDs. The historical icons use the existing hidden creative-icon item class.

Reuse the original textures and English names, and generate native item models. Restore the wood gear, stone gear and cobalt dust tags in the `forge` and `c` namespaces, so tagged ingredients from existing datapacks can accept the migrated stacks.

The test saves 11 distinct named-ID stacks with individual counts and NBT markers in a vanilla barrel using the original Ender IO jar. It copies that world before running the port. Native item predicates check the material tags. The converted world also moves a separate saved cobalt stack through a vanilla hopper on restart.

## Commands

```sh
./gradlew :enderio:runData --console=plain > build/cycle-108-datagen.log 2>&1
./gradlew build --continue --console=plain > build/cycle-108-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_before=build/port-runtime/cycle-107-legacy-client/mods/enderio.jar
port_jar=enderio/build/libs/enderio-8.2.14.107-1.20.1+ac80a801b9.jar
python3 scripts/porting/packaged_legacy_items.py --name cycle-108-baseline --port 25904 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_forge/mods/enderio.jar" --jar "$port_before"
python3 scripts/porting/packaged_legacy_items.py --name cycle-108-final --port 25905 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_forge/mods/enderio.jar" --jar "$port_jar"
```

## Results

- The previous port loads the copied world with the original barrel empty. Forge reports 11 missing item IDs and six missing enchantment IDs. See `build/port-runtime/cycle-108-baseline/port/smoke-converted.log` and the `legacy-items-results.json` in its parent profile.
- Datagen succeeds. The full build passes 79 JUnit cases and 483 native GameTest executions, including all 161 discovered gameplay cases. See `build/cycle-108-datagen.log` and `build/cycle-108-build.log`.
- The packaged fixture passes 17 server assertions. All 11 stacks keep their slot, ID, count and NBT marker. Both Forge and common tags recognize the two gears and the cobalt dust. A vanilla hopper moves exactly seven tagged cobalt dusts after restart, and the transferred stack survives a second restart.
- The production jar SHA-256 is `7b6e95e3fa7509fb785c64443134ee2001d2a964c53feae0591f0bdf941b7b0b`. All 11 generated model references, original texture bytes and English names are verified directly in the packaged artifact. All four headless servers exit at zero. Forge reports six missing enchantments at first conversion and no registry diagnostics at either restart. See `build/port-runtime/cycle-108-final/legacy-items-results.json`.

## Remaining verification

The historical IDs `auto_smelt`, `repellent`, `shimmer`, `soulbound`, `withering` and `xp_boost` still lack migration behavior. Historical crafting routes for the restored materials also need a compatibility decision.
