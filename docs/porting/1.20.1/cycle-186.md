# Cycle 186 — Bind original slicer parts to captured souls

The fixture starts from a disposable copy of the completed silicon progression world. Its original Z-Logic Controller and Ender Resonator are still in the output hoppers. Each hopper feeds a Soul Binder below it. A stock Soul Vial captures a live zombie or witch through a vanilla dispenser, then a separate hopper inserts that filled vial into the corresponding binder. The binders receive XP Juice through their north faces from ordinary Ender IO tanks. [Cycle 185](cycle-185.md)

Frank ’N’ Zombie requires the original Z-Logic Controller and the zombie. Sentient Ender requires the original Ender Resonator and the witch. Each recipe consumes 51,200 FE and 800 mB of XP Juice, the equivalent of four levels or 40 XP points from level zero. The fixture starts at 799 mB and checks that the binder keeps the ingredients and initial 12,300 FE. Another 38 mB starts the recipe. The dedicated server saves each job with 12,300 FE paid, 837 mB in its tank, both original inputs and both queued outputs.

After restart, the headless run finishes the two recipes and exports the result and empty vial through a hopper. The guarded run opens both restored native menus, confirms the live XP and progress displays, and uses standard shift-click on both outputs. Each job leaves 317 FE and exactly 37 mB. A second restart verifies the resulting storage state and the exact survival inventory or hopper contents.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_forge="$port_runtime/cycle-047-legacy-discover"
port_jar=enderio/build/libs/enderio-8.2.14.185-1.20.1+39bb6d3ea9.jar
port_world="$port_runtime/cycle-185-silicon-final/port-spawners"
port_guarded="$port_runtime/cycle-185-silicon-final-client"
port_client="$port_runtime/cycle-186-soul-components-final-client"

python3 -u scripts/porting/packaged_soul_components.py --name cycle-186-soul-components-headless --port 26169 \
  --java "$port_java" --forge-server "$port_forge" --source-world "$port_world" \
  --jar "$port_runtime/cycle-185-silicon-final/mods/enderio.jar"
./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-186-soul-components-build.log 2>&1
mkdir -p "$port_client/config"
cp -cR "$port_guarded/libraries" "$port_guarded/versions" "$port_client/"
cp "$port_guarded/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name cycle-186-soul-components-final-client \
  --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 -u scripts/porting/packaged_soul_components.py --name cycle-186-soul-components-final --port 26170 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --source-world "$port_world" \
  --launch "$port_client/launch.json"
```

## Results

- The headless profile passes 18 assertions across three dedicated sessions. Both live captures retain the expected type and health until the original vials are consumed. The paid jobs resume after restart and each lower hopper retains one result and one empty vial across the second restart. See `build/port-runtime/cycle-186-soul-components-headless/soul-components-results.json`.
- The full build passes 79 ordinary unit tests and 540 native test runs, including all 213 discovered main gameplay cases. The production jar contains no client QA classes. See `build/cycle-186-soul-components-build.log`.
- The packaged TCP profile passes 18 server assertions and 17 client checks. Both menus show the original ingredient, captured mob, 12,300 FE paid and a four-level requirement after restart. Each completed binder retains 317 FE and 37 mB. Native shift-click takes one of each crafted item and two empty vials. Those exact items remain in the player inventory after the second restart. All server sessions exit with zero runtime or registry errors.
- The multiplayer peers use production SHA-256 `9b6f35f49e86a4233498ec3dc0156696212865e77b41d66ed3d71b95c77b8503`. All four captures show all volume categories at zero and narrator off. The native observer records zero activation, foreground or visible-window events across 2,145 samples. See `build/port-runtime/cycle-186-soul-components-final/soul-components-results.json` and `build/port-evidence/cycle-186-soul-components-final-client/summary.json`.

## Next verification

Use the original Sentient Ender in the stock advanced Wireless Charger antenna recipe. Verify survival placement and the resulting charging range, alongside the stock standard antenna.
