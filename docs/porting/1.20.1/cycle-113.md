# Cycle 113 — Restore the wearable Hang Glider

The NeoForge baseline ships Wing and Glider recipes, steering, an item tag, a movement criterion and a player render layer. Its standard Glider lacks an active chest-slot implementation and a worn model. The port equips the existing item through vanilla's chest-slot interaction and bakes a worn canopy using the shipped Glider sprite. The ordinary inventory sprite and both recipe IDs stay the same.

The local player steers after the deployment delay. On dedicated servers the player position comes from accepted movement packets, so the server measures the descent using positions and applies the criterion and fall-distance reset there. Vanilla's movement handler recognizes a legitimate, equipped glide during accepted downward movement with the default dedicated-server flight setting. Grounded, crouching, submerged, riding, spectator and creative-flight states prevent activation.

## Commands

```sh
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-113-qa-diag.log 2>&1
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-113-final-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-113-qa-stable.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_previous=build/port-runtime/cycle-112-final-client
port_jar=enderio/build/libs/enderio-8.2.14.112-1.20.1+b15df5e633.jar

prepare_client() {
  port_client="build/port-runtime/$1"
  mkdir -p "$port_client"
  cp -R "$port_previous/libraries" "$port_previous/versions" "$port_client/"
  python3 scripts/porting/client/prepare_packaged.py --name "$1" --java "$port_java" \
    --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$2" \
    --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
}

run_glider() {
  port_client="build/port-runtime/$3"
  python3 scripts/porting/packaged_hang_glider.py --name "$1" --port "$2" --java "$port_java" \
    --forge-server "$port_forge" --jar "$port_client/mods/enderio.jar" --launch "$port_client/launch.json"
}

prepare_client cycle-113-before-client "$port_previous/mods/enderio.jar"
run_glider cycle-113-diagnose 25914 cycle-113-before-client
prepare_client cycle-113-final-client "$port_jar"
run_glider cycle-113-confirmed 25916 cycle-113-final-client
```

## Results

- The previous package crafted both wings and the Glider through the native survival crafting screen. Right-clicking the Glider failed to equip it. See `build/port-evidence/cycle-113-diagnose-exercise-client/result.json`.
- An intermediate package equipped the item, deployed it, rendered the canopy and awarded the test datapack's native `enderio:use_glider` criterion. The dedicated server then disconnected the descending player for floating. See `build/port-runtime/cycle-113-flight/server/smoke-fresh.log` and `build/port-evidence/cycle-113-flight-exercise-client/result.json`.
- The final full build passes all 79 JUnit cases and 483 Forge GameTest executions, including every one of the 161 discovered gameplay cases. See `build/cycle-113-final-build.log` and the three modules' `build/test-results/test` directories.
- The first final client run caught a transient nugget preview as the vanilla recipe book filled the crafting grid. The QA client now waits for the requested recipe output before taking it, and still checks the exact ingredient and product counts. The stabilization changes only the QA jar. See `build/port-evidence/cycle-113-verified-exercise-client/result.json` and `build/cycle-113-qa-stable.log`.
- The first confirmed flight lands about 175 blocks forward at 20 health from a height of 17 blocks and awards the custom datapack criterion. The crouched fall from the same height lands at the launch coordinates, takes damage and awards no criterion. The test profile has `allow-flight=false`. The dedicated server logs movement warnings on fixture teleports and accepts both landings. See `build/port-runtime/cycle-113-confirmed/server/smoke-fresh.log`.
- After a complete server restart, the player reconnects with the crafted Glider still equipped and repeats the safe landing and criterion award. The final packaged sessions pass nine server assertions and 21 guarded client checks. Both server runs exit at zero with zero runtime or registry errors. See `build/port-runtime/cycle-113-confirmed/hang-glider-results.json`. The client and server artifact SHA-256 is `a6894fde0958bda637f4e5155d19c4a87595dbd7b10428338ccbd8cd95b3b3c6`.
- The two native captures show the canopy on the player in flight. The window observer recorded 4,197 samples without activation, foreground or on-screen events. All sound categories are at zero and the narrator is off. See `build/port-evidence/cycle-113-confirmed-exercise-client/summary.json` and `build/port-evidence/cycle-113-confirmed-verify-client/summary.json`.

## Remaining verification

Cross-player rendering, sprint dive, interruption in water or on mounts, equipped chestplate swaps and packaged integrated-server gliding remain open. Dyed variants and the built-in adventure advancement are disabled in the source baseline. The fixture registers an isolated datapack criterion for the production trigger.
