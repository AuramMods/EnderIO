# Cycle 023: registry-aware block loading and native saved data

## Purpose and changes

Started clean at `ebe3e6b4e`. Conduit loading attempted to decode dynamic registry holders with `RegistryAccess.EMPTY` before Forge assigned the block entity to its level. This cycle addresses that lifecycle mismatch and the native saved-data APIs.

- Added production `RegistryBlockEntity`, an EnderCore base using native `load`, `setLevel`, persistent save and update-tag paths. It owns a copy of unprocessed NBT, waits for the actual owning level before registry decoding, applies each queued payload once, and retains the original payload if decoding fails. Saving before attachment preserves pending data. Persistent-only and synced fields use separate hooks after successful loading.
- Migrated `ConduitBundleBlockEntity` onto that lifecycle. Native packet callbacks, `RegistryOps`, saved item stacks (including Forge capability NBT), named holder diagnostics and bounded legacy connection lists replace unavailable APIs. Loading suppresses intermediate bundle/redstone updates, restores the previous collections on a decoding exception, refreshes capabilities after successful decoding and updates shape/models. Client extra node data follows the same registry-aware load path.
- Native Forge chunk registration calls `onLoad` synchronously for the initially empty bundle, unlike the source NeoForge placement assumption. Newly added server conduits now receive their creation callback even when first in the bundle. Repeated level assignment skips nodes already attached to this bundle, while detached nodes remain eligible for reclaiming. These actual conduit paths still require full-mod runtime tests.
- Conduit network saved data retains the owning level's registry provider and uses Forge's `computeIfAbsent(load, factory, name)` and `save(CompoundTag)`. Legacy graph loading uses the same explicit registry context. Capacitor-bank saved data uses native callbacks and ordinary NBT ops: its existing serialized graph contains positions, capacities, energy and redstone state, without dynamic registry holders.
- Travel saved data likewise retains its owning registry context. Client entries are scoped to actual level instances and removed on unload, preventing reuse across connections with identical dimension IDs. Target replacement decodes before replacing the old map. Range-squared calculations use floating-point multiplication to avoid integer overflow.
- Facade unload/removal now removes only the current position from section indexes, cleans empty sections, and clears client facade indexes on level unload. Model updates require an attached client level.
- Ported current/legacy conduit node and network codec alternatives, the legacy optional-empty-map codec, and explicitly lenient legacy filter fields. DFU 6's native optional field is already lenient, as confirmed from its actual bytecode.
- Added `StrictOptionalFieldCodec` and applied it to optional conduit node data and network context. It preserves errors for malformed present values and the source DFU 8 partial-recovery behavior; it does not silently convert those errors into successful absence. JSON null retains the source `JsonOps` absent-field behavior.

Source/API changes for addons: `ConduitNetworkSavedData.CODEC` becomes `codec(registries)`, and direct conduit/travel saved-data construction requires a registry provider. This prevents a standalone saved-data instance from quietly falling back to empty registries. Existing saved-data file names and serialized keys are unchanged.

## Verification

```sh
./gradlew :endercore:test :endercore:runGameTestServer :endercore:jar :endercore:reobfJar -I scripts/porting/compiler-diagnostics.init.gradle :enderio:portCompilerArguments --no-configuration-cache --console=plain
```

Final exit **0**:

| Suite | Discovered | Executed | Passed | Failed/errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| EnderCore unit tests | 55 | 55 | 55 | 0 | 0 |
| Native Forge headless GameTests | 126 | 126 | 126 | 0 | 0 |

Five new GameTests use a registered fixture extending the actual production base and the server's dynamic biome registry. They exercise native `BlockEntity.loadStatic`, native chunk registration, restoration before `onLoad`, repeated level assignment, NBT ownership, save/reload, immediate attached update tags, persistent-versus-synced fields, unknown-holder failure and recovery, and replacement of queued data. These prove the shared lifecycle, not actual conduit routing or full client networking. The existing 88-file main server bridge is unchanged.

Three new unit tests cover strict optional-field missing/present/zero values, malformed input, partial-result propagation, native NBT round trips, omission of absent values and retention of existing encoded fields. An initial test compile failed because JSON string literals were incorrectly escaped; that fixture was corrected. The next run passed 54/55 tests: the null case incorrectly assumed `JsonOps` exposed explicit JSON null as a present value. Inspection of the actual source-version DFU 8.0.16 jar and Mojang's source confirmed that it exposes null as absent. The final test explicitly checks this baseline behavior while retaining malformed-value and partial-error assertions. No required behavior was removed.

```sh
./gradlew :enderio:compileJava --console=plain
/Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/javac -J-Xmx4G @build/port-evidence/main-javac.args
```

Normal main compilation exits **1** at the 400-error cap. Supplemental real-classpath Java 17 compilation exits **1**, reporting **418 errors and 100 warnings**, down from 465. The first diagnostic run reported 424 errors; the remaining legacy/node codec conversions removed six more. No diagnostics remain in the production files changed this cycle. The main/addon build and packaged runtime remain incomplete; supplemental compilation disables annotation processing.

Evidence under ignored `build/port-evidence/`:

- `cycle-023-arguments.log`, `cycle-023-core-first.log`, `cycle-023-core-codec-test-compile.log`, `cycle-023-codec-test-initial.log`, `cycle-023-codec-tests-initial.xml`, `cycle-023-core-final.log`.
- `cycle-023-tests.json`, `cycle-023-gametest-results.xml`, `cycle-023-main-first.log`, `cycle-023-main-gradle.log`, `cycle-023-main-javac.log`, `cycle-023-error-counts.json`.
- `cycle-023-dfu-8.0.16.jar`, `cycle-023-dfu-baseline-bytecode.txt`, `cycle-023-artifacts.json` and commit scout output.

Core jar built before this cycle's commit:

- `endercore/build/libs/endercore-8.2.14.22-1.20.1+ebe3e6b4e6.jar`
- SHA-256: `146eb47594716a8bed019a9b1af16db33c82dce3f6ba04af5b797063cd637953`
- 225 classes, all Java 17 class-file major 61; no test classes or bridged main-module classes.

The isolated GameTest server used `nogui` and `java.awt.headless=true`, completed and shut down cleanly. No client, visible window, audio or user world was used. This cycle has its own local commit; no push.

## Outstanding work

Actual conduit routing, node reclamation, live server NBT replacement and graph reconciliation, saved-network restart, facade/travel reconnect behavior and historical world migration remain unverified. In particular, live replacement of a bundle's conduit list still needs an audit of previously attached graph nodes. Complete main compilation and exercise these against actual registered content rather than treating the shared fixture as parity evidence.

Audit remaining optional fields for DFU 6 leniency versus baseline strictness; 17 main source files still contain ordinary `optionalFieldOf` calls, some intentionally lenient. Apply strict behavior where the source requires it without changing intentional legacy recovery. Continue attachments/data maps, remaining fluids/content, integrations, datagen and packaging, then silent client and real multiplayer validation. The full goal remains active and incomplete.

References inspected: [Mojang optional-field codec](https://github.com/Mojang/DataFixerUpper/blob/master/src/main/java/com/mojang/serialization/codecs/OptionalFieldCodec.java), [Mojang JsonOps](https://github.com/Mojang/DataFixerUpper/blob/master/src/main/java/com/mojang/serialization/JsonOps.java), and the pinned Forge source/bytecode plus Mojang's DFU 6.0.8 and 8.0.16 artifacts. The pinned binaries establish the version-specific behavior; current upstream source provides supporting context.
