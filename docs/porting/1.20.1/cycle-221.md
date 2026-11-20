# Cycle 221 — Solar souls on reconnect

Cycle 220 recorded three Phantom-bound solar panels that kept their souls on the dedicated server. Jade received the correct binding at all three positions. The ordinary client block entities opened with empty souls after reconnect.

The solar panel now writes `EntityStorage` through EnderCore's shared disk and chunk-update hook. Energy stays in the disk field `EnergyStored`. The native regression uses all three registered panel types. It applies a source panel's chunk update to a new block entity, loads the disk snapshot and applies a later unbound update to clear the previous soul.

The packaged runner opens independent copies of the previous disposable world. The client reads each native block entity and its local nighttime generation rate, along with Jade's overlay in the optional profile. Both profiles inspect the same bound panels and the unbound Vibrant control on a real dedicated Forge connection.

## Commands

```sh
./gradlew :enderio:runGameTestServer --console=plain > build/cycle-221-solar-before.log 2>&1
./gradlew :enderio:assemble :enderio:reobfClientSmokeJar --console=plain > build/cycle-221-solar-packaging.log 2>&1
./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-221-solar-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_old="$port_runtime/cycle-220-jade-souls-verified/mods/enderio.jar"
port_new=enderio/build/libs/enderio-8.2.14.220-1.20.1+d4eb3f9421.jar
port_source="$port_runtime/cycle-220-jade-souls-verified-client"
port_world="$port_runtime/cycle-220-jade-souls-verified/port-spawners"
port_jade="$port_runtime/cycle-177-tin-fixed-client/mods/jade-xJQHCmWJ.jar"
export JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true

run_solar_profile() {
  port_profile="$1"
  port_number="$2"
  port_jar="$3"
  port_client="$port_runtime/$port_profile-client"
  shift 3
  mkdir -p "$port_client/config"
  cp -cR "$port_source/libraries" "$port_source/versions" "$port_client/"
  cp "$port_source/config/fml.toml" "$port_client/config/"
  port_client_mods=()
  if [ "$#" -gt 0 ]; then
    mkdir -p "$port_client/config/jade"
    printf '%s\n' '{"general":{"enableTextToSpeech":false,"debug":true}}' > "$port_client/config/jade/jade.json"
    port_client_mods=(--mod "$port_jade")
  fi
  python3 scripts/porting/client/prepare_packaged.py --name "$port_profile-client" \
    --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
    --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets" \
    "${port_client_mods[@]}"
  python3 -u scripts/porting/packaged_jade_souls.py --name "$port_profile" --port "$port_number" \
    --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar" \
    --saved-world "$port_world" --launch "$port_client/launch.json" "$@"
}
run_solar_profile cycle-221-solar-control 26259 "$port_old"
run_solar_profile cycle-221-solar-core 26260 "$port_new"
run_solar_profile cycle-221-solar-jade 26261 "$port_new" --jade "$port_jade"
run_solar_profile cycle-221-solar-core-retry 26262 "$port_new"
```

## Results

- The pre-fix development run registers the new test and records a failure in the solar batch. It then crashes before writing XML because the overlapping packaging build replaces classes on its development classpath. The packaged control uses the preserved pre-fix production jar for the runtime comparison. The final native suite runs after packaging finishes.
- The old jar reproduces the bug without Jade. The server confirms the Energetic panel's saved Phantom binding. The native client reports an empty soul and 0 FE per tick, and the new binding assertion fails. See `build/port-runtime/cycle-221-solar-control/jade-souls-results.json`.
- The fixed profile with Jade passes 8 server assertions and 34 client checks. The client's native Energetic, Pulsating and Vibrant block entities recover Phantom and calculate 4, 16 and 64 FE per tick at the fixture's saved midnight. The unbound control recovers no soul and calculates 0 FE per tick. The other three machine bindings and Jade's overlay agree with the current server state. See `build/port-runtime/cycle-221-solar-jade/jade-souls-results.json`.
- The first Forge-only connection using the fixed jar disconnects before gameplay. The retry uses the same production jar and a fresh copy of the same original test world. It passes 8 server assertions and 20 client checks, including the same three Phantom bindings and generation rates. After shutdown, both fixed worlds match the original disk balances of 4,000, 16,000, 64,000 and 0 FE. See `build/port-runtime/cycle-221-solar-core-retry/jade-souls-results.json`.
- The pre-fix control, successful Forge-only client and Jade client record 2,807, 2,997 and 2,851 window samples with zero activation, foreground or onscreen observations. All 18 captures record narrator off and all ten sound categories at zero.
- The full build passes 79 unit cases and 543 native Forge runs. All 215 discovered gameplay tests execute and pass, including the new chunk-update regression. The production jar contains 1,633 owned Java 17 class files across its modules and contains no client QA classes. See `build/cycle-221-solar-build.log` and `enderio/build/test-results/gameTestServer/results.xml`.

| Packaged artifact | SHA-256 |
| --- | --- |
| Ender IO before the fix | `cb5c73e91314a8427d5a4bc19063cc2cf1b6eb920701a68c0325be28e5d04645` |
| Ender IO with the fix | `120c879ff2aa25d18425748a3914e33788199d6c9d4312037b2b51a71570f20b` |
| Jade 11.13.3+forge | `a632c77975e5aedff6322b0072f8b0db3f5d4bf914df6bdcc87d1aea4d5f55f4` |

## Next verification

Exercise directional shapes, wrench visibility and canceled block breaks in the packaged FTB Ultimine fixture from [cycle 096](cycle-096.md).
