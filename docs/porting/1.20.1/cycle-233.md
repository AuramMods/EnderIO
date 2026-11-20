# Cycle 233 — Paid SAG Mill jobs after input swaps

Two stock SAG Mills each grind one coal on the base 2,400 FE budget and activate one Redstone Alloy Grinding Ball. Each retains one spare. Both mills then pay 840 FE for another coal job while all four output slots are full. The fixture records the queued powder rolls and restarts the dedicated server.

The survival client replaces one coal with tagged stone and the other with tagged charcoal through native hotbar swaps. The player takes the original output stack from each machine to make room. Stone belongs to a different recipe, so the paid coal job cancels and leaves the stone untouched. Charcoal matches the same coal tag, so the queued job finishes with that charcoal and delivers the saved roll. Both mills retain the original 840 points of grinding-ball wear and their spare balls. The stone job then receives a fresh 840 FE and produces one cobblestone. The active ball applies its energy discount to the stone recipe without incurring wear or adding bonus output.

## Commands

```sh
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew :enderio:reobfClientSmokeJar --max-workers=4 --console=plain
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew :enderio:assemble :enderio:reobfClientSmokeJar --max-workers=4 --console=plain
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew build :enderio:reobfClientSmokeJar --continue --max-workers=2 --console=plain

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_seed="$port_runtime/cycle-232-alloy-fixed-retry-client"
export JAVA_TOOL_OPTIONS='-Djava.net.preferIPv4Stack=true -XX:ActiveProcessorCount=4'

run_sag_profile() {
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
  python3 -Bu scripts/porting/packaged_sag_backpressure.py --name "$port_name" --port "$port_number" \
    --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar" \
    --launch "$port_client/launch.json"
}

run_sag_profile cycle-233-sag-before 26321 build/cycle-233-artifacts/before.jar build/cycle-233-artifacts/qa.jar
run_sag_profile cycle-233-sag-fixed 26322 build/cycle-233-artifacts/fixed.jar build/cycle-233-artifacts/fixed-qa.jar
python3 -Bu scripts/porting/packaged_grinding_balls.py --name cycle-233-sag-grinding-regression --port 26323 \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar build/cycle-233-artifacts/fixed.jar
run_sag_profile cycle-233-sag-before-retry 26324 build/cycle-233-artifacts/before.jar build/cycle-233-artifacts/qa.jar
run_sag_profile cycle-233-sag-fixed-retry 26325 build/cycle-233-artifacts/fixed.jar build/cycle-233-artifacts/fixed-qa.jar
```

## Results

- The first baseline run restores both paid jobs. Its guarded client disconnects during the Forge handshake, before interacting with a mill. The retry uses the same baseline and QA jars in a fresh profile. See `build/port-runtime/cycle-233-sag-before/logs/debug.log` and `build/port-evidence/cycle-233-sag-before-client/summary.json`.
- The first fixed run passes both live swaps. Its recovery assertion supplied the base 2,400 FE. The saved region shows one cobblestone, 1,560 FE remaining and wear still at 840. The active ball's discount applies to this recipe, as it does in the source baseline. The corrected fixture supplies 840 FE for the recovery. See `build/port-runtime/cycle-233-sag-fixed/port-spawners/region/r.9.0.mca` and `build/port-runtime/cycle-233-sag-fixed/sag-backpressure-results.json`.
- The baseline retry retains both saved rolls after restart. The player swaps in stone and receives the tagged coal. Opening the first output slot consumes the stone and releases one powdered coal. Both the server and client reject that result. The charcoal control preserves its saved roll and exact wear. See `build/port-runtime/cycle-233-sag-before-retry/smoke-reopened.log`, `build/port-runtime/cycle-233-sag-before-retry/sag-backpressure-results.json` and `build/port-evidence/cycle-233-sag-before-retry-client/summary.json`.
- The corrected fixed run passes 15 server assertions and 13 client checks across three clean server launches. The swapped stone remains in the slot until a new 840 FE payment produces one cobblestone. The compatible charcoal uses the existing payment and delivers the original one-powder roll. Both mills retain exactly 840 wear and one spare ball across the final restart. The survival player keeps both tagged coal inputs and the two full stacks removed from the outputs. See `build/port-runtime/cycle-233-sag-fixed-retry/sag-backpressure-results.json`.
- The guarded client records zero activations, foreground samples and onscreen samples across 1,955 observations. All four captures confirm narrator off and all ten volume categories at zero. Both peers load the same production jar. See `build/port-evidence/cycle-233-sag-fixed-retry-client/summary.json`.
- The full Gradle build passes 79 unit cases and 543 required Forge test runs. The headless stock-ball regression passes 48 assertions across two clean server launches. It covers all nine stock ball modifiers, saved rolls, completing on the exact remaining FE, exhaustion and claiming one spare after delivery. See `build/cycle-233-sag-full-build.log` and `build/port-runtime/cycle-233-sag-grinding-regression/grinding-balls-results.json`.
- The fixed artifact matches the final build jar byte-for-byte with SHA-256 `3dcf908153e1f92626aa2f8a71a0e155cf076212f2f7b5045385d5c0810dc86c`. The packaged jar and its nested jars contain no client smoke classes.

## Next verification

Check a completed VAT fermentation waiting behind a full output tank. Replace a valid reagent with one that matches the same recipe and has a different yield multiplier. Verify that the delivered fluid amount follows the ingredients the machine consumes.
