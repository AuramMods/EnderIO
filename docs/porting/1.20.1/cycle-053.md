# Cycle 053 — migrate placed AE2 and Refined Storage conduits

The previous packaged port skipped all eight placed storage conduits in a genuine 6.2.18-beta save. It also skipped the Refined Storage graph because the old registry ID was `enderio:refined_storage`. The historical saved data lists a mixed normal and dense ME network under both graph types. The entries contain only positions and native data, so neither copy identifies the tier at each position. The chunk's bundle records do identify the tier and, in this fixture, retain the AE2 owner after the historical shutdown removes the native grid. The previous build's failure is recorded in `build/port-evidence/cycle-053-before.log` and the copied world's logs.

Forge 6.x bundles now map both ME tiers and Refined Storage to their current registry keys. ME graphs in the old position-only format rebuild from the placed bundles using the existing node recovery path. This restores each registered tier and the native managed-node NBT from that bundle. ME node creation now exposes the faces recorded as connected. The converter preserves the old rule that either I/O flag connects an ME endpoint. The runtime connection check also accepts native blocks that implement AE2's grid host interface directly. A new native GameTest confirms a creative energy cell powers a drive through freshly placed conduits.

Refined Storage keeps its indexed graph and restores the native node payload under the new registry key. An addon can decode its own historical node data through the common conduit interface. An intermediate save contained duplicate Ender IO nodes at the left RS conduit. A headless diagnostic run traced the duplicate to synchronous RS graph invalidation during chunk unload. It invoked vanilla chunk loading and attached a second block entity before teardown finished. Unloading now unregisters the live RS node and queues graph invalidation on the server. The queued callback refreshes controllers whose block entities are still loaded. If a bundle reattaches first, it retires the old graph entry before publishing the new node at that position. Breaking a conduit still invalidates its network immediately.

The source fixture places three powered AE2 drive routes with normal, dense and mixed pairs, plus a Refined Storage controller and disk drive route. Every route crosses a real chunk boundary. A separate QA mod uses only native AE2 and Refined Storage APIs to check live connections and owners on both releases. It seeds distinct RS owners in the historical server and asks its controller to recalculate the graph after placement. The probe only observes the converted server. The fixture and probe jars are separate from the distributed port.

## Reproduction

```sh
./gradlew build --continue --console=plain

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_legacy=build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar
port_jar=enderio/build/libs/enderio-8.2.14.52-1.20.1+7867ed3c91.jar
port_art="$HOME/.gradle/caches/modules-2/files-2.1/net.neoforged/AutoRenamingTool/2.0.17/9388df4c3589faf10c35d9154cfb2451c83bd717/AutoRenamingTool-2.0.17-all.jar"
port_mod_cache="$HOME/.gradle/caches/modules-2/files-2.1/maven.modrinth"
port_ae="$port_mod_cache/ae2/QRpVsqDI/4e81984691e35c729880a11f72deb52dcc74d41f/ae2-QRpVsqDI.jar"
port_guide="$port_mod_cache/guideme/i7Tp1AHw/3d432fc99cefcfd17a12635d2d220f09a069f1c7/guideme-i7Tp1AHw.jar"
port_rs="$port_mod_cache/refined-storage/ZITLFjjf/14bf4de4975f6310d22a6ed06c6a7e1e0805641/refined-storage-ZITLFjjf.jar"

python3 scripts/porting/legacy_fixture/build.py \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" --art "$port_art" \
  --output build/port-runtime/cycle-053-fixture
python3 scripts/porting/legacy_fixture/build.py \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" --art "$port_art" \
  --storage-probe --mod "$port_ae" --mod "$port_rs" --output build/port-runtime/cycle-053-probe-verified
python3 scripts/porting/packaged_legacy_migration.py \
  --name cycle-053-storage-reattach --port 25675 --java "$port_java" \
  --forge-server "$port_forge" --legacy-jar "$port_legacy" --jar "$port_jar" \
  --conduit-fixture build/port-runtime/cycle-053-fixture/fixture.jar --placed-storage-conduits \
  --mod "$port_ae" --mod "$port_guide" --mod "$port_rs" \
  --mod build/port-runtime/cycle-053-probe-verified/storage-probe.jar
python3 scripts/porting/packaged_legacy_migration.py \
  --name cycle-053-core-reattach --port 25676 --java "$port_java" \
  --forge-server "$port_forge" --legacy-jar "$port_legacy" --jar "$port_jar" \
  --conduit-fixture build/port-runtime/cycle-053-fixture/fixture.jar --loose-conduits
```

Use fresh output directories and available loopback ports on reruns. Set `port_jar` to the bundled artifact built from the checkout under test. Both profiles use Forge 47.4.28 and Java 17.

## Results

- The full build passed 78 ordinary JUnit tests and 418 native GameTests. Native runs executed 171 core, 7 core Mekanism, 115 registry, 98 gameplay and 27 addon tests. The addon suite includes the powered AE2 route and the immediate RS block entity reload with a different native node and the original owner. Build log is `build/port-evidence/cycle-053-reattach-build.log`.
- The packaged storage profile passed 89 assertions. The native probe verified the three powered AE2 drives and the RS controller-to-drive route on the historical release, on initial conversion, after both chunks unloaded and reloaded, and after a clean restart. It also verified AE2 tier and exact owner UUIDs on both integrations. The core profile passed 72 assertions without those integrations, including loose item migration and filtered cross-chunk item delivery.
- The saved graph audit verified exactly two correctly typed nodes and one edge per storage route after shutdown. The mixed AE2 route kept one normal and one dense node. The evidence is in `build/port-evidence/cycle-053-saved-graphs.log`. The earlier duplicate and stack trace are recorded in the `cycle-053-native-host`, `cycle-053-storage-lifecycle` and `cycle-053-shutdown-debug` profiles.
- The per-assertion reports are at `build/port-runtime/cycle-053-{storage,core}-reattach/legacy-migration-results.json`. All six headless servers exited cleanly. Forge emitted four first-load missing registry diagnostic headers per profile for unresolved old IDs. The first migrated storage load also reported six missing legacy graph entries as it recovered the ME nodes from the bundles. The reports contain zero other runtime errors.
- Both final profiles use the bundled artifact with SHA-256 `fc047a6fd7fb506c0bfe36dfd4e668fba0fb27239d19d498d49a17d9c6fd2453`. The reports record the exact Forge release, source and fixture hashes, and the installed dependency hashes.

Worlds with these placed integration conduits need AE2 or Refined Storage installed during migration. The dependency-free profile exercises core content. Client rendering, storage operations and detailed ME channel limits still need verification. Placed Mekanism conduit migration and historical extraction speed upgrades remain open.
