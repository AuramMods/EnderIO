# Cycle 086 — Conduit facade painting, placement and rendering

## Scope

Exercise the basic, hardened, transparent and transparent hardened facades from the actual Painting Machine to hopper recovery, survival placement, client rendering and disk reload. Use distinct paint blocks across two chunk sections, with a real energy conduit behind each facade.

## Changes

- Add a packaged Forge fixture that pays for all four shipped recipes and verifies one exact painted output per template. The reusable paint remains in each machine. Vanilla hoppers extract the results.
- Use a guarded client to install all four recovered items over existing conduits. Check the painted item models, exact capability variants and synchronized chunk model data. Hold and put away the Yeta Wrench, measuring the expected paint in each facade's projected face on the rendered frames.
- Restart the dedicated server and call native loot for each bundle. Assert that each bundle retains its conduit and facade and produces one conduit item and one painted facade item.

## Commands

```sh
./gradlew build --continue --console=plain
./gradlew :enderio:reobfClientSmokeJar --console=plain

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.85-1.20.1+3451f3cdb1.jar
port_forge=build/port-runtime/cycle-047-legacy-discover
embeddium_jar="$HOME/.gradle/caches/modules-2/files-2.1/maven.modrinth/embeddium/UTbfe5d1/bb2fa8f3e493af16af9160d049f96c614a1faf2f/embeddium-UTbfe5d1.jar"

for profile in cycle-086-client cycle-086-emb-client; do
  mkdir "build/port-runtime/$profile"
  cp -R build/port-runtime/cycle-085-client/libraries build/port-runtime/cycle-085-client/versions \
    "build/port-runtime/$profile/"
done

python3 scripts/porting/client/prepare_packaged.py --name cycle-086-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/client/prepare_packaged.py --name cycle-086-emb-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets" \
  --mod "$embeddium_jar"

python3 scripts/porting/packaged_facades.py --name cycle-086-forge-final --port 25814 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" \
  --launch build/port-runtime/cycle-086-client/launch.json
python3 scripts/porting/packaged_facades.py --name cycle-086-embeddium --port 25815 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" \
  --launch build/port-runtime/cycle-086-emb-client/launch.json --embeddium
```

## Results

- All 79 unit tests and 482 required native tests passed with zero failures or skips. Native counts were 171 core, seven Mekanism, 117 registry, 160 gameplay and 27 addon. The separate client QA jar built successfully. See `build/cycle-086-build.log` and `build/cycle-086-client-build-final.log`.
- Standard packaged Forge passed 18 server assertions and 24 guarded client checks. Each variant spent exactly 2,400 FE, retained its paint supply, yielded one facade, installed in survival, and survived restart with its energy conduit. Native loot returned exactly one of each item per bundle. Both dedicated processes exited at 0 with no runtime or registry errors. See `build/port-runtime/cycle-086-forge-final/facade-results.json`.
- The Forge frames show gold, diamond, redstone and emerald faces. Holding the wrench exposes the energy conduit inside each faint cover. The four strongest color pixel counts were 1,730, 1,214, 1,849 and 2,578 in the visible frame, then 0, 0, 7 and 0 with the wrench. The weaker color sample contains each ghost, and putting the wrench away restores all four initial counts. All three 960×540 frames used zero volume and narrator off. The native observer took 1,654 samples with zero foreground, onscreen or activation events. See `build/port-evidence/cycle-086-forge-final-client/summary.json` and the three `facades-*.png` captures alongside it.
- The client with Embeddium 0.3.31+mc1.20.1 passed the same 18 server assertions and 24 client checks against the same production jar. Both servers exited at 0 with no runtime or registry errors. The visible color counts were 1,729, 1,214, 1,857 and 2,581. Holding the wrench reduced them to 0, 0, 7 and 0, and putting it away restored the initial counts. The three captures confirm zero sound and narrator off. The native observer took 1,731 samples with zero foreground, onscreen or activation events. See `build/port-runtime/cycle-086-embeddium/facade-results.json` and `build/port-evidence/cycle-086-embeddium-client/summary.json`.
- The production jar SHA-256 is `4f59a2538c93c6ab895c15dfa158f280b7798ec99e9825ce9bfd639d2efe9ee4`.
- The Embeddium jar SHA-256 is `eed3d1325f2acc2fd4e69bb495e5ccb91d962126ac5330f0582ebc2a3daf47fb`.

## Remaining verification

Translucent paint sources, tinted and connected-texture models, active shader packs, placement into an empty space, adjacent-face culling, resource reload and dimension transitions remain open. The baseline hardened variants carry a type flag and tooltip. The baseline source does not use the hardened flag to change bundle blast resistance.
