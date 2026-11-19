# Cycle 189 — Forge 6.2 Enderman Heads and loot

The released Forge 6.2.18-beta jar saves one standing head at rotation 5 and one wall head facing east in a disposable world. The release's own `loot mine` function generates one head from each placed block. Two distinct fixture tags identify the original loot stacks in a vanilla chest. The historical save preserves `enderio:ender_skull` on both positions.

The port opens a copy of the save and checks both original block states, block entities and tagged item stacks. The headless route runs the current loot tables from both preserved positions and restarts with both placed heads and all four loot stacks. The multiplayer route takes the two legacy loot stacks from the chest through native inventory clicks. The survival player sees and mines both original placed heads, collects their two untagged drops, and places the two tagged historical items at new positions. The current item places a standing head at rotation 8 and a north-facing wall head. A second dedicated session checks the replanted blocks and the exact saved player inventory. [Cycle 188 wall placement fix](cycle-188.md)

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_forge="$port_runtime/cycle-047-legacy-discover"
port_legacy="$port_runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar"
port_jar=enderio/build/libs/enderio-8.2.14.188-1.20.1+a09b6041d6.jar
port_guarded="$port_runtime/cycle-188-ender-skull-verified-client"
port_client="$port_runtime/cycle-189-legacy-skull-verified-client"

python3 -u scripts/porting/packaged_legacy_ender_skull.py --name cycle-189-legacy-skull-headless --port 26176 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" \
  --jar "$port_runtime/cycle-188-ender-skull-verified/mods/enderio.jar"
./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-189-legacy-skull-build.log 2>&1
mkdir -p "$port_client/config"
cp -cR "$port_guarded/libraries" "$port_guarded/versions" "$port_client/"
cp "$port_guarded/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name cycle-189-legacy-skull-verified-client \
  --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 -u scripts/porting/packaged_legacy_ender_skull.py --name cycle-189-legacy-skull-verified --port 26178 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" --jar "$port_jar" \
  --launch "$port_client/launch.json"
```

## Results

- The headless run passes eight server assertions across the legacy save, converted world and port restart. Both blocks keep the original facing or rotation, both historical loot stacks keep their tags, and the current loot tables return one head per source. See `build/port-runtime/cycle-189-legacy-skull-headless/legacy-ender-skull-results.json`.
- The full build passes 79 unit tests and 540 native runs, including all 213 discovered main gameplay cases. The client driver is packaged separately from the distributable. See `build/cycle-189-legacy-skull-build.log`.
- The first TCP attempt uses `cycle-189-legacy-skull-final` and port 26177 and passes five server assertions through conversion. The Forge handshake starts, then the client disconnects after 30 seconds without entering the world. The native observer records zero activation, foreground or visible-window events. See `build/port-runtime/cycle-189-legacy-skull-final/legacy-ender-skull-results.json` and `build/port-evidence/cycle-189-legacy-skull-final-client/summary.json`.
- The retry passes 13 server assertions and 22 client checks. Both original positions keep their saved block entities and orientation on conversion. The survival player collects the two original stacks, mines and collects exactly one head from each old position, and uses both historical stacks for the new display. The new floor rotation and wall facing survive the dedicated restart. The closed player save holds one untagged stack of two heads. All three server sessions exit cleanly with no registry errors.
- Both peers on the retry load production SHA-256 `1e948b3c1c2136f327e37e9fd95856a8bc1cdf66d6b427dfb996b2c10ae681a4`. Both runs use historical SHA-256 `9d1471e6d4562d0a50f0564bc46573b87fd738bd440fe19397d31847da20f172`. All four renderer captures show every volume category at zero and the narrator off. The native observer records zero activation, foreground or visible-window events across 2,165 samples. See `build/port-runtime/cycle-189-legacy-skull-verified/legacy-ender-skull-results.json` and `build/port-evidence/cycle-189-legacy-skull-verified-client/summary.json`.

## Next verification

Craft Pulsating and Vibrant Crystals with the stock survival recipes and grind the original crystals into the matching powders in the SAG Mill.
