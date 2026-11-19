# Cycle 151 — Verify painted building blocks in packaged Forge

Four Painting Machines use the shipped recipes for stairs, a wall, a crafting table and glowstone. Each job consumes one template and 2,400 FE, and a vanilla hopper pulls out the painted result. A survival player places all four results over TCP.

A spruce stair forces the emerald-painted stair to form an outside corner. Adding a second vanilla wall connects both sides of the gold-painted wall and lowers its center post. The diamond-painted table opens the native 3×3 crafting screen and crafts six slabs from three planks. Obsidian-painted glowstone lights the midnight scene. A fresh client checks the restored light after restart and watches it clear when the glowstone is removed.

## Commands

```sh
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-151-painted-building-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-151-painted-building-qa.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_jar=enderio/build/libs/enderio-8.2.14.150-1.20.1+e566f03df6.jar
port_client=build/port-runtime/cycle-151-painted-building-client
port_previous=build/port-runtime/cycle-150-painted-redstone-client
mkdir -p "$port_client/config"
cp -cR "$port_previous/libraries" "$port_previous/versions" "$port_client/"
cp "$port_previous/config/fml.toml" "$port_client/config/fml.toml"
python3 scripts/porting/client/prepare_packaged.py --name cycle-151-painted-building-client \
  --java "$port_java" --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_painted_building.py --name cycle-151-painted-building-synced --port 26035 \
  --java "$port_java" --forge-server "$port_forge" \
  --jar "$port_jar" --launch "$port_client/launch.json" > build/cycle-151-painted-building-synced.log 2>&1
```

## Results

- The full build passes 79 unit cases and 519 native runs, including all 192 main gameplay cases. The remapped QA jar builds successfully. The shared Painting Machine fixture also passes nine headless regression assertions for the previous redstone block recipes and hopper extraction. Its results are under `build/port-runtime/cycle-151-painted-redstone-hopper-regression`.
- Packaged Forge passes 47 guarded client checks and 22 server assertions. The four jobs each consume one template and 2,400 FE, and every palette remains installed. Survival placement and native loot preserve emerald, gold, diamond and obsidian paint. The stair and wall change their baked geometry when the neighbors join. The changed shapes survive restart and revert to the expected shapes after the neighbors are removed.
- The diamond-painted table stays open with the native 3×3 grid. The recipe book puts one plank in each of three slots and previews six slabs. Taking the result consumes all three planks, and all six slabs remain in the player inventory after reconnect. The first QA run read the preview as soon as the result arrived. The successful run captures the preview after the ingredient slots sync and records both counts in the client report.
- The obsidian-painted glowstone raises block light to 15 at the source and 14 beside it. A fresh client measures the same values after restart. Removing the block returns both readings to zero. The after-restart frames show the same spot with and without the source.
- Both final server sessions and both final clients exit normally with zero reported runtime or registry errors. The client and server use the same production jar with SHA-256 `9e6cd20a3a2a7ba13245b41cea2a773fb947c7b1dbcaad04c0c41f4eebae6b12`. Across 4,861 native samples from the three launches, the observer records zero activation, foreground or visible-window events. All nine final frames have the narrator off and every sound category at zero.

The final server report is `build/port-runtime/cycle-151-painted-building-synced/painted-building-results.json`. The client reports and frames are under `build/port-evidence/cycle-151-painted-building-synced-exercise` and `build/port-evidence/cycle-151-painted-building-synced-verify`.

## Next verification

Convert a Forge 6.2 fixture with painted stairs, walls, a crafting table and glowstone. Exercise waterlogged stairs and walls and the shipped repainting recipes.
