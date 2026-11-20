# Cycle 208 — Electromagnet attraction and energy draw

Two independent packaged Forge worlds run the same survival fixture. The first uses a magnet in the shipped Curios charm slot and a competing magnet in the hotbar. The second uses only the hotbar. The guarded client invokes the registered magnet key to start and stop the selected item.

The server samples its game time, stored charge, toggle and drop positions in one function. With the magnet off, three stationary drops retain their initial positions. Activation pulls a diamond stack four blocks away into the player inventory. A nearby apple is excluded by the Electromagnet blacklist tag and a copper stack starts beyond the stock five-block range. The fixture verifies one FE per native tick while active and zero while stopped. It checks the closed save, reopens the world, and reconnects the client to verify the exact charge, the off state and the pickup. The two untouched drops stay in the world.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_jar=enderio/build/libs/enderio-8.2.14.207-1.20.1+c9cd026d86.jar
port_curios="$HOME/.gradle/caches/modules-2/files-2.1/maven.modrinth/curios/IPQlZkz1/452175b95ad3db6ff58bb8968f6bf7a9d1e0f480/curios-IPQlZkz1.jar"
port_source="$port_runtime/cycle-207-stock-enchanter-client"

./gradlew build --continue --console=plain > build/cycle-208-magnet-baseline-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-208-magnet-qa-reconnect.log 2>&1
for port_mode in equipped inventory; do
  port_run="cycle-208-$port_mode-confirmed"
  port_client="$port_runtime/$port_run-client"
  mkdir -p "$port_client/config"
  cp -cR "$port_source/libraries" "$port_source/versions" "$port_client/"
  cp "$port_source/config/fml.toml" "$port_client/config/"
  port_client_extra=()
  port_server_extra=()
  port_number=26217
  if [ "$port_mode" = equipped ]; then
    port_number=26216
    port_client_extra=(--mod "$port_curios")
    port_server_extra=(--curios-jar "$port_curios")
  fi
  python3 scripts/porting/client/prepare_packaged.py --name "$port_run-client" \
    --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
    --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets" "${port_client_extra[@]}"
  python3 -u scripts/porting/packaged_electromagnet.py --name "$port_run" --port "$port_number" \
    --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar" \
    --launch "$port_client/launch.json" "${port_server_extra[@]}"
done
```

## Results

- The full build passes 79 unit tests and 540 native Forge runs, including all 213 discovered main gameplay tests. The final guarded client probe reobfuscates. See `build/cycle-208-magnet-baseline-build.log` and `build/cycle-208-magnet-qa-reconnect.log`.
- The initial profiles use `cycle-208-equipped-baseline` and `cycle-208-inventory-baseline` on ports 26214 and 26215. Both live scenarios pass. Their save reader initially requires an explicit `enderio:toggled` entry. The native codec omits the off value because it is the registered default. The corrected reader checks the old saves successfully. The final scenario also checks the restored item directly on the server and reconnecting client.
- The final Curios run passes 23 assertions. The hotbar run passes 18. Each spends exactly 12 FE over a 12-tick active window. The first active samples find the diamond stack 0.512 blocks closer to the player, and both players collect the original seven diamonds. The final disabled charges are 49,981 FE in the charm and 49,979 FE in the hotbar. The competing hotbar magnet in the Curios run keeps 34,000 FE. Both players reconnect with the exact saved charge and pickup. The excluded drops survive both restarts at their original test positions. All four server processes exit zero without runtime or registry errors. See `electromagnet-results.json` under `build/port-runtime/cycle-208-equipped-confirmed/` and `build/port-runtime/cycle-208-inventory-confirmed/`.
- The four guarded clients pass 22 checks. Over 9,105 native samples the observer records zero activation, foreground or visible-window events. All six captures record narrator off and zero volume in all ten sound categories. The reports are under `build/port-evidence/cycle-208-*-confirmed-*-client/`.
- The Ender IO production jar SHA-256 is `042abd953bc8e805ff05ec5bcf3f5312db059485f2edbafc37fd27c3b02577d0`. It contains no client probe classes. The Curios jar SHA-256 is `1e817919a35b37cf30524aaec73f0ca5130452f23f168f844854df282eb8e51f`.

## Next verification

Exercise the Powered Spawner's nearby-spawner efficiency setting and extend Mind Killer verification to stock survival crafting, placement and client rendering.
