# Cycle 272 — Rebuild and test the arrow-spacing candidate

The two clean distribution checkouts use commit `a00893d995da9ad24a876e78f30f1a71812f8401`. The packaged runtime checks install the reobfuscated main jar from the America/Chicago build. A third detached checkout runs the full native gameplay suite with 40 additional registrations of the original pressure-plate scenario to investigate the single failure from [cycle 271](cycle-271.md). The extra registrations reuse its fixture and body and have unique test IDs.

## Commands

```sh
port_evidence=build/port-evidence/cycle-272-release
mkdir -p "$port_evidence"
for port_tree in source-utc source-local source-plates; do
  git worktree add --detach "$port_evidence/$port_tree" a00893d995
done
export JAVA_HOME="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
export CI=true JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=4
port_flags=(--no-daemon --no-build-cache --no-configuration-cache --max-workers=2 --console=plain)
port_utc="$port_evidence/source-utc"
port_local="$port_evidence/source-local"
port_plates="$port_evidence/source-plates"
TZ=UTC LC_ALL=C "$port_utc/gradlew" -p "$port_utc" "${port_flags[@]}" build --continue \
  :enderio:reobfClientSmokeJar :enderio:apiJar :enderio:sourcesJar :enderio-modded-conduits:sourcesJar
TZ=America/Chicago LC_ALL=en_US.UTF-8 "$port_local/gradlew" -p "$port_local" "${port_flags[@]}" \
  :endercore:assemble :enderio:assemble :enderio:apiJar :enderio:sourcesJar \
  :enderio-modded-conduits:assemble :enderio-modded-conduits:sourcesJar :enderio:reobfClientSmokeJar
TZ=America/Chicago LC_ALL=en_US.UTF-8 "$port_local/gradlew" -p "$port_local" "${port_flags[@]}" \
  :enderio:runData :enderio-modded-conduits:runData
git -C "$port_local" diff --exit-code -- enderio/src/generated/resources enderio-modded-conduits/src/generated/resources
git -C "$port_plates" apply ../plate-repeat-instrumentation.patch
TZ=UTC LC_ALL=C "$port_plates/gradlew" -p "$port_plates" "${port_flags[@]}" :enderio:verifyGameplayTests

port_jar="$port_local/enderio/build/libs/enderio-8.2.14.271-dev+a00893d995.jar"
python3 -B scripts/porting/compare_jars.py "$port_utc" "$port_local"
python3 -B "$port_evidence/inspect-distribution.py" "$port_local" "$port_jar"
python3 -B build/port-evidence/cycle-255-completion/audit-tests.py "$port_utc"
python3 -B build/port-evidence/cycle-255-completion/compare-media.py "$port_jar"
```

The archived launcher reads the same [dependency pins](dependencies.md) used in [cycle 177](cycle-177.md) and installs the exact candidate into isolated Forge profiles. Its client scenarios run sequentially through the native macOS focus observer.

```sh
zsh build/cycle-272-runtime.zsh core-audit
zsh build/cycle-272-runtime.zsh combined-audit
zsh build/cycle-272-runtime.zsh plates
zsh build/cycle-272-runtime.zsh singleplayer
zsh build/cycle-272-runtime.zsh redstone
zsh build/cycle-272-runtime.zsh combined-sag
python3 -B scripts/porting/client/run_smoke.py \
  --launch build/port-runtime/cycle-272-singleplayer-client/launch.json --scenario model-inventory \
  --output "$port_evidence/baked-models" --timeout 180
```

## Results

- The clean Java 17 build completes all 81 Gradle tasks. All six distribution archives and the separately reobfuscated QA jar match byte for byte across both locales and time zones. The Ender IO binaries use Java 17 class files. The nested EnderCore and Modded Conduits jars match their standalone builds, and the production jar includes GraphLib3 3.0.5. The runtime SHA-256 is `58803ec07dc520e3da7ec4dc413ba04dc78db24b524aa20435e00f8d2cbf98bd`. See `build/port-evidence/cycle-272-release/{source-utc-build.log,source-local-build.log,jar-comparison.json,distribution.json}`.
- Both datagen tasks pass and leave all 6,028 tracked generated files unchanged. The Embeddium fingerprint diagnostic still occurs when it queries the unavailable Minecraft client in the data run. All 634 upstream textures, 44 animation metadata files and five sounds match their baseline bytes in the production jar. See `build/port-evidence/cycle-272-release/{datagen.log,media.json}`.
- The full build executes 79 unit cases and 548 native cases with zero failures, errors or skips. All 180 baseline method IDs, including the 58 original GameTests, appear in passing reports. The native suites execute 171 EnderCore, seven Mekanism, 220 main gameplay, 117 main registry and 33 modded-conduit cases. See `build/port-evidence/cycle-272-release/xml-audit.json`.
- The diagnostic build passes all 260 discovered cases, including all 41 runs of the original pressure-plate scenario. The packaged decorative redstone run also passes 112 assertions across two dedicated sessions, verifying the plate signals, item and hostile-mob triggers and release after restart. The intermittent failure from cycle 271 did not recur. See `build/port-evidence/cycle-272-release/{plate-repeat.log,plate-repeat-results.xml}` and `build/port-runtime/cycle-272-plates/decorative-redstone-results.json`.
- The core and combined registry audits pass. The two profiles account for 1,222 and 1,248 applicable baseline recipes and for 10 and 17 conduit variants. The combined server loads 15 pinned optional jars, enables the experimental packs and proves all six Almost Unified furnace replacements with native furnace operations. Nine typed catalogs agree with their baseline IDs and the port's recorded Forge additions. See both `build/port-runtime/cycle-272-{core,combined}-audit/registry-audit-results.json` files and `build/port-evidence/cycle-272-release/typed-registries.json`.
- The packaged single-player tank passes all 15 checks, including reopening from disk. The dedicated redstone route passes 36 client checks and 34 server assertions over three sessions. The combined client with Athena, Embeddium and Oculus passes 17 checks, including drawing the authored shader and seeing the corrected server-selected SAG Mill output after reload. The combined server passes seven assertions, including retaining both correct tin dusts after restart. See `build/port-evidence/cycle-272-release/cycle-272-singleplayer/summary.json`, `build/port-runtime/cycle-272-redstone/redstone-progression-results.json` and `build/port-runtime/cycle-272-combined-sag/unified-sag-results.json`.
- The packaged client model audit passes 31 checks and accounts for all 825 baseline blocks, 938 baseline core items and 3,416 baked block states. All 12 arrow-spacing checks pass before and after resource reload. See `build/port-evidence/cycle-272-release/{baked-models/summary.json,model-reconciliation.json}`.
- The four client runs record zero activations, foreground observations or onscreen observations across 8,179 samples. Every capture reports narration off and all ten volume categories at zero. All profiles load the retained candidate, and the client and server use matching bytes for all 15 shared optional jars in the combined run.

## Next verification

Resume the remaining eight original Forge 6.2.18-beta stock Soul Binder jobs on copies of their source worlds. Check the original recipe payment, queued result and converted output, including the Powered Spawner's captured soul. [Completion audit](completion-audit.md)
