# Cycle 102 — Apply saved LaserIO filters in Ender IO conduits

## Scope

Exercise four cross-chunk routes on packaged Forge. Use loose LaserIO Basic Filters on item and fluid extraction and Basic Filters inside LaserIO item and fluid cards on insertion. Verify exact quantities, item and fluid NBT matching, restart, blank cards and the same routes with LaserIO absent. Use the native conduit menus to remove and reinstall the filter stacks over TCP before feeding the routes.

## Changes

- Read the saved slots through LaserIO 1.6.8's `FilterBasic.getInventory`. Its Forge handler constructor creates empty slots, even when given a configured stack. Both item and fluid adapters now inspect the actual persisted samples.
- Allow transfers through a card with no installed filter. A configured empty Basic Filter still follows its own allow or deny mode.
- Add packaged server checks with native barrels and Ender IO fluid tanks. The guarded client uses normal block interaction and menu slot clicks. The server verifies removal, reinsertion and the configured filter NBT before testing routing.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_laser="$HOME/.gradle/caches/modules-2/files-2.1/curse.maven/laserio-626839/5074767/f9e8dc07506eaa058f64e875e1f46dcd44ab6003/laserio-626839-5074767.jar"
port_patch="$HOME/.gradle/caches/modules-2/files-2.1/maven.modrinth/patchouli/8Qyw08ld/e91d2e2f251375d647d592b670ea6f310c2554eb/patchouli-8Qyw08ld.jar"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_before=build/port-runtime/cycle-101-fixed-client/mods/enderio.jar
port_jar=enderio/build/libs/enderio-8.2.14.101-1.20.1+8b9dd482b6.jar

python3 scripts/porting/packaged_laserio.py --name cycle-102-before --port 25887 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_before" \
  --laserio "$port_laser" --patchouli "$port_patch"

./gradlew :enderio:assemble --console=plain > build/cycle-102-assemble.log 2>&1
python3 scripts/porting/packaged_laserio.py --name cycle-102-fixed --port 25888 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" \
  --laserio "$port_laser" --patchouli "$port_patch"
python3 scripts/porting/packaged_laserio.py --name cycle-102-core --port 25889 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar"
./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-102-build.log 2>&1

port_client=build/port-runtime/cycle-102-client
port_prior=build/port-runtime/cycle-101-fixed-client
mkdir -p "$port_client"
cp -R "$port_prior/libraries" "$port_prior/versions" "$port_client/"
python3 scripts/porting/client/prepare_packaged.py --name cycle-102-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets" \
  --mod "$port_laser" --mod "$port_patch"
python3 scripts/porting/packaged_laserio.py --name cycle-102-menu --port 25890 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_client/mods/enderio.jar" \
  --laserio "$port_laser" --patchouli "$port_patch" --launch "$port_client/launch.json"
```

## Results

- The original packaged build loaded all four routes and their saved filters. A loose item allowlist held back its exact tagged match. An item card transferred all stacks, including five paper with the denied tag. The loose fluid allowlist held back 1,000 mB tagged water. The fluid card did pass a differently tagged sample, which was the expected result for that individual check. See `build/port-runtime/cycle-102-before/laserio-results.json` and `smoke-fresh.log`.
- The fixed integration profile passes 18 server assertions. Fluid receivers are emptied before rejection checks so that tank mixing cannot produce a false pass. Both denied fluids remain in the source after 50 observed game ticks. Replacing each card with an empty card releases the previously denied paper or water.
- The core profile passes 13 checks across the same unfiltered item and fluid routes, including repeated transfers after restart. The four server processes for the two final headless profiles exit at zero without runtime or registry errors. See `build/port-runtime/cycle-102-fixed/laserio-results.json` and `build/port-runtime/cycle-102-core/laserio-results.json`.
- The tested Ender IO jar SHA-256 is `1afd2ca265881af1cff691f5b6c45f4cb7a246bc2e83028f0ff2d022383cf69c`. LaserIO 1.6.8 is `f07fc6cb7f2aa28197e87ffc3a35062390259ad803d8996b08b13473c0af6790`. Patchouli 1.20.1-84.1-forge is `9cc38ebb5e29b350222e337b7a5a2ba238c802230a89434ce00e90ee3026d056`.
- The build passes all 79 unit tests and 483 Forge GameTest runs, including all 161 discovered gameplay tests. See `build/cycle-102-build.log`.
- The guarded multiplayer profile passes 14 client checks and 26 native server assertions. Each of the four tested filter slots opens on the correct conduit face. Survival inventory clicks remove and return the original stacks with their configured NBT. The same item and fluid transfers and rejection checks pass after that menu interaction and a dedicated-server restart. Ender IO, LaserIO and Patchouli hashes match between client and server. Both server processes exit at zero with empty runtime and registry error lists. See `build/port-runtime/cycle-102-menu/laserio-results.json`.
- The client recorded 1,551 native samples with zero activation, foreground or on-screen events. Narrator and every sound category were off. See `build/port-evidence/cycle-102-menu-client/summary.json` and `laserio-card-in-fluid-conduit.png` in the same directory.

## Remaining verification

Configuring filters in LaserIO's own editor and opening the Patchouli LaserIO guide still need packaged client coverage. This cycle exercises the Basic Filter and cards carrying it or carrying no filter.
