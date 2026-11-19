# Cycle 172 — Redraw diagonal quartz corners when facade visibility changes

Two perpendicular fused-quartz panels share a single blue facade. The facade is two render-section offsets away from each ordinary corner, across X and Y for the south face and Z and Y for the east face. Both of the edge neighbors are ordinary quartz. That leaves the shared facade as the only block whose appearance changes when the wrench hides it.

Before the fix, the packaged Oculus client selects the correct disconnected quadrant sprite for each ordinary quartz corner while the wrench is held. Both drawn corners keep their old connected pixels. Repainting the same facade redraws them correctly. The wrench refresh used the facade section and the six face neighbors, which skipped diagonally adjacent sections. The refresh now uses Minecraft's block-radius section iterator and deduplicates every affected section.

The client compares the same interior corner crop and the actual baked sprites from the south and east. The dedicated server supplies the normal view, wrench view, restored visibility, red repaint, blue restore and resource reload in sequence. A second TCP session checks the saved layout and both views after a server restart.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_before=enderio/build/libs/enderio-8.2.14.170-1.20.1+dffcaf164d.jar
port_jar=enderio/build/libs/enderio-8.2.14.171-1.20.1+f5f162dbbe.jar
port_runtime=build/port-runtime
port_forge="$port_runtime/cycle-047-legacy-discover"
port_qa=enderio/build/test-libraries/enderio-client-qa.jar
port_assets="$HOME/.gradle/caches/neoformruntime/assets"
port_source="$port_runtime/cycle-171-verified-active-client"
port_athena="$port_runtime/cycle-090-render-embeddium-client/mods/athena-ctm-DULOQFj7.jar"
port_embeddium="$port_runtime/cycle-091-active-client/mods/embeddium-UTbfe5d1.jar"
port_oculus="$port_runtime/cycle-091-active-client/mods/oculus-iQ1SwGc3.jar"

prepare_corner_profile() {
  port_profile="$1"
  port_mode="$2"
  port_package="$3"
  port_dir="$port_runtime/$port_profile"
  mkdir -p "$port_dir/config"
  cp -cR "$port_source/libraries" "$port_source/versions" "$port_dir/"
  cp "$port_source/config/fml.toml" "$port_dir/config/"
  set --
  if [ "$port_mode" != core ]; then set -- --mod "$port_athena"; fi
  if [ "$port_mode" = shader ]; then set -- "$@" --mod "$port_embeddium" --mod "$port_oculus"; fi
  python3 scripts/porting/client/prepare_packaged.py --name "$port_profile" --java "$port_java" \
    --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_package" --qa-jar "$port_qa" --assets "$port_assets" "$@"
  if [ "$port_mode" != core ]; then
    printf '%s\n' 'resourcePacks:["mod/enderio:resourcepacks/athena"]' >> "$port_dir/options.txt"
  fi
  if [ "$port_mode" = shader ]; then
    mkdir -p "$port_dir/shaderpacks"
    cp -R scripts/porting/client/shaderpacks/EnderIO-Facade-Test "$port_dir/shaderpacks/"
    printf '%s\n' 'enableShaders=true' 'shaderPack=EnderIO-Facade-Test' > "$port_dir/config/oculus.properties"
  fi
}

./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-172-client-repro.log 2>&1
prepare_corner_profile cycle-172-before-client shader "$port_before"
python3 scripts/porting/packaged_facade_corners.py --renderer shader --name cycle-172-shader-repro \
  --port 26116 --java "$port_java" --forge-server "$port_forge" --jar "$port_before" \
  --launch "$port_runtime/cycle-172-before-client/launch.json"

./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-172-fixed-build.log 2>&1
while read -r port_mode port_number; do
  port_run="cycle-172-fixed-$port_mode"
  prepare_corner_profile "$port_run-client" "$port_mode" "$port_jar"
  python3 scripts/porting/packaged_facade_corners.py --renderer "$port_mode" --name "$port_run" \
    --port "$port_number" --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" \
    --launch "$port_runtime/$port_run-client/launch.json"
done <<'PROFILES'
shader 26117
athena 26118
core 26119
PROFILES
```

## Results

- The focused before run fails only the south and east wrench pixel checks. The texture query swaps each inside quadrant from `empty` to `center` as intended, but each crop changes zero pixels. Repainting the shared facade changes 20 pixels in each crop and restoring the paint returns them exactly. See `build/port-evidence/cycle-172-shader-repro-exercise-client/`.
- The fixed shader client changes 20 pixels in each wrench crop and restores both crops exactly after the wrench is put away. The red repaint changes 20 pixels in each crop. The blue repaint and resource reload restore the same matching crops. Standard Forge with Athena keeps both connections while the wrench ghosts the facade and redraws the corners on repaint. Core Forge without Athena uses the ordinary quartz model throughout.
- The three packaged profiles pass 167 client checks and 18 server assertions. All six matching crops are pixel-identical after dedicated restart. The first shader restart attempt disconnects during login before any world checks. Restarting the same saved server directory and reconnecting with the same client and jar passes the saved layout assertion and all 12 shader client checks. See `build/port-runtime/cycle-172-fixed-{core,athena,shader}/facade-corners-results.json`, `build/port-runtime/cycle-172-fixed-shader/facade-corners-reconnect.json` and `build/port-evidence/cycle-172-restart-crops.json`.
- The six passing client sessions record 12,934 native focus samples with zero activation, foreground or onscreen events. All tested profiles keep sound categories at zero and the narrator off.
- The full build passes 79 unit cases and 530 native runs, including all 203 discovered main gameplay cases. All packaged peers load the same jar, with SHA-256 `b47fb68263958ba387d77c7cc505202136522e5b6d97147abcdc65393bf188be`. See `build/cycle-172-fixed-build.log`.

## Next verification

Exercise the remaining glass lighting and collision variants and the authored translucent and shadow shader paths.
