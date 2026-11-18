# Cycle 031 — native Mekanism chemical families

## Objective and changes

Continue from the clean `076651c46` commit on `1.20.1`, addressing the addon compilation failure that prevented datagen. The previous goal turn made verified progress: main datagen compilation plus native recipe-output tests. The full objective was reread; no manual-change protocol or CodeGraph directory was present. No active Git conflict or recovery operation was found.

- Removed the Java 21 record patterns from chemical menu synchronization, exposing the addon's real API errors. The first native compile then reported 153 errors rather than stopping at ten syntax errors.
- Added `ChemicalResource`, an Ender IO adapter around Mekanism's actual `BoxedChemicalStack` and native gas/infusion/pigment/slurry stacks. The explicit family, registry ID and positive long amount survive JSON, NBT and packets. Empty data has a separate encoding. Unknown IDs/families, ambiguous data without a family, fractional/overflowing amounts and invalid packets fail instead of silently becoming another family or an empty stack. Copied/resized resources own their state; the empty singleton cannot be filled accidentally.
- Added `ChemicalHandlerAdapter`, dispatching operations through the four real Mekanism capability tokens. Tank enumeration includes every present family; extraction/insertion select the matching native handler and return native remainders. Missing families reject insertion and yield no extraction. Capability lookup still goes through the actual neighboring conduit accessor or item's Forge `LazyOptional`.
- Converted chemical filters, legacy lock data, sync slots, menu slots, ticker call sites and client display data to the native-backed resource. Filter captures copy mutable entries, keep the 256-entry limit, compare family as well as chemical identity and return the offered quantity. Full menu updates carry family/ID/amount; partial updates carry amounts; clearing uses the native null-slot payload. Server and client filter-slot coordinates now agree. Malformed present legacy lock fields stay codec errors instead of being hidden by optional defaults.
- Replaced the invented unified chemical capability with native Mekanism dispatch, used the actual heat-handler token, and wired Ender IO filter attachment/type registration through Forge. Client menu setup uses `FMLClientSetupEvent` and queued native screen registration. These module-level hooks still require full addon startup verification.
- Ported the LaserIO filter adapter to its pinned `BaseFilter` API and native item chemical capabilities; it returns the offered stack on a match. Card filter views read the current filter each time. LaserIO 1.20.1 has no built-in chemical card: the basic-filter hook is retained, with the chemical-card attachment registered by ID for extensions that supply that item. This does not invent a chemical-card item or claim that absent LaserIO functionality exists.
- Added a separate `mekanismGametest` source set/profile using the pinned Mekanism artifact. Production adapter/filter/sync sources are compiled into test output only. The normal core profile remains free of Mekanism; `:endercore:check` runs both profiles. A native test mod registers the same ID in all four chemical registries and uses Mekanism's own tank implementation, allowing actual cross-family collision and conservation assertions.

## Actual verification

The first test-profile configuration attempt failed because a guessed configuration name did not exist. It was replaced with ModDevGradle's actual `obfuscation.createRemappingConfiguration` API. A test import was corrected to native `net.minecraft.nbt.NbtOps`. The first launch rejected an overly specific metadata version: the pinned 10.4.16.80 artifact advertises Forge mod version **10.4.16**. The test dependency now matches the actual metadata; no check was bypassed. Subsequent native runs passed six tests, then seven after adding malformed-data/boundary cases.

```sh
./gradlew :endercore:check :endercore:jar :endercore:reobfJar --no-configuration-cache --console=plain
```

Final exit **0**. Authoritative JUnit/GameTest XML and native summaries:

- **61 unit tests**, 0 failures/errors/skips.
- **168 core GameTests without Mekanism**, 0 failures/errors/skips.
- **7 Mekanism GameTests with the real pinned mod loaded**, 0 failures/errors/skips.

The seven new tests cover all four families across JSON/NBT/packets up to `Long.MAX_VALUE`; invalid family/ID/amount rejection; native and wrapper copy isolation; allow/deny filters with colliding registry IDs; full/partial/clear menu synchronization through actual slot packet codecs; all four native tank handlers with simulated versus executed insert/extract accounting and missing-capability rejection; malformed packet/update preservation and the 256-entry filter boundary.

These are development-runtime tests of the actual adapter/filter/sync production code. Native tanks are exposed by small test handlers implementing the real Mekanism interface. They do not prove graph routing, neighbor unload/invalidation, integrated filter-item attachment, real machine endpoints or packaged client behavior.

```sh
./gradlew :endercore:test :endercore:runGameTestServer :endercore:jar :endercore:reobfJar :enderio:compileDatagenJava :enderio-modded-conduits:compileJava --continue --no-configuration-cache --console=plain
```

Exit **1** solely at addon compilation; core tests/artifacts and main/datagen compilation pass. The earlier pass reported 101 addon errors; the following pass reported 98 while the final component-codec reference was corrected. Final addon compilation reports **97 errors**, recorded in `cycle-031-final-addon.log`. Remaining errors concern addon lifecycle/datagen APIs and AE2/Refined Storage adapters. Early symbol errors can conceal deeper failures; this is not a complete error inventory. No module was disabled or replaced by platform stubs.

Evidence under ignored `build/port-evidence/`:

- `cycle-031-addon-first.log`, `cycle-031-mekanism-compile.log`, `cycle-031-mekanism-compile-second.log`.
- `cycle-031-mekanism-tests-first.log`, `cycle-031-mekanism-tests-second.log`, `cycle-031-mekanism-tests-third.log`.
- `cycle-031-validation.log`, `cycle-031-final.log`, `cycle-031-final-addon.log`, successful `cycle-031-core-check.log`.
- `cycle-031-tests.json`, both `cycle-031-*-results.xml` reports, `cycle-031-artifact.json`, `cycle-031-commit-scout.txt`.

Reobfuscated core artifact built before the commit:

- `endercore/build/libs/endercore-8.2.14.30-1.20.1+076651c464.jar`
- SHA-256: `6ac4e0e41c896bc4ab277704ee7a9cbf601edd0ea3f29f4d96453e39b1787b29`
- **249 classes**, all Java 17 major **61**, with no GameTest, main-mod or addon bridge classes. Mekanism remains a test-profile dependency, not a core release dependency.

All created server processes ran headlessly in isolated EULA-accepted profiles and shut down normally. No client window, sound, narrator, input capture, user world, push or release occurred. This cycle receives its own new commit; earlier commits are retained.

## Next work and limits

Port the remaining addon lifecycle/recipe/dynamic-registry generation and AE2/Refined Storage integration APIs, then rerun real addon compilation and datagen. Native Forge's `RegistryDataLoader` already calls `ICondition.shouldRegisterEntry`; inspect its exact condition JSON schema when porting addon registry generation so missing optional mods do not load unknown conduit types. Main custom recipe output still needs a condition-composing wrapper for the addon builders. Refined Storage 1.12.4 uses different network/node APIs from the source.

Chemical network behavior remains incomplete evidence: audit per-path tick-rate accounting, amount overflow, filter limits and simulated-versus-actual acceptance. The existing ticker does not restore a destination remainder if insertion accepts less than simulation; this remains a loss risk to resolve with actual transfer tests and appropriate owned-state recovery. Test neighboring-capability invalidation/unload, multi-family endpoints, split/merge, save/restart and GUI sync in the complete mod. Heat equilibrium/invalid numeric states, LaserIO runtime hooks and migration of historical chemical data remain unverified. A family-less chemical record is currently rejected rather than guessed; historical migration requires explicit fixtures and a supported mapping.

Full datagen/resource reconciliation, optional integrations present/absent, complete baseline tests and feature coverage, packaged client/dedicated multiplayer, persistence/conservation, historical-save compatibility and final distributables remain required. XP-vial merged-orb accounting and remaining codec audits are still open. No feature-family row is marked complete. The full goal remains active and incomplete.

Sources: pinned Mekanism 10.4.16.80 jar/API and Forge 47.4.28 sources/runtime; upstream [native boxed chemical stack](https://github.com/mekanism/Mekanism/blob/1.20.x/src/api/java/mekanism/api/chemical/merged/BoxedChemicalStack.java) and [native chemical handler interface](https://github.com/mekanism/Mekanism/blob/1.20.x/src/api/java/mekanism/api/chemical/IChemicalHandler.java). Exact signatures and behavior were checked against the pinned artifact and executed in native tests.
