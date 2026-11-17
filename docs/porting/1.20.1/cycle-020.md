# Cycle 020 — Native Forge models and client registration

Status: progress, not completion. Starts at `e7423f2d8`, after cycle 019. Main compilation remains incomplete. This cycle executes CPU model tests and the existing headless server suite; it does not launch a client or establish visual correctness.

## Changes

- Replaced 67 client/model imports with 17 real Forge counterparts across 38 production files, including the addon tooltip. The pinned Forge 47.4.28 source jar was inspected before conversion. This covers dynamic baked models, geometry loaders, model data/properties, quad transformers, vertex wrappers, item decorators and item/block/fluid extensions. APIs without a corresponding Forge class were left for explicit implementation rather than renamed speculatively.
- Migrated five geometry baking implementations to Forge's six-argument callback and forwarded the model ID when baking element and facade models. Additional conduit, glider and travel-anchor models use native `ResourceLocation` keys. `ConduitModelModifier.getModelDependencies()` now returns `List<ResourceLocation>`; addon implementers must use Forge's additional-model IDs. Existing model dependencies and geometry are retained.
- Converted conduit-model list access to Java 17. Kept the center-origin transformation: the pinned Forge extension already implements `Transformation.applyOrigin`, so no substitute rotation was necessary.
- Converted `ModelRenderUtil` to Forge's buffered quad consumer, native vertex methods and explicit vertex termination. Converted normalized sprite coordinates to 1.20.1's sixteenths while preserving the original helper's size-dependent UV scale. Explicitly retain tint index `-1`; Forge's builder otherwise defaults to zero. The exact source default was verified in the [NeoForge 21.1.216 source artifact](https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.216/neoforge-21.1.216-sources.jar), with the corresponding [upstream implementation](https://raw.githubusercontent.com/neoforged/NeoForge/1.21.1/src/main/java/net/neoforged/neoforge/client/model/pipeline/QuadBakingVertexConsumer.java) also inspected. Conduit break-particle UVs likewise retain their original quarter-sprite coverage.
- Register all 40 existing menu/screen factories through `MenuScreens.register` during enqueued client setup. A source comparison confirms identical menu IDs and factories. Register the travel HUD through `RegisterGuiOverlaysEvent` above the crosshair, with native overlay arguments and hidden-GUI suppression. Geometry-loader names are registered through Forge's namespace-aware string API. The Enderios inverted-name predicate uses native custom hover names and literal contents.
- Moved conduit block and both fluid-tank item extensions to native `initializeClient` callbacks. Tank renderer access remains lazy until the renderer is requested. Moved the unavailable NeoForge break-sound callback's facade visibility selection into the existing native block sound-type hook, which Forge break effects actually invoke. These lifecycle and sound paths still require actual client verification.
- Updated the facade breaking-overlay mixin's descriptor to Forge `ModelData`. Its six-argument target is a Forge-added overload and therefore does not use vanilla method-name remapping. Actual packaged mixin application remains unverified.
- Added four ordinary JUnit tests against the real `ModelRenderUtil` and Forge quad builder. The existing EnderCore test build compiles that production helper and its main-module test source into test output only. This is separate from the unchanged 88-file server GameTest bridge and does not package any main/test classes in EnderCore.

## Verification

```sh
./gradlew :endercore:test --tests com.enderio.enderio.client.ModelRenderUtilTests --console=plain
./gradlew :endercore:test :endercore:runGameTestServer :endercore:jar :endercore:reobfJar --console=plain
```

Both commands exit **0**. The targeted run discovers and passes four tests. The subsequent full run executes:

| Suite | Discovered | Executed | Passed | Failed/errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| EnderCore unit tests, including bridged model tests | 43 | 43 | 43 | 0 | 0 |
| Native Forge headless GameTests | 121 | 121 | 121 | 0 | 0 |

The new tests exercise all six face directions and outward normals, vertex bounds, atlas offsets, color/alpha, untinted/unshaded metadata, rectangular texture scaling, input-vector ownership and independent quad storage. They use native CPU image/vertex buffers without a Minecraft instance, window or GL context. They do not establish chunk rendering, GPU output, texture reloads or shader compatibility.

```sh
./gradlew :endercore:compileJava -I scripts/porting/compiler-diagnostics.init.gradle :enderio:portCompilerArguments --no-configuration-cache --console=plain
/Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/javac -J-Xmx4G @build/port-evidence/main-javac.args
./gradlew :enderio:compileJava --console=plain
```

Argument export exits **0**. Supplemental native Java 17 compilation exits **1**, reporting **613 errors and 100 warnings**, down from 936 in cycle 019. Intermediate diagnostics reported 664 after native imports and 614 after model/lifecycle conversion. Normal Gradle main compilation exits **1** at its **400-error cap**, with 100 warnings. Supplemental diagnostics use the actual source/classpath without annotation processors; neither this reduction nor passing core tests proves a working main module. Full datagen and addon packaging remain blocked by main compilation.

Local evidence under `build/port-evidence/`:

- `cycle-020-client-imports.json`, `cycle-020-client-source-audit.json`: exact import conversion and screen-factory parity.
- `cycle-020-model-tests-initial.log`, `cycle-020-core-final.log`, `cycle-020-tests.json`, `cycle-020-gametest-results.xml`: commands, test identities and actual results.
- `cycle-020-core-arguments.log`, `cycle-020-main-imports.log`, `cycle-020-main-models.log`, `cycle-020-main-javac.log`, `cycle-020-main-gradle.log`, `cycle-020-error-counts.json`: compiler evidence.
- `cycle-020-neoforge-sources.jar`, `cycle-020-neoforge-quad-source.java`: pinned source comparison. Python's HTTPS fetch initially failed its local certificate validation; a normal verified system `curl` download succeeded. No certificate verification was disabled.
- `cycle-020-artifacts.json`: core artifact inspection.

Intermediate EnderCore jar, named from this cycle's starting commit:

- `endercore/build/libs/endercore-8.2.14.19-1.20.1+e7423f2d83.jar`
- SHA-256: `e23f09c8a9539e6460df3b2dad95c36cfe86c1c7d968abf8236996e1349d13b0`
- 222 classes, all class-file major 61; no GameTest, model-test or bridged main-module classes.

The GameTest server used the isolated `nogui`, `java.awt.headless=true` profile and shut down cleanly. No client was launched, no desktop window was activated and no sound settings outside the test profiles were changed.

Git inspection confirms no unmerged index entries or active merge/rebase/cherry-pick. Local `1.20.1` still has no upstream. The cycle-012 recovery stash and both `refs/port-recovery/` refs remain intact; no recovery material was deleted. This cycle receives its own local commit, with no push or remote changes.

## Next work

Continue the client conversion with native vertex/buffer APIs in I/O configuration, Enderface and travel rendering. Replace the unavailable additional-section-geometry event with a real Forge rendering path that preserves visible/hidden facades and their translucent view. Finish native painted-sand/glass classes and inspect remaining client method descriptors. Then complete registry-dependent conduit loading, attachments/data maps, content and integrations. Keep actual non-activating client/rendering, asset reload, packaged multiplayer and historical-save validation explicitly pending.
