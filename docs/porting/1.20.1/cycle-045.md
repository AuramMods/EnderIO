# Cycle 045 — reproduce all Forge distribution jars byte for byte

The earlier packaged builds changed the main and addon jar hashes whenever a manifest was regenerated. This cycle reproduces that variation, removes its cause and checks the actual build outputs and packaged runtime.

## Build and comparison

Two fresh detached worktrees at `0010ce7c14` assembled all three modules with the build and configuration caches disabled. EnderCore already matched byte for byte. The main and addon differed only at `META-INF/MANIFEST.MF`, including the nested addon manifest inside the main jar. Both scripts wrote a wall-clock `Implementation-Timestamp`. The original title, vendor, implementation version and Mixin manifest configuration continue to identify the artifacts.

The fix removes the timestamp from the main and addon manifest declarations. Gradle 9 already gives its archive tasks stable ordering, timestamps and permissions. ModDevGradle still runs the real reobfuscation and nested-jar packaging tasks. Two more fresh worktrees with the same source and the fix applied ran every packaging task with `--no-build-cache --no-configuration-cache`. The first used `TZ=UTC LC_ALL=C`, and the second used `TZ=America/Chicago LC_ALL=en_US.UTF-8`. Both executed all 34 tasks. The six resulting jars match byte for byte.

| Artifact for detached candidate version `8.2.14.44-dev+0010ce7c14` | SHA-256 from both builds |
| --- | --- |
| EnderCore runtime | `b31b096235322c91866f1934f656f55a449ec03ba20735ca59c544e73168d193` |
| Ender IO runtime with nested modules | `818222ed19649204f9bd23987d728bc25eb91217c003d65aebcc15bb03d7da15` |
| Modded conduits runtime | `c5f7f857e6b3495ce24a827430c9530f951f08569aa4662961b5f173730bbb6f` |
| Ender IO API | `8cfd593ad74034a9581484f4338cd4dc2938310f3304074ea8020c11fd6df8e1` |
| Ender IO sources | `6c5d07cc951f59741f564221412b4b832a8b1930e744b2bba29c9476fe45cbdc` |
| Modded conduits sources | `30b2d10a5338db6944ade273ced8aacab1accfd3253571aa9bd72c088e8d1ecd` |

These runs use Gradle 9.8.2 on Oracle JDK 21.0.9, the Java 17 Temurin 17.0.18 toolchain and the pinned Forge 47.4.28 and ModDevGradle 2.0.148 inputs on macOS arm64. The worktrees share the installed dependency cache and have independent project outputs. Matching bytes include the compressed archives and every nested jar. Cross-platform and alternate JDK comparisons remain open.

Git versioning incorporates the branch name when present. Reproduce with two detached checkouts of the same commit and identical pinned inputs. A named-branch build produces a different version string. See [Gradle's reproducible archive behavior](https://docs.gradle.org/current/userguide/working_with_files.html#sec:reproducible_archives).

## Reproduce from the committed source

Run from the port checkout with two unused worktree paths. The comparator consumes the finished outputs, compares the bytes and reports differing entries including nested jars when applicable.

```sh
git worktree add --detach build/repro-a HEAD
git worktree add --detach build/repro-b HEAD

TZ=UTC LC_ALL=C ./build/repro-a/gradlew -p build/repro-a \
  --no-build-cache --no-configuration-cache --console=plain \
  :endercore:assemble :enderio:assemble :enderio:apiJar :enderio:sourcesJar \
  :enderio-modded-conduits:assemble :enderio-modded-conduits:sourcesJar
TZ=America/Chicago LC_ALL=en_US.UTF-8 ./build/repro-b/gradlew -p build/repro-b \
  --no-build-cache --no-configuration-cache --console=plain \
  :endercore:assemble :enderio:assemble :enderio:apiJar :enderio:sourcesJar \
  :enderio-modded-conduits:assemble :enderio-modded-conduits:sourcesJar
python3 scripts/porting/compare_jars.py build/repro-a build/repro-b
```

The cycle used the same commands with `build/port-evidence/cycle-045-{before,after}-{a,b}` worktrees. The before run built the three runtime jars. The after run applied the tracked manifest changes and added the three API/source tasks. The exact artifacts are preserved under `build/port-evidence/cycle-045-assembly-snapshots`. The before and after comparison JSON, individual build logs and packaging patch remain under the same evidence prefix.

## Full build and packaged verification

`./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain` passes **78 ordinary JUnit and 410 native GameTests**, including all 92 discovered gameplay cases. XML contains zero failures, errors or skips. Ordinary JUnit counts are 61 core, 16 main CPU and one addon. Native counts are 170 core, seven Mekanism, 115 main registry, 92 main gameplay and 26 addon. The client QA jar remains separate.

The named local branch build produced version `8.2.14.44-1.20.1+0010ce7c14`. Its runtime jar hashes are:

| Module | SHA-256 |
| --- | --- |
| EnderCore | `70566d8128940c8f3b4a59769608a9ed07be862024a018cc8aea194dc6614657` |
| Ender IO | `c876ef998873b216e66d289e73623539e99f88edf734196f5feda53f25ad9dfa` |
| Modded conduits | `da1471eab39a762d528032a6738b9d60826f64412553e25c4562fcac3479f727` |

The named-branch candidate passes 19 packaged client checks and five dedicated-server assertions across a real TCP interaction, reconnect and server restart. Both peers load identical distributable bytes. The actual tank menu and redstone controls retain the fluid and bucket through restart, and the post-restart framebuffer was visually inspected. Both clients and both dedicated servers exited zero with no ERROR/FATAL log lines. The native observer recorded 1,464 and 989 samples with zero activation, foreground or onscreen observations. All eight client captures verified every volume at 0.0, narrator OFF and a hidden, unfocused window with the cursor uncaptured.

The isolated client profile copies official Forge libraries and version metadata from cycle 042, then installs both current jars and resets quiet settings. The dedicated server profile is a fresh official installation. Both installations use the base modules without optional mods. The multiplayer script and isolated client profile are described in [the client harness](client-harness.md). Actual commands after preparing the isolated profile follow.

```sh
python3 scripts/porting/client/prepare_packaged.py \
  --name cycle-045-client \
  --java /Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java \
  --installer build/port-runtime/forge-47.4.28-installer.jar \
  --jar enderio/build/libs/enderio-8.2.14.44-1.20.1+0010ce7c14.jar \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar \
  --assets /Users/cyberpwn/.gradle/caches/neoformruntime/assets
python3 scripts/porting/client/run_multiplayer.py \
  --name cycle-045-multiplayer \
  --java /Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java \
  --installer build/port-runtime/forge-47.4.28-installer.jar \
  --jar enderio/build/libs/enderio-8.2.14.44-1.20.1+0010ce7c14.jar \
  --launch build/port-runtime/cycle-045-client/launch.json
```

Full build and XML/JSON results, installed hashes, multiplayer reports, logs and screenshots are stored under `build/port-evidence/cycle-045-*`. The four temporary worktrees were removed after saving their assembled artifacts. Full gameplay and integration parity, historical migration and further packaged client interactions remain open. The port goal remains active.
