# Cycle 068 — Powered Spawner survival progression and 6.2 saves

## Scope

Exercise the Broken Spawner drop, the survival crafting recipe and both modes of the Powered Spawner. Restore the bound mob, progress and portable machine state written by Forge Ender IO 6.2.18-beta.

## Changes

- Share the Forge 6 entity payload reader between Soul Vials and both spawner items. Move the old powered spawner's soul, FE, sided I/O setting and redstone mode into typed item data. Preserve the old capacitor inventory for native block placement.
- Restore a placed spawner's old entity payload and resume a saved task from its recorded FE using the bound mob's current recipe. Apply the bound soul and redstone cache when a machine is placed from typed item data.
- Add a disposable packaged-world fixture that runs the official Forge 6.2 jar, copies only its world into a second server using the port and saves and restarts that world. The guarded client exercises the native crafting table, shift-click, both block placements and the Powered Spawner menu over loopback TCP.

## Commands

```sh
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain
./gradlew :enderio:reobfClientSmokeJar --console=plain
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_legacy=build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar
port_jar=enderio/build/libs/enderio-8.2.14.67-1.20.1+83c9729bd5.jar
python3 scripts/porting/packaged_spawners.py --name cycle-068-baseline-verified --port 25736 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" \
  --jar enderio/build/libs/enderio-8.2.14.66-1.20.1+ef1012dcaf.jar
python3 scripts/porting/packaged_spawners.py --name cycle-068-legacy-fixed --port 25737 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" --jar "$port_jar"
mkdir build/port-runtime/cycle-068-client
cp -R build/port-runtime/cycle-067-client/libraries build/port-runtime/cycle-067-client/versions \
  build/port-runtime/cycle-068-client/
python3 scripts/porting/client/prepare_packaged.py --name cycle-068-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_spawners.py --name cycle-068-live-final --port 25741 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" --jar "$port_jar" \
  --launch build/port-runtime/cycle-068-client/launch.json
python3 scripts/porting/packaged_soul_vials.py --name cycle-068-vial-regression --port 25739 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" --jar "$port_jar"
```

## Results

- The previous production jar reproduced the failure using a genuine 6.2 world. The port logged an invalid stored-soul diagnostic and failed to recover the partial job. The old jar's actual loot tables yielded a cow-bound Broken Spawner and a cow-bound Powered Spawner with 877 FE, an octadic capacitor, north disabled and redstone disabled. See `build/port-runtime/cycle-068-baseline-verified/spawner-results.json`.
- The fixed headless profile passed nine assertions. It loaded the placed cow spawner with 4,321 of 10,000 FE paid, preserved its octadic capacitor and south input, recovered both saved items, and spawned cows after a restart using the remaining 5,679 FE. All three servers exited zero. The first conversion reported the previously observed historical item and enchantment mappings. No server reported a runtime error. See `build/port-runtime/cycle-068-legacy-fixed/spawner-results.json`.
- The initial and final packaged TCP runs each passed 18 server assertions and 11 guarded client checks. The port's global loot modifier produced a cow-bound Broken Spawner from actual vanilla spawner loot. A survival client crafted with the recovered 6.2 Broken Spawner, consumed all five ingredient types and placed the resulting cow-bound machine. The player also replanted the legacy Powered Spawner with all 877 FE, its capacitor, its disabled north face and its redstone mode intact. Switching the native menu to Capture Mobs and giving the machine the remaining 9,123 FE filled one vial without spawning a cow. Both placements and the filled output survived the next restart. The final run's four servers and client exited zero. Only the conversion logged the previously observed Forge registry diagnostics. See `build/port-runtime/cycle-068-live-final/spawner-results.json`.
- The crafting, spawn-mode and capture-mode frames show the item output and native menus. All three captures recorded narrator off, all ten sound volumes at zero, fullscreen off and an uncaptured cursor. The final native watcher recorded 1,906 samples with zero activation, foreground or visible-window events. See `build/port-evidence/cycle-068-live-final-client/`.
- A repeat timed out before Forge login completed. The server logged dispatch of the login messages, and the client logged no incoming Forge login packets. This matches the incomplete handshake observed in cycle 046. The packaged runner now observes the client's connection result directly and retains its report when login fails. See `build/port-evidence/cycle-068-live-verified-client/client-debug.log` and `build/port-runtime/cycle-068-live-verified/port/logs/debug.log`.
- The packaged vial regression passed eight assertions after sharing the legacy reader. The migrated vial retains its named cow, health and unrelated tags. A dispenser releases the original cow and returns the empty vial after restart. See `build/port-runtime/cycle-068-vial-regression/soul-vial-results.json`.
- The full build passed 78 unit tests and 430 Forge GameTests with zero failures, errors or skips. The updated guarded client probe reobfuscated successfully. See `build/cycle-068-build.log`, `build/cycle-068-client-final.log` and the modules' `build/test-results/test/` XML.

| Artifact | SHA-256 |
| --- | --- |
| Baseline production jar | `098f7a999eaca2c784e98b295fe9b84d28099a7bca7e60eb8a468990e42c95aa` |
| Fixed production jar used by server and client | `3633b866591f6ab95bf02daebce0e8fe0b2239413443bc483b7b4496e54dfb58` |
| Official Ender IO 6.2.18-beta | `9d1471e6d4562d0a50f0564bc46573b87fd738bd440fe19397d31847da20f172` |
| Final guarded client probe | `a711dd410d4e1ac59c93d634a06494b3561854ba9aa249546626df5e7527201c` |

## Remaining verification

Exercise the opt-in Soul Vial replacement of a vanilla spawner, including the default-disabled path. Verify tag restrictions and copy-mode mob state, plus the configured Mind Killer and overcrowding behavior.
