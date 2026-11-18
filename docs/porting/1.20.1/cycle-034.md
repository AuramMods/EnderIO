# Cycle 034: run the main registry tests inside native Forge

Starting commit: `8dca0c746`. Destination: local `1.20.1`.

## Git recovery remains intact

The working tree and index were clean at the start, with no unmerged entries. The earlier automatic pre-merge stash remains available. Recovery refs `refs/port-recovery/cycle-012-stash` (`121166425`) and `refs/port-recovery/cycle-012-conflicted-tree` (`2c597084a`) remain intact. `origin/1.20.1` still points to historical `18ee1b17b`; no push, reset, stash reapplication or history rewrite was performed.

## Changes

The previous full build failed with 42 early errors in the main module's unported tests. This cycle preserves all **128** existing main-module JUnit methods: **16** CPU-only cases remain under `src/test`; **112** registry-dependent cases in 20 classes move to `src/registryTest`. The moved cases retain all **310** explicit `Assertions.assert*` calls and their original method names.

`runRegistryTestServer` starts an isolated, headless Forge server with the actual Ender IO mod and a test-only mod. Native `GameTestGenerator` registers one test per discovered JUnit case. The real Jupiter engine performs discovery, setup and execution; an Ender IO test extension supplies the running Minecraft server on its own thread. The bridge records assertion failures as native GameTest failures and requires an actually executed successful case, with no skipped or aborted result. Native JUnit-style XML reports include original class/method names and failure details. A small test-only game-library jar supplies the pinned JUnit 5.7.1 engine and dependencies to Forge's transformed module layer. It is not bundled into release jars.

The test changes use native Forge capabilities and fluid stacks, NBT equality fixtures for item/fluid data, 1.20.1 registry operations and DFU results, native shaped recipes and crafting containers, and plural entity-tag directories. Broken-spawner loot tests now use the actual server level instead of Mockito. Their assertions remain intact; the unused main-test Mockito dependencies/agent are removed. CPU test logs now stay under the build directory. The class inventory and usage instructions are in `enderio/src/registryTest/README.md` and `resources/registry-tests.txt`.

## Actual verification

The first compile exposed test API differences and the new profile's engine dependency. The first server launch exposed a missing build dependency for the test-library jar; adding the run's explicit `taskBefore` dependency resolved it. These were diagnosed from actual compilation and launch failures.

`./gradlew build --console=plain` now **passes**. It compiles/packages all shipped modules and executes all four configured native test profiles. A final `:enderio:check` rerun verifies the final bridge after improving assertion failure messages.

| Verified profile | Executed | Failures/errors | Skips |
| --- | ---: | ---: | ---: |
| Core JUnit | 61 | 0 | 0 |
| Main CPU JUnit | 16 | 0 | 0 |
| Addon early-initialization JUnit | 1 | 0 | 0 |
| Core native GameTests | 170 | 0 | 0 |
| Core native Mekanism GameTests | 7 | 0 | 0 |
| Main registry tests, Jupiter inside native GameTests | 112 | 0 | 0 |
| Actual-mod addon GameTests | 18 | 0 | 0 |

That is **78 ordinary JUnit executions and 307 native GameTest executions**. Counts describe these profiles, not unique gameplay scenarios: some earlier core source-bridge checks overlap main-module cases. The 112 in-server Jupiter cases are counted only once, under native GameTests. An old `endercore/portFoundationTest` report was identified as stale and excluded; that task was not run in this cycle.

Failure propagation was tested by temporarily inverting the existing soul-bound input-preservation assertion. Both probe runs executed 112 cases, reported exactly one failure and exited Gradle with status 1. The final probe's XML includes the original expected/actual assertion message. The original assertion was restored before the passing runs; no intentional failure remains in source.

The registry profile runs without the optional integrations loaded by the addon profile. It validates these registry/recipe/filter cases under that configuration, not all gameplay in the absence of optional mods. No client was launched. Every test server used its own game directory, `nogui` and headless Java, and shut down after execution.

## Evidence and artifacts

Ignored evidence lives under `build/port-evidence/`: `cycle-034-compile-first.log`, `cycle-034-compile-second.log`, `cycle-034-native-first.log`, `cycle-034-native-second.log`, both `cycle-034-negative-control*.log` files and XML reports, `cycle-034-full-build.log`, `cycle-034-main-final.log`, `cycle-034-packaging.log`, `cycle-034-test-migration.json`, `cycle-034-test-results/`, `cycle-034-test-summary.json` and `cycle-034-artifacts.json`.

Final `./gradlew :enderio:jar :enderio:reobfJar --console=plain` packaging also passes. Candidate jars use pre-commit version `8.2.14.33-1.20.1+8dca0c7461`. Their own classes remain Java 17 major 61 (251 core, 1,260 main, 69 addon). Recursive inspection of the main/nested jars finds no registry test classes, fixtures or JUnit libraries. These are development artifacts, not validated releases.

| Artifact under its module's `build/libs` | SHA-256 |
| --- | --- |
| `endercore-8.2.14.33-1.20.1+8dca0c7461.jar` | `0668a8142dc6ed57826a97bd09389ae061b5506004528ff0f6468cdd5483210b` |
| `enderio-8.2.14.33-1.20.1+8dca0c7461.jar` | `d0f6239751cd954d7cbb96f69e079c570514203d8c15fb5fcf20cb0e6768ee83` |
| `enderio-modded-conduits-8.2.14.33-1.20.1+8dca0c7461.jar` | `67044f6f32c5a03d0d0201b0ccf178a6f88d25607f6ffab5ae3c76310655d635` |

## Remaining work

The separate 34-file `src/gametest` suite still uses NeoForge's dynamic test framework and is **not** covered by this passing build. Its 43 gameplay registrations and their helpers/templates still require migration. CI's unqualified `runGameTestServer` still selects that unported main profile; CI migration remains incomplete. A passing `build` is not completion of the port.

Also unresolved: chemical execution-time insertion remainder conservation, fluid transfer-rate auditing, complete machine/conduit/feature coverage, actual disk-backed chunk/world/server restart persistence, historical save migration coverage, clean packaged client/server installs, directly verified non-activating client instrumentation, visuals and real multiplayer. Byte-for-byte artifact reproducibility is not demonstrated; timestamped manifests remain. Datagen was not rerun because this cycle changes test infrastructure only; its cycle-033 results remain the latest evidence.

Next cycle: migrate the main gameplay GameTests to native Forge, preserve their assertions/templates and connect them to `check`/CI. Keep the full port goal active.
