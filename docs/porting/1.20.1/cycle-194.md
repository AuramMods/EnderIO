# Cycle 194 — Infinity Rods and Staff of Levity progression

The packaged test produces Grains of Infinity through ordinary bedrock fire crafting and collects the actual drops in survival. An Alloy Smelter uses the stock iron, powdered coal and obsidian recipe to supply two Dark Steel ingots. A dispenser captures a live blue Shulker in a Soul Vial, and native hoppers feed the captured soul and Vibrant Crystal to the Soul Binder. A sided tank supplies the XP Juice. The binder exports a Prescient Crystal and returns an empty vial.

The survival player takes the original ingots and Prescient Crystal from the workshop chest. With limited crafting enabled, the player turns one ingot into nine nuggets, then uses eight nuggets, four original grains and two sticks to craft two Infinity Rods. Those rods and the original Shulker-bound Prescient Crystal make one Staff of Levity. The player uses native charger and tank slots to put 10,000 FE and a tagged 60 mB batch of Vapor of Levity into that Staff. Crouch-use starts flight, and each active tick uses one FE and one mB. The staff stops at fuel exhaustion with 9,940 FE, and the player lands. The final restart checks the native machine balances and the closed player save checks the original Staff and crafting leftovers.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_forge="$port_runtime/cycle-047-legacy-discover"
port_jar=enderio/build/libs/enderio-8.2.14.193-1.20.1+a162d0085a.jar
port_guarded="$port_runtime/cycle-193-attractor-stock-client"
port_client="$port_runtime/cycle-194-infinity-rod-confirmed-client"

./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-194-infinity-rod-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-194-infinity-rod-qa-rebuild.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-194-infinity-rod-click-rebuild.log 2>&1
mkdir -p "$port_client/config"
cp -cR "$port_guarded/libraries" "$port_guarded/versions" "$port_client/"
cp "$port_guarded/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name cycle-194-infinity-rod-confirmed-client \
  --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 -u scripts/porting/packaged_infinity_rod_progression.py --name cycle-194-infinity-rod-confirmed --port 26187 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --launch "$port_client/launch.json"
```

## Results

- The full build passes 79 unit tests with none skipped and 540 native runs, including all 213 discovered main gameplay cases. The production jar contains no client test classes. See `build/cycle-194-infinity-rod-build.log`.
- The original run uses `cycle-194-infinity-rod-stock` on port 26184. The player collects 14 live fire drops and one Suspicious Seed and crafts the Staff. The guarded client reaches its time limit waiting for the charger. The saved player still has the Staff and the charger retains the full 10,000 FE with an empty input slot. A separate packaged server on port 26185 uses a vanilla hopper to charge a Staff to 10,000 FE. The client rerun selects an empty hand when opening machines and checks the native input slot before applying power. See `build/cycle-194-wired-probe.log` and `build/port-runtime/cycle-194-infinity-rod-stock/infinity-rod-progression-results.json`.
- The run on port 26186 shows the second recipe in the real crafting grid and stops when the inventory check immediately after the output shift-click fails. The saved inventory confirms the consumed materials and one stored rod. The client driver uses ordinary pickup and placement clicks and waits for the slot updates before advancing. See `build/port-evidence/cycle-194-infinity-rod-verified-client/result.json`.
- The final run passes 14 server assertions and 28 client checks. The survival player collects nine grains and three Suspicious Seeds and uses four grains, eight nuggets and two sticks for the two rods. One Shulker-bound crystal and both rods go into the Staff. The charger transfers all 10,000 FE and the tank transfers all 60 mB of tagged Vapor. Real flight uses both at one unit per tick. After restart the Staff is inactive with 9,940 FE and an empty tank, and the player still has five grains, three seeds, one ingot and one nugget. Both server sessions exit cleanly with zero runtime or registry errors.
- Both peers load production SHA-256 `89c7967a7c1b4137bc492367b0c7234b9d751cabbf724236ec82dbe8717a9667`. All four captures show every sound category at zero and the narrator off. The native observer records zero activation, foreground or visible-window events across 2,986 samples. See `build/port-runtime/cycle-194-infinity-rod-confirmed/infinity-rod-progression-results.json` and `build/port-evidence/cycle-194-infinity-rod-confirmed-client/summary.json`.

## Next verification

Use stock SAG Mill outputs to craft Photovoltaic Composite in survival. Smelt the original composites into Photovoltaic Plates and use those plates in an Energetic Photovoltaic Module.
