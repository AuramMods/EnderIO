# Cycle 016 — Machine storage and crafting state

Status: progress, not completion. Starts at `76b100f6c`, following verified progress in cycle 015. The complete main mod and addon still do not compile or run.

## Changes

- Ported `MachineInventory`, `MachineFluidHandler`, `MachineEnergyStorage` and machine tasks to native Forge `INBTSerializable` methods. Temporary registry-context overloads delegate to these methods for remaining callers. Inventory and fluid reads stage and validate contents before committing them, clear absent entries, reject duplicate indices, and preserve entries outside the current layout for a later expanded layout. Saved item counts use integers and retain native tags and Forge capability data.
- Inventory and fluid simulation no longer emit mutation callbacks. Actual quantity changes notify listeners, including changes that retain the same item/fluid type. Fluid assignment takes an owned copy, matching respects fluid NBT, nonpositive withdrawals do nothing, and existing fluid remains drainable after a validator changes. Sided views enforce their actual I/O rules.
- Energy transfer handles negative requests and integer boundaries without creating charge, and emits one change notification per mutation. Saved raw charge survives loading before capacitor capacity is available and temporary capacity reductions. The visible/usable amount remains bounded by current capacity; extracting subtracts from raw stored charge without deleting hidden excess. Retained sided views reject transfers when their side becomes disabled, including modes that otherwise ignore directional I/O settings.
- Moved the capacitor data key and capability token to the existing capacitor APIs; central registration aliases the same objects. Inventory layout checks can use the actual capacitor API without initializing the entire main mod. Language ID creation now uses `EnderIOAPI` with unchanged identifiers.
- Task hosts copy and preserve pending task NBT until the level is ready, clear stale task state on replacement, and remove stale task tags when saving without a task. Native task state retains progress and already-determined outputs. Missing recipes and live recipe replacement cancel a task before resource mutation.
- Newly saved crafting tasks include serialized recipe data. On restore, changed data under the same recipe ID cancels the old task. Ender IO codec-backed recipes encode registry names through NBT rather than connection-local numeric IDs. External serializers without a codec use their native wire encoding; arbitrary third-party serializer stability is not established. Older task NBT without this field still follows ID/type lookup; historical-save migration remains unaudited.
- Output placement copies rolled stacks for both reservation and insertion. Forge item handlers may retain an inserted stack; sharing that object with the task's saved output can alter the saved roll while merging subsequent outputs. Tests cover successful multi-output merging and insufficient-space rejection without partial insertion.
- Added a required, nonempty logical item-stack packet codec using an integer count. Seven recipe output serializers use it so quantities above the native byte range survive recipe synchronization. Physical slot-stack encoding is unchanged.
- Converted powered-spawner mode/task NBT and the alloy task's native save hooks. These complete machine workflows are not runtime-verified. The inventory block-item interface now accepts native `CompoundTag`; its newer component-based callers still need conversion in the next cycle.

No fake platform classes or disabled production features were added. The temporary GameTest source bridge now compiles **64 actual main-module files**, up from 33, including the real storage/task implementations. Test fixtures and bridged classes remain outside the EnderCore release jar.

## Actual verification

The first bridge compilation passed. Initial test compilation failed because the test capability fixture called `LazyOptional.orElseThrow()` without its required supplier. After fixing the fixture, the first runtime run passed **94 of 98** GameTests; four task tests failed because the fixture attempted to create a multi-slot accessor for a single output slot. The fixture now uses the actual single-slot accessor and its supported multi-slot adapter. No production assertion was weakened to resolve these fixture failures.

Additional recipe snapshot and storage assertions brought the final suite to 99. The final combined command was:

```sh
./gradlew :endercore:check :endercore:jar :endercore:reobfJar :enderio:compileJava --continue --console=plain
```

It exits **1 solely for main compilation**, which reaches the normal **400-error cap**. Core checks, jar and reobfuscation succeed. An earlier core-only invocation of the same core tasks exits **0**; the final combined run includes the subsequent disabled-side regression assertion and Java 17 alloy-task correction.

| Suite | Discovered | Executed | Passed | Failures/errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| EnderCore JUnit | 39 | 39 | 39 | 0 | 0 |
| Native headless Forge GameTests | 99 | 99 | 99 | 0 | 0 |

The 11 new GameTests exercise:

- Actual inventory persistence with count 256, item capability charge and nested NBT; layout shrink/expand, absent entries and saved output stacks.
- Inventory simulation, quantity notifications and atomic rejection of malformed/duplicate saved entries.
- Sided inventory restrictions and real capacitor data/capability detection.
- Fluid simulation, quantity notifications, NBT variants, negative withdrawals and sided access.
- Fluid copy ownership, native NBT, preserved overflow tanks and atomic invalid-load rejection.
- Energy integer limits, simulation, rates, notifications, capacity initialization/reduction, persistence and retained disabled-side views.
- A blocked crafting task's progress, stored outputs and energy through native save/load, followed by exactly-once completion after output space is available.
- Base crafting-task output reservation and insertion without shared-stack mutation or partial insertion on insufficient capacity.
- Live recipe replacement, same-ID changed saved recipes and malformed saved IDs.
- Pending host NBT before level readiness, copied state, reset and resumed progress.
- Native `ClientboundUpdateRecipesPacket` round trips preserving output count 257, recipe snapshots distinguishing counts 1 and 257, registry names in codec snapshots and rejection of empty required outputs.

The task fixtures use the actual production task/storage classes and an actual native level/recipe manager, with a minimal registered test block entity and fixture input-consumption implementation. They do **not** exercise a complete alloy smelter, VAT, spawner, real chunk unload/reload or server restart. Full main startup, block/item-state wiring, menus and client synchronization remain unverified.

Servers ran headlessly with `nogui`, saved and shut down cleanly. No client, audio system or narrator was launched. Final processes were checked and none of this cycle's Gradle wrappers, GameTest servers or supplemental compilers remained running.

Evidence under `build/port-evidence/`:

- `cycle-016-core-bridge-first.log`: actual main-source bridge compilation.
- `cycle-016-storage-test-compile.log`, `cycle-016-tests-first.log`: initial fixture compilation failure.
- `cycle-016-check-package-main.log`: initial 94/98 runtime result and main compile failure.
- `cycle-016-tests-second.log`, `cycle-016-tests-final.log`: successful intermediate 99-test core checks and packaging.
- `cycle-016-check-final.log`: final combined command, 99 passing GameTests and capped main compilation.
- `cycle-016-tests.json`: final report counts and names, extracted from the actual XML reports under `endercore/build/test-results/`.

Supplemental compiler operation:

```sh
./gradlew :endercore:compileJava -I scripts/porting/compiler-diagnostics.init.gradle :enderio:portCompilerArguments --no-configuration-cache --console=plain
/Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/javac -J-Xmx4G @build/port-evidence/main-javac.args
```

Argument export/core compilation succeeds. Final supplemental Java 17 compilation fails with **1,233 errors and 100 reported warnings**, down from cycle 015's 1,266. An intermediate run reported 1,234 before replacing the alloy task's remaining `getFirst()` call. These diagnostics use actual main sources/classpath with annotation processing disabled; counts are not independent defects or proof of a full build. Evidence: `cycle-016-core-arguments.log`, `cycle-016-main-javac.log`, `cycle-016-main-javac-final.log`.

Intermediate EnderCore artifact, named from this cycle's base commit:

- `endercore/build/libs/endercore-8.2.14.15-1.20.1+76b100f6ce.jar`
- SHA-256: `69b074fd5b3220911a361d7982b33af6340949ee85405e28d6104c760d1c7f7d`
- All 218 class files are version `61` (Java 17). Test fixtures and bridged main-module classes are absent.
- Inspection record: `build/port-evidence/cycle-016-artifacts.json`. This is not a packaged-runtime test.

`git diff --check` passes. The local branch retains no upstream; cycle-012 recovery refs and stash are preserved. No merge is in progress and no remote write or release was performed.

## Remaining work and next cycle

Convert actual machine block-entity save/load and block-item break/place state hooks to native NBT, then exercise those complete paths. Finish recipe behavior and machine-held recipe invalidation in the crafter, tank, enchanter and soul binder; helper/task tests do not establish those workflows. Validate in-progress jobs through actual unload/reopen and historical fixtures, including older task NBT without recipe snapshots.

Attachments/data maps, datagen/resource schemas, interactions, rendering/UI, integrations, historical saves and packaged single-player/dedicated multiplayer tests remain incomplete. The full goal remains active with its original scope.
