# Cycle 091 — Exercise conduit facades with an active Oculus shader pack

## Scope

Run the packaged stained-glass facade scenario on Embeddium and Oculus with a real shader pass. Verify that normal facades render, the wrench exposes the conduits while the shader path suppresses the ghost glass, and putting the wrench away restores the facades. Run the same fixture with shaders disabled as a control.

## Changes

- Add a two-stage shader pack under the client test fixtures. Its fullscreen final pass preserves the scene and draws a magenta square outside the facade samples. Each capture checks both the Iris API and the shader's output pixel so a fallback cannot satisfy the test. [Iris final-pass reference](https://shaders.properties/current/reference/programs/final/)
- Verify the screen colors and each bundled conduit in the active and disabled shader modes. Keep the four native Painting Machine, placement, reload, persistence and loot cases from cycle 087.
- Classify Embeddium's exact Oculus support notice once the live client has confirmed the shader pass, or the disabled control logs that it disabled shaders.

## Commands

```sh
./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-091-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.90-1.20.1+53fe64d8eb.jar
port_forge=build/port-runtime/cycle-047-legacy-discover
source_dir=build/port-runtime/cycle-087-oculus-client

for mode in active disabled; do
  profile="cycle-091-$mode-client"
  profile_dir="build/port-runtime/$profile"
  mkdir -p "$profile_dir/config"
  cp -R "$source_dir/libraries" "$source_dir/versions" "$profile_dir/"
  python3 scripts/porting/client/prepare_packaged.py --name "$profile" --java "$port_java" \
    --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
    --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets" \
    --mod "$source_dir/mods/embeddium-UTbfe5d1.jar" --mod "$source_dir/mods/oculus-iQ1SwGc3.jar"
done

active_dir=build/port-runtime/cycle-091-active-client
mkdir "$active_dir/shaderpacks"
cp -R scripts/porting/client/shaderpacks/EnderIO-Facade-Test "$active_dir/shaderpacks/"
printf '%s\n' 'enableShaders=true' 'shaderPack=EnderIO-Facade-Test' > "$active_dir/config/oculus.properties"
printf '%s\n' 'enableShaders=false' > build/port-runtime/cycle-091-disabled-client/config/oculus.properties

python3 scripts/porting/packaged_facades.py --name cycle-091-active --port 25839 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" \
  --launch build/port-runtime/cycle-091-active-client/launch.json --oculus --active-shaders
python3 scripts/porting/packaged_facades.py --name cycle-091-disabled --port 25840 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" \
  --launch build/port-runtime/cycle-091-disabled-client/launch.json --oculus
```

## Results

- The build passed 79 unit and 482 native tests. Native profiles ran 171 core, seven Mekanism, 117 registry, 160 gameplay and 27 addon tests. The packaged Ender IO jar SHA-256 is `d1a179b8b537ecd6ca7eff68be4b16ac76de257a7384a478a5ac05c4400aadd8`. See `build/cycle-091-build.log`.
- Oculus 1.8.0 with Embeddium 0.3.31 passed 19 server assertions and 42 client checks with the test pack active. All four captures contain the magenta marker and report an active pack. The four color samples count 3,949, 3,929, 3,948 and 3,960 matching pixels in normal view and 74, 0, 58 and 60 with the wrench selected and the glass suppressed. The wrench capture shows all four exposed conduits. Putting away the wrench and reloading resources each reproduce the original color counts. See `build/port-runtime/cycle-091-active/facade-results.json` and `build/port-evidence/cycle-091-active-client/`.
- On the Apple M4 Max OpenGL 4.1 renderer, shader fallback programs logged missing optional vertex-attribute warnings. Embeddium's Oculus support notice is recorded separately. The active run passed with an empty unexpected error list, all ten volumes at zero, narrator off and zero activation, foreground or onscreen events in 2,009 samples.
- The shaders-disabled control passed 19 server assertions and 38 client checks on the same jar. The four ghost glass regions retained 3,521, 3,792, 3,506 and 3,456 matching pixels under the same wrench-view color test. The client logs confirm shaders were disabled. The control was muted with narrator off and recorded zero activation, foreground or onscreen events in 1,999 samples. All four dedicated-server processes across the two profiles exited at 0 with empty runtime and registry error lists. See `build/port-runtime/cycle-091-disabled/facade-results.json` and `build/port-evidence/cycle-091-disabled-client/`.

## Remaining verification

The active run covers the test pack's final pass and Oculus's fallback world render passes. Shader packs with custom terrain, translucent or shadow programs, combinations with Athena, and facade rendering across dimension transitions remain open.
