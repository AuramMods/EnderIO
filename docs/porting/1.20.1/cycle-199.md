# Cycle 199 — plant matter and green and brown dyes

Two packaged SAG Mills take large ferns and dead bushes through their native input hoppers. Each saves its first stock roll with 600 FE paid. The first dedicated restart checks that roll and the original inputs before the mills complete the batch. The test reads the flushed output hopper stacks from the actual region file. Yield checks accommodate the stock probabilities and the fern's brown byproduct.

Each dye line moves one original output stack from the mill hopper to its smelter's input hopper. The egg recipes save with 400 FE paid. The second restart checks the saved recipe ID, queued output and original inputs before spending the remaining 600 FE. Each egg and six plant matter make one dye. The same smelter then uses a slimeball and twelve from the remaining original stack. Each slimeball recipe spends 1,600 FE and adds two dyes. The final restart checks both dye hoppers, leftover plant matter and all four energy reserves.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.198-1.20.1+ad7faf059c.jar

./gradlew build --continue --console=plain > build/cycle-199-plant-matter-build.log 2>&1
python3 -u scripts/porting/packaged_plant_matter.py --name cycle-199-plant-matter-verified --port 26195 \
  --java "$port_java" --forge-server build/port-runtime/cycle-047-legacy-discover --jar "$port_jar"
```

## Results

- The full build passes 79 unit tests with none skipped and 540 native runs, including all 213 main gameplay cases. The packaged test passes 31 assertions across four headless Forge sessions. See `build/cycle-199-plant-matter-build.log`.
- The two mills each consume 24 plants for exactly 57,600 FE. The ferns yield 48 green plant matter and two brown byproducts. The dead bushes yield 44 brown plant matter. The four stock dye recipes spend 5,200 FE total and consume eighteen original plant matter per color. The final restart keeps three green dyes, three brown dyes, 30 green matter and 26 brown matter in the smelters, both fern byproducts in their original hopper and 137 FE in each machine.
- The first run used `--name cycle-199-plant-matter-stock --port 26194`. It verifies that an empty fern roll is preserved across reload and spends exactly the same energy as a nonempty roll. Its last check generated an extra space before the empty-hopper condition. After correcting the command, the full fresh run passes, and a separate `reopened` check on the first fixture also passes both final assertions. That fixture retains its own 41 green and four bonus brown fern outputs through the same crafting and conservation checks.
- All packaged sessions use the same production jar, SHA-256 `4611b754e22a8c2359a56bf628a71fb20aef7d9fff9428771971c7c3be5af4d8`. The dedicated servers all exit cleanly with zero runtime or registry errors. See `build/port-runtime/cycle-199-plant-matter-verified/plant-matter-results.json` and `build/port-runtime/cycle-199-plant-matter-stock/plant-matter-resume-results.json`.

## Next verification

Supply the stock tank filling recipe with Nutrient Distillation from an actual VAT batch. Verify that a regular stick consumes exactly 1,000 mB, exports the original Nutritious Stick and preserves the remaining liquid and inventory through restart.
