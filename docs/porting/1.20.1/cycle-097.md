# Cycle 097 — Restore Niard placement and saved state

## Scope

Exercise the built-in Niard experiment on a packaged Forge 47.4.28 dedicated server. Verify opt-in selection, hopper input, source placement, exact energy and fluid accounting, range and redstone controls, and saves with a partially filled tank. Compare with a world created using default packs.

## Changes

- Place water directly below the machine at radius zero. Rebuild the placement iterator whenever saved block or item data supplies a radius. Saves without a custom radius use the machine's default radius of five.
- Require 1,500 FE and a full bucket before placing a source. Commit the full cost once the world accepts the block. Extend the interval as needed to keep average consumption within the capacitor's configured energy rate. Leave protected existing sources, failed placements and unloaded chunks alone.
- Restore tank data before world attachment without broadcasting a block update. Refresh the tank status after the block entity attaches to the level.

## Commands

```sh
./gradlew :enderio:assemble --console=plain > build/cycle-097-baseline-build.log 2>&1
./gradlew build --continue --console=plain > build/cycle-097-build-final.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.96-1.20.1+9586742ecd.jar
port_forge=build/port-runtime/cycle-047-legacy-discover

python3 scripts/porting/packaged_niard.py --name cycle-097-enabled --port 25870 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --enable-niard
python3 scripts/porting/packaged_niard.py --name cycle-097-default --port 25871 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar"
```

## Results

- The original jar placed a water source with 127 FE and charged only 10 FE. Another Niard with saved radius one placed outside that radius and also charged 10 FE. When reopened with 1,250 mB already stored, native block-entity loading hit a null level inside the tank callback. Forge replaced the data with empty inventory, tank and energy fields. See `build/port-runtime/cycle-097-before-native/smoke-fresh.log`, `smoke-seed.log` and `smoke-reopened.log`.
- The Niard profile passed 20 packaged checks. A vanilla hopper supplied one water bucket. The Niard returned the empty bucket, placed one source directly below at radius zero and retained 1,200 mB and 2,000 FE. The exact balances held during another placement interval and through restart. A Niard with 127 FE retained its 1,300 mB. A placement attempt below the world floor retained 1,500 mB and 2,500 FE.
- A saved radius of one excluded a cell two blocks away. After restart, changing the radius to two placed a source there and left 250 mB and 1,600 FE. A saved redstone-controlled Niard waited until a real redstone block supplied a signal after restart, then placed one source and kept 500 FE.
- The default profile passed six checks. Native pack commands reported all three feature packs as disabled by design and named each missing flag when asked to enable it on the existing default world. Selecting the Niard pack for world creation kept the two unrelated flags disabled. All four dedicated server processes exited at 0 with empty runtime and registry error lists. See `build/port-runtime/cycle-097-{enabled,default}/niard-results.json`.
- The full build passed. Unit reports list 79 tests with zero failures or skips. All 483 native GameTests passed, including the 161 discovered main gameplay tests. See `build/cycle-097-build-final.log`.
- Tested production jar SHA-256 `3cdf585094f23888293cced3bf8e9e1b7a4fda31509b2233a28b85d649866723`.

## Remaining verification

The Niard's menu and carried item need guarded client coverage. The Enderface still needs its own opted-in reach and remote menu tests.
