# Cycle 176 — Toggle shaders around a loaded conduit facade

The test reuses the packaged Oculus scene with a blue stained-glass facade, ordinary red stained glass and a stone backdrop. It applies shader settings through the pinned Oculus build's public Iris API. The packaged TCP client disables and reenables the active pack first while holding the Yeta Wrench and then again with an empty hand. Every capture verifies the same client level and facade block entity references. The server independently checks the original paint, energy conduit and selected wrench during the first round trip. [Iris shader configuration API](https://github.com/IrisShaders/Iris/blob/26.1/common/src/main/java/net/irisshaders/iris/apiimpl/IrisApiV0ConfigImpl.java).

With shaders active, the translucent terrain and shadow-map captures show both paints in the ordinary view and the independent red control when the wrench hides the facade. Disabling shaders with the wrench selected restores the native blue ghost. The shader signatures disappear, and the blue tint is fainter than the fully visible native glass recorded with the wrench put away. Reenabling shaders restores the exact hidden or visible state for the held item. The restarted server and a fresh client verify the saved facade and the active shader scene again.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_source="$port_runtime/cycle-175-verified-client"
port_shader_source=scripts/porting/client/shaderpacks
port_before=enderio/build/libs/enderio-8.2.14.174-1.20.1+68860385c8.jar
port_jar=enderio/build/libs/enderio-8.2.14.175-1.20.1+3b886ed0d4.jar

prepare_shader_profile() {
  port_profile="$1"
  port_artifact="$2"
  port_dir="$port_runtime/$port_profile"
  mkdir -p "$port_dir/config" "$port_dir/shaderpacks"
  cp -cR "$port_source/libraries" "$port_source/versions" "$port_dir/"
  cp "$port_source/config/fml.toml" "$port_dir/config/"
  python3 scripts/porting/client/prepare_packaged.py --name "$port_profile" --java "$port_java" \
    --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_artifact" \
    --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets" \
    --mod "$port_source/mods/athena-ctm-DULOQFj7.jar" --mod "$port_source/mods/embeddium-UTbfe5d1.jar" \
    --mod "$port_source/mods/oculus-iQ1SwGc3.jar"
  printf '%s\n' 'resourcePacks:["mod/enderio:resourcepacks/athena"]' >> "$port_dir/options.txt"
  cp -R "$port_shader_source/EnderIO-Facade-Test" "$port_dir/shaderpacks/"
  for port_overlay in terrain-programs translucent-shadow-programs; do
    cp -R "$port_shader_source/$port_overlay/shaders/." "$port_dir/shaderpacks/EnderIO-Facade-Test/shaders/"
  done
  printf '%s\n' 'enableShaders=true' 'shaderPack=EnderIO-Facade-Test' > "$port_dir/config/oculus.properties"
}

./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-176-client.log 2>&1
prepare_shader_profile cycle-176-initial-client "$port_before"
python3 -u scripts/porting/packaged_facade_shader_passes.py --live-toggle --name cycle-176-initial \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_before" \
  --launch "$port_runtime/cycle-176-initial-client/launch.json" --port 26125 > build/cycle-176-initial.log 2>&1

./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-176-verified-build.log 2>&1
prepare_shader_profile cycle-176-verified-client "$port_jar"
prepare_shader_profile cycle-176-regression-client "$port_jar"
python3 -u scripts/porting/packaged_facade_shader_passes.py --live-toggle --name cycle-176-verified \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar" \
  --launch "$port_runtime/cycle-176-verified-client/launch.json" --port 26126 > build/cycle-176-verified.log 2>&1
python3 -u scripts/porting/packaged_facade_shader_passes.py --name cycle-176-regression \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar" \
  --launch "$port_runtime/cycle-176-regression-client/launch.json" --port 26127 > build/cycle-176-regression.log 2>&1
```

## Results

- The original packaged build passes all 54 live checks, nine restart client checks and five server assertions. The raw disabled frames contain 612 blue-tinted pixels in both states, with total blue dominance of 5,657 in the ghost view and 16,776 in the ordinary view. Both enabled wrench frames have zero blue translucent and shadow markers. See `build/port-runtime/cycle-176-initial/shader-pass-results.json` and `build/port-evidence/cycle-176-initial-exercise-client/`.
- The rebuilt live-toggle profile passes 63 client checks and five server assertions. Both disabled captures produce the same two native tint measurements. The original repaint and reload profile passes 53 client checks and five server assertions on the same build.
- All 24 pixel comparisons pass across the authored translucent strip, shadow inset and blended backdrop. The active wrench state is pixel-identical after reenabling shaders. The visible state is pixel-identical after putting the wrench away, reenabling shaders with an empty hand and reconnecting to the restarted server. The original repaint and reload profile also matches after restart. See `build/port-evidence/cycle-176-pixel-comparisons.json`.
- All four packaged client sessions keep sound at zero and narrator off, with zero native activation, foreground or onscreen events in 7,319 samples. Both servers and their restarts exit normally with zero runtime or registry errors. The client and server copies match SHA-256 `1c43bede45df2c771fdc408d59259cfee04b264ca657d13671c54958026b2fbb`. Reports are under `build/port-runtime/cycle-176-{verified,regression}/` and `build/port-evidence/cycle-176-{verified,regression}-{exercise,verify}-client/`.
- The full build passes 79 unit tests and 534 native runs, including all 207 discovered main gameplay tests. See `build/cycle-176-verified-build.log`.

## Next verification

Run packaged Forge with the pinned optional integration stack installed together. Check multiplayer loading and the interactions between shared recipe, tooltip and renderer integrations.
