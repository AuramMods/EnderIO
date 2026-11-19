# Cycle 103 — Protect Ender IO slots from Inventory Sorter

## Scope

Run the pinned Forge release of Inventory Sorter in a packaged survival multiplayer session. Verify its middle click really sorts a vanilla barrel. Use the same installed input handler to check machine, template, preview, enchanting output, spawner vial and filter slots. Exercise the same menus with the dependency absent.

## Dependency

Inventory Sorter 23.0.11 is the Forge 1.20.1 release at `curse.maven:inventory-sorter-240633:7188685`, SHA-256 `987bfb57aaaad23189c9747cb7177b8b20208f5fa1f23a09e442ed7711136263`. The mod is loaded in disposable test profiles. Ender IO uses Forge IMC for optional registration. [Publisher release](https://www.curseforge.com/minecraft/mc-mods/inventory-sorter/files/7188685)

## Changes

- Add the concrete Powered Spawner vial slot class to the optional blacklist. Inventory Sorter compares exact class names and already recognizes the base machine slot name and the explicitly registered Enchanter output, ghost and preview classes.
- Verify the installed Inventory Sorter mouse handler on real screen slots with Forge input events inside the guarded client. The dedicated server checks the barrel and protected inventories independently of the client event result.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_sorter=build/port-runtime/cycle-103-inputs/inventorysorter-1.20.1-23.0.11.jar
mkdir -p build/port-runtime/cycle-103-inputs
curl --fail --silent --show-error --location --retry 2 \
  'https://www.cursemaven.com/curse/maven/inventory-sorter-240633/7188685/inventory-sorter-240633-7188685.jar' \
  -o "$port_sorter"
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-103-qa.log 2>&1

port_forge=build/port-runtime/cycle-047-legacy-discover
port_qa=enderio/build/test-libraries/enderio-client-qa.jar
port_prior=build/port-runtime/cycle-102-client

prepare_client() {
  name="$1"
  jar="$2"
  shift 2
  profile_dir="build/port-runtime/$name"
  mkdir -p "$profile_dir"
  cp -R "$port_prior/libraries" "$port_prior/versions" "$profile_dir/"
  python3 scripts/porting/client/prepare_packaged.py --name "$name" --java "$port_java" \
    --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$jar" --qa-jar "$port_qa" \
    --assets "$HOME/.gradle/caches/neoformruntime/assets" "$@"
}

prepare_client cycle-103-before-client "$port_prior/mods/enderio.jar" --mod "$port_sorter"
port_client=build/port-runtime/cycle-103-before-client
python3 scripts/porting/packaged_inventory_sorter.py --name cycle-103-before --port 25891 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_client/mods/enderio.jar" \
  --launch "$port_client/launch.json" --sorter "$port_sorter"
python3 scripts/porting/packaged_inventory_sorter.py --name cycle-103-before-retry --port 25892 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_client/mods/enderio.jar" \
  --launch "$port_client/launch.json" --sorter "$port_sorter"

./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-103-build.log 2>&1
port_jar=enderio/build/libs/enderio-8.2.14.102-1.20.1+78483ff455.jar
prepare_client cycle-103-fixed-client "$port_jar" --mod "$port_sorter"
prepare_client cycle-103-core-client "$port_jar"

port_client=build/port-runtime/cycle-103-fixed-client
python3 scripts/porting/packaged_inventory_sorter.py --name cycle-103-fixed --port 25893 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_client/mods/enderio.jar" \
  --launch "$port_client/launch.json" --sorter "$port_sorter"
port_client=build/port-runtime/cycle-103-core-client
python3 scripts/porting/packaged_inventory_sorter.py --name cycle-103-core --port 25894 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_client/mods/enderio.jar" \
  --launch "$port_client/launch.json"
```

## Results

- The first baseline connection stopped during Forge login before any gesture. The server sent all 30 login messages and the connection ended without a player joining. The retry used the same packaged client and installed mods on a fresh test world and joined successfully. See `build/port-runtime/cycle-103-before/logs/debug.log`.
- The baseline retry sorted a vanilla barrel into one 14-cobblestone stack. Both spawner vial slots accepted the sort and scroll gestures, and the server observed changed vial inventory. The Crafter input, ghost and preview slots, Enchanter output, and three held filter screens passed. See `build/port-runtime/cycle-103-before-retry/inventory-sorter-results.json` and `build/port-evidence/cycle-103-before-retry-client/summary.json`.
- The full build passes 79 unit tests and 483 native Forge GameTest runs. All 161 discovered gameplay tests executed. The Forge 47.4.28 production jar SHA-256 is `889c04ef5156dfa10ff74cdaadedbc195a8aa1b9fc242abf756dafda08f58480`. See `build/cycle-103-build.log`.
- Both packaged profiles pass 33 client checks and 16 server assertions. With Inventory Sorter present, the barrel combines the original two cobblestone stacks into one stack of 14. With the dependency absent, the stacks remain in their original slots. The Crafter's charge, physical input, template, preview and output stay in place. The Enchanter keeps all three inputs and its Sharpness preview. The spawner retains both original vials, including the filled vial's entity data. All three held filters keep their exact item or fluid data. The placed machine inventories and barrel state pass again after restart. See `build/port-runtime/cycle-103-fixed/inventory-sorter-results.json` and `build/port-runtime/cycle-103-core/inventory-sorter-results.json`.
- All four final dedicated server processes exit at zero with empty runtime and registry error lists. Both guarded clients report zero activation, foreground and on-screen samples across 2,171 measurements. Narrator and every volume category remain off. The client captures and reports are in `build/port-evidence/cycle-103-fixed-client` and `build/port-evidence/cycle-103-core-client`.

## Remaining verification

The Enchanter fixture verifies its prepared preview and protects it from sorting. Collecting the result, paying its XP and ingredients, and resuming a ready job after restart still need end-to-end verification. The intermittent Forge login interruption recorded in the first baseline attempt remains open.
