# Cycle 202 — live tank recipe reload

Four standard and pressurized tanks start with one item and 2,000 mB of water each. Their redstone controls hold processing. A live datapack reload replaces one recipe at the same ID, removes a second and adds another recipe for its input, removes a third with no replacement, and adds a fourth recipe for an input that was previously unmatched. The harness waits for a function introduced by the reload. Only a vanilla redstone signal then resumes the tanks. The saved input and fluid stay untouched during the reload.

The previous packaged jar ran stale recipes in both modes, still processed an input for the removed recipe, and left the newly matched input unprocessed. Tank processing now queries the current native recipe manager whenever either recipe input is occupied. Light and fluid-state updates still run during load and fluid changes.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_before=enderio/build/libs/enderio-8.2.14.200-1.20.1+397772f9a5.jar
port_jar=enderio/build/libs/enderio-8.2.14.201-1.20.1+481e7de0e5.jar

python3 -u scripts/porting/packaged_tank_recipe_reload.py --name cycle-202-tank-recipe-before --port 26201 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_before"
./gradlew build --continue --console=plain > build/cycle-202-tank-recipe-reload-build.log 2>&1
python3 -u scripts/porting/packaged_tank_recipe_reload.py --name cycle-202-tank-recipe-verified --port 26202 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar"
python3 -u scripts/porting/packaged_stock_tank_recipes.py --name cycle-202-stock-tank-regression --port 26203 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar"
python3 -u scripts/porting/packaged_nutritious_stick.py --name cycle-202-tank-stack-regression --port 26204 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar"
```

## Results

The previous jar reaches all four redstone releases and fails all four outcome checks. The new marker function was already active. The dedicated console records the following tank contents after processing.

| Case | Previous jar | Expected after reload |
| --- | --- | --- |
| Replace the same ID | One `old_fill` feather and 1,900 mB | Two `new_fill` ink sacs and 1,825 mB |
| Replace at a different ID | One `old_empty` glass pane and 2,125 mB | Two `new_empty` leather and 2,350 mB |
| Remove the recipe | One brick and 1,750 mB | Original clay ball and 2,000 mB |
| Add a matching recipe | Original amethyst shard and 2,000 mB | One `added` echo shard and 2,050 mB |

See `build/port-runtime/cycle-202-tank-recipe-before/tank-recipe-reload-results.json` and its `smoke-fresh.log`.

The fixed jar passes all 17 reload assertions across two headless dedicated sessions. The two replacement cases produce the exact new counts and tags, the removed recipe leaves its original clay ball and all water, and the added recipe spends the original amethyst shard. The final restart preserves all four reported states. See `build/port-runtime/cycle-202-tank-recipe-verified/tank-recipe-reload-results.json`.

- All 197 stock tank assertions and all 15 Nutritious Stick and tagged-stack assertions also pass on the fixed jar. Together the three suites run ten packaged dedicated sessions with zero runtime or registry errors. See `build/port-runtime/cycle-202-stock-tank-regression/stock-tank-recipes-results.json` and `build/port-runtime/cycle-202-tank-stack-regression/nutritious-stick-results.json`.
- All three packaged suites use SHA-256 `542078a9534ad5a2ae54cafbe611874d30a841edf482aac406afeddcf69d57e5`. The full build passes 79 unit tests with none skipped and 540 native runs, including all 213 main gameplay cases. See `build/cycle-202-tank-recipe-reload-build.log`.

## Next verification

Generate a Loot Capacitor through one of the stock vanilla chest loot tables. Verify that the native loot modifier attaches typed capacitor data and that installing the same item applies its exact machine modifiers through a dedicated restart.
