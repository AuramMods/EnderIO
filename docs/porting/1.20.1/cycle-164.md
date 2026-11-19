# Cycle 164 — Matching energy, fluid and chemical tiers and split networks

The packaged dedicated server disables tier mixing for energy, fluid, chemical and item conduits. For each resource route, a Forge fixture uses native survival placement to install two basic conduits across a chunk boundary. It replaces each end with the middle tier and then the highest tier, checking that each replacement refunds one conduit of the original variant. The mixed states retain the next full resource batch at the source and the matching states deliver it.

A separate five-piece item route uses a barrel pair on each half. Replacing the middle piece creates two independent basic conduit networks. Each half must deliver its own tagged paper with the other half active. The fixture also checks both routes after actual chunk eviction and dedicated restart.

The original packaged jar installs both basic parts in each resource route and connects their outer faces to the endpoints, but leaves all six inner faces disconnected. The item upgrade creates the expected split topology, but only one side delivers its batch. The new graph from the split was unregistered, so the server never ticked it. The fixed types allow connections to their exact counterpart under the respective tier settings. Replacement now registers the new networks using the same callback as ordinary conduit removal.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_legacy=build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar
port_art="$HOME/.gradle/caches/modules-2/files-2.1/net.neoforged/AutoRenamingTool/2.0.17/9388df4c3589faf10c35d9154cfb2451c83bd717/AutoRenamingTool-2.0.17-all.jar"
port_mek=build/port-runtime/cycle-142-controls-final/mods/mekanism-uxe1WQp4.jar
port_probe=build/port-runtime/cycle-164-tier-probe/mekanism-probe.jar
port_before=enderio/build/libs/enderio-8.2.14.162-1.20.1+5133205a65.jar
port_jar=enderio/build/libs/enderio-8.2.14.163-1.20.1+bc7b6923a3.jar

python3 scripts/porting/legacy_fixture/build.py --java "$port_java" --forge-server "$port_forge" \
  --legacy-jar "$port_legacy" --art "$port_art" --mekanism-probe --mod "$port_mek" \
  --output build/port-runtime/cycle-164-tier-probe > build/cycle-164-probe.log 2>&1
python3 scripts/porting/packaged_conduit_tier_lock.py --name cycle-164-before --port 26084 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_before" --mekanism "$port_mek" --probe "$port_probe" \
  > build/cycle-164-before.log 2>&1

./gradlew build --continue --console=plain > build/cycle-164-fix-build.log 2>&1
python3 scripts/porting/packaged_conduit_tier_lock.py --name cycle-164-fixed --port 26085 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --mekanism "$port_mek" --probe "$port_probe" \
  > build/cycle-164-fixed.log 2>&1
```

## Results

- The original jar fails four assertions, one for each resource family. The saved halves show the west source still holding eight tagged paper and the east receiver holding all eight paper from its own source. The original artifact has SHA-256 `eecd312fdb1593948dcc0119827f3e96ec0119ccda39661684ccc1fbcd3fddac`.
- The full build passes 79 unit cases and 529 native runs, including 202 main gameplay cases. The fixed packaged server passes all 31 assertions across two clean starts on Forge 47.4.28 with Mekanism 10.4.16.80. The test runs headless.
- In each mixed state the source keeps its full batch for at least 24 ticks. Matching the other side releases exactly 12,000 FE, 450 mB of NBT-tagged water or 1,450 units of hydrogen. The restarted routes retain their original balances and accept one new batch each, reaching 48,000 FE, 1,800 mB and 5,800 units respectively. Both isolated item halves end with 24 paper of their own tag after actual chunk eviction, reload and restart.
- The packaged artifact has SHA-256 `0434ac954dc32d22b8ae47b6935c0454069f7bd92217211fb2859df609e63bd0`. Both server sessions exit normally with zero runtime or registry errors. The before and after `conduit-tier-lock-results.json` reports and server logs are in `build/port-runtime/cycle-164-before/` and `build/port-runtime/cycle-164-fixed/`.

## Next verification

Reconcile loaded recipe and registry IDs with the fixed baseline. Establish which remaining progression paths require a port change or a runtime test.
