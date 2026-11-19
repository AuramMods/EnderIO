# Cycle 184 — Ensouled Chassis and alloy gear progression

The packaged Forge fixture reuses the Alloy Smelter jobs in [cycle 182](cycle-182.md) to supply the original stock alloy ingredients. A separate SAG Mill accepts six quartz through a hopper, processes the stock quartz recipe for 14,400 FE and exports its powder to another hopper. The fixture moves the entire original powder stack into the workshop chest. One unprocessed quartz, nine iron ingots and two Grains of Infinity supplement the machine output. The earlier survival route verifies the source of the fire drops. [Cycle 154](cycle-154.md)

The TCP survival client collects the output and uses the recipe book with limited crafting enabled. It checks that upgraded gear recipes remain locked before the base gears exist. It crafts and consumes two Iron Gears, turns one into an Energized Bimetal Gear and then into a Vibrant Bimetal Gear, and turns the other into a Dark Bimetal Gear. The client checks every occupied cell in each stock shaped recipe and the exact materials spent when it takes the output.

Three Soul Chain crafts spend six of the original quartz powder and yield six chains. Four chains, four Soularium ingots and one quartz gem become an Ensouled Chassis. The client places the chassis, attaches one of the remaining chains horizontally and places the other vertically. The saved-world check covers the two axis states, chassis, remaining gears and materials, and 137 FE still in the SAG Mill after a clean dedicated-server restart.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_forge="$port_runtime/cycle-047-legacy-discover"
port_jar=enderio/build/libs/enderio-8.2.14.183-1.20.1+c5fe9e62d6.jar
port_guarded="$port_runtime/cycle-183-balls-settled-client"

./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-184-components-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-184-components-qa-rebuild.log 2>&1
for port_name in cycle-184-components-final cycle-184-alloy-control-final; do
  port_profile="$port_name-client"
  port_client="$port_runtime/$port_profile"
  mkdir -p "$port_client/config"
  cp -cR "$port_guarded/libraries" "$port_guarded/versions" "$port_client/"
  cp "$port_guarded/config/fml.toml" "$port_client/config/"
  python3 scripts/porting/client/prepare_packaged.py --name "$port_profile" \
    --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
    --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
done
python3 -u scripts/porting/packaged_advanced_components.py --name cycle-184-components-final --port 26165 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --launch "$port_runtime/cycle-184-components-final-client/launch.json"
python3 -u scripts/porting/packaged_alloy_progression.py --name cycle-184-alloy-control-final --port 26166 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --launch "$port_runtime/cycle-184-alloy-control-final-client/launch.json"
```

## Results

- The full build passes 79 ordinary unit tests and 540 native test runs, including all 213 discovered main gameplay cases. The production jar contains no client QA classes. See `build/cycle-184-components-build.log`.
- The first TCP attempt sees the Energized Gear result before all ingredient slots arrive. The original alloy control hits the same issue at Redstone Alloy nugget crafting. The fixture now waits for the native menu to report the full grid before checking the item positions and counts. See `build/port-evidence/cycle-184-components-client/result.json` and `build/port-evidence/cycle-184-alloy-control-client/result.json`.
- The original alloy progression control passes again with 15 server assertions and 144 client checks. It completes the same 40 crafts over 32 recipe IDs and restores all eight placed alloy blocks after restart. Its guarded client records zero activation, foreground or visible-window events over 1,792 samples. See `build/port-runtime/cycle-184-alloy-control-final/alloy-progression-results.json`.
- The advanced progression passes 18 server assertions and 58 client checks. The SAG Mill uses 14,400 FE and produces seven powder. The player completes 14 crafts across 11 recipe IDs, places the chassis and two chains, and has one powdered quartz and both final upgraded gears in the inventory after restart. All expected ingot and nugget balances match. Both server sessions exit with zero runtime or registry errors.
- The client and server use production SHA-256 `f250b799379f16c4a5ee3adb08cf277eaa450d563fc7c3aa73194fe1c0254888`. The advanced client's four captures show all volume categories at zero and narrator off. The native observer records zero activation, foreground or visible-window events over 1,796 samples. See `build/port-runtime/cycle-184-components-final/advanced-components-results.json` and `build/port-evidence/cycle-184-components-final-client/summary.json`.

## Next verification

Produce silicon with the stock SAG Mill and spend its original output in a Slice’N’Splice recipe. The existing Slice’N’Splice fixture already verifies supplied ingredients, tool removal and saved jobs. [Cycle 159](cycle-159.md)
