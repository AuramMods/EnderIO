# Cycle 262 — Capacitor-bank corners across render sections

The packaged TCP fixture places four advanced banks in a square that crosses both a horizontal chunk boundary and the vertical section boundary between Y=79 and Y=80. A vertical pair of vibrant banks and a neighboring basic bank join the same energy graph. Different tiers keep their texture borders at their shared faces. The exposed fronts use the pull, push/pull, push and disabled cutout markers.

The runner replaces the upper diagonal advanced bank with a vibrant bank, then restores it. The two axial neighbors remain in place. The client checks the actual solid quads of four exposed south faces and the separate cutout markers. It also compares the rendered pixels in the advanced bank's affected quadrant before and after the swap, after restoration, and after an in-place resource reload. The plain Forge profile provides the ordinary texture control. The two Athena profiles exercise both the stock Forge chunk renderer and Embeddium.

The server records the exact seven node positions and capacities before, during and after the tier change. The network capacity changes with the replaced tier, and all banks remain in one graph. A second dedicated launch rechecks the restored layout and I/O settings.

## Commands

```sh
export JAVA_HOME="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
export CI=true JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=4
./gradlew --no-daemon --no-build-cache --no-configuration-cache --max-workers=2 --console=plain \
  :enderio:assemble :enderio:reobfClientSmokeJar

port_runtime=build/port-runtime
port_seed="$port_runtime/cycle-258-enderface-core-final-client"
port_forge="$port_runtime/cycle-047-legacy-discover"
port_jar=enderio/build/libs/enderio-8.2.14.261-1.20.1+acfa8461c7.jar
port_qa=enderio/build/test-libraries/enderio-client-qa.jar
port_mods="$port_runtime/cycle-176-verified-client/mods"
export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Djava.net.preferIPv4Stack=true'

prepare_bank_client() {
  port_mode="$1"
  shift
  port_profile="cycle-262-bank-$port_mode-client"
  port_client="$port_runtime/$port_profile"
  mkdir -p "$port_client/config"
  cp -cR "$port_seed/libraries" "$port_seed/versions" "$port_client/"
  cp "$port_seed/config/fml.toml" "$port_client/config/"
  python3 -B scripts/porting/client/prepare_packaged.py --name "$port_profile" --java "$JAVA_HOME/bin/java" \
    --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" --qa-jar "$port_qa" \
    --assets "$HOME/.gradle/caches/neoformruntime/assets" "$@"
  if [ "$port_mode" != core ]; then
    printf '%s\n' 'resourcePacks:["mod/enderio:resourcepacks/athena"]' >> "$port_client/options.txt"
  fi
}

prepare_bank_client core
prepare_bank_client athena --mod "$port_mods/athena-ctm-DULOQFj7.jar"
prepare_bank_client embeddium --mod "$port_mods/athena-ctm-DULOQFj7.jar" --mod "$port_mods/embeddium-UTbfe5d1.jar"

run_bank_textures() {
  python3 -B scripts/porting/packaged_capacitor_bank_textures.py --name "cycle-262-bank-$1" \
    --renderer "$1" --port "$2" --launch "$port_runtime/cycle-262-bank-$1-client/launch.json" \
    --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" --jar "$port_jar"
}
run_bank_textures athena 26396
run_bank_textures core 26397
run_bank_textures embeddium 26398

python3 -B scripts/porting/packaged_capacitor_banks.py --name cycle-262-bank-graph --port 26395 \
  --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" --jar "$port_jar"
```

## Results

- The Java 17 distributable and remapped QA jar build successfully. The existing headless bank regression passes all 13 assertions on topology, charge conservation, restart and wired export. The runtime SHA-256 is `6539462deb9520010c8a48ea6ed386eea4fa053b65a76aa4fba4a87f81c3589d`. The separate QA jar is `3a46989e2c5eeb50f496cfc3925e47ab7133f312eaf994d28b02822a34e013d2`. See `build/port-evidence/cycle-262-bank-textures/{build.log,graph.log}`.
- Plain Forge, Athena 3.1.2, and Athena with Embeddium 0.3.31+mc1.20.1 each pass 59 client checks and 10 dedicated-server assertions. The two Athena profiles change exactly 115 pixels in the sampled quadrant during the tier swap. Both restore every pixel after the matching bank returns and both retain that image after resource reload. The plain Forge sample stays pixel-identical throughout. All four configured overlay sprites remain present and visible. See `build/port-evidence/cycle-262-bank-{core,athena,embeddium}-client/`.
- The matching, mixed and restored graphs contain the same seven positions with capacities of 16,500,000, 18,500,000 and 16,500,000 FE. The restored graph and exposed I/O settings survive restart. All eight dedicated processes exit zero without runtime or registry errors. All three guarded clients exit zero without runtime errors. The native observer records 5,463 samples with zero activation, foreground or onscreen events. All twelve captures verify an uncaptured cursor, narrator off and ten muted sound categories. See `build/port-runtime/cycle-262-bank-{core,athena,embeddium}/bank-textures-results.json`.

## Next verification

Exercise the remaining Enchanter presentation case in a packaged Forge client. [Completion audit](completion-audit.md)
