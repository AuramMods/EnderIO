# Cycle 117 — Export Cloud Seed batches larger than the VAT tank

The stock Blue Ice multiplier produces 81,000 mB. The VAT output tank has an 8,000 mB capacity. The previous package reaches 100% fermentation with the entire result pending and the ingredients still in the machine. It cannot commit the batch.

An oversized result now commits once its output tank accepts the first portion. The paid remainder retains the original fluid and tag, saves with the VAT and occupies its output until the machine can publish it. The next recipe waits while that remainder is outstanding. The VAT retains the existing all-or-nothing output behavior for batches that fit in its tank.

The native VAT loot table carries the paid remainder and the ordinary tanks into the item. Placement restores both. The regression test creates a VAT with a tagged paid remainder, takes its native drop, serializes the stack, replants it and drains both exact portions.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_prior=build/port-runtime/cycle-116-fixed/server/mods/enderio.jar
port_jar=enderio/build/libs/enderio-8.2.14.116-1.20.1+6211ed3847.jar

python3 scripts/porting/packaged_vat_large_batch.py --name cycle-117-before --port 25921 \
  --java "$port_java" --forge-server "$port_forge" --prior-jar "$port_prior"
./gradlew build --continue --console=plain > build/cycle-117-build.log 2>&1
./gradlew :enderio:verifyGameplayTests --console=plain > build/cycle-117-native-verified.log 2>&1
python3 scripts/porting/packaged_vat_large_batch.py --name cycle-117-verified --port 25921 \
  --java "$port_java" --forge-server "$port_forge" --prior-jar "$port_prior" --jar "$port_jar"
python3 scripts/porting/packaged_vat_automation.py --name cycle-117-stocks --port 25922 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar"
```

## Results

- A clean run on the previous production jar reaches 400 of 400 ticks and saves an 81,000 mB Cloud Seed result. The VAT still holds the Blue Ice, prismarine shard and 1,000 mB water. The internal output tank and receiver stay empty. See `build/port-runtime/cycle-117-before/vat-large-batch-results.json`.
- The fixed package opens a copy of the old world, consumes the ingredients once and exports 16,000 mB. The VAT holds 8,000 mB in its output tank and 57,000 mB in its paid remainder. The next recipe's snowball, prismarine and water remain untouched while the output is blocked. The first restart preserves all three fluid balances and the waiting ingredients.
- The station fills two additional 32,000 mB receivers. Once it has published the rest of the Blue Ice batch to its output tank, the waiting snowball recipe produces another 110 mB. Four receivers finish with 16,000, 32,000, 32,000 and 1,110 mB. The second restart preserves all 81,110 mB with an empty VAT and no replay. See `build/port-runtime/cycle-117-verified/vat-large-batch-results.json`.
- The oversized-batch scenario passes nine assertions across four server runs, including the baseline reproduction. The stock-suite rerun passes all 17 assertions, including the tagged recipe's ordinary output backpressure. Every server exits at zero and reports no runtime or registry errors. The packaged SHA-256 is `c5fcb60e77ac04967221294643168602b1c8d97a4b47c1ec2964ab46aebf48c6`. See `build/port-runtime/cycle-117-stocks/vat-automation-results.json`.
- The full build passes 79 unit cases and the existing 483 Forge executions. The newly registered carry test initially used the template's coordinates directly. After accounting for Forge's one-block vertical offset, all 162 discovered gameplay cases pass. The new native test publishes 8,000 mB and then 4,302 mB of the same tagged fluid from the replanted VAT. See `build/cycle-117-build.log`, `build/cycle-117-native-verified.log` and `enderio/build/test-results/gameTestServer/results.xml`.

## Remaining verification

Check the progress indicator and redstone pause during a queued batch in a client. Remaining reagent variants, older VAT saves and Vapor bucket placement still need coverage.
