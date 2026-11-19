# Cycle 153 — Repaint the remaining stock blocks and travel to a painted anchor

The packaged server makes eight gold-painted blocks from the shipped base recipes. Vanilla hoppers feed those exact results into the shipped repainting recipes for fences, fence gates, redstone blocks, sand, slabs, trapdoors, wooden pressure plates and travel anchors. Each recipe gets 600 FE before the harness checks the active recipe ID, retained template, palette and empty output. It gets the remaining 1,800 FE before the harness checks the diamond-painted output in the bottom hopper.

A survival player places the repainted travel anchor, opens its menu and names it Diamond Hub. The player travels to it from an ordinary anchor before and after a dedicated server restart. The reopened client checks that the destination goes away when the block is removed.

## Commands

```sh
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-153-painted-travel-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-153-painted-travel-qa.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_jar=enderio/build/libs/enderio-8.2.14.152-1.20.1+72b7701d06.jar
port_client=build/port-runtime/cycle-153-painted-travel-client
port_previous=build/port-runtime/cycle-152-painted-building-client
mkdir -p "$port_client/config"
cp -cR "$port_previous/libraries" "$port_previous/versions" "$port_client/"
cp "$port_previous/config/fml.toml" "$port_client/config/fml.toml"
python3 scripts/porting/client/prepare_packaged.py --name cycle-153-painted-travel-client \
  --java "$port_java" --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_painted_travel.py --name cycle-153-painted-travel-rendered --port 26040 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --launch "$port_client/launch.json" \
  > build/cycle-153-painted-travel-rendered.log 2>&1
```

## Results

- The full build passes 79 unit cases and 519 native runs, including all 192 main gameplay cases. The revised remapped QA jar builds successfully.
- The packaged server exercises the eight remaining stock repainting paths. Each base recipe makes one gold-painted template. A vanilla hopper feeds that template to the second machine, which pays exactly 2,400 FE across both payments. A bottom hopper receives one diamond-painted result. The palettes stay installed, and the template, output slot and task clear. Together with the earlier cycles, this covers all 28 shipped painting recipes in the packaged jar.
- The first run passes the travel interactions, but its immediate placement screenshot catches the selection outline before the chunk mesh appears. The revised test waits for the mesh and verifies the diamond color in the rendered frame as well as the baked quads.
- The final packaged run passes 48 server assertions and 26 client checks. Native loot retains the diamond paint. The menu edit updates the visible target, and a fresh client receives Diamond Hub and travels to it after restart. Both world captures contain 17,597 matching diamond pixels in the sampled area. Removing the block clears the synced destination. The final `enderio_traveldata.dat` contains only the ordinary anchor at `(2704, 80, 8)`, as decoded with `scripts/porting/saved_world.py`.
- The two clients and two servers exit normally with zero runtime or registry errors. Both peers use the production jar with SHA-256 `c79f8a1d909834c1c050ceaab701afadcf4207d3b7d22d7eb2247158eb95776d`. The observer records zero activation, foreground or visible-window events in 2,650 samples. All four final captures confirm that the narrator is off and every sound category is at zero.

The server results and world are in `build/port-runtime/cycle-153-painted-travel-rendered`. The client reports and captures are in `build/port-evidence/cycle-153-painted-travel-rendered-exercise` and `build/port-evidence/cycle-153-painted-travel-rendered-verify`.

## Next verification

Exercise early survival progression with the shipped recipes. Collect Grains of Infinity from a stock fire-crafting source and use the drops to get the first generator, capacitor, alloy and energy conduit running in packaged Forge.
