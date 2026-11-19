# Cycle 142 — Mekanism chemical filter and heat controls

A packaged Forge 47.4.28 client loads Mekanism 10.4.16.80 and connects to a dedicated server running a copy of the converted [cycle 054](cycle-054.md) world. The survival player installs a Basic Chemical Filter in the normal chemical conduit’s extract slot. The editor copies 1,450 units each of oxygen, redstone infusion, red pigment and dirty iron slurry into four ordered ghost slots. The physical sample tanks stay in the player’s inventory. The client renders the four samples, selects deny mode and returns to the same conduit endpoint.

The server reads the installed filter independently, including each family, resource ID and amount. It retains all 1,450 oxygen upstream after a two-second observation and delivers all 1,450 hydrogen with an empty source. A clean restart retains the filter, the exact gas balance and the sample tanks. A fresh client reopens the editor and sees the same four entries and deny mode.

The player scrolls the native heat extraction control to Never Active. The server verifies the mode and the original orange channel, pulses a native thermodynamic conductor and observes no warming at the destination. A new client confirms the saved pause after restart and scrolls to Always Active. The next native pulse warms the destination by more than one kelvin.

## Commands

The variables `port_java`, `port_forge`, `port_legacy`, `port_art` and `port_mek` use the pinned artifact paths from [cycle 054](cycle-054.md).

```sh
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-142-mekanism-controls-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-142-mekanism-controls-qa-settled.log 2>&1

port_jar=enderio/build/libs/enderio-8.2.14.141-1.20.1+80d7f3796f.jar
port_client=build/port-runtime/cycle-142-controls-final-client
port_previous=build/port-runtime/cycle-138-impulse-guarded-client
python3 scripts/porting/legacy_fixture/build.py \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" --art "$port_art" \
  --mekanism-probe --mod "$port_mek" --output build/port-runtime/cycle-142-controls-probe-retry
mkdir -p "$port_client/config"
cp -cR "$port_previous/libraries" "$port_previous/versions" "$port_client/"
cp "$port_previous/config/fml.toml" "$port_client/config/fml.toml"
python3 scripts/porting/client/prepare_packaged.py --name cycle-142-controls-final-client \
  --java "$port_java" --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets" \
  --mod "$port_mek"
python3 scripts/porting/packaged_mekanism_controls.py --name cycle-142-controls-final --port 26015 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --launch "$port_client/launch.json" \
  --world build/port-runtime/cycle-054-mekanism-verified/converted/port-migration \
  --probe build/port-runtime/cycle-142-controls-probe-retry/mekanism-probe.jar --mekanism "$port_mek" \
  > build/cycle-142-controls-final.log 2>&1
```

## Results

- The build passes 79 unit cases and all 513 native runs. The final remapped client QA jar builds successfully.
- Both guarded clients pass 14 checks each and the dedicated runs pass 11 independent assertions. Both servers and clients exit normally with zero runtime or registry errors. The two client observers record zero activation, foreground or visible-window events across 2,858 samples. All frames have narrator off and all ten sound categories muted.
- The same production and Mekanism artifacts load on both ends. The production jar has SHA-256 `69fad72fa7e8d6d8f8a31e488ac500502a6ba6711210891ae513ff05ed204279`.
- Earlier runs exposed fixture issues. The first fill returned a remainder, so the probe fills each native test item tank directly and reads it back. The saved infusion family is `infuse_type`. The final heat interaction approaches from the north and gives the client time to settle after the fixture teleport. Reports and logs from the earlier runs are under `build/port-runtime/cycle-142-controls`, `build/port-runtime/cycle-142-controls-retry` and `build/port-runtime/cycle-142-controls-verified`.

The dedicated report is `build/port-runtime/cycle-142-controls-final/mekanism-control-results.json`. The client results and captures are at `build/port-evidence/cycle-142-controls-final-exercise` and `build/port-evidence/cycle-142-controls-final-verify`.

## Next verification

Measure heat equilibrium and recovery after a physical break and reconnection between conductors with different heat capacities.
