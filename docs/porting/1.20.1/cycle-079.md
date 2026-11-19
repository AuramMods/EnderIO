# Cycle 079 — Farming Station soul rules

## Scope

Exercise the published Farming Station Soul Binder recipe, fertilizer savings and Fortune bonuses. Check rule replacement and removal on a placed station and restore the bound soul after a dedicated server restart.

## Changes

- Register the Farming Station item's soul-binding capability so the Binder can store the captured soul in its output.
- Resolve the bound soul's current rule when the station fertilizes or harvests. Persist direct binding changes and accept an empty soul for clearing the machine. A missing rule lets the station use the usual harvesting and fertilizer behavior while retaining the saved soul.
- Supply a temporary loot-context item for hand harvests with a Fortune bonus. Merge the bonus into a copy of an equipped tool's enchantments so Fortune reaches loot conditions and the equipped tool keeps its own enchantments and tags.
- Require the station's capacitor before scanning. Keep its baseline scan cadence and FE consumption. Extract consumed fertilizer through the inventory handler so the slot change reaches ordinary save and sync paths.

## Commands

```sh
./gradlew :enderio:verifyGameplayTests --console=plain
./gradlew build --continue --console=plain
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
python3 scripts/porting/packaged_farming_station_souls.py --name cycle-079-souls-reproduction --port 25797 \
  --java "$port_java" --forge-server build/port-runtime/cycle-047-legacy-discover \
  --jar enderio/build/libs/enderio-8.2.14.77-1.20.1+dfcf56c159.jar
port_jar=enderio/build/libs/enderio-8.2.14.78-1.20.1+8c8b521127.jar
python3 scripts/porting/packaged_farming_station_souls.py --name cycle-079-souls-verified --port 25797 \
  --java "$port_java" --forge-server build/port-runtime/cycle-047-legacy-discover --jar "$port_jar"
python3 scripts/porting/packaged_farming_station.py --name cycle-079-conservation --port 25798 \
  --java "$port_java" --forge-server build/port-runtime/cycle-047-legacy-discover --jar "$port_jar"
```

## Results

- The four original Forge failures showed an unchanged fertilizer count after a rule replacement, loss of fertilizer savings immediately after loading NBT, an exception on clearing a binding and a wheat harvest without a capacitor. See `build/cycle-079-reproduction-results.xml`.
- The prior production jar spent 188,000 FE and 1,100 mB of experience fluid. Its Binder returned an empty vial and a Farming Station with no soul data. The packaged regression caught the missing output binding. See `build/port-runtime/cycle-079-souls-reproduction/smoke-fresh.log`.
- The final full build passed 79 unit tests and 461 required Forge GameTests with zero failures or skips. The native counts were 171 core, seven Mekanism, 117 registry, 139 gameplay and 27 addon. The four new cases passed. See `build/cycle-079-final-build.log`.
- The packaged soul fixture passed all 11 assertions, with zero runtime or registry errors and both server exits at 0. The published recipe bound the bee and returned an empty vial while spending 188,000 FE and 1,100 mB of XP. The fixture copied the produced item's soul into the placed station's native block data. Authored loot tables made the two Fortune levels measurable. The live station preserved all four units of fertilizer and produced three seeds with the bonus. It harvested two oak logs using Fortune III in the loot context while its equipped axe kept Fortune I and took one durability damage. Replacing, restoring and removing the bee rule changed the next harvest. Native block loot carried the bee, and the item and placed soul survived a server restart. See `build/port-runtime/cycle-079-souls-verified/farming-station-souls-results.json`.
- The unchanged crop conservation fixture passed all eight assertions again on this cycle's production jar. It used vanilla wheat loot to verify a full-output stall, replanting, hopper extraction and two restarts. See `build/port-runtime/cycle-079-conservation/farming-station-results.json`.
- Both packaged suites used Forge 47.4.28 and Java 17 with the production jar as the only installed mod. Its SHA-256 is `5ffb7c1797674447bcb563b3a4986af3daa03dde5d3b1183dcc23718dc9dc298`.

## Remaining verification

Next, verify multiblock crop and connected tree harvesting, especially when a full output or a broken tool interrupts a harvest. Client controls, chunk unloading and in-world pickup and placement also remain open. The published villager rule defines `power` as data. Energy use in this cycle follows the 1.21.1 scheduler.
