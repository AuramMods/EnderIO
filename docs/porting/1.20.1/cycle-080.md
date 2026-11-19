# Cycle 080 — Connected tree and stalk harvests

## Scope

Keep Farming Station tree harvests resumable when storage fills or the axe breaks. Verify native foliage drops, tool durability, sapling replacement and capped stalk crops. Exercise the paused inventories and plant state through a chunk unload and a dedicated server restart.

## Changes

- Discover connected logs and leaves breadth-first from the trunk base. Harvest the result in reverse discovery order so an interruption leaves each remaining block connected to the base. A working axe is required to start or continue. The leaves still use the equipped shears, and durability is spent after each accepted drop.
- Find the topmost connected cactus, bamboo or sugar cane segment regardless of the block above it. Use each segment's actual block state for loot and leave the growing base in place.
- Add three Forge regressions covering output capacity, a broken axe and capped cane. Add a packaged dedicated-server fixture that includes a branch crossing a chunk boundary and reports exact tool damage, inventory counts and remaining plot state.

## Commands

```sh
./gradlew :enderio:verifyGameplayTests --console=plain
./gradlew build --continue --console=plain
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.79-1.20.1+4abda4e624.jar
python3 scripts/porting/packaged_farming_station_harvest.py --name cycle-080-harvest-verified --port 25799 \
  --java "$port_java" --forge-server build/port-runtime/cycle-047-legacy-discover --jar "$port_jar"
python3 scripts/porting/packaged_farming_station_souls.py --name cycle-080-soul-regression --port 25800 \
  --java "$port_java" --forge-server build/port-runtime/cycle-047-legacy-discover --jar "$port_jar"
```

## Results

- Before the fix, the full-output case removed the trunk base while the upper logs remained standing. The last-use axe harvested a different block and left the branch tip behind. Capped sugar cane returned `IGNORED`. See `build/cycle-080-capped-reproduction-results.xml`.
- The full build passed 79 unit tests and 464 required Forge GameTests with zero failures or skips. The native counts were 171 core, seven Mekanism, 117 registry, 142 gameplay and 27 addon tests. See `build/cycle-080-build.log`.
- The first packaged tree fixture installed two saplings and included other available dirt plots. The station collected exactly the highest log and kept the connected trunk, but the flank plot used a sapling before the interrupted inventory assertion. The final fixture restricts dirt to the tree bases. See `build/cycle-080-packaged-inspection.log`.
- The final packaged harvest fixture passed all nine assertions. One item of output capacity accepted the highest log while two connected logs remained. One remaining golden axe use collected the outer log on a branch crossing a chunk border, and the shears collected one leaf block. Cane, bamboo and cactus all yielded two segments and retained their bases and glass caps. All affected chunks unloaded and reloaded, then both partial trees survived a server restart. Freeing output added exactly two more logs and replanted one sapling. A replacement axe collected the remaining three logs, leaving a total of four logs and one leaf in the branch station. See `build/port-runtime/cycle-080-harvest-verified/farming-station-harvest-results.json`.
- All 11 packaged soul regression assertions still pass, including Fortune on an equipped axe, live rules and restart. Both profiles used Forge 47.4.28 and Java 17 with the production jar as the only installed mod. All four server exits were 0 with zero runtime or registry errors. The jar SHA-256 is `c87bc202a450b60c22d1c9cf388013937a5d2d5c0f6634c59f6ea4b6754ad195`. See `build/port-runtime/cycle-080-soul-regression/farming-station-souls-results.json`.

## Remaining verification

The Farming Station's other crop families, native placement of a carried station, the client menu and range visualization still need coverage.
