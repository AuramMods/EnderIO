# Cycle 118 — Verify the VAT queue and redstone controls in multiplayer

Match the displayed reagent multiplier to the actual item in each VAT slot with the same rules used to calculate the finished batch. The dedicated fixture inserts gold first and copper second into a recipe authored with copper first and gold second. The expected slot multipliers are 0.75 and 1.5.

Synchronize the paid fluid remainder to the VAT menu using the existing Forge fluid sync slot. The output tooltip shows the physical tank contents and the separately queued amount. A redstone-paused VAT recalculates output backpressure when an external machine empties its output tank. The material stays queued until the VAT receives a signal.

## Commands

```sh
./gradlew :enderio:runData --console=plain > build/cycle-118-datagen.log 2>&1
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-118-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-118-qa-final.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_prior_client=build/port-runtime/cycle-115-fixed-client
port_prior_jar=build/port-runtime/cycle-117-verified/fixed/mods/enderio.jar
port_jar=enderio/build/libs/enderio-8.2.14.117-1.20.1+efb9cd5b23.jar

prepare_client() {
  port_client="build/port-runtime/$1"
  mkdir -p "$port_client"
  cp -R "$port_prior_client/libraries" "$port_prior_client/versions" "$port_client/"
  python3 scripts/porting/client/prepare_packaged.py --name "$1" --java "$port_java" \
    --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$2" \
    --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
}

run_controls() {
  run_name=$1
  run_port=$2
  run_client="build/port-runtime/$3"
  shift 3
  python3 scripts/porting/packaged_vat_controls.py --name "$run_name" --port "$run_port" --java "$port_java" \
    --forge-server "$port_forge" --jar "$run_client/mods/enderio.jar" --launch "$run_client/launch.json" "$@"
}

prepare_client cycle-118-before-client "$port_prior_jar"
prepare_client cycle-118-fixed-client "$port_jar"
run_controls cycle-118-before 25923 cycle-118-before-client --baseline
run_controls cycle-118-fixed 25924 cycle-118-fixed-client
cp enderio/build/test-libraries/enderio-client-qa.jar build/port-runtime/cycle-118-fixed-client/mods/enderio-client-qa.jar
run_controls cycle-118-confirmed 25925 cycle-118-fixed-client
```

## Results

- The previous packaged client renders gold and copper with `x1.0` in both slots. The corrected client renders `x0.75` under gold and `x1.5` under copper. Both menus show the same authored recipe at full progress with the reagents reversed. See `build/port-evidence/cycle-118-before/reversed-reagents.png` and `build/port-evidence/cycle-118-confirmed/reversed-reagents.png`.
- The native VAT picker switches to signal mode. With no signal, a neighboring pressurized tank pulls out the 8,000 mB already published. Through at least 15 server ticks with room in its output, the VAT keeps 57,000 mB queued, stays at full progress and reports redstone as the cause of the pause. The tooltip reports the empty tank and the full queued amount.
- A physical redstone block resumes the VAT while its screen remains open. The pressurized tank reaches 32,000 mB, the VAT output reaches 8,000 mB and the queue drops to 25,000 mB. Including the original 16,000 mB receiver, all 81,000 mB are accounted for. The new queue amount, redstone mode and receivers survive a server restart. See `build/port-runtime/cycle-118-confirmed/vat-controls-results.json`.
- Datagen and the full build pass all 79 unit cases and 484 Forge executions, including all 162 discovered gameplay cases. Both corrected packaged runs pass seven server assertions and 17 client checks. The final run visually captures each tooltip through the native Forge screen render path in `build/port-evidence/cycle-118-confirmed/{queued,paused-with-room,resumed}.png`. Both server runs exit cleanly with zero runtime or registry errors. The focus observer records 1,915 samples with zero activation, foreground or onscreen events. All sound categories and the narrator are off in every captured frame. The production SHA-256 is `993690cedf0969157c346c7774501f3a8916a623546c41b179fbe9865a8822cc`.

## Remaining verification

Remaining reagent variants, older VAT saves and Vapor bucket placement still need coverage. Check queued output with JEI and Jade installed and exercise the other physical redstone modes.
