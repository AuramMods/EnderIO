# Cycle 200 — Nutritious Stick and tank recipe output stacks

The dedicated test feeds 5,000 mB of water and five pairs of beef and wheat through the stock VAT. The first three batches leave 750 mB in an attached fluid tank, where two sticks wait. After restart the final two batches bring the tank to 1,250 mB. A preexisting Nutritious Stick with a distinct tag occupies the result slot. The tank must preserve both sticks and all the fluid until the result slot can take an untagged item.

Native hopper extraction clears the result slot after another restart. The same tank spends exactly 1,000 mB and one of the original sticks. The collector receives the tagged original and one untagged result as separate stacks. The final restart checks both stacks, the waiting regular stick and the remaining 250 mB. Two companion datapack recipes test that a two-item result leaves the inputs untouched when only one item fits. Once both results fit, each recipe keeps the exact tag, result count and fluid balance.

Both recipe modes now simulate the complete result through the machine's existing inventory handler before spending fluid or inputs. The actual result uses the same handler, so the merge respects item NBT and the slot's capacity and inserts the recipe's full count. This keeps the production path shared with other machine outputs.

## Original defect

On the previous packaged jar, these commands reproduce the incorrect merge in a disposable world. The saved tank changes from 1,250 to 250 mB and holds two items with the original tag, although only one item had that tag before the recipe ran.

```mcfunction
forceload add 3680 0 3695 15
setblock 3686 80 9 enderio:fluid_tank
data merge block 3686 80 9 {Fluids:{Tanks:[{Index:0,Contents:{FluidName:"enderio:fluid_nutrient_distillation_still",Amount:1250}}]},Items:{Items:[{Slot:2,id:"minecraft:stick",Count:1},{Slot:3,id:"enderio:nutritious_stick",Count:1,tag:{PortBatch:"original",OriginalCount:1b}}]}}
data get block 3686 80 9
```

Read the NBT after at least 15 game ticks. The recorded reproduction used `enderio-8.2.14.198-1.20.1+ad7faf059c.jar`. See `build/port-runtime/cycle-200-tank-stack-repro/repro-results.json`.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.199-1.20.1+ef77355206.jar

./gradlew build --continue --console=plain > build/cycle-200-tank-stack-build.log 2>&1
python3 -u scripts/porting/packaged_nutritious_stick.py --name cycle-200-nutritious-stick-stock --port 26198 \
  --java "$port_java" --forge-server build/port-runtime/cycle-047-legacy-discover --jar "$port_jar"
```

## Results

- The full build passes 79 unit tests with none skipped and 540 native runs, including all 213 main gameplay cases. The packaged suite passes 15 assertions across four headless dedicated sessions. All exit cleanly with zero runtime or registry errors. See `build/cycle-200-tank-stack-build.log`.
- The final stock route spends 5,000 mB of water and five original pairs of beef and wheat. The tank holds both sticks at 750 mB. At 1,250 mB the incompatible tagged result still blocks both sticks. After a hopper clears the slot, the tank spends exactly 1,000 mB and exports one new untagged item. Both distinct output stacks, one unprocessed stick and the final 250 mB persist after restart.
- Both authored two-item recipes preserve the input and all 1,000 mB with only one free output position in the stack. Once the existing stack is reduced from 63 to 62, both items merge with the matching tag. The restarted standard tank retains 64 tagged items and 800 mB. The restarted pressurized tank retains 64 tagged items and 1,200 mB.
- An exploratory run loaded extra reagents after a hopper had already delivered its first item. The final fixture places the exact initial count in each hopper before it starts. The passing packaged jar has SHA-256 `325b53553e1bc83d46708b5339724b21288485c01814695254758fe40fa019c7`. See `build/port-runtime/cycle-200-nutritious-stick-stock/nutritious-stick-results.json`.

## Next verification

Exercise the remaining stock filling and emptying recipes in the standard and pressurized tanks. Verify all sixteen concrete colors and the two sponge and experience bottle directions with exact item counts and fluid balances.
