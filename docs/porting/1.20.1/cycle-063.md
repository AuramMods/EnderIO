# Cycle 063 — conserve capacitor-bank charge when splitting

On the cycle 062 packaged jar, four banks across a chunk boundary stored 7,000,000 FE. Removing the basic bridge produced an uncharged item. The surviving networks held 466,666 and 6,000,000 FE, losing 533,334 FE in total. The old allocation divided the energy while the removed node's capacity was still in the denominator. It also tried to obtain the item's share during loot export after modifying the network, which could only work for some removal entry points.

The bank now reserves the removed block's charge before splitting. It retains the existing equal-share rule and exports enough to fit the remaining capacity when removing a larger tier. The surviving fragments divide the remainder in proportion to their capacities using exact integer arithmetic. Loot export reads the calculated or captured share without debiting the live network. This covers native loot generation both before block removal and after it.

Chunk unload releases the block-entity reference while keeping the graph node and its charge. Reload claims that node. Retained capability views stop transferring when the block detaches or is removed. Merge and split update the cached positions, register resulting fragments even when their nodes are unloaded, and discard the superseded network ID. Loading an I/O change invalidates the cached push targets.

## Reproduction

Use Java 17, Forge 47.4.28 and the [hidden-client harness](client-harness.md). Use fresh names to rerun the profiles.

```sh
./gradlew :enderio:assemble :enderio:reobfClientSmokeJar --console=plain
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_jar=enderio/build/libs/enderio-8.2.14.62-1.20.1+0c1a8a8bdf.jar
mkdir build/port-runtime/cycle-063-client
cp -R build/port-runtime/cycle-051-client-verified/libraries \
  build/port-runtime/cycle-051-client-verified/versions build/port-runtime/cycle-063-client/
python3 scripts/porting/client/prepare_packaged.py --name cycle-063-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_capacitor_banks.py --name cycle-063-menu-verified --port 25710 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" \
  --launch build/port-runtime/cycle-063-client/launch.json
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain
```

The first headless run used `--name cycle-063-baseline --port 25707` with the cycle 062 distributable `enderio-8.2.14.61-1.20.1+a7a6d1f5a1.jar`. The fixed headless run used `--name cycle-063-packaged --port 25708` with the new jar. Omit `--launch` for headless profiles. The runner inspects exact node positions, capacities, indexed edges and energy from the game's saved data after `save-all flush`.

## Results

- The baseline observed the missing drop charge, the 533,334 FE loss and the smaller balance after restart. The restarted bank also failed to push to a wired charger after a live I/O change. Both server processes exited zero with no ERROR or FATAL lines. See `build/port-runtime/cycle-063-baseline/capacitor-bank-results.json`.
- The fixed headless profile passed all 13 assertions. The graph retained its four unique nodes and three edges across chunk eviction and reload. The 500,000 FE drop and surviving balances of 500,000 and 6,000,000 FE accounted for all 7,000,000 FE. The replacement restored one graph with four nodes, three edges and 6,500,000 FE. After restart it delivered 128,000 FE to an adjacent wired charger, leaving 6,372,000 FE. Both server processes exited zero with no ERROR or FATAL lines. See `build/port-runtime/cycle-063-packaged/capacitor-bank-results.json`.
- The previous removal GameTest destroyed the block with drops explicitly disabled and assigned all pre-break energy to the surviving bank. The initial fixed build exposed this mismatch. The test now requests native drops and checks the exact amount in both the item and the surviving bank for partial and full arrays. A second case checks full-singleton loot before and after removal and checks that the retained capability cannot spend the carried charge. See `build/port-cycle-063-build.log` for the first build's failure.
- The first client-enabled attempt reached its server startup timeout during a concurrent full build. The server finished startup as the runner closed it, and both server processes exited cleanly. The rerun waits longer for actual startup. See `build/port-runtime/cycle-063-menu/smoke-restart.log`.
- The full build passed 78 ordinary unit tests and 429 native tests, with zero failures, errors or skips in XML. See `build/port-cycle-063-build-verified.log` and the modules' `build/test-results/`.
- The final packaged multiplayer run passed 17 server assertions and six client checks. The menu received four unique positions, 7,000,000 FE capacity and 6,372,000 FE stored. The native redstone picker updated all four server blocks and stopped the bank from topping up the emptied receiver, with no change to the bank's balance. Both servers exited zero with no ERROR or FATAL lines. The hidden 960×540 capture shows the energy bar and disabled mode. The narrator was OFF, all ten sound categories were muted, and there were zero activation, foreground or visible-window events in 2,145 native samples. The client exited zero with no ERROR or FATAL lines. See `build/port-runtime/cycle-063-menu-verified/capacitor-bank-results.json` and `build/port-evidence/cycle-063-menu-verified-menu/`.

| Artifact | SHA-256 |
| --- | --- |
| Baseline mod | `f846d39d3dcf30955322b98c604eae46ee109b34a897369eb5a9497f586bdb67` |
| Fixed packaged mod | `5b42c817fa27db4ed4812f633793599e204154eac3f40696d0d5c1dac681680f` |
| Test-only client probe | `9ea77f49a2775f1c84e0d96e7294031db39dba2ca8234326fc7cb43b22ff9539` |

## Remaining verification

Historical 6.2 capacitor-bank saves, survival replacement of a charged bank item, and optional connected textures remain open. The packaged fixture covers a four-block array and its normal controls.
