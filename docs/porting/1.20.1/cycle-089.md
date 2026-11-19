# Cycle 089 — Cull the vanilla glass face next to a matching facade

## Scope

Join a red painted facade to a red vanilla stained-glass block across a chunk boundary. Verify both sides of the join while repainting the facade and while holding or putting away the Yeta Wrench. The glass block's section contains no facade.

## Changes

- Implement Forge's neighbor-face hook for conduit bundles. A visible facade supplies its paint to the ordinary block's own face-culling rule. The hook reads an existing block entity from the render region and leaves the ordinary face exposed while the facade is ghosted.
- When facade visibility changes, queue a rebuild for sections with facades and for neighboring sections touched by those facades. Collect the section positions once so several facades can share a rebuild.
- Extend the packaged fixture with an ordinary glass neighbor. Count the vertices produced by Forge's normal model tessellator and query Embeddium's own face-culling cache when installed. Capture the actual rendered join at each stage.

## Commands

```sh
./gradlew :enderio:reobfClientSmokeJar --console=plain
./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_qa=enderio/build/test-libraries/enderio-client-qa.jar
port_previous=enderio/build/libs/enderio-8.2.14.87-1.20.1+6267aec1ec.jar
port_jar=enderio/build/libs/enderio-8.2.14.88-1.20.1+0a2cec40a5.jar
port_forge=build/port-runtime/cycle-047-legacy-discover
embeddium_jar=build/port-runtime/cycle-088-emb-client/mods/embeddium-UTbfe5d1.jar

for profile in cycle-089-before-client cycle-089-fixed-client cycle-089-emb-client; do
  mkdir "build/port-runtime/$profile"
  cp -R build/port-runtime/cycle-088-fixed-client/libraries build/port-runtime/cycle-088-fixed-client/versions \
    "build/port-runtime/$profile/"
done
python3 scripts/porting/client/prepare_packaged.py --name cycle-089-before-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_previous" --qa-jar "$port_qa" \
  --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/client/prepare_packaged.py --name cycle-089-fixed-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" --qa-jar "$port_qa" \
  --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/client/prepare_packaged.py --name cycle-089-emb-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" --qa-jar "$port_qa" \
  --assets "$HOME/.gradle/caches/neoformruntime/assets" --mod "$embeddium_jar"

python3 scripts/porting/packaged_facade_seams.py --name cycle-089-before --port 25827 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_previous" \
  --launch build/port-runtime/cycle-089-before-client/launch.json --mixed
python3 scripts/porting/packaged_facade_seams.py --name cycle-089-fixed --port 25828 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" \
  --launch build/port-runtime/cycle-089-fixed-client/launch.json --mixed
python3 scripts/porting/packaged_facade_seams.py --name cycle-089-embeddium --port 25829 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" \
  --launch build/port-runtime/cycle-089-emb-client/launch.json --embeddium --mixed
python3 scripts/porting/packaged_facade_seams.py --name cycle-089-pair-regression --port 25830 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" \
  --launch build/port-runtime/cycle-089-fixed-client/launch.json
```

## Results

- The previous jar emitted 16 vertices from the matching facade and 20 from the adjacent vanilla block. The normal Forge face query still drew the ordinary glass face. The fixture failed on that exact query, with the different-paint controls passing. See `build/port-evidence/cycle-089-before-client/summary.json` and `matching-facades.png`.
- The build passed 79 unit tests and 482 native tests and built the reobfuscated client QA jar. The native breakdown was 171 core, seven Mekanism, 117 registry, 160 gameplay and 27 addon. See `build/cycle-089-reproduction-build.log` and `build/cycle-089-build.log`.
- Fixed Forge emitted 16 vertices per side while the paints matched, 20 per side after the facade changed to blue, and 16 again after returning it to red. With the wrench selected the facade stayed at 16 and the vanilla glass returned to 20. Putting away the wrench restored the 16-vertex join. The native Forge query reported the same face decisions. The run passed six server assertions and 20 client checks, including restart. See `build/port-runtime/cycle-089-fixed/facade-seams-results.json`.
- The normal view's shared-plane sample at x 492–534 and y 263–306 averaged RGB 92.66, 58.11, 58.11 on the previous jar and 108.23, 81.45, 81.45 on fixed Forge. The normal view after putting away the wrench matched the fixed normal sample exactly. The five fixed Forge captures used zero volumes and narrator off. The native focus observer logged no activation, foreground or onscreen events in 2,018 samples. See `build/port-evidence/cycle-089-fixed-client/`.
- Embeddium 0.3.31+mc1.20.1 passed the same six server assertions and 25 client checks. Its own culling cache agreed on the five ordinary face decisions. The five rendered captures showed the same paint transitions, and the sampled join matched pixel for pixel after putting away the wrench. The native focus observer logged no activation, foreground or onscreen events in 2,022 samples. Each fixed profile's two servers exited at 0 with zero runtime or registry errors. See `build/port-runtime/cycle-089-embeddium/facade-seams-results.json` and `build/port-evidence/cycle-089-embeddium-client/`.
- The two-facade regression passed six server assertions and 13 client checks on the same jar. Both matching faces remained culled in the wrench view and both painted conduits reopened correctly. The background observer reported no activation, foreground or onscreen events in 1,639 samples. See `build/port-runtime/cycle-089-pair-regression/facade-seams-results.json` and `build/port-evidence/cycle-089-pair-regression-client/summary.json`.
- The production jar SHA-256 is `310d17516c6735540c2ee72017c9bf94519a1e5686e26a485ae04c5a577e165f`.

## Remaining verification

Connected-texture paint, tinting with nonconstant biome colors, active shader packs and facade rendering across dimension transitions still need coverage.
