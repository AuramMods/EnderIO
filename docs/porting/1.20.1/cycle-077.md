# Cycle 077 — Obelisk survival carriage

## Scope

Exercise the six obelisk loot tables through ordinary survival mining, automatic pickup, ordinary placement and a dedicated server restart. Verify an actual rain job with the tagged fuel from the replanted Weather Obelisk.

## Changes

- Weather recipes match the fluid type and amount. Forge's stack-based drain also compared tags, which let a job use a rocket and change the weather while leaving the tagged reagent untouched. The obelisk now checks its live inputs, debits the matched amount and rocket, then changes the weather and launches the firework.
- Add a Forge regression that executes a saved rain job at its completion boundary and checks the remaining tagged fluid, rocket and firework. The test restores the test world's weather afterward.
- Add a packaged survival fixture for Inhibitor, Aversion, Relocator, Attractor, XP and Weather. Verify the exact authoritative drop, inventory and replanted block, including nested filters, visibility, zero range, capacitors, energy, I/O modes, tagged fluids and tagged rockets.
- Preserve the 1.21.1 Weather Obelisk's separation of carried inputs from its world-only task. The survival drop carries the inputs and redstone mode. The replanted block begins its own job when re-enabled.

## Commands

```sh
./gradlew :enderio:verifyGameplayTests --console=plain
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain
./gradlew :enderio:reobfClientSmokeJar --console=plain
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_jar=enderio/build/libs/enderio-8.2.14.76-1.20.1+408c8540b1.jar
port_qa=enderio/build/test-libraries/enderio-client-qa.jar
mkdir build/port-runtime/cycle-077-client-verified
cp -R build/port-runtime/cycle-077-client-held/libraries build/port-runtime/cycle-077-client-held/versions \
  build/port-runtime/cycle-077-client-verified/
python3 scripts/porting/client/prepare_packaged.py --name cycle-077-client-verified --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" --qa-jar "$port_qa" \
  --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_obelisk_carry.py --name cycle-077-verified --port 25792 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" \
  --launch build/port-runtime/cycle-077-client-verified/launch.json
python3 scripts/porting/packaged_obelisks.py --name cycle-077-modes --port 25790 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar"
```

## Results

- The first native test run reproduced the tagged-fluid debit failure. The rocket count dropped to one while the tank stayed at 1,237 mB. The fixture expected 237 mB with the original tag. See `build/cycle-077-reproduction.log`.
- The full build passed 79 ordinary unit tests and 453 required Forge GameTests, with zero failures or skips. The profiles ran 171 core, seven Mekanism, 117 registry, 131 gameplay and 27 addon cases. The final 131-case gameplay rerun also proves that the loaded completion waits for a missing rocket without changing the weather or spawning a firework. See `build/cycle-077-final-build.log` and `build/cycle-077-rocket-regression.log`.
- The initial client predicted an instant break while the server kept the Inhibitor at its original coordinates. The saved pickaxe had taken zero damage and the server found the intact block on reopen. The fixture now waits for grounded ticks and holds the native mining action with an Efficiency V pickaxe. See `build/port-runtime/cycle-077-final/smoke-inspection.log` and `build/cycle-077-mining-qa-build.log`.
- The next run verified all six survival drops, pickup, placement and restored world state. Its restart assertion expected no task object. The authoritative reload creates an idle recipe with zero progress, so the final check measures paid ticks. See `build/port-runtime/cycle-077-held/obelisk-carry-results.json` and the `Task` read in `build/port-runtime/cycle-077-verified/smoke-reopened.log`.
- The existing headless weather regression passed 15 assertions on the same production jar. Rain, thunder and clear each launched a firework, changed the weather and debited one bucket and one rocket. The roof pause and input restart checks also passed. See `build/port-runtime/cycle-077-modes/obelisk-results.json`.
- The final survival run passed 35 server assertions and 27 client checks. Each of the six obelisks dropped once, entered the player's inventory through native pickup and was consumed by native placement. The server confirmed every inventory field, and the client received the exact charge and range or tagged fluid. The four powered obelisks conserved 31,610 FE in total and retained their original range, visibility, redstone modes, capacitors and any installed filters. The XP Obelisk kept its 1,573 mB, tag and both sides. The Weather Obelisk kept 1,237 mB of tagged Cloud Seed and two tagged rockets. After restart, the new rain job began with zero paid ticks, then produced rain and a firework while leaving 237 mB and one rocket. Both server instances saved and exited with code 0 and zero runtime or registry errors. See `build/port-runtime/cycle-077-verified/obelisk-carry-results.json` and both server logs.
- The client and server used the same Java 17 production jar, SHA-256 `421d533b586d1034ce1cabd9a108847b2a7ef394e5a9c892bde45e6f14b18dcc`. The QA probe is in a separate jar. Native observation recorded 2,200 samples with zero activations, foreground samples or visible windows. The replanted Weather menu rendered with all volumes at zero, narrator off, fullscreen off and the cursor uncaptured. See `build/port-evidence/cycle-077-verified-client/summary.json`, `focus.json` and `replanted-weather-obelisk.png`.

## Remaining verification

Mod-added bosses and cross-mod spawn listeners remain outside the obelisk evidence. XP erase crafting and the Weather JEI category remain open. The Farming Station still needs output-conservation, replanting, tool, menu and lifecycle coverage.
