# Cycle 033 — real conduit networks, chemical budgets and recipe loading

## Starting evidence and changes

Continued from clean `7f794de6f` on `1.20.1` after rereading the full goal objective and tracker. Cycle 032 made verified progress: generated resources, distributable build tasks and initial actual-mod server tests. There was no active merge, CodeGraph directory or manual-change protocol. The goal remains incomplete.

- Added pinned **Patchouli 1.20.1-84.1-forge**, Modrinth version `8Qyw08ld`, to development integration runtimes alongside LaserIO. The publisher's version API confirms Minecraft 1.20.1 and Forge. The real LaserIO book recipe now loads, matches a book plus logic chip, and assembles the correctly identified `patchouli:guide_book` with `patchouli:book=laserio:laseriobook`. This dependency is not bundled into the release jar or made mandatory for users without LaserIO.
- Removed `data/enderio/recipes/darksteel_upgrade.json`, an orphan resource naming the absent `enderio:dark_steel_upgrade` serializer. Inspection of the fixed baseline's Java and resources finds the same invalid JSON but no serializer or implemented upgrade recipe. This removes an already nonfunctional resource, not an implemented feature. The baseline inventory remains immutable; this one resource ID is an explicit orphan exception to the prior normalized 4,173-ID inventory.
- Added actual placed-conduit tests with native AE2 grids and RS controllers/disk drives, including splitting, replacement, disabled faces and serialization/reload. They use the real production block item, bundle, capability providers, node managers and integrations. The earlier managed-node test double is not used by these new grid/network tests.
- Added four native chemical transfer tests using Mekanism's actual ultimate chemical tank block entities, separately covering gas, infusion, pigment and slurry. Each reproduced only **750** units transferred in a five-tick update for a **750-per-game-tick** conduit. Path limits now use the same tick interval as extraction budgets, allowing **3,750** units per update when endpoints can accept it.
- A native branching test then reproduced **4,750** units extracted from a **3,750** budget after a partially full first destination accepted 1,000 units. Each subsequent path is now capped by the remaining extraction budget, and the simulated chemical amount is reduced after a successful transfer. The test now transfers exactly 3,750 and conserves the source plus destination amounts.
- Used saturating long multiplication for rate budgets. A real test-only datapack conduit with rate `3074457345618258602` transfers its available contents instead of overflowing into a negative budget.
- Datagen exposed an intermittent `ConcurrentModificationException` in Forge's shared key-mapping lookup while AE2 constructed client hotkeys during parallel mod loading. Ender IO's key mappings are already lazy and registered through the native key-mapping event. Datagen now uses its own `run/datagen` directories, with Forge's supported `maxThreads=1` setting. `PrepareTestProfile.forgeLoadingThreads` defaults to preserving other profiles' settings; only datagen requests serial loading. The profiles remain headless, muted and narrator-disabled. This does not change normal client or server loading settings or omit integrations.

## Actual verification and reproduced failures

The initial network test passed with a real RS creative controller connected to a disk drive through three placed conduits. Additional native tests passed for ME grid split/merge, RS face disabling/reconnection, and both integrations' native block-entity NBT reload and owner retention. These reload tests explicitly call the block-entity unload callback and use `BlockEntity.loadStatic`; they are not disk-backed chunk eviction or complete server-restart tests.

Chemical fixture development first exposed a Java module split-package error when a test was put in the production chemical package. It was moved into the test mod's own package and now drives the public ticker API at a valid scheduled offset. The source tank is seeded through its actual internal native tank: its output-only face correctly rejects external insertion. Assertions use the real stored amounts, not extraction through an input-only destination face.

Reproductions before the fixes:

- All four family tests: expected 3,750 per network update, received 750.
- After correcting path-rate intervals: the partial-destination branch test received 4,750 against its 3,750 budget.

Final verified suites:

| Suite | Executed | Failures/errors | Skips |
| --- | ---: | ---: | ---: |
| Core unit tests | 61 | 0 | 0 |
| Core native GameTests | 170 | 0 | 0 |
| Native Mekanism adapter/filter tests | 7 | 0 | 0 |
| Addon early-initialization unit test | 1 | 0 | 0 |
| Actual-mod addon GameTests | 18 | 0 | 0 |

The 12 new addon tests comprise five live ME/RS network/lifecycle cases, four native chemical-family rate cases, a branch budget case, a large-rate case and the actual LaserIO book recipe case. That is **62 passing unit tests and 195 passing native GameTests** across the verified profiles; it does not include the still-unported main module's test suite.

Commands actually run:

```sh
./gradlew :endercore:check :enderio-modded-conduits:check --console=plain
./gradlew :enderio-modded-conduits:check --console=plain
./gradlew build --console=plain
./gradlew :enderio:runData :enderio-modded-conduits:runData --console=plain
./gradlew :enderio:jar :enderio:reobfJar --console=plain
```

Core/addon checks pass. The book test was added after the first combined check, and the subsequent addon check passes all 18 tests. The **full repository build fails** at `:enderio:compileTestJava` with **42 early compilation errors**, including the old data-component, NeoForge fluid and test-framework APIs. Core and addon tests executed successfully during that failed full build; the main tests did not execute.

The first datagen rerun failed on the observed key-registration race. Both generators pass after the isolated serial-loading change. The nonfatal Embeddium fingerprint error and its mixin-taint message remain in the main datagen log. Actual addon server resource loading no longer reports the two recipe errors, and the book recipe is positively exercised rather than inferred from silent logs. Packaging was run separately after datagen.

## Evidence and boundaries

Ignored local evidence is under `build/port-evidence/`: `cycle-033-network-first.log`, `cycle-033-network-second.log`, `cycle-033-reload-first.log`, the chemical rate/budget reproduction logs, `cycle-033-full-build.log`, `cycle-033-final-check.log`, `cycle-033-final-artifact.log`, `cycle-033-book-final.log`, `cycle-033-datagen.log`, `cycle-033-datagen-final.log`, `cycle-033-packaging.log`, `cycle-033-test-results/`, `cycle-033-test-summary.json`, `cycle-033-artifacts.json` and `cycle-033-patchouli-version.json`.

Candidate artifacts use pre-commit version `8.2.14.32-1.20.1+7f794de6fd`. Their own classes are Java 17 major version 61 (251 core, 1,260 main, 69 addon classes). Recursive inspection finds no new integration-test classes, large-rate test conduit or test-mod resources in the main/nested jars, and confirms the orphan recipe is absent. These are development artifacts, not validated releases.

| Module artifact under its `build/libs` | SHA-256 |
| --- | --- |
| `endercore-8.2.14.32-1.20.1+7f794de6fd.jar` | `cc96e694b8564ddda0c163dc7146622bb802c007c427cc2e30c5328533b63ddf` |
| `enderio-8.2.14.32-1.20.1+7f794de6fd.jar` | `fac7e572244a8db60d6702e8e15fe84dd90db4cec6c8aff60495e61243def301` |
| `enderio-modded-conduits-8.2.14.32-1.20.1+7f794de6fd.jar` | `3cc1e52461452c4b99358cdc308f29b2fc03a66cb4d9f92f521b1f6854f7308f` |

[Patchouli's pinned Forge release](https://modrinth.com/mod/patchouli/version/8Qyw08ld) is the primary dependency reference. No additional repository was cloned. No client was launched; all gameplay execution used headless, isolated native test servers, which exited after their tests.

Remaining required work includes:

- Port and execute the main module's existing unit and GameTest suites; the full `build` is not green.
- Verify chemical execution-time remainder handling when destination execution accepts less than simulation. The rate/budget fixes do not resolve that previously recorded loss risk.
- Cover real AE2 power/channels/storage, RS storage operations, heat transfer, filtering/routing under more topologies, integration absence, disk-backed chunk unload and full restart persistence. Native bundle reload is only part of that requirement.
- Exercise all remaining feature families and resource semantics, investigate datagen-only client-mod diagnostics, and verify clean packaged installations.
- Implement and directly verify a non-activating client setup, then perform visuals, single-player and dedicated multiplayer validation. Client, clean packaged-server and multiplayer completion remain unproven.

Next cycle: address the actual main-test compilation failures and expand native gameplay coverage, then verify clean packaged-server loading. Keep the full goal active.
