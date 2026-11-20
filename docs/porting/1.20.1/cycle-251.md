# Cycle 251 — Rebuild and audit the distributable on Java 17

This cycle uses two detached checkouts of `fb31bbe8b` with independent project outputs and a shared dependency cache. Both run the Gradle wrapper with Temurin 17.0.18, Forge 47.4.28 and the pinned build inputs. One build uses UTC and the C locale. The other uses America/Chicago and en_US.UTF-8. The archive comparator checks the six actual outputs and descends into nested jars if they differ.

The runtime uses official Forge installations, the remapped distributable and a separate QA jar. The core profile has neither third-party mods nor a development classpath. The combined dedicated test installs all 15 pinned shared optional jars. The combined client also installs Athena, Embeddium and Oculus. [Dependency pins](dependencies.md)

The runtime registry audit covers the deferred block and item registers as well as the entries registered through them. The declaration comparison finds the same 257 names in the six audited baseline source classes and the feature matrix. Three of those names are the deferred register objects themselves.

## Build and data generation

```sh
port_evidence=build/port-evidence/cycle-251-distribution
mkdir -p "$port_evidence"
git worktree add --detach "$port_evidence/utc" fb31bbe8b
git worktree add --detach "$port_evidence/local" fb31bbe8b
export JAVA_HOME="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home"
export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4'

TZ=UTC LC_ALL=C "$port_evidence/utc/gradlew" -p "$port_evidence/utc" \
  --no-build-cache --no-configuration-cache --max-workers=2 --console=plain \
  build --continue :enderio:reobfClientSmokeJar :enderio:apiJar :enderio:sourcesJar :enderio-modded-conduits:sourcesJar
TZ=America/Chicago LC_ALL=en_US.UTF-8 "$port_evidence/local/gradlew" -p "$port_evidence/local" \
  --no-build-cache --no-configuration-cache --max-workers=2 --console=plain \
  :endercore:assemble :enderio:assemble :enderio:apiJar :enderio:sourcesJar \
  :enderio-modded-conduits:assemble :enderio-modded-conduits:sourcesJar
python3 -B scripts/porting/compare_jars.py "$port_evidence/utc" "$port_evidence/local" > "$port_evidence/jar-comparison.json"

TZ=America/Chicago LC_ALL=en_US.UTF-8 "$port_evidence/local/gradlew" -p "$port_evidence/local" \
  --no-build-cache --no-configuration-cache --max-workers=2 --console=plain :enderio:runData :enderio-modded-conduits:runData
git -C "$port_evidence/local" diff --exit-code -- enderio/src/generated/resources enderio-modded-conduits/src/generated/resources
```

The packaged tests use `build/cycle-251-runtime.zsh`. The script prepares guarded Forge client profiles from the official Forge 47.4.28 libraries and version manifest already used in cycle 250. Its options include the paths to the separate QA jar and pinned mod artifacts listed in `build/cycle-177-pins.zsh`.

```sh
port_jar="$port_evidence/local/enderio/build/libs/enderio-8.2.14.250-dev+fb31bbe8bc.jar"
zsh build/cycle-251-runtime.zsh "$port_jar" registry
zsh build/cycle-251-runtime.zsh "$port_jar" bank
zsh build/cycle-251-runtime.zsh "$port_jar" core
zsh build/cycle-251-runtime.zsh "$port_jar" combined
zsh build/cycle-251-runtime.zsh "$port_jar" shader
zsh build/cycle-251-runtime.zsh "$port_jar" tcp
python3 -B scripts/porting/client/audit_models.py --core "$port_evidence/model-inventory-recheck/summary.json" \
  --mekanism "$port_evidence/shared-model/summary.json" > "$port_evidence/model-reconciliation.json"
```

## Results

- The fresh UTC build passes 79 ordinary unit cases and 548 native runs, with zero failures, errors or skips. Gradle verifies that all 220 discovered main gameplay cases run. Other native counts are 171 core cases, seven isolated Mekanism cases, 117 main registry cases and 33 addon cases. See `build/port-evidence/cycle-251-distribution/{utc-build.log,test-summary.json,test-results/}`.
- Both builds produce byte-identical copies of all six archives. Artifact inspection finds Java 17 class files for all three modules and a Java 8-compatible bundled GraphLib3 library. The main jar contains the matching EnderCore and Modded Conduits jars plus GraphLib3. The client QA jar is separate and also matches byte for byte. See `build/port-evidence/cycle-251-distribution/{jar-comparison.json,distribution.json,jars/}`.
- Both datagen tasks complete and leave all 6,028 tracked files in the generated resource trees unchanged. In the main datagen run, Embeddium logs its known Oculus mixin integrity message and attempts to fingerprint a client singleton that headless datagen never creates. The generators complete afterward. See `build/port-evidence/cycle-251-distribution/datagen.log`.
- The core registry audit reconciles 1,222 enabled baseline recipes, 825 block definitions, 938 item definitions, 173 tags and ten conduit variants. The combined profile reconciles 1,248 baseline recipes and 17 conduit variants, including six cooking recipes replaced by Almost Unified. Both packaged servers exit zero without runtime or registry errors, and the combined server passes all 15 assertions. The measured reports live under `build/port-runtime/cycle-251-{core,combined}-audit/`.
- The core-only capacitor bank fixture passes 13 assertions across initial placement, chunk eviction and reload, split, rejoin and dedicated restart. The charged bridge drop and final receiver account for the exact original FE. The only installed jar is Ender IO, and the package inventory locates GraphLib3 3.0.5 inside it. Both servers exit zero without runtime or registry errors. See `build/port-runtime/cycle-251-graphlib/capacitor-bank-results.json`.
- The single-player tank passes all 15 checks, including ordinary bucket handling, the live menu and reopening from disk. The core and shared-mod model profiles pass 31 checks each. Both reconcile 825 baseline blocks and 3,416 baked states before and after resource reload. The item inventories reconcile all 938 core or 939 Mekanism-enabled baseline items. Each profile also passes the 12 conduit arrow depth checks. See `build/port-evidence/cycle-251-distribution/{singleplayer-recheck,model-inventory-recheck,shared-model}/summary.json` and `model-reconciliation.json` in the evidence directory.
- The redstone progression fixture passes 34 dedicated assertions and 36 client checks on the same archive. The crafted counter survives two cold starts with the eighth pulse held high and wraps when the lever creates a new edge. All three server processes exit zero without runtime or registry errors. See `build/port-runtime/cycle-251-tcp/redstone-progression-results.json`.
- The first model audit with Oculus installed passes its 31 client checks. The wrapper reports a failure because that case has no shader-specific assertion for Embeddium's mixin support notice. The final model audit uses the 15 shared mods, and the dedicated shader fixture checks the active renderer directly.
- The dedicated shader fixture passes 17 client checks and seven server assertions. The authored shader draws the world with the pinned optional stack. JEI displays both tin outputs chosen by the server before and after a datapack reload, transfers the original tagged ingredients, and the actual SAG Mill produces both displayed results. Both outputs remain after restart. Both servers exit zero without runtime or registry errors, and the client report retains the expected Embeddium notice. See `build/port-runtime/cycle-251-combined-shader/unified-sag-results.json`.
- The five final client launches pass 130 checks on the preserved main archive. Their 15 captures confirm all ten sound categories at zero, narrator off, and an uncaptured cursor. The native observer records zero activation, foreground or onscreen events across 15,138 samples. The compiled archives and native XML reports are preserved in the cycle evidence directory.

| Distribution archive | SHA-256 |
| --- | --- |
| Ender IO runtime | `a75142885db0e9038d954a755ce1fbbe904c4c45c41725d5ea745e061ad2c0fd` |
| EnderCore runtime | `d969f1ba8afcad20bc284cc13eec76704fcb15171935b207499c034af5b1f020` |
| Modded Conduits runtime | `59cbcb4a46f3d9446e1bd6fced01a28e45ef46d7e41591b5a1c615c17fc9f5d6` |
| Ender IO API | `2347f16282c78e454678a28d67d85390f11b9096e9adf366c33127289dde4760` |
| Ender IO sources | `b180aff9b97333a3344038f1df29a943dc24b7a9c11392c242db0f0228d42007` |
| Modded Conduits sources | `4811db9222f4c9b2b4543278077d6c248b8f79a0d73aa3b210456b69fd3e2ad6` |

## Next verification

Reconcile the shared menu, block-entity, sound and particle registrations with the baseline. Exercise the Reinforced Obsidian ambient effect in a live packaged client.
