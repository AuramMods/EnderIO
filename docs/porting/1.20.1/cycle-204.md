# Cycle 204 — Soul and Withering Powders

The stock Alloy Smelter consumes one gold ingot and one Soul Sand for exactly 5,600 FE. Its outlet supplies the actual Soularium ingot to a SAG Mill. Twenty-four separate mills process Wither Skeleton skulls, and twenty-four process Wither Roses. Each of the 49 jobs receives its input through a native hopper and spends 600 FE before saving. The report records the exact rolled item and quantity from each closed save. Skull jobs can produce two or three items, and rose jobs can produce zero or one.

The first dedicated restart restores each original input and its exact queued result. Supplying the remaining 1,800 FE per job consumes the input once and exports the saved yield. The final restart checks every outlet, the 137 FE reserve in every mill and the smelter's retained reserve. The baseline also ships `enderio:confusing_powder` as a plain item with translations, a model and a texture. The packaged test routes three through a native hopper into a barrel and checks the stock resources and saved stack. The [packaged client model inventory](cycle-168.md) also records that item ID before and after resource reload.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.203-1.20.1+cb75edde65.jar

./gradlew build --continue --console=plain > build/cycle-204-stock-powders-build.log 2>&1
python3 -u scripts/porting/packaged_soul_powders.py --name cycle-204-stock-soul-powders --port 26207 \
  --java "$port_java" --forge-server build/port-runtime/cycle-047-legacy-discover --jar "$port_jar"
```

## Results

- The full build passes 79 unit tests with none skipped and 540 native runs, including all 213 main gameplay cases. The packaged suite passes 352 assertions across three headless dedicated sessions. See `build/cycle-204-stock-powders-build.log`.
- The Soularium produces exactly one Soul Powder. Nineteen skulls roll two Withering Powders each, and five roll three. Nine roses yield one powder each, and fifteen yield an empty result. Those exact rolls produce 62 Withering Powders across the separate native outlets. The mill jobs consume exactly 117,600 FE in total, and the upstream Soularium costs 5,600 FE. The final restart preserves all outputs and every 137 FE reserve.
- The fixed 1.21.1 source references Confusion Powder in its plain item registration, model and translations. The packaged Forge jar retains those resources. Its three-item native hopper transfer survives both restarts. The cycle 168 client report includes `enderio:confusing_powder` among its resolved inventory models both before and after resource reload.
- All three sessions exit cleanly with zero runtime or registry errors and use the same package, SHA-256 `6a12af48de843c48218fc2bfbdc45c8acfaa6e66f668b86c8eb3c9cc2cd4849d`. See `build/port-runtime/cycle-204-stock-soul-powders/soul-powder-results.json`.

## Next verification

Exercise the registered Creative Power block through native Forge Energy extraction and a stock conduit route. Verify repeated delivery and saved sink balances, and check the baseline creative inventory visibility rules for the developer block and creative tab icon.
