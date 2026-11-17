# Cycle 022: Forge and Embeddium facade section geometry

## Purpose and changes

Replace the unavailable NeoForge `AddSectionGeometryEvent` while preserving the implemented facade renderer. The branch started clean at `441bc6445`; the earlier accidental merge is no longer active, no unresolved index entries exist, and both cycle-012 recovery refs remain intact. No upstream was configured and no remote was changed.

- `ConduitFacadeRendering` now captures facade states and model data from the chunk compiler's Forge model-data map, filtered to the exact section with immutable positions. The captured map belongs to the render task. Rendering no longer traverses the mutable, dimension-keyed facade indexes from a worker thread.
- The shared renderer still supplies the actual facade block state, actual facade model, derived model data and original render layer to native block-model tessellation. This retains the source path for tinting, ambient occlusion, face culling, seed 42, section-local coordinates and native light handling. Visible facades retain their source layers; hidden facades emit into the translucent layer at alpha 85. Pose pushes are paired in `finally`.
- `ChunkRebuildTaskMixin` appends geometry to the actual Forge chunk buffers before translucency sorting and buffer finalization. It starts only newly used layers and includes them in the compiler's normal layer set. `ChunkCompileTaskAccessor` reads the Forge-added field from its declaring superclass. The native mixin is client-only and requires its injection point; it does not silently skip facades if the target changes.
- Added an optional Embeddium `ChunkMeshEvent` / `MeshAppender` adapter. Client setup registers it only when Embeddium is present. It captures the section's model data and uses the appender's region and vertex consumers with the same renderer. The pinned Embeddium artifact is now also a compile-only dependency, retaining its optional development runtime profile.
- Shader detection recognizes Forge's `oculus` mod ID as well as `iris`. The existing behavior that suppresses only ghost facades with an active shader pack is retained. Optional shader API linkage is isolated in a nested bridge. Facade visibility is volatile for chunk-worker reads.
- Added `FixedAlphaVertexConsumer` using the native `color` callback, including default-color handling and alpha bounds, plus three CPU regression tests. Only the helper and tests are bridged into EnderCore's test output.

The native target was inspected in the pinned `forge-1.20.1-47.4.28-sources.jar` and compiled bytecode. Its live local-variable table at the first translucent-layer check matches the callback's captured locals. The inaccessible compile-result local uses Mixin's supported `@Coerce Object` parameter. Source inspection of Mixin 0.8.5 caught that an inherited field cannot be shadowed from the subclass, so the final implementation uses an accessor on the declaring superclass. This is source/bytecode evidence, **not proof that the mixins have applied at runtime**.

The actual pinned Embeddium 0.3.31+mc1.20.1 jar was inspected with `javap`: the event, appender context, vertex-consumer provider and section-local mesh flushing APIs are present. Its Modrinth release record and bytecode evidence are retained locally. No other repository clone was needed.

## Verification

```sh
./gradlew :endercore:test --tests com.enderio.enderio.client.FixedAlphaVertexConsumerTests --console=plain
./gradlew :endercore:test :endercore:runGameTestServer :endercore:jar :endercore:reobfJar --console=plain
```

The targeted final run exits **0**, discovers three tests and passes all three. The initial targeted command failed during test compilation because Minecraft's native vertex-mode enum has no `POINTS` entry; the fixture was corrected to emit complete native quads. No test assertions were weakened.

The complete core run exits **0**:

| Suite | Discovered | Executed | Passed | Failed/errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| EnderCore unit tests, including actual main render helpers | 52 | 52 | 52 | 0 | 0 |
| Native Forge headless GameTests | 121 | 121 | 121 | 0 | 0 |

The new tests send actual baked quads through native bulk vertex emission with differing per-vertex lighting, tinting and a translated pose. They compare every output byte against ordinary native emission, allowing only the expected alpha byte to differ, and verify shared input quads are not mutated. Additional cases cover fluent/default color paths at alpha 0, 85 and 255 and rejection of out-of-range values. These tests do not create Minecraft, a window or a GL context. The 88-file server GameTest bridge is unchanged.

```sh
./gradlew :endercore:compileJava -I scripts/porting/compiler-diagnostics.init.gradle :enderio:portCompilerArguments :enderio:compileJava --no-configuration-cache --console=plain
/Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/javac -J-Xmx4G @build/port-evidence/main-javac.args
```

Normal main compilation still exits **1** at the 400-error cap. Supplemental real-classpath Java 17 compilation exits **1** with **465 errors and 100 warnings**, down from 473 errors. There are no remaining reported diagnostics in the client package; all eight facade-event/API diagnostics are gone. The supplemental command disables annotation processing and does not prove packaging or mixin application. Remaining errors include registry-dependent conduit loading, attachments/data maps, content/fluid APIs and integrations.

Evidence under `build/port-evidence/` (ignored generated artifacts):

- `cycle-022-alpha-tests-initial.log`, `cycle-022-alpha-tests.log`, `cycle-022-core-final.log`, `cycle-022-tests.json`, `cycle-022-gametest-results.xml`.
- `cycle-022-core-arguments.log`, `cycle-022-main-first.log`, `cycle-022-main-gradle.log`, `cycle-022-main-final-gradle.log`, `cycle-022-main-javac.log`, `cycle-022-error-counts.json`.
- `cycle-022-native-chunk-bytecode.txt`, `cycle-022-worldslice-bytecode.txt`, `cycle-022-embeddium-version.json`, `cycle-022-embeddium.jar`, Mixin source inspection files and `cycle-022-artifacts.json`.

The core jar built before this cycle's commit uses the preceding commit's version suffix:

- `endercore/build/libs/endercore-8.2.14.21-1.20.1+441bc6445e.jar`
- SHA-256: `5de276deb8af77fe0a93c36a747a1a5102bdd9048145d17edd7188adb6905b78`
- 223 classes, all Java 17 class-file major 61; no test or bridged main-module classes.

The headless server used `nogui` and `java.awt.headless=true`, completed all tests and shut down. No game client, window activation, audible test or user world was involved. This cycle has its own local commit; no push.

## Remaining validation and next work

The facade backends are implemented but **unverified in-game**. Required checks include actual mixin application on the packaged Forge client, opaque/cutout/translucent and biome-tinted facades, dynamic/connected models, hidden/visible transitions, all facade types, lighting and face culling, resource reload, chunk rebuild/sorting, dimension changes and reconnect. Run with Embeddium absent/present and Oculus absent/present, with shaders both disabled and enabled. Source inspection and CPU alpha tests do not establish those results. The old facade indexes still serve visibility invalidation and the breaking overlay; their unload/reconnect lifecycle needs further review alongside conduit loading.

Continue the remaining common content, world-state and integration conversions. Finish datagen and packaging, then perform silent, non-activating client and real multiplayer validation with full parity evidence. The complete port goal remains active and incomplete.

References inspected:

- [Pinned Embeddium release record](https://api.modrinth.com/v2/version/UTbfe5d1).
- [Mixin 0.8.5 field attachment](https://github.com/SpongePowered/Mixin/blob/0.8.5/src/main/java/org/spongepowered/asm/mixin/transformer/MixinPreProcessorStandard.java).
- [Mixin 0.8.5 callback descriptor/coercion](https://github.com/SpongePowered/Mixin/blob/0.8.5/src/main/java/org/spongepowered/asm/mixin/injection/callback/CallbackInjector.java).
