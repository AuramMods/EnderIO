# Cycle 219 — Bundled redstone during chunk eviction

The packaged fixture uses a straight redstone conduit with 51 bundles through chunk columns 173 to 177. The receiver and white lever stay in force-loaded chunks. The black lever and its extraction connection sit in chunk 173, far enough away that keeping the receiver active does not keep the remote chunk loaded. The fixture uses native `execute unless loaded` and `execute if loaded` checks to verify the transition.

The CC:Tweaked startup program reads the back face on every native redstone event. It checks the full bundled mask, the analog reading, the white, black and red bits and the five other faces. In the relay profile, a computer above the relay uses the stock peripheral API to read the relay's back face. The regression also retains the powered-source removal, physical bridge split and replacement, and dedicated restart from [cycle 094](cycle-094.md).

## Commands

```sh
./gradlew build --continue --console=plain > build/cycle-219-cc-baseline-build.log 2>&1
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_jar=enderio/build/libs/enderio-8.2.14.218-1.20.1+76749e38bd.jar
port_cc="$port_runtime/cycle-177-tin-fixed-client/mods/cc-tweaked-Qm9iIIEY.jar"

run_bundled_route() {
  port_profile="$1"
  port_number="$2"
  shift 2
  python3 -u scripts/porting/packaged_cc_tweaked.py --name "$port_profile" --port "$port_number" \
    --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar" "$@"
}
run_bundled_route cycle-219-cc-relay 26247 --cc-jar "$port_cc" --receiver redstone_relay
run_bundled_route cycle-219-cc-normal 26248 --cc-jar "$port_cc"
run_bundled_route cycle-219-cc-advanced-verified 26249 --cc-jar "$port_cc" --receiver computer_advanced
run_bundled_route cycle-219-cc-turtle-verified 26250 --cc-jar "$port_cc" --receiver turtle_normal
run_bundled_route cycle-219-cc-advanced-turtle-verified 26251 --cc-jar "$port_cc" --receiver turtle_advanced
run_bundled_route cycle-219-cc-command-verified 26252 --cc-jar "$port_cc" --receiver computer_command
run_bundled_route cycle-219-core 26253
```

## Results

The full build passes 79 unit cases and 542 native Forge GameTests, including all 214 discovered gameplay cases. The packaged checks use Forge 47.4.28 and the unmodified production jar from `76749e38b`. Each profile uses its own headless server and disposable world.

The standard, advanced and command computers, both turtle variants and the native redstone relay each pass 29 assertions, including 16 Lua observations. All six receivers report the same values on the tested transitions.

| Transition | Bundled mask | Analog |
| --- | ---: | ---: |
| Both powered levers are loaded | 32,769 | 15 |
| Remote black source chunk unloads | 1 | 15 |
| White lever turns off while the remote chunk is absent | 0 | 0 |
| Remote chunk reloads with its original powered lever | 32,768 | 0 |
| White lever turns on again | 32,769 | 15 |
| Dedicated server restarts with both levers saved on | 32,769 | 15 |

The same route drives a vanilla redstone lamp with CC:Tweaked absent. The lamp remains lit when black unloads and follows the local white lever. It remains off when only the remote black channel returns. The control passes 28 assertions, bringing the total to 202. All 14 server processes exit at zero with empty runtime and registry error lists. Each profile writes `cc-tweaked-results.json` under its directory in `build/port-runtime`, alongside Lua traces for profiles with CC:Tweaked.

| Packaged artifact | SHA-256 |
| --- | --- |
| Ender IO | `d68eacb7b2d791dc66da2d8536367d00ac7197a2468c741a3b8cdf1a5f69c5b8` |
| CC:Tweaked 1.121.0 | `a95ba58640db0198c86f0be99154af92fda17a78975c88aa45ffc430159a6fe9` |

## Next verification

Verify Jade's soul overlay on the other soul-bearing Ender IO machines, including the Powered Spawner, after native binding, removal and restart.
