# Cycle 266 — Live facade changes on a three-resource route

Four bundles straddle a chunk boundary, with matching red glass covering the two middle bundles. Each bundle carries separate item, fluid and energy conduits. Barrels, fluid tanks, a Stirling Generator and an idle Alloy Smelter give the three paths distinct endpoints. Only the generator and smelter face connected to the conduits is configured for power transfer. The fixture uses tagged paper and tagged water to track identity as well as quantity.

The dedicated server delivers a baseline batch before repainting. It then uses one native `execute` command to check that all three sources still contain resources and all three destinations have advanced, and to repaint the east facade blue under those conditions. The client checks the shared faces with matching paint, different paint, restored paint and the wrench held. The server checks each route, port setting and exact balance after repainting, after restoring the paint, and after saving and restarting. It sends a final new batch through the reopened route.

## Commands

```sh
export JAVA_HOME="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
export CI=true JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=4
./gradlew --no-daemon --no-build-cache --no-configuration-cache --max-workers=2 --console=plain \
  :enderio:assemble :enderio:reobfClientSmokeJar

export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Djava.net.preferIPv4Stack=true'
port_runtime=build/port-runtime
port_seed="$port_runtime/cycle-265-water-retry-client"
port_forge="$port_runtime/cycle-047-legacy-discover"
port_jar=enderio/build/libs/enderio-8.2.14.265-1.20.1+52b9c6ff43.jar
port_qa=enderio/build/test-libraries/enderio-client-qa.jar
port_renderer="$port_runtime/cycle-090-render-embeddium-client/mods/embeddium-UTbfe5d1.jar"
for profile in cycle-266-triple-facade-client cycle-266-triple-embeddium-client; do
  mkdir -p "$port_runtime/$profile/config"
  cp -cR "$port_seed/libraries" "$port_seed/versions" "$port_runtime/$profile/"
  cp "$port_seed/config/fml.toml" "$port_runtime/$profile/config/"
done
python3 -B scripts/porting/client/prepare_packaged.py --name cycle-266-triple-facade-client --java "$JAVA_HOME/bin/java" \
  --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" --qa-jar "$port_qa" \
  --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 -B scripts/porting/client/prepare_packaged.py --name cycle-266-triple-embeddium-client --java "$JAVA_HOME/bin/java" \
  --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" --qa-jar "$port_qa" \
  --assets "$HOME/.gradle/caches/neoformruntime/assets" --mod "$port_renderer"
python3 -B scripts/porting/packaged_facade_seams.py --name cycle-266-triple-configured --port 26409 \
  --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" --jar "$port_jar" \
  --launch "$port_runtime/cycle-266-triple-facade-client/launch.json" --transfers
python3 -B scripts/porting/packaged_facade_seams.py --name cycle-266-triple-embeddium --port 26410 \
  --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" --jar "$port_jar" \
  --launch "$port_runtime/cycle-266-triple-embeddium-client/launch.json" --transfers --embeddium
```

## Results

- The Java 17 build passes and produces runtime SHA-256 `20ce7b3142eae22e803039335716b61c21de04abc63bc1c5cc60a2847843dd2a` and QA SHA-256 `52f23b0d57ae29cd838a12ee5c69488380f38e76348dc1274f887204ff1dfcdd`. See `build/port-evidence/cycle-266-mixed-facade/build.log`.
- The first fixture supplied incomplete item port NBT and failed its setup assertion before launching a client. The corrected fixture supplies the required round-robin, self-feed and priority fields. See `build/port-runtime/cycle-266-triple-facade/smoke-fresh.log` and `smoke-diagnostic.log`.
- Stock Forge passes 14 server assertions and 13 client checks. The repaint occurs with 32 paper in each barrel from the live batch, tagged water still flowing and energy still crossing. The route delivers and accounts for 104 paper, 10,000 mB of water and 124,000 FE over four batches, including a new batch after cold restart. Both servers stop cleanly without runtime or registry errors. See `build/port-runtime/cycle-266-triple-configured/facade-seams-results.json`.
- The matching covers emit 16 vertices each. The blue repaint adds four vertices to each mesh and restoring red removes them again. The wrench view retains the matching seam. The Forge client records 1,886 focus samples with zero activations, foreground or onscreen observations. All ten volumes are zero and narration is off. See `build/port-evidence/cycle-266-triple-configured-client/summary.json` and the four captures there.
- Embeddium `0.3.31+mc1.20.1` passes the same 14 server assertions and 13 client checks, with identical material balances and mesh vertex counts. Both server sessions stop cleanly, and the silent client records another 2,392 samples with zero activations, foreground or onscreen observations. See `build/port-runtime/cycle-266-triple-embeddium/facade-seams-results.json` and `build/port-evidence/cycle-266-triple-embeddium-client/summary.json`.

## Next verification

Exercise the Attractor with another registered boss and compare the default and opt-in behavior. [Completion audit](completion-audit.md)
