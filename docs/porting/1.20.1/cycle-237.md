# Cycle 237 — Hang Glider sprint dive and water entry

A survival player launches over a contained water runway on packaged Forge. The client measures local movement over five tick intervals at ordinary glide speed, with the native forward and sprint bindings held, and after releasing those bindings. The fixture also checks whether entering the pool stops gliding while the original tagged Hang Glider stays equipped.

The dedicated server accepts the actual movement packets with its default flight setting. It observes the production glide advancement and confirms that the player reaches water in the runway with full health. The test repeats after restarting both peers and restoring the original chest equipment.

## Commands

```sh
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew :enderio:reobfClientSmokeJar --max-workers=4 --console=plain
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew build :enderio:reobfClientSmokeJar --continue --max-workers=2 --console=plain

port_name=cycle-237-glider
port_runtime=build/port-runtime
port_seed="$port_runtime/cycle-235-conduit-arrow-client"
port_client="$port_runtime/$port_name-client"
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=build/cycle-237-artifacts/product.jar
port_qa=build/cycle-237-artifacts/qa.jar
export JAVA_TOOL_OPTIONS='-Djava.net.preferIPv4Stack=true -XX:ActiveProcessorCount=4'

mkdir -p "$port_client/config"
cp -cR "$port_seed/libraries" "$port_seed/versions" "$port_client/"
cp "$port_seed/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name "$port_name-client" --java "$port_java" \
  --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" --qa-jar "$port_qa" \
  --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 -Bu scripts/porting/packaged_glider_maneuvers.py --name "$port_name" --port 26331 --java "$port_java" \
  --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar" --launch "$port_client/launch.json"
```

## Results

- Both launches measure 0.054945 blocks per tick during cruise and 0.164835 during the sprint dive. Releasing the keys restores 0.054945 blocks per tick. In the pool the client observes at least six consecutive ticks without gliding. The server confirms the production advancement, the original equipped item and full health in the pool. Both launches exit cleanly with eight server assertions and 26 client checks in total. See `build/port-runtime/cycle-237-glider/glider-maneuvers-results.json`.
- The guarded launches record zero activations, foreground samples or onscreen samples over 4,700 observations. Eight frames confirm all ten volume categories at zero and narration off. The sprint and water captures show the corresponding third-person poses. See `build/port-evidence/cycle-237-glider-fresh-client/summary.json` and `build/port-evidence/cycle-237-glider-reopened-client/summary.json`.
- The full build passes 79 unit cases and 543 required Forge test runs. The tested client and server load the same production SHA-256 `5be58f9c9a8ed366467357b879601521e0977be42eee86e4d7b2106ccf5a0c37`. The latest build uses current commit metadata and has SHA-256 `b793302acde04c1b86118fed7d663b4f87466940ede5e8b28a17b7d9682ccad6`. All 15 glider and associated integration classes match the packaged verification jar byte-for-byte. The latest distributable and its nested jars contain no client smoke classes. See `build/cycle-237-glider-full-build.log`.

## Next verification

Run the packaged Hang Glider on an integrated server and verify that the equipped item and ordinary flight survive a save and reopen.
