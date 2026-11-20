# Cycle 240 — Industrial Insulation crafting and reuse

The native powered Alloy Smelter test supplies powdered iron, orange wool and powdered lapis. It verifies one block and the exact recipe cost of 3,200 FE. The packaged integrated client opens the fuel-fired Primitive Alloy Smelter menu and quick-moves coal, white wool and both stock powders. It uses the single output to clear two enclosed basins in succession.

The water basin contains a source, flowing water and a waterlogged stone slab. The second basin contains a source and flowing lava. Both basins also contain an isolated source behind stone. Survival placement clears the connected fluid and preserves the distant source. In between, normal mining and player pickup recover the output from the first basin. Reopening the world preserves both drained basins and the reused block. A new lava source next to the saved block checks its neighbor-update behavior.

## Commands

```sh
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew :enderio:reobfClientSmokeJar --max-workers=4 --console=plain
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew build :enderio:reobfClientSmokeJar --continue --max-workers=2 --console=plain
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew :enderio:reobfClientSmokeJar --max-workers=2 --console=plain

port_runtime=build/port-runtime
port_seed="$port_runtime/cycle-239-glider-boat-client"
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.239-1.20.1+8ad22b6898.jar
port_qa=enderio/build/test-libraries/enderio-client-qa.jar
export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4'

run_port_client() {
  local port_name="$1" port_scenario="$2"
  local port_client="$port_runtime/$port_name-client"
  mkdir -p "$port_client/config"
  cp -cR "$port_seed/libraries" "$port_seed/versions" "$port_client/"
  cp "$port_seed/config/fml.toml" "$port_client/config/"
  python3 scripts/porting/client/prepare_packaged.py --name "$port_name-client" --java "$port_java" \
    --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" --qa-jar "$port_qa" \
    --assets "$HOME/.gradle/caches/neoformruntime/assets"
  python3 scripts/porting/client/run_smoke.py --launch "$port_client/launch.json" --scenario "$port_scenario" \
    --output "build/port-evidence/$port_name" --timeout 245
}

run_port_client cycle-240-insulation-final industrial-insulation
run_port_client cycle-240-primitives-control primitive-smelter
```

## Results

- The first QA compile exposed Forge's declared `Block` type for vanilla water and lava. The fixture uses that type and still sets the native liquid level. The first packaged run passed the block assertions but captured old section meshes and reentered its shutdown callback. The fixture now waits for the updated section render before capture and leaves the active test stage before disconnecting. See `build/cycle-240-insulation-qa.log` and `build/port-evidence/cycle-240-insulation/focus.json.client.log`.
- The full build passes all 79 unit cases and 544 required native runs. The 216 main gameplay cases include the new Industrial Insulation recipe. It consumes all three ingredients once and leaves 3,300 of the supplied 6,500 FE. See `build/cycle-240-insulation-full.log` and `enderio/build/test-results/gameTestServer/results.xml`.
- The packaged Industrial Insulation scenario passes all 32 checks. The waterlogged slab stays in place and dries out, the isolated sources remain, and mining returns exactly one block. The reused block and drained basins persist through reopening. The subsequent lava refill drains through the saved block's native neighbor update. The unchanged Conductive Alloy control passes all 16 checks. Both launches complete once and exit cleanly. See `build/port-evidence/cycle-240-insulation-final/summary.json` and `build/port-evidence/cycle-240-primitives-control/summary.json`.
- Both packaged clients load the full build's production SHA-256 `b9aa8cce0274d3a8f7ca7db3aea55c0c03004d65c5bef9171730f8b4e9a23d80`. Its archive and nested jars contain no client smoke code. The observer records zero activations, foreground samples or onscreen samples over 2,983 observations. All nine captures have the narrator off and all ten volume categories at zero. The drained basin frames show air where the connected liquids were and show the isolated sources still present.

## Next verification

Place Painted Fences in source water beside compatible and incompatible neighbors. Verify that the paint and connection state follow neighbor changes and survive a save and reopen.
