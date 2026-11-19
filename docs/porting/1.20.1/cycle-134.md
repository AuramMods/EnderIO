# Cycle 134 — Verify the Drain menu and survival crafting

The Drain screen now draws its range value in menu-relative coordinates. The container renderer already positions the view at the menu origin. The previous code applied the screen offset a second time and drew the number outside the panel.

The packaged fixture copies the official Forge 6.2.18 beta world produced by cycle 133 into a disposable dedicated server. A real survival client unlocks the Drain from the Void Chassis advancement and crafts it through the vanilla recipe-book and output slots. The same client tests the live Drain controls and recipes, resets an actual filled Drain drop, then places both the reset Drain and the drop from the Forge 6 archive.

## Commands

```sh
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-134-drain-qa.log 2>&1
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-134-drain-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_source=build/port-runtime/cycle-132-steel-fixed-client
old_jar=build/port-runtime/cycle-133-drain-current/port/mods/enderio.jar
fixed_jar=enderio/build/libs/enderio-8.2.14.133-1.20.1+c45dbc78fc.jar
legacy_world=build/port-runtime/cycle-133-drain-legacy-exact/legacy/port-spawners

run_drain_profile() {
  profile_name=$1
  production_jar=$2
  game_port=$3
  game_directory="build/port-runtime/$profile_name-client"
  mkdir "$game_directory"
  cp -R "$port_source/libraries" "$port_source/versions" "$game_directory/"
  python3 scripts/porting/client/prepare_packaged.py --name "$profile_name-client" --java "$port_java" \
    --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$production_jar" \
    --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
  python3 scripts/porting/packaged_drain_menu.py --name "$profile_name" --port "$game_port" \
    --java "$port_java" --forge-server "$port_forge" --jar "$production_jar" \
    --launch "$game_directory/launch.json" --legacy-world "$legacy_world"
}
run_drain_profile cycle-134-drain-render-before "$old_jar" 25989
run_drain_profile cycle-134-drain-verified "$fixed_jar" 25990
```

## Results

- The original jar changes the range from zero to one through normal menu packets. The capture shows `1` over the world outside the panel while the right-hand range label region remains unchanged. That is the only failure among 27 client checks. All ten server assertions run. See `build/port-evidence/cycle-134-drain-render-before/drain-menu-one.png` and `summary.json`.
- The corrected jar passes all 27 client checks and 13 server assertions. The two screenshots show distinct `0` and `1` values beside the range controls. The plus and minus buttons both update the server range. The visibility toggle produces a three-block range overlay centered at the expected position below the Drain. Changing redstone mode in the open menu starts collection. The client sees the active model state and the exact change from 1,375 to 2,375 mB of lava and from 4,827 to 3,327 FE. All eight block states and the inventory model are present.
- The server confirms the native survival recipe consumes four conductive alloy ingots, two iron gears, one clear glass, one Void Chassis and one bucket for one Drain. The factory reset consumes one genuine filled Drain drop and returns a clean stack. Native placement restores an empty tank and capacitor slot, zero energy and progress, and default controls. Placing the original archived Forge 6 drop retains its 1,000 mB of lava, capacitor, visible radius of one, redstone settings and 1,327 FE payment. An independent server restart verifies the operated, reset and migrated machines. Both dedicated processes exit normally with zero runtime or registry errors. See `build/port-runtime/cycle-134-drain-verified/drain-menu-results.json`.
- The full build passes all 79 unit tests and 502 native Forge runs, including all 180 main gameplay tests. Both client runs record zero activation, foreground or onscreen events. All five corrected captures have narrator off and all ten volume channels at zero. The corrected client and server use SHA-256 `f95723f0a09d9fe0482613148f65eb344be3459a65a123eedd562d0311b9f97f`. See `build/port-evidence/cycle-134-drain-verified/summary.json` and the captured screens.

## Remaining verification

The Drain's maximum-radius scan across chunks and source pickup from optional mods remain open. The Vacuum Chest and XP Vacuum have separate pending feature rows.
