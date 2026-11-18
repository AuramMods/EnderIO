# Cycle 061 — migrate solar panels and wired chargers

The official Forge Ender IO 6.2.18-beta jar saves four photovoltaic panels spanning all three tiers and two wired chargers. The first charger has a tagged capacitor, a partially charged input, a full output, and powered-only redstone with configured input, output and disabled faces. The second has just enough buffered energy to finish its saved staff. After a clean legacy shutdown, the runner copies the world to a disposable port installation. It checks balances and items through conversion, actual chunk eviction and reload, and a clean dedicated restart.

The optional guarded client opens the restarted world over loopback. It places a vibrant panel beside a migrated one, checks their connected models and all three item models, and opens the first wired charger. It shift-clicks the saved output into the inventory and changes the redstone mode with the GUI widget. The server checks the resulting panel connections, charged stack and machine buffer.

The wired charger's appended armor and offhand slots moved the calculated player inventory boundary. This caused shift-clicks from early player slots to miss the charger and could put collected output directly into the offhand. The menu now records the actual end of the main inventory and hotbar. The shared transfer method uses that boundary, and the native regression exercises both directions.

## Reproduction

Use Java 17, Forge 47.4.28 and the [hidden-client harness](client-harness.md). Use fresh profile names when repeating the tests. The launcher preparation can use `--install` with the official installer for a fresh client, or copy `libraries` and `versions` from an isolated Forge client.

```sh
./gradlew :enderio:assemble :enderio:reobfClientSmokeJar --console=plain
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.60-1.20.1+34ae81f064.jar
port_forge=build/port-runtime/cycle-047-legacy-discover
port_legacy=build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar
mkdir build/port-runtime/cycle-061-client-verified
cp -R build/port-runtime/cycle-051-client-verified/libraries \
  build/port-runtime/cycle-051-client-verified/versions build/port-runtime/cycle-061-client-verified/
python3 scripts/porting/client/prepare_packaged.py --name cycle-061-client-verified --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_legacy_migration.py --name cycle-061-legacy-release-path --port 25704 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --legacy-jar "$port_legacy" \
  --solar-client build/port-runtime/cycle-061-client-verified/launch.json
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain
```

To run only the dedicated fixture, use `--solar-charging` in place of `--solar-client <launch.json>`. The first run used `--name cycle-061-legacy-baseline --port 25699` with the earlier port jar `enderio-8.2.14.59-1.20.1+662cffdd8c.jar`.

## Results

- The first headless run passed 47 server assertions. The panels retained exact balances of 3,000, 3,500, 12,000 and 50,000 FE. The first charger kept its facing, I/O modes, redstone mode, 12,345 FE, and all three tagged items. The second used exactly 180 FE to complete one tagged staff. Its full staff and empty buffer survived chunk eviction and restart. The legacy, converted and restarted server processes all exited zero. Three Forge registry diagnostics during conversion concern other legacy IDs. See `build/port-runtime/cycle-061-legacy-baseline/legacy-migration-results.json`.
- The initial client waited for the charger's original buffer and timed out. The diagnostic run showed 62,345 FE after placing a second vibrant panel, which routed the migrated panel's 50,000 FE through the charger's passive world-west face. The next attempt used the wrong persisted face key. In both 6.2 and the port, a south-facing machine's world-west face uses the stored east key. The final fixture disables that face using the historical key. See `build/port-evidence/cycle-061-legacy-tcp-tcp/`, `build/port-evidence/cycle-061-legacy-diagnostics-tcp/` and `build/port-evidence/cycle-061-legacy-verified-tcp/`.
- The new shift-click GameTest failed against the original menu. `./gradlew :enderio:runGameTestServer --console=plain` discovered 108 tests and reported one failure, with the tagged assertion for a staff in the first main inventory slot. See `build/port-cycle-061-quick-move-repro.log`.
- The full build passed 78 JUnit and 428 native tests, including the new menu regression. XML reports recorded zero failures, errors or skips. See `build/port-cycle-061-build-verified.log` and each module's `build/test-results/`.
- During shutdown in the first run with the corrected face, the probe received a nested render callback and tried to read the closed menu. The probe now enters its terminal stage before disconnecting. See `build/port-evidence/cycle-061-legacy-final-tcp/` and `build/port-cycle-061-client-final-build.log`.
- The final packaged run passed 50 server assertions. The restarted array delivered all 18,500 FE from the three connected panels to the adjacent charger. The fourth panel retained 50,000 FE through the client's new placement, because the migrated charger face stayed disabled. The two connected vibrant panels used their joined models. The output from the original charger moved once to the player's hotbar, and the redstone control change spent exactly its 12,345 FE to bring the input from 40,000 to 52,345 FE. All server processes exited zero, with three unrelated legacy registry diagnostics and no other errors. The client and server used identical packaged mod bytes. See `build/port-runtime/cycle-061-legacy-release-path/legacy-migration-results.json`.
- The guarded client passed all 12 checks, with a single passing result and no ERROR or FATAL lines. The 960×540 captures show the connected panels, the migrated charger slots, and the energy and progress bars after charging. The narrator was OFF and all ten sound channels were muted. The native observer recorded 1,801 samples, zero activation, foreground or visible-window events and exit code zero. See `build/port-evidence/cycle-061-legacy-release-path-tcp/`.

| Artifact | SHA-256 |
| --- | --- |
| Official historical Forge 6.2.18-beta mod | `9d1471e6d4562d0a50f0564bc46573b87fd738bd440fe19397d31847da20f172` |
| Packaged port mod | `e882adbc1704b44c145f4104c08b8b0660e13397e9ad02ea0ce0b04bcbc9d064` |
| Test-only client probe | `c4a407564eede8eb0b8d46ac3371cbe4ea0e0ca6a841b15e4a2f5adb08003712` |

## Remaining verification

Weather and dimension cases, charged solar item drops and placement, and broader single-player and machine menu coverage remain open. The historical registry diagnostics for other features remain tracked separately.
