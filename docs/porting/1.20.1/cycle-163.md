# Cycle 163 — Item conduit upgrades with tier mixing disabled

The packaged scenario disables `canMixItemConduitTiers` in disposable Forge client and server profiles. Two freshly crafted basic conduits span a chunk boundary and keep their original barrel connections and NBT filter as the player upgrades each end to Energetic, then Vibrant. The same procedure runs with the default mixing setting as a control.

The original jar places both basic conduits, but the saved faces between them are both disconnected. The configured rule had been applied to matching and mixed variants. Item conduit compatibility now always accepts the same registered variant.

A build with that change joins the two basic conduits, then separates the mixed route and retains both source stacks. After the second Energetic conduit is placed, its west face connects and the first Energetic conduit loses its east face. The replacement removed the former tier's neighbor links after creating the new connection. It now clears former conduit links first, preserving endpoint settings and filters and then checking the new variant's neighbors.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_previous=build/port-runtime/cycle-162-item-client
port_client=build/port-runtime/cycle-163-uniform-client
port_control=build/port-runtime/cycle-163-default-client
port_before=enderio/build/libs/enderio-8.2.14.161-1.20.1+30c16f1d4a.jar
port_jar=enderio/build/libs/enderio-8.2.14.162-1.20.1+5133205a65.jar

mkdir -p "$port_client/config/enderio"
cp -cR "$port_previous/libraries" "$port_previous/versions" "$port_client/"
cp "$port_previous/config/fml.toml" "$port_client/config/fml.toml"
cat > "$port_client/config/enderio/conduits-common.toml" <<'EOF'
[conduits]
canMixEnergyConduitTiers = true
canMixFluidConduitTiers = true
canMixItemConduitTiers = false
EOF
python3 scripts/porting/client/prepare_packaged.py --name cycle-163-uniform-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_before" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_item_progression.py --name cycle-163-before --no-tier-mixing --port 26079 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_before" --launch "$port_client/launch.json" \
  > build/cycle-163-before.log 2>&1

# After the matching-variant fix, before the replacement fix.
./gradlew :enderio:reobfJar --console=plain > build/cycle-163-matching-build.log 2>&1
cp "$port_jar" build/cycle-163-matching.jar
cp "$port_jar" "$port_client/mods/enderio.jar"
python3 scripts/porting/packaged_item_progression.py --name cycle-163-matching --no-tier-mixing --port 26080 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --launch "$port_client/launch.json" \
  > build/cycle-163-matching.log 2>&1

# With both fixes applied.
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-163-final-build.log 2>&1
mkdir -p "$port_control/config"
cp -cR "$port_client/libraries" "$port_client/versions" "$port_control/"
cp "$port_client/config/fml.toml" "$port_control/config/fml.toml"
for port_name in cycle-163-uniform-client cycle-163-default-client; do
  python3 scripts/porting/client/prepare_packaged.py --name "$port_name" --java "$port_java" \
    --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
    --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
done
python3 scripts/porting/packaged_item_progression.py --name cycle-163-uniform --no-tier-mixing --port 26081 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --launch "$port_client/launch.json" \
  > build/cycle-163-uniform.log 2>&1

# With world spawn moved away before removing the chunk tickets.
python3 scripts/porting/packaged_item_progression.py --name cycle-163-eviction --no-tier-mixing --port 26083 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --launch "$port_client/launch.json" \
  > build/cycle-163-eviction.log 2>&1
python3 scripts/porting/packaged_item_progression.py --name cycle-163-default --port 26082 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --launch "$port_control/launch.json" \
  > build/cycle-163-default.log 2>&1
```

## Results

- The before and intermediate runs stop on distinct topology assertions. Their reports and saved block entities are under `build/port-runtime/cycle-163-before/` and `build/port-runtime/cycle-163-matching/`.
- The first attempt with both fixes passes all five live route states. The chunk assertion exposes a fixture issue because world spawn remains next to the test route. The scenario now moves spawn away after the client leaves, then removes the force-load tickets and checks for actual eviction.
- The full build passes 79 unit cases and 529 native runs, including all 202 main gameplay cases. The packaged matching-only profile passes 35 client checks and 20 server assertions. Each of the three uniform route states delivers exactly 12 tagged paper. In each mixed state the next 12 remain in the source barrel for at least 40 ticks and transfer when the other end is upgraded. The reloaded and restarted Vibrant route accepts four more items each time, ending with 44 tagged paper in the receiver and seven nonmatching paper still in the source.
- The default mixing profile passes 35 client checks and 17 server assertions. All five route states transfer their full batch, including the two mixed states. After restart the route receives four more items and has one exact stack of 64. Both profiles use the production jar with SHA-256 `eecd312fdb1593948dcc0119827f3e96ec0119ccda39661684ccc1fbcd3fddac`.
- All four server starts exit cleanly with zero runtime or registry errors. The guarded clients record zero activation, foreground or onscreen-window samples across 1,853 matching-only and 1,730 default-profile observations. Every capture shows the narrator off and all volumes at zero.

The reports are `build/port-runtime/cycle-163-eviction/item-progression-results.json` and `build/port-runtime/cycle-163-default/item-progression-results.json`. The client captures and focus reports are under `build/port-evidence/cycle-163-eviction-client/` and `build/port-evidence/cycle-163-default-client/`.

## Next verification

Exercise the equivalent fixed-tier settings for energy, fluid and Mekanism chemical conduits. Include a center replacement that leaves two separate routes and confirm both remain available after saving and loading.
