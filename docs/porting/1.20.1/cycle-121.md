# Cycle 121 — Verify redstone filters in the conduit inventory

## Changes

- Restore shift-click transfers in the conduit menu. Player items use the actual conduit inventory's slot validation and stack limits. Taking items back uses the player's ordinary inventory and hotbar.
- Calculate slot capacity from the conduit and the item. Forge's default capacity probe clears and restores the live inventory, which can trigger block updates during a placement check.
- Notify blocks around the strong receiver when its output filter changes. The output can change while the raw channels and physical input remain steady.

## Commands

```sh
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-121-qa-before-final.log 2>&1
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-121-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_client_source=build/port-runtime/cycle-120-fixed-client
port_prior=build/port-runtime/cycle-120-confirmed/server/mods/enderio.jar
port_current=enderio/build/libs/enderio-8.2.14.120-1.20.1+3762b8b729.jar

prepare_slot_client() {
  port_profile="build/port-runtime/$1"
  mkdir -p "$port_profile"
  cp -R "$port_client_source/libraries" "$port_client_source/versions" "$port_profile/"
  python3 scripts/porting/client/prepare_packaged.py --name "$1" --java "$port_java" \
    --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$2" \
    --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
}

run_slot_client() {
  run_name=$1
  run_port=$2
  run_client="build/port-runtime/$3"
  shift 3
  python3 scripts/porting/packaged_conduit_filter_slots.py --name "$run_name" --port "$run_port" \
    --java "$port_java" --forge-server "$port_forge" --jar "$run_client/mods/enderio.jar" \
    --launch "$run_client/launch.json" "$@"
}

prepare_slot_client cycle-121-before-client "$port_prior"
run_slot_client cycle-121-before 25939 cycle-121-before-client --baseline
prepare_slot_client cycle-121-fixed-client "$port_current"
run_slot_client cycle-121-fixed 25941 cycle-121-fixed-client
```

The headless control on the previous jar uses `prepare` and `exercise_logic` with `PYTHONPATH=scripts/porting python3 -B` on port `25940`. It passes 33 assertions covering topology, gates, input inversion and the sensor.

## Results

- In the previous jar, survival pickup moves the exact tagged NOT filter from the output slot onto the cursor. The physical input lever remains off. The lamp beyond the strongly powered dispenser stays on after six server ticks with the filter removed. See `build/port-runtime/cycle-121-before/conduit-filter-slot-results.json`.
- The fixed jar clears that lamp on pickup and lights it again when the same filter is reinstalled, with the source lever still off. Multiplayer shift-click installs and recovers one AND filter with its exact red/blue configuration and marker. The input slot rejects it and an ordinary stick stays in the player inventory.
- A cross-chunk packaged Forge circuit confirms all 24 input combinations for AND, NAND, OR, NOR, XOR and XNOR. A NOT filter in the extraction slot reverses both lever states. A sensor filter sends exact analog strengths of 0, 1, 15 and 8 from a real dropper to redstone dust. A dedicated restart preserves the installed inverter and sensor behavior and both receivers respond when the sources change again.
- The full build passes 79 unit cases and 486 native executions, including all 164 discovered gameplay cases. The fixed packaged run passes 41 server assertions and eight client checks. Both dedicated runs exit at zero with no runtime or registry errors. The native macOS observer records 1,628 samples with zero activation, foreground or onscreen events. The capture records all volumes at zero and narrator off.

See `build/port-runtime/cycle-121-fixed/conduit-filter-slot-results.json` and `build/port-evidence/cycle-121-fixed/{summary.json,redstone-conduit-filters.png}`. The installed production jar has SHA-256 `3f408b1384f5d37b247ec1b6ca2dcb7c7cd2172310c99d607c1f5d69b73b4cf0` on both ends.

## Remaining verification

Use a physical Forge 6.2 world fixture with installed redstone filters to verify conversion, chunk eviction and the original saved settings. The sensor fixture samples a dropper. Other analog sources and wrench-forced links remain open.
