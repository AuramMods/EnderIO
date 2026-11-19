# Cycle 120 — Configure redstone conduit filters over multiplayer

## Changes

- Open the timer, counting and two-channel filter menus with Forge's screen-opening data. The payload identifies which hand opened the menu. A counter held in the offhand uses its own settings even when the main hand holds another counter.
- Apply updates only to the item bound to the currently open menu. The timer's confirm button sets the period and resets its phase. Counting settings carry the color and limit, and the server retains the accumulated pulses and edge state. Both gate colors remain configurable.
- The filter packet format changes the Ender IO network protocol to `forge-2`. Use matching versions of Ender IO on the client and server. Saved filter data uses the same format as cycle 119.

## Commands

```sh
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-120-qa-before.log 2>&1
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-120-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_client_source=build/port-runtime/cycle-118-fixed-client
port_prior=build/port-runtime/cycle-119-final-core/mods/enderio.jar
port_current=enderio/build/libs/enderio-8.2.14.119-1.20.1+7d78d144a9.jar

prepare_redstone_client() {
  port_profile="build/port-runtime/$1"
  mkdir -p "$port_profile"
  cp -R "$port_client_source/libraries" "$port_client_source/versions" "$port_profile/"
  python3 scripts/porting/client/prepare_packaged.py --name "$1" --java "$port_java" \
    --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$2" \
    --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
}

run_redstone_menus() {
  run_name=$1
  run_port=$2
  run_client="build/port-runtime/$3"
  shift 3
  python3 scripts/porting/packaged_redstone_filter_menus.py --name "$run_name" --port "$run_port" \
    --java "$port_java" --forge-server "$port_forge" --jar "$run_client/mods/enderio.jar" \
    --launch "$run_client/launch.json" "$@"
}

prepare_redstone_client cycle-120-before-client "$port_prior"
run_redstone_menus cycle-120-before 25936 cycle-120-before-client --baseline
prepare_redstone_client cycle-120-fixed-client "$port_current"
run_redstone_menus cycle-120-fixed 25937 cycle-120-fixed-client
run_redstone_menus cycle-120-confirmed 25938 cycle-120-fixed-client
```

## Results

- The prior packaged build already lets the main-hand timer change from 20 to 40 using the screen. The same client opens a real counting menu with two distinct counting filters in hand. When using the offhand, the menu fails the assertion for its limit of eight and current count of one. See `build/port-evidence/cycle-120-before/summary.json`. Native observation records 1,947 samples with zero foreground, activation or onscreen events.
- The fixed client passes 16 checks. The timer confirm button sets 40 ticks. The offhand counter's color and confirm controls set white and two pulses while keeping its one accrued pulse and rearmed state. The separate main-hand counter keeps its blue channel, limit of eleven, count of three and rearmed state. Two independent AND pickers set red and blue. The server confirms all three configured stacks before the screens close.
- The first fixed run passed the client checks. Its circuit fixture tried to save an empty conduit inventory before copying the filter, and the bundle omitted the empty list. The confirmed run archives each item while the player is connected and installs its exact serialized stack in one native command.
- The final packaged run passes 18 server assertions. Across a chunk boundary, a timer pulse lights the counting filter's lamp at two, and the next pulse wraps it to one and clears the lamp. The AND route stays dark with zero or one selected input, lights for both red and blue, and responds to removing blue after a full restart. The counting route also continues its cycle after restart. Each server process exits at zero with no runtime or registry errors. See `build/port-runtime/cycle-120-confirmed/redstone-filter-menu-results.json`.
- The full build and QA remapping pass, with 79 unit cases and 486 native executions. All 164 discovered gameplay cases execute. The two installs use the same production jar with SHA-256 `628b44a0dad2e1f89988f4f873b26bb8901ad1b430b5d2ce1dc934ac09f119fc`.
- All three final frames record the narrator off and all volumes zero. The native observer reports zero activation events, foreground samples and onscreen samples over 1,743 observations. See `build/port-evidence/cycle-120-confirmed/{timer,counter,and-gate}.png` and `summary.json` in that directory.

## Remaining verification

This scenario saves the configured stacks through multiplayer menus and uses native console commands to install those stacks in the circuit. The hidden client operates the controls through game UI callbacks. Installing filters through the conduit inventory UI, recovering installed Forge 6.2 filters, and testing the other gate, sensor and inversion paths remain open.
