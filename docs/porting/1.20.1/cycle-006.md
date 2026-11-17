# Cycle 006: correct access transformation and run native Forge tests

Date: 2026-11-17. Starting commit: `87292135b`.

## Changes and diagnosis

- Compared the actual generated Minecraft sources with the successful core artifact. The sole falling-block difference was widening a second constructor to public, which makes the constructor reference in `EntityType` inexact and breaks its generic inference. Changed it to protected, preserving the required `PaintedSandEntity` subclass access, and removed the duplicate rule.
- Converted every remaining access-transformer member to its actual 1.20.1 SRG name using generated mappings. The former named entries did not match the SRG source transformation stage. The older shared-buffer field is `builder`; its native mapping is now used. Required transformations remain enabled.
- Replaced four files' Java 21 record patterns with Java 17 pattern variables and record accessors, preserving null-operation handling, conduit-upgrade behavior and stored-soul decoding.
- Moved the seven registry-dependent assertions from the plain JUnit bootstrap to a separate native Forge GameTest source set and test mod. Used Forge's real registries, event transformations and server lifecycle. Assertions now use native `GameTestHelper` checks with failure messages; no production platform classes or behavior were mocked.
- Added the test mod's metadata, pack metadata, authored empty structure and native XML reporter. The test source set and fixtures are outside production jar sources. Core `check` now requires both unit tests and the headless GameTest server.
- The test profile accepts the EULA, uses its own game directory, passes `nogui` and sets `java.awt.headless=true`. Forge logs explicitly confirm that its immediate window provider does not load for this launch target. No client was launched.

## Actual execution

| Command | Result |
| --- | --- |
| `./gradlew :enderio:compileJava --console=plain` after AT changes | Minecraft source rebuild succeeded; Ender IO source compilation then failed with 24 Java 21 syntax diagnostics |
| `./gradlew :endercore:test :endercore:runGameTestServer --console=plain` | 36 unit tests passed; Forge booted and loaded both core/test mods; server failed because the fixture pack lacked `pack.mcmeta` |
| `./gradlew :endercore:runGameTestServer --console=plain` after adding pack metadata | Seven tests discovered/executed; all seven failed because JUnit assertion classes were not available to Forge's game module classloader. Server exited with code 7 and produced failure XML |
| Same command after native assertion conversion | Seven discovered/executed/passed, zero failed/skipped. Server saved and shut down, exit 0 |
| `./gradlew :endercore:check :enderio:compileJava --continue --console=plain` | Core `check` passed: 36 unit tests plus seven GameTests, all executed with zero failures/errors/skips. Main compilation failed at the 400-error reporting cap. Overall exit 1 due only to main source compilation |
| `git diff --check` | Passed |

Passing native tests verify item count/custom NBT and empty-stack packet round trips; fluid amount/custom NBT and empty-state round trips; particle type/color/scale/position/motion; fluid quantity versus NBT change detection; quantity-update copying/tag preservation; registry-slot change detection; and incremental/full NBT slot saves. Core network registration also survives an actual dedicated-server mod load without loading client-only classes. Packet delivery, multiplayer authority and client behavior have not yet been exercised.

The unit XML reports contain 10 graph, eight chunk lookup, eight ordered-list and 10 packet-codec tests. The native XML contains seven test cases and no failure/skipped children. This replaces the prior class-initialization failure with actual execution; no test has been disabled or silently omitted.

## Evidence and remaining work

Local generated logs:

- `build/port-evidence/cycle-006-enderio-compile-1.log`
- `build/port-evidence/cycle-006-core-tests-1.log`
- `build/port-evidence/cycle-006-core-gametest-{2,3}.log`
- `build/port-evidence/cycle-006-check-main-compile.log`
- `endercore/build/test-results/test/TEST-*.xml`
- `endercore/build/test-results/gameTestServer/results.xml`
- `endercore/run/gametest-server/logs/` (latest run; older failures retained in evidence logs)

Next: port main-module lifecycle/registrations, networking, NBT-backed item state and capabilities against the now-compiling core. The 400 diagnostics include remaining NeoForge imports and newer Minecraft APIs; this is a reporting cap, not the total defect count. Main gameplay, addon integrations, production packaged-jar runtime, complete resource generation, persistence and all client checks remain incomplete. The native tests use a development classpath and a disposable core-only test world; they do not establish full-mod functionality or release readiness.
