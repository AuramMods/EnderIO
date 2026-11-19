# Cycle 088 — Cull shared faces between glass conduit facades

## Scope

Compare matching and different stained-glass facades across a chunk boundary. Keep a matching vanilla glass neighbor as a control for the facade renderer. Repaint one bundle while the client is connected to verify the change in face visibility.

## Changes

- Present adjoining bundle paint states to Forge's facade tessellator. The renderer retains the captured states for facades in its section and reads boundary appearances through the renderer's supplied region.
- Limit the projected block states to facade tessellation. The native render region continues to supply light, shade, fluids, biome tint and model data. Each model still resolves its model data in the original region.
- Verify the vertex counts produced by the native tessellator for matching paint, different paint, an ordinary matching glass neighbor, live repainting and the wrench view. Capture the actual loaded world and recheck the painted bundles after restarting Forge.

## Commands

```sh
./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain
./gradlew :enderio:reobfClientSmokeJar --console=plain

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_qa=enderio/build/test-libraries/enderio-client-qa.jar
port_previous=enderio/build/libs/enderio-8.2.14.86-1.20.1+27f3980533.jar
port_jar=enderio/build/libs/enderio-8.2.14.87-1.20.1+6267aec1ec.jar
port_forge=build/port-runtime/cycle-047-legacy-discover
embeddium_jar=build/port-runtime/cycle-087-emb-client/mods/embeddium-UTbfe5d1.jar

for profile in cycle-088-before-client cycle-088-fixed-client cycle-088-emb-client; do
  mkdir "build/port-runtime/$profile"
  cp -R build/port-runtime/cycle-087-client/libraries build/port-runtime/cycle-087-client/versions \
    "build/port-runtime/$profile/"
done

python3 scripts/porting/client/prepare_packaged.py --name cycle-088-before-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_previous" --qa-jar "$port_qa" \
  --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/client/prepare_packaged.py --name cycle-088-fixed-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" --qa-jar "$port_qa" \
  --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/client/prepare_packaged.py --name cycle-088-emb-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" --qa-jar "$port_qa" \
  --assets "$HOME/.gradle/caches/neoformruntime/assets" --mod "$embeddium_jar"

python3 scripts/porting/packaged_facade_seams.py --name cycle-088-repro --port 25823 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_previous" \
  --launch build/port-runtime/cycle-088-before-client/launch.json
python3 scripts/porting/packaged_facade_seams.py --name cycle-088-fixed --port 25824 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" \
  --launch build/port-runtime/cycle-088-fixed-client/launch.json
python3 scripts/porting/packaged_facade_seams.py --name cycle-088-embeddium --port 25825 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" \
  --launch build/port-runtime/cycle-088-emb-client/launch.json --embeddium
python3 scripts/porting/packaged_facades.py --name cycle-088-stained-regression --port 25826 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" \
  --launch build/port-runtime/cycle-088-fixed-client/launch.json --stained-glass
```

## Results

- The build and reobfuscated client QA jar passed. The build ran 79 unit and 482 native tests. Native counts were 171 core, seven Mekanism, 117 registry, 160 gameplay and 27 addon. See `build/cycle-088-build.log` and `build/cycle-088-client-final.log`.
- With the previous jar, both matching facades emitted 20 vertices each, including both shared faces above the stone floor. Different-color facades also emitted 20 vertices each. The facade next to matching vanilla glass was the control and emitted 16. The matching-facade check failed and the capture shows the dark overlapping plane inside the join. See `build/port-evidence/cycle-088-repro-client/summary.json` and `matching-facades.png`.
- Standard Forge and Embeddium 0.3.31+mc1.20.1 each passed six packaged server assertions and 13 guarded client checks. On each renderer, matching glass emitted 16 vertices per facade and different glass emitted 20. Repainting the east facade added one quad to both meshes, and restoring its paint removed both quads again. Matching ghosted facades still emitted 16 vertices per facade. The facade beside matching vanilla glass kept its original 16 vertices. Both saved energy conduits and covers reopened exactly. See `build/port-runtime/cycle-088-fixed/facade-seams-results.json` and `build/port-runtime/cycle-088-embeddium/facade-seams-results.json`.
- The four 960×540 captures in each profile showed the matching, repainted, restored and wrench views from the same camera. In the central part of the join, the old frame averaged RGB 89.92, 55.38, 55.38. The fixed Forge frame averaged 103.63, 76.88, 76.88 and Embeddium averaged 103.42, 76.67, 76.67 over the same pixels at x 492–534 and y 263–306. Both fixed clients had all ten volumes at zero and narrator off. The native observers recorded zero foreground, onscreen or activation events in 1,636 standard Forge and 1,693 Embeddium samples. See `build/port-evidence/cycle-088-fixed-client/` and `build/port-evidence/cycle-088-embeddium-client/`.
- The four-variant stained-glass regression passed another 19 server assertions and 38 guarded client checks on the same jar, including painting, survival placement, resource reload and reopen. The observer recorded zero foreground, onscreen or activation events in 1,932 samples. See `build/port-runtime/cycle-088-stained-regression/facade-results.json` and `build/port-evidence/cycle-088-stained-regression-client/summary.json`.
- The packaged production jar SHA-256 is `e560d609e68bb50ea1ae22bb447ef19102a4052f11680a920507c0387bfb18aa`.

## Remaining verification

The test of a vanilla glass neighbor covers the face drawn by the conduit facade. The vanilla glass block's own face next to a facade, connected-texture paint and active shader packs still need coverage.
