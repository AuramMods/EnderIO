# Cycle 246 — Mekanism chemical conduit crafting and upgrades

The packaged survival client uses all five shipped chemical conduit recipes. Direct recipes convert three Mekanism Basic, Advanced or Elite Pressurized Tubes and six Conduit Binder into three Ender IO conduits. The two upgrade recipes require eight pieces of the preceding Ender IO tier around one Mekanism alloy. The test replaces one piece of each upgrade grid with the wrong tier and checks the empty result before correcting the grid. The tooltips report 750, 2,000 and 64,000 mB per tick.

Two crafted basic conduits form a line across a chunk boundary between Mekanism Ultimate Chemical Tanks. The fixture configures a blue transfer channel, an inverted extraction gate and an oxygen allowlist. The client then replaces both pieces with Pressurized and then Ender Chemical Conduits. Each click consumes one upgrade and returns one replaced part. The server verifies the saved face settings, the filter and the exact gas balances while each new batch waits behind the powered gate and after the lever turns off.

The dedicated server evicts both chunks, reloads the upgraded line and delivers a fresh batch. It then saves another batch behind the powered gate. A cold start checks the original balance before turning off the lever and verifying that only the queued amount transfers.

## Commands

```sh
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew :enderio:reobfJar :enderio:reobfClientSmokeJar --max-workers=2 --console=plain

port_runtime=build/port-runtime
port_seed="$port_runtime/cycle-245-mekanism-controls-client"
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.245-1.20.1+c9a1ed8f7c.jar
port_mek="$port_runtime/cycle-177-tin-fixed-client/mods/mekanism-uxe1WQp4.jar"
port_name=cycle-246-chemical-craft
port_client="$port_runtime/$port_name-client"
export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Djava.net.preferIPv4Stack=true'

mkdir -p "$port_client/config"
cp -cR "$port_seed/libraries" "$port_seed/versions" "$port_client/"
cp "$port_seed/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name "$port_name-client" --java "$port_java" \
  --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets" \
  --mod "$port_mek"
python3 -Bu scripts/porting/packaged_chemical_progression.py --name "$port_name" --port 26367 \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" \
  --jar "$port_jar" --mekanism "$port_mek" --launch "$port_client/launch.json"
```

For the core fluid control, prepare `cycle-246-fluid-craft-control-client` with the same production and QA jars, using `build/port-runtime/cycle-245-fluid-controls-client` as the Forge library seed. Run `packaged_fluid_progression.py` on port 26367 with that client profile and `--name cycle-246-fluid-craft-control`. Both commands use the core profile without the Mekanism argument.

## Results

- The production jar and separate QA jar reobfuscate successfully. See `build/cycle-246-chemical-assembly.log`.
- The packaged Mekanism profile passes 27 server assertions and 44 client checks. The binder advancement unlocks all five recipes. The three direct recipe IDs yield three correctly typed parts each, and the two upgrade recipes yield eight each. The server verifies exact material consumption and one returned conduit for every actual replacement.
- The oxygen allowlist leaves all 9,731 units of hydrogen at the source while the target is empty. The original line and four upgraded configurations preserve the gate, filter and both balances while powered and deliver 389,211 units of oxygen across five transfers. Actual chunk eviction and reload preserve the line and allow another 11,127 units through. A dedicated restart holds the saved 92,503 units at the source and 400,338 at the target. Removing power delivers exactly the saved batch, and the receiver stabilizes at 492,841 units. Both server processes exit cleanly without runtime or registry errors. See `build/port-runtime/cycle-246-chemical-craft/chemical-progression-results.json`.
- The core fluid progression passes 16 server assertions and 35 client checks on the same build. Its five recipes, live tier replacements, saved water filter and exact 500 mB retained batch all pass. Both server processes exit cleanly without runtime or registry errors. See `build/port-runtime/cycle-246-fluid-craft-control/fluid-progression-results.json`.
- Both clients load Ender IO jars matching their servers. The chemical profile also matches the pinned Mekanism jar. The native window observer records 4,644 samples with zero activation, foreground or onscreen events across the two launches. All six captures confirm all ten volume categories at zero, narrator off and the cursor uncaptured. The client instrumentation is in the separate QA archive. See `build/port-evidence/cycle-246-chemical-craft-client/summary.json` and `build/port-evidence/cycle-246-fluid-craft-control-client/summary.json`.

| Packaged artifact | SHA-256 |
| --- | --- |
| Ender IO | `4c787e99646719c26ac93a9401a6d7fef1abe7d5939489e9a700e84f5a30bca0` |
| Mekanism 10.4.16.80 | `3b0d191fa4a45e662f9725991d76a30192f30186abb2ba694b873b5f7b9358d2` |

## Next verification

Craft and use the stock Mekanism heat conduit and Basic Chemical Filter in a packaged survival world. Check that the crafted filter can be configured and installed and that the heat conduit takes part in a native heat transfer.
