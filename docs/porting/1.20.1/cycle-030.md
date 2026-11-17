# Cycle 030 — native datagen compilation and recipe output

## Objective and changes

Port the main data generators and verify the native recipe data they produce. This cycle continues from `8f7c1ddae` on local `1.20.1`. Reinspection found no active merge/rebase, no unmerged index entries and no upstream for this branch. The previous auto-stash and both cycle-012 recovery refs remain intact; `origin/1.20.1` still points to the historical `18ee1b17b`. No reset, stash deletion, remote write or new history merge occurred. No on-disk manual-change protocol or CodeGraph directory was present.

- Added EnderCore `RecipeOutput`, a consumer of native `FinishedRecipe` objects. Custom recipes encode through their actual runtime codec with registry-aware JSON. Forge conditions are serialized with `CraftingHelper`; recipe IDs, serializer identity and optional advancement data remain attached to the finished record.
- Added `StackShapedRecipeBuilder` and a finished-recipe result wrapper using Forge's supported `item`/`count`/SNBT result object. The result is copied when captured. Conduit crafting retains its variant NBT and eight-item output; both conduit-binder cooking recipes retain their two-item output. Ordinary native builders forward their finished records unchanged.
- Ported all main datagen imports and APIs to pinned Forge 47.4.28. The gather-data handler is a Forge mod-bus subscriber instead of a second `@Mod` entry point. A native `DatapackBuiltinEntriesProvider` supplies the conduit registry lookup to dependent generators. Loot providers use native IDs and supplier constructors; advancement generators use native triggers and Forge's existing-file-aware save method.
- Adapted enchantment holders/constants, recipe conditions, Forge ingredient tags, model loaders, bucket fluid access and item-model helpers. Tag language strings retain registry-qualified translation keys. The Athena resource pack keeps its existing `resourcepacks/athena` destination and a distinct provider name, avoiding collision with the main blockstate provider.
- Defined compatibility tags for chains, skulls and relocation restrictions where Forge lacks NeoForge constants. Recreated the vanilla skull and meat memberships used by the source from the official 1.21.1 data. Blocks/items introduced after 1.20.1 use optional registry IDs in tags; armadillo/bogged/breeze soul presets retain their resource IDs without referencing absent Java constants. This does not add those Minecraft entities/items to 1.20.1.
- Added four native GameTests: actual alloy output through the native loader with installed/absent-mod conditions and advancement data; shaped output NBT/count/pattern/unlock preservation; cooking output quantity/SNBT/time/XP preservation; native finished-record forwarding and empty-output rejection. Tests also exercise mutation isolation of captured outputs and generated JSON.

## Actual verification

The first converted datagen compile reported **214 errors**, followed by **6**, then **1** as the native APIs were adapted. Final command:

```sh
./gradlew :enderio:compileDatagenJava --no-configuration-cache --console=plain
```

Exit **0**, normal Gradle compilation including main Java and annotation processors. Development output contains **1,201 main classes** and **59 datagen classes**, all Java 17 major **61**. This is not a packaged-mod runtime result.

```sh
./gradlew :endercore:test :endercore:runGameTestServer :endercore:jar :endercore:reobfJar --no-configuration-cache --console=plain
```

Exit **0**. JUnit XML: **61 executed/passed, 0 failures/errors/skips**, across 11 suites. Native GameTest XML and server summary: **168 executed/passed, 0 failures/skips**, including all four new recipe-generation tests. The actual server stayed headless and shut down normally. Existing intentionally exercised rejection paths write expected diagnostics; the native test summary and XML report success.

```sh
./gradlew :enderio:compileDatagenJava :enderio:runData --no-configuration-cache --console=plain
```

Exit **1** after successful main/datagen compilation: `:enderio-modded-conduits:compileJava` stops at **10 Java 21 record-pattern syntax errors** in `ChemicalStackSyncSlot`. The generator process did not start. No dependency or module was excluded to bypass the failure; full resource generation and loading are still unverified. The deeper addon API errors remain unmeasured until the syntax is converted.

Evidence in ignored `build/port-evidence/`:

- `cycle-030-datagen-first.log`, `cycle-030-datagen-second.log`, `cycle-030-datagen-run.log`, `cycle-030-datagen-runtime.log`.
- Successful `cycle-030-final-datagen-compile.log` and `cycle-030-core-tests.log`.
- `cycle-030-tests.json`, `cycle-030-gametest-results.xml`, `cycle-030-artifact.json`, `cycle-030-commit-scout.txt`.

Reobfuscated core artifact built before this cycle's commit:

- `endercore/build/libs/endercore-8.2.14.29-1.20.1+8f7c1ddae4.jar`
- SHA-256: `9ea2c340f520c5b8cb4a76f0c36e5a72a653803592a1d32a8e718a3833b5161c`
- **249 classes**, all major **61**, with no GameTest or bridged main-module classes.

No client, audio, input capture, user world, push or publication occurred. Each process created for this cycle completed. This work has its own new local commit; earlier cycles remain unchanged.

## Next work and remaining limits

Port the addon against native Mekanism/Refined Storage/AE2 APIs, beginning with the actual compilation failure. Mekanism 1.20.1 separates gas, infusion, pigment and slurry; the newer unified API must preserve all four categories through real native adapters. Removing record patterns is only the first step.

Then rerun complete datagen and fix its actual runtime failures. Reconcile generated IDs/schemas/layouts against the immutable baseline, remove obsolete singular 1.21 resource paths where appropriate, and audit `c:` tags against Forge 1.20.1 equivalents so installed-mod ingredients are populated. Compilation alone does not establish valid tags, recipes, optional pack assets or dynamic registry data. Full source provider execution remains unverified even though the adapter's actual generated recipe records pass native loaders.

Main startup, client rendering/UI, packaged dedicated multiplayer, supported integrations present/absent, complete feature-family behavior, transfer conservation, restart/unload/reconnect state and historical-save compatibility remain required. XP-vial merged-orb accounting and the remaining codec audits are still unresolved. No gameplay matrix row is promoted to complete. The full goal remains active and incomplete.

Sources: pinned Forge 47.4.28 source artifact and native runtime; official Minecraft 1.21.1 client data already downloaded and verified in cycle 029; repository baseline and historical Forge reference.
