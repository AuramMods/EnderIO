# Cycle 029 — complete main Java compilation

## Objective and changes

Resolve the main module's remaining native API and optional integration compilation errors, preserve the implemented behavior, and run the next real build stages. The preceding goal turn made verified progress in commit `b7a0cb986`; this cycle began from its clean worktree on `1.20.1`. Applicable instructions were rechecked; no CodeGraph directory or manual-change protocol was present.

- Added a native `BaseSpawner` adapter exposing the powered spawner's actual block entity through `getSpawnerBlockEntity`. It posts one Forge finalize-spawn event, preserves event-mutated initialization parameters, distinguishes cancelled initialization from cancelled insertion, and leaves copied soul state uninitialized. Powered-spawner collision checks and no-AI movement now use native APIs; the temporary AI flag is restored in a `finally` block.
- Slicer tools use native `hurtAndBreak` with a machine-owned Forge fake player positioned at the slicer, preserving native Unbreaking, item damage hooks and break handling. This avoids using a newer overload unavailable on Forge 1.20.1 and avoids sharing the generic Minecraft fake player.
- Replaced the absent colored-particle option with a registered native spell effect retaining the experience rod's green color and independent motion. Added a native SAG dust particle with the 1.21.1 dust-plume constructor constants, color variation, gravity/friction changes and existing Minecraft sprite sequence. The official 1.21.1 client and mappings were downloaded and SHA-1 checked against Mojang's metadata; the relevant mapped bytecode was inspected. No game client was executed.
- Ported Enderface's interaction-range override to Forge's `canReach`/`canReachRaw` methods. Native menu distance helpers now recognize valid nearby Enderfaces while retaining block/entity identity checks. Core block-entity menus delegate to Forge's actual `Container` validity helper, retaining Forge reach attributes. A normal mixin covers `AbstractContainerMenu`; Forge's static `Container` interface overload uses a small Forge coremod because bundled Mixin 0.8.5 rejects interface injectors. The shipped coremod is tested against the actual native bytecode.
- Adapted Almost Unified to `AlmostUnifiedLookup`, Curios to its native optional capability, Jade server data registration to block-entity classes, and JEI fluid/enchantment/holder APIs to its Forge 1.20.1 API.
- Adapted LaserIO filters/cards to `BaseFilter`'s native NBT accessors and Forge item capability attachment. Fixed the reversed NBT-comparison toggle and preserved the offered fluid amount when a sample matches. Card filters read their current embedded filter on each query, avoiding a stale captured copy. Actual LaserIO gameplay still requires verification.
- FTB Ultimine 2001.1.8 has no newer block-selection/break-handler registration APIs. Added optional mixins at its actual `ShapeContext.check(BlockPos)` and per-block breaking call, retaining conduit-network/facade selection, partial-bundle removal, drops, Forge block-break cancellation, adventure restrictions, exhaustion/cost loop and cleanup. The native Minecraft call target is present in the generated reobfuscation refmap. Optional mixins are selected only when FTB Ultimine is present; the existing pinned Architectury dependency is now also on the compilation classpath. No integration was removed. Actual mixin application, protection interactions, shape previews and gameplay remain unverified.

## Verification

The complete main source set now passes the **normal Gradle Java/Mixin annotation-processing pipeline**, replacing the previous 74-error supplemental diagnostic report. This is compilation evidence, not packaged mod startup or feature parity.

Final successful command, exit **0**:

```sh
./gradlew :endercore:test :endercore:jar :endercore:reobfJar :enderio:compileJava --no-configuration-cache --console=plain
```

Native GameTests executed on the final applicable core/spawner production code in:

```sh
./gradlew :endercore:test :endercore:runGameTestServer :endercore:jar :endercore:reobfJar :enderio:compileJava -I scripts/porting/compiler-diagnostics.init.gradle :enderio:portCompilerArguments --continue --no-configuration-cache --console=plain
```

That command ran all 164 GameTests successfully but exited **1** because the new unit test initially used a JUnit assertion introduced after this repository's pinned version. The assertion was changed to an equivalent type assertion and cast; the final command above executed the complete unit suite successfully. The earlier main build also exposed the unsupported interface injector and a missing compile-only Architectury dependency; both were corrected rather than suppressed.

| Suite | Discovered | Executed | Passed | Failed/errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| EnderCore unit tests | 61 | 61 | 61 | 0 | 0 |
| Native Forge headless GameTests | 164 | 164 | 164 | 0 | 0 |

Four new GameTests execute the actual spawner adapter: one event/one initialization with owner and modified parameters; initialization cancellation still allowing insertion; spawn cancellation blocking both preparation and actual Forge insertion; and copied mob state surviving without reinitialization. The bridge includes 116 actual main production files and three datagen files, confined to test output.

The new CPU-only unit test executes the actual shipped coremod through Forge's pinned Nashorn 15.4 engine, selects the method from the real native `Container` class, applies the transform and verifies its stack flow with ASM. It does not substitute for actual menu interaction, complete mod loading, transformed-player behavior or multiplayer. Nashorn was added only to the unit-test runtime, not release dependencies.

The next real build stages were attempted:

```sh
./gradlew :enderio:compileJava :enderio:compileDatagenJava :enderio-modded-conduits:compileJava :endercore:jar :endercore:reobfJar -I scripts/porting/compiler-diagnostics.init.gradle :enderio:portCompilerArguments --continue --no-configuration-cache --console=plain
```

Exit **1**: main compilation passed; datagen stopped at the **400-error cap**, primarily unported loader/generator/recipe APIs. The addon stopped at **10 syntax errors** from Java 21 record patterns in `ChemicalStackSyncSlot`; this early failure does not measure the remaining addon API work. Neither stage was disabled or excluded from the attempted build. The full distributable remains unavailable because the main jar bundles the unfinished addon.

Evidence under ignored `build/port-evidence/`:

- `cycle-029-main-first.log`, `cycle-029-build-second.log`, `cycle-029-build-third.log`, `cycle-029-final.log`, successful `cycle-029-final-verified.log`.
- `cycle-029-tests.json`, `cycle-029-gametest-results.xml`, `cycle-029-build-summary.json`, `cycle-029-artifacts.json`, `cycle-029-commit-scout.txt`.
- Pinned FTB source archives and `cycle-029-ftb-bytecode.txt`; official client/mappings/metadata in `cycle-029-minecraft-1.21.1/`.

Reobfuscated core artifact built before this cycle's commit:

- `endercore/build/libs/endercore-8.2.14.28-1.20.1+b7a0cb9860.jar`
- SHA-256: `48676236d404aeaa294e7786803281b5de4e4e645ca19c039197c9c359087e07`
- 245 classes, all Java 17 major 61; no test or bridged main/datagen classes.
- Main compilation produced 1,201 class files, all major 61. These are development outputs, not a distributable jar.

All native servers used the isolated headless profile, saved and shut down. All cycle processes completed. No client window, audio, input capture, user world, push or release was used. This cycle has its own local commit.

## Next work and limits

Port full datagen and resource layouts/schemas, then the addon APIs and remaining baseline tests. In particular, Mekanism 1.20.1 has separate gas/infusion/pigment/slurry APIs rather than the newer unified chemical API; preserve all supported categories through an appropriate native adapter. Refined Storage and AE2 must likewise use the pinned versions' actual network APIs. Removing record-pattern syntax alone is not an addon port.

Run complete packaged startup once all modules/resources are ready, then verify optional integrations both absent and present. The new FTB/Enderface mixins, coremod runtime invocation, native tool hooks and particles still require actual gameplay/client checks. Powered-spawner task scheduling, costs, copied souls, no-AI physics and persistence still need complete-machine verification beyond the tested event adapter. XP-vial merged-orb accounting remains unresolved.

Full feature-family coverage, rendering/UI, dedicated multiplayer, transfer conservation, split/merge/unload/reconnect/restart behavior, historical save migration and final artifacts remain required. No feature-family status is promoted to complete. The full goal remains active and incomplete.

Sources: pinned Forge 47.4.28 source artifact; [FTB Ultimine 2001.1.8 source artifact](https://maven.ftb.dev/releases/dev/ftb/mods/ftb-ultimine/2001.1.8/ftb-ultimine-2001.1.8-sources.jar); pinned integration jars and their bytecode; official Minecraft 1.21.1 client/mappings selected through [Mojang's version manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json).
