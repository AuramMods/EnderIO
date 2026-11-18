# Cycle 050 — preserve historical travel staff charge

Ender IO 6.2.18-beta stores handheld energy as `tag.Energy.EnergyStored`. The older server charged a real travel staff through its Forge energy capability and saved it to a barrel. Its first native console assertion found exactly 6,000 FE. Opening a copy with the pre-change port reproduced the failed charge check on the connected client.

The storage provider now reads that balance for the travel staff, levitation staff and electromagnet. Current typed NBT takes precedence, including a stored zero. Reading and simulating preserve the original tag. A real transfer writes the current balance and removes the recognized old charge fields so an emptied item cannot revive its previous balance. The new native test checks an old balance above the current capacity, simulation, the current value's precedence, exact extraction across two transfers, persistence of zero and preservation of unrelated NBT. Packaged runtime coverage in this cycle is for the travel staff.

## Commands

```sh
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain
python3 scripts/porting/legacy_fixture/build.py \
  --java /Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java \
  --forge-server build/port-runtime/cycle-047-legacy-discover \
  --legacy-jar build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar \
  --art /Users/cyberpwn/.gradle/caches/modules-2/files-2.1/net.neoforged/AutoRenamingTool/2.0.17/9388df4c3589faf10c35d9154cfb2451c83bd717/AutoRenamingTool-2.0.17-all.jar \
  --output build/port-runtime/cycle-050-fixture
python3 scripts/porting/client/prepare_packaged.py \
  --name cycle-050-client \
  --java /Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java \
  --installer build/port-runtime/forge-47.4.28-installer.jar \
  --jar enderio/build/libs/enderio-8.2.14.49-1.20.1+146cb8ae24.jar \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar \
  --assets /Users/cyberpwn/.gradle/caches/neoformruntime/assets
python3 scripts/porting/client/run_travel_migration.py \
  --name cycle-050-verified --port 25654 \
  --java /Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java \
  --forge-server build/port-runtime/cycle-047-legacy-discover \
  --legacy-jar build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar \
  --legacy-fixture build/port-runtime/cycle-050-fixture/fixture.jar \
  --jar enderio/build/libs/enderio-8.2.14.49-1.20.1+146cb8ae24.jar \
  --launch build/port-runtime/cycle-050-client/launch.json
```

The isolated client reused official client libraries and version metadata from cycle 049. It can also be created with `prepare_packaged.py --install`. Both packaged peers use Minecraft 1.20.1, Forge 47.4.28 and Temurin 17.0.18. The old fixture mod and the client observer are separate from the bundled distribution.

The native build passes **78 ordinary JUnit and 414 GameTests**. The new test adds one to the EnderCore native suite, which now has 171 tests. The other native suites have seven Mekanism, 115 main registry, 95 main gameplay and 26 addon cases. The final full build log is `build/port-evidence/cycle-050-final-build.log`, and `cycle-050-verified-client-build.log` records the rebuild after adjusting the test-only aim. The first version of the native test expected a 150 FE extraction in one operation despite a 100 FE transfer limit. The corrected test verifies both the 100 FE transfer and the final 50 FE drain.

The pre-change runtime failure is under `build/port-runtime/cycle-050-before` and `build/port-evidence/cycle-050-before-converted`. The next run reached the workshop and charged one use, then timed out waiting for the predicted blink position. The player file in `cycle-050-first/converted/port-travel/playerdata` records the actual server landing at `(56.5, 80, 0.5)` and 4,000 FE, showing the blink succeeded. The fixture now aims through the same platform block from both standing and crouching eye heights and asserts that exact landing on both peers.

The final run passed **44 client checks and 29 server assertions**. A survival player uses the migrated staff from ordinary ground to reach the painted workshop and then blinks while crouching. The dedicated server independently confirms each position and each 1,000 FE charge. The balance goes from 6,000 to 5,000 to 4,000 FE. A new client joins after server restart and uses the same staff twice more, reading 4,000, 3,000 and 2,000 FE. The client sees the cooldown and waits for its expiry before blinking. The source barrel is empty after the fixture moves the staff into the player's inventory. The existing anchor menu, horizontal route, hidden elevator and Nether target checks also pass in both sessions.

All owned processes exited. The two final clients have zero activation, foreground or onscreen observations across 1,145 and 1,223 samples. Both clients and all three servers have zero runtime ERROR/FATAL entries apart from Forge's four known registry diagnostics during conversion. The console repositioning steps still produce movement warnings when the client sends a position immediately after a fixture teleport. Native focus, sound and narrator checks pass in all final captures.

The final report is `build/port-runtime/cycle-050-verified/travel-migration-results.json`. Client results, focus reports and captures are under `build/port-evidence/cycle-050-verified-{converted,restart}`. The matching client and server distribution SHA-256 is `262bc2ba1722094f21092f2bd4e1aeeb548c9d6b06ad4e544a3bfbf171878280`. The separate client QA jar is `bec938d403d8c271478318acb7e0d226dba757cf73d42d2690e9f1981ac34102`.

The levitation staff and electromagnet use the same charge adapter and still need packaged gameplay verification. Other 6.x migration gaps remain, including carried tanks with fluid stored under their old `BlockEntityTag` and conduit speed upgrades.
