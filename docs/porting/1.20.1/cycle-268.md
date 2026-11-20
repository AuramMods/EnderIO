# Cycle 268 — Ender IO 6.2.0-beta save upgrades

The original 6.2.0-beta release runs on a disposable Forge server. The fixture uses its own block and item APIs to place the mixed item and energy bundle, configure its endpoints and paint all six facade faces. Minecraft writes the historical chunk NBT and graph. The port opens a copy after the source server stops. A second source profile creates the same fixture under 6.2.18-beta for comparison. [Original 6.2.0-beta release](https://modrinth.com/mod/enderio/version/6.2.0-beta), [6.2.18-beta release](https://modrinth.com/mod/enderio/version/6.2.18-beta)

The old fixture writes priority through a public field in 6.2.0-beta and through a setter in 6.2.18-beta. Native NBT checks require priorities 42 and 71 in both original saves. The runner then checks the same priorities, direction settings, round robin, filter and facade in the converted block entities. It verifies loose conduit items, renamed blocks, tagged water and XP tanks, the saved Alloy Smelter configuration and a Stirling Generator that continues burning its original fuel.

The 6.2.0-beta fluid ticker transfers 50 mB of the 625 mB tagged-water sample and stalls. The source world saves 575 mB upstream and 50 mB downstream. The upgraded route must finish that same transfer and retain the tag before the test fills the tank again. Separate phases exercise all four migrated item and fluid filters, chunks actually unloading, and a cold restart.

## Commands

```sh
export JAVA_HOME="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
export CI=true JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=4
./gradlew --no-daemon --no-build-cache --no-configuration-cache --max-workers=2 --console=plain :enderio:assemble

port_runtime=build/port-runtime
port_forge="$port_runtime/cycle-047-legacy-discover"
port_jar=enderio/build/libs/enderio-8.2.14.267-1.20.1+49910cb105.jar
port_early="$port_runtime/legacy-artifacts/EnderIO-1.20.1-6.2.0-beta-all.jar"
port_later="$port_runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar"
port_art=$(rg --files --hidden "$HOME/.gradle/caches/modules-2/files-2.1/net.neoforged/AutoRenamingTool/2.0.17" \
  | rg '/AutoRenamingTool-2\.0\.17-all\.jar$' | head -1)
curl -fL --retry 2 'https://cdn.modrinth.com/data/49ZofO4f/versions/Pjq3lYBn/EnderIO-1.20.1-6.2.0-beta-all.jar' -o "$port_early"

export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Djava.net.preferIPv4Stack=true'
python3 -B scripts/porting/legacy_fixture/build.py --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" \
  --legacy-jar "$port_early" --art "$port_art" --output "$port_runtime/cycle-268-fixture-early-final"
python3 -B scripts/porting/legacy_fixture/build.py --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" \
  --legacy-jar "$port_later" --art "$port_art" --output "$port_runtime/cycle-268-fixture-later-final"
port_early_fixture="$port_runtime/cycle-268-fixture-early-final/fixture.jar"
port_later_fixture="$port_runtime/cycle-268-fixture-later-final/fixture.jar"
python3 -B scripts/porting/packaged_legacy_migration.py --name cycle-268-early-final-migration --port 26417 \
  --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" --legacy-jar "$port_early" --jar "$port_jar" \
  --conduit-fixture "$port_early_fixture" --loose-conduits --stirling-generator
python3 -B scripts/porting/packaged_legacy_migration.py --name cycle-268-later-final-migration --port 26421 \
  --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" --legacy-jar "$port_later" --jar "$port_jar" \
  --conduit-fixture "$port_later_fixture" --loose-conduits --stirling-generator
python3 -B scripts/porting/packaged_legacy_transfer_filters.py --name cycle-268-early-final-filters --port 26419 \
  --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" --legacy-jar "$port_early" --jar "$port_jar" \
  --fixture "$port_early_fixture" --legacy-partial-tagged-water
python3 -B scripts/porting/packaged_legacy_transfer_filters.py --name cycle-268-later-final-filters --port 26420 \
  --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" --legacy-jar "$port_later" --jar "$port_jar" \
  --fixture "$port_later_fixture"
python3 -B scripts/porting/packaged_legacy_redstone_filters.py --name cycle-268-later-redstone --port 26418 \
  --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" --legacy-jar "$port_later" --jar "$port_jar" \
  --fixture "$port_later_fixture"
```

## Results

- Java 17 assembly passes. The tested port archive has SHA-256 `5a9c6fb53bcfeae94b340275c011d3cd217dfe2a97bdfd59616fe4e1327b6c04`. The original archives hash to `64cd7ca4b094470ba425c849787ca40b984de1f65aedfd9fa166a421fcc57ec3` for 6.2.0-beta and `9d1471e6d4562d0a50f0564bc46573b87fd738bd440fe19397d31847da20f172` for 6.2.18-beta. The historical fixture builds are byte-identical with SHA-256 `85dd9948eba527bd5b507c0537d3d05f3d273f2f2ad6672e22dffe2a577b2234`. See `build/port-evidence/cycle-268-early-6-2/`.
- Building the unchanged fixture against 6.2.0-beta exposed the earlier item priority and redstone connection APIs. The first attempt also incorrectly expected the early save to omit priority. The corrected fixture uses the public field and verifies both saved values. See `build/port-evidence/cycle-268-early-6-2/fixture.log` and `build/port-runtime/cycle-268-early-migration/legacy-migration-results.json`.
- A separate first run of the fluid fixture exposed the changed historical ticker behavior. Reopening that source world confirmed the 575/50 mB split and exact tag in both original tanks. See `build/port-runtime/cycle-268-early-filters/legacy/smoke-diagnostic.log`.
- The mixed bundle and machine fixture passes 79 assertions for each original release. Priorities 42 and 71, the converted graph, facade and filter survive the first load and restart. The live route delivers seven diamonds in the source world, then reaches ten after conversion and 13 after restart. The five rejected emeralds remain upstream. Both profiles retain the tagged inventories and tanks, Alloy Smelter settings and active Stirling fuel. See `build/port-runtime/cycle-268-{early,later}-final-migration/legacy-migration-results.json`.
- The transfer-filter fixture passes 61 assertions for each release. The upgraded 6.2.0-beta route drains the remaining 575 mB into the existing 50 mB and reaches exactly 625 mB with the original `Sample:43` tag. The sparse filter samples and flags survive chunk eviction and restart. Both reopened profiles transfer approved batches and preserve rejected contents. See `build/port-runtime/cycle-268-{early,later}-final-filters/legacy-transfer-filter-results.json`.
- The 6.2.18-beta redstone fixture passes 71 assertions, including the native force-connected comparator, saved counters and timers, and the exact recovered XOR filter drop. The five final profiles pass 351 assertions across 15 headless sessions, each exiting at zero with zero runtime or registry errors. See `build/port-runtime/cycle-268-later-redstone/legacy-redstone-filter-results.json`.

## Next verification

Exercise more stock SAG Mill recipes and their alternate tagged inputs on the current packaged candidate. [Completion audit](completion-audit.md)
