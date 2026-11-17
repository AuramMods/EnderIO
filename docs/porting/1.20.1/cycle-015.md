# Cycle 015 — Native recipe consumers and reload-aware caches

Status: progress, not completion. Cycle 014 was verified progress. This cycle starts at `2bbe9ae5f`; main Ender IO and the addon still cannot run as complete mods.

## Changes

- Converted main recipe consumers, machine task factories, menus, JEI categories and transfer helpers from newer `RecipeHolder` wrappers to native 1.20.1 recipe objects and `getId()`. Removed the now-duplicate helper overloads. JEI recipe types use the actual recipe class. The shaped soul-storage extension uses the pinned JEI API's per-recipe factory and instance methods. Its crafting recipe now uses native `CraftingContainer`/`RegistryAccess` signatures. These integration and machine paths still require full runtime validation.
- Kept the enchanter's level-specific JEI entries as display adapters. Their former Minecraft `Recipe` interface consisted of unused null/false implementations; JEI does not require that interface for these custom display entries. Their level, ID, ingredients, book, enchantment and cost behavior remains present.
- Added `CraftingRecipeInput`, an isolated stack snapshot implementing the actual native crafting container. The crafter uses it for vanilla/Forge matching, assembly and container remainders. It retains grid dimensions and positions, and native container mutations fail explicitly. Mutating a snapshot stack does not consume the source machine inventory.
- Added `RecipeCache`, scoped to each actual recipe manager. A core mixin accessor exposes the native recipe-map snapshot identity, which changes on native reload and recipe replacement. Weak manager keys keep independent client/server contexts separate. Native tag events invalidate derived data as well. The cache consumes Forge's ingredient invalidation before rebuilding; `Ingredient.getItems()` alone can retain old tag members even after `invalidateAll()`.
- Machine input caches now receive their owning level's manager explicitly, replacing a global current-server lookup and shared reload flags. The fire-crafting recipe list is scoped to its manager/snapshot instead of a static list refreshed only when the manager object changes. The slicer uses its manager's current recipes and evaluates actual positional ingredient predicates, preserving custom NBT checks.
- Added a partial-input ingredient index. Ordinary item candidates are indexed; custom ingredients remain candidates even when display stacks omit a matching item. A matching assignment gives each occupied input a distinct recipe ingredient, including overlapping predicates that a greedy match incorrectly rejects. Empty input slots remain available for missing ingredients. This admission check does not replace the recipe's full quantity/crafting checks.
- Replaced the newer synthetic recipe-manager mixin target with native `RecipeManager.apply` at its tail. The extracted smelting-inheritance implementation consumes recipes that passed Forge conditions, preserves derived IDs, ingredient predicates, output data, experience and configured energy, and copies output stacks. An explicit recipe with the derived ID takes precedence. The unused cooking-field accessor and uninitialized logger were removed.
- Saved task lookup verifies both ID and recipe type before creating a restored task. Missing, malformed or repurposed IDs cannot be cast into a different machine recipe type. Nullable recipe access no longer dereferences a missing wrapper. Complete task NBT migration and in-progress machine restart behavior remain unfinished.

API details were checked against the generated Forge 47.4.28 sources and the pinned JEI jar on the actual compiler classpath. No additional repository clone was needed. The cycle-012 recovery stash and recovery refs remain preserved; the local branch still has no upstream and no remote write was performed.

## Actual verification

The first test command was:

```sh
./gradlew :endercore:check --console=plain
```

It failed while compiling the new test-only ingredient fixture because its required `toJson()` implementation was missing. After implementing that method, the combined command ran:

```sh
./gradlew :endercore:check :endercore:jar :endercore:reobfJar :enderio:compileJava --continue --console=plain
```

This exited **1**: main compilation reached the normal **400-error cap**, and **87 of 88 GameTests passed**. The failing tag-reload test showed that rebuilding our index still read stale native ingredient stacks. The production cache was corrected to consume Forge's ingredient invalidation before reading those stacks. The test was retained and its assertions separated to distinguish event invalidation from stale members.

Final core verification:

```sh
./gradlew :endercore:check :endercore:jar :endercore:reobfJar --console=plain
```

Exit **0**. Final main compilation, after resolving the duplicate JEI helper overload and native tag names, was `./gradlew :enderio:compileJava --console=plain`; it exits **1** at the normal **400-error cap**.

| Suite | Executed | Passed | Failures/errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| EnderCore JUnit | 39 | 39 | 0 | 0 |
| Native headless Forge GameTests | 88 | 88 | 0 | 0 |

Eight new GameTests cover:

- Cache population, reuse, same-ID replacement, removal and isolation between two real native managers. This exercises the actual core mixin accessor.
- Partial machine inputs, overlapping ingredients in both input orders, rejection of duplicated inputs, custom NBT predicates and a custom ingredient whose display stack list omits a valid matching item.
- Actual item-tag rebinding from iron to gold, the native Forge tag event and ingredient invalidation, without replacing the recipe manager or recipe objects. Original tag membership is restored afterward.
- The actual production slicer recipe manager's positions, custom matching, manager isolation, recipe removal and invalid slot handling.
- Actual smelting inheritance preserving IDs, energy, experience and independent output copies, followed by a native `ClientboundUpdateRecipesPacket` round trip.
- Native `RecipeManager.apply` condition processing, changed furnace recipes, explicit derived-ID overrides and removal on reload.
- Typed saved-recipe lookup with valid, missing, malformed and wrong-type IDs.
- Native shaped crafting at a grid offset, snapshot independence, native milk-bucket remainders and rejected container mutations.

The temporary bridge now compiles **33 actual main-module files**, adding the smelting-inheritance implementation and slicer recipe manager to cycle 014's 31. It remains test output only. The smelting tests call the actual native `apply` implementation followed by the production inheritance helper; they **do not load the main mod's mixin/configuration path**. Main-mod startup, the injection's production runtime, machine block entities, task persistence, JEI UI and a real client connection remain unverified. Full datapack and optional-mod ingredient reloads still require end-to-end coverage.

The test servers ran with `nogui` and headless AWT, saved and stopped cleanly. No graphical client, audio system or narrator was launched. Logs and result records under `build/port-evidence/`:

- `cycle-015-tests-first.log`: initial fixture compilation failure.
- `cycle-015-check-package-main.log`: first 88-test run, the tag failure and capped main compilation.
- `cycle-015-tests-final.log`: successful final core checks and packaging.
- `cycle-015-main-final.log`: final normal main compilation failure.
- `cycle-015-tests.json`: final counts and test names; XML also remains under `endercore/build/test-results/`.

Supplemental compiler operation:

```sh
./gradlew :endercore:compileJava -I scripts/porting/compiler-diagnostics.init.gradle :enderio:portCompilerArguments --no-configuration-cache --console=plain
/Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/javac -J-Xmx4G @build/port-evidence/main-javac.args
```

Argument export/core compilation passes. Final supplemental Java 17 compilation exits **1 with 1,266 errors and 100 reported warnings**, down from cycle 014's 1,472. The first intermediate count was 1,285; a later intermediate count was 1,271 before the final JEI/tag cleanup. Annotation processing is disabled in this diagnostic command; counts are not independent defects or full-build evidence. Logs: `cycle-015-core-arguments.log`, `cycle-015-core-arguments-final.log`, `cycle-015-main-javac-first.log`, `cycle-015-main-javac-final.log`.

Intermediate core artifact, named from this cycle's base commit:

- `endercore/build/libs/endercore-8.2.14.14-1.20.1+2bbe9ae5f9.jar`
- SHA-256: `337a61e32ef810a80060c9b3dfbe53cdff9ec9dfcc0195d256b19a453590e2a7`
- All class files are version `61` (Java 17); test fixtures and bridged main-module classes are absent.
- The packaged refmap includes the recipe accessor's native SRG field mapping. This inspection is not a packaged-runtime test.

Artifact record: `build/port-evidence/cycle-015-artifacts.json`. `git diff --check` passes. All owned command handles reached terminal states. No push or release was performed.

## Remaining work and next cycle

Port native machine inventory/task/block-entity persistence and finish remaining recipe behavior, datagen and JEI APIs. Exercise actual machine-held recipe references on reload, including the crafter, fluid tank, enchanter and soul binder; this cycle's cache/helper tests do not establish those complete workflows. Preserve in-progress outputs and resource accounting through restart and recipe changes.

Attachments/data maps, resource schemas, full capability gameplay, block interaction, rendering/UI, integrations, historical saves and packaged client/server tests remain incomplete. Main compilation still has substantial blockers. The complete baseline scope is unchanged, and the goal remains active.
