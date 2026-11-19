# Cycle 187 — Wireless Charger and antenna progression

The fixture opens a disposable copy of [cycle 186](cycle-186.md) and retains its original Sentient Ender in the survival inventory. It supplies the remaining stock crafting ingredients and a Basic Capacitor. The survival player uses the normal recipe book with limited crafting enabled. The stock Wireless Charger and standard antenna each use one supplied Ender Resonator, and the advanced antenna uses the original Sentient Ender. The client checks the exact shaped grids and the ingredients spent.

The player places the original charger, installs its capacitor in the native menu, and places each original antenna above the same machine. Survival mining removes the standard antenna between the two placements. The server measures the same tagged Travel Staff at controlled offsets. Each measurement samples game time, charger FE and staff FE together in one server tick and reconciles the result with the charger's ordinary 2 FE/tick upkeep. The fixture clears charger power between measurements. The restart checks the installed advanced antenna and capacitor, range, and exact saved staff charge before measuring the restored charger in use.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_forge="$port_runtime/cycle-047-legacy-discover"
port_jar=enderio/build/libs/enderio-8.2.14.186-1.20.1+85613e0272.jar
port_world="$port_runtime/cycle-186-soul-components-final/port-spawners"
port_guarded="$port_runtime/cycle-186-soul-components-final-client"
port_client="$port_runtime/cycle-187-antenna-synced-client"

./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-187-antenna-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-187-antenna-qa-rebuild.log 2>&1
mkdir -p "$port_client/config"
cp -cR "$port_guarded/libraries" "$port_guarded/versions" "$port_client/"
cp "$port_guarded/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name cycle-187-antenna-synced-client \
  --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 -u scripts/porting/packaged_antenna_progression.py --name cycle-187-antenna-synced --port 26172 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --source-world "$port_world" \
  --launch "$port_client/launch.json"
```

## Results

- The full build passes 79 ordinary unit tests and 540 native runs, including all 213 discovered main gameplay cases. The production jar contains no client QA classes. See `build/cycle-187-antenna-build.log`.
- The first multiplayer run completes all three survival crafts. The client's first capacitor capacity check fails immediately after the native insert. The saved charger already contains the Basic Capacitor and has the correct 16-block range. The revised fixture waits for the inventory, capacity and range packets within the same menu before asserting the combined state. See `build/port-evidence/cycle-187-antenna-final-exercise-client/result.json` and the world's saved charger block entity.
- The final packaged run passes 34 server assertions and 32 client checks across two dedicated sessions. The bare charger reaches eight blocks and leaves the staff unchanged at 24. The standard antenna charges at 24 and leaves the staff unchanged at 40. Normal mining takes one iron pickaxe durability, restores 16 blocks and leaves the staff unchanged at 24 again. The advanced antenna charges at 40 and leaves the staff unchanged at 52. All eight sampled energy balances account for the exact staff gain and 2 FE/tick upkeep.
- The reopened world retains the advanced antenna, the native capacitor, the 48-block range and the staff's 2,280 FE. The same charger delivers another 660 FE over the 11-tick sample at 40 blocks. The final saved staff has 3,060 FE, including the ticks between that sample and power clearing. Both server sessions exit with zero runtime or registry errors.
- Both peers use production SHA-256 `b494dace7a6b7ae093c8d43261d1285dc4510b61f80b193b85da346e94813f78`. The five captures show every sound category at zero and narrator off. The native observers record zero activation, foreground or visible-window events across 4,026 samples. See `build/port-runtime/cycle-187-antenna-synced/antenna-progression-results.json` and the two `build/port-evidence/cycle-187-antenna-synced-*-client/summary.json` reports.

## Next verification

Craft the stock Dark Steel Sword and obtain the Enderman Head through its native mob-drop behavior. Check the head's floor and wall placement, then use the resulting stock item in the Ender Resonator recipe.
