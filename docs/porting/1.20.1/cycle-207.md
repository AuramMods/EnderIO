# Cycle 207 — Stock Enchanter recipes and levels

The fixture reads all 36 stock Enchanter recipes at the fixed Ender IO 1.21.1 baseline. Minecraft 1.21.1's built-in enchantment definitions supply their maximum levels and minimum enchantment costs. The runner derives the normal Enchanter XP and lapis payments from those independent inputs and uses a representative item from each applicable ingredient tag. The stock Sweeping Edge enchantment has the `minecraft:sweeping` ID in Forge 1.20.1.

The packaged Forge server rejects a short lapis input and a short catalyst input. A survival client then opens the native Enchanter and collects each expected enchanted book. The client checks the synchronized recipe, enchants and cost. The server checks the exact ingredient remainders and levels after every take. The fixture saves an unpaid preview at the midpoint, opens it on a new server process and collects it without reseeding. The test archives each actual book and reads the final slots from the closed world after another restart.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_jar=enderio/build/libs/enderio-8.2.14.206-1.20.1+ace85e2dcd.jar
port_source="$port_runtime/cycle-206-stock-spawner-client"
port_run=cycle-207-stock-enchanter
port_client="$port_runtime/$port_run-client"

./gradlew build --continue --console=plain > build/cycle-207-enchanter-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-207-enchanter-qa.log 2>&1
mkdir -p "$port_client/config"
cp -cR "$port_source/libraries" "$port_source/versions" "$port_client/"
cp "$port_source/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name "$port_run-client" \
  --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 -u scripts/porting/packaged_enchanter_catalog.py --name "$port_run" --port 26213 \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar" --launch "$port_client/launch.json" \
  --baseline-minecraft build/port-evidence/cycle-029-minecraft-1.21.1/client.jar
```

## Results

- The full build passes 79 unit tests and 540 native Forge runs, including all 213 discovered main gameplay tests. The dedicated client probe reobfuscates. See `build/cycle-207-enchanter-build.log` and `build/cycle-207-enchanter-qa.log`.
- The two clients pass 224 checks, and the runner passes 227 assertions over three clean dedicated server sessions. Each of the 108 stock books matches the baseline enchantment and level. Across all takes the server debits 108 writable books, 825 lapis, 2,105 catalyst items and 1,867 XP levels. The depleted inputs produce no extra books. The final closed world preserves all 108 original books in their archive slots. See `build/port-runtime/cycle-207-stock-enchanter/enchanter-catalog-results.json`.
- The guarded clients record 5,453 native samples with zero activation, foreground or visible-window events. All four captures have all ten sound categories at zero and narrator off. The summaries and captures are in `build/port-evidence/cycle-207-stock-enchanter-fresh-client/` and `build/port-evidence/cycle-207-stock-enchanter-reopened-client/`.
- The production jar used by all sessions has SHA-256 `1bf26efe1a392fb46df905c9bc91b93f96e55fceaed6b47e228c013356477a76`. It contains no client probe classes. The Minecraft 1.21.1 input used to derive the expected values has SHA-256 `499f6897d1837516680f3114072d8106e11c9adcd933fe5cf051b551089b0c99`.

## Next verification

Exercise Electromagnet attraction, item pickup and active FE draw in survival. The current evidence covers toggle state, Curios priority and restart, and the same keybinding without Curios.
