# Cycle 009 — native registration handles and typed item NBT

Status: progress, not completion. All modules and feature families remain in scope. The main mod still fails compilation and has not run.

## Changes

- Added project-owned block/item registration handles backed by the actual Forge `RegistryObject` returned from registration. They retain lazy resolution, item forms, stack helpers, resource keys, factory properties and entry enumeration. Main block/item/glass registrations, creative tab enumeration and relevant addon/datagen references now use these helpers. Other deferred holder declarations use native `RegistryObject`.
- Added native Forge registry aliases during the registration event, after normal entry registration. Existing renamed item/block IDs retain their aliases. A real GameTest loads an item by an old ID, resolves the current item and re-saves its canonical ID. This is narrow alias evidence, not a historical-world migration test.
- Added `ItemDataKey<T>` using native stack NBT and codecs. It stores namespaced keys under `enderio:data` and explicit default removals under `enderio:removed_data`. Reads do not modify a stack. Mutable data is copied through serialization; defaults, overrides, removal, reset and previous key names are supported. Registry-backed values require an explicit current `RegistryAccess`.
- NBT writes finish serialization before modifying the stack, preserve unrelated tags, reject invalid data without accepting partial codec results, avoid redundant capability invalidation, and refresh only Ender IO's owned snapshot providers after a change. Live storage keys can retain their providers. Empty global stacks cannot be written.
- Added live native Forge `NbtEnergyStorage` and `StackedNbtEnergyStorage`. Powered toggle items, travel staffs and capacitor-bank item factories use them, and `EIODataComponents.ENERGY` now names the typed NBT key. Transfers respect capacity, limits, simulation and nonnegative amounts. Stacked banks preserve per-item charge and untransferred remainders; an already-retained provider safely handles an emptied stack.
- Corrected native sound registry construction, entity registration factory shape and door/trapdoor constructor argument order. Registration changes do not imply that the corresponding gameplay classes are already ported.

## Actual verification

Final build/test/package command:

```sh
./gradlew :endercore:check :endercore:jar :endercore:reobfJar :enderio:compileJava --continue --console=plain
```

The command exits **1 because main Ender IO compilation still fails** at the normal 400-error reporting limit. EnderCore check, jar and reobfuscation pass. Log: `build/port-evidence/cycle-009-check-package-main-final.log`.

| Suite | Executed | Passed | Failures/errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| EnderCore JUnit | 36 | 36 | 0 | 0 |
| Native headless Forge GameTests | 33 | 33 | 0 | 0 |

Seventeen new GameTests cover registration lifecycle/aliases, default/copy/serialization/removal/error behavior, dynamic registry holders, capability refresh after writes, live energy accounting, stacked transfers and integer boundaries. Existing 16 GameTests and 36 unit tests remain. XML reports are under `endercore/build/test-results/test/` and `endercore/build/test-results/gameTestServer/results.xml`; a named test list is in `build/port-evidence/cycle-009-tests.json`.

The GameTest server used the existing isolated `nogui`/headless profile and accepted EULA. It saved and shut down cleanly. No client or audio subsystem was launched. No desktop focus interaction was needed.

Supplemental source diagnostic commands:

```sh
./gradlew -I scripts/porting/compiler-diagnostics.init.gradle :enderio:portCompilerArguments --no-configuration-cache --console=plain
/Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/javac -J-Xmx4G @build/port-evidence/main-javac.args
```

Argument export passed; direct Java 17 compilation exits **1 with 3,140 errors and 100 reported warnings**, down from cycle 008's 3,458 diagnostics. This is a source diagnostic with annotation processing disabled, not a passing build or a count of independent defects. Logs: `build/port-evidence/cycle-009-compiler-arguments.log` and `build/port-evidence/cycle-009-main-javac-final.log`.

Intermediate verification is retained: two successful EnderCore compile commands; a combined run passing 36 unit/28 GameTests before adding energy tests; a second combined run passing 36 unit/33 GameTests; an initial supplemental run with 3,143 errors; and a package/main-compile run passing core packaging but failing main compilation. A source-edit script initially stopped while processing an addon holder name; its type parser was corrected before compilation. Final review added an alias-removal regression to the existing test, then reran the final suite.

The intermediate core jar is named from the pre-cycle base commit because packaging precedes this cycle's commit. Its final path/checksum/class-version and test-class exclusion inspection are recorded in `build/port-evidence/cycle-009-artifacts.json`. It is not a full Ender IO distribution.

- Jar: `endercore/build/libs/endercore-8.2.14.8-1.20.1+916545c968.jar`
- SHA-256: `804707f86bd4175e5cb14aa36783e8db8641781d7fd260032c66201be989d17a`
- All class-file versions: `61` (Java 17); test fixture classes in jar: `0`.

`git diff --check` passes.

## Remaining work and next cycle

The remaining item keys, defaults and all consumers must be converted to the NBT API. In particular, machine block-to-item/drop/place state still uses newer component hooks, including the energy transfer hooks; those must be replaced with actual 1.20.1 NBT behavior before any machine persistence claim. Main powered item classes still contain other unported APIs, so their gameplay is unverified despite testing the actual shared storage implementations.

Use `ItemDataKey.withDefault` during item creation for former component defaults; use `get/set/remove/reset` on the key instead of nonexistent 1.20.1 stack component calls. A registry-backed value must use the current world's registry-access overload. Snapshot data writes should keep capability refresh enabled; live energy data uses `false`. Preserve codec semantics, alias removals and native stack copying/stacking. Audit old 6.x NBT separately: this new typed container alone does not migrate historical formats or support 1.21.1 world downgrades.

Remaining blockers include packet codecs, item/filter/soul/paint/fluid state, attachments/data maps, recipe/loot/criterion APIs, block behavior/properties, rendering, datagen, all optional integration API conversions and the addon build. All feature-parity rows remain unverified. Packaged client/server gameplay, multiplayer, full persistence/migration, optional-mod presence/absence and non-activating silent client tests remain required.
