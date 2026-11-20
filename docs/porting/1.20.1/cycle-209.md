# Cycle 209 — Powered Spawner recovery and the Mind Killer

The packaged survival player unlocks the stock Mind Killer recipe with a Zombie Electrode. The native recipe book fills the crafting table with one electrode, one Energetic Alloy ingot and two Soularium ingots. The player collects the result and mounts it above a cow-bound Powered Spawner. The client checks the inventory model, the placed texture and shape, and the native spawner menu.

The server starts with `maxSpawners = 0`. The charged cow machine reaches 10,000 FE paid and reports the nearby-spawner warning. Editing the running server's common config raises the limit to three. A newly charged control spawner outside the cow's range verifies that Forge applied the change. The original paid job must finish with its remaining energy still at zero and clear its warning in the open menu. The spawned cow carries the Mind Killer's no-AI flag and retains the ability to move. The mounted device and completed job are checked after restart.

The task now computes efficiency from the current nearby block count and current limit on every spawn attempt. Its probability comparison treats zero and full efficiency as exact endpoints. When a formerly blocked spawn succeeds, the machine clears the old spawn warning even if the completed task has already been retired.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_jar=enderio/build/libs/enderio-8.2.14.208-1.20.1+1299c55906.jar
port_baseline=build/cycle-209-before-efficiency.jar
port_source="$port_runtime/cycle-208-inventory-confirmed-client"

./gradlew :enderio:assemble --console=plain > build/cycle-209-spawner-baseline-build.log 2>&1
cp "$port_jar" "$port_baseline"
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-209-mind-killer-qa.log 2>&1

run_mind_killer() {
  local port_run="$1" port_number="$2" port_artifact="$3"
  local port_client="$port_runtime/$port_run-client"
  mkdir -p "$port_client/config"
  cp -cR "$port_source/libraries" "$port_source/versions" "$port_client/"
  cp "$port_source/config/fml.toml" "$port_client/config/"
  python3 scripts/porting/client/prepare_packaged.py --name "$port_run-client" \
    --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_artifact" \
    --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
  python3 -u scripts/porting/packaged_mind_killer.py --name "$port_run" --port "$port_number" \
    --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_artifact" --launch "$port_client/launch.json"
}

run_mind_killer cycle-209-mind-killer-before 26218 "$port_baseline"
./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-209-spawner-build.log 2>&1
run_mind_killer cycle-209-mind-killer-fixed 26219 "$port_jar"
python3 -u scripts/porting/packaged_spawner_rules.py --name cycle-209-spawner-rule-regression --port 26220 \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar"
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-209-mind-killer-qa-camera.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-209-mind-killer-qa-final.log 2>&1
run_mind_killer cycle-209-mind-killer-persistence 26224 "$port_jar"
```

## Results

- The original jar completes the stock craft and mounts the Mind Killer. The control sheep spawns after the live config change. The original cow job stays blocked through the ten-second assertion window. The closed world retains the cow's task at 10,000 of 10,000 FE paid and zero stored FE. See `build/port-runtime/cycle-209-mind-killer-before/mind-killer-results.json`.
- The first fixed client on port 26219 receives the crafting output before the final grid update. The fixture now waits for all four occupied slots before checking the recipe. The camera probes on ports 26221 and 26222 used a line of sight just above the six-pixel model. The final fixture lowers the camera angle and requires a hit on the mounted block before capture.
- The run on port 26223 passes the live craft, render and spawn checks. Its first persistence assertion expects an empty task host. The closed world instead contains the existing cow and a fresh task with zero FE paid, which the machine normally prepares on load. The restart assertion now checks for exactly one cow and zero progress on the new task.
- The full build passes 79 unit tests and 540 native Forge runs, including all 213 discovered main gameplay tests. The packaged spawner-rule regression passes seven assertions, including the copied cow's persisted state and movement from applied velocity. See `build/cycle-209-spawner-build.log` and `build/port-runtime/cycle-209-spawner-rule-regression/spawner-rules-results.json`.
- The final run passes eight runner assertions and 14 guarded client checks. The original cow job releases one cow at zero stored FE as soon as the live limit permits the attempt. The open native menu clears the overcrowding warning. The cow and Mind Killer survive restart. The next task on the reopened machine has zero FE paid. Both server sessions exit zero without runtime or registry errors. See `build/port-runtime/cycle-209-mind-killer-persistence/mind-killer-results.json`.
- The client captures the crafting output, the crosshair aimed at the six-pixel-high mounted model, the blocked menu and the same menu after recovery. Across 1,887 native observations the client records zero activation, foreground or visible-window events. All four captures have the narrator off and all ten sound categories at zero. See `build/port-evidence/cycle-209-mind-killer-persistence-client/`.
- The original production jar SHA-256 is `0a6ce9b945f23351dcb42f6996ee3793535e12ca533cf2cf427d3101040d4e2f`. The fixed production jar SHA-256 is `12a8f3b68e00527c921b8502a873906fcacdaebaadc54b96b2e44ce0517de472`. The fixed production jar contains no client probe classes.

## Next verification

Use the stock Electromagnet recipe in survival and isolate experience-orb attraction from the vanilla attraction path. The item pickup and active FE checks already pass in the hotbar and the shipped Curios charm slot.
