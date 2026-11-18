# Cycle 057 — migrate the primitive alloy smelter

The original Forge 6.2.18-beta release saved this fuel-fired machine under `enderio:primitive_alloy_smelter`. Its copper-and-silicon recipe cost 3,200 units of work and queued one copper alloy ingot. The port's powered machine uses iron and copper for two conductive alloy ingots. A real partially completed job reproduced the missing block on conversion with the cycle 056 jar. See `build/port-runtime/cycle-057-before/`.

The port restores the historical block, item, block entity, five-slot inventory, sided item handling and fuel and progress gauges. A live machine processes 20 units of work per tick with the default Stirling generator setting, accepts Forge furnace fuels and keeps container remainders such as a lava bucket's empty bucket. Active fuel continues burning while redstone blocks work, as in 6.2. New fuel is used when an alloy job needs more work.

A dedicated recipe type carries the 47 recipes from the original jar with their historical costs, yields and ingredient counts. It uses the port's registry IDs and tags, including the existing copper-to-conductive and industrial-insulation aliases. Forge silicon accepts Ender IO silicon through a tag reference and can also accept silicon registered by another Forge mod. Seven historical material IDs and their models are restored for recipe inputs, outputs and existing inventories.

Placed machines retain the burn duration, remaining fuel, work already done, pending output and inventory. The task importer maps the old recipe ID into the primitive recipe type and resolves the item IDs in the saved output through Forge. The port resumes the saved task and consumes inputs when it can place the output. The machine uses the retained 6.2 block and screen assets and the port's redstone and I/O controls.

## Reproduction

```sh
./gradlew :enderio:runData --console=plain
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_legacy=build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar
port_jar=enderio/build/libs/enderio-8.2.14.56-1.20.1+f753b863a1.jar

python3 scripts/porting/packaged_legacy_migration.py --name cycle-057-primitive-final --port 25691 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" --jar "$port_jar" --primitive-smelter

mkdir build/port-runtime/cycle-057-client-final
cp -R build/port-runtime/cycle-051-client-verified/libraries \
  build/port-runtime/cycle-051-client-verified/versions build/port-runtime/cycle-057-client-final/
python3 scripts/porting/client/prepare_packaged.py --name cycle-057-client-final --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/client/run_smoke.py --launch build/port-runtime/cycle-057-client-final/launch.json \
  --scenario primitive-smelter --timeout 235 --output build/port-evidence/cycle-057-hidden-smelter-final
```

Use fresh run directories, loopback ports and the jar built from the checkout under test. For the AE2 profile, replace the last flag with `--primitive-smelter ae2:silicon --mod "$port_ae2" --mod "$port_guide"`, using AE2 15.4.12 and GuideME 20.1.15 from [cycle 053](cycle-053.md).

## Results

- Datagen and the full build passed. There were 78 ordinary JUnit tests and 422 native GameTests with zero failures, errors or skips. The two new GameTests resolve all 47 recipes, exercise native lava-bucket fuel and its remainder, and verify that the machine accepts an alloy input and rejects vanilla smelting and external Forge Energy. The first build's fuel regression found that the item hook lacked Forge's fallback to vanilla furnace fuel. The final build uses `ForgeHooks.getBurnTime`. Logs are `build/port-evidence/cycle-057-{datagen,build}-final.log`.
- The core packaged profile passed 36 assertions. The original jar saved work at exactly 300 of 3,200. The port read that same value at conversion, after real chunk eviction and after the dedicated server restart. It retained the original 1,600-tick burn duration, both inputs, one tagged spare coal and the tagged loose machine stack. Re-enabling the machine deposited exactly one conductive alloy ingot and consumed both inputs. All three processes exited cleanly. First conversion reported three Forge diagnostics for remaining registry IDs and no other runtime errors. See `build/port-runtime/cycle-057-primitive-final/legacy-migration-results.json`.
- The AE2 profile passed the same 36 assertions using actual `ae2:silicon` from AE2 15.4.12 with GuideME 20.1.15 in both releases. The first attempt stopped at Forge's missing mandatory GuideME dependency. With both pinned jars installed, all three servers exited cleanly and conversion reported only the same remaining registry diagnostics. See `build/port-runtime/cycle-057-primitive-ae2-final/legacy-migration-results.json`.
- The hidden packaged client passed 16 checks. The client baked the eight block states and item model, opened the native five-slot menu, shift-clicked coal and ingredients into the machine, received the flame and progress values, and collected its one ingot. Both 960×540 captures show the expected menu state. The guard recorded 1,939 samples, zero activation or foreground events, zero visible-window samples and exit code zero. Narrator was OFF and all ten sound channels were at zero in both captures. There were zero ERROR or FATAL log entries on the final client run. See `build/port-evidence/cycle-057-hidden-smelter-final/`.
- The distributable SHA-256 is `20f1e8e1e0ae19eff62dc037c31176a8918e5de49ad34c069669919779d24e79`. The separate client QA jar SHA-256 is `39491b2334248fa79e6f6f3dfb745de0843ed42a5d1393bea3d0a52fa4c4a182`.

## Remaining historical coverage

The primitive smelter is retained for upgraded worlds. Its old survival crafting recipe and older material production outside its alloy recipes are outside the restored path. JEI display of the primitive recipe type, saved jobs from custom 6.2 datapacks and the menu over dedicated multiplayer remain unverified. Further legacy IDs still appear in the Forge conversion log, including six historical enchantments.

The vanilla fuel regression also identified the same item-hook calls in the Stirling generator. Its normal fuel insertion and energy generation need a dedicated runtime check.
