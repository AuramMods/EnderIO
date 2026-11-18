# Cycle 075 — Scheduled natural spawning

## Scope

Verify Aversion and Relocator when the native Minecraft scheduler selects and spawns mobs on a packaged Forge server. Verify that the Inhibitor can block a Relocator landing and that the Relocator resumes after actual chunk eviction.

## Fixture

- A real survival TCP player waits at a glass station. A dark stone chamber at the bottom of the flat world provides an eligible spawn area. A separate raised, lit pad is within Relocator range. The survival player and world spawn are far enough from the chamber for Minecraft to spawn monsters there.
- The runner uses native commands to build the scene, toggle redstone modes and clear old mobs between samples. It enables `doMobSpawning` during each sample so Minecraft's scheduler creates every observed mob.
- A read-only test jar records the Forge `NATURAL` finalization event, the teleport event when present and the native insertion event for the same mob UUID. The test records floor support and light at the landing location. The observer is separate from the distributable Ender IO jar.
- Each of the six phases requires at least three zombie and three skeleton samples. Skeletons are the nonmatching control. All observed events must agree with the phase, including finalization, insertion and the destination when a teleport occurs. Native entity selectors independently check that a relocated zombie exists and that a canceled zombie does not.

## Commands

```sh
./gradlew :enderio:assemble :enderio:reobfClientSmokeJar --console=plain
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain
./gradlew :enderio:reobfClientSmokeJar --console=plain
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_jar=enderio/build/libs/enderio-8.2.14.74-1.20.1+5e676f698c.jar
port_qa=enderio/build/test-libraries/enderio-client-qa.jar
mkdir build/port-runtime/cycle-075-client-final
cp -R build/port-runtime/cycle-075-client/libraries build/port-runtime/cycle-075-client/versions \
  build/port-runtime/cycle-075-client-final/
python3 scripts/porting/client/prepare_packaged.py --name cycle-075-client-final --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" --qa-jar "$port_qa" \
  --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_natural_spawns.py --name cycle-075-final --port 25784 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --qa-jar "$port_qa" \
  --launch build/port-runtime/cycle-075-client-final/launch.json
```

## Results

- `build/cycle-075-full-build.log` records 79 unit tests and 448 required Forge GameTests passing. The native suites ran 171 core, seven Mekanism, 117 registry, 126 gameplay and 27 addon tests. The final QA jar rebuild is recorded in `build/cycle-075-final-qa-build.log`.
- The initial packaged run passed all six phases and the supporting block and chunk assertions. It observed five unmodified zombies in the control, three Aversion rejections, three Relocator landings, ten Inhibitor-blocked relocations, three intact spawns with the landing sealed and three more live relocations after chunk reload. See `build/port-runtime/cycle-075-first/natural-spawn-results.json`.
- The final packaged run passed 16 server assertions and observed 66 scheduled spawn attempts. All 38 skeletons spawned at their original positions. Of the 28 zombies, five spawned normally in the control, four were denied by Aversion, six landed on the Relocator pad, three stayed in the chamber when Inhibitor blocked the destination, four stayed in the chamber with the pad sealed, and six landed on the pad after real chunk eviction and reload. All 12 landings had a solid floor and block light 12. Native selectors confirmed the absent Aversion target and live relocated zombies. The server saved and exited with code 0 with zero runtime or registry errors. See `build/port-runtime/cycle-075-final/natural-spawn-results.json` and `smoke-natural.log`.
- The production jar used by both runtimes has SHA-256 `9ea8a69346f38c3c245fce467866564f084f502e7b37c6b30f622a6bac77020e`. It targets Java 17 and contains zero QA classes. The native client observer recorded 2,860 samples, with zero activations, foreground samples or visible windows. Fullscreen and cursor capture were off, all volume categories were zero, and narrator was off. See `build/port-evidence/cycle-075-final-client/focus.json`, `summary.json` and `natural-spawn-station.png`.

## Remaining verification

Exercise Attractor's flying and boss behaviors on the packaged game and audit the remaining obelisk integrations and portable-block coverage. The scheduled spawn scene covers ordinary zombies and skeletons in the Overworld. Other mods' spawn listeners, different spawn placement types and separate dimensions remain outside this verification.
