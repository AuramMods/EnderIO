# Cycle 255 — CI toolchain repair and completion audit

The audit starts from clean commit `7fd7fcee7`. It compares the fixed 1.21.1 source inventory with actual test XML, packaged assets and live registries, then records the remaining gameplay and delivery checks. The installation document describes the tested runtime and historical migration scope. [Completion audit](completion-audit.md), [Installation and upgrade](install.md)

Two fresh checkouts with `CI=true` fail to compile five anonymous subclasses. Java 17 reports `variable o is already defined` in the two filter menus, Painted Crafting Table, and redstone timer and counting filter screens. Removing the old environment conditional still selects ModDevGradle's binary-only Forge path under CI and reproduces the same errors. All three modules now request Forge source recompilation explicitly. This keeps the same pinned runtime mappings, Java version, production classes and test settings.

The first full run with that toolchain finds flowing water in the blocked-harvest wheat plot. Both powered wheat tests previously used a waterlogged Farming Station beside the crop. The tests now share a dry station and a contained hydration source beneath the neighboring light. The fixture keeps the timed blocked-harvest, capacitor, output and replanting assertions.

## Commands

The evidence directory holds the three-line build configuration replacement and its explanatory comment as `forge-source-toolchain.patch`. The before runs use the same commands on detached checkouts of the same revision before applying the patch.

```sh
port_evidence=build/port-evidence/cycle-255-completion
mkdir -p "$port_evidence"
git worktree add --detach "$port_evidence/source-utc" 7fd7fcee7
git worktree add --detach "$port_evidence/source-local" 7fd7fcee7
git -C "$port_evidence/source-utc" apply ../forge-source-toolchain.patch
git -C "$port_evidence/source-local" apply ../forge-source-toolchain.patch
export JAVA_HOME="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home"
export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' CI=true

TZ=UTC LC_ALL=C "$port_evidence/source-utc/gradlew" -p "$port_evidence/source-utc" \
  --no-build-cache --no-configuration-cache --max-workers=2 --console=plain \
  build --continue :enderio:reobfClientSmokeJar :enderio:apiJar :enderio:sourcesJar :enderio-modded-conduits:sourcesJar
TZ=America/Chicago LC_ALL=en_US.UTF-8 "$port_evidence/source-local/gradlew" -p "$port_evidence/source-local" \
  --no-build-cache --no-configuration-cache --max-workers=2 --console=plain \
  :endercore:assemble :enderio:assemble :enderio:apiJar :enderio:sourcesJar \
  :enderio-modded-conduits:assemble :enderio-modded-conduits:sourcesJar :enderio:reobfClientSmokeJar
python3 -B scripts/porting/compare_jars.py "$port_evidence/source-utc" "$port_evidence/source-local" \
  > "$port_evidence/jar-comparison.json"

git -C "$port_evidence/source-utc" apply ../farm-fixture.patch
env -u JAVA_TOOL_OPTIONS TZ=UTC LANG=C LC_ALL=C "$port_evidence/source-utc/gradlew" -p "$port_evidence/source-utc" \
  --no-daemon --no-build-cache --no-configuration-cache check --continue

TZ=America/Chicago LC_ALL=en_US.UTF-8 "$port_evidence/source-local/gradlew" -p "$port_evidence/source-local" \
  --no-build-cache --no-configuration-cache --max-workers=2 --console=plain :enderio:runData :enderio-modded-conduits:runData
git -C "$port_evidence/source-local" diff --exit-code -- enderio/src/generated/resources enderio-modded-conduits/src/generated/resources

port_jar="$port_evidence/source-local/enderio/build/libs/enderio-8.2.14.254-dev+7fd7fcee72.jar"
python3 -B "$port_evidence/compare-media.py" "$port_jar" > "$port_evidence/media.json"
python3 -B "$port_evidence/audit-tests.py" "$port_evidence/source-utc" > "$port_evidence/xml-audit.json"
python3 -B "$port_evidence/typed-registries.py" build/port-runtime/cycle-255-core-audit/registry-snapshot.json \
  build/port-runtime/cycle-255-combined-audit/registry-snapshot.json > "$port_evidence/typed-registries.json"
```

The packaged runs use `build/cycle-255-runtime.zsh` around the existing registry, migration and guarded client scripts. It reads the retained candidate and QA archives and uses the original Forge libraries and optional pinned artifacts. The migration runner builds a fresh Forge 6.2 fixture and converts its copy. The guard prepares the client profiles separately.

```sh
zsh build/cycle-255-runtime.zsh core
zsh build/cycle-255-runtime.zsh combined
zsh build/cycle-255-runtime.zsh legacy
zsh build/cycle-255-runtime.zsh singleplayer
zsh build/cycle-255-runtime.zsh redstone -verified
```

## Results

- The initial two CI builds and both attempts using the toolchain default reproduce the five javac errors. The source-recompiled toolchain assembles all six distribution archives under both `CI=true` environments. All six match byte for byte across UTC/C and America/Chicago/en_US.UTF-8. The standalone modules match their bundled copies, the Ender IO modules use Java 17 class files, and the production jar bundles GraphLib3 3.0.5. See `build/port-evidence/cycle-255-completion/{jar-comparison,distribution}.json`.
- The first clean source-toolchain build reports one failure in the blocked-harvest fixture. After the fixture repair, the CI check exits successfully and verifies execution of all 220 discovered main gameplay cases. The retained XML from the two stages contains 79 passing unit cases and 548 passing native runs with zero failures, errors or skips. Native runs comprise 171 EnderCore, seven Mekanism, 220 main gameplay, 117 registry and 33 modded-conduit cases. All 180 recorded baseline methods, including 58 native cases, appear in passing reports. See `build/port-evidence/cycle-255-completion/{pre-fixture-fix,source-utc-check.log,test-results,xml-audit.json}`.
- The tested runtime is `enderio-8.2.14.254-dev+7fd7fcee72.jar`, SHA-256 `8e260e86ca78d36482cdf55d4efb848c03b938c1e62ba4ad491574ff4308de2b`. Both datagen tasks exit successfully and leave all 6,028 tracked generated files unchanged. The optional Embeddium fingerprint code logs a missing Minecraft instance during headless datagen. All 634 baseline textures, 44 animation metadata files and five sounds match the upstream bytes. See `build/port-evidence/cycle-255-completion/{datagen.log,media.json}`.
- The core and combined registry profiles pass and account for 1,222 and 1,248 baseline recipes and 10 and 17 registered conduit variants respectively. The source comparison accounts for nine tested typed catalogs, including 22 fluid IDs, Painted Sand, both travel targets and serializers, and the core and addon conduit types, configurations and node data. It includes the Forge fluid and chemical node data used for saved remainders. See `build/port-evidence/cycle-255-completion/typed-registries.json` and `build/port-runtime/cycle-255-{core,combined}-audit/registry-audit-results.json`.
- The Forge 6.2.18-beta fixture passes 100 assertions through conversion and dedicated restart. All three owned server processes exit successfully. See `build/port-runtime/cycle-255-legacy-storage/legacy-migration-results.json`.
- The packaged single-player tank world passes 15 checks, including a saved-world reopen. The packaged dedicated redstone profile passes 34 server assertions and 36 client checks across the initial run and two cold starts. The native observer records zero activations, foreground samples or onscreen samples over 6,717 observations. Narration is off and all ten volume categories are zero in both clients. The first concurrent redstone preparation lost the shared asset index temporary file, so the successful run starts from a fresh profile after single-player preparation. See `build/port-evidence/cycle-255-completion/{cycle-255-singleplayer/summary.json,redstone.log,redstone-verified.log}` and `build/port-runtime/cycle-255-redstone-verified/redstone-progression-results.json`.

## Next verification

Exercise Vacuum Chest and XP Vacuum collection and conservation across actual chunk eviction with blocked routes. The completion audit also records the remaining gameplay variants and final delivery checks. [Completion audit](completion-audit.md)
