# Cycle 041 — packaged TCP multiplayer and recipe synchronization

Status: progress, not completion. Cycle 040 verified packaged single-player tank interactions. This cycle adds a real separately installed dedicated-server connection, finds and fixes a login-blocking recipe decoder defect, and verifies tank state over reconnect and server restart. Full feature parity remains incomplete.

## Changes and reproduced failure

- Added the test-only `ClientMultiplayerSmoke` controller and multiplayer runner options. The client connects through native `ConnectScreen`, verifies an actual TCP socket/no integrated server, opens a tank through the block-use packet, sends an inventory shift-click and uses the actual redstone picker widget. It checks synchronized menu and block-entity state, disconnects/reconnects, and opens a fresh menu.
- Added `run_multiplayer.py`. It installs the official Forge server in a fresh owned directory, copies only the bundled distributable, binds to loopback and uses a disposable offline identity/world. Console commands set up fixtures and independently assert actual server NBT. A second server process reopens the same disk world; a new guarded client checks the persisted state. No server QA mod, development classpath, real user world or global input is used.
- The first actual connection completed Forge's handshake, joined the server and immediately disconnected. Its native `failure.png` shows `Codec$2 cannot be cast to StringRepresentable$EnumCodec`. `SoulBindingRecipe.Serializer` incorrectly cast Forge's extensible mob-category codec to the vanilla implementation when reading recipe synchronization. Replaced that cast with `MobCategory.byName`; unknown names now throw a deliberate `DecoderException`.
- Added three native registry/Jupiter regressions: the entire loaded native login recipe packet round trip, every current mob-category value through the actual recipe serializer, and malformed-category rejection. The first invocation ran the existing 112 cases because the new class was not yet in the explicit inventory; it is not counted as testing the additions. After adding the inventory entry, all three new cases failed with the reproduced cast defect. After the fix all 115 cases pass.
- The client harness now captures an unexpected native disconnect screen immediately instead of waiting for its overall scenario timeout. The runner retains summaries from failed client sessions as well as successful ones.

## Actual multiplayer results

The final run passes **14 exercise client checks, five restart client checks and five independent server assertions**. These are separate from unit/GameTest counts.

1. Real TCP login succeeds against the dedicated server. The client opens the standard 16,000 mB tank through a native block-use packet.
2. One water bucket shift-click produces exactly 1,000 mB and one empty output bucket, with no input/remaining player water bucket. The real widget packet changes server NBT to `active_with_signal`.
3. A console fixture changes the tank to 1,500 mB. Both the client menu and separate block entity used by the renderer receive that state.
4. Native disconnect/reconnect creates a fresh client connection/menu with 1,500 mB, the selected redstone mode and exactly one empty output bucket.
5. The server saves/stops; a new server process loads the same world. Server NBT and a newly launched TCP client both retain those values.

| Observation | Exercise/reconnect | After server restart |
| --- | ---: | ---: |
| Native observer samples | 2,340 | 986 |
| Owned native windows observed | 3 | 3 |
| Activation / foreground / onscreen observations | 0 / 0 / 0 | 0 / 0 / 0 |
| Client/observer exit | 0 | 0 |
| ERROR/FATAL entries | 0 | 0 |
| Actual framebuffer captures | 6 | 2 |

WindowServer queries worked and neither observer timed out. Every capture verifies all ten volume categories at 0.0, narrator OFF, hidden/unfocused/windowed state and uncaptured cursor, all shipped module IDs and twelve baked model states. Both dedicated server processes exit 0 without ERROR/FATAL entries. The initial failing client also remained hidden/silent: 14,733 native samples and zero activation/foreground/onscreen observations.

Visually inspected processed-tank, filled-world and post-restart captures show the correct bucket, fluid gauge, redstone icon, tank water, enchanter and tank models. Vanilla multiplayer chat/security/recipe notifications are visible in this isolated offline fixture, sometimes overlapping the scene. This is limited rendering evidence, not full UI/render parity. The pressurized tank is scenery here; its multiplayer interaction is not claimed from the standard-tank scenario.

## Commands and evidence

From repository root:

```sh
./gradlew :enderio:runRegistryTestServer --console=plain
./gradlew :enderio:runRegistryTestServer :enderio:reobfClientSmokeJar :enderio:reobfJar --console=plain
python3 scripts/porting/client/prepare_packaged.py \
  --name cycle-041-client \
  --java /Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java \
  --installer build/port-runtime/forge-47.4.28-installer.jar \
  --jar enderio/build/libs/enderio-8.2.14.40-1.20.1+c3d60fb222.jar \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar \
  --assets /Users/cyberpwn/.gradle/caches/neoformruntime/assets
python3 scripts/porting/client/run_multiplayer.py \
  --name cycle-041-multiplayer-fixed \
  --java /Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java \
  --installer build/port-runtime/forge-47.4.28-installer.jar \
  --jar enderio/build/libs/enderio-8.2.14.40-1.20.1+c3d60fb222.jar \
  --launch build/port-runtime/cycle-041-client/launch.json
./gradlew build --continue --console=plain
```

The initial failed multiplayer invocation used `--name cycle-041-multiplayer`; its output and world remain preserved. The client installation started with copied official libraries/version metadata from the preceding isolated installation, then preparation copied current mod snapshots and reset quiet settings; no prior world was copied. The fix run re-prepared that owned client profile with rebuilt main/QA jars. Server installations are fresh official installer outputs.

Full build passes **78 ordinary JUnit and 386 native GameTest results**, no failures/errors/skips: core unit 61, main CPU 16, addon unit 1; core native 170, Mekanism 7, main registry/Jupiter 115, main gameplay 76, addon native 18. Valid up-to-date outputs are reused where applicable; native profiles execute. Jupiter cases are counted once through their native wrapper. Profiles may overlap.

Evidence under `build/port-evidence/`, prefixed `cycle-041-`: initial/final multiplayer run logs, `multiplayer-exercise/` (failure PNG/result/focus/debug log), `multiplayer-fixed-exercise/`, `multiplayer-fixed-verify/` (results, focus, logs and PNGs), copied `multiplayer-results.json` and initial report, recipe reproduction/fix logs and failing XML, full build log, `final-test-results/`, `test-results.json`, `artifacts.json`, `installed-artifacts.json`, `package-comparison.json` and `qa/`. The final QA collection has 67 events: 27 pass, 40 info, zero fail/warn. Event counts include harness summaries; the actual client assertion count is 19.

## Artifact identity and remaining work

Candidate version `8.2.14.40-1.20.1+c3d60fb222` records the pre-cycle base. Both actual multiplayer peers use identical main-jar bytes with SHA-256 `b6c240ff334f0f4dd3286403e900af8dd1878967d7f31864f608d39ced74aa4d`. The installed separate QA jar is `f87ad29bde292145898ea0bd7923904e7e9720c5a3cf7fcd140233a114004807`.

The subsequent full build rewrites timestamped main/addon manifests. Recursive ZIP-content comparison finds only `Implementation-Timestamp` changes in those two manifests between the tested main jar and the final build output; classes/resources match. Byte-for-byte reproducibility remains unresolved and is not claimed.

| Full-build artifact under its module's `build/libs` | SHA-256 |
| --- | --- |
| `endercore-8.2.14.40-1.20.1+c3d60fb222.jar` | `baf3ddce8a21177e33ee95388c46f2c49bf05d22c20cb09306f304cc93ac4b58` |
| `enderio-8.2.14.40-1.20.1+c3d60fb222.jar` | `f3fce9eed740ccce6655b8de07e67ee6684ac0be66d5316846e1fa92ea6bf429` |
| `enderio-modded-conduits-8.2.14.40-1.20.1+c3d60fb222.jar` | `957525068fbf6a7c630aac6a38e85b71651c7b31a5aaaf238585b267d83c0f97` |

Own class counts remain 251/1,260/69, all major 61. Recursive artifact inspection finds no QA, guard/probe, JUnit or gameplay fixtures in production jars. All owned processes from this cycle exited; polling delays were not treated as failures/restart requests.

Next expand dedicated-client synchronization coverage, including data-map reload and remaining recipes/menus; resolve the recorded chemical/fluid execution-time remainder conservation risks. Complete feature-family gameplay, optional integrations, broader conduit/lifecycle/rendering, historical-save compatibility and reproducible packaging remain required. The user paused the full goal while requesting a push of current progress; implementation remains incomplete. The user then explicitly requested replacing remote `1.20.1` with this local port branch. The old remote tip was fetched into local recovery ref `refs/port-recovery/pre-port-publish-1.20.1` at `18ee1b17bf6879298e911fff751204b07f2e4228`; publish with an explicit lease against that tip and set the matching upstream. This authorized replacement supersedes the original remote-preservation instruction. No release is requested.
