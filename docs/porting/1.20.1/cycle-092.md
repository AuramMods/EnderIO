# Cycle 092 — Verify painted conduit facades across dimension changes

## Scope

Use the same four conduit coordinates in the Overworld and Nether with opposite stained-glass colors. Travel to the Nether and back over one dedicated-server session while holding the Yeta Wrench on both crossings.

## Changes

- Extend the packaged facade scenario with a lit Nether fixture at the same coordinates as the Overworld scene. Compare the live facade models and sampled render colors against the expected paint at each arrival.
- Calibrate the Nether color samples against exposed stone on the same scanline to account for warm lighting. The Overworld and previous fixture modes retain their existing color checks.
- Verify that the departing dimension releases its tracked facade positions and the arriving dimension indexes all four facades. Check wrench-mode and normal rendering in the Nether and after returning to the Overworld.
- Reopen the dedicated server and check that both dimensions retain their own paint and conduit data. The original fixture still tests exact Painting Machine energy use, hopper output, survival placement and native loot.

## Commands

```sh
./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-092-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.91-1.20.1+8884fa72c3.jar
qa_jar=enderio/build/test-libraries/enderio-client-qa.jar
forge_server=build/port-runtime/cycle-047-legacy-discover
prior_dir=build/port-runtime/cycle-090-render-core-client
embeddium=build/port-runtime/cycle-090-render-embeddium-client/mods/embeddium-UTbfe5d1.jar

for renderer in forge embeddium; do
  profile="cycle-092-$renderer-client"
  profile_dir="build/port-runtime/$profile"
  mkdir "$profile_dir"
  cp -R "$prior_dir/libraries" "$prior_dir/versions" "$profile_dir/"
  set --
  if [ "$renderer" = embeddium ]; then set -- "$@" --mod "$embeddium"; fi
  python3 scripts/porting/client/prepare_packaged.py --name "$profile" --java "$port_java" \
    --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" --qa-jar "$qa_jar" \
    --assets "$HOME/.gradle/caches/neoformruntime/assets" "$@"
done

python3 scripts/porting/packaged_facades.py --name cycle-092-forge --port 25841 \
  --java "$port_java" --forge-server "$forge_server" --jar "$port_jar" \
  --launch build/port-runtime/cycle-092-forge-client/launch.json --dimensions

./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-092-client-final.log 2>&1
for renderer in forge embeddium; do
  cp "$qa_jar" "build/port-runtime/cycle-092-$renderer-client/mods/enderio-client-qa.jar"
done

python3 scripts/porting/packaged_facades.py --name cycle-092-forge-final --port 25843 \
  --java "$port_java" --forge-server "$forge_server" --jar "$port_jar" \
  --launch build/port-runtime/cycle-092-forge-client/launch.json --dimensions
python3 scripts/porting/packaged_facades.py --name cycle-092-embeddium-final --port 25844 \
  --java "$port_java" --forge-server "$forge_server" --jar "$port_jar" \
  --launch build/port-runtime/cycle-092-embeddium-client/launch.json --dimensions --embeddium
```

## Results

- The first Forge run reached the Nether with the Yeta Wrench selected. Both the synced models and facade index had the new dimension's state. The initial screenshot showed blue panes, but a check for blue RGB dominance counted zero pixels. Comparing red minus blue with the nearby warm-tinted stone accounts for the Nether light. See `build/port-evidence/cycle-092-forge-client/nether-ghosted.png` and `summary.json` in the same directory.
- The build passed 79 unit and 482 native tests. The native breakdown was 171 core, seven Mekanism, 117 registry, 160 gameplay and 27 addon. The final QA jar rebuilt successfully after the calibration change. See `build/cycle-092-build.log` and `build/cycle-092-client-final.log`.
- The standard Forge and Embeddium 0.3.31 profiles each passed 26 server assertions and 60 client checks. The server confirmed the wrench and actual player level on both crossings. Each client verified the arriving facade index, cleanup of the previous dimension's tracked positions, the four swapped Nether colors, and the model state for all four conduits in both render modes. Both runs recovered the original Overworld color counts of 3,949, 3,929, 3,948 and 3,960 after returning and after reloading resources.
- Both server restarts recovered all four facade types and paints separately in the Overworld and Nether. All four server processes exited at 0 with zero runtime or registry errors. Each of the seven captures per renderer had sound volumes at zero and narrator off. Forge's 2,203 and Embeddium's 2,273 focus samples recorded zero activation, foreground or onscreen events. See `build/port-runtime/cycle-092-forge-final/facade-results.json`, `build/port-runtime/cycle-092-embeddium-final/facade-results.json` and the matching `build/port-evidence/cycle-092-*-final-client/` directories.
- The production jar SHA-256 is `666eb5e2cdc1e833213c1fb99d181fd61a2882d94fdee52e5f37e11226703e0e`.

## Remaining verification

The round trip covers the Overworld and Nether with the standard and Embeddium renderers. The End, connected-texture paint and active shader combinations remain open for dimension changes.
