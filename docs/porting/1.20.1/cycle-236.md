# Cycle 236 — Hang Glider rendering for another player

Two isolated Java 17 Forge clients join the same dedicated server under distinct survival usernames. One wears a tagged Hang Glider. The observer watches the player standing, starting to fall, gliding and standing after landing. After a server restart, both clients reconnect and repeat the sequence with the same glider still equipped.

The observer records the pose submitted to the native glider layer relative to the player's root render pose. Its upright component is 1.0 before departure and after landing. During the actual glide, it is about −0.174, which corresponds to the 100-degree flight rotation. The captures show the canopy and the player's pose. The server confirms each glide via the production advancement trigger and checks forward movement and full health at landing.

## Commands

```sh
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew :enderio:reobfClientSmokeJar --max-workers=4 --console=plain

port_name=cycle-235-glider-baseline
port_runtime=build/port-runtime
port_seed="$port_runtime/cycle-234-vat-fixed-client"
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=build/cycle-235-glider-artifacts/before.jar
port_qa=build/cycle-235-glider-artifacts/qa.jar
export JAVA_TOOL_OPTIONS='-Djava.net.preferIPv4Stack=true -XX:ActiveProcessorCount=4'

prepare_glider_client() {
  port_role="$1"
  port_username="$2"
  port_client="$port_runtime/$port_name-$port_role"
  mkdir -p "$port_client/config"
  cp -cR "$port_seed/libraries" "$port_seed/versions" "$port_client/"
  cp "$port_seed/config/fml.toml" "$port_client/config/"
  python3 scripts/porting/client/prepare_packaged.py --name "$port_name-$port_role" --username "$port_username" \
    --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
    --qa-jar "$port_qa" --assets "$HOME/.gradle/caches/neoformruntime/assets"
}

prepare_glider_client wearer EnderIOQA
prepare_glider_client observer EnderIOObserver
python3 -Bu scripts/porting/packaged_remote_hang_glider.py --name "$port_name" --port 26330 --java "$port_java" \
  --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar" \
  --wearer "$port_runtime/$port_name-wearer/launch.json" --observer "$port_runtime/$port_name-observer/launch.json"
```

## Results

- The first test build used the base entity type to read equipment. Narrowing the probe to the remote player type resolves the compile error. The corrected QA jar builds successfully. See `build/cycle-235-glider-qa.log` and `build/cycle-235-glider-qa2.log`.
- The packaged test passes 11 server assertions and 32 client checks across two dedicated-server launches. Both runs render the canopy upright, rotate it during server-confirmed flight and return it to the upright pose at landing. The tagged equipment survives the restart and both landings are forward and undamaged. The server, wearer and observer use the same production SHA-256 `b7ff21f96258b5f8f4801b7f5dc5748605521a980835fdbaf2e330a37482c614`. See `build/port-runtime/cycle-235-glider-baseline/remote-hang-glider-results.json`.
- The later conduit arrow package contains the same 11 glider and provider classes byte-for-byte.
- All ten frames confirm zero volumes and narration off. The four guarded client launches record zero activations, foreground samples and onscreen samples over 10,091 observations. The two observer captures named `deployed.png` show the actual canopy in flight. See `build/port-evidence/cycle-235-glider-baseline-fresh-observer` and `build/port-evidence/cycle-235-glider-baseline-reopened-observer`.

## Next verification

Exercise the sprint dive and the transition into water in a packaged flight.
