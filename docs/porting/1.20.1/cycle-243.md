# Cycle 243 — Bundled redstone gates and fluid conduit menus

The packaged server places a Vibrant inlet and an Energetic outlet across a chunk boundary. The same two bundles carry redstone. A vanilla lever on the outlet bundle drives the blue redstone channel, away from the inlet, so the fluid extractor sees the bundled signal through the network. The fixture gives the fluid ports their stock defaults.

The packaged TCP client opens the fluid arms and uses the stock widgets to set insertion, extraction, channel colors, insertion priority and the redstone mode. Matching blue fluid channels stay blocked while the gate listens on red. Changing the gate to blue passes tagged water. Changing the outlet to green stops the transfer while blue redstone is still active. The test then switches to Active without signal and verifies both live lever transitions. Before restart, a powered lever holds exactly 730 mB at the source. A second client reads the saved settings in both menus, and the lever releases the waiting batch.

The original build preserves the edited settings but stops at the signal release step. The bundled redstone ticker updates its own output receivers. Fluid routes cache extraction permission, and the source bundle never receives a local neighbor update when that remote signal changes. The bundled ticker now refreshes routing on each loaded bundle when the set of powered channels changes. Ordinary neighbor updates use the same routing refresh. The native test also checks transition from unpowered to powered and back without reopening a menu.

## Commands

```sh
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew :enderio:reobfClientSmokeJar --max-workers=4 --console=plain
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew :enderio:verifyGameplayTests --max-workers=2 --console=plain
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew build :enderio:reobfClientSmokeJar --continue --max-workers=2 --console=plain

port_name=cycle-243-controls-fixed
port_runtime=build/port-runtime
port_seed="$port_runtime/cycle-242-fluid-verified-client"
port_client="$port_runtime/$port_name-client"
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.242-1.20.1+403fe988b7.jar
port_qa=enderio/build/test-libraries/enderio-client-qa.jar
export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Djava.net.preferIPv4Stack=true'

mkdir -p "$port_client/config"
cp -cR "$port_seed/libraries" "$port_seed/versions" "$port_client/"
cp "$port_seed/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name "$port_name-client" --java "$port_java" \
  --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" --qa-jar "$port_qa" \
  --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 -Bu scripts/porting/packaged_fluid_conduit_controls.py --name "$port_name" --port 26366 \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" \
  --jar "$port_jar" --launch "$port_client/launch.json"
```

## Results

- The first fixture assumes the default conduit config is serialized, but the native save omits it. The next fixture aims at a shared connector plate. Matching the actual default representation and targeting the individual fluid arm lets the real menus open. The first native test also used plain block placement to add a second conduit and could never attach it to the existing bundle. It now uses the bundle's conduit insertion method.
- In the packaged reproduction, switching from red to blue releases 1,440 mB, and restoring the blue outlet releases another 1,920 mB. With inverted control, removing the blue signal leaves all 2,100 mB queued at the source and the destination remains at 3,360 mB. See `build/port-runtime/cycle-243-controls-arm/fluid-conduit-controls-results.json` and the two saved tank states.
- The full patched build passes 79 unit cases and 547 required Forge runs, including all 219 main gameplay cases. The native regression opens and closes a fluid route from a remote bundled blue lever without any intervening configuration change. See `build/cycle-243-controls-fixed-build.log` and `enderio/build/test-results/gameTestServer/results.xml`.
- The final packaged run passes 14 server assertions and 58 client checks. The previously stuck 2,100 mB reaches the destination when the remote lever switches off, raising the total to 5,460 mB with the tag intact. With the lever powered and 730 mB waiting, the dedicated restart restores both menus, the blue channels and insertion priority 2. Switching off again delivers exactly 730 mB, for a total of 6,190 mB. See `build/port-runtime/cycle-243-controls-fixed/fluid-conduit-controls-results.json`.
- Both server sessions exit cleanly without runtime or registry errors. Both clients load the same patched production jar as the server and record eight muted captures with the narrator off. The observer reports zero activation, foreground or onscreen events over 3,833 observations. The tested production SHA-256 is `0f3224440f33b7d5e5d6f14e2bc18d44f7c94884792a7c861e30d89f31a32c09`. The production and nested jars exclude the client smoke code.

## Next verification

Exercise a saved powered gate while the remote redstone chunk is evicted and reloaded. Verify the first transfer after the source chunk begins ticking and after its remote signal returns.
