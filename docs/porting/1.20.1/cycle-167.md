# Cycle 167 — Reconcile custom data and preserve unchanged crafting jobs on reload

The packaged probe serializes each of the three loaded EnderCore data maps and four loaded Ender IO soul tables through their production codecs. The audit compares the decoded values with the fixed 1.21.1 baseline, resolves any source tag selectors against live Forge tags and compares soul entity IDs with the real Forge registry.

The packaged gameplay fixture then reloads a different antenna range and a cow capture rule. A stock SAG Mill with a partially worn flint ball and a custom SAG Mill both have 600 FE paid before the first reload. A powered spawner also has part of an original Cow capture paid. After the first reload the two unchanged mill jobs still have work to do. The next reload changes the custom mill recipe's output and energy cost under the same ID, which must invalidate the old task and preserve the unconsumed input.

Forge creates new recipe instances on every reload. The previous port compared object identity in the powered crafting task. Both mills lost their 600 FE and queued products even though they were still using the same recipe definitions. The task now rebinds an unchanged recipe using the same serialized representation as the saved-task check. A different or removed recipe still invalidates the old task. Inventory updates that prompt the task host to look for a recipe use the same comparison.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_before=enderio/build/libs/enderio-8.2.14.165-1.20.1+29f2bfb9a2.jar
port_jar=enderio/build/libs/enderio-8.2.14.166-1.20.1+9d04391939.jar
port_art="$HOME/.gradle/caches/modules-2/files-2.1/net.neoforged/AutoRenamingTool/2.0.17/9388df4c3589faf10c35d9154cfb2451c83bd717/AutoRenamingTool-2.0.17-all.jar"
port_probe=build/port-runtime/cycle-167-data-probe/registry-probe.jar

python3 scripts/porting/legacy_fixture/build.py --java "$port_java" --forge-server "$port_forge" \
  --legacy-jar "$port_before" --art "$port_art" --registry-probe \
  --output build/port-runtime/cycle-167-data-probe > build/cycle-167-data-probe.log 2>&1
python3 scripts/porting/packaged_registry_audit.py --name cycle-167-stock --port 26097 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_before" --probe "$port_probe" \
  > build/cycle-167-stock.log 2>&1
python3 scripts/porting/packaged_rule_reload.py --name cycle-167-before --port 26098 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_before" --probe "$port_probe" \
  > build/cycle-167-before.log 2>&1

./gradlew build --continue --console=plain > build/cycle-167-recipe-task-build.log 2>&1
python3 scripts/porting/packaged_rule_reload.py --name cycle-167-verified --port 26100 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --probe "$port_probe" \
  > build/cycle-167-verified.log 2>&1
```

## Results

- The packaged stock snapshot includes two range extenders, nine grinding balls, 15 VAT reagent entries, six Soul Engine rules, three Farming Station rules, one solar soul rule and 81 spawner rules. Every decoded value matches its baseline definition. The spawner rules retain Armadillo, Bogged and Breeze entries. The 1.20.1 entity registry has no matching types for those three rules.
- The before run proves both mills hold 600 FE of paid work, the new antenna range is applied and the spawner retains its 4,000 FE old task when the pack reloads. It then fails the assertion that both mills retain their jobs. The server log captures the idle stock mill and its still-present coal.
- The fixed package passes all 21 headless assertions. Both mills retain their 600 FE after the data-only reload. The stock mill preserves its original powder roll and finishes with exactly 1,440 more FE and 2,040 total flint wear. A second reload changes the custom recipe and clears its old task with one Golden Carrot still in the input. The new recipe spends exactly 1,200 FE and produces one Iron Nugget.
- The placed charger changes its range from 48 to 24 to 36 without removing its advanced antenna. The stock Cow job uses exactly its remaining 6,000 FE and captures a fresh soul. The next job adopts the authored 14,000 FE cost and copy mode. It pays 4,000 FE before restart and 10,000 FE after, then outputs one tagged vial containing the original entity data. The reloaded charger range also survives restart.
- The full build passes 79 unit cases and 529 native runs, including 202 main gameplay cases. Both packaged server sessions exit normally with zero runtime or registry errors. The production jar has SHA-256 `aee9ea9201da8c0b5a2dc54700738932ab33850810cf85e3828a811e169cb82e`. The stock, reloaded and replaced snapshots and the 21 assertions are recorded under `build/port-runtime/cycle-167-verified/`. The original failure and saved world are under `build/port-runtime/cycle-167-before/`.

## Next verification

Check remaining packaged client surfaces with the guarded launch, including item model variants and recipe or tooltip display that the server inventory cannot exercise.
