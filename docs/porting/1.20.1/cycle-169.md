# Cycle 169 — Conserve XP in vials and rods

The packaged survival test starts with tools provided in the fixture and compares the player's XP, both held vials, a Mending item and the tank against the real server state. A guarded client clicks the tank and runs the normal item-use countdown. The server creates deterministic groups of merged vanilla orbs and checks that every point reaches the vial, Mending or the player.

On the original jar, collecting three merged seven-point orbs fills the vial with 140 mB and awards zero XP. Forge decrements a merged orb's count once per pickup, so writing the absorbed value to the entity also overwrote every remaining member. The vial now handles the XP after Forge accepts the pickup, immediately before native XP and Mending processing. The per-pickup remainder reaches vanilla while the original orb value is restored for later pickups.

The original rod draws 140 mB from a tank holding 159 mB and awards zero XP. It inspects the remaining tank after executing the first drain. The fixed rod simulates both directions before transferring, requests only whole points and uses the actual transfer result for the player balance. Vial drinking applies the XP and fluid change on the server.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_profile=cycle-169-xp-client
port_before=enderio/build/libs/enderio-8.2.14.167-1.20.1+97e9d2f05a.jar
port_jar=enderio/build/libs/enderio-8.2.14.168-1.20.1+9a311ec4a1.jar
port_qa=enderio/build/test-libraries/enderio-client-qa.jar
port_assets="$HOME/.gradle/caches/neoformruntime/assets"

mkdir -p "$port_runtime/$port_profile/config"
cp -cR "$port_runtime/cycle-168-model-core/libraries" "$port_runtime/cycle-168-model-core/versions" \
  "$port_runtime/$port_profile/"
cp "$port_runtime/cycle-168-model-core/config/fml.toml" "$port_runtime/$port_profile/config/"
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-169-client-build.log 2>&1
python3 scripts/porting/client/prepare_packaged.py --name "$port_profile" --java "$port_java" \
  --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_before" --qa-jar "$port_qa" --assets "$port_assets"
python3 scripts/porting/packaged_xp_tools.py --name cycle-169-before --port 26101 --java "$port_java" \
  --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_before" \
  --launch "$port_runtime/$port_profile/launch.json" --reproduce

./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-169-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-169-client-final.log 2>&1
python3 scripts/porting/client/prepare_packaged.py --name "$port_profile" --java "$port_java" \
  --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" --qa-jar "$port_qa" --assets "$port_assets"
python3 scripts/porting/packaged_xp_tools.py --name cycle-169-fixed --port 26102 --java "$port_java" \
  --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar" \
  --launch "$port_runtime/$port_profile/launch.json"
```

## Results

- The before run records both XP losses in an actual packaged Forge world. The client and server both complete normally. The comparison reports two failed assertions and retains the measured XP and fluid values in the server log.
- The full build discovers and passes all 203 main gameplay cases. Across all profiles it passes 79 unit cases and 530 native runs.
- The fixed main-hand vial stores all 21 XP as 420 mB. The offhand vial starts with 3,150 mB and captures two of the next 14 points. Vanilla uses six points to repair 12 damage on the boots and awards the other six to the player. The main-hand vial keeps its 420 mB while the offhand has priority.
- Native tank clicks empty the main-hand vial and refill it with 455 mB. Drinking awards 22 points and leaves 15 mB. A second attempt preserves that fractional remainder. The rod withdraws 260 mB and then 40 mB, awarding 13 and two points and leaving 19 mB. With 29 mB free, crouching with the rod deposits 20 mB and costs one point.
- The fixed jar passes 12 packaged server assertions and 15 client checks across two dedicated-server starts. The restarted world retains 42 player XP, 15 and 3,190 mB in the two vials, the rod and 15,991 mB in the tank. The reconnected client shows the correct fractional tooltip. Both servers stop with zero runtime or registry errors.
- The focus watcher records zero foreground, activation or on-screen events across 1,465 initial and 1,291 restarted samples. The client remains hidden and windowed with the narrator off and every volume at zero. Screenshots, client checks and native logs are under `build/port-evidence/cycle-169-{before-initial,fixed-initial,fixed-restarted}-client/`. The server reports are in `build/port-runtime/cycle-169-{before,fixed}/xp-tools-results.json`. The production jar SHA-256 is `aee822f7e8f3302e4ae83c3de7c974dbb9c2f19c1c98885302e69d393b80ddfc`.

## Next verification

Check survival acquisition for the XP tools and other remaining crafted items. Continue the packaged connected-texture and shader checks.
