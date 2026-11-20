# Cycle 211 — Stock Niard crafting and carried lava placement

The dedicated profile selects the shipped Niard feature pack when it creates the world. A survival player discovers the stock recipe from a Void Chassis under limited crafting. The native recipe book fills the nine-slot grid, and the player places the original result with its default controls and empty storage.

The native Niard menu accepts a Basic Capacitor and an original lava bucket. The supplied 3,677 FE and 400 mB reserve let the fixture measure exact accounting. The bucket fills the tank to 1,400 mB and leaves its empty container in the output slot. The player sets a visible radius of one and selects Active with Signal. Survival mining produces the original state-bearing item, and ordinary pickup and placement carry the charge, lava, capacitor, empty bucket and controls to the second site.

At the second site the cell directly below remains solid. One adjacent cell at radius one is empty. The redstone gate keeps the cell empty for at least 155 ticks. A vanilla redstone block then enables the Niard to place one lava source for 1,500 FE and 1,000 mB. The native menu sees the debit. The final dedicated restart checks the lava source and all remaining machine state.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_jar=enderio/build/libs/enderio-8.2.14.210-1.20.1+b968d0f33d.jar
port_source="$port_runtime/cycle-210-stock-magnet-orb-client"
port_run=cycle-211-stock-niard
port_client="$port_runtime/$port_run-client"

./gradlew build --continue --console=plain > build/cycle-211-niard-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-211-niard-qa.log 2>&1
mkdir -p "$port_client/config"
cp -cR "$port_source/libraries" "$port_source/versions" "$port_client/"
cp "$port_source/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name "$port_run-client" \
  --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 -u scripts/porting/packaged_niard_progression.py --name "$port_run" --port 26226 \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar" \
  --launch "$port_client/launch.json"
python3 -u scripts/porting/packaged_niard.py --name cycle-211-niard-default --port 26227 \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar"
```

## Results

- The full build passes 79 unit tests and 540 native Forge runs, including all 213 discovered main gameplay tests. The guarded probe reobfuscates. See `build/cycle-211-niard-build.log` and `build/cycle-211-niard-qa.log`.
- The enabled world passes 16 server assertions and 22 native client checks. The mined item and replanted Niard preserve 3,677 FE, 1,400 mB of lava, one Basic Capacitor, one empty bucket, a visible radius of one and Active with Signal mode. The placed lava source, the remaining 2,177 FE and 400 mB, both items and both controls survive dedicated restart. The default world passes six assertions with all three feature packs disabled across restart. Both boots in each profile exit successfully with no server or registry errors. See `build/port-runtime/cycle-211-stock-niard/niard-progression-results.json` and `build/port-runtime/cycle-211-niard-default/niard-results.json`.
- All four menu captures record the narrator off, every volume category at zero and a hidden, unfocused window. The macOS watcher records 2,932 samples with zero activation, foreground or onscreen observations. See `build/port-evidence/cycle-211-stock-niard-client/`.
- The packaged client and servers use the same production jar with SHA-256 `592eafb7637870684e3bf8d52e437996869e9c29e90ba63c4cf4259d046179e3`. The archive contains no client probe classes.

## Next verification

Exercise the Farming Station's stock survival recipe and factory reset of its carried state. Existing packaged tests cover crop operations, the native controls and survival replanting.
