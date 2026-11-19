# Cycle 201 — stock concrete, sponge and experience tank recipes

The packaged server runs sixteen concrete filling recipes, sponge wetting and experience bottling in both standard and pressurized tanks. Each station receives two matching inputs from a vanilla hopper. It starts one millibucket short of making the second item, so the tank must keep the first result and the second input through restart.

After restart a neighboring supply tank provides 138 mB through the native pull face. The second recipe joins the first result, empties the supply and leaves exactly 137 mB. Native output hoppers take both original results. The four sponge and experience routes send their same outputs back through the input hoppers while the outlet is briefly closed. The harness seeds each tank so it has room to empty the first returned item and ends one millibucket short of accepting the second. A restart preserves the waiting item and near-capacity balance. The harness then removes one millibucket, and each recipe empties its second item and fills its tank to exactly 16,000 or 32,000 mB. A final restart checks all 36 tanks and collections.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.200-1.20.1+397772f9a5.jar

./gradlew build --continue --console=plain > build/cycle-201-tank-recipes-build.log 2>&1
python3 -u scripts/porting/packaged_stock_tank_recipes.py --name cycle-201-stock-tank-recipes-verified --port 26200 \
  --java "$port_java" --forge-server build/port-runtime/cycle-047-legacy-discover --jar "$port_jar"
```

## Results

- The full build passes 79 unit tests with none skipped and 540 native runs, including all 213 main gameplay cases. See `build/cycle-201-tank-recipes-build.log`.
- The fixed 1.21.1 baseline and Forge branch have the same 19 filling and two emptying recipe IDs. This test exercises the remaining 18 filling and two emptying recipes in both tank variants. The Nutritious Stick recipe ran in [cycle 200](cycle-200.md).
- The first run used `--name cycle-201-stock-tank-recipes --port 26199`. All 36 filling routes passed, and the saved pressurized tank held exactly 31,751 mB and the waiting bottle. The first returned glass bottle landed in hopper slot 1 while the handoff still occupied slot 0. The fixture now closes the outlet during that handoff and checks that the hopper is empty before reopening it.
- The final run passes 197 assertions across four headless Forge sessions. The final restart retains 64 concrete blocks across 32 outlets and exactly 137 mB in each corresponding tank. The four reversible stations retain two dry sponges or two empty glass bottles each. Their exact liquid balances are 16,000 mB in the standard tanks and 32,000 mB in the pressurized tanks.
- All four sessions load the same packaged jar, SHA-256 `84fb82f6dad733bdca1674716b136fa6d968a8320dbb9ed13018833ba875c4fa`, and exit cleanly with zero runtime or registry errors. See `build/port-runtime/cycle-201-stock-tank-recipes-verified/stock-tank-recipes-results.json`.

## Next verification

Verify authored tank recipe replacement and removal on a live reload while a stored input is paused. Check which recipe runs when redstone allows processing to resume, and verify the new result and exact balance through restart.
