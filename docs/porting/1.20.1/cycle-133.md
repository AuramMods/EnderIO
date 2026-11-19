# Cycle 133 — Restore Drain collection and saved work

Range zero now collects directly from the intake. At other ranges, each bounded scan includes the last position and reserves the intake until a full pass finds no other source to collect. Forge's bucket pickup empties a waterlogged block through its native behavior. The Drain credits an actual source removal, marks tank changes dirty and keeps the original 1,500 FE cost per bucket.

The Drain now reads Forge 6 `Range`, `RangeVisible` and `Consumed` from placed machines and uses the historical range during collection. Carried Forge 6 drops move the range, fluid, stored FE and redstone mode into the port's item data. The obsolete fluid copy is removed from the drop's block entity tag so that placement uses a single copy of the contents.

## Commands

```sh
./gradlew :enderio:runGameTestServer --console=plain > build/cycle-133-drain-before.log 2>&1
./gradlew :enderio:runGameTestServer --console=plain > build/cycle-133-drain-native.log 2>&1
./gradlew build --continue --console=plain > build/cycle-133-drain-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_jar=enderio/build/libs/enderio-8.2.14.132-1.20.1+7336263321.jar
legacy_jar=build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar
python3 scripts/porting/packaged_drain.py --name cycle-133-drain-current --port 25984 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar"
python3 scripts/porting/packaged_drain.py --name cycle-133-drain-legacy-exact --port 25987 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --legacy-jar "$legacy_jar"
```

## Results

- The before run found 179 gameplay tests and failed the four Drain scenarios. It left the range-zero intake unchanged, skipped the final scan position, deleted a waterlogged fence and treated the old range as five. The first fixed run passes all 179 tests. The full build adds the carried-drop regression and passes all 180 main gameplay tests, 502 native Forge runs across five profiles and 79 ordinary unit cases. There are no failed or skipped tests in the reported suites.
- The packaged port passes seven assertions across three headless server sessions with no runtime or registry errors. Redstone leaves the first 600 FE unspent while off. Once enabled, the underfunded Drain saves its 600 FE payment and both lava sources remain. After restart, it pays the outstanding 900 FE, keeps 500 FE, collects exactly 1,000 mB and exports it to an adjacent Forge tank. The second source remains outside the configured range. A further restart preserves the exported bucket and the empty Drain. The tested jar has SHA-256 `f1e1c80e8dec44913154b939889ba58c6d159ccf7a44d8f43d4c9c96c0607e21`. See `build/port-runtime/cycle-133-drain-current/drain-results.json`.
- The Forge 6.2.18 beta server collects the first bucket and spends the remaining 1,327 FE toward the second. The copied save preserves those balances, the visible range, the capacitor and the original machine drop. The converted Drain receives exactly 173 FE and collects the second source. A further headless restart retains 2,000 mB of lava, an intact intake, zero outstanding payment and the converted drop's data. All nine assertions pass across four server sessions with zero runtime or registry errors. See `build/port-runtime/cycle-133-drain-legacy-exact/drain-results.json`.

## Remaining verification

The Drain's multiplayer menu controls, range rendering, normal survival crafting and factory reset still need a client pass.
