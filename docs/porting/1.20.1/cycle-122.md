# Cycle 122 — Migrate installed Forge 6.2 redstone filters

The packaged 6.2.18-beta server places redstone conduits across a chunk boundary using its own block items. The fixture configures the original connection types and their native filter accessors. It saves the world with an XOR gate, a held-high toggle filter, two paused timer/counter pairs, and a sensor and inverter on a chest route that uses the original forced-connection path. The port opens a copy of that world with the production Ender IO jar alone.

A Forge 6 timer could keep the elapsed ticks when the player shortened the period. If a saved timer already met the shorter period, the current codec could interpret the equal phase and period as the older port's corrupt data and restart from zero. Importing a Forge 6 stack now stores an already-due phase just below the period so that it pulses on the next conduit sample. An in-range phase is retained exactly. This leaves the behavior for previously written port data as specified in [cycle 119](cycle-119.md).

## Commands

```sh
./gradlew build --continue --console=plain > build/cycle-122-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_legacy=build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar
port_prior=build/port-runtime/cycle-121-fixed/server/mods/enderio.jar
port_current=enderio/build/libs/enderio-8.2.14.121-1.20.1+e480b9460c.jar
port_art="$HOME/.gradle/caches/modules-2/files-2.1/net.neoforged/AutoRenamingTool/2.0.17/9388df4c3589faf10c35d9154cfb2451c83bd717/AutoRenamingTool-2.0.17-all.jar"
port_fixture=build/port-runtime/cycle-122-complete-fixture

python3 scripts/porting/legacy_fixture/build.py --java "$port_java" --forge-server "$port_forge" \
  --legacy-jar "$port_legacy" --art "$port_art" --output "$port_fixture" > build/cycle-122-complete-fixture.log 2>&1

run_filter_migration() {
  run_name=$1
  run_port=$2
  run_jar=$3
  python3 scripts/porting/packaged_legacy_redstone_filters.py --name "$run_name" --port "$run_port" \
    --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" \
    --fixture "$port_fixture/fixture.jar" --jar "$run_jar" > "build/$run_name.log" 2>&1
}

run_filter_migration cycle-122-due-before 25943 "$port_prior"
run_filter_migration cycle-122-fixed 25945 "$port_current"
```

The isolated timing control on port `25944` uses `PYTHONPATH=scripts/porting python3 -B`, copies the closed converted world from `cycle-122-due-before/port`, and calls `load_routes` and `resumed_timer` on lane 1 with a 24–45 tick observation window. This measures the previous port's first pulse without needing to change the original Forge 6 world. The earlier four-route check used the same fixture before adding the shortened timer and forced chest. It passed 61 assertions on port `25942`.

## Results

- The previous port passes the ordinary converted filter checks. It saves the shortened timer with `ticks:30,maxTicks:30`, and its live first pulse arrives after 33 ticks from the enable command. See `build/port-runtime/cycle-122-due-before/legacy-redstone-filter-results.json` and `build/port-runtime/cycle-122-due-control/results.json`.
- The full build passes 79 unit cases and 487 native executions. All 165 discovered gameplay cases execute. The added case loads actual legacy timer item tags at and beyond the shorter period, saves and reopens both, and verifies the first real filter sample emits the due pulse.
- The fixed packaged run passes 71 assertions. All eight installed filters keep their slots and original settings through conversion, both chunks unloading and reloading, and a dedicated restart. The held latch keeps its output and responds to a new rising edge. The migrated XOR still uses both configured colors. The forced chest connection keeps driving the sensor and inverter as the chest fills and empties. Breaking the converted gate conduit drops one filter with its exact channels and original tag.
- The timer saved at 30 of 80 ticks reaches its counter's limit 52 ticks after the enable command. The shortened timer reaches its counter's limit within eight observed ticks, including command and polling time. Native testing verifies the exact first-sample behavior. Both counters resume from their saved counts, reach their limits and wrap on the next pulse.
- The original, converted and reopened servers each exit at zero with no runtime or registry errors. All runs are headless. See `build/port-runtime/cycle-122-fixed/legacy-redstone-filter-results.json`. The production SHA-256 is `4ba0a0279508f987df378acfc55702867198117d7e0a9b84186523219689c33e`.

## Remaining verification

This fixture covers the installed redstone filter types across five routes in 6.2.18-beta. Other historical releases, legacy item and fluid filter variants, and more mixed conduit fixtures remain to be reconciled in the port tracker.
