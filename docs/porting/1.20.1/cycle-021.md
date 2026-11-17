# Cycle 021 — Native preview buffers, GUI sprites and remaining client APIs

Status: progress, not completion. Starts at `2611edf17`, after cycle 020. Main compilation still fails. Client presentation and actual GPU behavior remain unverified; no client was launched.

## Changes

- Ported I/O configuration preview buffers to native `BufferBuilder`/`MultiBufferSource` APIs. Ghost and solid previews have separate reusable buffer pools instead of borrowing the main GUI builders, whose batch state is owned by those builders on 1.20.1. Flush preceding GUI work before drawing the preview. Selection quads emit attributes in the native vertex-format order and terminate every vertex.
- I/O preview clicks now send the existing server-authoritative cycle packet for both legacy and current configurable machines. Removed the legacy-only local mutation path and the remaining obsolete packet-distributor call. Existing server validation remains in effect; actual screen interaction still needs client testing.
- Ported Enderface geometry compilation and translucent re-sorting to native rendered buffers and saved sort states. The new production `PreviewLayerBuffer` retains CPU sorting data, emits index-only updates and clears obsolete sorting state when a layer is rebuilt empty or opaque. Geometry and sorting positions use the same world-origin-relative coordinates. Reuse native allocation pools across screens, and close GPU buffers when the screen is removed. Native model-data lookup and fluid-vertex transformation preserve block/fluid preview content. Camera/raycast alignment, custom render layers and GPU output remain unverified.
- Extended the existing reloadable GUI sprite atlas with cropped blitting. The CPU vertex path preserves atlas offsets, logical sprite dimensions, the current GUI pose and fractional UVs. Zero or negative fill emits no backwards geometry. Converted main GUI sprite calls, including progress/energy bars, activity indicators, conduit/glass icons and I/O controls.
- Added `SpriteButton` in EnderCore for the existing image-only buttons. It uses native `Button` input handling and the GUI atlas without adding a vanilla button background. Vat, XP-obelisk and range buttons keep their sprites, callbacks, tooltips and dimensions. Experience bars use the actual native `textures/gui/icons.png` regions at `(0,64)` and `(0,69)`; 1.21 vanilla GUI sprite IDs are unavailable on 1.20.1.
- Ported travel rendering's bulk-quad calls, packed colors and partial-tick access. Backdrop quad caching now follows the actual baked-model identity so resource reload can replace atlas coordinates. Key mappings use Forge's real lazy utility.
- Ported probe scroll events to Forge's single delta. Forge already folds horizontal scroll into that delta on macOS; the new client-only `MouseHandlerMixin` preserves horizontal-only probe input on other platforms without processing the same gesture twice. It respects the current window, screen and overlay. Mixin application and real input remain unverified.
- Ported skull model RGBA callbacks, fluid display-name tooltips, tooltip registry context, dye-color conversion and Java 17 list access. Range particles use native `shouldCull()` to retain their previous uncullable rendering and emit complete native vertices. Moved capacitor-bank render bounds from the unavailable renderer callback to the Forge block-entity hook.
- Migrated fused glass to native `AbstractGlassBlock` and painted sand to native `SandBlock`, including native block tooltips and falling-block NBT capture. Inspected the pinned base classes to preserve empty visual shape, glass shading/skylight behavior and dust/falling behavior. All existing custom collision, lighting, blast-resistance and paint behavior remains in source; its full runtime validation is pending.

The implementation was checked against the pinned `forge-1.20.1-47.4.28-sources.jar`, including `BufferBuilder`, `VertexBuffer`, `MultiBufferSource`, native chunk transparency re-sorting, `GuiGraphics`, `Gui`, `MouseHandler`, particle/block-entity hooks, glass and sand base classes. This is API evidence, not a substitute for running the client.

## Verification

```sh
./gradlew :endercore:test --tests com.enderio.core.client.GuiSpritesTests --tests com.enderio.enderio.client.PreviewLayerBufferTests --console=plain
./gradlew :endercore:test :endercore:runGameTestServer :endercore:jar :endercore:reobfJar --console=plain
```

The final targeted run exits **0**, discovers six tests and passes all six. The complete core run exits **0**:

| Suite | Discovered | Executed | Passed | Failed/errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| EnderCore unit tests, including actual main render helpers | 49 | 49 | 49 | 0 | 0 |
| Native Forge headless GameTests | 121 | 121 | 121 | 0 | 0 |

Three new GUI tests inspect real native vertex buffers for cropped UVs/atlas offsets/transformed positions, logical versus physical sprite resolution, and empty/negative fill. Three preview tests check repeated transparency ordering from opposite viewpoints, index-only payloads, and removal of old sorting state when rebuilding empty or opaque geometry. These tests allocate CPU buffers/images only; they do not initialize Minecraft, a display or GL context. The 88-file server GameTest bridge remains unchanged. The two main render helpers and their JUnit sources are compiled into test output only.

The initial targeted run discovered six tests, passed five and failed one because the test assumed an index-only result's unused `vertexBuffer()` view would have zero length. Inspection of the real native implementation showed that this view still uses the original vertex count, while `DrawState.indexOnly()` controls upload and `bufferSize()` bounds the actual payload. The corrected test asserts the index-only flag, exact 24-byte index payload, retained vertex count and changed ordering across repeated re-sorts. No production exception was suppressed and no required ordering assertion was removed.

```sh
./gradlew :endercore:compileJava -I scripts/porting/compiler-diagnostics.init.gradle :enderio:portCompilerArguments --no-configuration-cache --console=plain
/Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/javac -J-Xmx4G @build/port-evidence/main-javac.args
./gradlew :enderio:compileJava --console=plain
```

Argument export exits **0**. Final supplemental compilation exits **1**, with **473 errors and 100 warnings**, down from 613 in cycle 020. Initial diagnostics also exposed fastutil lambda inference and a protected vanilla texture constant; both were fixed using explicit lambda types and the native texture resource ID. Normal Gradle main compilation exits **1** at the **400-error cap**, with 100 warnings. All remaining reported errors under the client package belong to the unported facade section-rendering hook; failures in other packages still block the full mod, datagen and addon packaging. Supplemental compilation uses actual sources/classpath with annotation processing disabled and does not prove packaged mixin correctness.

Local evidence under `build/port-evidence/`:

- `cycle-021-render-tests-initial.log`, `cycle-021-initial-unit-results/`: original 5/6 targeted result and failure details.
- `cycle-021-render-tests-final.log`, `cycle-021-core-final.log`, `cycle-021-tests.json`, `cycle-021-gametest-results.xml`: final targeted/full results and test identities.
- `cycle-021-core-arguments.log`, `cycle-021-main-first.log`, `cycle-021-main-second.log`, `cycle-021-main-javac.log`, `cycle-021-main-gradle.log`, `cycle-021-error-counts.json`: compiler results.
- `cycle-021-artifacts.json`: core jar inspection.

Intermediate core artifact, named from this cycle's starting commit:

- `endercore/build/libs/endercore-8.2.14.20-1.20.1+2611edf17a.jar`
- SHA-256: `071ce5aeddd3f46e3106c8beed143a9de12ec14dee33b2e5bfc680815d83eb68`
- 223 classes, all class-file major 61; no test classes or bridged main-module classes.

The isolated GameTest server ran with `nogui` and `java.awt.headless=true` and shut down cleanly. All owned test/compiler sessions finished. No client, window activation, audible test or user world was involved. This cycle has its own local commit; no push or remote change.

## Next work

Replace the NeoForge-only additional-section-geometry hook with real Forge facade rendering, preserving both visible facades and the translucent view when hidden. Complete registry-dependent conduit loading, attachments/data maps, remaining content/fluid APIs and integrations. Validate actual rendering, resource reload, input, GPU lifetime and shader compatibility using a verified non-activating client setup once the full mod can load. Complete datagen, packaged single-player/multiplayer and persistence/parity evidence before marking the goal complete.
