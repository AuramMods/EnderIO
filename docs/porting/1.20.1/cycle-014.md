# Cycle 014 — Native recipe inputs, identity and serializers

Status: progress, not completion. Cycle 013 was verified progress. This cycle starts at `0e4f21914`; main Ender IO and the addon still cannot run as complete mods.

## Changes

- Added a read-only recipe input view implementing the actual Minecraft 1.20.1 `Container` contract. Existing machine input records retain their item/fluid context. Native inventory mutation methods fail explicitly instead of modifying a recipe view or pretending to consume stock.
- Added native recipe ID binding and `CodecRecipeSerializer`. All ten custom recipe serializers now implement Forge's real `fromJson`, `fromNetwork` and `toNetwork` operations through their codecs. The loader/network-supplied resource ID is attached to the immutable recipe value; datagen construction can precede ID assignment, but unassigned recipe data cannot be inserted into a native manager as an identified recipe. Native recipe consumers still need conversion from the newer `RecipeHolder` API.
- Adapted native `RegistryAccess` method signatures and ingredient codecs, including optional positional slicer ingredients and strict nonempty ingredients. Recipe stack codecs preserve lowercase `id`/`count`/`amount` fields while using the native NBT stack codec from cycle 013. Unknown/empty outputs fail. NBT uses typed SNBT in JSON. Unconverted newer `components` fields fail explicitly; generated vanilla recipes containing those fields still require datagen/NBT conversion.
- Implemented project-owned sized item/fluid ingredients using actual Forge ingredients, fluids and native tag membership. Item quantities and custom predicates survive native Forge ingredient packets. Compound item alternatives use native array serialization nested under `ingredient` when a flat object cannot represent the alternatives; the baseline NeoForge compound form is also decoded.
- Fluid predicates support direct fluids, tags, union, intersection and difference. JSON accepts the corresponding baseline compound forms; new compound output uses project IDs. Packets retain symbolic predicates instead of freezing tag members, and display stacks are regenerated without retaining old tag membership. Native UTF packet bounds, 32-level JSON nesting and 256-child compound limits apply. Unknown predicate types fail rather than being flattened into a different match.
- Updated sized ingredient imports in recipes, datagen and JEI helpers. Four recipe classes look up their already-registered native services without initializing every recipe registration class. The actual production recipe serializers are registered in the isolated fixture mod solely for these tests; main registration startup remains unverified.
- Converted weather firework data to native `Fireworks`/`Explosions` item NBT, preserving flight duration, explosion shape, colors, fades, flicker and trail flags. Weather-mode getters return copies. The obelisk attaches these native tags to its rocket stack; actual rocket rendering and weather-machine gameplay remain pending.
- Replaced the tank recipe's newer fluid-copy operation with the native copy constructor. Painting's native `assemble` returns its painted result instead of null. Soul binding merges copied item NBT and refreshes attached capability snapshots before binding, replacing newer component copying. Those machine workflows still require runtime coverage.

The generated baseline contains **31 sized-fluid recipes**: 21 tank and ten fermenting recipes, with 25 direct-fluid inputs and six tag inputs. This is a source-schema inventory, not proof that the full data pack loads. Evidence: `build/port-evidence/cycle-014-recipe-schema-audit.json`.

Native Forge implementation was checked in the generated 47.4.28 source jar. Baseline sized-ingredient and compound-fluid formats were checked against primary [NeoForge sized item](https://raw.githubusercontent.com/neoforged/NeoForge/1.21.1/src/main/java/net/neoforged/neoforge/common/crafting/SizedIngredient.java), [sized fluid](https://raw.githubusercontent.com/neoforged/NeoForge/1.21.1/src/main/java/net/neoforged/neoforge/fluids/crafting/SizedFluidIngredient.java), [intersection](https://raw.githubusercontent.com/neoforged/NeoForge/1.21.1/src/main/java/net/neoforged/neoforge/fluids/crafting/IntersectionFluidIngredient.java), [difference](https://raw.githubusercontent.com/neoforged/NeoForge/1.21.1/src/main/java/net/neoforged/neoforge/fluids/crafting/DifferenceFluidIngredient.java) and [fluid predicate](https://raw.githubusercontent.com/neoforged/NeoForge/1.21.1/src/main/java/net/neoforged/neoforge/fluids/crafting/FluidIngredient.java) sources. These references do not imply compatibility with arbitrary NeoForge extensions or unconverted data components.

## Actual verification

Final combined operation:

```sh
./gradlew :endercore:check :endercore:jar :endercore:reobfJar :enderio:compileJava --continue --console=plain
```

Exit **1**, from main compilation at the normal **400-error cap**. EnderCore check, jar and reobfuscation pass. Log: `build/port-evidence/cycle-014-check-package-main.log`.

| Suite | Executed | Passed | Failures/errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| EnderCore JUnit | 39 | 39 | 0 | 0 |
| Native headless Forge GameTests | 80 | 80 | 0 | 0 |

Nine new GameTests cover:

- Actual alloy, slicer, fire-crafting and weather recipe classes through native JSON loading, `ClientboundUpdateRecipesPacket` encoding/decoding and native recipe manager replacement/lookup. Resource IDs, quantities, positional holes, output NBT, fire-crafting dimensions/base blocks/results and weather mode survive. Replacing recipe collections removes stale IDs.
- Actual alloy count/permutation matching, rejection of insufficient or unexpected inputs, inherited smelting output scaling and independent output copies. Actual slicer positional/custom matching and read-only native container behavior are exercised.
- Sized item quantities above 127, item alternatives, native custom ingredient NBT predicates, and invalid/empty quantities or registry IDs.
- Real native fluid-tag rebinding from water to lava, including an ingredient decoded from a packet. The predicate and display stacks observe the new members. Test tag changes are restored afterward.
- Fluid union/intersection/difference semantics, serialization and independent display stacks; unknown types and ambiguous fluid/tag forms fail.
- Weather fluid quantity matching and native firework tag contents/copy independence. No firework entity, graphical client or sound subsystem is launched by the test.

The first expanded check passed **39 unit tests and 79 GameTests**; the final combined operation added and passed the weather test. Initial log: `build/port-evidence/cycle-014-tests-first.log`. Test compile: `cycle-014-main-recipe-bridge.log`. Counts/names: `cycle-014-tests.json`; XML remains under `endercore/build/test-results/`.

The temporary test bridge now compiles **31 actual main-module source files**, adding `MachineRecipe`, `ValidatingListCodec` and four recipe implementations to cycle 013's 25. This is not main-mod startup. The test profile registers the four real serializers/types separately from the full `EIORecipes` initialization. It does not execute the actual machine block entities, remaining six recipe implementations, complete datapack reload, recipe-inheritance mixin, JEI lifecycle or a client connection. All those checks remain required.

The headless `nogui` server saved and stopped cleanly. No desktop focus, input, sound or narrator activity was requested. A final indentation-only source cleanup was followed by successful `./gradlew :endercore:jar :endercore:reobfJar --console=plain`; log: `cycle-014-package-final.log`.

Supplemental compiler operation:

```sh
./gradlew :endercore:compileJava -I scripts/porting/compiler-diagnostics.init.gradle :enderio:portCompilerArguments --no-configuration-cache --console=plain
/Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/javac -J-Xmx4G @build/port-evidence/main-javac.args
```

Argument export/core compilation passes. Supplemental direct Java 17 compilation exits **1 with 1,472 errors and 100 reported warnings**, down from cycle 013's 1,670. The intermediate count was 1,501 before the weather/NBT changes. Annotation processing is disabled in this diagnostic command; counts are not independent defects or full-build evidence. Logs: `cycle-014-core-arguments.log`, `cycle-014-main-javac-first.log`, `cycle-014-main-javac-final.log`, all under `build/port-evidence/`.

Intermediate core artifact, named from this cycle's base commit:

- `endercore/build/libs/endercore-8.2.14.13-1.20.1+0e4f21914e.jar`
- SHA-256: `886e81d32202e132951b2400c320b5e0d7f7f75d3dacbcbc2de8e9961ba14f37`
- All classes are version `61` (Java 17). Test fixtures and bridged main-module classes are absent.

Artifact report: `build/port-evidence/cycle-014-artifacts.json`. `git diff --check` passes. All owned command handles reached terminal states. No push, client launch or release was performed.

## Remaining work and next cycle

Convert recipe consumers/caches/tasks/JEI and vanilla smelting inheritance to native recipe objects and IDs. Finish the remaining recipe behavior APIs and datagen, including sized-output bounds/packet behavior, component-bearing recipe outputs, resource directory names and supported data conditions. Avoid treating the fixture serializer registration as evidence for full-mod initialization.

Attachments/data maps, block interaction, machine NBT/drop/place state, capabilities in full gameplay, rendering/UI, integration behavior, historical saves and packaged client/server validation remain incomplete. The scope remains the full implemented baseline; no feature family or final validation requirement has been dropped. The goal remains active.
