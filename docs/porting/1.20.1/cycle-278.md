# Cycle 278 — Verify the delivery candidate

The packaged distribution is built from `c61aec1168628f851018a7a15ecf3c77885ca4fe`. Both builds use the same source and Temurin 17.0.18. The runtime profiles install the reobfuscated jar from the America/Chicago checkout. The guard and the reobfuscated QA jar are separate test artifacts.

## Commands

```sh
port_evidence=build/port-evidence/cycle-278-candidate
mkdir -p "$port_evidence"
for port_checkout in source-utc source-local; do
  git worktree add --detach "$port_evidence/$port_checkout" c61aec1168628f851018a7a15ecf3c77885ca4fe
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

port_jar="$port_local/enderio/build/libs/enderio-8.2.14.277-dev+c61aec1168.jar"
python3 -B scripts/porting/compare_jars.py "$port_utc" "$port_local"
python3 -B build/port-evidence/cycle-272-release/inspect-distribution.py "$port_local" "$port_jar"
python3 -B build/port-evidence/cycle-255-completion/compare-media.py "$port_jar"
python3 -B build/port-evidence/cycle-255-completion/audit-tests.py "$port_utc"
```

The archived scenario launcher uses the exact optional mods in the [dependency table](dependencies.md) and the client-only rendering stack. The independently generated test profiles reuse Forge 47.4.28 libraries and Mojang assets. Each client launch uses the native focus observer and silent isolated options.

```sh
for port_scenario in core-audit combined-audit legacy singleplayer mixed-facade combined-sag; do
  zsh build/port-evidence/cycle-278-candidate/run-candidate.zsh "$port_scenario"
done
python3 -B scripts/porting/client/run_smoke.py \
  --launch build/port-runtime/cycle-278-singleplayer-client/launch.json \
  --scenario model-inventory --output "$port_evidence/baked-models" --timeout 180
python3 -B build/port-evidence/cycle-255-completion/typed-registries.py \
  build/port-runtime/cycle-278-core-audit/registry-snapshot.json \
  build/port-runtime/cycle-278-combined-audit/registry-snapshot.json
python3 -B "$port_evidence/audit-runtime.py"
```

## Distribution archives

Paths in the table are relative to `build/port-evidence/cycle-278-candidate/source-local`. All six archive filenames use version `8.2.14.277-dev+c61aec1168`.

| Archive | Directory | SHA-256 |
| --- | --- | --- |
| `enderio-8.2.14.277-dev+c61aec1168.jar` | `enderio/build/libs` | `99c5d8c4851cae03e89b75a044f34e0dfd67e9f9e56f8167259503a21581f64a` |
| `endercore-8.2.14.277-dev+c61aec1168.jar` | `endercore/build/libs` | `d2b68a924ad2f841fb8f6371481b22bc49dd0b2195447679286ed14bf53daee5` |
| `enderio-modded-conduits-8.2.14.277-dev+c61aec1168.jar` | `enderio-modded-conduits/build/libs` | `c7943b7822859d3169274c39a2284573f50de499c4f381a7da052747fb9afda2` |
| `enderio-8.2.14.277-dev+c61aec1168-api.jar` | `enderio/build/libs` | `c57d96f0855b8409de2a43d09e64463b5f52ab2b4b76984695273d5c9096b29a` |
| `enderio-8.2.14.277-dev+c61aec1168-sources.jar` | `enderio/build/libs` | `00eb05ad695b64ff821d6571bf4ce1cc80f28a14ca3720e353e7ef13bbecf537` |
| `enderio-modded-conduits-8.2.14.277-dev+c61aec1168-sources.jar` | `enderio-modded-conduits/build/libs` | `48d7b45febe3c3c486452e6685333de36e06e2e6556ef39dbaa4a4e919376219` |

## Glass fixture correction

The clean build executes 220 main gameplay cases. `GlassTests.darkAndEnlightenedGlassUpdateBlockLight` fails when one shaft still has block light 4 during the final plain-glass phase. Both shafts were open above the temporary glowstone. The test fixture now caps each shaft with stone. It retains the same variants, native block changes, exact brightness assertions and timeout.

```sh
cp "$port_utc/enderio/build/test-results/gameTestServer/results.xml" "$port_evidence/gameplay-before.xml"
git diff c61aec116 -- enderio/src/gametest/java/com/enderio/enderio/gametests/content/GlassTests.java \
  > "$port_evidence/glass-fixture.patch"
git -C "$port_utc" apply "$PWD/$port_evidence/glass-fixture.patch"
TZ=UTC LC_ALL=C "$port_utc/gradlew" -p "$port_utc" "${port_flags[@]}" :enderio:verifyGameplayTests
python3 -B build/port-evidence/cycle-255-completion/audit-tests.py "$port_utc"
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Djava.net.preferIPv4Stack=true' \
  python3 -Bu scripts/porting/packaged_glass_light.py --name cycle-278-glass --port 26450 \
  --java "$JAVA_HOME/bin/java" --forge-server build/port-runtime/cycle-047-legacy-discover --jar "$port_jar"
```

## Results

- Independent Java 17 assemblies match in all six distribution archives. The separate QA jars also match, with SHA-256 `1136eaf2209e1cdd0fcb55e3f219bd57926a4f75e93f24238942e6ae212ea960`. The three Ender IO binaries use class version 61. The nested EnderCore and Modded Conduits match the standalone archives and GraphLib3 3.0.5 is present. Across 8,629 runtime entries including nested payloads, all differences from the cycle 272 package are version metadata. See `jar-comparison.json`, `distribution.json` and `production-payload-comparison.json` under the evidence directory.
- The clean UTC build exits with the single glass test failure. Applying the recorded fixture patch and rerunning discovery and all 220 cases passes. The combined XML from the successful suites and the corrected run records 79 unit cases and 548 native runs with zero failures, errors or skips. All 180 baseline method IDs appear in passing reports. See `source-utc-build.log`, `gameplay-before.xml`, `gameplay-glass-rerun.log` and `xml-audit.json`.
- Both datagen tasks pass and leave all 6,028 tracked generated files unchanged. All 683 baseline media files match the original bytes. The two live registries account for 1,222 core and 1,248 combined baseline recipes, 10 and 17 conduit variants, all 813 baseline loot tables and all nine typed catalogs. Both snapshots reconcile the 173 baseline tags and match the 26 data-map values and 91 soul rules. The combined result also verifies all six Almost Unified furnace replacements. See `{datagen.log,media.json,typed-registries.json,*-tag-audit.json,custom-data.json}` and `build/port-runtime/cycle-278-{core,combined}-audit/`.
- The packaged single-player tank passes 15 checks including its disk save/reopen. The model client passes 31 checks and includes all 825 baseline block models, 938 baseline core items and 3,416 baked block states. All 12 arrow depth checks pass before and after resource reload. The dedicated mixed route passes 13 client and 14 server checks with items, tagged water and FE moving through live facade changes and after restart. The combined shader and JEI profile passes 17 client and seven server checks with the authored shader active and the server-selected output updating on datapack reload. All 15 shared optional jars match between the client and server.
- The copied Forge 6.2.18-beta storage graphs pass 101 assertions through original-world fixture creation, conversion and restart. The packaged glass run passes ten assertions and verifies block and sky light after chunk eviction and a dedicated restart. All 11 owned headless server sessions exit at zero with no runtime or registry failures. Four guarded clients pass 76 checks, with no activation, foreground or onscreen observations over 8,114 samples. All 14 captures report narrator off and all ten volume categories at zero. See `runtime-validation.json` and the reports it identifies.
- The tested Oculus/Embeddium pair logs its known mixin notice, and headless datagen logs Embeddium's fingerprint diagnostic. The tested shader frames render, and datagen generates the expected resources.

## Next verification

Run a clean build of the committed revision with the corrected glass fixture. Record that revision's distribution checksums and confirm the exact jar in fresh Forge runtime profiles. [Completion audit](completion-audit.md)
