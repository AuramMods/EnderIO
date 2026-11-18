# Cycle 056 — restore historical lights

The official 6.2.18-beta jar saved six light variants and an invisible light-node block. It wrote separate block entities for powered lights and their nodes. The previous port discarded both types of block entity, and the copied save failed its first light assertion. The source world passed the original lamp, node-owner and loose-item checks. See `build/port-runtime/cycle-056-baseline/`.

The port restores all seven block IDs, six item IDs and both block-entity IDs. Native Forge loading preserves the face, facing, tagged stacks and the original `Active` and `BlockPos` fields. The default-on and inverted variants follow the old redstone polarity. A wired light draws one Forge Energy per enabled tick through its mounting face. The historical wireless variants retain their redstone-only operation. The powered light and its surrounding nodes update block emission through the native Minecraft light engine. Node updates preserve obstacles and other owners' nodes. An unloaded owner is checked again once its chunk is available.

Datagen supplies all rotated states, item models, names and drops. The fixture geometry from the repository now uses the vanilla redstone lamp textures for lit and unlit models. The old packaged models had no texture binding. The six lights return their own items when broken and are retained for save compatibility.

## Reproduction

```sh
./gradlew :enderio:runData --console=plain
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_legacy=build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar
port_jar=enderio/build/libs/enderio-8.2.14.55-1.20.1+98c378bfe4.jar

python3 scripts/porting/packaged_legacy_migration.py --name cycle-056-lights-final --port 25689 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" --jar "$port_jar" --legacy-lights

mkdir build/port-runtime/cycle-056-client
cp -R build/port-runtime/cycle-051-client-verified/libraries \
  build/port-runtime/cycle-051-client-verified/versions build/port-runtime/cycle-056-client/
python3 scripts/porting/client/prepare_packaged.py --name cycle-056-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/client/run_smoke.py --launch build/port-runtime/cycle-056-client/launch.json \
  --scenario legacy-lights --timeout 230 --output build/port-evidence/cycle-056-hidden-lights-final
```

Use fresh run and evidence directories and set `port_jar` to the distributable for the checkout under test. The client installation copies only official libraries and version files from a disposable profile.

## Results

- Datagen and the full build passed. There were 78 ordinary JUnit tests and 420 native GameTests with no failures, errors or skips. The wired floor-light regression checks native light emission of 15 at its node, measures one Forge Energy consumed between consecutive lit ticks, verifies zero draw while off, and verifies that losing the power source clears the nodes and darkens the fixture. Logs are at `build/port-evidence/cycle-056-{datagen,build}-final.log`.
- The packaged headless Forge 47.4.28 test passed 87 assertions. It uses the original mod to generate the node data and saves six distinct tagged stacks. The port preserves orientation, item data and node ownership, including a node on the other side of a chunk boundary. The fixture also checks redstone shutoff, obstacle removal and a native block drop after restart. All three servers exit cleanly. Four first-load Forge registry diagnostics remain for other IDs, and zero other runtime errors were reported. See `build/port-runtime/cycle-056-lights-final/legacy-migration-results.json`.
- The guarded packaged client passed 11 checks. All 144 visible block states and six item models baked successfully. The integrated server synchronized changes to the lamp, nodes and block light. The first capture sequence sampled the synchronized state one mesh rebuild before it appeared onscreen. Both final 960×540 captures match the intended lit and dark states after the rebuilt mesh rendered. The native observer recorded 949 samples, zero activation or foreground events, zero visible-window samples and exit code zero. Both captures confirm narrator OFF and zero volume on all ten channels. The client log contains zero ERROR or FATAL lines. Evidence is in `build/port-evidence/cycle-056-hidden-lights-final/`.
- The distributable SHA-256 is `cea7338f10b7e7567cd233f604391f06381a77b440f5ae3d82bb52348212b8c1`. The separate client QA jar SHA-256 is `93fdc6535927313565aadfc479bdc4cf72bda51e47900890b707bc39683f02ec`.

Dedicated multiplayer rendering of these fixtures remains unverified. The primitive alloy smelter and its saved alloy jobs remain the next historical conversion gap. The older and current conductive-alloy recipes use different inputs and yields, so a migration needs a fixture with a real saved job and evidence for the exact output.
