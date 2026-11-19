# Cycle 181 — Carry Forge 6.2 igniter fuel into the port

The 6.2.18-beta Cold Fire Igniter used Forge's item fluid handler. The fixture seeds that released format in a disposable historical server, using `Fluid` with 25 mB of tagged `enderio:vapor_of_levity`, a custom item name and an unrelated marker. A second igniter stores a fluid ID whose mod is absent. The historical server saves both items before its world is copied to the port.

Cold Fire Igniters now use the Staff of Levity's existing item-fluid migration on native stack load. The migration remaps the historic Vapor ID, preserves its exact quantity and tag, and removes the old compound after conversion. Current item fluid data takes precedence over a leftover legacy compound. An unavailable fluid ID leaves the old compound intact with no active contents, so it can be read on a later load with the fluid available.

The packaged survival client uses the converted igniter from the historical barrel. The same tool rejects unsupported and occupied targets, performs one valid ignition before and after restart, and keeps the remainder when it falls below the 10 mB needed for ignition. The run verifies the original name and marker on both sides and observes cold-fire crafting and grain pickup across the two sessions.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_forge="$port_runtime/cycle-047-legacy-discover"
port_legacy="$port_runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar"
port_before="$port_runtime/cycle-180-cold-fire-final/mods/enderio.jar"
port_jar=enderio/build/libs/enderio-8.2.14.180-1.20.1+d5cd5f8f72.jar
port_guarded="$port_runtime/cycle-180-cold-fire-final-client"
port_client="$port_runtime/cycle-181-igniter-final-client"

./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-181-igniter-final-build.log 2>&1
python3 -u scripts/porting/packaged_cold_fire.py --name cycle-181-igniter-before --port 26150 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" --jar "$port_before"
mkdir -p "$port_client/config"
cp -cR "$port_guarded/libraries" "$port_guarded/versions" "$port_client/"
cp "$port_guarded/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name cycle-181-igniter-final-client \
  --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 -u scripts/porting/packaged_cold_fire.py --name cycle-181-igniter-final --port 26153 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" --jar "$port_jar" \
  --launch "$port_client/launch.json"
python3 -u scripts/porting/packaged_levitation_staff.py --name cycle-181-levity-final --port 26154 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" --jar "$port_jar"
```

## Results

- The released 6.2.18-beta jar saved both original item records. The previous port loaded the copied barrel with the historical `Fluid` compounds, but the usable item-fluid field was absent. See `build/port-runtime/cycle-181-igniter-before/smoke-fresh.log`.
- Passing the tagged unavailable fluid through Forge's codec reproduced `Can't modify the empty stack` in the native regression and prevented the copied barrel from loading in the packaged server. The migration now uses Forge's original item-fluid reader after ID remapping. That reader treats the unavailable fluid as empty without modifying the saved record. See `build/port-runtime/cycle-181-igniter-verified/smoke-fresh.log`.
- The final full build passes 79 unit tests and 540 native runs, including all 213 discovered gameplay cases. The new native case verifies exact tagged fuel, item name and marker, modern data precedence and intact storage for an unavailable fluid.
- The Staff of Levity control passes all three headless assertions on the same production jar. Two historical staffs retain their exact tagged 40 mB and 18 mB charges, with their inactive and active states respectively. Both servers exit without registry or runtime errors. See `build/port-runtime/cycle-181-levity-final/levitation-staff-results.json`.
- The packaged survival scenario passes 16 server assertions and 43 client checks. The original named igniter keeps 25 mB after two rejected targets, then 15 mB after a successful ignition and through restart, and 5 mB after a second ignition and the rejected follow-up. Both crafted grains enter the player's inventory. The unavailable fluid keeps its original ID, amount and tag in the historical barrel across both port stages. The three servers and two clients report zero runtime or registry errors.
- Both peers load production SHA-256 `046cf3a4ab29388eac38ed6731992f9a4e43d2d84434fa1e237429d2dcd70ec2`. The two guarded clients record no activation, foreground or visible-window samples across 4,184 observations. Both captures show zero volume in every category and the narrator off. See `build/port-runtime/cycle-181-igniter-final/cold-fire-results.json` and `build/port-evidence/cycle-181-igniter-final-{exercise,verify}-client/summary.json`.

## Next verification

Exercise the stock Redstone Alloy, Pulsating Alloy, Dark Steel, Soularium and End Steel recipes in the packaged Alloy Smelter and use their outputs in survival crafting. The current material rows still need this gameplay evidence.
