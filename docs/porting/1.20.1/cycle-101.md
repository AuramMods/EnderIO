# Cycle 101 — Keep paid Crafter outputs on the carried block

## Scope

Mine a working Crafter in survival while a paid cake and three bucket remainders are waiting. Pick up the Crafter, place it again over TCP, restart the server, and extract the paid goods through a vanilla hopper.

## Changes

- Store the pending output queue in typed item NBT when the Crafter drops. Placement restores that queue in order using the existing Forge item-data hook. Placed machines still use the existing `OutputBuffer` save field. Carried machines without a queue key can still load ordinary inventory or older `BlockEntityTag` data.
- Reuse the guarded survival carry client and the packaged cake fixture. The server verifies the actual drop, player inventory, placement and saved machine data separately.

## Commands

```sh
# First run with the cycle 100 production source and cycle 101 client QA.
./gradlew :enderio:assemble :enderio:reobfClientSmokeJar --console=plain > build/cycle-101-baseline-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.100-1.20.1+8a97873815.jar
port_qa=enderio/build/test-libraries/enderio-client-qa.jar
port_forge=build/port-runtime/cycle-047-legacy-discover
port_prior=build/port-runtime/cycle-100-core-client

run_carry() {
  profile="cycle-101-$1-client"
  profile_dir="build/port-runtime/$profile"
  mkdir -p "$profile_dir"
  cp -R "$port_prior/libraries" "$port_prior/versions" "$profile_dir/"
  python3 scripts/porting/client/prepare_packaged.py --name "$profile" --java "$port_java" \
    --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
    --qa-jar "$port_qa" --assets "$HOME/.gradle/caches/neoformruntime/assets" &&
  python3 scripts/porting/packaged_crafter_carry.py --name "cycle-101-$1" --port "$2" \
    --java "$port_java" --forge-server "$port_forge" --jar "$profile_dir/mods/enderio.jar" \
    --launch "$profile_dir/launch.json"
}

run_carry before 25883

# Repeat with the Crafter fix applied.
./gradlew :enderio:assemble :enderio:reobfClientSmokeJar --console=plain > build/cycle-101-fixed-assemble.log 2>&1
run_carry fixed 25884
./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-101-build.log 2>&1
python3 scripts/porting/packaged_crafter.py --name cycle-101-placed --port 25885 \
  --java "$port_java" --forge-server "$port_forge" \
  --jar build/port-runtime/cycle-101-fixed-client/mods/enderio.jar

./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-101-final-qa.log 2>&1
profile_dir=build/port-runtime/cycle-101-fixed-client
python3 scripts/porting/client/prepare_packaged.py --name cycle-101-fixed-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar "$port_qa" --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_crafter_carry.py --name cycle-101-verified --port 25886 \
  --java "$port_java" --forge-server "$port_forge" --jar "$profile_dir/mods/enderio.jar" \
  --launch "$profile_dir/launch.json"
```

## Results

- The baseline Crafter consumed exactly 3,200 FE and produced the cake and three queued buckets. Native survival mining dropped one Crafter with the cake, template, preview, capacitor, redstone, I/O and 3,200 FE. The drop lacked the queued buckets. The test failed at `native_drop_preserves_the_paid_queue_and_all_machine_data`. See `build/port-runtime/cycle-101-before/smoke-fresh.log` and `crafter-carry-results.json` in the same directory.
- The first patched run recorded all three buckets on the native dropped stack and in survival player inventory. The client test used the Forge energy capability, which the carried Crafter does not expose. The client assertion now reads the Crafter's stored energy data. Forge also logged slow GLFW initialization during the first patched launch. See `build/port-runtime/cycle-101-fixed/crafter-carry-results.json`.
- The full build passes 79 JUnit tests and 483 native Forge GameTests, with all 161 discovered gameplay tests reconciled. The packaged Forge 47.4.28 jar has SHA-256 `6129c62442ffe46a508e87bfebe8c3081670cfb89281fda43cddfafef20ff1ed`. See `build/cycle-101-build.log`.
- The final carry test passes 10 dedicated-server assertions and five client checks. No loose items remain after pickup. Survival placement consumes the carried stack and restores the cake, all three queued buckets, template, preview, capacitor, controls and 3,200 FE. The first restart preserves the queue. A vanilla hopper then exports one cake and three buckets to a barrel. The second restart keeps those exact counts, the empty queue and the same charge. See `build/port-runtime/cycle-101-verified/crafter-carry-results.json`.
- The run recorded 1,901 native samples with zero activation, foreground or on-screen events. All volume categories were at zero and narrator was off. See `build/port-evidence/cycle-101-verified-client/summary.json` and `crafter-replaced.png` in the same directory.
- The separate packaged placed-machine regression passes all 14 assertions for native crafting, hoppers, paid outputs and live recipe reload on the same jar. The six dedicated-server processes across both scenarios exit at zero without runtime or registry errors. See `build/port-runtime/cycle-101-placed/crafter-results.json`.

## Remaining verification

Other JEI machine recipe transfers and packaged optional integrations still need coverage. LaserIO filter and card behavior is a remaining integration case.
