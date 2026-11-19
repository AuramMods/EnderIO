# Cycle 175 — Verify translucent facade terrain and shadows under Oculus

The dedicated Forge world places a blue-stained-glass energy-conduit facade and an ordinary red stained-glass block on opposite sides of a chunk boundary. A stone wall behind both blocks makes the real translucent blend visible. The test uses Oculus 1.8.0, Embeddium 0.3.31 and Athena 3.1.2 with the built-in Athena pack.

The authored `gbuffers_water` program marks translucent glass in a narrow strip and uses its texture, shade and alpha across the rest of the surface. The authored `shadow` program marks stained-glass fragments in `shadowcolor0`. The final pass displays that shadow texture in an inset and draws the existing magenta signature. The camera view and the shadow inset distinguish red, blue and lime from the actual sampled glass textures. [Iris terrain programs](https://shaders.properties/current/reference/programs/gbuffers/), [Iris shadow programs](https://shaders.properties/current/reference/programs/shadow/), [shadow color attachments](https://shaders.properties/current/reference/buffers/shadowcolor/).

The client records the initial blue facade, a lime repaint, a return to blue, the hidden wrench view, the restored view and the resource reload. Both paints must enter the translucent pass and cast a matching marked shadow while visible. The wrench must remove both markers for the facade and retain the independent red control in both passes. The comparison also measures stone texture edges in the exposed view and checks that the blue tint preserves those same edges before the wrench is selected. Putting the wrench away restores the measured pixels. A fresh client reconnects after a dedicated restart and exercises both passes again on the saved scene.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_source="$port_runtime/cycle-172-fixed-shader-client"
port_shader_source=scripts/porting/client/shaderpacks
port_name=cycle-175-verified
port_profile="$port_name-client"
port_dir="$port_runtime/$port_profile"
port_jar=enderio/build/libs/enderio-8.2.14.174-1.20.1+68860385c8.jar

./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-175-verified-build.log 2>&1
mkdir -p "$port_dir/config" "$port_dir/shaderpacks"
cp -cR "$port_source/libraries" "$port_source/versions" "$port_dir/"
cp "$port_source/config/fml.toml" "$port_dir/config/"
python3 scripts/porting/client/prepare_packaged.py --name "$port_profile" --java "$port_java" \
  --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets" \
  --mod "$port_source/mods/athena-ctm-DULOQFj7.jar" --mod "$port_source/mods/embeddium-UTbfe5d1.jar" \
  --mod "$port_source/mods/oculus-iQ1SwGc3.jar" > build/cycle-175-verified-prepare.log 2>&1
printf '%s\n' 'resourcePacks:["mod/enderio:resourcepacks/athena"]' >> "$port_dir/options.txt"
cp -R "$port_shader_source/EnderIO-Facade-Test" "$port_dir/shaderpacks/"
for port_overlay in terrain-programs translucent-shadow-programs; do
  cp -R "$port_shader_source/$port_overlay/shaders/." "$port_dir/shaderpacks/EnderIO-Facade-Test/shaders/"
done
printf '%s\n' 'enableShaders=true' 'shaderPack=EnderIO-Facade-Test' > "$port_dir/config/oculus.properties"
python3 -u scripts/porting/packaged_facade_shader_passes.py --name "$port_name" --java "$port_java" \
  --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar" \
  --launch "$port_dir/launch.json" --port 26124 > build/cycle-175-verified.log 2>&1
```

## Results

- The initial run on the prior packaged artifact passes 47 client checks and five server assertions. Its captures show that the ordinary red control and the painted facade reach both authored programs. The final regression additionally checks the visible backdrop texture and exact pixel restoration. See `build/port-runtime/cycle-175-initial/shader-pass-results.json` and `build/port-evidence/cycle-175-initial-{exercise,verify}-client/`.
- The rebuild passes 53 client checks and five server assertions. In the visible and repainted frames the facade contributes 1,149 translucent marker pixels and 64 shadow-map pixels. Holding the wrench reduces both to zero. Ordinary red glass contributes 1,271 translucent and 64 shadow-map pixels in every capture.
- All 73 sampled stone texture edges are visible through the blue tint. All twelve pixel comparisons pass across the backdrop, translucent strip and shadow inset after repaint restoration, wrench removal, resource reload and server restart. See `build/port-evidence/cycle-175-restart-pixels.json`.
- Both clients and servers exit cleanly with zero runtime or registry errors. The known Embeddium support notice is recorded separately. The native observer records zero activation, foreground and onscreen samples across 3,343 observations. All seven captures have narrator off, every volume category at zero, and fullscreen and cursor capture disabled.
- The full build passes 79 unit tests and 534 native runs, including all 207 discovered main gameplay tests. The server and both clients load matching production jars with SHA-256 `10de5fdb97a13be29218dad99190f93b60c3491dfdff4327815d9ba9f4055af7`. See `build/cycle-175-verified-build.log`, `build/port-runtime/cycle-175-verified/shader-pass-results.json` and `build/port-evidence/cycle-175-verified-{exercise,verify}-client/`.

## Next verification

Exercise shader activation and deactivation with a loaded facade, then continue the remaining feature and integration paths in the parity matrix.
