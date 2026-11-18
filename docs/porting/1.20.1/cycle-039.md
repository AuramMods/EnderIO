# Cycle 039 — clean packaged client and bundled-module startup

Status: progress, not completion. Cycle 038 made verified progress by establishing the hidden development-client harness. This cycle runs the actual distributable in an official Forge client installation. No full gameplay, singleplayer or multiplayer claim follows from a title-screen pass.

## Changes

- Added a separate test-only client QA jar and explicit ModDevGradle `RemapJar` task using the configured named-to-SRG operation. It remains outside the production jar and publication variants. The first attempt used the general `reobfuscate` helper, which expects runtime/API publication configurations absent from this plain test source set; the direct remapping task fixes that actual Gradle error without changing shipped modules.
- Added a reproducible packaged-client preparer using the official Forge installer and Mojang/Forge version metadata. It downloads/verifies actual library/asset content, merges inherited library versions, selects native macOS artifacts, snapshots the distributable and QA jar, and writes launch arguments, artifact records and quiet settings. Its working directory and installer logs stay inside the owned installation.
- Extended the observed runner to accept an exported launch file. Extended the QA mod to record actual mod IDs, versions and file paths, and require specified IDs. The packaged profile requires all three shipped mod IDs.
- Corrected the launcher's inherited vanilla-jar exclusion after a real module-layer failure. Forge's metadata excludes the selected version's jar filename; our inherited vanilla jar retains its original name. Adding that actual filename to the exclusion leaves Forge's patched Minecraft as the runtime implementation. This does not remove any Ender IO module or feature.

## Actual installation and execution

The official Forge 47.4.28 installer initially reported that the new directory lacked a launcher profile. Created an empty launcher-profile fixture solely in `build/port-runtime/cycle-039-client` and reran the actual headless installer successfully. It downloaded and patched the real client. The preparer also successfully exercised its `--install` path against that owned installation.

Resolved **80 launch libraries and 3,577 unique official asset objects**. Asset contents are shared with the existing ordinary Minecraft asset cache and verified by the manifest hashes; no development classes or mapped development libraries are used at runtime. The installed `mods` directory contains the distributable `enderio.jar` and separate reobfuscated `enderio-client-qa.jar`. EnderCore, Modded Conduits and GraphLib3 remain nested inside the main distributable.

The packaged classpath's actual LWJGL native probe passed **100 rendered/pixel-read frames**, hidden/unfocused/windowed with normal cursor mode. Native observation: **146 samples, two owned windows, zero activation/foreground/onscreen events**, available WindowServer queries, exit zero.

The first actual packaged client then failed with `ResolutionException`: both patched Minecraft and the inherited original jar exported a Minecraft package. Its observer recorded zero activation/visibility events and a nonzero exit. The launcher correction described above resolves that failure.

The final packaged client exits zero and verifies:

- All six expected mod entries: Minecraft, Forge, Ender IO, EnderCore, Modded Conduits and the separate QA mod. Ender IO's actual source path is the installed `mods/enderio.jar`; Forge reports the nested modules as virtual files with empty file-path strings. All three shipped module versions match `8.2.14.38-1.20.1+9cee1d53c9`.
- An actual **960×540 Apple M4 Max framebuffer**, 31,320 distinct pixels, visually inspected title-screen screenshot and all **12 enchanter/spawner baked block states present**.
- Narrator **OFF**, every one of ten audio categories **0.0**, hidden/unfocused/windowed state and uncaptured cursor.
- Independent macOS observation: **762 samples, three owned windows, zero activation/foreground/onscreen events**, available window queries, no timeout and exit zero.
- **No ERROR/FATAL log lines.** The offline fixture's Realms authorization message is informational. Existing nonfatal texture/mipmap and vanilla shader/sound warnings remain for visual investigation where relevant.

Commands and reproduction instructions are in the [client harness document](client-harness.md). Actual commands included:

```sh
./gradlew :enderio:reobfClientSmokeJar build --continue --console=plain
python3 scripts/porting/client/prepare_packaged.py --install \
  --name cycle-039-client \
  --java /Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java \
  --installer build/port-runtime/forge-47.4.28-installer.jar \
  --jar enderio/build/libs/enderio-8.2.14.38-1.20.1+9cee1d53c9.jar \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar \
  --assets /Users/cyberpwn/.gradle/caches/neoformruntime/assets
python3 scripts/porting/client/run_smoke.py \
  --launch build/port-runtime/cycle-039-client/launch.json \
  --output build/port-evidence/cycle-039-packaged-title-fixed
```

The full build passes with **78 ordinary JUnit and 383 native GameTest results**, zero failures/errors/skips. Counts: core unit 61, main CPU 16, addon unit 1; core native 170, Mekanism adapters 7, main registry/Jupiter 112, main gameplay 76, addon native 18. Valid up-to-date unit outputs are reused; native profiles run. In-server Jupiter is counted once; profile overlap remains possible. The launcher exclusion fix affects the standalone Python launcher, not those compiled artifacts.

Evidence under `build/port-evidence/`, prefixed `cycle-039-`: installer initial/final logs and extracted metadata; QA-jar initial/fixed logs; `build.log`; `prepare-packaged*.log` and `prepare-with-install.log`; `packaged-probe.json` and its log; `packaged-title/` (initial failure), `packaged-title-fixed/` (final summary/result/focus/log/PNG); `test-results.json`, `final-test-results/`, `artifacts.json`, and `qa/`. The installed libraries, artifact hashes and launch arguments are under `build/port-runtime/cycle-039-client`. All owned process handles reached terminal states. No user profile or world was used.

## Artifact record and next work

Version `8.2.14.38-1.20.1+9cee1d53c9` identifies the pre-cycle base. Own classes remain Java 17 major 61: core 251, main 1,260, addon 69. Recursive production-jar inspection finds no QA classes, window agent/probe, JUnit or gameplay fixture resources. The separately installed QA jar SHA-256 is `581430472c439ef4f02da9446b79936b73815e928a8e35935cfe5b33a98d0639`.

| Artifact under its module's `build/libs` | SHA-256 |
| --- | --- |
| `endercore-8.2.14.38-1.20.1+9cee1d53c9.jar` | `55edcba9b052d6a2d4760b94f33141e9f42f8b76bcdd39eb3d4d61f436575335` |
| `enderio-8.2.14.38-1.20.1+9cee1d53c9.jar` | `7f4a8a4d3968c60f73b2f3e5b1be87de7fcbf30675500fcb018e5a0681ec5b20` |
| `enderio-modded-conduits-8.2.14.38-1.20.1+9cee1d53c9.jar` | `f0736ce2ec04a62a6b738706313aeb45a3099bdff2a0621a22120e62db452042` |

Next, extend this packaged scenario into a disposable integrated-server world and actual menu/interaction/render checks, then a real dedicated-server connection and synchronization/reconnect scenarios. Native `WorldOpenFlows.createFreshLevel` is present in the pinned 1.20.1 API and can start that owned fixture without global input or showing a window; source inspection alone does not verify it.

Every feature family, optional integrations present/absent, full visual parity, wider persistence and historical-save migration still require evidence. Chemical/fluid execution-time remainder loss risks from cycle 037 remain mandatory unresolved work, as does timestamped-manifest byte reproducibility. The full goal remains active; no push or release was performed.
