# Ender IO: Forge 1.20.1 port tracker

Prepared on 2026-11-17. Status: implementation in progress. Cycle 008 adds native Forge capability attachment, refresh and neighbor tracking, with main-machine/conduit wiring. EnderCore passes 36 unit tests and 16 headless GameTests. Main compilation still fails at the normal 400-error cap; supplemental direct Java 17 compilation reports 3,458 source errors without the previous diagnostic transport OOM. No client has been launched.

The executable goal prompt is in [PORTING_1.20.1_GOAL.md](PORTING_1.20.1_GOAL.md). Keep this tracker current during implementation; source inspection is not runtime verification.

## Objective and source baseline

Port the complete implemented feature set of this checkout to Minecraft 1.20.1 on **Minecraft Forge**, with Java 17 compatible artifacts. Completion requires a working packaged mod in single-player and on a dedicated Forge server, supported integrations, working persistence, and recorded verification of the feature inventory.

| Reference | Recorded state |
| --- | --- |
| Working repository | `AuramMods/EnderIO` |
| Local destination branch | `1.20.1`, created from the checked-out `1.21.1` source |
| Feature baseline | `4c482da69e76f2b46bfeeac64ef0ac277c318eaa` |
| Baseline release series | 8.2 |
| Baseline platform | Minecraft 1.21.1, NeoForge 21.1.216, Java 21 |
| Historical Forge reference | `origin/1.20.1` at `18ee1b17bf6879298e911fff751204b07f2e4228` |
| Historical platform | Minecraft 1.20.1, Forge 47.1.1, Java 17, release series 6.2 |
| Target platform | Minecraft 1.20.1, Forge 47.4.28 (pinned; runtime verification pending), Java 17 |

The two upstream branch tips were checked with `git ls-remote` against `https://github.com/Team-EnderIO/EnderIO.git` and match the fork's fetched remote references. The historical Forge branch already implements an older version of this mod; merely checking it out would not preserve the current feature set. The branches have diverged: at preparation, 154 commits are unique to the historical branch and 951 to the current source baseline.

The new local `1.20.1` branch intentionally has no upstream. The existing remote `origin/1.20.1` has not been changed. Do not force-push over it. Local commit history is sufficient for the requested port cycles; decide remote integration explicitly if publishing the branch is later requested.

No `.codegraph/`, applicable on-disk `AGENTS.md`, or `docs/MANUAL_CHANGE_PROTOCOL.md` was found during preparation. The instructions supplied in chat still apply. Recheck applicable instructions when resuming; if the manual change protocol appears, follow its PR/worktree/automerge workflow.

## Repository map and scale

| Module | Role | Java source files at baseline, including tests and package descriptors |
| --- | --- | ---: |
| `endercore` | Shared client utilities, serialization, graph and other common support | 131 |
| `enderio` | Public APIs, registrations, gameplay, client code, compatibility, data generation, tests | 1,046 |
| `enderio-modded-conduits` | AE2, Mekanism chemical/heat, and Refined Storage conduits | 58 |

EnderCore is already part of this repository and bundled by the Ender IO build. No additional repository clone is currently needed. The historical branch has 909 Java files under a different layout: `src/api`, `src/core`, `src/main`, `src/machines`, `src/conduits`, and `src/armory`.

Current build ownership is in `settings.gradle.kts`, `gradle/libs.versions.toml`, `buildSrc/src/main/kotlin/mod-common-conventions.gradle.kts`, and the three module build files. It uses ModDevGradle 2.0.148 and Gradle 9.8.2. The historical build uses ForgeGradle 6 with additional mapping/mixin plugins; its old repositories and versions are reference material, not assumed to remain resolvable.

The baseline tests include 33 Java files under `src/test` and 34 under `src/gametest`, including helpers and package descriptors. These are file counts, not executed test counts. The build now targets Java 17; remaining NeoForge test-framework sources and game bootstrap must still be ported.

## Main conversion areas

These are source-grounded work estimates. Exact target APIs and dependency versions must be confirmed by compilation and execution.

| Area | Evidence in current source | Target work and behavioral risk |
| --- | --- | --- |
| Build and packaging | NeoForge ModDevGradle, Java 21 toolchain, three `neoforge.mods.toml` templates, nested EnderCore/addon/GraphLib jars | Select compatible Forge tooling and Gradle; preserve modules and bundled dependencies; generate Forge `mods.toml`, mixin metadata and reobfuscated distributable jars; update CI |
| Java language/library use | `getFirst()` in 27 Java files; a `Sequenced` reference | Replace unsupported Java 21 constructs and APIs while retaining order and empty-collection behavior |
| Loader lifecycle and registries | `net.neoforged` occurs in 436 Java files | Port event buses, subscribers, deferred registrations, configs, registry access and client/server separation; namespace replacement alone is insufficient |
| Item and fluid data | `DataComponent` in 114 files; `init/EIODataComponents.java` stores energy, souls, filters, painting and machine state | Implement 1.20.1 NBT-backed state with equivalent defaults, copying, equality, stacking, persistence and synchronization |
| Networking | `StreamCodec` in 129 files; `CustomPacketPayload` in 36; `foundation/network/EIONetwork.java` uses `PayloadRegistrar` | Port packet serialization and registration to Forge's networking API; preserve direction, server authority, threading, menu and world synchronization |
| Capabilities | `init/EIOCapabilityProviders.java`, `api/EnderIOCapabilities`, conduit capability caches | Adapt to Forge capability providers and `LazyOptional`; preserve sided access, invalidation, unload/reload and item capability behavior |
| Attachments/data maps | `init/EIOAttachments.java`, `init/EIODataMaps.java` | Replace with suitable Forge capabilities, saved state or reloadable data; preserve obelisk/fire-crafting managers, VAT reagents, range extenders and grinding-ball data |
| Serialization and registries | `HolderLookup` in 104 files, `MapCodec` in 61 | Adapt to available registry/codec APIs and NBT save signatures; verify serialization round trips and unload/restart behavior |
| Recipes and resources | Current generated resources and GameTest data use `recipe` and `tags/item` paths | Generate 1.20.1 layouts and JSON schemas, including plural directories where required; translate tags, ingredients, loot, advancements and pack metadata |
| Rendering and UI | Client code, dynamic conduit/facade rendering, painted blocks, screens, particles, six main-module mixin Java files | Port render and model APIs, GUI hooks, key mappings, access transformers and mixin targets; test visual results and interactions |
| Optional integrations | Version catalog and `compat`/modded-conduit modules | Pin Forge 1.20.1 variants and adapt older APIs, especially Refined Storage and Mekanism; test both presence and absence |
| Test infrastructure | `gametests/EnderIOTests.java` uses NeoForge test framework configuration/dumpers | Move tests and fixtures to runnable Forge-compatible infrastructure; preserve coverage and report actual executed counts |

Counts above are lexical file matches across module Java sources, including tests and datagen; they overlap and are not estimates of required edits.

## Implementation strategy

Retain the current multi-module feature baseline. Reuse proven 1.20.1 Forge implementations from the recorded historical commit where they fit, then adapt them to current semantics. Do not replace newer conduit, soul, machine or filter behavior with historical behavior just to reduce compile errors.

First expand the inventory below into concrete registrations/features and map each to its source, port status and verification evidence. Inventory the existing opt-in features separately: the source declares flags for farming station, Enderface and Niard, but only Enderface/Niard registrations require them; farming station is currently enabled by default. Preserve existing defaults and implemented opt-in behavior. Unimplemented historical wishlist items and unrelated newer branches are outside this baseline.

Prefer Forge-native adapters and shared utilities in this project's namespaces. Do not supply fake Minecraft/NeoForge classes or empty implementations that merely satisfy compilation. Preserve public APIs where practical; document unavoidable source/API changes for addon authors.

| Cycle area | Work | Status |
| --- | --- | --- |
| Preparation | Record source references, create local branch, write tracker and goal | Complete |
| Inventory and build | Expand parity/dependency matrix; establish Forge 1.20.1 toolchain, metadata, artifacts and silent test profiles | In progress: EnderCore compiles/reobfuscates and main-module Minecraft artifacts rebuild; full packaging and non-activating client launch pending |
| Common foundation | Java 17 changes, lifecycle, registries, NBT state, codecs, networking, capabilities, attachments, reloadable data | In progress: core passes 36 unit/16 GameTests; native capability ownership, neighbor tracking and main bindings added. Main NBT/network conversion and actual gameplay validation pending |
| Core content | Port every implemented block/item/entity, machine, conduit, filter, tool, soul and travel feature | Pending |
| Assets and client | Data generation, recipes/tags, models, textures, rendering, screens, particles, input, mixins | In progress: core sprite atlas, slot-rendering hook and native vertex/UI APIs compile; all visual/runtime checks pending |
| Integrations | Adapt each supported integration and modded conduit; test with dependencies present and absent | Pending |
| Verification | Port existing tests, add meaningful regressions, exercise gameplay, multiplayer, persistence and packaged artifacts | Pending |
| Delivery | Clean build, complete evidence matrix, usage/migration notes and final committed artifact record | Pending |

These are work areas, not a requirement to put a huge phase into one commit. Each bounded implementation/verification cycle gets its own commit. Early commits may have documented build failures while dependent systems are being converted. Do not describe them as working until verified.

## Feature and integration evidence matrix

The [concrete feature matrix](porting/1.20.1/features.md) contains 257 declaration rows and explicit factory expansions. The [fixed baseline inventory](porting/1.20.1/baseline.json) records 487 registration declarations, 469 content/integration source files, 180 captured test methods and 4,173 data/blockstate IDs. These are source discoveries, not runtime/test results. Every row starts **unverified**. Each completed row must name the tested build/commit, profile, commands or procedure, expected and observed results, and evidence location.

| Feature group | Required verification | Status |
| --- | --- | --- |
| Registration and resources | All baseline registrations accounted for; creative inventory, crafting progression, loot, tags, configs and resource reload work | Unverified |
| Conduits and filters | Energy/item/fluid/redstone transfer, bundled connections, modes/channels/priorities, all filter types, facades, wrench/probe, split/merge and chunk boundaries | Unverified |
| Machines | Every registered machine's recipe/behavior, sided I/O, redstone controls, capacitors, menus, automation, break/place and saved state | Unverified |
| Generation and storage | Stirling/soul generation, solar panels, capacitor-bank multiblocks, tanks, charging and resource accounting | Unverified |
| Souls and spawning | Vials, broken/powered spawners, soul binding, soul-specific bonuses, entity capture/release and persistence | Unverified |
| Travel and tools | Travel targets, anchors/items, coordinate selection, magnets and other implemented tools/armory, permissions and multiplayer sync | Unverified |
| World and decorative content | Painting, glass, decor, fire/cold-fire crafting, enchanter, obelisks, farming and other implemented content | Unverified |
| Opt-in content | Actual implemented feature-flag behavior, preserved defaults and explicit enablement tests | Unverified |
| Client presentation | Textures/models, facades/paint, particles, screens/tooltips/key mappings, recipe UI, resource reload and reconnect | Unverified |
| Modded conduits | AE2 network connectivity, Mekanism chemicals and heat, Refined Storage networks, optional-module load isolation | Unverified |
| Persistence and lifecycle | Save/reopen, dedicated-server restart, unload/reload, dimensions, player reconnect, block removal/replacement, no duplication/loss | Unverified |
| Distribution | Final remapped/reobfuscated jars load in clean Forge installations without a development classpath | Unverified |

Compatibility inventory: JEI, Jade, CC:Tweaked, Almost Unified, Curios, FTB Ultimine, LaserIO, Inventory Sorter hooks, Athena/connected textures, AE2, Mekanism and Refined Storage. Also inspect the existing Sodium/Iris-specific compatibility code and determine appropriate Forge 1.20.1 equivalents. Each entry needs an exact dependency version, artifact coordinate/source, supported behavior, presence/absence result and any required adaptation. Cycle 002 selected pinned target artifacts from author Maven metadata and project release APIs; see the [dependency record](porting/1.20.1/dependencies.md). Actual integration behavior remains unverified. An unavailable dependency is a tracked unresolved problem, not permission to delete an integration or claim completion.

New-world operation and save/reload within this port are required. Audit historical Ender IO 6.x identifiers and NBT formats, test upgrades using disposable fixtures/copies, and implement necessary migration or document any genuinely unsupported upgrade explicitly. Never test on the user's real worlds. Minecraft 1.21.1 world downgrades are not promised by this port.

## Quiet background runtime testing

- Use isolated development/test game directories. Before the first launch of each profile, seed its options with master/category volumes at zero, narrator disabled (`narrator:0`), and onboarding/accessibility narration suppressed. Keep these settings through relaunches. Do not change system-wide sound or the user's normal Minecraft profile.
- Prefer dedicated-server and GameTest runs for automated behavior checks. Use `nogui` where the server launcher supports it.
- Client testing must not activate, raise, or focus a game window, steal keyboard/mouse input, or open fullscreen. Shell backgrounding alone does not establish this: Minecraft/LWJGL can still activate a window.
- Implement and directly verify a non-activating client launch/test mechanism on this host (or an isolated display/session). Test-only launch instrumentation must stay out of the distributed mod. Do not rely on restoring focus after stealing it.
- Use logs, reports and background captures/automation. Keep game ticking while unfocused when an automated scenario needs it. Do not use CUA or global key injection in ways that take over the user's active app.
- Set `eula=true` for disposable test servers; the user has already accepted. Track test processes, stop only processes created for this work and cleanly save/shut down servers.
- If a real host limitation prevents a required client test within these constraints, record the actual failure and keep client verification incomplete. Continue independent port work; ask only for a specific unavoidable intervention.

## Completion evidence

Completion requires all of the following actual results, with no known unresolved port blockers:

1. A reproducible Java 17 compatible build of all shipped modules against pinned Minecraft 1.20.1/Forge 47.x dependencies; runnable build/data/test commands and corrected CI.
2. Successful datagen and loading of all expected recipes, tags, loot, advancements, models and textures. Resolve port-introduced resource errors.
3. All applicable unit and GameTests executed and passing. Report discovery/execution/pass/fail/skip counts; a successful process with zero discovered tests is not evidence of coverage. Preserve or replace the baseline tests meaningfully.
4. Verified single-player gameplay and client connection to a real dedicated Forge server, including representative interactions in every feature family and detailed testing of high-risk state/transfer paths.
5. Persistence across save/reload, chunk unload, dimension change, disconnect/reconnect and server restart; no known port-caused duplication, deletion, stale networks or desynchronization.
6. Supported optional integrations tested with compatible versions and the core mod tested without optional mods. No optional dependency may become accidentally mandatory through eager class loading or packaging.
7. Smoke tests of the final packaged jars in clean Forge client/server profiles, using the intended bundled dependencies. Record filenames and checksums, exact runtime versions, test results and remaining limitations.
8. All source changes and tracker updates committed, final feature matrix reconciled to the recorded baseline, and clear installation/build/test/migration notes. Do not publish releases or overwrite remote branches implicitly.

Compilation, a title-screen launch or a single working machine does not meet this definition. Missing evidence remains missing; scope cannot be reduced silently to make the checklist pass.

## Cycle log

| Cycle | Result | Verification | Commit |
| --- | --- | --- | --- |
| 001: preparation | Created local `1.20.1` from the fixed 1.21.1 baseline; recorded architecture, historical Forge reference, work areas and goal | Read-only source/build/test inspection, fetched fork refs and compared upstream tips; documentation diff review; no build/runtime tests | `82e957e17` |
| 002: Forge build foundation | Legacy Forge build plugin, Forge 47.4.28/Java 17, metadata/refmap/reobfuscation configuration, dependency pins, CI platform settings, isolated muted profiles, source inventory and feature matrix | Minecraft artifacts generated; EnderCore compilation reached source errors (400-report cap); metadata/profile tasks passed; no gameplay tests or client/server launch. [Evidence](porting/1.20.1/cycle-002.md) | `7cf58a36d` |
| 003: core registration and serialization | Native Forge registration/capability factories, NBT fluid/item helpers, recipe/datagen API adaptations and bounded ordered-list codec | Eight isolated codec tests passed; full EnderCore compile fails with 319 errors; runtime remains unverified. [Evidence](porting/1.20.1/cycle-003.md) | `7d4dbcaa2` |
| 004: core networking and synchronization | Native Forge SimpleChannel transport, bounded packet codecs, menu/legacy slot handling, block-entity save and capability APIs | 18 isolated tests pass; one test-class bootstrap failure prevents seven further methods from executing. Full EnderCore compile fails with 25 errors. [Evidence](porting/1.20.1/cycle-004.md) | `a41928e47` |
| 005: compile and package EnderCore | Java 17 graph API, native GUI/vertex calls, reloadable sprite atlas and slot-rendering mixin; retired scoped test compilation | EnderCore compile/jar/reobfuscation pass; 36 normal tests pass, one bootstrap failure remains. Main-module Minecraft recompile fails. [Evidence](porting/1.20.1/cycle-005.md) | `87292135b` |
| 006: access transformation and native tests | Forge SRG access transformers, protected falling-block constructor, Java 17 record patterns, native core GameTest module | Core check passes: 36 unit tests plus seven GameTests. Main-module artifacts rebuild and source compilation reaches 400-error cap. [Evidence](porting/1.20.1/cycle-006.md) | `df4edbc39` |
| 007: main lifecycle and registries | Forge startup/configuration, explicit mod/game event buses and tick phases, deferred custom registries/codecs, client isolation, native built-in packs and feature flags | Main compile exits 1 at 400-error cap. Expanded diagnostics report 4,526 errors before tooling OOM; no new runtime tests. Configuration/flag/pack source audits pass. [Evidence](porting/1.20.1/cycle-007.md) | `b987675fc` |
| 008: capability ownership and neighbor updates | Native Forge tokens, item/block attachments, owned-provider refresh, neighbor invalidation/discovery, machine/conduit cache wiring and direct compiler diagnostics | Core check passes: 36 unit/16 GameTests. Main Gradle compile fails at 400-error cap; supplemental javac reports 3,458 errors. [Evidence](porting/1.20.1/cycle-008.md) | Commit containing cycle 008 |

For each later cycle add: objective, changes, exact commands and exit/results, evidence paths, unresolved failures, next step and commit reference. A commit can identify its own entry by cycle number; add its hash in the following cycle if useful. Do not amend/squash earlier cycle commits merely to fill this table.

## References

- Recorded source: [Team-EnderIO 1.21.1](https://github.com/Team-EnderIO/EnderIO/tree/4c482da69e76f2b46bfeeac64ef0ac277c318eaa).
- Historical implementation: [Team-EnderIO Forge 1.20.1](https://github.com/Team-EnderIO/EnderIO/tree/18ee1b17bf6879298e911fff751204b07f2e4228).
- [Forge 1.20.1 setup, Java 17, build and runtime tasks](https://docs.minecraftforge.net/en/1.20.1/gettingstarted/).
- [Forge capabilities, providers and lifecycle](https://docs.minecraftforge.net/en/1.20.1/datastorage/capabilities/).
- [Forge SimpleChannel networking](https://docs.minecraftforge.net/en/1.20.1/networking/simpleimpl/).
- [Forge GameTests and server execution](https://docs.minecraftforge.net/en/1.20.1/misc/gametest/).
