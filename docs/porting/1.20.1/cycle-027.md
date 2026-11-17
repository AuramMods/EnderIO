# Cycle 027 — native soul-vial interactions and entity tags

## Objective and changes

Port actual soul-vial capture/release, dispenser behavior and configurable vanilla spawner changes onto native Forge 1.20.1. Preserve implemented soul state and tag defaults, and verify that failed releases retain the filled vial. Powered-spawner task spawning and void-vial XP handling remain separate unfinished work.

- Replaced newer block-source/spawner APIs with native `BlockSource` accessors and `SpawnerBlockEntity`. Configured spawner changes mark the block entity changed and retain the native update/game event behavior.
- Implemented the absent dispenser remainder helper using native inventory insertion and default item dispensing: shrink one input, replace an exhausted stack, otherwise insert or eject exactly one remainder. Reset the optional dispenser's success flag for each attempt so an earlier failure does not contaminate later attempts.
- Release consumes the filled vial only after native entity creation and `ServerLevel.addFreshEntity` succeed. A failed factory or cancelled insertion returns failure and leaves its soul/count intact. Empty vials return failure. The baseline soul implementation deliberately removes UUIDs and regenerates them on release; this policy is preserved.
- Specific interactions for horses/villagers/traders/wolves cancel further vanilla interaction after a successful capture. Creative capture retains the source's copied-empty-vial behavior.
- Centralized the existing max-health key on the vial class; `EIODataComponents` aliases it. Soul helpers/providers now use the existing domain-owned soul key and capability token directly. IDs, read-only binding behavior, NBT semantics and capability identity remain unchanged. The actual item is resolved through its registered `enderio:soul_vial` ID where a static factory/registration needs it.
- Ported the entity-tag generator and four Ender IO capture/spawner tags to native plural `entity_types` paths. Forge lacks the NeoForge-provided common tags used by these definitions, so the port supplies their baseline contents: `c:bosses` includes dragon/wither and optional `forge:bosses`; `c:capturing_not_supported` defaults empty. Warden exclusions, empty whitelists and dragon's unconditional exclusion remain intact.
- Invalidated cached capturable entries on Forge `TagsUpdatedEvent`. Native direct capture checks and cached creative entries now reflect whitelist/blacklist changes together. Cache reads/invalidation synchronize access.

## Verification

Final command, exit **0**:

```sh
./gradlew :endercore:test :endercore:runGameTestServer :endercore:jar :endercore:reobfJar -I scripts/porting/compiler-diagnostics.init.gradle :enderio:portCompilerArguments --no-configuration-cache --console=plain
```

| Suite | Discovered | Executed | Passed | Failed/errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| EnderCore unit tests | 57 | 57 | 57 | 0 | 0 |
| Native Forge headless GameTests | 155 | 155 | 155 | 0 | 0 |

Nine new GameTests use the **actual `SoulVialItem`**, actual read-only capability provider, actual dispenser common-setup registration, actual Forge interaction listener and shipped resource tags:

1. Player capture/release through the actual item methods: one live entity, one vial, name/health/max-health state, native saved item NBT, read-only capability, and fresh UUID on release.
2. Rejection of players, dead entities, default blacklisted entities and already-filled vials without consuming inputs or overwriting souls.
3. Uncreatable entity factories and actual cancelled `EntityJoinLevelEvent` insertions retain the filled vial; retry succeeds after the cancellation listener is removed.
4. Enabled native vanilla-spawner replacement consumes one vial and serializes the new entity type; disabling that option uses normal entity release.
5. Native vanilla dispenser ticks capture into an available remainder slot and release a filled vial back to an empty vial.
6. Full dispenser inventory: failed release preserves the stack; a successful retry consumes one filled vial and ejects exactly one empty remainder without overwriting other slots. A registered test accessor exposes native behavior selection to make the occupied-slot case deterministic; the placed block remains a vanilla dispenser.
7. Actual specific-entity Forge events stop further vanilla interaction after capture and preserve creative empty-vial accounting.
8. The actual entity-tag generator writes six temporary native tag files matching shipped definitions; output is removed afterward.
9. Native registry tag rebinding plus `TagsUpdatedEvent` refreshes cached capturable entries, preserves whitelist precedence and unconditional dragon exclusion, then restores the original bindings/cache.

The first test run exposed incorrect fixture assumptions about UUID preservation and an attempted late construction of a test block after registry freeze. Tests now verify the source's fresh-UUID policy, use actual spawn cancellation/factory failure to test rejection, and register the small accessor during normal registration. The final suite has no failures. Native expected failure cases and earlier malformed data-map/packet tests can log rejection messages. Unit tests executed earlier in this cycle and were up-to-date in the final run; all native GameTests executed on the final code.

The isolated bridge now compiles **113 actual main production files and three actual datagen files** into test output. These are absent from the production core jar. This establishes the targeted native item/server behavior, not packaged main-mod startup, a real remote client connection, rendered tooltips/models, or every entity/mod combination.

```sh
./gradlew :enderio:compileJava --console=plain
/Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/javac -J-Xmx4G @build/port-evidence/main-javac.args
```

Both exit **1**. Normal compilation stops with **75 early symbol errors**. Supplemental javac reports **302 errors and 100 warnings**, down from 309. No compiler diagnostics remain in changed production files. Supplemental compilation disables annotation processing and is not a successful build.

Evidence under ignored `build/port-evidence/`:

- `cycle-027-compile-first.log`, `cycle-027-core-first.log`, `cycle-027-core-second.log`, successful `cycle-027-core-final.log`.
- `cycle-027-tests.json`, `cycle-027-gametest-results.xml`, `cycle-027-main-gradle.log`, `cycle-027-main-javac.log`, `cycle-027-error-counts.json`.
- `cycle-027-artifacts.json` and `cycle-027-commit-scout.txt`.

Reobfuscated core artifact built before this cycle's commit:

- `endercore/build/libs/endercore-8.2.14.26-1.20.1+888b107983.jar`
- SHA-256: `d579dda530bc7df29103fa9995763a7125a9849ebe8eca700f5014a6e047ce37`
- 242 classes, all Java 17 major 61; no test or bridged main/datagen classes.

All native test servers used the isolated profile with `nogui` and `java.awt.headless=true`, saved and shut down. All cycle processes completed. No client/window/audio or user world was used. This cycle has a new local commit and no push.

## Next work and limits

Batch the remaining native/Java API conversions to unblock the complete main and addon builds, including powered-spawner finalize-spawn ownership/events, no-AI motion, void-vial XP handling, entity-storage recipe datagen, farming and optional integrations. Void-vial XP conservation with merged orbs requires specific validation; it was inspected here but remains unchanged and unverified.

Full datagen, resource reconciliation, packaged client/server interaction, reconnect/restart persistence, client rendering, save migration and optional integrations remain required. Soul-vial acceptance still needs actual packaged client use, dispenser/world persistence across restarts, supported entity/mod compatibility and synchronization. No complete feature-family status is promoted by this cycle. The full goal remains active and incomplete.

Sources: [Forge 1.20.1 tags](https://docs.minecraftforge.net/en/1.20.1/resources/server/tags/), pinned Forge 47.4.28 native dispenser/entity/spawner/event sources, and [NeoForge 21.1.216 sources](https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.216/neoforge-21.1.216-sources.jar) (`NeoForgeEntityTypeTagsProvider`) for baseline common-tag contents.
