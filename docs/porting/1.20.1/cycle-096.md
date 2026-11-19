# Cycle 096 — Verify FTB Ultimine conduit and facade mining

## Scope

Use the registered Ultimine key and normal survival mining on a packaged Forge client connected over TCP. Preview and mine across a chunk boundary in the default shapeless mode. Test an oak facade beside matching, different and uncovered bundles. Test an energy line beside a mixed bundle and a redstone-only bundle. Repeat without FTB Ultimine and reopen each dedicated server to verify block and drop state.

## Commands

```sh
./gradlew :enderio:assemble :enderio:reobfClientSmokeJar --console=plain > build/cycle-096-baseline-build.log 2>&1
./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-096-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.95-1.20.1+872092f6c3.jar
port_qa=enderio/build/test-libraries/enderio-client-qa.jar
port_server=build/port-runtime/cycle-047-legacy-discover
port_installer=build/port-runtime/forge-47.4.28-installer.jar
port_deps="$HOME/.gradle/caches/modules-2/files-2.1"
port_ult="$port_deps/dev.ftb.mods/ftb-ultimine-forge/2001.1.8/c2a1eb72c36ad067faafbff4e1618c4db906de74/ftb-ultimine-forge-2001.1.8.jar"
port_lib="$port_deps/dev.ftb.mods/ftb-library-forge/2001.2.13/a5427d5ff07eb044144ed0b54308370d88bce17e/ftb-library-forge-2001.2.13.jar"
port_arch="$port_deps/maven.modrinth/architectury-api/1MKTLiiG/a52918a500ff421180a1cbd1a0cabee712134ec4/architectury-api-1MKTLiiG.jar"
port_prior=build/port-runtime/cycle-093-both-client

for mode in baseline absent; do
  profile="cycle-096-$mode-client"
  mkdir -p "build/port-runtime/$profile"
  cp -R "$port_prior/libraries" "$port_prior/versions" "build/port-runtime/$profile/"
  set --
  if [ "$mode" = baseline ]; then set -- --mod "$port_ult" --mod "$port_lib" --mod "$port_arch"; fi
  python3 scripts/porting/client/prepare_packaged.py --name "$profile" --java "$port_java" \
    --installer "$port_installer" --jar "$port_jar" --qa-jar "$port_qa" \
    --assets "$HOME/.gradle/caches/neoformruntime/assets" "$@"
done

python3 scripts/porting/packaged_ultimine.py --name cycle-096-enabled --port 25867 \
  --java "$port_java" --forge-server "$port_server" --jar "$port_jar" \
  --launch build/port-runtime/cycle-096-baseline-client/launch.json --ultimine "$port_ult" "$port_lib" "$port_arch"
python3 scripts/porting/packaged_ultimine.py --name cycle-096-control --port 25868 \
  --java "$port_java" --forge-server "$port_server" --jar "$port_jar" \
  --launch build/port-runtime/cycle-096-absent-client/launch.json
```

The fixture grants Haste II and an Efficiency V diamond pickaxe so each test completes one native survival break even with the GLFW cursor left free. The QA client reads Ultimine's actual server-provided preview and changes its registered key state inside the isolated JVM. The dedicated server uses the uninstrumented release jar and verifies exact item counts from native entities.

## Results

- Forge 47.4.28 with FTB Ultimine 2001.1.8, FTB Library 2001.2.13 and Architectury 9.2.14+forge passed eight client checks and 14 server assertions. In both previews, the server selected exactly the origin and its matching east neighbor across the chunk boundary. Mining removed exactly two oak facades from the first row and kept the birch facade and all four energy conduits. On the second row, it removed exactly two energy conduits and kept both redstone conduits. The mixed bundle kept its redstone component.
- Core Forge passed six client checks and 14 server assertions. It removed the targeted facade or energy conduit and left its neighbor in place. Both profiles produced exactly the expected loot, and their reopened saves retained the expected layouts and tagged drop counts. The four server processes exited at 0 with empty registry and runtime error lists. See `build/port-runtime/cycle-096-enabled/ultimine-results.json` and `build/port-runtime/cycle-096-control/ultimine-results.json`.
- The independent window observer recorded zero foreground, onscreen or activation events over 1,785 enabled-profile samples and 1,782 control samples. All four captured frames report narrator off and all sound categories at zero. See the two `build/port-evidence/cycle-096-*-client/summary.json` reports and their before-mining captures.
- The fixture's first profile run exposed a hidden-window input constraint. Vanilla cancels progressive mining when the cursor is free. After switching to a one-hit survival setup, the native entity log confirmed Energy Conduit's shared item ID and NBT type. The final assertions check those exact values after mining and restart.
- The build and reobfuscated QA jar passed. Unit reports list 79 tests with zero failures or skips. All 483 native tests passed, including every one of the 161 discovered gameplay tests. See `build/cycle-096-build.log`.
- Production jar SHA-256 `0095b3b380a1fa32d5b5a43bb1787e5a8b106f1fd5d3455250be793480eb33d1`. The enabled report records the exact optional jar checksums.

## Remaining verification

Test the other Ultimine shapes, wrench ghosting during Ultimine, and Forge break-event cancellation.
