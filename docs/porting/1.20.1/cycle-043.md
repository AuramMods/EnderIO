# Cycle 043 — conserve Mekanism chemicals during conduit transfers

The preceding cycle fixed rejected fluid transfers in commit `0eef7f55f`. This cycle fixes the equivalent loss in Mekanism chemical conduits and reproduces a separate bundle import and chunk-save failure in the packaged server.

## Reproduction and implementation

The Forge GameTest server loaded the pinned Mekanism 10.4.16.80. The test mod attaches the pinned Mekanism capability interfaces to fixture barrels and controls their execution results. The production graph and ticker connect the barrels. Every source holds 10,000 units and refuses insertion. The destination advertises capacity for 3,750 units, then accepts 1,250 or zero.

Before the fix, the partial gas/pigment transfers left only 7,500 units across source and destination. The fully rejected infusion/slurry transfers left 6,250. The initial test compile needed an explicit generic attachment event type, then the native run executed 22 tests with those four specific failures. The failing XML is preserved.

The chemical ticker now retains the entire actual extraction in persisted node data, offers it to the destination and removes the amount accepted. The source budget counts the extraction, and each path budget counts its deliveries. A later eligible update drains retained resources first and leaves the source alone for that update. The retry passes through existing channel, redstone and insertion-filter rules. The unused old transfer helper could discard remainders, so the ticker no longer exposes it.

Chemical node data preserves the native family, registry ID, face and long quantity in network and bundle saves. The existing conduit item hooks carry pending chemicals through native drops and placement. A tier upgrade keeps the live node's retained data without returning a second copy in the lower-tier item. The node-data registry entry is created only when the Mekanism module loads.

## Verification

Eight new native addon cases pass.

- Partial gas and pigment rejection and complete infusion and slurry rejection retain the exact remainder. A second partial execution and a final complete retry deliver one batch without draining the source again.
- A forced link disconnect splits the actual graph. The source node retains oxygen until the link reconnects, then delivers once to the new destination.
- Infusion survives native unload/reattach callbacks. A filter allowing gas and disabled extraction each hold the infusion until the route allows delivery.
- A slurry tier upgrade retains the batch and returns one lower-tier item without retained slurry.
- The large-rate pigment case retains `Long.MAX_VALUE - 7` units. The network-node codec, native block destruction, vanilla ItemStack NBT round trip and native item placement preserve the precise amount. Successful retry clears ownership.

The full build passes **78 ordinary JUnit and 405 native GameTests**. The results contain no failures, errors or skips. Ordinary unit counts are 61 core, 16 main CPU and one addon. Native counts are 170 core, seven Mekanism adapters, 115 main registry/Jupiter, 87 main gameplay and 26 addon. Existing unit results may be reused when up to date. The coverage comparison confirms all 87 discovered gameplay tests executed and passed.

A clean packaged installation with no optional mods passes all 16 tank, save and reload assertions over three headless dedicated-server processes. It uses the same production main jar listed below. This exercises optional-module absence and base gameplay.

## Packaged disk probe

A separate official Forge installation starts the same bundled Ender IO jar with the pinned Mekanism jar as its only optional mod. The probe tries to create a saved-state fixture using native `/setblock` at `0 80 0` with both conduit IDs and two `NodeData` entries. It supplies 750 mB of tagged water and 2,500 units of oxygen. The server accepts the command and displays both conduit IDs, but `/data get block` immediately reports `NodeData: []`. During shutdown the same chunk reports a save failure because `onChunkUnloaded` tries to detach a missing live node. The probe exits before its intended second server process, so it does not verify disk restart. The experimental flat settings also produced a vanilla missing-layers diagnostic. The process stopped and its logs and fixture remain in the isolated installation.

The block type and NBT were loaded on an already constructed block entity, while current graph attachment happens in `setLevel`. Reconcile live bundle data with its graph and verify valid updates and failure behavior in a dedicated cycle. Use normal item placement for the retained-resource disk test, so the fixture starts with an attached graph created by the actual item path.

## Commands and artifacts

Commands executed from the repository root.

```sh
./gradlew :enderio-modded-conduits:runGameTestServer --console=plain
./gradlew :enderio-modded-conduits:compileJava --console=plain
./gradlew build --continue --console=plain
python3 scripts/porting/packaged_server_smoke.py \
  --name cycle-043-no-optional \
  --java /Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java \
  --installer build/port-runtime/forge-47.4.28-installer.jar \
  --jar enderio/build/libs/enderio-8.2.14.42-1.20.1+0eef7f55f1.jar \
  --port 25644
python3 build/port-evidence/cycle-043-conduit-disk-probe.py
```

Candidate version `8.2.14.42-1.20.1+0eef7f55f1` identifies the pre-cycle commit. The three artifacts contain 251, 1,262 and 71 own classes at Java 17 major version 61. Recursive jar inspection found no QA, JUnit or gameplay fixture entries.

| Jar under its module's `build/libs` directory | SHA-256 |
| --- | --- |
| `endercore-8.2.14.42-1.20.1+0eef7f55f1.jar` | `2918bbc30e47326f700f122b090377a54d41619a744516e35ecffa46e04f6911` |
| `enderio-8.2.14.42-1.20.1+0eef7f55f1.jar` | `4d7fdbddf1b822122f0d4b982ade60fe99fc11d18beed1d885f65da41ffff543` |
| `enderio-modded-conduits-8.2.14.42-1.20.1+0eef7f55f1.jar` | `a878d813ef3d0c2e92ce00221743cab6a09f9b8b36c3b109ca12af7e726e093e` |

Local evidence beneath `build/port-evidence/` has the `cycle-043-` prefix and includes reproduction logs/XML, targeted and full build logs, final XML/JSON results, artifact audit, packaged absence results and the disk probe script/log/report. Packaged server logs and worlds are in `build/port-runtime/cycle-043-{no-optional,conduit-probe}`. All processes completed headlessly.

True disk and chunk reload of retained resources, the live bundle import defect, full integration/client parity, migration and reproducible packaging remain open. The full goal remains active.
