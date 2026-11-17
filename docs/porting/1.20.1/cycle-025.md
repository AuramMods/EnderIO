# Cycle 025: reloadable registry data maps

## Purpose and changes

Started clean on `1.20.1` at `8d379f52c`. This cycle replaces the unavailable NeoForge data-map APIs for the three implemented maps while retaining their IDs, JSON paths and values.

- Added EnderCore `RegistryDataMap`, `DataMaps`, and `DataMapReloadListener`. Native `AddReloadListenerEvent` supplies the owning recipe manager, registry access and staged Forge condition/tag context. The loader reads every pack layer in priority order and resolves tag selectors after the reload preparation barrier. Missing files produce empty maps, so reloads cannot retain removed entries.
- Snapshots are immutable and scoped to recipe-manager instances. Server resource reloads and client connections have distinct owners, including in an integrated server. Weak owner keys avoid retaining discarded resource sets; shutdown/logout explicitly clear current state. Consumers use their level or the active client connection rather than shared static registry-attached values.
- Preserved the source maps' **last-value-wins** behavior. Although range/reagent maps used `AdvancedDataMapType`, they did not configure a custom merger. Combining inner maps would change the baseline. The loader supports whole-file replacement, entry wrappers, ID/tag additions and removals, and the advanced maps' default whole-value removal forms. Each layer decodes completely before its changes apply; malformed layers are logged and skipped as in the source loader. Optional booleans and removal IDs decode strictly.
- Forge conditions are evaluated using the staged reload context. Common legacy `neoforge:` condition type IDs translate to Forge's serializers, including nested conditions and conditional value wrappers. Unknown condition serializers fail that layer with a diagnostic; arbitrary third-party 1.21 datapacks still require compatible Forge conditions. Existing generated files are retained unchanged.
- Added a mandatory `endercore:data_maps` Forge channel. Resolved snapshots are sent on login and datapack synchronization in ordered 128 KiB chunks. The receiver retains the last complete snapshot until all chunks and all registered map types decode successfully. It rejects invalid sizes, missing/duplicate map types, registry mismatches, unknown/duplicate entries, malformed values, trailing bytes and out-of-order fragments. Limits are 64 MiB per snapshot and 1,048,576 characters per encoded value. These are real wire-format bounds, not inferred launch gates.
- Client connection guards come from the existing native registry channel. Logout clears pending transfers; malformed complete transfers leave the last good snapshot intact before disconnecting. Forge sends the map snapshot before its tag packet, so the client tag event refreshes snapshot identity after those bindings arrive. The actual client packet/event sequence still needs runtime validation.
- Added a native `DataProvider` implementation and migrated all three datagen providers to it. It emits the existing `data/<namespace>/data_maps/<registry>/<map>.json` layout and replacement wrappers.
- Migrated SAG mill inputs, tooltips, vat modifiers/screens, JEI map enumeration and wireless charger range lookups. Wireless chargers refresh cached range after snapshot/tag changes, preserving a custom range when possible and clamping it when the maximum falls. The actual machine/client paths remain unverified.
- `FermentingRecipe.Input` now carries its owning level; `getModifier` also takes the level. The vat and its existing regression source supply it. Recipe type/serializer lookups use the already-established native `RecipeRegistry` API. This makes server/client lookup ownership explicit rather than guessing the logical side from a global registry.
- Fixed inverted `GrindingBallData.saveOptional`: identity writes an empty compound, while a real grinding ball writes its data. Native registry ops replace newer serialization APIs; required identity saves still reject invalid use.

The new API is EnderCore-owned; no fake NeoForge classes or static defaults substitute for loaded data. The old framework type APIs are not binary compatible with the Forge port. Addons must query `RegistryDataMap` with a level/recipe-manager owner and supply a level when constructing vat inputs.

## Verification

```sh
./gradlew :endercore:compileJava --console=plain
./gradlew :endercore:test :endercore:runGameTestServer :endercore:jar :endercore:reobfJar -I scripts/porting/compiler-diagnostics.init.gradle :enderio:portCompilerArguments --no-configuration-cache --console=plain
```

Final exit **0**:

| Suite | Discovered | Executed | Passed | Failed/errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| EnderCore unit tests | 57 | 57 | 57 | 0 | 0 |
| Native Forge headless GameTests | 138 | 138 | 138 | 0 | 0 |

Seven new GameTests cover:

1. Actual Forge reload-listener registration and initial resource loading, staged native item tags, a block map, and actual vat recipes applying both reagent multipliers in either input order.
2. Real `MultiPackResourceManager` / `PathPackResources` layering, tag selectors, overrides, removals, failed-layer atomicity, strict replacement metadata, and clearing absent files.
3. Forge/legacy condition wrappers, source-compatible replacement of nested maps, and advanced default removal forms.
4. Native datagen output loaded through the actual reload listener, independent resource owners, and cleanup that leaves the other owner's state intact.
5. Multi-chunk native-buffer codec round trips, publication only after completion, and clearing values with an empty synchronized snapshot.
6. Rejection of out-of-order/corrupt/oversized transfers, retention of the last good snapshot, and acceptance after clearing the receiver's connection state.
7. Actual grinding-ball optional NBT persistence, identity omission and required-save rejection.

The bridge now compiles **92 actual main production files**, adding the three value classes and the actual fermenting recipe. These remain test-only and are absent from the production core jar. Malformed-layer tests intentionally emit decoding errors; their assertions verify that bad data was rejected without replacing valid data. All test runs completed successfully. Follow-up runs covered stricter metadata parsing, the native staged-tag assertion, remaining client lookup helpers and the client tag-refresh hook.

The transfer tests exercise native buffers and the production receiver with isolated recipe-manager owners. They do **not** establish an actual client connection, login/reload packet ordering, GUI/JEI rendering, or network reconnect. The initial Forge resource pipeline and targeted listener reloads run, but a full packaged-mod `/reload` and multiplayer session remain required. Native datagen is tested with a fixture provider; full Ender IO datagen remains blocked by the incomplete main build.

```sh
./gradlew :enderio:compileJava --console=plain
/Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/javac -J-Xmx4G @build/port-evidence/main-javac.args
```

Both exit **1**, reporting **350 errors and 100 warnings**, down from 372. No diagnostics remain in the new data-map implementation, value classes, fermenting recipe, wireless range conversion or migrated UI/JEI callers. Two pre-existing diagnostics remain in files also touched here: `SagMillBlockEntity` still references the newer `DUST_PLUME` particle, and `VatBlockEntity` still uses `List.getFirst()` in its task implementation. Other errors include FTB hooks, farming, loot/advancements, souls/spawning and remaining content APIs. Supplemental javac disables annotation processing and is not a successful build.

Evidence under ignored `build/port-evidence/`:

- `cycle-025-core-compile.log`, `cycle-025-core-first.log`, `cycle-025-core-final.log`, `cycle-025-core-verified.log`, `cycle-025-core-complete.log`.
- `cycle-025-tests.json`, `cycle-025-gametest-results.xml`, `cycle-025-main-gradle.log`, `cycle-025-main-javac.log`, `cycle-025-error-counts.json`.
- `cycle-025-artifacts.json`, `cycle-025-commit-scout.txt`, and the inspected `cycle-025-neoforge-sources.jar`.

Reobfuscated core jar built before this cycle's commit:

- `endercore/build/libs/endercore-8.2.14.24-1.20.1+8d379f52c5.jar`
- SHA-256: `05358563cce60d2b029deadcc88c533d56a5d7fa827498ff09ad6f7755ba2819`
- 240 classes, all Java 17 class-file major 61; no test or bridged main-module classes.

The isolated GameTest server used `nogui` and `java.awt.headless=true`, completed and shut down cleanly. No client, window, audio or user world was used. Temporary filesystem packs are removed by the tests. This cycle has its own local commit; no push.

## Outstanding work

Complete the remaining content/integration compiler conversions, full datagen and packaging. Then verify the actual map channel with packaged client/server jars on login, `/reload`, dimension changes and reconnect, including integrated-server isolation and optional integrations. Exercise real vat/SAG mill/wireless charger gameplay, cached range refresh, GUI tooltips and JEI after map/tag changes. No complete feature-family acceptance status is promoted by these focused tests.

The previous conduit graph-reconciliation, strict optional-codec, actual obelisk/fire-crafting, save migration, client rendering, integration and packaged multiplayer requirements remain outstanding. The full goal stays active and incomplete.

Sources: [NeoForge 1.21.1 data maps](https://docs.neoforged.net/docs/1.21.1/resources/server/datamaps/), the exact [NeoForge 21.1.216 source archive](https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.216/neoforge-21.1.216-sources.jar) (`DataMapLoader`, `DataMapFile`, `DataMapEntry`, `ConditionalOps`, default merger/remover), and pinned Forge 47.4.28 reload/tag/player-list/network sources. Pinned source establishes the version-specific behavior.
