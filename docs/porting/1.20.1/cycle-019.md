# Cycle 019 — Powered-machine energy, capacitors and save restoration

Status: progress, not completion. Starts at `416b0baca`, after cycle 018. This cycle fixes runtime defects in the actual powered-machine foundation. It does not finish main compilation or validate all concrete machines.

## Changes

- The production `PoweredMachineBlockEntity`, `PoweredMachineEnergyStorage`, `CapacitorItem` and capacitor scaling APIs now join the native headless source bridge. It compiles **88 actual main-module files**, up from 80. The fixture registers real powered-machine blocks with Forge item/energy capabilities and inherits the production block/entity behavior. Test-only source bridging remains excluded from EnderCore's release jar.
- Moved the existing `enderio:energy` item key to the energy-state owner and retained the central alias, codec and removal semantics. Powered machines use the existing owner-level capacitor key/capability instead of initializing the full main registration classes.
- Made energy additions overflow-safe and nonnegative. Adding after a capacitor downgrade no longer reduces already-stored charge. Negative requests transfer nothing; unchanged writes and simulations do not dirty the machine. Internal uncapped consumption follows `IMachineEnergyStorage` independently of external output permission, and capped consumption retains its configured limit.
- Preserve nonnegative raw charge through native NBT and item export, including charge above the current capacity after downgrade or before the level/capacitor is available. Acceptance stays limited by capacity. Native load refreshes power status, and item export removes the historical redundant energy compound as well as the current scalar field.
- Retained energy handlers now reject disabled sides for input-only, output-only and bidirectional machines. External receive/extract methods also reject removed owners. This covers stale references after native block removal; it is not yet a complete chunk-unload/restart audit.
- Treat nullable capacitor-extension data as `CapacitorData.NONE`. The regression initially crashed the actual GameTest server on the next machine tick; the source fix removes that crash. Capacitor-free machines return an empty capacitor item without accessing slot `-1`.
- Native capacitor right-click installation checks the real slot's validity and emptiness, inserts one item, preserves creative stacks and leaves occupied zero-strength capacitors intact. Client-side code predicts consumption without changing inventories. Only the server path has runtime evidence; client prediction remains unverified.
- Refresh capacitor scaling and mark state changed after the existing extension-use callback. Removed an unused dirty flag that was never set. The protected callback is tested directly; the existing TODO about completion-hook coverage across concrete machines remains unresolved.
- Added migration for the historical Forge 6.x `IoConfig` spelling. The earlier helper decoded old direction/mode scalars but did not map that root field to current `IOConfig`. Current fields take precedence, and migration copies caller-owned NBT. The exact old energy compound, I/O key and integer redstone layout were read from recorded historical commit `18ee1b17bf6879298e911fff751204b07f2e4228`, including `src/machines/java/com/enderio/machines/common/MachineNBTKeys.java`, `blockentity/base/{MachineBlockEntity,PoweredMachineBlockEntity}.java`, `io/energy/MachineEnergyStorage.java` and `src/core/java/com/enderio/core/CoreNBTKeys.java`.

## Verification

```sh
./gradlew :endercore:test :endercore:runGameTestServer :endercore:jar :endercore:reobfJar --console=plain
```

Final command exits **0** on Java 17.0.18 / Forge 47.4.28. Unit tests and jar/reobfuscation executed in the first full attempt; unchanged inputs remained up to date during later GameTest iterations.

| Suite | Discovered | Executed | Passed | Failed/errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| EnderCore unit tests | 39 | 39 | 39 | 0 | 0 |
| Native Forge headless GameTests | 121 | 121 | 121 | 0 | 0 |

Nine new tests exercise:

1. Actual powered storage: negative arguments, simulation, dirty notifications, capped/uncapped internal consumption, integer-limit addition and negative direct state.
2. Capacitor downgrade, preserved excess energy, `BlockEntity.loadStatic` before level attachment, full item save and real `BlockItem` placement with modified carried charge.
3. Retained native capabilities after disabling each energy-mode variant and after removing its owner.
4. Nullable extension data and a real powered machine that needs no capacitor.
5. Actual `CapacitorItem.onItemUseFirst` through `ServerPlayerGameMode`: survival/creative counts, occupied zero-strength slots and invalid capacitors.
6. Dynamic extension scaling refreshed after the existing use callback, preserving excess charge and dirtying saved state.
7. Production powered storage, `CraftingMachineTaskHost` and `PoweredCraftingMachineTask` together: partial energy/progress save, native entity reconstruction, redstone pause and exactly-once completion. The fixture uses the real alloy recipe type with a minimal inventory/task consumer; this is not a full alloy-smelter gameplay test.
8. A generator fixture pushing energy to a neighbor through real registered Forge capabilities and respecting a disabled side.
9. Literal historical `Energy`/`EnergyStored`, `IoConfig` and integer `RedstoneControl` fields in the real powered entity: migration, source-NBT ownership, current-field precedence and negative-energy handling.

The historical fixture uses known old scalar/compound fields alongside a current fixture inventory/capacitor. Full Ender IO 6.x item/world compatibility is still unproven. No real user world was used. Neither client rendering nor actual packaged multiplayer, chunk reloading or process-restart persistence is established by these tests.

Local evidence under `build/port-evidence/`:

- `cycle-019-bridge.log`: actual production-source bridge compiles.
- `cycle-019-tests-before-fixes.log`, `cycle-019-null-capacitor-crash.txt`: first server run aborted on nullable capacitor data. It did not write a fresh completed report; the older 112-case XML was not counted as this run.
- `cycle-019-tests-after-null-fix.log`, `cycle-019-after-null-fix.xml`: 120 executed, 113 passed and seven failed. Six failures exposed remaining production defects; one exposed a fixture input with one slot where the real alloy recipe requires three. Both production defects and fixture shape were corrected.
- `cycle-019-tests-after-fixes.log`: 120/120 passed.
- `cycle-019-tests-legacy-first.log`, `cycle-019-legacy-before-fix.xml`: after adding the historical-field test, 121 executed and 120 passed; the missing `IoConfig` mapping failed as expected before its production fix.
- `cycle-019-core-final.log`, `cycle-019-tests.json`, `cycle-019-gametest-results.xml`: final success and actual test identities/counts. Existing malformed-packet/obsolete-slot warnings are negative-test evidence, not unexpected new gameplay errors.

```sh
./gradlew :endercore:compileJava -I scripts/porting/compiler-diagnostics.init.gradle :enderio:portCompilerArguments --no-configuration-cache --console=plain
/Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/javac -J-Xmx4G @build/port-evidence/main-javac.args
./gradlew :enderio:compileJava --console=plain
```

Argument export exits **0**. Supplemental compilation exits **1**, with **936 errors and 100 reported warnings**, unchanged from cycle 018. Normal main compilation exits **1** at its **400-error cap**, with 100 warnings. Evidence: `cycle-019-core-arguments.log`, `cycle-019-main-javac.log`, `cycle-019-main-gradle.log`, `cycle-019-error-counts.json`. Supplemental diagnostics use actual main sources/classpath without annotation processing and do not establish successful packaging. Main compilation still blocks complete datagen and the addon.

Intermediate EnderCore artifact, named from this cycle's base commit:

- `endercore/build/libs/endercore-8.2.14.18-1.20.1+416b0bacaf.jar`
- SHA-256: `1a2c72efb14ca7797576489013c10b59085b9b00eccde221869e3f72d927b981`
- 222 classes, all class-file major 61; no GameTest or bridged main-module classes. Inspection: `build/port-evidence/cycle-019-artifacts.json`.

All tests used the existing isolated headless server profile with `nogui`, `java.awt.headless=true` and accepted test EULA. All owned test processes terminated. No client was launched or activated. This cycle gets its own local commit; no push or remote branch changes.

## Next work

Continue reducing main compilation failures so runtime tests can load the full mod. The largest clusters are painted/conduit model APIs, client setup/widgets and conduit registry-dependent serialization. Conduit data must resolve against the attached level rather than `RegistryAccess.EMPTY`. Complete concrete machine task/input reload behavior and completion hooks, then run their actual registrations and menus. Preserve all pending integration, asset/datagen, non-activating client and packaged multiplayer requirements.
