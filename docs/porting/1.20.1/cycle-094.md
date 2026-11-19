# Cycle 094 — Clear stale bundled and vanilla redstone

## Scope

Use a real CC:Tweaked 1.121.0 computer beside an Ender IO redstone conduit. Route white and black channels across a chunk boundary and use the white channel for the analog output. Exercise independent lever changes, removal of a powered source, bridge removal and replacement, and a server restart. Test the same route with a vanilla lamp and CC:Tweaked absent.

## Changes

- Keep conduit counts consistent after removing a node that splits the network. The split callback already counts the nodes in each fragment. The later removal callback now counts the actual remaining nodes when the cache needs a rebuild.
- Compare the whole previous and current redstone signal maps. Removing the last connection for a channel now notifies bundled readers and redstone filters even when the channel has dropped out of the connection cache.
- Add a native Forge regression that verifies both fragments have one ticking redstone conduit after breaking the bridge and that the isolated lamp turns off.
- Add a packaged server fixture with an ordinary ComputerCraft computer. Its Lua startup script records the actual bundled and analog readings on each redstone event. [CC:Tweaked redstone API](https://tweaked.cc/module/redstone.html)

## Commands

```sh
./gradlew :enderio:assemble --console=plain > build/cycle-094-baseline-build.log 2>&1
./gradlew :enderio:runGameTestServer --console=plain > build/cycle-094-native-reproduction.log 2>&1
./gradlew build --continue --console=plain > build/cycle-094-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.93-1.20.1+c015542c56.jar
forge_server=build/port-runtime/cycle-047-legacy-discover
cc="$HOME/.gradle/caches/modules-2/files-2.1/maven.modrinth/cc-tweaked/Qm9iIIEY/739e77a2b60d727d8dcf7874188edf683683329d/cc-tweaked-Qm9iIIEY.jar"

python3 scripts/porting/packaged_cc_tweaked.py --name cycle-094-fixed --port 25856 \
  --java "$port_java" --forge-server "$forge_server" --jar "$port_jar" --cc-jar "$cc"
python3 scripts/porting/packaged_cc_tweaked.py --name cycle-094-core-fixed --port 25857 \
  --java "$port_java" --forge-server "$forge_server" --jar "$port_jar"
```

## Results

- The original jar reported `0 → 1 → 32,769 → 32,768 → 0` as the two levers switched on and off. Removing the bridge while both were active left the computer's mask at `32,769` and the analog input at 15, with no new redstone event. The control lamp also stayed lit after the same bridge removal without CC:Tweaked. See `build/port-runtime/cycle-094-before/cc-tweaked-results.json` and `build/port-runtime/cycle-094-core-before/cc-tweaked-results.json`.
- In a separate run on the original jar, removing the last black source left the computer's mask at `32,769` instead of `1`. The live bundle NBT confirmed that the black endpoint was gone and the bridge still connected. See `build/port-runtime/cycle-094-before-channel/`.
- The added native bridge regression was the one failure among 161 gameplay tests on the original code. See `build/cycle-094-native-reproduction.log`.
- The fixed build passed 79 unit tests and 483 native tests across 171 core, seven Mekanism, 117 registry, 161 gameplay and 27 addon cases. All 161 discovered gameplay tests ran. See `build/cycle-094-build.log`.
- Packaged Forge with CC:Tweaked passed 21 assertions, including 12 Lua observations. Removing the powered black source changed the bundled mask from `32,769` to `1` while analog strength stayed at 15. Breaking the bridge cleared both readings to zero, replacing it restored both channels, and reopening rebuilt the same mask from saved lever and conduit state. Lua also verified the white and black color queries, checked that red stayed off and read zero on the five other computer faces. See `build/port-runtime/cycle-094-fixed/cc-tweaked-results.json` and its `fresh-computer-trace.jsonl` and `reopened-computer-trace.jsonl`.
- The vanilla-lamp profile passed 20 assertions with CC:Tweaked absent. The lamp turned off after the bridge broke, came back after replacement and followed the white channel again after restart. The four final headless server processes exited at 0 without registry or runtime errors. See `build/port-runtime/cycle-094-core-fixed/cc-tweaked-results.json`.
- Production jar SHA-256 `06517d79e447d4ac5071352861f477f258248f0a873afb47bb4618d7c6ab9df4`. CC:Tweaked jar SHA-256 `a95ba58640db0198c86f0be99154af92fda17a78975c88aa45ffc430159a6fe9`.

## Remaining verification

The native computer test covers Ender IO bundled output with an ordinary CC:Tweaked computer. Dedicated chunk eviction and other bundled receiver types remain open.
