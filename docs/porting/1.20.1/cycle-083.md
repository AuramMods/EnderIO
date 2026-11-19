# Cycle 083 — Painting Machine inputs and delivered paint

## Scope

Verify that saved Painting Machine jobs use the live template and reusable paint block. Exercise fully paid output backpressure, an invalid saved palette, a replaced palette after restart, and production and placement of a painted fence.

## Changes

- Use live Painting Machine input slots for recipe matching. Apply the same full-occlusion and non-painted-block checks to new insertions and saved inputs. Check crafting recipe matches before generating any output.
- Paint the delivered item with the palette installed when output space becomes available. Keep already paid progress when a valid palette is replaced for the same recipe. If a palette is removed, require a valid recipe to start again.
- Consume the template after insertion and award painting advancements after delivery. A blocked production job leaves the template and the reusable palette installed.
- Add three native regressions and a guarded packaged-client scenario for advancement, menu transfer, survival placement and the painted fence renderer.

## Commands

```sh
./gradlew :enderio:verifyGameplayTests --console=plain
./gradlew build --continue --console=plain
./gradlew :enderio:reobfClientSmokeJar --console=plain
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.82-1.20.1+39676eb376.jar
mkdir build/port-runtime/cycle-083-client
cp -R build/port-runtime/cycle-081-client-verified/libraries build/port-runtime/cycle-081-client-verified/versions \
  build/port-runtime/cycle-083-client/
python3 scripts/porting/client/prepare_packaged.py --name cycle-083-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_painting_machine.py --name cycle-083-paint --port 25806 \
  --java "$port_java" --forge-server build/port-runtime/cycle-047-legacy-discover --jar "$port_jar" \
  --launch build/port-runtime/cycle-083-client/launch.json
python3 scripts/porting/packaged_soul_binder.py --name cycle-083-binder-regression --port 25807 \
  --java "$port_java" --forge-server build/port-runtime/cycle-047-legacy-discover --jar "$port_jar"
```

## Results

- The first Forge reproduction crashed while ticking a placed Painting Machine with a stick in the saved paint slot. The isolated regression reproduced the exception on a detached machine, alongside delivery after removing the palette and delivery of diamond paint after replacing it with gold in a paid saved job. See `build/cycle-083-paint-reproduction.log` and `build/cycle-083-isolated-reproduction-results.xml`.
- The full build passed 79 unit tests and 476 required Forge GameTests with zero failures or skips. The native counts were 171 core, seven Mekanism, 117 registry, 154 gameplay and 27 addon. The separate client QA jar built successfully. See `build/cycle-083-build.log` and `build/cycle-083-client-build.log`.
- The packaged Painting Machine fixture passed 11 server and seven guarded client checks. Behind full output, both jobs spent exactly 2,400 FE and kept both templates. A nearby player earned the diamond-paint advancement when the diamond fence reached output. The regular menu transferred one fence, survival placement consumed it, and the block entity, client model, native loot and restarted server all retained its paint. A second fully paid job accepted a gold palette after restart and used no more FE. The station with an invalid saved stick retained its charge and then completed one fence when fitted with stone. See `build/port-runtime/cycle-083-paint/painting-machine-results.json`.
- The 960×540 menu and painted fence captures used all ten volumes at zero, narrator off and the cursor uncaptured. The native observer took 1,361 samples with zero activation events, foreground observations or onscreen windows. See `build/port-evidence/cycle-083-paint-client/summary.json`, `painting-machine.png` and `painted-diamond-fence.png` in the same directory.
- The packaged Soul Binder regression passed seven assertions for input automation, paid progress, XP conservation and hopper output after restart. All four dedicated server instances in both profiles exited at 0 with no runtime or registry errors. The matched Java 17 production jar SHA-256 is `ebf73582fa86d33bbc2643fea6358095d54ff310e3a760a51afd3ac01ab219ef`. See `build/port-runtime/cycle-083-binder-regression/soul-binder-results.json`.

## Remaining verification

Historical Painting Machine and painted block save migration, the other painted block families and the two placement orders for mixed painted slabs remain open.
