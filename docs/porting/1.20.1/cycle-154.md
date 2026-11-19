# Cycle 154 — Run the starter survival recipes and first energy route

Two fields in a disposable Forge world exercise the stock bedrock and deepslate fire-crafting recipes under the default fire rule. The survival player picks up actual bedrock drops. The fixture supplies vanilla crafting materials and unlocks the recipes for the native recipe book. The player crafts four iron gears, two void chassis, two basic capacitors, a Stirling Generator, an Alloy Smelter and eight conduit binder composites.

The starter machines are placed in survival. Native hoppers install the crafted capacitors and feed the generator and smelter. The generator burns one coal to make four Conductive Alloy Ingots and leaves 17,600 FE after the two alloying batches. A vanilla blast furnace turns the crafted composites into 16 binders. The player uses three of the four ingots and six of the binders to craft eight tagged energy conduits.

The player places two conduits across a chunk boundary. Both direct machine faces are disabled and the machines connect through the two sections. The generator burns one more coal. The receiver gains exactly 24,000 FE through the conduit route and retains its full 41,600 FE after restart.

## Commands

```sh
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-154-starter-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-154-starter-qa.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_jar=enderio/build/libs/enderio-8.2.14.153-1.20.1+f75ee9fcff.jar
port_client=build/port-runtime/cycle-154-starter-retry-client
port_previous=build/port-runtime/cycle-153-painted-travel-client
mkdir -p "$port_client/config"
cp -cR "$port_previous/libraries" "$port_previous/versions" "$port_client/"
cp "$port_previous/config/fml.toml" "$port_client/config/fml.toml"
python3 scripts/porting/client/prepare_packaged.py --name cycle-154-starter-retry-client \
  --java "$port_java" --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_starter_progression.py --name cycle-154-starter-retry --port 26044 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --launch "$port_client/launch.json" \
  > build/cycle-154-starter-retry.log 2>&1
```

## Results

- The full build passes 79 unit cases and 519 native runs, including all 192 main gameplay cases. The revised remapped client QA jar builds successfully.
- Independent headless probes produce authentic Grains of Infinity on bedrock and deepslate with vanilla fire ticks both enabled and disabled. The default-rule world saves all 36 bedrock bases and changes one successful deepslate base to cobblestone. The logs are `build/cycle-154-{default-fire,fire-probe}.log`.
- The initial guarded run collects 17 real grains, then stalls before opening the crafting table immediately after the test teleport. The revised test waits eight player ticks and verifies block use before starting the first recipe. It reports per-recipe timeouts and completes the crafting sequence.
- The final packaged run passes 13 server assertions and 40 client checks. The player collects 21 grains and spends 16 on the starter recipes. Both crafted capacitors are installed by vanilla hoppers. The first coal makes four Conductive Alloy Ingots and leaves 17,600 FE in the smelter. The blast furnace makes 16 binders from the eight crafted composites. The table yields eight energy conduits with the correct typed data and consumes three ingots and six binders.
- The two crafted conduits join across the chunk boundary and deliver exactly 24,000 FE from the second coal. Both direct faces are disabled. A dedicated restart restores the route and all 41,600 FE in the receiver. The saved player inventory contains five grains, one alloy ingot, ten binders and six conduits of the energy type.
- The two final servers and the final client exit zero with no runtime or registry errors. The native observer records zero activation, foreground or visible-window events in 5,254 samples. Both captures confirm that the narrator is off and all sound categories are at zero. The packaged runtime jar SHA-256 is `e55732958f545bb8ca5eb2b2e93b3d10080ac027feaa2b2d55f7a57fb55302be`.

The final server report is `build/port-runtime/cycle-154-starter-retry/starter-progression-results.json`. The guarded client report and two captures are under `build/port-evidence/cycle-154-starter-retry-client`.

## Next verification

Extend packaged progression to energetic and vibrant alloys, capacitor upgrades and both higher energy-conduit tiers. Exercise the upgrade recipes, live connections and measured transfer limits.
