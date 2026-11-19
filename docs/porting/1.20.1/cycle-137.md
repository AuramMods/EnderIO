# Cycle 137 — Conserve Impulse Hopper batches and energy

The Impulse Hopper checks every configured lane before making a transfer. Each lane needs its full matching input, and its output must have available capacity and the same item and tags. Clearing a template leaves the leftover input in its slot and lets the other lanes continue. The lane indicators use the configured template and report input readiness and output compatibility independently.

The hopper charges the full batch at 10 FE per item from its capacitor. It retains the existing minimum interval of 20 ticks. Larger batches use an interval based on the batch cost and the capacitor's configured average energy draw. A six-stack batch at the basic capacitor's default 16 FE/t uses a 240-tick interval and costs 3,840 FE. The port item reader also promotes charge, redstone and side controls from archived Forge 6.2 Impulse Hopper drops while preserving their carried inventory.

## Commands

```sh
./gradlew :enderio:runGameTestServer --console=plain > build/cycle-137-impulse-before.log 2>&1
./gradlew build --continue --console=plain > build/cycle-137-impulse-build-final.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_jar=enderio/build/libs/enderio-8.2.14.136-1.20.1+412e786ea5.jar
legacy_jar=build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar
python3 scripts/porting/packaged_impulse_hopper.py --name cycle-137-impulse-verified --port 26002 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar"
python3 scripts/porting/packaged_impulse_hopper.py --name cycle-137-impulse-legacy-verified --port 26003 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --legacy-jar "$legacy_jar"
```

## Results

- The before run discovers 190 gameplay cases and fails all five new Impulse Hopper regressions. They exercise an underfunded batch, conflicting outputs, a cleared lane with stored input, a maximum-size batch, and the lane indicators. The final build passes all 190 main gameplay cases, 512 native Forge runs across five profiles, and 79 unit cases.
- The packaged port passes nine assertions across three headless runs. A vanilla hopper supplies both lanes and a separate hopper on the disabled physical side retains its three tagged ingots. At 29 FE, a ready 30 FE batch waits through more than two cycles under signal. Both inputs, templates, and a newly staged conflicting output persist across restart. With 30 FE available, the conflicting tag keeps the whole batch waiting. Removing the blocker releases two tagged ingots and one redstone for exactly 30 FE. A vanilla hopper sends those outputs to a barrel, and another restart preserves them. See `build/port-runtime/cycle-137-impulse-verified/impulse-hopper-results.json`.
- The official Forge 6.2.18 beta runs one real batch, leaving a second partially supplied batch and 980 FE. The copied save restores both templates, the placed inputs and outputs, the capacitor, both configured sides and redstone. Its archived drop keeps the inventory and promotes the exact charge and controls to the port's item data. A vanilla hopper supplies the missing redstone, and the converted machine completes the second batch for 20 FE. Eight assertions pass through conversion and restart. All six headless processes exit normally with zero runtime or registry errors. The tested port jar has SHA-256 `f1fc355291b26798f734a85f4e712ee1a5ef8b78abcd4a4a0863d3bf214b34b1`. See `build/port-runtime/cycle-137-impulse-legacy-verified/impulse-hopper-results.json`.

## Remaining verification

The multiplayer menu still needs an interactive pass for template editing, the lane indicators and side controls. Survival crafting, factory reset and ordinary placement of the original Forge 6 drop also remain open.
