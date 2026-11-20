# Cycle 232 — Paid Alloy Smelter jobs after input swaps

Two stock Alloy Smelters pay for a batch with their output slots full. The first uses the red-dye alloy recipe for 1,600 FE. The second uses inherited vanilla raw iron smelting for 1,500 FE and queues three ingots. The dedicated server saves both jobs and restarts. A survival player opens each native menu, swaps its input and shift-clicks the original 64-item output stack into their inventory.

Removing the six eggs cancels the alloy job and leaves the beetroot and three clay balls in place. Swapping three raw iron for one tagged raw iron leaves a valid furnace batch. The furnace keeps its paid progress and reduces the pending output to one ingot, consuming exactly the one current input. The saved batch size limits how much a waiting job can produce after additional inputs arrive.

## Commands

```sh
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew :enderio:reobfClientSmokeJar --max-workers=4 --console=plain
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew :enderio:assemble :enderio:reobfClientSmokeJar --max-workers=4 --console=plain
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew build :enderio:reobfClientSmokeJar --continue --max-workers=2 --console=plain

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_seed="$port_runtime/cycle-231-slicer-fixed-client"
export JAVA_TOOL_OPTIONS='-Djava.net.preferIPv4Stack=true -XX:ActiveProcessorCount=4'

run_alloy_profile() {
  port_name="$1"
  port_number="$2"
  port_jar="$3"
  port_qa="$4"
  port_client="$port_runtime/$port_name-client"
  mkdir -p "$port_client/config"
  cp -cR "$port_seed/libraries" "$port_seed/versions" "$port_client/"
  cp "$port_seed/config/fml.toml" "$port_client/config/"
  python3 scripts/porting/client/prepare_packaged.py --name "$port_name-client" --java "$port_java" \
    --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" --qa-jar "$port_qa" \
    --assets "$HOME/.gradle/caches/neoformruntime/assets"
  python3 -Bu scripts/porting/packaged_alloy_backpressure.py --name "$port_name" --port "$port_number" \
    --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar" \
    --launch "$port_client/launch.json"
}

run_alloy_profile cycle-232-alloy-before 26317 build/cycle-232-artifacts/before.jar build/cycle-232-artifacts/qa.jar
run_alloy_profile cycle-232-alloy-fixed 26318 build/cycle-232-artifacts/fixed.jar build/cycle-232-artifacts/fixed-qa.jar
python3 -Bu scripts/porting/packaged_alloy_jei.py --name cycle-232-alloy-reload-regression --port 26319 \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar build/cycle-232-artifacts/fixed.jar
run_alloy_profile cycle-232-alloy-fixed-retry 26320 build/cycle-232-artifacts/fixed.jar build/cycle-232-artifacts/fixed-qa.jar
```

## Results

- The cycle 231 jar reproduces both failures. With the eggs in the player's hotbar, the machine consumes the remaining tagged ingredients and delivers 12 red dye. With the original three tagged raw iron in the player's hotbar, the furnace consumes the replacement raw iron and delivers three ingots. Both server assertions and both matching client checks fail. See `build/port-runtime/cycle-232-alloy-before/smoke-reopened.log`, `build/port-runtime/cycle-232-alloy-before/alloy-backpressure-results.json` and `build/port-evidence/cycle-232-alloy-before-client/summary.json`.
- The first fixed run saves and reloads both paid jobs. The guarded TCP client disconnects during the Forge handshake, before either menu opens. The retry uses the same two jars in a fresh profile. See `build/port-runtime/cycle-232-alloy-fixed/logs/debug.log` and `build/port-evidence/cycle-232-alloy-fixed-client/summary.json`.
- The retry passes 11 server assertions and 13 client checks across three clean server launches. The first smelter keeps the tagged beetroot and three clay balls and produces no extra dye. The second consumes the one replacement raw iron and produces one ingot with its existing 1,500 FE payment. The player retains both original 64-item output stacks and the tagged raw iron stack. The last restart preserves both machines’ exact balances. See `build/port-runtime/cycle-232-alloy-fixed-retry/alloy-backpressure-results.json`.
- The guarded client records zero activations, foreground samples or onscreen samples across 1,851 observations. All four captures record narrator off and every volume at zero. Both peers load the same fixed jar. See `build/port-evidence/cycle-232-alloy-fixed-retry-client/summary.json`.
- The full Gradle build passes 79 unit cases and 543 required Forge test runs. The three native powered smelter tests and both primitive smelter tests pass. The packaged Forge-only hopper regression passes seven assertions across two clean server launches, including the stock red dye and tagged quartz from a reloaded recipe. See `build/cycle-232-alloy-full-build.log` and `build/port-runtime/cycle-232-alloy-reload-regression/alloy-jei-results.json`.
- The fixed artifact matches the final build jar byte-for-byte with SHA-256 `d146efe33b0ed522467cc54ac099bed1db17e884521ab41bce7479e5458e9390`. Its outer and nested jars contain no client smoke classes.

## Next verification

Swap a SAG Mill input while a paid job waits for space. Verify that an incompatible input cannot produce the old result and that a compatible input keeps the original roll and grinding-ball accounting.
