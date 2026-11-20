# Cycle 241 — Painted Fence migration, waterlogging and connections

The Forge 6.2.18-beta server saves an emerald-painted fence with source water, a stone brick block to the north and an oak fence to the west. A Nether Brick Fence to the east stays disconnected. The original loot carries the old paint tag, which the port converts to typed item data when it opens a disposable copy of the world.

The packaged TCP client replants that exact loot and a new diamond-painted fence made by a stock Painting Machine recipe with a spruce template. The two sites start with real source water and the same original neighbors. The client checks the paint sprite on every baked quad and measures which edges the model's rails actually reach. Server assertions also check that oak and Nether Brick Fences update their reciprocal sides. Swapping the two fences and removing the stone changes the rendered rails on both replants without altering paint or water.

After restarting the dedicated server, a second packaged TCP session checks the rendered geometry and water again before the fixture removes the remaining oak neighbor. The two isolated posts retain their water and paint. The original Forge 6.2 placement remains in its initial state. Native loot from each replant keeps its distinct paint through restart.

## Commands

```sh
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew :enderio:reobfClientSmokeJar --max-workers=4 --console=plain

port_name=cycle-241-painted-fence
port_runtime=build/port-runtime
port_seed="$port_runtime/cycle-240-insulation-final-client"
port_client="$port_runtime/$port_name-client"
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.239-1.20.1+8ad22b6898.jar
port_qa=enderio/build/test-libraries/enderio-client-qa.jar
export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Djava.net.preferIPv4Stack=true'

mkdir -p "$port_client/config"
cp -cR "$port_seed/libraries" "$port_seed/versions" "$port_client/"
cp "$port_seed/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name "$port_name-client" --java "$port_java" \
  --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" --qa-jar "$port_qa" \
  --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 -Bu scripts/porting/packaged_painted_fence.py --name "$port_name" --port 26362 \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" \
  --legacy-jar "$port_runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar" \
  --jar "$port_jar" --launch "$port_client/launch.json"
```

## Results

- The reobfuscated client QA jar builds successfully. All 20 server assertions and 46 packaged client checks pass. The Forge 6.2 original retains both arms, paint and water. The modern stock recipe and hopper supply the diamond-painted control. Each replant spends one item and saves one matching native loot item. Both waterlogged replants keep their paint and correct arms when the neighbors change and after the server restarts.
- The client renders 26 painted quads for the north and west rails, 16 for the east rail, and six for an isolated post. Both paints use their original sprites in every state. All eight captures show zero volume in ten categories and the narrator off. Two guarded launches record zero activations, foreground samples or onscreen samples over 2,962 observations.
- All three server sessions exit normally without runtime or registry errors. Both clients and both port server sessions use the production build from cycle 240 with SHA-256 `b9aa8cce0274d3a8f7ca7db3aea55c0c03004d65c5bef9171730f8b4e9a23d80`. The independent Forge 6.2.18-beta build has SHA-256 `9d1471e6d4562d0a50f0564bc46573b87fd738bd440fe19397d31847da20f172`. See `build/port-runtime/cycle-241-painted-fence/painted-fence-results.json` and the exercise and verify directories in `build/port-evidence/`.

## Next verification

Reconcile the pressurized and ender fluid conduit rows with the packaged tier-lock and recovery evidence. Verify which remaining stock survival crafting, client setup and throughput paths still lack direct coverage.
