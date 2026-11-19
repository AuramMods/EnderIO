# Cycle 123 — Migrate Forge 6.2 item and fluid filters

The native Minecraft item loader now converts filters in ordinary inventories and in saved conduit bundles. The conversion retains the indexed samples, allow or deny setting, tag setting and unrelated item NBT. It preserves the Copper Alloy sample through the Forge item alias and remaps a tagged XP Juice sample to the target fluid ID. A sample that cannot be decoded leaves the original filter payload intact so it can be recovered when the dependency is present.

Forge 6 advanced item filters include damage in an exact tag match. Forge 6 fluid filters compare only tags in their NBT mode and compare both fluid ID and tags otherwise. Empty sample slots participate in the old fluid tag comparison. The item data codecs store these modes, and the menu setters carry them forward during edits. Newly made filters use the 1.21.1 rules.

## Commands

```sh
./gradlew build --continue --console=plain > build/cycle-123-build-verified.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_legacy=build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar
port_fixture=build/port-runtime/cycle-123-native-fixture
port_art="$HOME/.gradle/caches/modules-2/files-2.1/net.neoforged/AutoRenamingTool/2.0.17/9388df4c3589faf10c35d9154cfb2451c83bd717/AutoRenamingTool-2.0.17-all.jar"
port_current=enderio/build/libs/enderio-8.2.14.122-1.20.1+61eb0b4154.jar

python3 scripts/porting/legacy_fixture/build.py --java "$port_java" --forge-server "$port_forge" \
  --legacy-jar "$port_legacy" --art "$port_art" --output "$port_fixture" > build/cycle-123-native-fixture.log 2>&1
python3 scripts/porting/packaged_legacy_transfer_filters.py --name cycle-123-fixed --port 25950 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" \
  --fixture "$port_fixture/fixture.jar" --jar "$port_current" > build/cycle-123-fixed.log 2>&1
```

## Results

- The original 6.2.18-beta server places four routes across a chunk boundary and writes filters using its own item and fluid filter capabilities. It saves matching copies in a barrel. The previous production jar opens the copied world and fails the first loose filter migration assertion. See `build/port-runtime/cycle-123-stock-loose-repro/results.json`.
- The original fluid ticker simulates a tagless drain before calling the filter. That leaves the tagged test input in its source in 6.2.18-beta. The fixture also verifies real untagged transfers on the original server and uses tagged fluids to exercise the migrated filters in the port. The destinations are empty in the rejection checks so the tank's own fluid rules cannot account for a rejection.
- The full build passes 79 unit cases and 489 native test executions, including all 167 discovered gameplay tests. The two added native cases verify exact damage, both legacy fluid modes, the current matching rules, and lossless handling of unavailable item or fluid samples.
- The fixed packaged run passes 61 assertions. Four loose and four installed stacks retain their sparse sample positions, flags and tags after conversion, chunk eviction and dedicated restart. The live routes accept both paper tags and reject iron, distinguish exact pickaxe damage and NBT, and keep the aliased ingot at the source. The two fluid routes move exactly 625 mB and 575 mB when permitted and leave rejected tagged water in its original tank. All three server sessions are headless, exit at zero and report zero runtime or registry errors. See `build/port-runtime/cycle-123-fixed/legacy-transfer-filter-results.json`. The production SHA-256 is `f089567670feb07b0fffadb25a628c1c2ced56457a39d73b058b91543a91c89f`.

## Remaining verification

Packaged multiplayer editing remains open for the item and fluid filter families. The large item filters and both limited stock filters are newer than this Forge 6 fixture and need their own live scenarios. Earlier port builds wrote installed filters without the comparison modes added here. Reload the original 6.2 save to recover those modes for such filters.
