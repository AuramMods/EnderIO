# Cycle 149 — Verify painted redstone blocks in survival

Four packaged Painting Machines use the shipped recipes for the redstone block, wooden pressure plate, fence gate and trapdoor. The fixture uses a different reusable paint for each recipe and extracts each typed result through a vanilla hopper. A survival player places each output over TCP. The client checks the exact paint and baked geometry and captures each block in the world.

The redstone block powers vanilla dust and a lamp. A dropped item presses the wooden plate and lights its lamp. The gate fits between two walls. The gate and trapdoor open and close by hand and open again under redstone power. The server checks all four native loot items and keeps the powered fixtures through a dedicated restart before releasing the signals.

## Commands

```sh
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-149-painted-redstone-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-149-painted-redstone-qa.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.148-1.20.1+345424402e.jar
port_client=build/port-runtime/cycle-149-painted-redstone-client
port_previous=build/port-runtime/cycle-148-legacy-sand-client
mkdir -p "$port_client/config"
cp -cR "$port_previous/libraries" "$port_previous/versions" "$port_client/"
cp "$port_previous/config/fml.toml" "$port_client/config/fml.toml"
python3 scripts/porting/client/prepare_packaged.py --name cycle-149-painted-redstone-client \
  --java "$port_java" --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_painted_redstone.py --name cycle-149-painted-redstone-fixed --port 26029 \
  --java "$port_java" --forge-server build/port-runtime/cycle-047-legacy-discover \
  --jar "$port_jar" --launch "$port_client/launch.json" > build/cycle-149-painted-redstone-fixed.log 2>&1
```

## Results

- The build passes 79 unit tests and 519 native runs, including all 192 main gameplay cases. Each of the four recipes consumes one template and exactly 2,400 FE. The hopper extracts one painted item and the reusable palette stays in the machine.
- Packaged multiplayer passes 44 client checks and 27 server assertions. The diamond-painted redstone block drives dust at 15 and lights an adjacent lamp. The gold-painted wooden plate detects a dropped item and powers its lamp. The emerald-painted gate fits between cobblestone walls. It and the lapis-painted trapdoor respond to ordinary hand use and redstone. The client renders the correct paint and a distinct shape for the pressed plate and open gate and trapdoor.
- Native loot returns one item with the correct paint for each fixture. A dedicated restart preserves the active states, power outputs and all four paint tags. After restart, removing the signal sources restores the resting states and clears the outputs.
- The first client QA launch read the test block handles during Forge construction. The corrected QA runner reads them after login. The final remapped QA build passes. Both final server sessions and the final client exit normally with zero reported runtime or registry errors. Client and server load the same production jar with SHA-256 `625f9f30426dfec720d53608345674ea1330473b3878660e3a467d56783be5a5`.
- Across both client launches, 4,602 native observations record zero activation, foreground or visible-window events. The eight captured frames show every volume category at zero and the narrator off.

Server results are in `build/port-runtime/cycle-149-painted-redstone-fixed/painted-redstone-results.json`. Frame captures and focus reporting are under `build/port-evidence/cycle-149-painted-redstone-fixed-client`.

## Next verification

Verify waterlogged painted trapdoor behavior and convert placed states and original loot for these four blocks from a disposable Forge 6.2 save.
