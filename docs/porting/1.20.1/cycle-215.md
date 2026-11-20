# Cycle 215 — Stock Niard and obelisk factory resets

The dedicated world opts into the Niard pack at creation. The player joins in survival with limited crafting. The six stock shapeless reset recipes start locked, then each becomes available when the player mines and picks up the corresponding placed machine. The workshop uses the original item throughout.

The Niard starts with 3,677 FE, 1,400 mB of lava, an empty bucket, a capacitor and configured redstone and range. The XP Obelisk starts with 1,573 mB of tagged XP Juice and two configured faces. The Inhibitor, Aversion, Relocator and Attractor Obelisks have different charge balances and ranges. The three filtered obelisks also carry the original tagged soul filters.

After each reset, the server checks that exactly one untagged machine is present and that its contents and XP were discarded. The player places the result normally and opens its native menu to check the restored defaults. The dedicated restart checks the replanted blocks and the saved player inventory.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_jar=enderio/build/libs/enderio-8.2.14.214-1.20.1+ba89ca0f7e.jar
port_source="$port_runtime/cycle-214-stock-fluid-buckets-client"
port_run=cycle-215-stock-machine-resets
port_client="$port_runtime/$port_run-client"

./gradlew build --continue --console=plain > build/cycle-215-factory-resets-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-215-factory-resets-qa.log 2>&1
mkdir -p "$port_client/config"
cp -cR "$port_source/libraries" "$port_source/versions" "$port_client/"
cp "$port_source/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name "$port_run-client" \
  --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 -u scripts/porting/packaged_machine_resets.py --name "$port_run" --port 26233 \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar" \
  --launch "$port_client/launch.json"
```

## Results

- The full build passes 79 unit tests and 541 native Forge GameTest runs. The guarded client probe reobfuscates. See `build/cycle-215-factory-resets-build.log` and `build/cycle-215-factory-resets-qa.log`.
- The packaged run passes 44 server assertions and 115 client checks on Forge 47.4.28. Each populated drop unlocks its own recipe and resets to one untagged output. The Niard returns to zero FE, an empty tank and inventory, and a hidden radius of five. The XP Obelisk returns to an empty tank and six default side modes. The four ranged obelisks return to zero FE, empty slots, their stock pull modes and a hidden radius of zero. All six return to Always Active and preserve their defaults after restart. The original pickaxe is the only saved inventory item and the player's XP total remains zero. Both server boots exit cleanly with no runtime or registry errors. See `build/port-runtime/cycle-215-stock-machine-resets/machine-resets-results.json`.
- All twelve captures record the narrator off and every volume category at zero. The native watcher records 2,332 samples with zero activation, foreground or onscreen events. See `build/port-evidence/cycle-215-stock-machine-resets-client/`.
- The client and server use the same production jar with SHA-256 `7858ca3090b7fc46d6be562bcf9dcf68badaca428afb542c922e2a7b63e0426f`. The production jar contains no client probe classes.

## Next verification

Exercise the Weather Obelisk's shipped recipe category in packaged JEI. Check fluid and catalyst lookup, rocket transfer and the resulting jobs.
