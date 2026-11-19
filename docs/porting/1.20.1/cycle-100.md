# Cycle 100 — Preserve tagged ingredients in JEI Crafter templates

## Scope

Use the actual JEI transfer button in the packaged Crafter screen over TCP. Transfer a shaped stick recipe with birch planks in the player's inventory, then replace it with a shapeless Forge recipe requiring tagged paper and redstone. Shift-click in the physical ingredients, take the output and reopen the saved world. Repeat the tagged recipe using manual ghost slots in a client with JEI absent.

## Changes

- Keep the matching stack's NBT when Forge's ingredient matcher requires it. Ordinary ingredients still use a one-item template without incidental item data. The fallback copies JEI's advertised ingredient with a count of one.
- Add a guarded client exercise that uses JEI's rendered recipe and real transfer button, plus a manual input exercise on the same native menu without JEI. The server independently checks the exact ghost grid, real ingredients, output and energy.

## Commands

```sh
./gradlew :enderio:assemble --console=plain > build/cycle-100-baseline-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-100-qa.log 2>&1
./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-100-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.99-1.20.1+fc5a6142be.jar
port_qa=enderio/build/test-libraries/enderio-client-qa.jar
port_jei="$HOME/.gradle/caches/modules-2/files-2.1/maven.modrinth/jei/9jqubC9n/7f64b7f8fde7f001ef054dabe3618a8780ba2650/jei-9jqubC9n.jar"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_prior=build/port-runtime/cycle-098-fixed-client

for mode in fixed core; do
  profile="cycle-100-$mode-client"
  profile_dir="build/port-runtime/$profile"
  mkdir -p "$profile_dir"
  cp -R "$port_prior/libraries" "$port_prior/versions" "$profile_dir/"
  set --
  if [ "$mode" = fixed ]; then set -- --mod "$port_jei"; fi
  python3 scripts/porting/client/prepare_packaged.py --name "$profile" --java "$port_java" \
    --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
    --qa-jar "$port_qa" --assets "$HOME/.gradle/caches/neoformruntime/assets" "$@"
done

python3 scripts/porting/packaged_crafter_menu.py --name cycle-100-fixed --port 25880 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" \
  --launch build/port-runtime/cycle-100-fixed-client/launch.json --jei
python3 scripts/porting/packaged_crafter_menu.py --name cycle-100-core --port 25881 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" \
  --launch build/port-runtime/cycle-100-core-client/launch.json
python3 scripts/porting/packaged_crafter_menu.py --name cycle-100-fixed-serial --port 25882 \
  --java "$port_java" --forge-server "$port_forge" \
  --jar build/port-runtime/cycle-100-fixed-client/mods/enderio.jar \
  --launch build/port-runtime/cycle-100-fixed-client/launch.json --jei
```

Both dedicated servers use the core Ender IO jar. The original jar and failed JEI run are preserved in `build/port-runtime/cycle-100-before`.

## Results

- The original JEI transfer passed the shaped birch recipe. The shapeless transfer sent plain paper to the server and cleared the Crafter preview. The player's actual paper stack still had the required tag. Manual ghost slots worked with the same tagged recipe and JEI absent. See `build/port-runtime/cycle-100-before/smoke-fresh.log` and `build/port-runtime/cycle-100-core-before/crafter-menu-results.json`.
- The full build passes 79 JUnit and 483 native Forge GameTests. Both final packaged profiles use Forge 47.4.28 and the production jar with SHA-256 `4c3e53795d2bd677d1ca7e70b4023255478a606594c6590afe6c4e192934a314`.
- JEI 15.56.0.205 transferred the shaped recipe using the player's birch planks and retained the exact paper tag on the shapeless template. The packaged TCP test passes 14 client checks and six server assertions. The manual profile with JEI absent passes 13 client checks and five server assertions. Each profile crafted and collected exactly one amethyst shard for 3,200 FE, preserved the other ingredients, and kept the expected template, preview and charge after a full restart.
- The first patched JEI attempt passed all gameplay checks. Forge logged slow GLFW initialization as an error, so the driver stopped before the restart stage. The serial run completed both stages with no runtime errors. Final reports are `build/port-runtime/cycle-100-fixed-serial/crafter-menu-results.json` and `build/port-runtime/cycle-100-core/crafter-menu-results.json`. Both corresponding client evidence directories under `build/port-evidence/` contain the two menu captures. The clients recorded zero activation, foreground or on-screen samples. Both captures in each profile show narrator off and all volume categories at zero.

## Remaining verification

The Crafter's survival carry-and-place path still needs gameplay coverage. Other machine recipe transfers still need interaction tests.
