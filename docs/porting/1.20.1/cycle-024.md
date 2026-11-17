# Cycle 024: transient level services and native obelisk/fire hooks

## Purpose and changes

Started clean on `1.20.1` at `532dc2a4d`. Git had no merge/rebase or unmerged paths; the earlier recovery refs remain preserved and the branch still has no upstream. No remote operations were performed.

- Added production `ServerLevelState<T>` using a real, automatically registered Forge capability attached to server levels. Each level owns an identity-keyed collection of lazy services. Read-only lookups do not invoke service factories, failed creation does not install a value, and different dimensions cannot share services through a static cache.
- Capability invalidation clears service references before notifying listeners and rejects recreation through a retained storage reference. The native `LevelEvent.Unload` handler also invalidates this provider: the pinned Forge 1.20.1 level-close path does not call `invalidateCaps()`. The provider intentionally does not serialize, matching the four transient source attachments.
- Replaced `EIOAttachments` with `EIOLevelStates` for aversion, inhibitor, relocator and fire-crafting managers. Removed obsolete NeoForge attachment registration. The three deprecated serialized machine attachment fields already migrate through `LegacyMachineData`; that implementation and its existing regression coverage remain intact. This is not a removal of saved redstone, I/O or action-range state.
- Converted aversion/relocator events to native `MobSpawnEvent.FinalizeSpawn`, retaining `setSpawnCancelled` semantics and natural-spawn restrictions. Manager queries avoid creating unused obelisk services. Spawn and teleport positions now use floor-based `BlockPos.containing`, preserving negative chunk boundaries.
- Adapted the relocator to Forge's boolean event-post return value and honor listeners' modified teleport coordinates. Inhibitor command exemptions remain unchanged.
- Obelisks join their manager during `onLoad`, after native load/level setup, and unregister on chunk unload or removal. Range changes and attached NBT/item updates refresh the existing index; unloaded entities cannot rejoin through a range change alone. A same-level reassignment preserves registration, while reassignment to another level removes the old entry. The index reference avoids recreating a manager merely to unregister.
- Made `ChunkBoundLookup.remove` idempotent, including removal before registration, repeated lifecycle cleanup and re-registration in a new range.
- Fire crafting uses the same native level-owned service. Fire water now extends `ForgeFlowingFluid`, routes placement through `ForgeEventFactory.fireFluidPlaceBlockEvent`, and uses the pinned native lava flammability checks (`ignitedByLava()` and sided Forge `isFlammable`). Its source/flowing variants and recipe behavior remain present.

No data-map fallback or fake platform classes were introduced. Reloadable/synchronized data maps are a separate remaining conversion.

## Verification

```sh
./gradlew :endercore:test :endercore:runGameTestServer :endercore:jar :endercore:reobfJar -I scripts/porting/compiler-diagnostics.init.gradle :enderio:portCompilerArguments --no-configuration-cache --console=plain
```

Final exit **0**:

| Suite | Discovered | Executed | Passed | Failed/errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| EnderCore unit tests | 57 | 57 | 57 | 0 | 0 |
| Native Forge headless GameTests | 131 | 131 | 131 | 0 | 0 |

Five new GameTests verify actual capability registration and attachment to native server levels, lazy creation and identity, overworld/Nether isolation, failed-factory recovery, listener ordering, invalidated-reference rejection, and fresh state after provider reattachment. Invalidation fixtures dispatch real independent attachment events through the Forge bus and invoke their native listener lists; they do not invalidate the shared test world. They exercise provider replacement, not a complete server restart. Two new unit tests cover repeated spatial removal and range rebuilding without retaining old chunk entries.

The first command (`:endercore:test :endercore:runGameTestServer :endercore:jar`) passed the 57 unit tests but the server failed before GameTest execution: the new tests shared a Java package with EnderCore across two named modules. Moving them into the existing separate GameTest package and exposing the capability storage API resolved the actual module-resolution error. The final run executes all 131 tests successfully. The 88-file main production test bridge is unchanged; actual main-mod obelisks, fire crafting and fire water are not included in that runtime coverage.

```sh
./gradlew :enderio:compileJava --console=plain
/Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/javac -J-Xmx4G @build/port-evidence/main-javac.args
```

Both final commands exit **1**, with **372 errors and 100 warnings**, down from 418 source errors. Normal compilation is now below its 400-error reporting cap. No diagnostics remain in the production files changed this cycle. Supplemental compilation uses the actual main classpath/source list but disables annotation processing; it is not proof of a successful build. Main and addon packaging remain incomplete.

Evidence under ignored `build/port-evidence/`:

- `cycle-024-core-first.log`, `cycle-024-core-final.log`, `cycle-024-tests.json`, `cycle-024-gametest-results.xml`.
- `cycle-024-main-gradle.log`, `cycle-024-main-javac.log`, `cycle-024-main-gradle-final.log`, `cycle-024-main-javac-final.log`, `cycle-024-error-counts.json`.
- `cycle-024-artifacts.json`, `cycle-024-commit-scout.txt`.

Reobfuscated core jar built before this cycle's commit:

- `endercore/build/libs/endercore-8.2.14.23-1.20.1+532dc2a4d1.jar`
- SHA-256: `f15c30dcc8af8fcf0d390c87b63185583e758d4723ae067f8431d44ddff177e5`
- 228 classes, all Java 17 class-file major 61; no test classes or bridged main-module classes.

The isolated GameTest server used `nogui` and `java.awt.headless=true`, completed and shut down cleanly. No client, visible window, audio or user world was used. This cycle has its own local commit; no push.

## Outstanding work

Actual obelisk spawn suppression/relocation, teleport inhibition, range/filter/energy behavior, chunk unload/reload, replacement, save/restart, and fire-crafting/fire-water recipes remain unverified in the full mod. Add those acceptance tests when the actual registrations can run. The shared capability tests do not establish these gameplay results.

Continue native reloadable/synchronized data maps (range extenders, vat reagents, grinding balls), then remaining content APIs, optional integrations, datagen and packaging. Preserve data-pack precedence, tags, removals, reload and client synchronization. The remaining compiler errors also include FTB Ultimine hooks, loot/advancements, farming, soul/spawn APIs and additional obelisk types. The prior conduit graph-reconciliation and strict optional-field audits remain outstanding. Client, packaged multiplayer and every unverified feature family remain required; the full goal is active and incomplete.

Sources inspected: [Forge 1.20.1 capabilities](https://docs.minecraftforge.net/en/1.20.1/datastorage/capabilities/) and the pinned Forge 47.4.28 sources for capability events, `MobSpawnEvent.FinalizeSpawn`, `LavaFluid`, `BlockEntity`, `ServerLevel`, `Level`, and `MinecraftServer`. The local pinned sources establish version-specific behavior.
