# Cycle 042 — retain rejected conduit fluid through removal and recovery

Status: progress, not completion. The preceding goal turn made verified progress with packaged TCP tests and commit `32e89eb8e`; the user then published that branch and paused/resumed development. This cycle addresses the fluid execution-remainder risk recorded in cycle 037. Chemical transfer and the broader parity matrix remain incomplete.

## Reproductions and changes

The native gameplay fixture attaches a real Forge fluid capability to disposable barrel block entities. Its source refuses all insertion, while its destination advertises capacity during simulation and accepts either 250 mB or zero during execution. The production conduit ticker, graph, capabilities and filters perform the transfer; the fixture only controls the external handler behavior.

The first run executes 78 gameplay cases and fails the two new conservation assertions. A 3,000 mB source loses 1,000 mB; the partial destination receives only 250 mB, leaving a total of **2,250 mB**. Complete rejection leaves **2,000 mB**. Forge's `FluidUtil.tryFluidTransfer` returns only the amount accepted after draining, losing the remainder; inserting back into this output-only source cannot repair it.

- The fluid ticker now takes ownership of the entire actual extraction in persistent `FluidConduitNodeData` before calling the destination. It removes only the amount actually accepted. Source budgets account for the entire extraction, including any retained remainder; destination path budgets account for actual delivery.
- Retained fluid is associated with the extraction face and preserves fluid identity/NBT. Subsequent updates attempt that batch through the currently eligible insertion paths/filters before extracting again. A retry update does not also drain fresh source fluid. Redstone/channel routing remains governed by the existing network configuration. Blocked fluid stays owned, rather than being discarded or forced past a filter.
- The registered node-data codec stores retained entries in normal network saves and the bundle's independent recovery snapshot. Changes mark the owning block entity dirty. Tier upgrades retain the old node data, and carried data merges without replacing existing resources or mixing different fluids.
- Optional conduit item save/load hooks carry the resources in a dropped conduit item's NBT. Both new block placement and insertion/upgrading an existing bundle restore that state. Returned lower-tier upgrade items do not carry a second copy.
- Broader destruction tests exposed two additional failures. Waterlogging invoked `onRemove` and cleared the entire bundle even though its block type stayed the same; cleanup now requires an actual block-type change. Native block destruction with drops enabled produced no conduit item; the native loot query now copies each conduit with its owned data, connection inventories and facade. Selected player removal still emits its items once, and subsequent native harvest sees the cleared bundle.

The first waterlogging test aborted its native server with `Conduit not found in bundle`; its crash report is preserved. After the state-change fix the suite reported the remaining native-drop assertion (`expected 1, got 0`). Both failures are resolved by the final run. An intermediate test compilation also exposed a missing `assertTrue` wrapper; the helper now delegates that assertion to native GameTest.

## Verification

Eleven new cases execute in the actual Forge gameplay profile:

1. Partial execution retains 750 mB and later delivers it exactly once.
2. Complete rejection retains the full 1,000 mB and later delivers it exactly once.
3. Native unload/detach, `BlockEntity.loadStatic`, attachment and `onLoad` preserve the retained graph node.
4. The network-node codec preserves resources; removing the live graph independently exercises recovery from the block entity's saved `NodeData`.
5. Conduit removal produces a resource-bearing item; native `BlockItem.place` restores it and normal transfer resumes.
6. Last-conduit player destruction followed by native `playerDestroy` emits the retained item once, without a duplicate harvest drop.
7. Native item upgrade preserves 1,000 mB existing water plus 500 mB carried lava. Water delivers while incompatible lava remains owned, and the returned lower-tier item contains no duplicated resources.
8. Waterlogging preserves the conduit and its retained fluid.
9. Native `Level.destroyBlock(..., true)` emits the retained item once; placement restores it.
10. A mixed fluid/energy bundle with a filter returns all three items once. Querying drops does not mutate live ownership.
11. Redstone-disabled extraction and an incompatible insertion filter retain the fluid; enabling the route delivers it.

The tagged-water cases check NBT preservation, exact source/destination/retained totals, no new drain during retry and normal rate resumption afterward. The original 76 gameplay cases remain passing. The final full build passes **78 ordinary JUnit and 397 native GameTest results**, with no failed/error/skipped cases: core unit 61, main CPU 16, addon unit 1; core native 170, Mekanism 7, main registry/Jupiter 115, main gameplay 87, addon native 18. The post-run coverage comparison confirms all 87 discovered gameplay cases executed and passed. Valid up-to-date ordinary unit outputs are reused where applicable. Native profiles execute; cross-profile overlap is possible and Jupiter is counted once through its native wrapper.

The callback/codec tests above are not an actual chunk eviction or disk/server restart of a retained conduit. Those stronger lifecycle cases, arbitrary topology changes, source removal/reconfiguration and hostile handlers that violate Forge's basic return-value contracts remain unverified. Native block destruction is covered; an actual explosion and canceled player-break event are not exercised here. The generic item hooks are not evidence of chemical conservation until the chemical implementation uses and tests them.

Actual commands (all from the repository root):

```sh
./gradlew :enderio:runGameTestServer --console=plain
./gradlew :enderio:compileJava --console=plain
./gradlew build --continue --console=plain
python3 scripts/porting/client/prepare_packaged.py \
  --name cycle-042-client \
  --java /Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java \
  --installer build/port-runtime/forge-47.4.28-installer.jar \
  --jar enderio/build/libs/enderio-8.2.14.41-1.20.1+32e89eb8e9.jar \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar \
  --assets /Users/cyberpwn/.gradle/caches/neoformruntime/assets
python3 scripts/porting/client/run_multiplayer.py \
  --name cycle-042-multiplayer \
  --java /Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java \
  --installer build/port-runtime/forge-47.4.28-installer.jar \
  --jar enderio/build/libs/enderio-8.2.14.41-1.20.1+32e89eb8e9.jar \
  --launch build/port-runtime/cycle-042-client/launch.json
```

The packaged regression reuses the unchanged standalone QA mod with the newly built main jar, copied official libraries/metadata in a fresh owned client directory, and freshly prepared quiet settings. The server is a new official installation. It exercises the cycle-041 TCP tank scenario; it does not reproduce rejected conduit insertion in a packaged client.

The packaged regression passes **19 client checks and five independent server assertions** across reconnect and a disk-backed tank server restart. Client/server SHA-256 values match the final main jar below. The exercise/reconnect observer records 1,512 samples; the new client after restart records 1,102. Both see three owned native windows, working WindowServer queries, zero activation/foreground/onscreen observations, no timeout and exit 0. All eight captures report narrator OFF, ten zero volume categories and hidden/unfocused/windowed/uncaptured-cursor state. Both clients and both servers have zero ERROR/FATAL entries and exit 0. Filled-world and post-restart menu PNGs were visually inspected: water, tank/enchanter models, bucket, fluid gauge and redstone icon render; vanilla offline multiplayer notifications overlap part of the view. The QA collection contains 67 events (27 pass, 40 info, no fail/warn), including summaries as well as assertions. All owned processes completed normally.

## Artifacts and remaining work

Candidate `8.2.14.41-1.20.1+32e89eb8e9` records the pre-cycle base. Own class counts are 251/1,262/69, all major 61. Recursive production-jar inspection finds no GameTest, registry-test, JUnit, QA or window-guard/probe entries.

| Artifact under its module's `build/libs` | SHA-256 |
| --- | --- |
| `endercore-8.2.14.41-1.20.1+32e89eb8e9.jar` | `c6df5dfcb3e2e88360f4561d9bfc0e18cb2f9f9bf0f0f8d0ad907e03b0031c3a` |
| `enderio-8.2.14.41-1.20.1+32e89eb8e9.jar` | `1938b9aa6463fab6cbb4f8d61223b3bf18527271c1567564b32011ed46d53381` |
| `enderio-modded-conduits-8.2.14.41-1.20.1+32e89eb8e9.jar` | `a7f6ecf45bfe31da74a264fb74da596bdc2b8020f04bccf3b89ed7deb6a1fab9` |

Evidence lives under `build/port-evidence/` with prefix `cycle-042-`: `fluid-repro.log/xml`, compile and intermediate lifecycle logs, `fluid-destruction-repro.log`, `waterlogging-crash.txt`, `fluid-waterlogging-fixed.log`, `native-drop-repro.xml`, `fluid-drops-fixed.log`, `build.log`, `final-test-results/`, `test-results.json`, `artifacts.json`, packaged preparation/run outputs and `qa/`. Native reports from successful intermediate runs can be superseded by subsequent runs; the initial failing XML and final full-build XML are copied separately. Intentional missing-graph recovery produces the existing recovery warning.

Next address the analogous Mekanism chemical execution remainder and exercise retained resources over actual disk-backed conduit restarts/topology transitions. Full feature/integration/client parity, historical-save migration and timestamped-manifest reproducibility remain incomplete. No feature was disabled, no release was published, and the full goal remains active.
