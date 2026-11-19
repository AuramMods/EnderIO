# Cycle 155 — Craft and verify the higher power tiers

The alloy fixture copies the disposable starter world from cycle 154. Its Stirling Generator supplies another 48,000 FE from two coal through the original basic conduit route. Four batches produce eight Conductive Alloy Ingots. The same eight feed eight Energetic Alloy batches, and four of those ingots feed four Vibrant Alloy batches. The original smelter finishes with 16,000 FE, 12 unspent Energetic Alloy Ingots and eight Vibrant Alloy Ingots.

The survival fixture uses those actual output stacks, saved basic conduits, and supplied starter capacitors, additional binders and powdered coal. It crafts Double-Layer and Octadic Capacitors and both direct and upgrade recipes for Enhanced and Ender Energy Conduits. The two upgrade recipes receive the wrong tagged variant first. The player then swaps in the Octadic Capacitor through the Alloy Smelter menu and replaces the two live conduits twice, recovering each lower tier. One hopper-fed stick measures the delivered FE at each mixed and uniform stage.

The native GameTest uses isolated charged banks to measure consecutive-tick transfer through three uniform lines and one mixed Enhanced/Ender line. It compares the source and receiver at both samples.

## Commands

```sh
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-155-energy-verified-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_previous_jar=enderio/build/libs/enderio-8.2.14.153-1.20.1+f75ee9fcff.jar
port_jar=enderio/build/libs/enderio-8.2.14.154-1.20.1+13cb9cd391.jar
port_starter=build/port-runtime/cycle-154-starter-retry/port-spawners
port_alloys=build/port-runtime/cycle-155-alloys-conserved/port-spawners
python3 scripts/porting/packaged_energy_progression.py --name cycle-155-alloys-conserved --port 26046 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_previous_jar" --starter-world "$port_starter" \
  > build/cycle-155-alloys-conserved.log 2>&1

port_client=build/port-runtime/cycle-155-energy-client
port_previous=build/port-runtime/cycle-154-starter-retry-client
mkdir -p "$port_client/config"
cp -cR "$port_previous/libraries" "$port_previous/versions" "$port_client/"
cp "$port_previous/config/fml.toml" "$port_client/config/fml.toml"
python3 scripts/porting/client/prepare_packaged.py --name cycle-155-energy-client \
  --java "$port_java" --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_energy_progression.py --name cycle-155-energy-upgrades --port 26047 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --alloy-world "$port_alloys" \
  --launch "$port_client/launch.json" > build/cycle-155-energy-upgrades.log 2>&1
```

The capacity probe imports `load`, `route_state`, `installed_octadic`, `prepare_profile` and `run_stage` from the same harness. It copies the final `port-spawners` world to `build/port-runtime/cycle-155-octadic-charge`, binds to port 26048, loads the saved 21,920 FE route, and executes `item replace block 3039 81 8 container.0 with minecraft:coal 2`. Its final `route_state(69920, ("ender_energy", "ender_energy"))` assertion also requires the Octadic Capacitor and an empty fuel hopper.

## Results

- The full build passes 79 unit cases and 520 native runs, including 193 main gameplay cases. The new consecutive-tick GameTest measures 8,000 FE for basic, 48,000 FE for Enhanced, 192,000 FE for Ender and 48,000 FE for a mixed Enhanced/Ender path. Each receiver's increase equals its source's decrease. The discovery and result files are `enderio/build/test-results/gameTestServer/discovered-tests.txt` and `results.xml`.
- The copied starter world passes seven alloy assertions. The shipped alloy recipes spend 12,800 FE for Conductive, 38,400 FE for Energetic and 22,400 FE for Vibrant. Both coal deliver all 48,000 FE, yielding a 16,000 FE remainder without an injected charge. The source jar from cycle 154 has SHA-256 `e55732958f545bb8ca5eb2b2e93b3d10080ac027feaa2b2d55f7a57fb55302be`.
- The packaged upgrade and restart run passes 15 server assertions and 32 client checks. Both manual upgrade grids reject the wrong tagged conduit and yield eight of the expected variant with the proper input. The Octadic Capacitor increases the smelter's displayed capacity from the basic tier's 64,000 FE to 576,000 FE while preserving 16,000 FE at installation. Four in-place conduit replacements each return one lower-tier item. Each resulting route transports 1,480 FE from the next stick. The restart retains the two Ender conduits, Octadic Capacitor and 21,920 FE. The saved player has seven basic, 15 Enhanced and 14 Ender conduits.
- The follow-up headless probe passes three assertions and recharges the reopened Octadic smelter to 69,920 FE via its saved Ender conduit route. The packaged client and both packaged upgrade servers use SHA-256 `ae0515998164625b476bd8364a4540faa7028f455dc4a5015dbdbafeed3e949c`.
- The four final server sessions and the guarded client exit normally with no runtime or registry errors. In 2,313 samples the observer records zero focus, activation or visible-window events. All four client captures confirm narrator off and every sound category muted.

The server reports are `build/port-runtime/cycle-155-{alloys-conserved,energy-upgrades}/energy-progression-results.json` and `build/port-runtime/cycle-155-octadic-charge/octadic-charge-results.json`. The client reports and four captures are under `build/port-evidence/cycle-155-energy-upgrades-client`.

## Next verification

Exercise a packaged SAG Mill through stock coal processing, grinding-ball modifiers, bonus outputs, automation and saved work. Verify the material path that supplies powdered coal for capacitor crafting.
