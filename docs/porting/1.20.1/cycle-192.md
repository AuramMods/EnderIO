# Cycle 192 — Stock soul-bound crystals and powders

Three dispensers capture a blue Shulker, a level-two Librarian and an Enderman. The fixture checks each captured Soul Vial against the original entity data. A vanilla hopper below each dispenser moves the vial directly to an adjacent Soul Binder, and a second hopper supplies the stock crystal or emerald. The XP Juice arrives through a native tank. All three binders wait at one millibucket below their stock cost and pause at a paid 12,300 FE after the tank supplies the final portion.

The first dedicated restart restores the captured entities, original ingredients and queued crystals and vials. Native hoppers carry the Prescient and Ender Crystals from the binders to the two SAG Mills. The empty vials stay in the delivery hoppers. The Enticing Crystal and its returned vial enter a barrel through their own hopper. The mills save one-quarter progress and each original crystal. After a second restart, each spends the remaining 1,800 FE and pushes one matching powder into its output hopper. The final reopen verifies the exact output counts and all five machines' resource reserves.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_forge="$port_runtime/cycle-047-legacy-discover"
port_jar=enderio/build/libs/enderio-8.2.14.191-1.20.1+a5accd0a5a.jar

./gradlew build --continue --console=plain > build/cycle-192-bound-crystals-build.log 2>&1
python3 -u scripts/porting/packaged_bound_crystal_progression.py --name cycle-192-bound-crystals-stock --port 26182 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar"
```

## Results

- The full build passes 79 unit tests with none skipped and 540 native runs, including all 213 discovered main gameplay cases. See `build/cycle-192-bound-crystals-build.log`.
- The packaged headless fixture passes 28 server assertions across four sessions, each with zero runtime or registry errors. The bindings spend 100,000 FE and 2,240 mB for Prescient, 51,200 FE and 800 mB for Enticing, and 76,800 FE and 1,440 mB for Ender. The final save retains 317 FE and 37 mB in each binder. The barrel holds one Enticing Crystal and one empty vial. Each mill spends 2,400 FE, retains 137 FE and keeps its returned vial in the delivery hopper. The output hoppers hold one Prescient Powder and one Ender Crystal Powder respectively.
- Forge loads production SHA-256 `ea29f2f953a48fa1d80a724cb8df994dfc790c9395b26e853dae9563e4b294e2`. See `build/port-runtime/cycle-192-bound-crystals-stock/bound-crystal-progression-results.json`.

## Next verification

Copy this fixture and use the original saved Enticing Crystal to craft and operate a stock Attractor Obelisk in survival.
