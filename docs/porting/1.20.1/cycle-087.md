# Cycle 087 — Stained-glass conduit facades

## Scope

Paint all four conduit facade types with red or blue stained glass. Exercise placement without a conduit, insertion of the conduit afterward, and rendering before and after a client resource reload.

## Changes

- Extend the packaged facade fixture with a stained-glass scenario. Each pair uses the same paint, with one variant retaining conduit geometry in normal view and the other using the conduit-hiding flag.
- Place the red transparent facade into empty space by targeting the floor with the native player ray. Then target the standalone facade and insert an energy conduit through the normal survival interaction.
- Check the four translucent covers in the framebuffer and the baked conduit geometry with the wrench held and put away. Reload the actual client resources and verify the same four facade models and meshes.
- Retain the server checks for exact Painting Machine expenditure, hopper extraction, paint, loot and server restart.
- Report Embeddium's exact Oculus mixin support notice separately for the packaged profile with shaders disabled. Keep client, focus and unexpected log failures in the pass criteria.

## Commands

```sh
./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain
./gradlew :enderio:reobfClientSmokeJar --console=plain

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.86-1.20.1+27f3980533.jar
port_forge=build/port-runtime/cycle-047-legacy-discover
embeddium_jar=build/port-runtime/cycle-086-emb-client/mods/embeddium-UTbfe5d1.jar
oculus_jar="$HOME/.gradle/caches/modules-2/files-2.1/maven.modrinth/oculus/iQ1SwGc3/984f774e71790deaec674c7587bd24e0711871b2/oculus-iQ1SwGc3.jar"

for suffix in client emb-client; do
  mkdir "build/port-runtime/cycle-087-$suffix"
  cp -R "build/port-runtime/cycle-086-$suffix/libraries" "build/port-runtime/cycle-086-$suffix/versions" \
    "build/port-runtime/cycle-087-$suffix/"
done
python3 scripts/porting/client/prepare_packaged.py --name cycle-087-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/client/prepare_packaged.py --name cycle-087-emb-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets" \
  --mod "$embeddium_jar"
mkdir -p build/port-runtime/cycle-087-oculus-client/config
cp -R build/port-runtime/cycle-087-emb-client/libraries build/port-runtime/cycle-087-emb-client/versions \
  build/port-runtime/cycle-087-oculus-client/
python3 scripts/porting/client/prepare_packaged.py --name cycle-087-oculus-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets" \
  --mod "$embeddium_jar" --mod "$oculus_jar"
printf '%s\n' 'enableShaders=false' > build/port-runtime/cycle-087-oculus-client/config/oculus.properties

python3 scripts/porting/packaged_facades.py --name cycle-087-oculus-final --port 25819 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" \
  --launch build/port-runtime/cycle-087-oculus-client/launch.json --oculus
python3 scripts/porting/packaged_facades.py --name cycle-087-forge-final --port 25820 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" \
  --launch build/port-runtime/cycle-087-client/launch.json --stained-glass
python3 scripts/porting/packaged_facades.py --name cycle-087-embeddium-final --port 25821 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" \
  --launch build/port-runtime/cycle-087-emb-client/launch.json --embeddium --stained-glass
```

## Results

- The build passed 79 unit and 482 native tests with zero failures or skips. The native counts were 171 core, seven Mekanism, 117 registry, 160 gameplay and 27 addon. The reobfuscated client QA jar built successfully. See `build/cycle-087-build.log` and `build/cycle-087-client-final.log`.
- The first Oculus run rendered the whole fixture, including after resource reload, and its focus observation passed. The wrapper reported a failed run because it counted Embeddium's mixin support notice as a runtime error. The log includes confirmation that Oculus had shaders disabled. See `build/port-evidence/cycle-087-oculus-client/summary.json`.
- Standard Forge and Embeddium 0.3.31+mc1.20.1 each passed 19 server assertions and 38 guarded client checks. All four Painting Machines spent exactly 2,400 FE and retained their stained glass supplies. Hoppers extracted one result each. Native placement made a facade-only bundle with red glass and used one energy conduit to populate it. After a restart all four bundles retained the exact glass and one conduit, and native loot recovered one of each item. All four dedicated server processes exited at 0 with zero runtime or registry errors. See `build/port-runtime/cycle-087-forge-final/facade-results.json` and `build/port-runtime/cycle-087-embeddium-final/facade-results.json`.
- Both clients rendered red and blue glass. The basic and hardened variants showed the conduit through the glass. The transparent variants initially hid their conduits, which appeared while the wrench was held and disappeared again when it was put away. All four facades rendered again after native resource reload, with the sampled color counts matching the original view in both profiles. The 960×540 frames confirm all ten volume controls at zero and narrator off. Native observers recorded zero activation, onscreen and foreground events across 1,920 standard Forge and 1,971 Embeddium samples. See `build/port-evidence/cycle-087-forge-final-client/` and `build/port-evidence/cycle-087-embeddium-final-client/`.
- The final Oculus 1.8.0 run with shaders disabled passed 19 server assertions and 38 guarded client checks on Embeddium. The client recorded the expected Oculus installation, matching pre-reload and post-reload color counts, and one separately reported Embeddium support diagnostic. The unexpected error list was empty. Both dedicated processes exited at 0 with zero runtime or registry errors. The client was muted with narrator off and recorded zero activation, onscreen and foreground events in 1,992 samples. See `build/port-runtime/cycle-087-oculus-final/facade-results.json` and `build/port-evidence/cycle-087-oculus-final-client/summary.json`.
- The packaged production jar SHA-256 is `c4170612df66e2cc9ef46a7cc4ba0512ce6a87ffafa23264a0be92f2f346a697`.
- The Oculus jar SHA-256 is `0945df0cba0f62b3901dd80c3268e5311b770ece78c78037a45db12ac0425fef`.

## Remaining verification

Connected-texture paints, adjacent-face culling, dimension transitions and active shader packs remain open.
