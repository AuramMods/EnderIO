# Cycle 018 — Native machine interactions and wrench lifecycle

Status: progress, not completion. Starts at `3a7136f04`, after cycle 017. Main Ender IO still does not compile; the addon, packaged gameplay, integrations and client validation remain incomplete.

## Changes

- Converted newer split block item/menu callbacks to native Forge 1.20.1 `Block.use`. The machine base tries its held-item extension first, then opens a menu for the main hand. Native `NetworkHooks.openScreen` carries the block position. Converted legacy machines, enchanter, capacitor bank, conduit bundle, tank, vat, Niard, farming station and resetting lever entry points. Native public override visibility is restored where the compiler reported weaker access. These source conversions do not establish runtime verification for every concrete machine.
- Replaced newer item interaction result types with native `InteractionResult`. Removed newer block codec overrides from machine/enchanter/capacitor-bank bases where Forge 1.20.1 has no corresponding block codec entry point. Creative soul binding and Yeta side-capability mutation now occur on the server.
- Moved wrench handling to native `PlayerInteractEvent.RightClickBlock` at low priority. A consumed action runs once on the server and prevents both item hooks and menu opening. The separate foreign-wrench subscriber is folded into this handler. The existing `c:tools/wrench` tag now uses the native `tags/items` directory. The native event order and independent block/item decisions were checked against the pinned Forge sources and the [Forge interaction event source](https://raw.githubusercontent.com/MinecraftForge/MinecraftForge/1.20.x/src/main/java/net/minecraftforge/event/entity/player/PlayerInteractEvent.java).
- A new regression exposed that denying block use alone still allows Forge to invoke Yeta's `onItemUseFirst`. The handler now denies the subsequent wrench item hook as well. Canceled events, explicit block/item denial and spectator interactions do not wrench machines in the native server-path test. This is not an audit of every external protection mod.
- Added `MachineWrench` for native server removal and stateful loot delivery. It retains the old entity, performs removal callbacks before exporting loot, and returns items to inventory with native overflow drops. This ordering is needed by capacitor-bank state export, which reads a network detached during removal; actual capacitor-bank multiblock accounting remains unverified.
- Both machine bases and capacitor banks now use that shared dismantle path and perform side cycling on the server without sending a second client-originated cycle packet. Existing packet handlers remain for other UI callers.
- Moved side/soul capability tokens and I/O/redstone item keys to their owning APIs, preserving the central aliases and serialized IDs. The modern machine base owns the shared native model property; the legacy alias retains its identity. Native block-state properties remove unnecessary dependencies from the base. `TransferUtil` uses Java 17 list access and native fluid copying.
- Resetting levers use the native lever interaction/pull methods and server scheduling. Normal/inverted output and the one-second fixture delay are exercised by actual scheduled ticks.

The headless source bridge now compiles **80 actual main-module files**, up from 67. The new fixture inherits the production `MachineBlock` and `MachineBlockEntity`, registers native capabilities, and uses actual Yeta/foreign tagged wrench dispatch. Its minimal menu and inventory layout are test fixtures; no fake platform classes or disabled production features were added.

## Verification

```sh
./gradlew :endercore:test :endercore:runGameTestServer :endercore:jar :endercore:reobfJar --console=plain
```

Final command exits **0**. Unit tests executed during the first attempt, and remained up to date while the separate GameTest sources were corrected. Final GameTests all executed on Java 17.0.18 / Forge 47.4.28.

| Suite | Discovered | Executed | Passed | Failed/errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| EnderCore unit tests | 39 | 39 | 39 | 0 | 0 |
| Native Forge headless GameTests | 112 | 112 | 112 | 0 | 0 |

Six new tests cover:

1. Real `ServerPlayerGameMode.useItemOn` ordering: held-item action before menu, offhand action support, no offhand menu fallback, and a server menu opened by native Forge networking.
2. Actual Yeta and a tagged foreign wrench: one cycle, no second item callback or menu, and invalidation/rebinding of a retained native side capability.
3. Canceled, block-denied, item-denied and spectator interactions preserving the machine.
4. Native wrench removal, exactly one carried item after removal callbacks, `BlockItem` placement, owner assignment and `BlockEntity.loadStatic` restoring the actual machine base's 257-item inventory and I/O/redstone state.
5. Full inventory overflow producing exactly one stateful world item, including a repeated use after the machine is gone.
6. Actual resetting levers retaining normal/inverted signals before the delay and resetting through native scheduled ticks.

The connection fixture uses native server/network classes and an embedded Netty channel, not a real multiplayer client. These tests do not verify rendered menus, client prediction, concrete machine recipes, conduit graph actions, chunk unloading, a reopened world or packaged multiplayer. In particular, conduit client prediction and its registry-dependent load ordering remain unresolved.

All logs below are under `build/port-evidence/`:

- `cycle-018-bridge-first.log`: initial bridge compile found Java 21 `List.getFirst`; fixed.
- `cycle-018-core-first.log`: unit/jar/reobf tasks passed; new fixture compilation found two generic supplier mismatches; fixed.
- `cycle-018-core-second.log`: 112 GameTests executed, 111 passed, protection regression failed; fixed in production handling.
- `cycle-018-core-final.log`: final successful command, 112/112 GameTests; all test processes shut down normally.
- `cycle-018-tests.json` and `cycle-018-gametest-results.xml`: actual counts and test identities. Malformed-packet and obsolete-inventory-slot warnings are exercised negative cases from existing tests.

```sh
./gradlew :endercore:compileJava -I scripts/porting/compiler-diagnostics.init.gradle :enderio:portCompilerArguments --no-configuration-cache --console=plain
/Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/javac -J-Xmx4G @build/port-evidence/main-javac.args
./gradlew :enderio:compileJava --console=plain
```

Argument export exits **0**. Supplemental direct compilation exits **1**, with **936 errors and 100 reported warnings**, down from 1,067. Normal main compilation exits **1** at its **400-error cap**, with 100 warnings. Evidence: `cycle-018-core-arguments.log`, `cycle-018-main-javac-first.log`, `cycle-018-main-gradle-first.log`. Supplemental diagnostics use the actual source list/classpath with annotation processing disabled; counts are neither independent defects nor a successful main build. Main compile failure still blocks full datagen and addon validation.

Intermediate EnderCore artifact, named from this cycle's base commit:

- `endercore/build/libs/endercore-8.2.14.17-1.20.1+3a7136f045.jar`
- SHA-256: `0309969eca7f94a0cae9fd3d0c58baa0d02fc7e26b1fb5c97383481236b99f9e`
- 222 classes, all class-file major 61; no GameTest or bridged main-module classes in the jar. Inspection record: `build/port-evidence/cycle-018-artifacts.json`.

Tests used the existing isolated headless server profile (`nogui`, `java.awt.headless=true`). No client was launched, activated or given input. Client focus/audio/rendering verification remains pending.

## Git state and next work

The earlier accidental merge recovery remains intact: no merge/rebase in progress, no unmerged index entries, no upstream on local `1.20.1`, and recovery stash/refs retained. No push or remote branch modification was performed. This cycle is a new local commit containing its tracker update.

Next, exercise the actual powered-machine base, energy/capacitor state and concrete recipe/task lifecycle rather than only independent storage helpers. Continue through remaining main compilation errors. Registry-dependent conduit loading must defer resolution until the current level is attached; model/render APIs, source-specific interactions and all optional integration configurations still need implementation and runtime evidence. Foreign-wrench tag handling is tested with a fixture, not the actual optional mods.
