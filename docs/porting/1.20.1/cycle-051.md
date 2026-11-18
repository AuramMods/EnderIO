# Cycle 051 — migrate carried fluid tanks from Forge 6.2

Ender IO 6.2.18-beta kept portable fluid in the tank item's `BlockEntityTag`. Its equipped capability wrote flat Forge fluid stacks under `Fluid.Tanks`. The older standalone `Fluid` layout can hold a single stack directly. The port could already read these formats from placed machines. Its item capability previously saw only the current item data key, so it reported a filled legacy item as empty before placement.

Both tank items now use the same saved-fluid decoder as the placed machine. Current item data takes precedence. Reading and simulating leave the original stack intact. An executed transfer writes the current item value and retires the legacy tank-zero entry. Separate saved tank records and non-fluid block metadata survive. Tank placement normalizes a copy of legacy `BlockEntityTag` before vanilla merges it into a freshly serialized block entity. This keeps default empty `Fluids` and `IOConfig` fields from masking the old values. Applying typed item state afterward preserves inventory and I/O already loaded from the carried tag when those fields have no typed override.

The original fixture uses three item stacks. The legacy server saves actual tank block entities into their items, including seven diamonds, pull mode on the east face and active-with-signal redstone control. It fills those items using the old Forge capability and writes the barrel to disk. The first and third items hold 11,500 mB of water with a typed tag. The middle item holds 21,750 mB of tagged XP Juice, which uses its original fluid registry ID. All three source tag and metadata assertions pass. The preceding port fails `untouched_old_fluid_visible_in_item` on the connected client. Evidence is under `build/port-runtime/cycle-051-before` and `build/port-evidence/cycle-051-before-converted`.

## Reproduction

```sh
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain
python3 scripts/porting/legacy_fixture/build.py \
  --java /Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java \
  --forge-server build/port-runtime/cycle-047-legacy-discover \
  --legacy-jar build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar \
  --art /Users/cyberpwn/.gradle/caches/modules-2/files-2.1/net.neoforged/AutoRenamingTool/2.0.17/9388df4c3589faf10c35d9154cfb2451c83bd717/AutoRenamingTool-2.0.17-all.jar \
  --output build/port-runtime/cycle-051-fixture
python3 scripts/porting/client/prepare_packaged.py \
  --name cycle-051-client-verified \
  --java /Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java \
  --installer build/port-runtime/forge-47.4.28-installer.jar \
  --jar enderio/build/libs/enderio-8.2.14.50-1.20.1+afd81ebedf.jar \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar \
  --assets /Users/cyberpwn/.gradle/caches/neoformruntime/assets
python3 scripts/porting/client/run_carried_tank_migration.py \
  --name cycle-051-verified --port 25658 \
  --java /Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java \
  --forge-server build/port-runtime/cycle-047-legacy-discover \
  --legacy-jar build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar \
  --legacy-fixture build/port-runtime/cycle-051-fixture/fixture.jar \
  --jar enderio/build/libs/enderio-8.2.14.50-1.20.1+afd81ebedf.jar \
  --launch build/port-runtime/cycle-051-client-verified/launch.json
```

All runs use official Forge 47.4.28 and Temurin 17.0.18. The disposable client reused official libraries and version metadata from cycle 050. It can also be created with `prepare_packaged.py --install`. The converted server receives a copy of the closed source world and runs the bundled port jar alone.

The build passes **78 ordinary JUnit and 415 native GameTests**. Main gameplay has 96 discovered and executed cases. The added native case tests direct and indexed carried formats, simulation purity, current item data precedence, separate tank records and exact full drainage followed by an `ItemStack` save and reload. Build evidence is in `build/port-evidence/cycle-051-final-build.log`.

The first implementation restored the fluid in the held item. The placement assertion timed out and a subsequent diagnostic showed why. Vanilla accepted the use packet and placed the block with its seven diamonds and redstone setting. Its fluid and I/O fields were empty because the freshly serialized defaults took precedence during merge. The diagnostic shows the native block state in `build/port-runtime/cycle-051-diagnostic/converted/smoke-converted.log`. The final implementation also normalizes the carried tag before placement.

The final packaged run passed **19 client checks and 18 server assertions**. The untouched standard tank places with its 11,500 mB of tagged water. The pressurized item gives 1,000 mB of tagged XP Juice to the prefilled receiver, which reaches 32,000 mB, and places with the remaining 20,750 mB. The third item transfers all 11,500 mB to its empty receiver and places empty, including after restart. Each placed block keeps its seven diamonds, east pull mode and active-with-signal setting. The client sees the exact tagged fluid in each item and block after the actual use packets. After restart, independent server assertions and a fresh connection confirm all five persisted tanks. The source barrel is empty.

The final report is `build/port-runtime/cycle-051-verified/carried-tanks-results.json`. Per-client results, captures and native focus reports are under `build/port-evidence/cycle-051-verified-{converted,restart}`. The observers record 1,056 and 1,000 samples with zero activation, foreground or onscreen events. Both clients stay muted with narration off. All owned processes exit cleanly. Forge reports the four known missing-registry diagnostics during conversion and no other runtime ERROR/FATAL entries. The client and server use the same bundled jar with SHA-256 `992e30aab1d9f56896c03286efb23b2e362f43d82ad0a5c7638af334f9259e15`. The separate client QA jar is `75b0cc55416ff6dec247f2d5adce77c0ff6d9c2d24a901fa00f0290fecae7f1c`.

Historical conduit items and upgrades, other machine item formats and full feature parity remain open.
