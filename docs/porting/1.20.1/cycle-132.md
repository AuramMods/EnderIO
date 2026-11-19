# Cycle 132 — Verify steel construction and the Dark Steel ladder

The ladder's speed hint now appears on the Dark Steel Ladder item. The previous handler attached it to the solid Dark Steel storage block.

Native Forge tests place the door, trapdoor and both bars from survival item stacks. Redstone sent to the upper door half opens both halves. The tests also check hand interaction, one door drop, bar connections and waterlogging, the material recipes, and Reinforced Obsidian's explosion and wither protection.

The packaged client measures climbing, descent and crouching on the vanilla and Dark Steel ladders, and the dedicated server checks the reported heights. The same profile bakes the six blocks' 201 states and six inventory models. A separate run places all six blocks in the official Forge 6.2 release and verifies them from a copied save on the port.

## Commands

```sh
./gradlew :enderio:runGameTestServer --console=plain > build/cycle-132-steel-native.log 2>&1
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-132-steel-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-132-steel-recorded-qa.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_source=build/port-runtime/cycle-131-levers-client
old_jar=build/port-runtime/cycle-131-verified/server/mods/enderio.jar
fixed_jar=enderio/build/libs/enderio-8.2.14.131-1.20.1+5de841cf1f.jar
prepare_profile() {
  profile_name=$1
  production_jar=$2
  profile="build/port-runtime/$profile_name"
  mkdir "$profile"
  cp -R "$port_source/libraries" "$port_source/versions" "$profile/"
  python3 scripts/porting/client/prepare_packaged.py --name "$profile_name" --java "$port_java" \
    --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$production_jar" \
    --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
}
prepare_profile cycle-132-tooltip-before-client "$old_jar"
prepare_profile cycle-132-steel-fixed-client "$fixed_jar"
python3 scripts/porting/packaged_steel_construction.py --name cycle-132-verified-before --port 25991 \
  --java "$port_java" --forge-server "$port_forge" --jar "$old_jar" \
  --launch build/port-runtime/cycle-132-tooltip-before-client/launch.json
python3 scripts/porting/packaged_steel_construction.py --name cycle-132-verified --port 25992 \
  --java "$port_java" --forge-server "$port_forge" --jar "$fixed_jar" \
  --launch build/port-runtime/cycle-132-steel-fixed-client/launch.json
python3 scripts/porting/packaged_steel_construction.py --name cycle-132-legacy-steel --port 25989 \
  --java "$port_java" --forge-server "$port_forge" --jar "$fixed_jar" \
  --legacy-jar build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar
```

## Results

- The full build passes all 79 unit and 497 native Forge cases. All 175 discovered main gameplay cases execute. The four steel cases verify the recipes produce 12 ladders, 16 Dark Steel Bars, three doors, one trapdoor, 12 End Steel Bars and one Reinforced Obsidian. The normal Forge wither destruction gate rejects Reinforced Obsidian but allows its dirt control. A native explosion removes the control and leaves the reinforced block intact. The registered mining tags reject iron and accept a diamond pickaxe.
- The before jar passes both ladder movement paths and bakes all 201 state models. It fails the two tooltip checks. The ladder lacks the hint and the solid Dark Steel block shows it. The fixed packaged run passes 21 client checks and 21 server assertions, with the hint shown only on the ladder. See `build/port-evidence/cycle-132-verified-before/summary.json` and `build/port-runtime/cycle-132-verified/steel-construction-results.json`.
- In 12 sampled ticks, the fixed build climbs 2.898 blocks on Dark Steel versus 0.948 on vanilla. It descends 3.378 versus 1.578 blocks and holds position while crouching. The server's height agrees within 0.375 blocks for all five trials. The paired server sessions also preserve open powered doors and joined waterlogged bars across restart, then close both opening blocks after power is removed. Both servers exit zero with no runtime or registry errors.
- The verified packaged capture shows both ladder textures. The guarded client records zero activation, foreground or onscreen events in 1,667 observations. All ten volumes are zero and the narrator is off in the capture. The client and server use the same production SHA-256 `4fa7a808d1cd4b4b44ebf477a9af7b2d4886d0abfab855bd42870b7f7365f699`. See `build/port-evidence/cycle-132-verified`.
- The archived Forge 6.2.18 beta fixture and its converted copy pass all 16 assertions. The six block IDs and loot load in the old jar. After conversion the powered opening blocks retain both door halves and the trapdoor's state. The bars retain their join and water, and both opening blocks close when their power is removed. Both server sessions exit zero with no runtime or registry errors. See `build/port-runtime/cycle-132-legacy-steel/steel-construction-results.json`.
- A further headless reopen of both saved fixtures passes four checks each. The Dark Steel Ladder retains its facing at both tested elevations, the bars retain their connection and water, and Reinforced Obsidian stays in place. These checks use `load` and `preserved_blocks` from the fixture runner and are recorded in `saved-block-probe.json` in both run directories.

## Remaining verification

The client verifies baked Reinforced Obsidian models. Its ambient particles still need a live client check. The ladder tests use the default configured boost.
