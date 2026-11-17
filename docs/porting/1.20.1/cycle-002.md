# Cycle 002: establish the Forge build foundation

Date: 2026-11-17. Starting commit: `82e957e17`.

## Changes

- Retained the three-module 8.x source baseline and switched the existing ModDevGradle setup to its legacy Forge plugin, using Forge 47.4.28, Java 17 and 1.20.1 Parchment mappings.
- Converted production and GameTest mod descriptors to Forge `mods.toml`; added 1.20.1 pack metadata and configured the mixin annotation processor/refmap and production reobfuscation, including the API jar.
- Selected pinned 1.20.1 integrations and remapped dependency configurations; added known AE2/FTB runtime dependencies and Forge renderer/shader alternatives.
- Updated build/test/release workflow platform settings. No workflow was dispatched, remote branch pushed, message sent or artifact published.
- Replaced the unavailable NeoForge unit-test launcher configuration with Forge test compile dependencies. All test Java sources and assertions remain; NeoForge test-framework imports and bootstrap behavior still need conversion. Tests have not passed or run.
- Added isolated client/server/GameTest directories and a Gradle preparation task that preserves unrelated option keys while setting all sound categories to zero, narrator off, accessibility onboarding off, windowed mode and continued ticking while unfocused. The server EULA is accepted in these disposable profiles. These are settings only: focus-safe window creation still needs implementation and verification before any client launch.
- Captured the immutable source inventory and created 257 concrete feature declaration rows, plus factory expansions and modded-conduit scenarios.

## Actual validation

| Operation | Observed result |
| --- | --- |
| Baseline `./gradlew help --console=plain` | Passed before changes, Gradle 9.8.2 configured all modules |
| Initial legacy Forge `help` | Failed because the legacy plugin adds project repositories while the source build prohibited them; changed central repository policy and added Forge Maven |
| `./gradlew :endercore:compileJava --console=plain` | Forge/Minecraft artifacts downloaded, decompiled, patched, mapped and recompiled successfully; EnderCore compilation failed at the existing 400-error reporting cap |
| `./gradlew :enderio:dependencies --configuration compileClasspath --console=plain` | Dependency graph report succeeded; no `FAILED` entries; not a jar-download or integration test |
| `./gradlew :endercore:generateModMetadata :enderio:generateModMetadata :enderio-modded-conduits:generateModMetadata :enderio:prepareClientProfile :enderio:prepareServerProfile :enderio:prepareGameTestClientProfile :enderio:prepareGameTestServerProfile --console=plain` | Passed; parsed all generated TOML descriptors, confirming `[1.20.1]`, Forge `[47.4.28,48)` and loader `[47,)`; inspected actual options and EULA files |
| `python3 scripts/porting/capture_baseline.py` | Captured 487 registration declarations, 469 content/integration source paths, 180 source test methods and 4,173 data/blockstate IDs |

Representative compile failure:

```text
EnderCore.java:4: error: package net.neoforged.fml.common does not exist
import net.neoforged.fml.common.Mod;
...
400 errors
> Task :endercore:compileJava FAILED
```

Full local logs: `build/port-evidence/cycle-002-{help,compile,dependencies,metadata-profiles}.log`. Generated platform sources for subsequent port work: `endercore/build/moddev/artifacts/forge-1.20.1-47.4.28-sources.jar`. Those generated files are intentionally ignored by git; this record preserves the results across checkouts.

## Remaining work and next cycle

EnderCore still uses NeoForge registries, capabilities, events and newer Minecraft data/network APIs. Port those foundations against the actual generated Forge sources, rerun compilation, and then move through Ender IO and the addon. Source porting, test framework migration, complete dependency artifact resolution, datagen, production packaging and all runtime validation remain incomplete. No game window or server was launched in this cycle.
