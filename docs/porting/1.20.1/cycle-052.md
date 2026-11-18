# Cycle 052 — migrate loose Forge 6.2 conduit items

Forge 6.2.18-beta registered a separate item for each conduit variant. The port uses `enderio:conduit` and stores the variant in item data. The previous packaged port opened an authentic world with an empty barrel after the official 6.2.18-beta server had saved six core conduit stacks. Their original quantities ranged from three to eight. The source and conversion both shut down normally. The reproduction and server logs are in `build/port-evidence/cycle-052-before.log` and `build/port-runtime/cycle-052-before/`.

Forge aliases resolve the thirteen old item IDs to the current conduit item. During native item-stack loading, Ender IO copies the conduit type from the original ID if the loaded stack lacks typed conduit data. Existing typed data, quantities and other tags survive. The saved input is left intact. Optional conduit IDs can be preserved when their addon is absent because recording the type does not require a registered variant.

The two new GameTests place each of the six core variants using native block placement and verify that each placement consumes one item. They also verify precedence for explicit type data and round-trip an unavailable Refined Storage conduit through the strict inventory codec with an integer count of 137.

## Reproduction

```sh
./gradlew build --continue --console=plain

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_legacy=build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar
port_jar=enderio/build/libs/enderio-8.2.14.51-1.20.1+8724381e50.jar
port_forge=build/port-runtime/cycle-047-legacy-discover

python3 scripts/porting/packaged_legacy_migration.py \
  --name cycle-052-core --port 25660 --java "$port_java" \
  --forge-server "$port_forge" --legacy-jar "$port_legacy" --jar "$port_jar" \
  --loose-conduits --conduit-fixture build/port-runtime/cycle-051-fixture/fixture.jar

port_mod_cache="$HOME/.gradle/caches/modules-2/files-2.1/maven.modrinth"
python3 scripts/porting/packaged_legacy_migration.py \
  --name cycle-052-integrations --port 25661 --java "$port_java" \
  --forge-server "$port_forge" --legacy-jar "$port_legacy" --jar "$port_jar" \
  --loose-integration-conduits \
  --mod "$port_mod_cache/ae2/QRpVsqDI/4e81984691e35c729880a11f72deb52dcc74d41f/ae2-QRpVsqDI.jar" \
  --mod "$port_mod_cache/guideme/i7Tp1AHw/3d432fc99cefcfd17a12635d2d220f09a069f1c7/guideme-i7Tp1AHw.jar" \
  --mod "$port_mod_cache/mekanism/uxe1WQp4/b7f12ae710888b01746507a0aa8cb375f5897158/mekanism-uxe1WQp4.jar" \
  --mod "$port_mod_cache/refined-storage/ZITLFjjf/14bf4de4975f6310d22a6ed06c6a7e1e0805641/refined-storage-ZITLFjjf.jar"
```

Use fresh names and available loopback ports on reruns. When building at a later commit, set `port_jar` to the new bundled artifact. Both profiles use Forge 47.4.28 and Java 17. The integration profile installs [the pinned Forge dependencies](dependencies.md), including GuideME for AE2, in both releases.

## Results

- Full build passed with 78 ordinary JUnit and 417 native GameTests. Native counts were 171 core, 7 core Mekanism, 115 main registry, 98 main gameplay and 26 addon tests. Build log is `build/port-evidence/cycle-052-final-build.log`.
- The headless core profile passed 72 assertions. It checked the six barrel variants, the old item conduit stack in an alloy smelter's inventory and the existing placed-conduit migration fixture. The headless integration profile passed 63 assertions, including ME, Dense ME, Refined Storage, three tiers of chemical conduit and heat conduit. Each profile verified the exact item types, counts, custom tags and inventory slots on the original server, after conversion and after a clean restart.
- Both reports are at `build/port-runtime/cycle-052-{core,integrations}/legacy-migration-results.json`. They record six clean server exits and zero runtime errors outside the first-load Forge diagnostics for missing registry mappings. Those diagnostics still list historical entries such as the old lights and extraction speed upgrades. Forge also warned that several old numeric IDs resolve to the same conduit item. The tested stacks and their types survived the remap and restart.
- The packaged artifact has SHA-256 `9c5e954369d03cdc1935bf5b89918725e085774dc7b6750ed7b5c13d402c6013`. Each report includes the original jar hash and the installed dependency hashes.

Placed integration bundles and their old graph and node data still need migration coverage. The transfer effect of historical per-connection extraction speed upgrades remains unresolved. These headless tests cover server-side inventory migration and core placement. Dedicated-client inventories, optional placement and full parity still need verification.
