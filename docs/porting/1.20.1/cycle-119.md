# Cycle 119 — Restore stateful redstone conduit filters

## Changes

- Encode the timer's elapsed ticks separately from its configured period, including the zero after a pulse. Old port stacks that saved the period in both fields keep the period and restart the phase at zero.
- Store the toggle filter's output separately from whether it has rearmed for another rising edge. Old port stacks missing the output field start with the output off. Their previously recorded output cannot be recovered from that format.
- Sample the complete conduit input set before advancing either output filter. Reading the toggle or counting output no longer advances the filter. Mark the conduit chunk for saving when a filter changes its installed stack data.
- Restore Forge 6.2 filter settings during native item loading. The six two-input gate types keep their two channels. The counting filter keeps its channel, count, maximum and edge state. The timer and toggle filter keep their period and elapsed time or their output and edge state. Current typed data takes precedence when present.

## Commands

```sh
./gradlew build --continue --console=plain > build/cycle-119-build.log 2>&1
./gradlew build --continue --console=plain > build/cycle-119-sampled-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_old=build/port-runtime/cycle-118-fixed-client/mods/enderio.jar
port_intermediate=build/port-runtime/cycle-119-saved-phase/mods/enderio.jar
port_jar=enderio/build/libs/enderio-8.2.14.118-1.20.1+ab6455e0a3.jar
port_legacy=build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar
port_cc="$HOME/.gradle/caches/modules-2/files-2.1/maven.modrinth/cc-tweaked/Qm9iIIEY/739e77a2b60d727d8dcf7874188edf683683329d/cc-tweaked-Qm9iIIEY.jar"

run_filters() {
  run_name=$1
  run_port=$2
  run_jar=$3
  shift 3
  python3 scripts/porting/packaged_redstone_filters.py --name "$run_name" --port "$run_port" \
    --java "$port_java" --forge-server "$port_forge" --jar "$run_jar" "$@"
}

run_filters cycle-119-before 25926 "$port_old"
run_filters cycle-119-verified 25928 "$port_intermediate" --legacy-jar "$port_legacy"
run_filters cycle-119-saved-phase 25930 "$port_intermediate"
run_filters cycle-119-sampled 25931 "$port_jar" --legacy-jar "$port_legacy"
run_filters cycle-119-sampled-core 25932 "$port_jar"
python3 scripts/porting/packaged_cc_tweaked.py --name cycle-119-cc-regression --port 25933 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --cc-jar "$port_cc"
run_filters cycle-119-armed-core 25934 "$port_jar"
run_filters cycle-119-final-core 25935 "$port_jar"
```

The timer-only control on the previous jar uses `prepare` and `exercise_timer` from the same script via `PYTHONPATH=scripts/porting python3 -B`, in a disposable profile on port `25929`.

## Results

- The previous packaged build powers the lamp through a fresh toggle filter with its input lever off. See `build/port-runtime/cycle-119-before/redstone-filters-results.json`.
- A configured timer saved by the previous codec stores 18 in both fields. On the previous jar it supplies one rising edge and then remains high for the rest of the eight-second observation. See `build/port-runtime/cycle-119-saved-timer-before/smoke-timer.log`.
- With the codec fixes alone, the core test passed but a copied world missed a falling edge after reopening. The installed state is now advanced before notifying neighbors, and a native regression checks a read from an empty context before the first actual input sample. See `build/port-runtime/cycle-119-verified/redstone-filters-results.json`.
- The first test of the tick-based fix could submit the third rising edge before the previous falling edge was sampled. A dark lamp was already the expected result of the second rising edge. The final fixture checks the rearmed state before changing the lever again, with the lamp still dark.
- The full build passes 79 ordinary unit cases and 486 native Forge executions. All 164 discovered gameplay cases execute. The two new cases check an eight-tick timer across an actual stack save/load and a held-high toggle filter across a stack save/load and subsequent signal edges.
- The native Forge 6.2.18-beta server writes four configured filter stacks to a real barrel. The port checks the two XOR colors, counting channel and its exact 7 of 12 progress, toggle state and timer phase of 6 of 18. Each keeps the unrelated marker and drops the converted legacy fields. The copied world has no conversion-time registry errors. See `build/port-runtime/cycle-119-sampled/port/smoke-converted.log`.
- The final jar passes all 27 assertions in the copied world. Across separate chunk boundaries, a toggle filter follows the lever's rising edges and an 18-tick timer triggers distinct edges in a counting filter. The latch retains its output with the lever held high across restart, rearms on the falling edge and switches off on the next rising edge. The timer and counter resume, and the barrel still contains all four converted settings. All four server processes exit cleanly with zero runtime or registry errors. See `build/port-runtime/cycle-119-sampled/redstone-filters-results.json`.
- The clean Forge run passes 14 assertions with the rearm check in place and all optional mods absent. The same timer data that previously stalled reaches four counted pulses before shutdown and five after restart. Both server runs exit cleanly with zero runtime or registry errors. See `build/port-runtime/cycle-119-final-core/redstone-filters-results.json`.
- CC:Tweaked 1.121.0 passes all 21 bundled redstone assertions with the same production jar, including split, reconnection and restart. See `build/port-runtime/cycle-119-cc-regression/cc-tweaked-results.json`.

Production jar SHA-256 `efb6637b9b19a873d6313b08fe563896b65aa05039cc1b287a66bb55c6e6ba63`. Historical jar SHA-256 `9d1471e6d4562d0a50f0564bc46573b87fd738bd440fe19397d31847da20f172`.

## Remaining verification

Exercise the timer and counting settings through the packaged multiplayer screens and cover the remaining logic gates, sensor and inversion paths in a packaged world. The 6.2 migration fixture samples loose filters in a saved inventory. Verify already-installed 6.2 filters through a converted conduit route and chunk eviction.
