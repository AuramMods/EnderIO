# Cycle 171 — Keep Athena glass connected to the drawn facade geometry

Two matching clear-glass facades span a chunk boundary and meet an ordinary glass block. A packaged TCP client captures the matching, repainted, restored, wrench and reloaded views. The test compares the baked face sprites and the actual pixels at both joins. It runs on core Forge and with Athena 3.1.2, Embeddium 0.3.31 and Oculus 1.8.0 together. The Oculus profiles cover shaders disabled, an active final pass with fallback terrain, and authored solid and cutout terrain programs.

The test pack draws a magenta marker in its final pass. The authored terrain programs additionally mark a patch of stone in cyan and part of the west facade in yellow. The cutout marker disappears when the wrench hides the facade, while the solid terrain marker persists. These markers test which programs drew the frame. [Iris terrain programs](https://github.com/IrisShaders/ShaderDoc/blob/master/iris-features.md#terrain-cutout-iris-16)

The original shader capture shows a missing border where ordinary glass meets a hidden facade. With the wrench held, the regression measures zero blue pixels at that border and finds connected sprites on the ordinary glass. The Forge appearance query now uses the same shader suppression decision as the facade renderer. Ordinary glass receives the underlying bundle appearance when the active shader hides the ghost. With shaders disabled, the renderer still draws the ghost and the ordinary glass keeps its matching neighbor appearance.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_before=enderio/build/libs/enderio-8.2.14.169-1.20.1+3098df827a.jar
port_jar=enderio/build/libs/enderio-8.2.14.170-1.20.1+dffcaf164d.jar
port_runtime=build/port-runtime
port_qa=enderio/build/test-libraries/enderio-client-qa.jar
port_forge="$port_runtime/cycle-047-legacy-discover"
port_assets="$HOME/.gradle/caches/neoformruntime/assets"
port_athena="$port_runtime/cycle-090-render-embeddium-client/mods/athena-ctm-DULOQFj7.jar"
port_embeddium="$port_runtime/cycle-091-active-client/mods/embeddium-UTbfe5d1.jar"
port_oculus="$port_runtime/cycle-091-active-client/mods/oculus-iQ1SwGc3.jar"
port_shaders=scripts/porting/client/shaderpacks

./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-171-client-edge.log 2>&1

prepare_facade_profile() {
  port_profile="$1"
  port_mode="$2"
  port_package="$3"
  port_dir="$port_runtime/$port_profile"
  mkdir -p "$port_dir/config"
  cp -cR "$port_runtime/cycle-091-active-client/libraries" "$port_runtime/cycle-091-active-client/versions" "$port_dir/"
  cp "$port_runtime/cycle-170-final-normal/config/fml.toml" "$port_dir/config/"
  set --
  if [ "$port_mode" != core ]; then
    set -- --mod "$port_athena" --mod "$port_embeddium" --mod "$port_oculus"
  fi
  python3 scripts/porting/client/prepare_packaged.py --name "$port_profile" --java "$port_java" \
    --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_package" --qa-jar "$port_qa" --assets "$port_assets" "$@"
  if [ "$port_mode" != core ]; then
    printf '%s\n' 'resourcePacks:["mod/enderio:resourcepacks/athena"]' >> "$port_dir/options.txt"
  fi
  case "$port_mode" in
    oculus) printf '%s\n' 'enableShaders=false' > "$port_dir/config/oculus.properties" ;;
    oculus-shader|oculus-terrain)
      mkdir -p "$port_dir/shaderpacks"
      cp -R "$port_shaders/EnderIO-Facade-Test" "$port_dir/shaderpacks/"
      printf '%s\n' 'enableShaders=true' 'shaderPack=EnderIO-Facade-Test' > "$port_dir/config/oculus.properties"
      if [ "$port_mode" = oculus-terrain ]; then
        cp -R "$port_shaders/terrain-programs/shaders/." "$port_dir/shaderpacks/EnderIO-Facade-Test/shaders/"
      fi ;;
  esac
}

prepare_facade_profile cycle-171-edge-before-client oculus-terrain "$port_before"
python3 scripts/porting/packaged_facade_ctm.py --renderer oculus-terrain --name cycle-171-edge-before --port 26107 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_before" \
  --launch "$port_runtime/cycle-171-edge-before-client/launch.json"

./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-171-fixed-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-171-client-verified.log 2>&1
while read -r port_name port_mode port_number; do
  prepare_facade_profile "${port_name}-client" "$port_mode" "$port_jar"
  python3 scripts/porting/packaged_facade_ctm.py --renderer "$port_mode" --name "$port_name" --port "$port_number" \
    --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" \
    --launch "$port_runtime/${port_name}-client/launch.json"
done <<'PROFILES'
cycle-171-fixed-disabled oculus 26110
cycle-171-fixed-core core 26111
cycle-171-verified-program oculus-terrain 26112
cycle-171-verified-active oculus-shader 26113
PROFILES
```

## Results

- Before the fix, the expected blue border is absent and the ordinary glass uses two horizontal and two isolated quads while the facade is hidden. The failing assertion and screenshot are in `build/port-evidence/cycle-171-edge-before-client/`.
- The first fixed captures show the restored ordinary border extending into the center of the original ghost sample. The verified test measures that edge in its own crop and samples the interior of the middle facade separately. The first fixed captures are under `build/port-evidence/cycle-171-fixed-{program,active}-client/`.
- With the authored terrain programs, the native client passes 44 checks. The ordinary glass has 318 blue border pixels and four isolated quads when the wrench hides the neighboring facades. Both energy conduit models appear. The yellow cutout sample contains 111 pixels in every visible phase and zero with the wrench selected. The solid terrain and final-pass markers appear in all six captures.
- The fallback terrain profile passes 32 checks and restores 280 blue border pixels in wrench mode. The shaders-disabled profile passes 32 checks. Its translucent ghost remains visible, and the ordinary glass keeps its connected appearance. The core profile passes 18 checks with the plain glass model.
- In the authored terrain profile, repainting changes 600 and 603 pixels at the two joins. The fallback and disabled profiles change 581 and 584. All three return both crops pixel-for-pixel when paint is restored, after the wrench is put away and after resource reload. The core profile also restores both crops exactly.
- The four fixed profiles pass 23 server assertions across eight exits, with zero runtime or registry errors and exact restored paint after each restart. Embeddium's Oculus support notice is recorded separately from client errors. The focus observer records zero activation, foreground or on-screen events in 2,062 authored terrain, 2,062 fallback, 2,053 disabled and 1,830 core samples. All client captures have narrator off, zero game volumes and no fullscreen or cursor capture.
- Native validation passes 79 unit cases and 530 Forge runs, including all 203 discovered main gameplay cases. The fixed production jar has SHA-256 `b3d0585a47eee12e37825e2b1bfa26bf7739fb5205667355a052fe599dddb5c2`. The screenshots and client checks are under `build/port-evidence/cycle-171-{verified-program,verified-active,fixed-disabled,fixed-core}-client/`. Their server reports and logs use the same profile names without the `-client` suffix under `build/port-runtime/`.

## Next verification

Exercise perpendicular and diagonal glass connections, the fused-quartz family, and authored translucent and shadow programs in the packaged renderer.
