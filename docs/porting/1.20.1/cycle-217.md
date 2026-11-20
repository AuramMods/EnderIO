# Cycle 217 — LaserIO guide crafting and Patchouli navigation

The packaged Forge server and guarded client load the pinned LaserIO and Patchouli jars. The survival player receives one stock LaserIO logic chip and one vanilla book. The original recipe arrives over the real recipe packet. A chip alone produces no result. The player adds the book and collects the original Patchouli guide from the vanilla crafting grid.

Using the guide normally opens LaserIO 101. The probe follows the native Filters and Basic Filter buttons and checks the two displayed pages about ghost slots and JEI. The server keeps the same named stack in survival inventory. After the dedicated restart, the second hidden client uses the guide saved on disk, opens the stock entry and checks both pages again.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_jar=enderio/build/libs/enderio-8.2.14.216-1.20.1+92cba49364.jar
port_mods="$port_runtime/cycle-177-tin-fixed-client/mods"
port_laser="$port_mods/laserio-626839-5074767.jar"
port_patch="$port_mods/patchouli-8Qyw08ld.jar"
port_source="$port_runtime/cycle-215-stock-machine-resets-client"
port_run=cycle-217-laserio-guide-ipv4
port_client="$port_runtime/$port_run-client"

./gradlew build --continue --console=plain > build/cycle-217-laserio-guide-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-217-laserio-guide-qa.log 2>&1
mkdir -p "$port_client/config"
cp -cR "$port_source/libraries" "$port_source/versions" "$port_client/"
cp "$port_source/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name "$port_run-client" \
  --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets" \
  --mod "$port_laser" --mod "$port_patch"
JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true \
  python3 -u scripts/porting/packaged_laserio_guide.py --name "$port_run" --port 26237 \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar" \
  --laserio "$port_laser" --patchouli "$port_patch" --launch "$port_client/launch.json"
```

## Results

- The full build passes 79 unit tests and 541 native Forge GameTest runs. The guarded client probe reobfuscates. See `build/cycle-217-laserio-guide-build.log` and `build/cycle-217-laserio-guide-qa.log`.
- The default JVM launches on ports 26235 and 26236 disconnect during login. Forge sends the login handshake packets and receives no acknowledgements. The client logs show no play session. The completed profile uses the same jars on IPv4 loopback with `-Djava.net.preferIPv4Stack=true` on both peers. The cause of the default login failures remains unverified. See the server and client debug logs under `build/port-runtime/cycle-217-laserio-guide*`.
- The IPv4 profile passes eight server assertions and 32 client checks. Vanilla crafting consumes the original logic chip and book and creates one guide tagged `patchouli:book=laserio:laseriobook`. Both launches open the named guide, select Filters and render both Basic Filter pages. The dedicated world saves that exact single guide after both sessions. Both server boots exit with code zero and no runtime or registry errors. See `build/port-runtime/cycle-217-laserio-guide-ipv4/laserio-guide-results.json`.
- The two successful native watchers record 1,705 and 1,483 samples with zero activation, foreground or onscreen observations. All seven captures record every volume category at zero and narrator off. See `build/port-evidence/cycle-217-laserio-guide-ipv4-craft-client/` and `build/port-evidence/cycle-217-laserio-guide-ipv4-reopen-client/`.

| Packaged artifact | SHA-256 |
| --- | --- |
| Ender IO | `8d3cdbe65f27a25a8a6c22f7387b087cc07fcd6a5524e7f5948387663d864cab` |
| LaserIO 1.6.8 | `f07fc6cb7f2aa28197e87ffc3a35062390259ad803d8996b08b13473c0af6790` |
| Patchouli 1.20.1-84.1-forge | `9cc38ebb5e29b350222e337b7a5a2ba238c802230a89434ce00e90ee3026d056` |

## Next verification

Configure a Basic Filter in LaserIO's own editor, install that original stack in an Ender IO conduit and verify its matching behavior after restarting the server.
