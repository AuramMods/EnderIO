# Cycle 054 — migrate placed Mekanism chemical and heat conduits

An actual Ender IO 6.2.18-beta world contains twelve placed Mekanism conduits across six routes. The previous bundled port omitted all twelve from their loaded bundles despite Mekanism being installed. Forge 6.x registered three distinct chemical tier IDs and a heat ID. The port now maps all four placed IDs and loads their indexed networks and block connections. Heat endpoints preserve the original insert and extract flags, redstone control and redstone channel.

An old chemical node can store a boxed chemical in `LockedFluid`. The migration decodes that compound with Mekanism's native API and stores its family and chemical identity in the current node. The lock applies to fresh extraction. It does not add any chemical to inventory or to the queue of already extracted material. The current codec also reads earlier port saves that have no lock.

The old fixture places conduits through the original block items and configures their native endpoints. It initializes a hydrogen lock through the original node's deserializer and lets the original server write it to disk. This exercises the old save format. The separate probe uses real Mekanism tanks and thermodynamic conductors on both releases. It checks exact chemical totals, tests the hydrogen lock with oxygen, and measures the destination warming after each heat pulse.

## Reproduction

```sh
./gradlew build --continue --console=plain

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_legacy=build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar
port_jar=enderio/build/libs/enderio-8.2.14.53-1.20.1+55651a327b.jar
port_art="$HOME/.gradle/caches/modules-2/files-2.1/net.neoforged/AutoRenamingTool/2.0.17/9388df4c3589faf10c35d9154cfb2451c83bd717/AutoRenamingTool-2.0.17-all.jar"
port_mek="$HOME/.gradle/caches/modules-2/files-2.1/maven.modrinth/mekanism/uxe1WQp4/b7f12ae710888b01746507a0aa8cb375f5897158/mekanism-uxe1WQp4.jar"

python3 scripts/porting/legacy_fixture/build.py \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" --art "$port_art" \
  --output build/port-runtime/cycle-054-fixture-verified
python3 scripts/porting/legacy_fixture/build.py \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" --art "$port_art" \
  --mekanism-probe --mod "$port_mek" --output build/port-runtime/cycle-054-probe-verified
python3 scripts/porting/packaged_legacy_migration.py \
  --name cycle-054-mekanism-verified --port 25684 --java "$port_java" \
  --forge-server "$port_forge" --legacy-jar "$port_legacy" --jar "$port_jar" \
  --conduit-fixture build/port-runtime/cycle-054-fixture-verified/fixture.jar --placed-mekanism-conduits \
  --mod "$port_mek" --mod build/port-runtime/cycle-054-probe-verified/mekanism-probe.jar
python3 scripts/porting/packaged_legacy_migration.py \
  --name cycle-054-core --port 25681 --java "$port_java" \
  --forge-server "$port_forge" --legacy-jar "$port_legacy" --jar "$port_jar" \
  --conduit-fixture build/port-runtime/cycle-054-fixture-verified/fixture.jar --loose-conduits
```

Use fresh output directories and available loopback ports on reruns. Set `port_jar` to the built artifact for the checkout under test. The profiles use Forge 47.4.28 and Java 17. The first profile installs Mekanism 10.4.16.80 and the QA probe. The converted core profile uses the bundled Ender IO jar alone.

For the combined profile, use a fresh run name and port such as `cycle-054-combined-verified` and `25683`. Add `--placed-storage-conduits --loose-integration-conduits` and the four `--mod` arguments for AE2, GuideME, Refined Storage and the already built storage probe from [cycle 053](cycle-053.md).

## Results

- The full build passed 78 ordinary JUnit and 418 native GameTests. Native counts were 171 core, 7 core Mekanism, 115 registry, 98 gameplay and 27 addon tests. The build log is `build/port-evidence/cycle-054-build.log`. The baseline packaged run against the prior port passed authentic source checks and failed the first converted Mekanism bundle assertion. It logged all twelve skipped bundles. Its report is under `build/port-runtime/cycle-054-baseline-verified/`.
- The packaged Mekanism profile passed 110 assertions. Each run starts with empty source tanks and transfers 1,450 units of oxygen, redstone infusion, red pigment and dirty iron slurry from separate tanks. Destination totals reach 1,450 in the original world, 2,900 after conversion, 4,350 after chunk reload and 5,800 after restart. The separate hydrogen-locked route holds its 1,450 units of oxygen at the source and accepts all 1,450 units of hydrogen after restart. A real Mekanism thermodynamic conductor warms by more than one kelvin after each pulse.
- The combined profile passed 196 assertions. It verifies both sets of placed routes, exact AE2 and RS owners and all thirteen loose conduit variants with AE2, GuideME, Refined Storage and Mekanism installed together. The core profile passed 72 assertions without optional integration mods. Reports are at `build/port-runtime/cycle-054-{mekanism-verified,combined-verified,core}/legacy-migration-results.json`.
- All nine headless processes exited cleanly. Each profile records four first-load diagnostic headers for unresolved old Forge registry IDs and zero other runtime errors.
- The post-restart disk audit found exactly two correctly typed nodes and one edge per route. Both locked nodes contain the gas family and hydrogen identity with an empty pending-transfer list. See `build/port-evidence/cycle-054-saved-graphs.log`. The packaged jar SHA-256 is `2161c941f11f5bd41d69a38e4b5b5cfd0ebfd8858b7509cbeb43425332769170`.

Mekanism must be installed while converting worlds with its placed conduits. The lock is attached to the imported node. The heat port uses the current single transfer channel while retaining the saved redstone channel. Wider heat behavior, client conduit controls and historical extraction speed upgrade migration remain open.
