# Cycle 229 — Rebind a configured Powered Spawner

When the player looks up the stock rebinding recipe from a configured Powered Spawner, JEI displays that spawner in the material slot. Its output preview uses the same item-data copy as the Soul Binder itself. The selected captured soul replaces the old soul in the result.

The fixture uses the stock loot function to pack a Cow spawner with 877 FE, a north-side I/O setting, signal-only redstone control and an Octadic Capacitor. The real JEI transfer button supplies that drop and a named Pig vial. The binder spends 288,000 FE and 2,240 mB of XP fluid, returns an empty vial and preserves the spawner configuration. Survival placement restores the configured spawner. Applying a redstone signal starts the Pig's stock 12,000 FE job, using the carried 877 FE first.

The Forge-only control uses vanilla hoppers. It saves the binding after 400 FE of progress, resumes it after restart and checks the outputs after a second restart.

## Commands

```sh
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-229-rebinding-qa-build.log 2>&1
./gradlew :enderio:assemble :enderio:reobfClientSmokeJar --console=plain > build/cycle-229-rebinding-fixed-build.log 2>&1
./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-229-rebinding-full-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_seed="$port_runtime/cycle-228-binder-rules-client"
port_jei="$port_runtime/cycle-158-jei-final-client/mods/jei-9jqubC9n.jar"
port_qa=build/cycle-229-artifacts/qa.jar
export JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true

run_rebinding_client() {
  port_name="$1"
  port_number="$2"
  port_jar="$3"
  port_client="$port_runtime/$port_name-client"
  mkdir -p "$port_client/config"
  cp -cR "$port_seed/libraries" "$port_seed/versions" "$port_client/"
  cp "$port_seed/config/fml.toml" "$port_client/config/"
  python3 scripts/porting/client/prepare_packaged.py --name "$port_name-client" \
    --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" \
    --jar "$port_jar" --qa-jar "$port_qa" --assets "$HOME/.gradle/caches/neoformruntime/assets" --mod "$port_jei"
  python3 -u scripts/porting/packaged_spawner_rebinding.py --name "$port_name" --port "$port_number" \
    --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar" \
    --jei "$port_jei" --launch "$port_client/launch.json"
}

run_rebinding_client cycle-229-rebinding-before 26305 build/cycle-229-artifacts/before.jar
python3 -u scripts/porting/packaged_spawner_rebinding.py --name cycle-229-rebinding-core-before --port 26306 \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar build/cycle-229-artifacts/before.jar
port_qa=build/cycle-229-artifacts/fixed-qa.jar
run_rebinding_client cycle-229-rebinding-fixed 26307 build/cycle-229-artifacts/fixed.jar
python3 -u scripts/porting/packaged_spawner_rebinding.py --name cycle-229-rebinding-core --port 26308 \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar build/cycle-229-artifacts/fixed.jar
export JAVA_TOOL_OPTIONS='-Djava.net.preferIPv4Stack=true -XX:ActiveProcessorCount=4'
run_rebinding_client cycle-229-rebinding-fixed-retry 26309 build/cycle-229-artifacts/fixed.jar
python3 -Bu build/cycle-229-core-recovery.py "$port_java"
```

## Results

- The baseline client finds the stock recipe from the configured Cow spawner and checks its original cost and data-copy flag. Its focused preview fails the exact machine-data check. See `build/port-evidence/cycle-229-rebinding-before-client/result.json`.
- The baseline Forge-only control passes nine assertions across three launches. It harvests the original item, stops below the XP threshold, saves both inputs and the paid progress, and restores the exact rebound item and empty vial. See `build/port-runtime/cycle-229-rebinding-core-before/spawner-rebinding-results.json`.
- The first fixed profiles hit launch deadlines during a period of high host load. The client completed the Forge handshake before its window observer reached the deadline. The core server passed the initial recipe checks and reached world loading on restart. Subsequent runs use longer launch deadlines and smaller JVM worker pools. See `build/port-evidence/cycle-229-rebinding-fixed-client/summary.json` and `build/port-runtime/cycle-229-rebinding-core/smoke-reopened.log`.
- The full Java 17 build passes 79 unit tests and 543 Forge GameTest runs, including all 215 main gameplay cases. The production jar matches the artifact in the fixed profiles. Neither it nor its nested jars contain the QA mod. See `build/cycle-229-rebinding-full-build.log`.
- The fixed client passes 17 checks, including exact material and output previews, actual JEI transfer, survival output collection, and placement. The observer records zero activation, foreground or onscreen samples across 4,645 observations. All four captures record ten muted sound settings and narrator `OFF`. See `build/port-evidence/cycle-229-rebinding-fixed-retry-client/summary.json`.
- The fixed dedicated server with JEI passes 14 assertions. After placement, the signal applies the carried 877 FE to the Pig job. The remaining 11,123 FE produces only pigs. The placed spawner and its spawned pigs survive the dedicated restart. See `build/port-runtime/cycle-229-rebinding-fixed-retry/spawner-rebinding-results.json`.
- Reopening the saved Forge-only fixture with the fixed jar passes all nine assertions across three successful launches. It resumes the existing 400 FE job and persists both outputs with 317 FE and 37 mB left. See `build/port-runtime/cycle-229-rebinding-core/spawner-rebinding-recovery-results.json`.

| Jar | SHA-256 |
| --- | --- |
| Baseline Ender IO | `501f62f238ef9d6005033f7ead042088622e28ca0c8bb8389308f95543014f2e` |
| Fixed Ender IO | `b992372b2a01986add789e66ac50a7052b0aca2f998578c40d1bab894681db2c` |
| Client QA | `f032ee5a299ce34480659071d89457643257e297073959bd3d535558b9599e01` |
| JEI `15.56.0.205` | `6b251e60a4719c874da89d750af97aa6498f6f4475e3ae79cb1288c7bf0cb65c` |

## Next verification

Fill the Soul Binder's empty-vial output while a stock job runs. Verify that the paid binding waits without consuming the original soul or XP, then completes exactly once after the blockage clears, including across a dedicated restart.
