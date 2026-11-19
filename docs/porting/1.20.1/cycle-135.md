# Cycle 135 — Conserve Vacuum Chest items and XP Vacuum points

The Vacuum Chest applies the active filter to the original item stack, inserts only the allowed amount into its 27 storage slots, and updates the original ground entity with the unaccepted items. The filter control slot is reserved for configuration. The server owns inventory and fluid updates while the client can still animate attraction.

The XP Vacuum counts all members of a vanilla merged orb, converts whole points at 20 mB each, and preserves the tag on stored XP Juice. When capacity allows only part of an orb, the untouched members preserve their denomination and a separate orb retains the partial balance. Both machines restore Forge 6 `Range` and `RangeVisible` in placed saves and promote old carried range and redstone settings to item data.

## Commands

```sh
./gradlew :enderio:runGameTestServer --console=plain > build/cycle-135-vacuums-before.log 2>&1
./gradlew build --continue --console=plain > build/cycle-135-vacuums-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_jar=enderio/build/libs/enderio-8.2.14.134-1.20.1+073882456a.jar
legacy_jar=build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar
python3 scripts/porting/packaged_vacuums.py --name cycle-135-vacuums-current --port 25991 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar"
python3 scripts/porting/packaged_vacuums.py --name cycle-135-vacuums-legacy --port 25992 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --legacy-jar "$legacy_jar"
```

## Results

- The before run discovers 185 gameplay tests and fails all five new cases. A stock filter discards seven surplus tagged items. A full Vacuum Chest takes a dropped filter into its control slot. A merged group of three seven-point orbs credits only one member. A near-capacity tagged tank rejects otherwise compatible XP and the old range falls back to the new default. The full build passes all 185 main gameplay tests and 507 native Forge executions across five profiles. It also passes all 79 unit cases with zero failures or skips.
- The packaged port passes nine assertions across two headless servers. Redstone gates both machines. The Vacuum Chest takes three of ten tagged ingots to reach a stock limit of four. After restart, a sided barrel drains the chest and the remaining seven ground items are picked up and delivered. The barrel receives all eleven tagged ingots, including the one already in storage. The mismatched and out-of-range items stay in the world, and the installed filter remains in its slot.
- Two of 21 XP points fit in the tagged tank near capacity. The original entity retains two seven-point orbs and a separate five-point orb retains the balance. The remaining 7 mB of capacity stays free. The tank and both entities survive restart. An independent XP Vacuum collects all 21 points and delivers exactly 420 mB to its neighbor. The tested jar has SHA-256 `b7e70dd91d420c193abe19dedefb6646ce6fd31b6bfebb7d780bf07a2d361b53`. See `build/port-runtime/cycle-135-vacuums-current/vacuum-results.json`.
- The official Forge 6.2.18 beta server produces real collected inventory, XP Juice and both machine drops. A copied save retains the original visible ranges, installed filter, redstone mode and stored amounts. The converted Vacuum Chest collects three additional matching ingots and leaves five mismatched ones on the ground. The converted XP Vacuum collects two more seven-point orbs, taking its stored amount from 140 to 420 mB. Both original drops and the updated machines survive restart. All 13 migration assertions pass. All five headless processes finish with exit code zero and no runtime or registry errors. See `build/port-runtime/cycle-135-vacuums-legacy/vacuum-results.json`.

## Remaining verification

The Vacuum Chest and XP Vacuum still need a guarded multiplayer pass for their screens, range controls and overlays, normal crafting, factory reset and survival placement of the archived Forge 6 drops.
