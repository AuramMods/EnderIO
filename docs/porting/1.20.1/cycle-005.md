# Cycle 005: compile and package EnderCore

Date: 2026-11-17. Starting commit: `a41928e47`.

## Changes

- Replaced the final core Java 21 list API with its Java 17 equivalent, preserving the existing nonempty/single-node checks.
- Adapted vertex emission to native matrix/color/UV/normal methods and explicit `endVertex()`. Converted normalized sprite coordinates to 1.20.1's 16-unit texture coordinates.
- Added a reloadable native `TextureAtlasHolder` for the existing mod GUI sprite assets across namespaces. Native buttons and bundle-style tooltip borders use the actual 1.20.1 widget/bundle textures. Existing mod sprite assets have no GUI scaling metadata to convert.
- Adapted screen frame time, scrolling and selection background rendering to native signatures.
- Added a client-only slot-rendering mixin that preserves vanilla drag/split/count calculations and invokes the existing Ender IO screen extension hook. This keeps foreground overlays and specialized ghost-item rendering available for subsequent main-module porting. Generated/refmapped targets were inspected, but mixin application and visual output are still untested.
- Configured the core mixin processor, refmap, manifest and client config. Removed the temporary scoped-source test init script because the normal module now compiles. Normal tests use an isolated `build/test-runtime` directory.

## Actual validation

| Command | Observed result |
| --- | --- |
| `./gradlew :endercore:compileJava --console=plain` | Passed, zero errors, 20 warnings (deprecated APIs and unchecked use); mixin mappings generated against the actual 1.20.1 SRG mappings |
| `./gradlew :endercore:test :endercore:jar --continue --console=plain` | Test source compilation, jar and reobfuscation passed. Test task failed on the previously observed Forge bootstrap issue. Overall exit 1 |
| `./gradlew :enderio:compileJava --console=plain` | Failed in `:enderio:createMinecraftArtifacts`, before compiling Ender IO source; actual Minecraft source recompile failure recorded below |
| Jar inspection | Forge metadata, mixin config/refmap/manifest and GUI atlas definition present; all class major versions are 61 (Java 17) |
| `git diff --check` | Passed |

Normal test results:

| Class | Passed methods | Failed results | Unexecuted methods |
| --- | ---: | ---: | ---: |
| `NetworkTests` | 10 | 0 | 0 |
| `ChunkBoundLookupTests` | 8 | 0 | 0 |
| `OrderedListCodecTests` | 8 | 0 | 0 |
| `PacketCodecTests` | 10 | 0 | 0 |
| `SyncSlotTests` | 0 | 1 class initialization failure | 7 |

The runner reports 37 results, one failed, zero skipped. Only **36 test methods executed and passed**. The seven registry-dependent methods still need a transformed Forge launch; see cycle 004 for the `NetworkEvent.<init>()` bootstrap failure. No tests were disabled.

The main-module build reached source transformation, mapping and recompilation of 5,453 Minecraft files. It failed at `EntityType.java:204`:

```text
incompatible types: cannot infer type-variable(s) T
invalid constructor reference
EntityType<Entity> cannot be converted to EntityType<? extends FallingBlockEntity>
```

This occurs in the artifact build that applies Ender IO's current access transformers; the core artifact build without those transformers succeeds. The exact transformer/source interaction still needs diagnosis. Do not bypass it by disabling access transformation or deleting dependent gameplay behavior.

Intermediate core artifact (not a release or runnable complete mod):

- `endercore/build/libs/endercore-8.2.14.4-1.20.1+a41928e471.jar`
- SHA-256: `70e57b7e63c105ee01f2048ed113bdcb2f4c6973e25c21b86ad04bf7cc9b19a7`
- The generated filename records the starting commit; it contains this cycle's then-uncommitted changes. Rebuild after future changes; this is not final delivery evidence.

Local evidence (generated, ignored by git): `build/port-evidence/cycle-005-compile-1.log`, `cycle-005-test-jar-1.log`, `cycle-005-enderio-compile.log`, `endercore/build/test-results/test/TEST-*.xml`, `endercore/build/reports/tests/test/index.html` and `endercore/build/mixin/endercore.refmap.json`.

## Next step

Resolve main-module Minecraft/access-transformer rebuilding and establish a proper transformed Forge test launch. Then port the main module and addon against the now-compiling core APIs. Client atlas loading, mixin application, rendering, all gameplay/integrations, persistence, datagen and full packaging remain unverified. No client or server was launched; the non-activating client setup remains required before client testing.
