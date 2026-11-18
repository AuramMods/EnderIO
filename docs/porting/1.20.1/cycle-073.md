# Cycle 073 — Entity obelisks

## Scope

Exercise the Inhibitor, Aversion, Relocator and Attractor on Forge. Verify native spawn and teleport hooks, filter behavior, range changes in the actual multiplayer screens, and the machine lifecycle after chunk unload and server restart.

## Changes

- Require the installed capacitor for active obelisks and compute their per-tick upkeep without dividing by a zero maximum range.
- Check both ends of an Inhibitor teleport. A destination chunk with an unrelated obelisk can no longer hide a source-side field. Console teleports and spread-player commands keep their exception.
- Derive redstone blocking from the loaded mode and cached input signal. A native NBT change to the mode takes effect even when the surrounding blocks are unchanged.
- Select a loaded Relocator destination with the entity's native spawn support and collision clearance. Recheck destinations amended by a Forge teleport listener. A sealed destination leaves the native spawn in place.
- Add six Forge gameplay scenarios and a separate packaged client probe. They exercise the Forge spawn hook and real insertion, actual mob navigation, both teleport endpoints, capacitor and upkeep, and the visible range controls on all four screens.

## Commands

```sh
./gradlew :enderio:verifyGameplayTests --console=plain
./gradlew :enderio:verifyGameplayTests :enderio:assemble --console=plain
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.72-1.20.1+1fa2e430e0.jar
port_forge=build/port-runtime/cycle-047-legacy-discover
mkdir build/port-runtime/cycle-073-client-final
cp -R build/port-runtime/cycle-073-client/libraries build/port-runtime/cycle-073-client/versions \
  build/port-runtime/cycle-073-client-final/
python3 scripts/porting/client/prepare_packaged.py --name cycle-073-client-final --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_entity_obelisks.py --name cycle-073-final --port 25780 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" \
  --launch build/port-runtime/cycle-073-client-final/launch.json
```

## Results

- The original native tests reproduced capacitor-free inhibitor activation, an unprotected source when an unrelated destination candidate existed, and relocation into a sealed area. Early movement fixtures also needed the standable floor and initial grounded state used by native mob navigation. See `build/cycle-073-reproduction.log` and `build/cycle-073-fixed-gameplay.log`.
- The first packaged scene supplied a plain soul list. The game's filter codec uses indexed entries. The corrected scene uses the exact item payload and preserves it through chunk reload. See `build/port-runtime/cycle-073-packaged/smoke-fresh.log`.
- Before the redstone fix, the server consumed both pearls while the saved player position stayed at the origin. A native regression also reproduced stale redstone blocking after a block-data update without a neighbor change. After fixing the mode, the first timed attempt exposed a separate test issue. The saved hotbar still held the second pearl even though the client predicted a successful use. The fixture now waits 20 server ticks after the first impact and checks the server's item-use statistic for each shot. See `build/port-runtime/cycle-073-ordered/`, `build/port-runtime/cycle-073-redstone/` and their `smoke-fresh.log` files.
- The first attraction threshold was 2.5 blocks. Two isolated saves put the pig at `1715.0218849515943, 80.0, 8.500005872270481`, 2.522 blocks from the obelisk and 1.478 blocks closer than where it spawned. The final fixture checks for at least 1.25 blocks of movement toward the machine in a straight corridor. See `build/port-runtime/cycle-073-timed/` and `build/port-runtime/cycle-073-live/`.
- The final Gradle build passed 78 ordinary unit tests and 444 required Forge GameTests with zero failures or skips. The native profiles ran 171 core, seven Mekanism, 115 registry, 124 gameplay and 27 addon tests. See `build/cycle-073-redstone-build.log` and the module test XML.
- The packaged Forge 47.4.28 run passed 24 server assertions and 24 client checks over TCP in survival. All four menus synchronized the range and visibility controls. All four obelisks retained their inputs and settings through actual chunk eviction and server restart. After chunk reload, the Attractor drew its pig into range. The server counted both real pearl throws, blocked the first, and let the second teleport the player after the Inhibitor's mode was updated. The client received the new position and empty hotbar. Both server instances saved and exited with code 0 and reported zero runtime or registry errors. See `build/port-runtime/cycle-073-final/entity-obelisk-results.json`.
- The client and server used the same production jar, SHA-256 `1abc0beb4db5fde21ca89e959193bbb2331c738107939ebde7e41686421a9d95`. All top-level class files target Java 17 (major 61). The production jar contains no client QA classes.
- The native window observer recorded 1,941 samples, zero activations, zero foreground samples and zero on-screen samples. The four captured menus show hidden, windowed rendering, all sound volumes at zero and narrator off. See `build/port-evidence/cycle-073-final-client/summary.json`, `focus.json` and the four PNGs in that directory.

## Remaining verification

Run engine-scheduled natural spawning on a packaged server with Aversion and Relocator. A copied official 6.2.18-beta save already reproduces a migration gap. Aversion's saved range of 4 expands to 16, and the filter's item ID migrates while its five legacy entries and both modes remain in unread fields. Restore the range and the full filter, then exercise the result in the port. See `build/cycle-073-legacy-inspection.log`. Other mods' spawn handlers and carried blocks remain in the wider port audit.
