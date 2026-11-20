# Cycle 271 — Item conduit arrow spacing

The arrow planes now sit one 256th of a block apart along the connector's outward normal. Input and output each use the first plane. Push/Pull puts its second arrow on the next plane, one 128th of a block from the connector. The resulting lift is at most one eighth of a model pixel. The previous gaps from the connector were 0.00125 and 0.0025 blocks.

The packaged JSON comparison checks every plane and confirms that its outward coordinate is the only change. The baked-model probe measures input, output and Push/Pull on all four source faces at launch and after resource reload. The conduit bundle rotates those same models to match the connection direction.

## Commands

```sh
export JAVA_HOME="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
export CI=true JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=4
./gradlew --no-daemon --no-build-cache --no-configuration-cache --max-workers=2 --console=plain \
  :enderio:assemble :enderio:reobfClientSmokeJar

port_runtime=build/port-runtime
port_evidence=build/port-evidence/cycle-271-arrow-offset
port_seed="$port_runtime/cycle-265-water-retry-client"
port_fixed=enderio/build/libs/enderio-8.2.14.270-1.20.1+e965390b9c.jar
port_before=build/port-evidence/cycle-271-distribution/source-local/enderio/build/libs/enderio-8.2.14.270-dev+e965390b9c.jar
port_qa=enderio/build/test-libraries/enderio-client-qa.jar
port_renderer="$port_runtime/cycle-090-render-embeddium-client/mods/embeddium-UTbfe5d1.jar"
for port_kind in core embeddium before; do
  port_name="cycle-271-arrow-$port_kind-client"
  port_target="$port_runtime/$port_name"
  mkdir -p "$port_target/config"
  cp -cR "$port_seed/libraries" "$port_seed/versions" "$port_target/"
  cp "$port_seed/config/fml.toml" "$port_target/config/"
  port_jar="$port_fixed"
  port_addons=()
  if [ "$port_kind" = before ]; then port_jar="$port_before"; fi
  if [ "$port_kind" = embeddium ]; then port_addons=(--mod "$port_renderer"); fi
  python3 -B scripts/porting/client/prepare_packaged.py --name "$port_name" --java "$JAVA_HOME/bin/java" \
    --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" --qa-jar "$port_qa" \
    --assets "$HOME/.gradle/caches/neoformruntime/assets" "${port_addons[@]}"
  python3 -B scripts/porting/client/run_smoke.py --launch "$port_target/launch.json" \
    --scenario model-inventory --output "$port_evidence/$port_kind" --timeout 180
done

JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Djava.net.preferIPv4Stack=true' \
  python3 -Bu scripts/porting/packaged_item_progression.py --name cycle-271-arrow-item-route --port 26428 \
    --java "$JAVA_HOME/bin/java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_fixed" \
    --launch "$port_runtime/cycle-271-arrow-embeddium-client/launch.json"
```

## Results

- Java 17 assembly and test-jar reobfuscation pass. The tested runtime jar SHA-256 is `6c7e7a3fd2f54497756138f12c46fb4196858da58948a7ba2f1104b3f66410ab`. See `build/port-evidence/cycle-271-arrow-offset/{build.log,model-comparison.json}`.
- The previous models fail exactly the 12 updated depth checks. The new models pass all 31 checks on stock Forge and on Forge with Embeddium `0.3.31+mc1.20.1`. Both runs record zero activations, foreground samples or onscreen samples across 1,665 observations. Narration is off and all ten volume categories are at zero. See `build/port-evidence/cycle-271-arrow-offset/{before,core,embeddium}/summary.json`.
- The dedicated item conduit route passes 35 client checks and 17 server assertions over two sessions. The final frame shows both endpoint arrows in-world with Embeddium. The client and server load the same production jar. The muted client records zero activations, foreground samples or onscreen samples across 1,775 observations. See `build/port-evidence/cycle-271-arrow-item-route-client/{upgraded-conduit-route.png,summary.json}` and `build/port-runtime/cycle-271-arrow-item-route/item-progression-results.json`.
- The updated runtime jar contains byte-identical copies of all 634 baseline PNG textures, five OGG files and 44 animation metadata files. See `build/port-evidence/cycle-271-arrow-offset/media.json`.

## Distribution checks on the preceding revision

Two clean detached builds of `e965390b9c6c26203de1054a4a7cf4025616021a` used Java 17 and `CI=true`. With the same Gradle flags as above, the UTC and C locale worktree ran `build --continue :enderio:reobfClientSmokeJar :enderio:apiJar :enderio:sourcesJar :enderio-modded-conduits:sourcesJar`. The America/Chicago and en_US.UTF-8 worktree ran `:endercore:assemble :enderio:assemble :enderio:apiJar :enderio:sourcesJar :enderio-modded-conduits:assemble :enderio-modded-conduits:sourcesJar :enderio:reobfClientSmokeJar` followed by `:enderio:runData :enderio-modded-conduits:runData`.

- `python3 -B scripts/porting/compare_jars.py build/port-evidence/cycle-271-distribution/source-utc build/port-evidence/cycle-271-distribution/source-local` finds six identical distribution archives. The two separately reobfuscated QA jars also match.
- Both datagen tasks pass and leave all 6,028 tracked generated files unchanged. The known Embeddium fingerprint diagnostic still appears during headless datagen.
- The first full build reports one failure in `registeredPlatesFilterActualEntitiesAndRelease`. Rerunning `:enderio:verifyGameplayTests` with the same UTC and C locale settings passes all 220 discovered gameplay tests. The reconciled XML reports have 79 passing unit cases and 548 passing native runs, with all 180 baseline methods present. See `build/port-evidence/cycle-271-distribution/{source-utc-build.log,gameplay-retry.log,xml-audit.json,datagen.log,jar-comparison.json}`.

## Next verification

Investigate the intermittent pressure-plate test and repeat the final distribution and dedicated regression checks on a committed candidate that includes the larger arrow spacing. [Completion audit](completion-audit.md)
