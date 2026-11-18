# Cycle 038 — hidden client rendering and native model loaders

Status: progress, not completion. Cycle 037 made verified progress. This cycle establishes the first actual client runtime evidence; packaged client, gameplay, multiplayer and complete feature parity remain incomplete.

## Changes

- Added test-only GLFW instrumentation, a real OpenGL probe, an independent macOS focus/window observer and a client result runner. The actual guard suppresses window activation/showing/fullscreen and input capture before creation; no focus restoration or global input is used. See the [harness instructions](client-harness.md).
- Added the isolated `clientSmoke` profile and QA mod. It records real renderer/window/audio state, twelve baked block states, a framebuffer image and shutdown. Its resources declare the required format-15 pack metadata. None of these test components are shipped.
- Added an export task that prepares the real Java invocation without launching Minecraft. It avoids a run-task provider dependency that initially caused an unintended guarded client launch, and includes JVM argument providers only once.
- Corrected the enchanter, powered-spawner and active powered-spawner model loaders from `neoforge:composite` to `forge:composite`. The first real client reproduced all three loader failures; the fixed client loads the corresponding baked block states with no ERROR/FATAL log entries.

## Actual runs and corrections

1. Compiled the Java 17 guard/probe and Swift observer with `prepare_harness.py`. The probe deliberately requests activating/fullscreen/capture operations. It rendered and read back **100 frames**, remained hidden/unfocused/windowed with normal cursor mode, and exited zero. macOS observed 146 samples, two owned windows, **zero activation/foreground/onscreen events**, and available WindowServer queries. Evidence: `build/port-evidence/cycle-038-probe-focus.json` and its `.client.log`.
2. The initial export implementation accidentally invoked its run dependency. The verified guard was already installed and directly observed hidden/unfocused/windowed/uncaptured client state, with narrator off and all volumes zero. No independent native observer was attached to that initial Minecraft launch, so there is no macOS observation claim for it. It captured Forge's loading-warning screen: the QA mod lacked pack metadata. Three real Ender IO models also reported unknown NeoForge composite loaders. The hook captured that failure and shut down normally after its deadline. Evidence: `cycle-038-prepare-smoke.log`, `cycle-038-first-client/` and the thread dump in the evidence directory.
3. Corrected the export dependency, QA metadata and production model namespaces. The observed client reached the title screen with no native activation/visibility events. A QA assertion incorrectly queried raw parent model IDs from the top-level model cache, reporting three false missing-model results. The correction uses the actual block-model shaper for every enchanter/spawner state; it does not disable a test or replace production models. Evidence: `cycle-038-observed-client/`.
4. Final export and observed client run both exit zero. The actual title screen renders at **960×540 on Apple M4 Max**, with 31,330 distinct pixels and all **12 baked block states present**. The screenshot was visually inspected. The client reports hidden/unfocused/windowed/uncaptured state, narrator **OFF**, and all ten sound categories **0.0**. Native observation records **795 samples, three owned windows, zero activation/foreground/onscreen events**, available window queries, no timeout and child exit zero. No ERROR/FATAL lines remain. The title screenshot is not a visual inspection of the machines themselves.

Final commands:

```sh
./gradlew :enderio:exportClientSmokeLaunch --no-configuration-cache --console=plain
python3 scripts/porting/client/run_smoke.py --output build/port-evidence/cycle-038-final-client
./gradlew build --continue --console=plain
```

The full build passes: **78 ordinary JUnit results and 383 native GameTest results**, with zero failures/errors/skips. Core unit 61, main CPU 16, addon unit 1; core native 170, Mekanism adapters 7, main registry/Jupiter 112, main gameplay 76, addon native 18. Valid up-to-date unit outputs are reused where applicable; all native profiles run. Jupiter inside the native profile is counted once; profile overlap is possible. The final QA-only lookup/timing adjustment was compiled and run through the exporter after that build; production artifacts did not change afterward.

Evidence under `build/port-evidence/`: `cycle-038-export-fixed.log`, `cycle-038-export-final.log`, `cycle-038-final-client/` (summary, result, focus, log and PNG), `cycle-038-final-client-run.log`, `cycle-038-build.log`, `cycle-038-test-results.json`, `cycle-038-final-test-results/`, `cycle-038-artifacts.json` and `cycle-038-qa/`. All owned launches exited and their handles were closed. No game window was displayed in the verified probe/client observations; no normal user profile or world was touched.

## Artifacts and remaining work

Candidate version `8.2.14.37-1.20.1+8a7ee6f2af` identifies the pre-cycle base. The recursive jar audit finds no client QA, agent, probe, JUnit or gameplay fixtures; own classes remain major 61 (Java 17), with 251 core, 1,260 main and 69 addon classes. The main jar contains all three corrected loader declarations.

| Artifact under its module's `build/libs` | SHA-256 |
| --- | --- |
| `endercore-8.2.14.37-1.20.1+8a7ee6f2af.jar` | `814743857a3a78fa07d2d5d498e918e229615797eceb2f83078df6184340e70c` |
| `enderio-8.2.14.37-1.20.1+8a7ee6f2af.jar` | `b02d2ba1940505184d768ed5ff11011d1e89c67c9c42d5809c63f104df703de4` |
| `enderio-modded-conduits-8.2.14.37-1.20.1+8a7ee6f2af.jar` | `bfb0f42c91bbef45ac48d8aa3cf23cdf0bae5763e7e02f93082d8079ab4058cb` |

This is development-client evidence without optional integrations. Clean packaged client, all three bundled modules together on the client, singleplayer, multiplayer, rendered machine geometry/GUI interactions, facade/glass/render compatibility and client synchronization remain required. Continue using the directly verified hidden launch setup for those scenarios. Current nonfatal resource warnings include unused alloy-smelter animation frames and item-conduit texture mip reduction; classify/fix actual visual problems during those tests. The offline development identity cannot authorize Realms; Realms is outside the port objective.

The mandatory chemical/fluid execution-remainder conservation risks from cycle 037 remain unresolved, as do broader lifecycle/disk persistence, historical-save migration, integration parity and timestamped-manifest byte reproducibility. The Git recovery stash/refs remain intact; there are no unmerged paths or upstream tracking, and no push was performed. The full port goal remains active.
