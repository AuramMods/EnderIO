# Cycle 162 — Verify item conduit crafting, rates and upgrades

The packaged survival scenario uses the native crafting table to make the basic item conduit and both direct and upgrade recipes for the Energetic and Vibrant tiers. Each recipe yields eight of its saved conduit ID. The upgrade grids exercise the wrong variant first, then consume one item of the required lower tier. The client verifies the displayed effective rates from the actual result stacks.

Two freshly crafted basic conduits cross a chunk boundary. The player replaces both with Energetic conduits and then both with Vibrant conduits through ordinary block interaction. The server verifies all five uniform and mixed route states. At each state, the same installed filter passes 12 paper with the required NBT and keeps seven paper with the other tag in the source barrel. The final route receives a fresh batch after restart.

The native tests sample both endpoints of real Forge networks on every scheduler phase over 20 ticks. They check source and destination counts and tags for three uniform lines and both extraction directions through a mixed Energetic/Vibrant line.

## Commands

```sh
./gradlew :enderio:verifyGameplayTests --console=plain > build/cycle-162-item-rates.log 2>&1
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-162-item-final-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-162-item-client-final-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_previous=build/port-runtime/cycle-159-slicer-client
port_jar=enderio/build/libs/enderio-8.2.14.161-1.20.1+30c16f1d4a.jar
port_client=build/port-runtime/cycle-162-item-client
mkdir -p "$port_client/config"
cp -cR "$port_previous/libraries" "$port_previous/versions" "$port_client/"
cp "$port_previous/config/fml.toml" "$port_client/config/fml.toml"
python3 scripts/porting/client/prepare_packaged.py --name cycle-162-item-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_item_progression.py --name cycle-162-item-filtered --port 26078 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --launch "$port_client/launch.json" \
  > build/cycle-162-item-filtered.log 2>&1
python3 scripts/porting/packaged_energy_progression.py --name cycle-162-energy-regression --port 26077 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" \
  --alloy-world build/port-runtime/cycle-155-alloys-conserved/port-spawners --launch "$port_client/launch.json" \
  > build/cycle-162-energy-regression.log 2>&1
```

## Results

- The first native run passed the existing gameplay tests. The new test class needed an explicit entry in Forge’s gameplay inventory. The final run verifies that the five new tests are discovered and executed.
- The full build passes 79 unit tests and 529 native runs, including all 202 discovered main gameplay cases. The timed tests measure 32 items per 20 ticks for basic, 64 per 20 for Energetic, and 64 per 10 for Vibrant. A mixed line moves 64 per 20 ticks from its Energetic end and 32 per 10 from its Vibrant end, with exact balances and matching tags at both ends.
- The first packaged attempt finishes all five crafts, then stops at the fixture teleport because Minecraft centers an integer coordinate. The next run places both crafted conduits and writes the expected filter, then stops on an invalid NBT path in the assertion. The final fixture uses an explicit block-center coordinate and matches the filter inside its saved connection inventory.
- The finished item run passes 35 client checks and 17 assertions across two dedicated Forge server starts. Each of the five recipes produces eight conduits. The player recovers one lower-tier part for each replacement and finishes with seven basic, 15 Energetic and 14 Vibrant conduits. The same installed filter admits exactly 60 tagged items across the five route states and keeps seven nonmatching items in the source. After restart it admits four more to reach one exact stack of 64.
- The shared energy scenario passes 32 client checks and 15 assertions across two dedicated server starts. The upgraded route and Octadic Capacitor smelter retain the expected 21,920 FE balance after restart. Both scenarios use the same production jar with SHA-256 `4c270303c05e54a4864f58f581590ff25b4bd70a470d6d3172f240ebb7bdb7e9`.
- Both packaged clients run with their narrator off and every sound volume at zero. The macOS observer reports zero activation, foreground or onscreen-window samples over 1,644 item samples and 1,925 energy samples. Server assertions and logs are in `build/port-runtime/cycle-162-item-filtered/` and `build/port-runtime/cycle-162-energy-regression/`. Client checks, captures and native focus reports are in the corresponding `build/port-evidence/` directories with a `-client` suffix.

## Next verification

Exercise the item-conduit mix setting with same-tier routes, runtime replacement and a restart. Check cross-chunk transfers after the two ends have been upgraded at different times while mixing is disabled.
