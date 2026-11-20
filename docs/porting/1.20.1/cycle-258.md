# Cycle 258 — Live Enderface preview and picking

The Enderface preview now invalidates its cached scene when Forge's client receives a block update or loads or unloads a chunk affecting the preview. Invalidation checks the active client level and includes the immediate neighbors of the preview volume since their faces can affect its geometry. The preview checks the anchor's current chunk availability. A right-click traces the pointer at the click position through the live scene before selecting a block.

The guarded regression opens a survival Staff of Travelling preview over TCP. The server places an emerald block between the preview camera and a remote copper-filled chest. The client reads the pointer's actual framebuffer pixels and exercises the occluded click inside the same screen. After the server removes the block, the client checks the cleared pixels, rejects a click away from the hovered target, and transfers seven copper ingots through the chest menu. A second view uses 90 degrees of yaw, 25 degrees of pitch and a different zoom to open the remote tank. The existing checks cover its saved fluid, menu closure on anchor removal, and a fresh chest interaction after dedicated restart.

## Commands

```sh
export JAVA_HOME="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
export CI=true JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Djava.net.preferIPv4Stack=true'
./gradlew --no-daemon --no-build-cache --no-configuration-cache --max-workers=2 --console=plain \
  :enderio:assemble :enderio:reobfClientSmokeJar
JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=4 ./gradlew --no-daemon --no-build-cache \
  --no-configuration-cache --max-workers=2 --console=plain check --continue

port_evidence=build/port-evidence/cycle-258-enderface
port_seed=build/port-runtime/cycle-255-redstone-verified-client
port_forge=build/port-runtime/cycle-047-legacy-discover
port_installer=build/port-runtime/forge-47.4.28-installer.jar
port_assets="$HOME/.gradle/caches/neoformruntime/assets"
port_name=cycle-258-enderface-before
port_number=26371
port_jar="$port_evidence/enderio-before.jar"
port_qa="$port_evidence/enderio-client-qa-before.jar"
port_client_mods=()
port_server_mods=()
# The first fixed run uses port_number=26372, enderio-fixed.jar and enderio-client-qa-fixed.jar.
# The final core run uses port_name=cycle-258-enderface-core-final, port_number=26374,
# enderio-fixed.jar and enderio-client-qa-final.jar.
# For the integrated run use port_name=cycle-258-enderface-integrated-final, port_number=26375,
# enderio-fixed.jar and enderio-client-qa-integrated-final.jar.
# Source build/cycle-177-pins.zsh for the integrated profile, set port_server_mods to port_mods,
# and put port_mods plus one --mod argument per port_render jar in port_client_mods.
port_client="build/port-runtime/$port_name-client"
mkdir -p "$port_client/config"
cp -cR "$port_seed/libraries" "$port_seed/versions" "$port_client/"
cp "$port_seed/config/fml.toml" "$port_client/config/"
python3 -B scripts/porting/client/prepare_packaged.py --name "$port_name-client" \
  --java "$JAVA_HOME/bin/java" --installer "$port_installer" --jar "$port_jar" \
  --qa-jar "$port_qa" --assets "$port_assets" "${port_client_mods[@]}"
python3 -Bu scripts/porting/packaged_enderface.py --name "$port_name" --port "$port_number" \
  --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" \
  --jar "$port_jar" --launch "$port_client/launch.json" "${port_server_mods[@]}"
```

The reused client test repeats the server command with `--name cycle-258-enderface-integrated-verified --port 26376`. The passing integrated run prepares the complete profile again as `cycle-258-enderface-integrated-fresh` on port 26375 with `enderio-client-qa-integrated-fresh.jar`.

## Results

- The original packaged client sees the actual server block placement while the same preview is open. The pointer's sample still contains zero emerald pixels. The client fails the new visibility check with its preview still open. Native observation records 1,531 samples with zero activation, foreground and onscreen events. See `build/port-evidence/cycle-258-enderface-before-exercise-client/summary.json`.
- The first fixed run uses the byte-identical QA jar and records 0, 81 and 0 emerald pixels before placement, with the occluder and after removal. The angled view reaches the tank. The server misses the first 8,000 FE staff balance because the client already starts the next use and reaches 7,000 FE. The harness now waits for the server's exact chest and charge assertion between the two clicks. The first integrated run reaches the server but hits the previous nine-second client join deadline during JEI initialization. The join phase now has 40 seconds and keeps the same interaction assertions. See `build/port-evidence/cycle-258-enderface-fixed-exercise-client/result.json`, `build/port-evidence/cycle-258-enderface/fixed.log` and `build/port-evidence/cycle-258-enderface-integrated-exercise-client/focus.json.client.log`.
- The final core run passes 38 client checks and nine server assertions across two sessions. The occluded click leaves the chest intact. The revealed chest transfers all seven copper ingots for the first 1,000 FE, and the rotated view opens the 1,750 mB tank for the second 1,000 FE. The same player reopens the empty chest after restart with the seven ingots intact. See `build/port-runtime/cycle-258-enderface-core-final/enderface-results.json`.
- The next integrated exercise passes the client checks and triggers Embeddium's existing support-policy diagnostic about Oculus modifying its renderer. The harness records the exact diagnostic separately when the captured preview confirms both mods loaded and Oculus reports shaders disabled. See `build/port-evidence/cycle-258-enderface-integrated-final-exercise-client/summary.json`.
- The reused integrated profile disconnects during the Forge login handshake. The fresh retry passes 38 client checks and nine server assertions across the exercise and dedicated restart. The server and client share the exact 15 pinned optional jars and the Ender IO runtime. The client also loads Athena, Embeddium and Oculus with shaders disabled. Each session records the one identified support-policy diagnostic. All four passing dedicated server processes exit with zero registry or runtime errors. See `build/port-runtime/cycle-258-enderface-integrated-fresh/enderface-results.json`.
- The four passing client sessions record zero activation, foreground or onscreen observations across 7,505 samples. All 16 framebuffer captures show disabled narrator, ten muted sound categories, a hidden window and uncaptured cursor. The live occluder sample reads 0, 81 and 0 emerald pixels in both passing profiles. See `build/port-evidence/cycle-258-enderface-{core-final,integrated-fresh}-{exercise,verify}-client/summary.json`.
- The Java 17 CI check passes with 79 unit cases and 548 native runs. The reports include all 180 baseline test methods and all 220 discovered main gameplay cases, with zero failures, errors or skips. The archived report set uses the suites from the current check and excludes an older report from the retired foundation test task. The packaged Ender IO jar has SHA-256 `56f69ca355726726d4b044f4420bafd6b1b7c25993f9a361029d00f129ae3de9`. The client QA remains in its separate test archive. See `build/port-evidence/cycle-258-enderface/{check.log,xml-audit.json}`.

## Next verification

Verify a historical SAG Mill with a custom grinding ball and its original recipe definition. Check whether the port can restore its active modifiers without replacing the stored ball identity or wear. [Cycle 157](cycle-157.md), [Completion audit](completion-audit.md)
