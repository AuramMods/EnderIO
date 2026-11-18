# Cycle 044 — attach imported conduit nodes and verify disk persistence

Cycle 043 retained rejected Mekanism chemicals. Its packaged `setblock` probe imported conduit IDs, left `NodeData` empty and failed to save the chunk during shutdown. This cycle fixes that live import path and exercises actual chunk eviction and dedicated-server restarts on the shipped jar.

## Reproduction and changes

The native gameplay tests reproduced three failures after correcting a fixture that put stone at the test placement position. The `setblock` command returned success without attaching a graph node. A live import that removed an energy conduit left its old node attached. A rejected import restored the live collection, but the rejected payload appeared in the next save. The first run with the fix then exposed the malformed bundle persisted by the earlier reproduction. That owned test world was moved to `build/port-evidence/cycle-044-poisoned-game-test-world` before the clean rerun.

A server-side load on an attached bundle now reconciles the incoming conduit list with the live graph. It detaches removed nodes, initializes missing nodes through the existing saved-data recovery path, applies explicitly changed node data with conduit lifecycle callbacks and retains unchanged node identity. It refreshes connection state and splits existing graph links when the imported face is disabled. Initial chunk loading continues to claim saved network nodes through `setLevel`.

Invalid live node data is rejected during decoding and leaves the previous live bundle intact. The base registry-aware block entity now defers NBT only when it has no level. A malformed live load can no longer leave a rejected tag waiting to be used by the next save.

Five added GameTests cover actual `setblock` with tagged fluid, removal from the live graph, disabled-face graph splitting, explicit node-data replacement and rollback after invalid conduit or resource NBT. The full build passes **78 ordinary JUnit and 410 native GameTests**, with zero errors, failures or skips. Unit counts are 61 core, 16 main CPU and one addon. Native counts are 170 core, seven Mekanism adapters, 115 main registry/Jupiter, 92 main gameplay and 26 addon. The coverage task confirms all 92 discovered gameplay cases executed and passed. The run can reuse valid up-to-date unit results.

## Packaged dedicated-server results

The new runner installs an official Forge 47.4.28 server and supplies the bundled Ender IO jar and Mekanism 10.4.16.80. It imports a mixed fluid/chemical conduit bundle into an isolated world at `1024 80 1024`, outside spawn chunks. The fixture contains 750 mB of water with `Conservation:42` NBT and exactly 4,294,967,297 units of Mekanism oxygen.

| Process | Observed results |
| --- | --- |
| First server | Imported node data matches the fixture. Forceload removal yields an actually unloaded position, as measured by native `execute unless loaded`. Forceload re-add loads the position, and the block NBT retains both exact batches. |
| First restart | The same disk world retains both batches. Native block destruction removes the bundle and produces exactly one item for each retained resource. |
| Second restart | The bundle position remains air. Both item entities still exist, each with count one and exact retained-resource NBT. |

All **14 server assertions** pass. Each process saves, exits zero and reports no ERROR/FATAL lines. The test uses an explicit retained-state fixture, while cycles 042–043 exercise actual transfer rejection, retry and carried placement in native GameTests. This cycle used headless servers throughout. Dedicated client gameplay for these conduit families, mixed-network behavior across dimensions, broader integrations and full parity remain open.

## Commands and evidence

```sh
./gradlew :enderio:runGameTestServer --console=plain
./gradlew build --continue --console=plain
python3 scripts/porting/packaged_conduit_persistence.py \
  --name cycle-044-conduit-persistence \
  --java /Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java \
  --installer build/port-runtime/forge-47.4.28-installer.jar \
  --jar enderio/build/libs/enderio-8.2.14.43-1.20.1+168a197e72.jar \
  --mekanism /Users/cyberpwn/.gradle/caches/modules-2/files-2.1/maven.modrinth/mekanism/uxe1WQp4/b7f12ae710888b01746507a0aa8cb375f5897158/mekanism-uxe1WQp4.jar
```

Evidence lives beneath `build/port-evidence/` with the `cycle-044-` prefix. It includes the repro logs and XML, the preserved malformed fixture world, the clean rerun, the full build, the final XML/JSON test results, the artifact audit and the packaged report. The installer output, three actual server logs and their shared world are under `build/port-runtime/cycle-044-conduit-persistence`.

Candidate version `8.2.14.43-1.20.1+168a197e72` identifies the pre-cycle base. All own classes are Java 17 major version 61. The audit found no QA, JUnit or fixture content in the production jars.

| Jar under its module's `build/libs` directory | SHA-256 |
| --- | --- |
| `endercore-8.2.14.43-1.20.1+168a197e72.jar` | `c4a9350cdeceddf5ba747b1a06b7a790675058e49c41e1397393ed7cdb5d716d` |
| `enderio-8.2.14.43-1.20.1+168a197e72.jar` | `0841cee76e99df2a9841479825c68c60e9e7c435b8505bab6292168abf8bf535` |
| `enderio-modded-conduits-8.2.14.43-1.20.1+168a197e72.jar` | `42dcdc3c20861409f4b15444410c8f0edca863ba6e274d3b321473f0a45f45dd` |

The Mekanism runtime jar SHA-256 is `3b0d191fa4a45e662f9725991d76a30192f30186abb2ba694b873b5f7b9358d2`. Packaging reproducibility, historical migration and the remaining gameplay and integration matrix are still required. The full goal remains active.
