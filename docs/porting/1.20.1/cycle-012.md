# Cycle 012 — Forge transport, dispatch and server-authoritative transfers

Status: progress, not completion. Cycle 011 made verified progress. Main Ender IO and the addon remain incomplete and cannot yet run. No completion requirement has been reduced.

## Changes

- Added `RegistryNetworkChannel`, backed by actual Forge `SimpleChannel`. Separate client/server envelopes enforce native packet directions. The network thread copies bounded payload bytes; Forge schedules decoding and handling on the receiving main thread. Decoding uses that player's current world registry access and rejects unknown IDs, malformed data and trailing bytes before invoking the handler. Client references are isolated behind the physical client path; stale connection/player identity is checked before dispatch.
- Applied native payload limits, including the SimpleChannel discriminator: 32,767 total bytes serverbound and 1,048,576 clientbound. Encoding fails explicitly when the native limit is exceeded. No global registry cache, fake platform classes or transport stubs are used.
- Replaced the main NeoForge payload registration/context and distributors. `EIONetwork` registers during `FMLCommonSetupEvent` with protocol `forge-1`; handlers receive the real player after main-thread scheduling. The 28 existing packet IDs are preserved, with 29 direction registrations because conduit configuration is bidirectional. Packet types are ordinary project records/classes. Per-player, dimension and chunk-tracking sends use current server recipients without loading chunks just to find watchers. The experience rod's core particle message uses its existing core channel.
- Kept soul datapack synchronization on native `OnDatapackSyncEvent`, with strict successful codec results and per-player context. Client synchronization, reconnect and live datapack reload still need full-mod runtime tests.
- Added server checks for menu identity/validity, negative/out-of-range slots, spectator mutations, conduit configuration type, filter inventory bounds, timer/count nonnegative values and coordinate-name length. I/O interaction checks loaded chunks, reach and world interaction permission. Enderface uses native block interaction while retaining its remote-access permission test. Travel range squaring no longer overflows an integer.
- Recipe-transfer and crafter-template packets now carry their container ID, with bounded input lists. The server limits physical recipe transfers to the existing VAT, weather obelisk and fluid tank input ranges. `MenuRecipeTransfer` obtains items only from the player's inventory, honors insertion/extraction permissions and slot limits, keeps NBT variants separate, fills empty inputs and preserves item counts. Crafter templates update only the nine ghost inputs of the matching valid menu. Actual JEI integration/GUI behavior is still pending.

## Actual verification

Final command:

```sh
./gradlew :endercore:check :endercore:jar :endercore:reobfJar :enderio:compileJava --continue --console=plain
```

Exit **1**, due to main Ender IO compilation at the normal **400-error cap**. EnderCore check, jar and reobfuscation pass. Final log: `build/port-evidence/cycle-012-check-package-main-final.log`.

| Suite | Executed | Passed | Failures/errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| EnderCore JUnit | 39 | 39 | 0 | 0 |
| Native headless Forge GameTests | 58 | 58 | 0 | 0 |

Seven new GameTests cover:

- Actual Forge `NetworkHooks.onCustomPayload` → `SimpleChannel` → main-thread dispatch of the actual main-module timer packet, received from a worker thread using a native server connection/listener and Netty embedded channel.
- Dynamic biome-holder resolution using the receiving player's registry through that same native channel.
- Unknown IDs, truncated/trailing payloads and wrong-direction messages disconnect without invoking handlers; oversized and unregistered outgoing messages fail.
- Physical recipe-transfer counts, filling empty slots, multiple source stacks, slot limits (including zero capacity), insertion/extraction permission, menu validity, invalid ranges and NBT identity.
- Actual transfer/crafter packet serialization, container identity, empty ghost inputs and oversized template rejection.

The temporary source bridge compiles **15 actual main-module source files** into the GameTest output: the prior eleven plus `EnderIOAPI` and three packet records. Tests instantiate the real core transport and use native Forge dispatch; they do **not** boot the entire main module, exercise its actual menus/handlers, connect a graphical client or establish packaged multiplayer parity. Clientbound dispatch and connection lifecycle still require those tests. Malformed-packet warnings in the logs are expected negative-test evidence.

Counts/names: `build/port-evidence/cycle-012-tests.json`. Current XML: `endercore/build/test-results/test/` and `endercore/build/test-results/gameTestServer/results.xml`. The isolated `nogui`/headless server saved and shut down cleanly; all test worker threads used by the successful dispatch test finished. No client, sound subsystem or desktop interaction was launched.

Intermediate results: the first core compile exposed an unavailable `ServerGamePacketListenerImpl.getConnection()` method; using its native public connection field fixed it. The first expanded runtime passed 55 GameTests, followed by 58 after transfer tests. A subsequent final run verified the single-item transfer capacity adjustment. All combined runs continued to report the main compilation failure.

Supplemental diagnostics:

```sh
./gradlew :endercore:compileJava -I scripts/porting/compiler-diagnostics.init.gradle :enderio:portCompilerArguments --no-configuration-cache --console=plain
/Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/javac -J-Xmx4G @build/port-evidence/main-javac.args
```

Argument export/core compilation passed. Final direct Java 17 compilation exits **1 with 1,796 errors and 100 reported warnings**, compared with cycle 011's 2,095. Annotation processing is disabled in this supplemental command; counts are not independent defects or full-build proof. Logs: `build/port-evidence/cycle-012-core-arguments.log` and `cycle-012-main-javac-final.log` in the same directory.

Source audit against the preceding commit confirms all 28 message IDs and all 29 direction registrations are retained: `build/port-evidence/cycle-012-registration-audit.json`. This supplements, rather than replaces, native dispatch testing. Main and addon production source no longer references NeoForge networking or Minecraft's newer custom-payload API.

Intermediate core artifact, named from the pre-cycle base commit:

- `endercore/build/libs/endercore-8.2.14.11-1.20.1+0169e5a47c.jar`
- SHA-256: `faab921fc6cab511aa8af023ea4ff181c041c338e83a5b0c1824bb14d5f7082f`
- All classes are version `61` (Java 17); native transport is included; test fixtures and main-source bridge classes are absent.

Artifact inspection: `build/port-evidence/cycle-012-artifacts.json`. `git diff --check` passes. All owned build/test commands reached terminal states.

## Accidental upstream merge recovery

During the final documentation/commit step, an external Git action saved this cycle in an automatic stash, configured the local branch to track `origin/1.20.1`, and attempted to merge that historical branch. It produced **582 unmerged paths**. This was not a fast-forward; the local committed HEAD remained `0169e5a47`. The user confirmed the unexpected sync/conflicts while the recovery was being investigated.

The conflict index/metadata, working-tree diff and current documentation were backed up under `.git/codex-recovery/cycle-012-accidental-merge/`. Local recovery refs preserve the automatic stash and conflicted tree. The accidental merge was aborted and the stash applied without dropping it. All **65 restored cycle files match the stash blobs exactly**, no unmerged paths remain, and incorrect upstream tracking was removed. `git ls-remote origin refs/heads/1.20.1` confirms the remote remains at historical commit `18ee1b17bf6879298e911fff751204b07f2e4228`; no push was performed.

Recovery evidence: `build/port-evidence/cycle-012-recovery.json`, `cycle-012-status-unexpected.txt`, and `cycle-012-stash-restore.log`. The same combined check/package/main-compile command was rerun after recovery; log: `build/port-evidence/cycle-012-post-recovery.log`. The saved stash/recovery refs are retained for inspection. Do not merge the unrelated historical remote branch into this port or enable automatic syncing to it.

## Remaining work and next cycle

Continue the remaining serialization and item-state blockers: registry-aware conduit item NBT, filter/recipe codecs, native recipe interfaces/sized ingredients and block-entity item drop/place state. Attachments/data maps, saved-data signatures, vanilla block APIs, rendering/UI, datagen and optional integrations remain incomplete.

The main module still fails to compile. Full registration startup, all main packet handlers and menus, clientbound delivery, login/reconnect, registry/datapack reload, travel, transfer gameplay, integrations and actual client/server multiplayer remain unverified. Native fixture dispatch does not satisfy those requirements. Large real-world synchronization payloads also need testing against the native limits; support valid gameplay rather than silently dropping oversized state if such cases arise.

Preserve the complete baseline and continue until every required feature, persistence path and packaged-runtime check is demonstrated. The goal remains active.
