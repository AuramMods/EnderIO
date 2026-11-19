# Cycle 148 — Convert painted sand saved by Forge 6.2

The official Forge 6.2.18-beta jar saves gold- and emerald-painted sand blocks in place. Its own loot table copies the emerald paint to an original drop in a barrel. Removing a third block's support spawns a diamond-painted falling entity. The fixture suspends the entity after it has started falling and saves it in the air. The port opens a copy of the resulting world.

The port reads the original block entities, imports the barrel item into the current item format, and restores the original falling entity. The guarded multiplayer client renders the falling entity and replants the emerald drop in survival. The server resumes gravity and tests native loot from the landed diamond-painted sand. A separate fresh-world run checks the same client harness through the Painting Machine recipe and both falling outcomes.

## Commands

```sh
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-148-legacy-sand-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_old=build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar
port_jar=enderio/build/libs/enderio-8.2.14.147-1.20.1+aefc1bafe3.jar
port_client=build/port-runtime/cycle-148-legacy-sand-client
port_previous=build/port-runtime/cycle-147-painted-sand-client
mkdir -p "$port_client/config"
cp -cR "$port_previous/libraries" "$port_previous/versions" "$port_client/"
cp "$port_previous/config/fml.toml" "$port_client/config/fml.toml"
python3 scripts/porting/client/prepare_packaged.py --name cycle-148-legacy-sand-client \
  --java "$port_java" --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_legacy_painted_sand.py --name cycle-148-legacy-sand --port 26026 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_old" \
  --jar "$port_jar" --launch "$port_client/launch.json" > build/cycle-148-legacy-sand.log 2>&1
python3 scripts/porting/packaged_painted_sand.py --name cycle-148-fresh-sand-regression --port 26027 \
  --java "$port_java" --forge-server "$port_forge" \
  --jar "$port_jar" --launch "$port_client/launch.json" > build/cycle-148-fresh-sand-regression.log 2>&1
```

## Results

- The full build passes 79 unit cases and 519 native runs, including all 192 discovered main gameplay cases. The remapped client QA jar builds successfully.
- The Forge 6.2 conversion passes 14 server assertions and eight client checks. The original gold and emerald block entities survive. The emerald drop converts from `BlockEntityTag.Paint` to the port's typed item data, then one ordinary survival placement restores that paint. The client renders the replanted block and the original in-flight diamond entity. The diamond entity resumes falling, lands with its original paint and gives one typed loot item. All original paint and the new loot survive another dedicated restart.
- The fresh-world regression passes 16 server assertions and 11 client checks. Both packaged runs use the same production jar on the port server and the client. Its SHA-256 is `24ca56fcd7905556b9ceb769c39de5ea15df74fd44997f71735c163e49382ae6`. The original 6.2.18-beta jar has SHA-256 `9d1471e6d4562d0a50f0564bc46573b87fd738bd440fe19397d31847da20f172`.
- All six server runs and both guarded clients exit normally with zero reported client, server or registry errors. The observer records 2,734 native samples across both clients with zero activation, foreground or visible-window events. All four frames have the narrator off and every sound category at zero.

The packaged results are under `build/port-runtime/cycle-148-legacy-sand` and `build/port-runtime/cycle-148-fresh-sand-regression`. Client reports and frames are in the corresponding directories ending in `-client` under `build/port-evidence`.

## Next verification

Exercise the painted redstone block, wooden pressure plate, fence gate and trapdoor with their shipped painting recipes and ordinary redstone interactions.
