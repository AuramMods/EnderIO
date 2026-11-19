# Cycle 090 — Restore Athena connected textures on glass and facades

## Scope

Verify the optional pack with a blue clear-glass facade next to a second facade across a chunk edge and an ordinary clear-glass block. Repaint the middle facade red to split both joins, restore the original paint, reload the client resources and restart the server. Compare against core Forge and test the Athena pack with Embeddium.

## Changes

- Generate Athena's glass overrides through the same Forge blockstate provider used for capacitor banks. Athena 3.1.2 reads its loader and texture configuration from blockstate resources. The optional pack now supplies those resources for all 714 registered clear-glass and fused-quartz blocks.
- Preserve the original five texture slots, tint index, cutout layer and the regular blockstate model as the fallback in each generated override. The core resource pack still supplies the ordinary models and blockstates.
- Supply the cutout layer and tint index to the baked Athena glass model. Athena 3.1.2 returns the connected geometry on Forge without applying those two fields.
- Add a packaged TCP fixture that records native baked south-face quads and verifies painted color and transparent openings in the rendered frames across both paint changes and a client resource reload. Both energy conduits and their paint are asserted again after the server restarts.

## Commands

```sh
./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-090-build.log 2>&1
./gradlew :enderio:runData :enderio-modded-conduits:runData --console=plain > build/cycle-090-datagen.log 2>&1
./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-090-fixed-build.log 2>&1
./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-090-render-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.89-1.20.1+af6cced137.jar
port_qa=enderio/build/test-libraries/enderio-client-qa.jar
forge_server=build/port-runtime/cycle-047-legacy-discover
athena=build/port-runtime/cycle-065-athena-client/mods/athena-ctm-DULOQFj7.jar
embeddium=build/port-runtime/cycle-089-emb-client/mods/embeddium-UTbfe5d1.jar

for renderer in core athena embeddium; do
  profile="cycle-090-render-$renderer-client"
  path="build/port-runtime/$profile"
  mkdir "$path"
  cp -R build/port-runtime/cycle-089-fixed-client/libraries build/port-runtime/cycle-089-fixed-client/versions "$path/"
  set --
  if [ "$renderer" != core ]; then set -- "$@" --mod "$athena"; fi
  if [ "$renderer" = embeddium ]; then set -- "$@" --mod "$embeddium"; fi
  python3 scripts/porting/client/prepare_packaged.py --name "$profile" --java "$port_java" \
    --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" --qa-jar "$port_qa" \
    --assets "$HOME/.gradle/caches/neoformruntime/assets" "$@"
  if [ "$renderer" != core ]; then
    printf '%s\n' 'resourcePacks:["mod/enderio:resourcepacks/athena"]' >> "$path/options.txt"
  fi
done

python3 scripts/porting/packaged_facade_ctm.py --renderer athena --name cycle-090-render-athena --port 25836 \
  --java "$port_java" --forge-server "$forge_server" --jar "$port_jar" \
  --launch build/port-runtime/cycle-090-render-athena-client/launch.json
python3 scripts/porting/packaged_facade_ctm.py --renderer embeddium --name cycle-090-render-embeddium --port 25837 \
  --java "$port_java" --forge-server "$forge_server" --jar "$port_jar" \
  --launch build/port-runtime/cycle-090-render-embeddium-client/launch.json
python3 scripts/porting/packaged_facade_ctm.py --renderer core --name cycle-090-render-core --port 25838 \
  --java "$port_java" --forge-server "$forge_server" --jar "$port_jar" \
  --launch build/port-runtime/cycle-090-render-core-client/launch.json
```

## Results

- The initial jar activated the built-in Athena pack, but both facades and the ordinary glass each returned a single `enderio:block/clear_glass` quad. The expected connected-quad assertion failed. The same jar without Athena passed five server assertions and ten client checks. See `build/port-evidence/cycle-090-athena-client/summary.json` and `build/port-runtime/cycle-090-core/facade-ctm-results.json`.
- Datagen passed for both shipped mods. All 714 replacement JSON files preserve the previous texture slots, tint and layer and match the ordinary blockstate's model variants. The existing capacitor-bank resources are unchanged. See `build/cycle-090-datagen.log`.
- The first corrected pack produced the expected four connected quads, but the actual frames showed opaque black openings and untinted borders. This isolated the remaining failure to Forge's render layer and tint. See `build/port-evidence/cycle-090-fixed-athena-client/`.
- The final build passed 79 unit and 482 native tests. The native profiles ran 171 core, seven Mekanism, 117 registry, 160 gameplay and 27 addon tests. See `build/cycle-090-render-build.log`.
- On Forge with Athena, the outer faces used two connected and two isolated quads each, and the middle face used four connected quads. Changing the middle paint to red made all three faces use four isolated quads and preserved transparent openings. Reverting to blue restored both joins. In a 24-by-57-pixel crop on each join, all 614 pixels changed by repainting returned to their original values after restoring the paint and after resource reload. The profile passed five server assertions and 14 client checks. See `build/port-runtime/cycle-090-render-athena/facade-ctm-results.json` and `build/port-evidence/cycle-090-render-athena-client/`.
- Embeddium 0.3.31 with Athena passed the same five server assertions and 14 client checks. Repainting changed 617 pixels at the chunk-edge join and 618 at the ordinary-glass join in the same crops. Both crops were pixel-identical to the originals after restoring the blue paint and reloading resources. See `build/port-runtime/cycle-090-render-embeddium/facade-ctm-results.json` and `build/port-evidence/cycle-090-render-embeddium-client/`.
- The core profile passed five server assertions and 14 client checks using the plain tinted glass model throughout. All six final server processes exited at 0 with zero registry or runtime errors. The Athena, Embeddium with Athena and core clients recorded 1,832, 1,867 and 1,807 focus samples respectively, with zero activation, foreground or onscreen events. Every capture had narrator off and sound volumes at zero. See `build/port-runtime/cycle-090-render-core/facade-ctm-results.json` and `build/port-evidence/cycle-090-render-core-client/`.
- The production jar SHA-256 is `c3b67bed7238a74069e169d506b856e885855256cad306737d55453ae2d25d4e`.

## Remaining verification

Covers two colors and straight joins. Corner layouts, in-world fused-quartz examples, nonconstant biome tinting on other paints, active shader packs and dimension transitions still need runtime verification.
