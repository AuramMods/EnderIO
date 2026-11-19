# Cycle 197 — stock iron, gold and copper powders

The packaged Forge server runs six stock SAG Mill recipes with one input per route. The three raw-metal routes feed furnaces. The three ingot routes feed blast furnaces. Each mill receives its input through a native hopper and saves after spending 600 of the stock 2,400 FE. The first restart compares the paid progress, original input and exact queued output, including any rolled byproduct, before supplying the remaining 1,800 FE and a 137 FE reserve.

The original output reaches a hopper. If the job rolled a byproduct, the harness moves only that stack to its neighboring barrel. The hopper sends the original primary powder directly into the furnace below it. Vanilla output hoppers collect the ingots. A second restart checks the exact ingot counts, recorded stock cooking recipes, reserved FE and any stashed byproduct.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.196-1.20.1+23b3e789af.jar

./gradlew build --continue --console=plain > build/cycle-197-metal-powders-build.log 2>&1
python3 -u scripts/porting/packaged_metal_powders.py --name cycle-197-metal-powders-verified --port 26192 \
  --java "$port_java" --forge-server build/port-runtime/cycle-047-legacy-discover --jar "$port_jar"
```

## Results

- The full build passes 79 unit tests with none skipped and 540 native runs, including all 213 main gameplay cases. See `build/cycle-197-metal-powders-build.log`.
- The packaged run passes 55 assertions across three headless Forge sessions. All six mills retain the original saved rolls and spend exactly 14,400 FE combined, leaving 137 FE in each mill. The three raw routes produce two iron, one gold and two copper ingots through 200-tick stock furnace recipes. The three ingot routes each return one original metal through 100-tick stock blasting recipes. The restart preserves all eight outputs, the furnace recipe counts and all six FE reserves.
- The first run used `--name cycle-197-metal-powders-stock --port 26191`. It stopped at the initial furnace check after looking for a short in `CookTimeTotal`. The saved furnaces contained the exact powders and used the native integer field. After correcting the assertion, a fresh full run passed. A separate resume used `fuel_cookers` and `collected` on the first saved fixture and passed 18 assertions across two further dedicated sessions. That fixture kept the original raw-gold roll of two gold powders and one bonus copper powder. The two gold ingots and the separate copper powder survived the final restart.
- All completed sessions use packaged jar SHA-256 `2383aff8b477bfa186722b9fb643f918b489214f715ed81667f5962ba54ad4f0` and exit cleanly with zero runtime or registry errors. See `build/port-runtime/cycle-197-metal-powders-verified/metal-powders-results.json` and `build/port-runtime/cycle-197-metal-powders-stock/metal-powders-resume-results.json`.

## Next verification

Run the stock Ender Pearl and Obsidian grinds. Follow one original powdered pearl into the survival Enderios recipe and verify the remaining powdered pearl, powdered obsidian and crafting remainder after restart.
