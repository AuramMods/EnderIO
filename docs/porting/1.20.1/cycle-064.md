# Cycle 064 — migrate placed and carried capacitor banks

The Ender IO 6.2.18-beta server wrote a disposable world with two charged basic banks, a charged advanced bank and an isolated vibrant bank. The three adjacent banks straddle a chunk boundary. Native 6.2 block loot put a separate basic bank with 78,912 FE in a barrel before the source world was copied. The fixture seeds balances and settings with server commands, then checks the data emitted by the official jar. It uses no client or real user save.

The previous packaged build parked its server thread during converted chunk loading. The thread dump traces `CapacitorBankBlockEntity.setLevel` through `setRedstoneControl` and `BlockEntity.setChanged` to a blocking lookup for the chunk Minecraft was still loading. The network constructor now initializes redstone without writing to the chunk. `onLoad` marks the converted tile for saving after attachment. A position already owned by the restored graph uses that graph's balance, so retrying an old chunk cannot import its balance twice.

The item capability reads the older `BlockEntityTag.Energy` balance until a real transfer writes the port's typed energy data. The transfer retires the old balance and keeps the rest of the legacy tag. Placement imports one unit's charge, takes explicit current I/O and charge first, and restores a legacy bank's redstone mode when it forms an isolated network. The normal placement callback still selects the initial display on the player-facing side. A native GameTest covers nondestructive simulation, a carried stack of two, recharge, native placement, and relocation with the new bank's actual drop.

## Reproduction

```sh
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain
python3 scripts/porting/packaged_legacy_migration.py \
  --name cycle-064-verified-retry --port 25718 --capacitor-banks \
  --java /Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java \
  --forge-server build/port-runtime/cycle-047-legacy-discover \
  --legacy-jar build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar \
  --jar enderio/build/libs/enderio-8.2.14.63-1.20.1+54614232b1.jar
python3 scripts/porting/packaged_capacitor_banks.py \
  --name cycle-064-fresh-banks --port 25719 \
  --java /Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java \
  --forge-server build/port-runtime/cycle-047-legacy-discover \
  --jar enderio/build/libs/enderio-8.2.14.63-1.20.1+54614232b1.jar
```

The packaged fixture observes each tile's native I/O, display and redstone data, then reads the flushed graph files for exact balances, node capacities and edges. After a clean restart, it copies only the isolated bank's original region back into the disposable port world and restarts once more with the port's saved graph still present. The wired charger consumes a known 10,001 FE from its buffer and checks the carried bank's total and NBT.

## Results

- The baseline stalled during conversion and the runner terminated its hung child. The log and thread dump are under `build/port-runtime/cycle-064-baseline/`.
- The full Java 17 build passed 78 ordinary unit tests and 430 native Forge GameTests. No tests failed or were skipped. See `build/cycle-064-final-build.log` and the three modules' `build/test-results/test/` XML reports.
- The fresh packaged world passed all 13 capacitor-bank assertions. It conserved 7,000,000 FE across a chunk reload, drop and graph split, then transferred exactly 128,000 FE after restarting. Both server processes exited zero. See `build/port-runtime/cycle-064-fresh-banks/capacitor-bank-results.json`.
- The legacy run passed all 54 assertions across four server launches. The mixed-tier array retained 703,701 FE in three unique nodes joined by two edges. The isolated bank retained 2,345,678 FE through conversion, a normal restart, and a retry from its original 6.2 chunk alongside the converted graph. All placed tiers kept their disabled and push faces, display modes and shared redstone modes. The wired charger raised the copied legacy item from 78,912 to 88,913 FE and retained the other block data. All four servers exited zero. Forge reported the existing three registry mapping diagnostics on the initial conversion and no runtime ERROR or FATAL lines. See `build/port-runtime/cycle-064-verified-retry/legacy-migration-results.json`.
- The tested production jar has SHA-256 `e47483d992e5cb13b4f6cdf3d3a19335c76dcc1131e610ecd10eccacc72450d7`. The source 6.2.18-beta jar has SHA-256 `9d1471e6d4562d0a50f0564bc46573b87fd738bd440fe19397d31847da20f172`.

## Remaining

The headless placement test uses Forge's fake survival player. An actual TCP player's pickup and replant and the optional connected-texture rendering remain open. Continue with the guarded client and exact FE and side-setting checks after a real pickup and restart.
