# Cycle 147 — Verify painted sand through its falling lifecycle

The shipped Painting Machine recipe turns two sand templates into a stack of two diamond-painted sand blocks while retaining the palette. A packaged multiplayer client takes the output through the machine menu and places each block in survival. Removing the supports produces two native falling entities. The client checks the synced state, paint and renderer and captures the diamond surface in the framebuffer.

The server briefly suspends the already spawned entities to keep the frame repeatable and save both entities in flight. It resumes gravity after a dedicated restart. The clear column yields a landed block and the torch column yields one carried item. Native block loot and a second restart check that the original paint persists.

## Commands

```sh
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-147-painted-sand-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.146-1.20.1+46db1fbe4a.jar
port_client=build/port-runtime/cycle-147-painted-sand-client
port_previous=build/port-runtime/cycle-146-me-colors-client
mkdir -p "$port_client/config"
cp -cR "$port_previous/libraries" "$port_previous/versions" "$port_client/"
cp "$port_previous/config/fml.toml" "$port_client/config/fml.toml"
python3 scripts/porting/client/prepare_packaged.py --name cycle-147-painted-sand-client \
  --java "$port_java" --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_painted_sand.py --name cycle-147-painted-sand --port 26024 \
  --java "$port_java" --forge-server build/port-runtime/cycle-047-legacy-discover \
  --jar "$port_jar" --launch "$port_client/launch.json" > build/cycle-147-painted-sand-packaged.log 2>&1
```

## Results

- The full build passes 79 unit tests and 519 native runs. The 192 main gameplay cases include native placement, airborne paint, landing, block loot and the exact single drop when a torch prevents landing. The separate remapped client QA jar builds successfully.
- Packaged Forge passes 11 client checks and 16 server assertions. The machine spends exactly 4,800 FE, consumes two templates, delivers two diamond-painted sand items and retains one diamond block in the palette. The player collects both outputs through the regular menu and consumes one per survival placement.
- The falling entity's spawn packet carries the original painted-sand state and diamond paint. The registered renderer draws the diamond surface, with 16,065 diamond-colored pixels in the central crop. After the first dedicated restart, both suspended entities resume falling. One lands as a painted block and produces the typed loot item. The torch stays in place and the other entity drops the same painted item. The landed block, both item results and machine balance survive the second restart.
- All three packaged servers and the client exit normally with zero reported client, server or registry errors. The client and server load the same production jar with SHA-256 `49917ef290abe1f7a3d5ca27d147ab6c8e5583f2bbc4367fbfc08d669ac9673e`. Across 1,439 native observations, the client records zero activation, foreground or visible-window events. Both frames have all sound categories at zero and the narrator off.

Results and server logs are under `build/port-runtime/cycle-147-painted-sand`. The client result and two frames are under `build/port-evidence/cycle-147-painted-sand-client`.

## Next verification

Convert a disposable Forge 6.2 world with a placed painted sand block, an original painted drop and a saved falling entity. Verify that each retains its paint through conversion and native gameplay.
