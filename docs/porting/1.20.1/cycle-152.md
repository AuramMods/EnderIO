# Cycle 152 — Convert and repaint Forge 6.2 building blocks

Forge 6.2.18-beta saves an emerald-painted top-half stair corner, a joined gold-painted wall, a diamond-painted crafting table and obsidian-painted glowstone. The corner and wall are both waterlogged. The original native loot tables save two painted items of each kind. The port opens a copy, checks the original state and imports the items into typed paint data.

One set of items replants in survival. The stairs and wall go into source water and keep it when their shapes change. The table crafts six slabs from three planks, and the obsidian-painted glowstone lights the scene. Native hoppers feed the other set into four repainting jobs. Each job pays 600 FE before restart and receives the remaining 1,800 FE afterward.

## Commands

```sh
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-152-painted-building-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_old=build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar
port_jar=enderio/build/libs/enderio-8.2.14.151-1.20.1+af2c87244a.jar
port_client=build/port-runtime/cycle-152-painted-building-client
port_previous=build/port-runtime/cycle-151-painted-building-client
mkdir -p "$port_client/config"
cp -cR "$port_previous/libraries" "$port_previous/versions" "$port_client/"
cp "$port_previous/config/fml.toml" "$port_client/config/fml.toml"
python3 scripts/porting/client/prepare_packaged.py --name cycle-152-painted-building-client \
  --java "$port_java" --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_legacy_painted_building.py --name cycle-152-painted-building-backdrop --port 26038 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_old" \
  --jar "$port_jar" --launch "$port_client/launch.json" > build/cycle-152-painted-building-backdrop.log 2>&1
```

## Results

- The full build passes 79 unit cases and 519 native runs, including all 192 main gameplay cases. The remapped client QA jar builds successfully. A separate Forge 6.2 fixture run passes all seven assertions for the placed blocks and native loot.
- The final packaged run passes all 42 server assertions and 53 client checks. The source corner and wall retain their water and shapes during conversion. Both sets of legacy loot convert to typed paint. The survival replants retain source water during placement, neighbor changes, restart and neighbor removal. The table consumes three planks for six slabs, which survive reconnect.
- Vanilla hoppers feed the second set of imported items into the shipped repainting recipes. All four jobs save and resume at exactly 600 of 2,400 FE. Applying the remaining 1,800 FE produces one gold stair, diamond wall, obsidian table and emerald glowstone. Output hoppers recover one result each. The machines retain their palettes and clear the consumed templates and finished tasks.
- The obsidian-painted glowstone gives block light 15 at the source and 14 beside it before and after restart. Removing it clears both readings. The initial run measured those readings successfully, but its dark framebuffer fell below the shared capture threshold. Two non-emitting Painting Machines give both final frames the same visible reference. The camera, brightness checks and capture threshold stay the same.
- All three final server sessions and both clients exit zero with no runtime or registry errors. The original jar SHA-256 is `9d1471e6d4562d0a50f0564bc46573b87fd738bd440fe19397d31847da20f172`. Both current peers use SHA-256 `38a599bc2e5e10e22516119a7b9797563878138df1db57966c56660596810203`. The native observer records zero focus, activation or visible-window observations in 3,170 samples across the two final clients. All nine final captures confirm that the narrator is off and every sound category is at zero.

The final server report is `build/port-runtime/cycle-152-painted-building-backdrop/legacy-painted-building-results.json`. Client reports and frames are under `build/port-evidence/cycle-152-painted-building-backdrop-exercise` and `build/port-evidence/cycle-152-painted-building-backdrop-verify`. The separate original release fixture report is `build/port-runtime/cycle-152-painted-building-legacy-probe/legacy-probe.json`.

## Next verification

Exercise the remaining shipped repainting recipes. Paint an ordinary travel anchor and verify placement and travel with the painted result.
