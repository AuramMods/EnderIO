# Cycle 231 — Paid Slice’N’Splice jobs with a missing ingredient

The packaged Forge fixture fills two Slice’N’Splice outputs with 64 Z-Logic Controllers. Each machine receives the six stock ingredients through a hopper, pays the 20,000 FE recipe cost, and retains the ingredients and both unworn tools until output space opens. Both jobs save in that state and reopen on the dedicated server.

A hopper frees one place in the control machine. Its paid job delivers one controller and uses one point of durability on each tool. In the other machine, a survival player takes the tagged zombie head using the native hotbar swap, then shift-clicks the full output.

Slice’N’Splice checks the current six input slots before progressing or delivering. Removing a required ingredient cancels the job without consuming the remaining ingredients or using either tool. Returning the exact head lets a new job start when the machine receives power. The fixture supplies a fresh 20,000 FE and verifies one result, one use on each tool, and exact inventory balances after restart.

## Commands

```sh
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew :enderio:reobfClientSmokeJar --max-workers=4 --console=plain
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew :enderio:assemble :enderio:reobfClientSmokeJar --max-workers=4 --console=plain
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew build :enderio:reobfClientSmokeJar --continue --max-workers=2 --console=plain

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_seed="$port_runtime/cycle-230-binder-fixed-client"
export JAVA_TOOL_OPTIONS='-Djava.net.preferIPv4Stack=true -XX:ActiveProcessorCount=4'

run_slicer_profile() {
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
  python3 -Bu scripts/porting/packaged_slicer_backpressure.py --name "$port_name" --port "$port_number" \
    --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar" \
    --launch "$port_client/launch.json"
}

run_slicer_profile cycle-231-slicer-before 26313 build/cycle-231-artifacts/before.jar build/cycle-231-artifacts/qa.jar
run_slicer_profile cycle-231-slicer-fixed 26314 build/cycle-231-artifacts/fixed.jar build/cycle-231-artifacts/fixed-qa.jar
python3 -Bu scripts/porting/packaged_slicer.py --name cycle-231-slicer-tool-regression --port 26315 \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" \
  --jar build/cycle-231-artifacts/fixed.jar --probe-missing-tool
```

## Results

- The baseline restarts both paid jobs and completes the unchanged control. The native swap places the tagged zombie head in the player's hotbar. After the original output stack is cleared, the test machine still delivers one controller. The server records the other five ingredients gone and the axe and shears at damage 8 and 12. See `build/port-runtime/cycle-231-slicer-before/smoke-reopened.log` and `build/port-runtime/cycle-231-slicer-before/slicer-backpressure-results.json`.

- The fixed packaged run passes 10 server assertions and 10 hidden client checks across three clean dedicated-server launches. With the head absent, the remaining five ingredients and both tools stay in place and the original 64 controllers stay in the player's inventory. Returning the head at zero FE leaves the machine idle. With a fresh 20,000 FE the machine produces one controller and the axe and shears go from damage 7 and 11 to 8 and 12. Both machines retain the expected output and tool damage after the next restart. See `build/port-runtime/cycle-231-slicer-fixed/slicer-backpressure-results.json` and `build/port-evidence/cycle-231-slicer-fixed-client/summary.json`.
- The hidden client records zero activations, foreground samples and onscreen samples over 4,343 observations. The three captures `blocked.png`, `without-head.png` and `recovered.png` record narrator off and all ten volumes at zero.
- The existing missing-shears scenario passes nine server assertions across three clean launches. Removing the shears pauses the job with 3,600 FE already paid. A hopper reinstalls them and the original job resumes on its exact remaining budget. See `build/port-runtime/cycle-231-slicer-tool-regression/slicer-results.json`.
- The full Gradle build and reobfuscation exit successfully. All 79 unit cases and 543 required native test runs pass. See `build/cycle-231-slicer-full-build.log`. The packaged product loads on both peers with SHA-256 `dec92146e9771af73461b7f933fc125ab3bd61b578ce2769d4558a58482f1600`, which matches the final build jar byte-for-byte. Its outer and nested jars contain no client smoke classes.

## Next verification

Check whether a paid Alloy Smelter job handles ingredient withdrawal while its output is full. Include vanilla smelting batches whose planned output depends on the original input count.
