# Cycle 099 — Preserve Crafter recipes and output through restart

## Scope

Run the packaged Crafter with native cake and stick recipes and a disposable reloadable datapack. Check that the machine holds onto crafting remainders while its output is occupied, restores them after restart and exports them through a vanilla hopper exactly once.

## Changes

- Resolve the ghost-grid recipe after the Crafter attaches to a server level. Refresh the active recipe and preview when a datapack reload replaces the recipe manager or cached recipe.
- Keep queued crafting results and remainders in block-entity NBT. Prepare the result and remainders from the same input snapshot before consuming one item from each occupied slot and charging for the craft.
- Use the real output stack limit and notify inventory listeners as buffered stacks move into the output slot.

## Commands

```sh
./gradlew :enderio:assemble --console=plain > build/cycle-099-baseline-build.log 2>&1
./gradlew build --continue --console=plain > build/cycle-099-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.98-1.20.1+d75ad39779.jar
port_forge=build/port-runtime/cycle-047-legacy-discover
python3 scripts/porting/packaged_crafter.py --name cycle-099-fixed --port 25877 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar"
```

## Results

- The original packaged Crafter produced one cake and retained 3,200 FE from a starting balance of 6,400 FE. The native block-entity save had its template and cake but lacked the waiting bucket queue. On reopen, the template callback accessed a null level during NBT deserialization. Forge recreated an empty Crafter with zero FE. See `build/port-runtime/cycle-099-before/smoke-fresh.log` and `smoke-reopened.log`.
- The fixed Crafter saved one cake, the ghost template, the capacitor, 3,200 FE and exactly three queued empty buckets. It restored every field after restart. A vanilla hopper took the cake and the three buckets into a barrel. The second restart kept those exact counts with the output and queue empty.
- A second Crafter with a cake already in its output retained its full ingredients and 6,400 FE before and after both restarts. Two vanilla hoppers fed oak planks to a third Crafter and exported four sticks for exactly 3,200 FE.
- The test datapack replaced a brick result with a nether brick through a live reload. The preview changed while redstone control kept the Crafter disabled. Enabling it produced one nether brick, used its one copper ingot and charged once.
- All 14 packaged assertions passed across three headless dedicated-server processes. Each process exited at 0 with empty runtime and registry error lists. See `build/port-runtime/cycle-099-fixed/crafter-results.json`.
- The build passed. Unit reports list 79 tests with zero failures or skips. All 483 native GameTests passed, including all 161 discovered gameplay tests. See `build/cycle-099-build.log`. The tested production jar SHA-256 is `2c724524246bfa23a09c85e61fbf64e396561e7d2654f9db9fa61cb5d06cea4b`.

## Remaining verification

The Crafter's GUI, JEI template transfer and survival carry-and-place behavior still need client coverage.
