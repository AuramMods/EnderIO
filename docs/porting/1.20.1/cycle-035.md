# Cycle 035: native gameplay tests and fluid-tank reloads

Starting commit: `b8e093ee9`. Destination: local `1.20.1`. The prior turn made verified progress; the tree was clean and no additional on-disk instructions/protocol or CodeGraph index was present.

## Restored gameplay coverage

All **58 enabled baseline gameplay tests** now run on native Forge. This corrects the earlier tracker shorthand of 43 dynamic registrations: the suite also contains methods using shared fixtures. The immutable baseline inventory's 58 gameplay method IDs exactly match the executed baseline subset. The already-commented cold-fire crafting scenario remains unexecuted; its active expiration scenario runs.

The Ender IO test mod replaces NeoForge framework initialization with native GameTest registration/reporting. Existing `@GameTest` settings, method names, sequences and assertions remain. Project-owned `GameplayTest` descriptors bind fixtures to native callbacks; `FixtureBuilder` creates native structure NBT and uses the public loader; the helpers compose a native `GameTestHelper` and access real Forge capabilities. Shared fixtures live in the test namespace and install through the native structure manager before tests start. Test data moves to 1.20.1's plural recipe/tag directories. Native fluid NBT replaces test-only components, and interaction, enchantment, recipe and Java APIs match the target version.

The first server run executed 43 passing tests because Forge filtered out 15 shared-template cases still using the production namespace. After fixing the fixture namespace, all 58 ran and passed. A **post-run** verification task now compares the actual discovered method names with actual XML execution records. It does not prevent the server from running. A deliberate namespace-filtering probe ran 60 successful cases out of 62 discovered, then correctly failed Gradle with the two missing capacitor-bank cases named. The probe change was restored before final checks. Each gameplay run clears its owned prior report/discovery files, so stale output cannot satisfy verification; the final cleanup-enabled profile also passes.

`check` now includes the main gameplay profile and its result reconciliation. CI invokes `./gradlew check --continue`, covering all unit/native profiles and retaining independent results after a failure. Its change triggers also cover resources/build configuration. The exact CI command passes locally; no remote CI run or push occurred.

## Reproduced production defects

Restarting the gameplay server exposed `FluidTankBlockEntity` failures during native chunk loading. Its fluid-content callback called `level.sendBlockUpdated` before `BlockEntity.loadStatic` had assigned a level. Minecraft logged a null-pointer exception and skipped the tank block entity. Two new regressions, one per tank variant, reproduced this as failed native loads.

The callback now sends world updates only when a level exists. The load regressions then passed, but two follow-up cases showed that saved recipes stayed idle: the inventory/fluid callbacks had run without world access and left the transient recipe cache empty. The tank now rebuilds its recipe and empty-tank state in `onLoad`, after world assignment.

The four added cases verify both standard and pressurized tanks: native loading before level assignment retains fluid amount/NBT and item inventory, followed by successful transfer after attachment; a saved water/concrete-powder recipe resumes and consumes exactly its input/fluid. The initial load reproduction failed 2 of 60 cases; the recipe reproduction failed 2 of 62; the final suite passes all 62. The restarted final gameplay log contains no tank-load errors. This is concrete native block-entity load coverage, not a claim that every feature's disk-backed restart persistence is complete.

## Actual results

`./gradlew build --continue --console=plain`, final `./gradlew check --continue --console=plain`, and final `./gradlew :enderio:jar :enderio:reobfJar --console=plain` all pass.

| Profile | Executed | Failures/errors | Skips |
| --- | ---: | ---: | ---: |
| Core JUnit | 61 | 0 | 0 |
| Main CPU JUnit | 16 | 0 | 0 |
| Addon early-initialization JUnit | 1 | 0 | 0 |
| Core native GameTests | 170 | 0 | 0 |
| Native Mekanism adapter tests | 7 | 0 | 0 |
| Main registry tests, Jupiter inside native GameTests | 112 | 0 | 0 |
| Main native gameplay tests | 62 | 0 | 0 |
| Actual-mod addon GameTests | 18 | 0 | 0 |

Totals: **78 ordinary JUnit executions and 369 native GameTest executions**, with the in-server Jupiter cases counted once. Counts describe profiles; some source-bridge cases overlap. Core data-map negative tests intentionally log their malformed-input errors; those are separate from the resolved tank errors. No stale historical test report is included.

The restored gameplay cases cover machine processing/energy, basic capacitor-bank split/merge, conduit routing/priority/tiers, item/fluid data, break/wrench/drop behavior, redstone and prior regression scenarios. The feature matrix records this as **partial** evidence on corresponding rows, not complete family parity. Test configuration runs without the optional integrations loaded by the addon profile. All servers were isolated, headless and stopped after their runs. No client was launched.

## Evidence and candidate artifacts

Ignored evidence under `build/port-evidence/` includes `cycle-035-compile-*.log`, `cycle-035-native-first.log`, `cycle-035-native-all.log`, the two tank reproduction logs/XMLs, `cycle-035-native-fixed.log`, `cycle-035-full-build.log`, `cycle-035-coverage-probe.log` and XML, `cycle-035-final-check.log`, `cycle-035-gameplay-final.log`, `cycle-035-packaging.log`, `cycle-035-baseline-test-inventory.json`, `cycle-035-test-summary.json`, `cycle-035-full-test-results/`, `cycle-035-final-test-results/` and `cycle-035-artifacts.json`. Tracked usage documentation lives under both main test source sets.

Candidate version: `8.2.14.34-1.20.1+b8e093ee9b`. Own classes remain Java 17 major 61 (251 core, 1,260 main, 69 addon). Recursive jar inspection finds no gameplay/registry test classes, test recipes, inventories or JUnit libraries in distributable main/nested jars.

| Artifact under its module's `build/libs` | SHA-256 |
| --- | --- |
| `endercore-8.2.14.34-1.20.1+b8e093ee9b.jar` | `d80dd921e4b7fe51d18f42e4b44688ff68c4d0ae0e3ae29a17a6586258de9550` |
| `enderio-8.2.14.34-1.20.1+b8e093ee9b.jar` | `ba1a8683805bbe525d9de02837033a144c575edc013f1b3acaa2fae1b147c33e` |
| `enderio-modded-conduits-8.2.14.34-1.20.1+b8e093ee9b.jar` | `cbac3dc15c4c9d3238673699928787b8af7580051cb2e2fde28dd501de88f87b` |

The [upstream fixture builder](https://github.com/neoforged/NeoForge/blob/1.21.1/testframework/src/main/java/net/neoforged/testframework/gametest/StructureTemplateBuilder.java) was consulted for existing template semantics; target APIs were inspected in the actual pinned Forge sources and exercised by execution. No other repository was cloned.

## Remaining work

Clean packaged Forge server/client installations, a directly verified non-activating client setup, GUI/rendering and real multiplayer remain unverified. Complete feature parity, historical saves and broader chunk/world/server persistence still need evidence. Chemical execution-time insertion remainder conservation and the fluid conduit rate audit remain unresolved. Timestamped manifests still prevent a byte-reproducibility claim. Datagen was not rerun for these test/lifecycle changes; cycle 033 remains its latest execution evidence.

Next cycle: exercise a clean packaged dedicated Forge installation, then expand the remaining transfer/persistence coverage. Keep the full port goal active.
