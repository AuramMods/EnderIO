# Cycle 007 — main lifecycle, events and custom registries

## Changes

- Main and modded-conduit entrypoints obtain Forge's mod event bus/container and register the existing config filenames through `FMLJavaModLoadingContext`. All 23 config source files now use `ForgeConfigSpec`; a source comparison against cycle 006 confirms their keys, defaults, ranges and descriptions are unchanged.
- Converted loader/dist/event-bus imports and corresponding existing Forge event types. Subscribers explicitly select the mod or gameplay bus. Key registration and soul-vial dispenser registration have separate mod-bus subscribers. Client recipe reload and conduit-login listeners have client-only subscribers. Player/level/client tick handlers filter Forge's START/END phases to retain their previous Pre/Post timing.
- Removed NeoForge's additional client `@Mod` entrypoints. Ender IO client setup remains a client-only mod-bus subscriber; modded-conduit client initialization is invoked through Forge's sided executor, retaining lazy optional-module selection.
- Seven custom registries use real Forge `DeferredRegister.makeRegistry` suppliers, preserving every registry key. Built-in and addon registrations reference the keys without resolving an unavailable registry during construction. Serialization codecs defer registry access; DFU dispatch consumes the underlying codecs of the existing map codecs. Runtime lookup, sorting and numeric tick IDs use native Forge registry APIs.
- Built-in packs use Forge `AddPackFindersEvent.addRepositorySource` and `PathPackResources`. Pack IDs retain the original `mod/enderio:...` prefix, priorities, required flags and sources. Athena remains conditional on Athena's presence; feature packs remain opt-in. Their metadata now uses pack format 15, including the generated Athena pack and its generator's native metadata constructor.
- Replaced NeoForge's feature-flag metadata with a production mixin registering the same farming-station, Enderface and Niard IDs before vanilla builds its flag registry, plus a registry accessor. Enderface/Niard keep their required-feature checks. Farming-station registration remains enabled by default as at baseline. **Mixin application, world creation, pack selection and flag persistence have not run yet.**

The Forge public API now exposes `Supplier<IForgeRegistry<T>>` for custom registries. Addons should register against `EnderIORegistries.Keys.*`, resolve suppliers only after registry creation, and use `EnderIORegistries.codec(...)` when declaring static codecs. The bundled addons' registrations were updated; their complete build remains pending.

## Actual verification

All commands ran at the repository root on branch `1.20.1`.

1. `./gradlew :enderio:compileJava --console=plain` ran three times during the cycle; each exited 1 with the normal 400-error diagnostic cap. Final log: `build/port-evidence/cycle-007-main-compile-3.log`. EnderCore was up to date. These are failed main-module compiles, not successful startup validation.
2. Expanded diagnostics used a temporary Gradle init script in `build/port-evidence/report-all-errors.gradle` to raise only the error-reporting limit to 10,000. Its initial invocation failed because the init script also applied to `buildSrc`; the script was corrected to configure the actual Ender IO project.
3. `./gradlew -I build/port-evidence/report-all-errors.gradle :enderio:compileJava --no-configuration-cache --console=plain` emitted 4,634 compiler errors, then exited 1 after Gradle stopped for garbage-collector thrashing. Log: `cycle-007-main-compile-full-2.log` in the evidence directory.
4. After event/API fixes, `./gradlew -I build/port-evidence/report-all-errors.gradle :enderio:compileJava --no-configuration-cache --no-problems-report -Dorg.gradle.jvmargs=-Xmx6G --console=plain` emitted 4,526 errors and 100 warnings. Gradle then ran out of heap while deserializing its error report. Its identified wrapper/daemon PIDs 1794/1901 were terminated with SIGTERM; command exit 143. Log: `cycle-007-main-compile-full-3.log`. These counts include cascading errors and are not counts of independent defects. The normal capped compile subsequently completed its failure reporting normally.
5. Source/resource audits verified the 23 configuration files retain their settings, all three original feature IDs remain registered, and all four built-in pack metadata files specify format 15. `git diff --check` passed. No new unit/GameTests were executed in this cycle; the 36 unit/seven GameTest passes are specifically cycle 006 results.

No client, server or audio subsystem was launched in this cycle. No background test process remains from these commands. Existing main-module tests, actual registry synchronization, feature flags, optional integrations and client/server gameplay are still unverified.

## Remaining work

Main compilation still fails across components/NBT, capabilities, attachments, packet codecs and transport, native registration helpers, recipe/input/serializer APIs and rendering. NeoForge-only events such as capability registration, entity ticks, item-use events, GUI layer registration and client extensions still require behavioral replacements; their changed subscriber annotations alone do not implement them. Data generation and addon compilation remain pending as well.

Next: finish common native Forge type/registration conversions, then replace item data components and capability ownership with persisted 1.20.1 implementations. Continue with actual compilation, preserve integration/feature coverage, and test lifecycle/flag behavior once the main test mod can load. The parity matrix remains unverified.

API references: [Forge registries](https://docs.minecraftforge.net/en/1.20.1/concepts/registries/), [Forge event buses](https://docs.minecraftforge.net/en/1.20.1/concepts/events/), and the [NeoForge pack helper used to preserve pack identifiers and defaults](https://github.com/neoforged/NeoForge/blob/1.21.1/src/main/java/net/neoforged/neoforge/event/AddPackFindersEvent.java). Exact target APIs were also inspected in the resolved Forge 47.4.28 source jar and loader classes.
