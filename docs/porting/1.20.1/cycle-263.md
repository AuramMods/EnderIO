# Cycle 263 — Enchanter placement and book presentation

The fixed 1.21.1 source uses a static composite model. Its book child tilts by −22.5 degrees around X and translates up and toward the front of the stand. The book's own elements contain the page and cover angles. The Forge asset retains those same transforms and blockstate rotations.

The packaged dedicated fixture gives a survival player five Enchanters. The guarded client places four using ordinary block-use packets aimed at the stone platform, with the player looking from the four cardinal directions. The server confirms each resulting facing and the exact remaining stack. The client reads the live baked page and cover quads. It compares the bounds to those obtained by applying the baseline asset transforms and checks the rotated vertices against the north-facing model. Each world capture samples the drawn pages above the stand.

The player opens the native inventory with the fifth Enchanter. The test checks the actual item model's book and stand passes and compares the rendered slot with an empty neighboring slot. After a resource reload it rechecks the four world models and captures the west-facing scene again. A dedicated restart separately confirms the four blocks are saved with their placed facings.

## Commands

```sh
export JAVA_HOME="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
export CI=true JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=4
./gradlew --no-daemon --no-build-cache --no-configuration-cache --max-workers=2 --console=plain \
  :enderio:assemble :enderio:reobfClientSmokeJar

port_runtime=build/port-runtime
port_seed="$port_runtime/cycle-262-bank-core-client"
port_forge="$port_runtime/cycle-047-legacy-discover"
port_jar=enderio/build/libs/enderio-8.2.14.262-1.20.1+af04dc2e61.jar
port_qa=enderio/build/test-libraries/enderio-client-qa.jar
export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Djava.net.preferIPv4Stack=true'

prepare_enchanter_client() {
  port_mode="$1"
  shift
  port_profile="cycle-263-enchanter-$port_mode-client"
  port_client="$port_runtime/$port_profile"
  mkdir -p "$port_client/config"
  cp -cR "$port_seed/libraries" "$port_seed/versions" "$port_client/"
  cp "$port_seed/config/fml.toml" "$port_client/config/"
  python3 -B scripts/porting/client/prepare_packaged.py --name "$port_profile" --java "$JAVA_HOME/bin/java" \
    --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" --qa-jar "$port_qa" \
    --assets "$HOME/.gradle/caches/neoformruntime/assets" "$@"
}
prepare_enchanter_client core-final
prepare_enchanter_client embeddium --mod "$port_runtime/cycle-176-verified-client/mods/embeddium-UTbfe5d1.jar"

run_enchanter_presentation() {
  python3 -B scripts/porting/packaged_enchanter_presentation.py --name "cycle-263-enchanter-$1" \
    --renderer "$2" --port "$3" --launch "$port_runtime/cycle-263-enchanter-$1-client/launch.json" \
    --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" --jar "$port_jar"
}
run_enchanter_presentation core-final core 26402
run_enchanter_presentation embeddium embeddium 26400
```

## Results

- The Java 17 package and remapped client QA jar build successfully. The tested runtime SHA-256 is `9d71e736d0aa0b1811a4ff26d4c262dc94843396d6d005f137602e40c046d1f0`. The final separate QA jar is `27b5863551948dbd02ae9c1ad46ba69c93a5d8bcabef0e70b2bb196bd9293fae`. See `build/port-evidence/cycle-263-enchanter-presentation/{build.log,qa-placement.log}`.
- In the first two runs the client predicts successful placement immediately after a fixture teleport. The server keeps the input count and has no block entity at the target. The server can reject block use while a teleport is pending. The final fixture allows five client ticks at the requested camera before sending the normal placement and checks the authoritative server state before proceeding. See `build/port-evidence/cycle-263-enchanter-presentation/{first-world-diagnosis.log,core-retry.log,qa-placement.log}`.
- Plain Forge and Embeddium 0.3.31+mc1.20.1 each pass 55 client checks. Every facing and the inventory model contain eight page quads and four cover quads at the expected coordinates, plus the stand. The native inventory icon changes 433 pixels compared with the adjacent empty slot in each renderer. The west-facing capture retains the same page pixel count after reload in each profile. See `build/port-evidence/cycle-263-enchanter-{core-final,embeddium}-client/`.
- Each profile passes seven dedicated-server assertions. The player spends exactly four of the five Enchanters, and all four facings survive restart. The four final server processes exit zero without registry or runtime errors. The two clients exit zero without runtime errors. The observer records 3,630 samples with zero activation, foreground or onscreen events. All twelve captures verify an uncaptured cursor, narrator off and ten muted sound categories. See `build/port-runtime/cycle-263-enchanter-{core-final,embeddium}/enchanter-presentation-results.json`.

## Next verification

Exercise a Relocator spawn placement variant that has yet to be checked in a packaged world. [Completion audit](completion-audit.md)
