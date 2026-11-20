# Cycle 203 — stock dungeon Loot Capacitors

Native hoppers open three vanilla simple-dungeon loot tables using fixture seeds 2, 4 and 30. The shipped Global Loot Modifier supplies one original Loot Capacitor for each selected seed. A plains village chest with seed 30 provides a vanilla loot control. The harness reads the original typed stacks from the flushed region file and moves those exact stacks into machine input hoppers.

Two Stirling Generators receive the fuel-efficiency roll and the combined capacity and generation roll. Each burns one stick while one FE short of the predicted capacity. After restart, another stick burns with 137 FE initially stored in each buffer. The test compares the exact burn durations and total generated FE against the original roll. The SAG Mill receives the energy-use roll. Two functions scheduled three game ticks apart sample the mill's actual energy and task progress. The job saves with 1,700 of its 2,400 FE paid, then finishes with the exact remaining payment after restart. The final closed save checks each installed stack against its original full NBT.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.202-1.20.1+607c14a5f6.jar

./gradlew build --continue --console=plain > build/cycle-203-loot-capacitor-build.log 2>&1
python3 -u scripts/porting/packaged_loot_capacitors.py --name cycle-203-loot-capacitors-stock --port 26206 \
  --java "$port_java" --forge-server build/port-runtime/cycle-047-legacy-discover --jar "$port_jar"
```

## Results

- The full build passes 79 unit tests with none skipped and 540 native runs, including all 213 main gameplay cases. See `build/cycle-203-loot-capacitor-build.log`.
- A discovery run opened 48 seeded dungeon chests with native hoppers and found eleven typed Loot Capacitors. The final three seeds cover all four specializations. Every selected chest produced exactly one capacitor. The plains village control produced ordinary vanilla loot. See `build/cycle-203-capacitor-seeds.log` and `build/port-runtime/cycle-203-capacitor-seeds/seed-results.json`.
- The packaged run passes 36 assertions across three headless sessions. The three original items retain their entire NBT after installation and after the final restart. The mill uses 888 FE over three measured ticks, saves with 1,700 FE paid and finishes on the original 2,400 FE budget. Both generators consume one stick per measured burn and retain their exact final stored FE.

| Dungeon seed | Rolled specialization | Measured machine behavior |
| --- | --- | --- |
| 2 | Energy use | The SAG Mill spends 296 FE per tick and exports one powdered lapis. |
| 4 | Fuel efficiency | The Stirling Generator caps at 432,737 FE and generates 4,680 FE over a 45-tick stick burn. |
| 30 | Energy capacity and burning energy generation | The Stirling Generator caps at 778,208 FE and generates 7,315 FE over a 55-tick stick burn. |

All sessions load production SHA-256 `a07fb774f1f678cd4401cdeb40c03f6fdcaddb32ab6018d6ef8b37a49e6bbd04` and exit cleanly with zero runtime or registry errors. See `build/port-runtime/cycle-203-loot-capacitors-stock/loot-capacitor-results.json`.

## Next verification

Verify the stock Soularium, Wither Skeleton skull and Wither Rose grinds with exact power and preserved rolls. Review the baseline use of Confusion Powder to distinguish its shipped registration and presentation from any implemented production route.
