# Cycle 040 — packaged single-player menus, rendering and disk reopen

Status: progress, not completion. Cycle 039 made verified progress with packaged startup. This cycle exercises real packaged single-player gameplay for both tank variants, using the existing verified hidden client setup. The full feature matrix and actual dedicated-server multiplayer remain incomplete.

## Changes and scope

- Added the test-only `ClientWorldSmoke` scenario and `--scenario singleplayer` / `--tank standard|pressurized` runner options. Native `WorldOpenFlows` creates and later reopens a disposable flat survival world, using a unique output basename inside the isolated installation. No real user world or global input is touched.
- The actual integrated server places a stone platform, both tank block variants and an enchanter, seeds one water bucket in the player's inventory, and teleports the player into interaction range. These are explicit fixture operations, not player placement/crafting coverage.
- The client opens the target tank through `MultiPlayerGameMode.useItemOn`, moves the water bucket through `handleInventoryMouseClick(QUICK_MOVE)`, and checks the actual `FluidTankScreen`/menu and packet synchronization. The test invokes the real redstone picker scroll handler, then observes the resulting server state. It does not directly set that redstone state on the server.
- A later server-side fixture adds 500 mB to the actual tank. The client checks both menu synchronization and the separate block-entity state used by the renderer. Native disconnect/clear-level saves and stops the integrated server; native world reopen creates a new server and reloads the same disk world before another real block-use/menu interaction.
- Shared framebuffer/state observations now support title and world scenarios. Complete JSON remains on disk while the Java QA event records a compact result path and checks. Test instrumentation remains separate from the distributable.

No production behavior was changed in this cycle. The newly exercised packaged paths passed; this adds evidence without claiming the rest of the mod verified.

## Actual results

The first standard-tank run passed thirteen checks. Final coverage was strengthened with exact container counts, client block-entity observations, visible world fluid and a second tank variant. Both final runs pass **15 checks each**, giving **30 final scenario checks**, separate from the unit/GameTest counts below.

| Observation | Standard tank | Pressurized tank |
| --- | ---: | ---: |
| Menu capacity | 16,000 mB | 32,000 mB |
| Fluid after actual bucket shift-click/processing | 1,000 mB | 1,000 mB |
| Empty output buckets; remaining water buckets | 1; 0 | 1; 0 |
| Fluid after server fixture update | 1,500 mB | 1,500 mB |
| Client block-entity fluid used by renderer | 1,500 mB | 1,500 mB |
| Saved/reopened fluid; empty output buckets | 1,500 mB; 1 | 1,500 mB; 1 |
| Native observer samples | 1,500 | 2,543 |
| Activation / foreground / onscreen events | 0 / 0 / 0 | 0 / 0 / 0 |
| ERROR/FATAL log entries | 0 | 0 |
| Client/observer exit status | 0 | 0 |

Both variants retain `ACTIVE_WITH_SIGNAL` after the widget update and disk reopen. New client menus receive the saved state. Both observers saw three owned native windows, had working WindowServer queries and did not time out. The pressurized run was inspected while still progressing, then completed normally without intervention or restart.

Six 960×540 framebuffer captures per variant cover the title, initial world, processed tank menu, updated menu, visible world fluid and reopened menu. Each capture directly checks hidden/unfocused/windowed state, uncaptured native cursor, narrator **OFF**, all ten volume categories **0.0**, loaded module IDs and the existing twelve baked model states. The world and menu images were visually inspected: the enchanter and both tank models render, water is visible after closing the GUI, and the fluid gauge/output bucket/redstone icon are present. The absence of menu title/inventory labels matches the baseline `EnderContainerScreen.shouldRenderLabels = false`; that behavior is unchanged. This is one scene and camera angle, not exhaustive render parity.

The isolated installation `build/port-runtime/cycle-040-client` was copied from cycle 039's actual official installed libraries/versions into a fresh directory, then prepared with current artifact snapshots and quiet settings. Its worlds are only the generated test fixtures. Installed main jar and standalone QA jar are recorded in `cycle-040-installed-artifacts.json`. No optional integrations or development class directories are used at runtime.

Actual commands:

```sh
./gradlew :enderio:reobfClientSmokeJar :enderio:reobfJar --console=plain
./gradlew :enderio:reobfClientSmokeJar build --continue --console=plain
python3 scripts/porting/client/prepare_packaged.py \
  --name cycle-040-client \
  --java /Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java \
  --installer build/port-runtime/forge-47.4.28-installer.jar \
  --jar enderio/build/libs/enderio-8.2.14.39-1.20.1+581963e233.jar \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar \
  --assets /Users/cyberpwn/.gradle/caches/neoformruntime/assets
python3 scripts/porting/client/run_smoke.py \
  --launch build/port-runtime/cycle-040-client/launch.json \
  --scenario singleplayer --timeout 300 \
  --output build/port-evidence/cycle-040-standard-final
python3 scripts/porting/client/run_smoke.py \
  --launch build/port-runtime/cycle-040-client/launch.json \
  --scenario singleplayer --tank pressurized --timeout 300 \
  --output build/port-evidence/cycle-040-pressurized-final
```

The full build passes **78 ordinary JUnit and 383 native GameTest results**, zero failures/errors/skips: core unit 61, main CPU 16, addon unit 1; core native 170, Mekanism adapters 7, main registry/Jupiter 112, main gameplay 76, addon native 18. Valid up-to-date unit outputs are reused where applicable; native profiles execute. In-server Jupiter is counted once; profiles can overlap. The final QA-only count/renderer/variant additions were subsequently recompiled/reobfuscated and exercised by both packaged runs; production artifacts were unchanged after the full build.

Evidence under `build/port-evidence/`, prefixed `cycle-040-`: QA compile/package/build logs; preparer logs; `singleplayer/` (initial passing scenario), `standard-final/`, `pressurized-final/` (summary, detailed result, focus report, runtime log and six PNGs each); `test-results.json`, `final-test-results/`, `artifacts.json`, `installed-artifacts.json`, and `qa/`. The initial preparer invocation used an incorrectly typed artifact hash and failed before launch; the actual filename was then used successfully. All owned client, server and build processes completed; no runtime restart was inferred from a polling delay.

## Artifacts and next work

Candidate version `8.2.14.39-1.20.1+581963e233` identifies the pre-cycle base. Own class counts remain 251 core, 1,260 main and 69 addon, all major 61. Recursive production-jar inspection finds no client QA, window agent/probe, JUnit or gameplay fixtures. The separate QA jar SHA-256 is `e28ff82a49d914422ce9dea6aaa995b639f668125e2099cd79ed2080846e4955`.

| Artifact under its module's `build/libs` | SHA-256 |
| --- | --- |
| `endercore-8.2.14.39-1.20.1+581963e233.jar` | `f052aeafe49bfe48c3d02f76cbe0db5feb2df8808fcb53cf2d9c5061d3a62674` |
| `enderio-8.2.14.39-1.20.1+581963e233.jar` | `5826d3d5b9ee17cc7f86336b4fca590a6db94822dfa7063e6fa19a528c291d96` |
| `enderio-modded-conduits-8.2.14.39-1.20.1+581963e233.jar` | `3f87f5fc3257b80be19c67ccac0d13a43cef3b327a8bfb415976d0c40670a745` |

Next, connect the packaged client to an actual separately launched dedicated Forge server and verify native menu/state changes, disconnect/reconnect and persistence over that TCP connection. The present test uses a real integrated client/server connection, not a dedicated server. Continue expanding actual interactions across all feature families, including tanks' remaining drain/recipe/automation/placement paths.

Optional integrations present/absent, broader conduit/GUI/rendering/lifecycle coverage, historical-save migration and timestamped-manifest reproducibility remain incomplete. The chemical/fluid execution-time remainder conservation risks recorded in cycle 037 remain mandatory unresolved work. The full port goal remains active; no push or release was performed.
