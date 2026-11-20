# Cycle 230 — Paid Soul Binder jobs with a full output

Two stock Energetic Photovoltaic Module jobs receive a named Phantom and the original material from vanilla hoppers. Each machine spends exactly 12,800 FE while its second output holds 64 empty Soul Vials. Neither output is delivered until both fit. Both jobs retain the inputs and all 2,277 mB of XP fluid through a dedicated-server restart.

The first job releases when a hopper can take a single empty vial. The existing stack returns to 64, the hopper holds its one extra vial, and the product slot receives one panel.

For the second job, a survival hotbar swap replaces the Phantom named Flint with one named Nova while the output is blocked. The player retains Flint and shift-clicks the full output stack. The Soul Binder checks the live recipe inputs and refreshes the result when either input stack changes. An eligible replacement keeps the original paid progress. The resulting panel must contain Nova's captured data, and the machine must retain exactly 37 mB of XP fluid and return one empty vial.

## Commands

```sh
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew :enderio:reobfClientSmokeJar --max-workers=4 --console=plain
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew :enderio:assemble :enderio:reobfClientSmokeJar --max-workers=4 --console=plain
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew build :enderio:reobfClientSmokeJar --continue --max-workers=2 --console=plain

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_seed="$port_runtime/cycle-229-rebinding-fixed-retry-client"
export JAVA_TOOL_OPTIONS='-Djava.net.preferIPv4Stack=true -XX:ActiveProcessorCount=4'

run_binder_profile() {
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
  python3 -Bu scripts/porting/packaged_soul_binder_backpressure.py --name "$port_name" --port "$port_number" \
    --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar" \
    --launch "$port_client/launch.json"
}

run_binder_profile cycle-230-binder-before 26310 build/cycle-230-artifacts/before.jar build/cycle-230-artifacts/qa.jar
run_binder_profile cycle-230-binder-fixed 26311 build/cycle-230-artifacts/fixed.jar build/cycle-230-artifacts/fixed-qa.jar
python3 -Bu scripts/porting/packaged_spawner_rebinding.py --name cycle-230-rebinding-regression --port 26312 \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar build/cycle-230-artifacts/fixed.jar
```

## Results

- The baseline passes the two paid-job and restart checks. The unchanged job delivers exactly once after hopper extraction. The survival swap leaves Nova in the machine and Flint with the player, but the cached output still contains Flint. Clearing the output consumes Nova and yields a panel with Flint's name, health and `NoAI` tag. See `build/port-evidence/cycle-230-binder-before-client/result.json` and `build/port-runtime/cycle-230-binder-before/smoke-reopened.log`.
- The fixed client passes all nine checks. The server sees Nova's name and health in the queued output while the slot is still full. The user's shift-click releases one panel bound to Nova and one empty vial. The player retains Flint's vial and the original stack of 64 empties. See `build/port-evidence/cycle-230-binder-fixed-client/summary.json` and `build/port-runtime/cycle-230-binder-fixed/smoke-reopened.log`.
- The fixed client records zero activations, foreground samples or onscreen windows across 2,356 observations. Each of the three captures records ten muted sound settings and narrator `OFF`.
- The fixed dedicated server passes nine assertions across three launches. Both saved panels and both exact post-craft balances survive the final restart. The client and the server run the same packaged Ender IO jar with the core Forge profile. See `build/port-runtime/cycle-230-binder-fixed/binder-backpressure-results.json`.
- The full Java 17 build passes 79 unit tests and 543 native Forge runs, including 215 gameplay cases. The tested production jar matches the rebuilt artifact. Inspection of that jar and its nested jars finds no client QA classes. See `build/cycle-230-binder-full-build.log`.
- The Forge-only Powered Spawner rebinding regression passes nine assertions across three launches on the same jar. The paid job restores after restart, copies the original machine configuration, replaces the Cow with the Pig, and retains both outputs after a second restart. See `build/port-runtime/cycle-230-rebinding-regression/spawner-rebinding-results.json`.

| Jar | SHA-256 |
| --- | --- |
| Baseline Ender IO | `b992372b2a01986add789e66ac50a7052b0aca2f998578c40d1bab894681db2c` |
| Fixed Ender IO | `0e905b9e28cf4be230411067529cb2b878f40ff1f0d95873a8ca446424995ba9` |
| Client QA | `c3fea90461cd6b9e4a5183e2908023a4d520817c6e16aec563b04b158d5b8aae` |

## Next verification

Remove a required ingredient through the Slice’N’Splice survival menu while a fully paid job waits behind a full output. Verify the output and tool wear after clearing the blockage, and verify recovery when valid inputs return.
