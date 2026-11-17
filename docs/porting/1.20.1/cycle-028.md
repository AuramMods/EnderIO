# Cycle 028 — shared native Forge and Java 17 APIs

## Objective and changes

Batch remaining common API conversions to move toward compilation of the complete main module. Preserve the 1.21.1 feature baseline; no modules or integrations were removed. The main module still fails compilation, so the untested content changes below are implementation progress, not complete gameplay evidence.

- Added native fluid copying with independent NBT and empty-stack handling. Converted fluid holders, display names, tags, equality and capability lookups to Forge 1.20.1 APIs. Tank synchronization now snapshots mutable fluid state and sends full updates for NBT changes, reserving amount-only updates for equal fluid/NBT and capacity. Fluid-storage value equality/hash include amount, tag and capacity.
- Added component codecs for conduit descriptions: structured native component JSON in data files and native component JSON strings in NBT, including registry-wrapped NBT operations. The first regression run exposed lost boolean styles in generic JSON-to-NBT conversion; the corrected representation retains translation arguments, style, click events and siblings.
- Replaced newer integer clamps and accidental floating-point bounds with integer-only long/int helpers in capacitor-bank accounting and reporting. This preserves units above float/double precision thresholds. Complete bank split/merge/conservation and arithmetic-overflow behavior still need actual network tests.
- Adapted item tooltips, custom names, use duration, menu opening, Forge fake players, native enchantment constants, Java 17 list access and registered-holder access. Enderios uses the native bowl-food implementation with its existing single-item stack limit and food/teleport settings.
- Adapted block constructors/properties, pressure-plate sensitivity, sound suppliers, clone-stack callbacks, bucket pickup and painted block-entity packet signatures. Removed block codec overrides that have no corresponding native 1.20.1 block API; registered blocks and their behavior remain. Forge's native menu-opening implementation already closes the server container without emitting a separate client close-screen packet, replacing the unavailable newer menu-provider override.
- Ported range-particle command/network deserialization and native codec signatures. Painted falling sand uses Forge additional spawn data and carries the native falling-block save fields, including block state and paint in `TileEntityData`; sending paint alone would lose the falling block's state because the Forge custom spawn packet does not carry vanilla's block-state ID. Actual client spawn/render behavior remains unverified.
- Ported the shaped entity-storage recipe builder to native `FinishedRecipe`, preserving its recipe payload, custom serializer ID and advancement. Adapted soul-binding recipe validation to DFU 6's bidirectional validation API and powered-spawner mode serialization to native registry operations.
- Supplied `c:experience` in the native plural fluid-tag directory, retaining `enderio:fluid_xp_juice_still` and adding optional `forge:experience`. Supplied `c:fertilizers` with bone meal and optional `forge:fertilizers`; updated the corresponding generators. Farming uses native plantable/bonemeal/shears/fortune APIs. Full tag/datagen reconciliation is still pending.
- Rechecked the reported Git incident: no unmerged index entries, conflict markers, merge or rebase state were present. Reflog records the earlier automatic stash before merging with the historical remote branch. Both `refs/port-recovery/cycle-012-stash` and `refs/port-recovery/cycle-012-conflicted-tree` remain intact. The local branch has no upstream; the remote was not changed.

## Verification

Final core command, exit **0**:

```sh
./gradlew :endercore:test :endercore:runGameTestServer :endercore:jar :endercore:reobfJar -I scripts/porting/compiler-diagnostics.init.gradle :enderio:portCompilerArguments --no-configuration-cache --console=plain
```

| Suite | Discovered | Executed | Passed | Failed/errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| EnderCore unit tests | 60 | 60 | 60 | 0 | 0 |
| Native Forge headless GameTests | 160 | 160 | 160 | 0 | 0 |

Three new unit tests cover precision above 2^24 and 2^53, long extremes and bounded native int reporting. Five new GameTests cover fluid-copy independence/empty values, actual tank-slot wire round trips through quantity/NBT/capacity/fluid/empty transitions, tank equality/hash, structured component JSON/NBT/registry-NBT round trips, and malformed/null component rejection. Tank tests mutate the existing fluid object to verify that change detection retains an independent prior snapshot.

The first core run executed 160 GameTests with one failure: component NBT round-trip lost style. After correcting production serialization, all tests passed without weakening those assertions; the final test additionally covers mixed translation arguments and registry-wrapped NBT. The isolated test bridge now includes 115 actual main production files and three actual datagen files, compiled into test output only. This is not a complete main-mod or packaged-runtime test.

```sh
./gradlew :enderio:compileJava --console=plain
/Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/javac -J-Xmx4G @build/port-evidence/main-javac.args
```

Both commands exit **1**. Normal compilation stops at **27 early symbol errors**. Supplemental Java 17 compilation reports **74 errors and 100 warnings**, down from 302 at cycle start (intermediate reports: 181, 126, 94). The supplemental command disables annotation processing and is diagnostic evidence, not a successful build. It reports 18 non-integration errors across powered-spawner ownership/events/motion, experience-rod particles, player reach mixin, SAG mill particles and slicer tool durability. The other 56 errors are integration API mismatches. Changed integration/tool files may still contain these unresolved errors.

Evidence in ignored `build/port-evidence/`:

- `cycle-028-core-compile.log`, `cycle-028-core-first.log`, successful `cycle-028-core-second.log`.
- `cycle-028-tests.json`, `cycle-028-gametest-results.xml`.
- `cycle-028-main-first.log`, `cycle-028-main-second.log`, `cycle-028-main-third.log`, `cycle-028-main-final.log`, `cycle-028-main-gradle.log`, `cycle-028-error-counts.json`.
- `cycle-028-artifacts.json`, `cycle-028-commit-scout.txt`.

Reobfuscated core artifact built before this cycle's commit:

- `endercore/build/libs/endercore-8.2.14.27-1.20.1+d977b53138.jar`
- SHA-256: `c7f345aa01015d7e642c1ceea8c80888e7de299c6f1ba058eae428c57c6cc866`
- 245 classes, all Java 17 major 61; no test or bridged main/datagen classes.

Native test servers used the isolated profile with `nogui` and `java.awt.headless=true`, then saved and shut down. All cycle processes completed. No game client/window/audio or user world was used. This cycle has a separate local commit and no push.

## Next work and limits

Finish the 18 remaining non-integration diagnostics with actual native semantics: powered-spawner event cancellation/owner identity, no-AI motion, tool durability hooks, missing particle effects and the native player interaction/reach mixin. Adapt FTB Ultimine, LaserIO, Almost Unified, JEI and remaining integrations to the pinned 1.20.1 APIs, then compile the complete addon module and port full datagen/test sources.

The common-tag and block/particle/menu/food/farming conversions still require actual complete-mod runtime validation. XP-vial merged-orb accounting remains a known unverified concern. Full resources, client rendering, real multiplayer, conduit/machine lifecycle and transfer accounting, supported integrations present/absent, historical save migration and final packaged artifacts remain required. No complete feature-family status is promoted. The goal remains active and incomplete.

Sources: pinned Minecraft/Forge 47.4.28 sources in the generated native source artifact, baseline source at `4c482da69e76f2b46bfeeac64ef0ac277c318eaa`, and [Forge particle documentation](https://docs.minecraftforge.net/en/1.20.x/gameeffects/particles/). The exact pinned sources, actual compiler and executed tests determine API behavior.
