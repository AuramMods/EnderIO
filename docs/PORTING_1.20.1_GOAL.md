# Goal: fully port Ender IO to Minecraft Forge 1.20.1

Fully port the entire implemented Ender IO feature set in this repository to Minecraft 1.20.1 on Minecraft Forge. Continue implementing, testing and fixing until it is fully functional in single-player and on a dedicated Forge server. A successful compile or title-screen launch is not completion.

## Repository and baseline

- Work in `/Users/cyberpwn/development/workspace/AuramMods/EnderIO` on the prepared local branch `1.20.1`.
- Read `docs/PORTING_1.20.1.md`, applicable `AGENTS.md` instructions and the existing cycle history before continuing. Keep that tracker current.
- Preserve the implemented features of the 1.21.1 NeoForge source at `4c482da69e76f2b46bfeeac64ef0ac277c318eaa`, including `endercore`, `enderio` and `enderio-modded-conduits`.
- Use the historical Forge implementation at `18ee1b17bf6879298e911fff751204b07f2e4228` as a reference and source of reusable code. Reverting to that older release does not satisfy the goal.
- The remote `origin/1.20.1` is the historical branch, and its history differs from this local branch. Preserve it; do not force-push or overwrite it. Do not publish releases without a separate request.
- EnderCore is already included here. Inspect or clone other Team-EnderIO repositories only when a concrete dependency requires it. Stay within this port's scope.

## Implementation requirements

1. Expand the tracker's feature and dependency matrix into concrete baseline registrations, gameplay features, integrations and test cases. Record what is implemented and what is already experimental or disabled by default. Preserve implemented opt-in features and their defaults; do not expand the goal to unrelated unfinished features or other branches.
2. Establish a reproducible Minecraft 1.20.1/Forge 47.x build with pinned compatible dependencies and Java 17 compatible output. Convert build tooling, metadata, mappings, access transformers, mixins, nested dependencies, datagen, run configurations, tests, CI and packaging for every shipped module.
3. Replace NeoForge-specific APIs and newer Minecraft/Java APIs with working 1.20.1 equivalents. Address lifecycle/registries, networking, item/fluid data components and NBT, attachments, data maps, capabilities and invalidation, serialization, recipes/tags/resources, rendering and menus. Preserve behavior and state; do not introduce empty stubs, fake platform classes or disable modules to obtain a green build.
4. Port all implemented conduits, filters, machines, generators, storage, capacitor banks, souls, vials, spawners, tools/armory, travel, painting, glass, decorative content, farming, obelisks and other baseline content. Derive the exhaustive list from source; this list is not permission to omit an unlisted feature.
5. Port optional integrations and bundled modded conduits against compatible Forge 1.20.1 APIs, including AE2, Mekanism, Refined Storage, JEI, Jade, CC:Tweaked, Almost Unified, Curios, FTB Ultimine, LaserIO and applicable remaining hooks. Resolve connected-texture and shader/render compatibility appropriately for this platform. Test supported integrations both present and absent. An unavailable dependency or missing behavior stays unresolved; document it and pursue an equivalent implementation rather than silently dropping it.
6. Preserve registry IDs and sensible save formats. Verify new worlds and save/reload within the port. Audit historical Ender IO 6.x save compatibility using disposable fixtures/copies, implement necessary migration where supported and document any unsupported upgrade clearly. Never use my real worlds for tests or imply that Minecraft 1.21.1 worlds can safely be downgraded.

## Development cycles and persistence

- Work in bounded implementation/verification cycles. Each cycle must have its own new git commit containing its changes and tracker update. Do not squash or amend away previous cycles. Preparation is cycle 001; continue from there.
- Record each cycle's purpose, changes, actual commands/results, evidence paths, unresolved failures and next step. Intermediate commits may have honestly documented failures while the port is incomplete; continue resolving them.
- Run the real build/test/launch operation and diagnose its actual output. Do not invent speculative preflight checks that block a command based on indirect evidence. Useful validation must test the actual behavior it claims to validate.
- If `.codegraph/` exists, use CodeGraph before searching/reading code to locate or understand it. Otherwise use normal repository tools without creating an index.
- If `docs/MANUAL_CHANGE_PROTOCOL.md` exists, follow its PR/worktree/automerge process while keeping `1.20.1` as the destination branch.
- Make routine implementation decisions autonomously, preserve unrelated work and give concise progress updates. Continue through ordinary compile errors and test failures. Ask only for information or intervention that genuinely cannot be resolved from the repository, tools or documentation.

## Silent, background testing

- Never let a game launch, relaunch or automated interaction steal my desktop focus, activate/raise a window, capture my keyboard/mouse or enter fullscreen. Shell backgrounding alone is insufficient. Implement and directly verify a non-activating client test setup or isolated display/session; restoring focus after stealing it does not satisfy this requirement.
- Use isolated test game directories. Before every profile's first launch, disable master/category sound, narrator and onboarding/accessibility narration; keep them disabled on subsequent launches. Do not change system-wide audio or my normal Minecraft settings.
- Prefer headless dedicated-server and GameTest runs for automated checks, but still perform the required client/rendering/UI and multiplayer validation. Use background logs, reports and captures/automation. Keep test-only launch instrumentation out of release jars.
- Set `eula=true` automatically for test servers; I accept the Minecraft server EULA. Track and cleanly stop only the test processes created for this work.
- If an observed host limitation prevents a required test under these constraints, keep that test explicitly incomplete, continue independent work and request only the specific unavoidable intervention. Never claim runtime validation that did not happen.

## Definition of complete

Mark the goal complete only when all of these are demonstrated on the final code:

1. All shipped modules build reproducibly for Minecraft 1.20.1/Forge with Java 17 compatible distributable jars and documented commands.
2. Data generation and resource loading succeed, with expected registrations, recipes, tags, loot, models, textures and progression accounted for against the baseline.
3. Existing applicable unit/GameTests are ported and passing, with meaningful regression coverage for changed state, transfer and lifecycle behavior. Record actual discovered/executed/passed/failed/skipped counts; zero discovered tests is not a passing suite.
4. The packaged mod works in a clean Forge client, single-player world and real dedicated-server multiplayer session. Exercise every feature family, GUIs and synchronization; investigate port-related errors in logs and visual output.
5. Verify conduit routing/split/merge, transfer accounting, filters, machine automation, multiblocks, capabilities, souls, travel and persistence across chunk unload/reload, save/reopen, reconnect, dimension changes and server restarts. Resolve known port-caused crashes, duplication, loss and desynchronization.
6. Supported integrations work with pinned compatible versions, and core gameplay works without optional mods. Final packaged-jar tests must not depend on an IDE/development classpath.
7. The parity matrix has evidence for every required feature, no known unresolved port blockers remain, and all work is committed. Provide the final commit, jar locations/checksums, exact dependency versions, installation/build/test instructions, results and any explicit migration limitations.

Do not redefine success to mean a partial port, remove difficult features, weaken tests to make them pass or mark the goal complete while required work or verification remains. Continue until the full Forge 1.20.1 port meets this definition.
