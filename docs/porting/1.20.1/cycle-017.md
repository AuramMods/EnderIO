# Cycle 017 — Native block persistence and carried state

Status: progress, not completion. Starts at `1d9b564e9`, following verified progress in cycle 016. All three shipped modules remain in scope; main Ender IO still does not compile and the addon remains blocked by it.

## Changes

- Converted world lifecycle entry points across 31 block-entity source files: native `load(CompoundTag)`, `saveAdditional(CompoundTag)`, `getUpdateTag()` and the core's native synced-save hook. Existing helper serializers receive the current level's registry access where available. This also reaches conduit, paint, travel-anchor and Enderface entry points; their remaining implementation/API errors and registry-dependent load ordering are **not** resolved merely by changing these signatures.
- Replaced all 23 newer block-entity component read/write implementations with typed native item NBT hooks on `EnderBlockEntity`. Inventory state uses the existing tested machine inventory serializer under `enderio:container`; energy, fluids, souls, I/O, redstone, mode, grinding ball and range state continue using their existing Ender IO typed keys. No newer platform component classes are emulated.
- Added the production `BlockItem` placement mixin. It applies carried state after vanilla `BlockEntityTag` loading and before the original `setPlacedBy` callback. The original callback still runs once. Plain items and historical items containing only native `BlockEntityTag` skip the typed-key import, preserving their native load result. The new mixin's actual placement path is exercised by GameTests, and its SRG mappings are present in the reobfuscated jar.
- Added `endercore:copy_block_entity_data`, a native registered loot function. It exports the state declared by the block entity's item hooks, with an optional ID selection. It preserves unrelated destination item tags, copies mutable NBT and refreshes item capabilities. Explicit values override an earlier removal marker. Ordinary drops omit world-only NBT; native `saveToItem` still retains other block-entity state while moving carried fields into their typed item keys.
- Updated the machine loot provider and moved 35 corresponding generated tables from `loot_table` to `loot_tables`. Counts, pools, conditions, result IDs and explicit powered-spawner include semantics are preserved. The inventory include ID maps from the former vanilla component to the native inventory key. The remaining resource tree and complete datagen still require conversion.
- Removed redundant native fluid state from full machine item saves, and redundant solar soul/energy fields, so changing or draining carried item state cannot reveal an old serialized copy on placement. The removed-fluid lifecycle regression passes. Actual machine-specific capacity/network behavior remains unverified.
- Replaced deprecated I/O and redstone attachment storage in the legacy machine base with instance fields and native NBT. A migration helper copies earlier attachment fields into native fields when those native fields are absent, including action range and range visibility. Native state takes precedence, and the helper does not mutate the input tag. The old attachment container/key shape was checked against [NeoForge's attachment implementation](https://raw.githubusercontent.com/neoforged/NeoForge/1.21.1/src/main/java/net/neoforged/neoforge/attachment/AttachmentHolder.java). This is support for serialized fields, not a promise that Minecraft 1.21.1 worlds can be downgraded.
- Added native I/O, redstone and range codec operations. I/O and redstone also read the actual historical Ender IO 6.x formats: numeric direction names with mode-ordinal bytes, and the four redstone ordinals. These layouts and enum order were read from reference commit `18ee1b17bf6879298e911fff751204b07f2e4228`. Invalid numeric modes are rejected. This limited migration test does not establish full 6.x world compatibility.
- Converted the newer powered-machine energy storage's NBT interface to native Forge integer tags and rejects negative saved charge. Full energy transfer behavior for this newer implementation still needs actual powered-machine tests.

The shared base has explicit item import/export extension hooks. Their default behavior leaves block entities without carried data unchanged; no fake Minecraft/NeoForge classes, disabled modules or substitute machine implementations were introduced. The source bridge now compiles **67 actual main-module files**, adding I/O configuration, redstone control and the migration helper to cycle 016's 64.

## Actual verification

Initial EnderCore compilation passed. The first test command failed to compile the new fixture because native `LootContext.Builder.create` takes a nullable resource ID rather than an `Optional`. Correcting the fixture allowed the combined command to run:

```sh
./gradlew :endercore:check :endercore:jar :endercore:reobfJar :enderio:compileJava --continue --console=plain
```

The first runtime pass passed all **105** GameTests. The final command, after adding the removed-fluid regression and range migration assertions, passed **106**. It exits **1 solely for main compilation**, at the normal **400-error cap**. Core check, jar and reobfuscation succeed.

| Suite | Discovered | Executed | Passed | Failures/errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| EnderCore JUnit | 39 | 39 | 39 | 0 | 0 |
| Native headless Forge GameTests | 106 | 106 | 106 | 0 | 0 |

Seven new GameTests cover:

1. Native `Block.getDrops` loading a real loot-table resource, native item save/load, and `BlockItem.place` invoking the production mixin. Inventory count 257, nested Forge item capability charge, fluid quantity/NBT, I/O and redstone survive. The placement callback observes imported energy, and placed/source inventories are independent.
2. Native `saveToItem` retaining world-only fields while removing redundant carried fields, followed by an item energy change and placement that uses the changed value.
3. A historical-style item containing only `BlockEntityTag` loading through native placement without being reset by absent typed item keys.
4. Removing carried fluid after a full item save, with no fluid restoration from an old block-NBT copy and no change to unrelated carried resources.
5. Native `saveWithFullMetadata` and `BlockEntity.loadStatic` dispatching the actual base hooks, followed by installation in the level.
6. Native loot-function JSON serialization/deserialization, explicit field selection, preservation of unrelated item NBT and overriding a removal marker.
7. Historical I/O and redstone fixtures, current-format round trips, invalid mode rejection, attachment-to-native field migration, range/visibility, native-field precedence and unchanged source/unrelated data.

The lifecycle fixture is a minimal registered test block entity extending the **production `EnderBlockEntity`**, with the **actual main `MachineInventory`** and native I/O/redstone codecs. It exercises actual Forge loot, world factories and block placement, but does **not** load the complete main machine classes, run a real alloy/sag/spawner/capacitor-bank workflow, unload chunks, reopen a world or restart a dedicated server. Those requirements remain incomplete. The removed-fluid test proves the shared save/import contract with the fixture; complete main fluid-machine behavior remains unverified.

All servers used the isolated headless `nogui` profile, saved and stopped cleanly. No graphical client, audio system or narrator was launched.

Evidence in `build/port-evidence/`:

- `cycle-017-core-first.log`: successful core compilation.
- `cycle-017-tests-first.log`: new fixture compilation failure before GameTest execution.
- `cycle-017-check-second.log`: 105 passing GameTests and capped main compilation.
- `cycle-017-check-final.log`: final 106 passing GameTests and capped main compilation.
- `cycle-017-tests.json`: actual final XML report counts and test names.
- `cycle-017-loot-migrations.json`: structural comparison of all 35 converted tables against their prior versions, allowing only the intended path/function/include-ID changes. This comparison is not proof that the complete main resource pack loads.

Supplemental compiler operation:

```sh
./gradlew :endercore:compileJava -I scripts/porting/compiler-diagnostics.init.gradle :enderio:portCompilerArguments --no-configuration-cache --console=plain
/Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/javac -J-Xmx4G @build/port-evidence/main-javac.args
```

Argument export/core compilation passes. Supplemental compilation exits **1**, with **1,067 errors and 100 reported warnings**, down from 1,233 in cycle 016. The first intermediate run reported 1,069. Evidence: `cycle-017-core-arguments.log`, `cycle-017-main-javac-first.log`, `cycle-017-main-javac-final.log`. These use actual main sources and compiler classpath, with annotation processing disabled; counts are not independent defects or full-build evidence. Full datagen cannot yet build through its main-module dependency.

Intermediate core artifact, named from this cycle's base commit:

- `endercore/build/libs/endercore-8.2.14.16-1.20.1+1d9b564e9f.jar`
- SHA-256: `5db18316f2dabe0182ae7df7022b8c14f21b3fd1f8d20bb8b447a2de36fea233`
- All 222 classes are version `61` (Java 17); test fixtures and bridged main classes are absent.
- Native placement and callback SRG mappings are present in `endercore.refmap.json`.
- Inspection record: `build/port-evidence/cycle-017-artifacts.json`. Packaged-runtime validation remains pending.

`git diff --check` passes. All owned command handles reached terminal states. No push, release or upstream configuration change was performed; recovery stash/refs are preserved.

## Remaining work and next cycle

Port native block interaction and wrench/menu entry points, then move complete machine classes toward compilation and direct runtime tests. The newer `ItemInteractionResult`/split item-use methods still block machine bases. Finish actual held-recipe invalidation, newer powered-machine energy behavior and block-specific state handling, especially capacitor-bank/solar networks and fluid machines.

Audit registry-dependent load paths that now enter through native `load` before a level is attached. In particular, conduit data must defer dynamic-registry resolution until its owning level is available; an empty registry context is not sufficient to establish correct world loading. Remaining main codecs and attachments/data maps still have compiler/runtime work.

Full resource/datagen conversion, client rendering/UI, integrations, historical-world fixtures, real persistence/restart and packaged single-player/dedicated multiplayer validation remain incomplete. The full goal remains active and unchanged.
