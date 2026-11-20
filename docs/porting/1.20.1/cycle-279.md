# Cycle 279 — Verify the final Forge distribution

The distribution comes from commit `8c0fe1b6d43b23db28f8029c84b3caf1e770fcf2` on `1.20.1`, which includes the stone caps on both glass lighting test shafts. The build uses Temurin 17.0.18, Gradle 9.8.2, Minecraft 1.20.1 and Forge 47.4.28.

## Commands

```sh
port_evidence=build/port-evidence/cycle-279-delivery
mkdir -p "$port_evidence"
for port_checkout in source-utc source-local; do
  git worktree add --detach "$port_evidence/$port_checkout" 8c0fe1b6d43b23db28f8029c84b3caf1e770fcf2
done
export JAVA_HOME="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
export CI=true JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=4
port_flags=(--no-daemon --no-build-cache --no-configuration-cache --max-workers=2 --console=plain)
port_utc="$port_evidence/source-utc"
port_local="$port_evidence/source-local"
TZ=UTC LC_ALL=C "$port_utc/gradlew" -p "$port_utc" "${port_flags[@]}" build --continue \
  :enderio:reobfClientSmokeJar :enderio:apiJar :enderio:sourcesJar :enderio-modded-conduits:sourcesJar
TZ=America/Chicago LC_ALL=en_US.UTF-8 "$port_local/gradlew" -p "$port_local" "${port_flags[@]}" \
  :endercore:assemble :enderio:assemble :enderio:apiJar :enderio:sourcesJar \
  :enderio-modded-conduits:assemble :enderio-modded-conduits:sourcesJar :enderio:reobfClientSmokeJar
TZ=America/Chicago LC_ALL=en_US.UTF-8 "$port_local/gradlew" -p "$port_local" "${port_flags[@]}" \
  :enderio:runData :enderio-modded-conduits:runData
git -C "$port_local" diff --exit-code -- enderio/src/generated/resources enderio-modded-conduits/src/generated/resources

port_jar="$port_local/enderio/build/libs/enderio-8.2.14.278-dev+8c0fe1b6d4.jar"
python3 -B scripts/porting/compare_jars.py "$port_utc" "$port_local"
python3 -B build/port-evidence/cycle-272-release/inspect-distribution.py "$port_local" "$port_jar"
python3 -B build/port-evidence/cycle-255-completion/compare-media.py "$port_jar"
python3 -B build/port-evidence/cycle-255-completion/audit-tests.py "$port_utc"
```

The packaged server and client profiles use installed Forge libraries and the same runtime archive. The combined profile uses the shared optional mods and client renderers in the [dependency table](dependencies.md). The client uses the isolated options, window guard and native observation described in the [client harness](client-harness.md).

```sh
for port_scenario in core-audit combined-audit legacy singleplayer mixed-facade combined-sag; do
  zsh "$port_evidence/run-delivery.zsh" "$port_scenario"
done
python3 -B scripts/porting/client/run_smoke.py \
  --launch build/port-runtime/cycle-279-singleplayer-client/launch.json \
  --scenario model-inventory --output "$port_evidence/baked-models" --timeout 180
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Djava.net.preferIPv4Stack=true' \
  python3 -Bu scripts/porting/packaged_glass_light.py --name cycle-279-glass --port 26456 \
  --java "$JAVA_HOME/bin/java" --forge-server build/port-runtime/cycle-047-legacy-discover --jar "$port_jar"
python3 -B "$port_evidence/audit-runtime.py"
```

## Native fixture corrections

The clean UTC build passes the glass lighting test and fails the native granite fire case with no grain drop on extinction. Both vanilla fire cases now burn inside a stone enclosure and check the original base and live flame immediately before extinction. They retain the configured minimum age, the original grain drop and the expected granite or stone result. The local gameplay run with the enclosed fire fixtures passes both fire cases and exposes a torchflower placement failure. The crop fixture now waits for its glowstone to light the plot before the native planting action. The shared helper also serves pitcher pods and wheat, which use the same native light rule. All existing planting, seed, fertilizing and output assertions remain. Both corrections live in the GameTest source set.

```sh
git diff 8c0fe1b6d -- enderio/src/gametest/java/com/enderio/enderio/gametests/content/FireCraftingTests.java \
  > "$port_evidence/fire-fixture.patch"
git diff 8c0fe1b6d -- enderio/src/gametest/java/com/enderio/enderio/gametests/machines/FarmingStationCropTests.java \
  > "$port_evidence/crop-light.patch"
for port_checkout in "$port_utc" "$port_local"; do
  git -C "$port_checkout" apply "$PWD/$port_evidence/fire-fixture.patch" "$PWD/$port_evidence/crop-light.patch"
done
TZ=America/Chicago LC_ALL=en_US.UTF-8 "$port_local/gradlew" -p "$port_local" "${port_flags[@]}" :enderio:verifyGameplayTests
TZ=UTC LC_ALL=C "$port_utc/gradlew" -p "$port_utc" "${port_flags[@]}" build --continue \
  :enderio:reobfClientSmokeJar :enderio:apiJar :enderio:sourcesJar :enderio-modded-conduits:sourcesJar
```

The packaged stock fire control uses the field and drop checks from `scripts/porting/packaged_starter_progression.py`. It uses the shipped random drop recipes with native vanilla fire on bedrock and deepslate in a Forge-only server. Its command uses the normal packaged harness with only the stock field and drop stage. See `build/port-runtime/cycle-279-stock-fire/stock-fire-results.json`.

## Distribution archives

Archive paths are relative to `build/port-evidence/cycle-279-delivery/source-local`.

| Archive | Directory | SHA-256 |
| --- | --- | --- |
| `endercore-8.2.14.278-dev+8c0fe1b6d4.jar` | `endercore/build/libs` | `87b652b878a191763de26b2ffd7eed1ecc020fdfe9970dbc99277ffc4a16db41` |
| `enderio-8.2.14.278-dev+8c0fe1b6d4-api.jar` | `enderio/build/libs` | `f3fc2ea5f2cc12566cf3e394bbfb68b13fd4d1ab30489527e740b60c90eb32d9` |
| `enderio-8.2.14.278-dev+8c0fe1b6d4-sources.jar` | `enderio/build/libs` | `0d12c53dc4096fc3ecb6241310398db8b33b3abfbd621ff5636932bfab5dfc64` |
| `enderio-8.2.14.278-dev+8c0fe1b6d4.jar` | `enderio/build/libs` | `b781ce3846dc6f502251ba383d7153f99143c9a2ba99f1975219a5bbe6185cf3` |
| `enderio-modded-conduits-8.2.14.278-dev+8c0fe1b6d4-sources.jar` | `enderio-modded-conduits/build/libs` | `af83dcc0df73c28e82f41169efdae6f2f981dc25d14caec214124a236ce3e887` |
| `enderio-modded-conduits-8.2.14.278-dev+8c0fe1b6d4.jar` | `enderio-modded-conduits/build/libs` | `6024936520c455889d40a148e179bc417cb6e4038e649737461d62a507c7857c` |

## Results

- Two independent Java 17 assemblies produce six byte-identical distribution archives and matching QA jars. The QA SHA-256 is `1579deaa3443a8712655b907f5dd130f66079d647b86f929732509a90b98b8a9`. The runtime contains Java 17 classes, the exact standalone EnderCore and Modded Conduits archives, and GraphLib3 3.0.5. The distribution audit excludes fixture resources, test classes and window instrumentation. A recursive comparison with cycle 278 checks 8,629 runtime entries and finds differences in the Git-derived version metadata alone. See `jar-comparison.json`, `distribution.json` and `production-payload-comparison.json`.
- The final Java 17 `build --continue` on the tested source with both fixture corrections succeeds. The first clean run's failure and the local torchflower repro are retained. The final XML records 79 passing JUnit cases and 548 passing native runs with zero failures, errors or skips. All 180 baseline method IDs appear, including 58 original native gameplay methods. Both environments pass all 220 discovered main gameplay cases with the corrected fixtures. The UTC run additionally passes 171 EnderCore, seven Mekanism, 117 main registry and 33 modded-conduit cases. See `source-utc-build-final.log`, `gameplay-fixtures-final.log`, `gameplay-before-fire-fixture.xml`, `gameplay-before-crop-light.xml` and `xml-audit.json`.
- Both datagen tasks reproduce all 6,028 tracked generated resources. Packaged inventories account for 1,222 core and 1,248 combined baseline recipes, 10 and 17 conduit variants, nine typed catalogs, 173 tags and 3,027 available tag memberships. The tested catalogs retain all 26 stock data-map values and 91 soul rules. All 683 baseline media assets have matching bytes. The resource reload client checks all 825 baseline blocks, 938 baseline core items and 3,416 baked states, including the 12 conduit arrow depth assertions. See `{datagen.log,typed-registries.json,custom-data.json,*-tag-audit.json,media.json}` and `baked-models/summary.json`.
- Four guarded clients pass 76 checks on the exact package. The single-player tank saves and reopens, and the dedicated three-resource route keeps tagged transfers through facade changes and a restart. The combined profile draws the authored shader and updates the JEI display after the server reloads its data. Both peers load matching bytes for all 15 shared optional jars. Across 7,246 native observations the four clients record zero activation, foreground or onscreen observations. All 14 captures report narrator off and all ten volume categories at zero. See `runtime-validation.json` and the client reports it identifies.
- The dedicated profiles pass 151 assertions across 12 server sessions. They cover the registry inventories, the live routes, six Almost Unified furnace replacements, both original stock fire substrates, glass illumination and a copied Forge 6.2.18-beta storage upgrade. All owned server sessions exit at zero with zero runtime or registry failures in the scenario reports. See the reports identified in `runtime-validation.json` and `build/port-runtime/cycle-279-stock-fire/stock-fire-results.json`.
- The tested renderer pair emits the recorded Embeddium mixin diagnostic, and headless datagen emits Embeddium's fingerprint diagnostic. The authored shader renders and the generated resources match the checkout. The installation guide records the tested Forge 6.2 scope and the original datapacks needed for custom jobs. [Installation and upgrade](install.md), [Completion audit](completion-audit.md)

The final verification commit contains these records and the two GameTest fixture corrections. The runtime archive is built from the source revision at the top of this report.
