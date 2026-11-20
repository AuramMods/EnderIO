# Cycle 273 — Historical stock Soul Binder jobs

The fixture now exercises every shipped Soul Binder recipe in both Forge 6.2.0-beta and 6.2.18-beta. The ten shared definitions are byte-identical. The later release adds Powered Spawner rebinding. Its fixture mines a cow-bound Powered Spawner and feeds that native drop into the binder with a named pink sheep captured by a dispenser. The Broken Spawner uses native cow-spawner mining loot and a named saddled pig. Each remaining stock case uses its original recipe input and a separate named capture.

Each original server saves after paying 6,000 FE per job. The copied world must retain the recipe ID, both input stacks, original XP Juice, paid work and two queued outputs before and after a cold restart. The fixture supplies all but one of the remaining FE, confirms the outputs are still queued and then supplies the last FE. A hopper collects the exact result and one empty vial. Another restart verifies the hopper and the machine's 40 mB XP Juice remainder. The eight unbound stock recipes also check that the product carries no captured soul.

| Recipe | Capture | Energy | XP Juice |
| --- | --- | ---: | ---: |
| Animal Token | Cow | 12,800 FE | 140 mB |
| Monster Token | Zombie | 12,800 FE | 140 mB |
| Player Token | Villager | 12,800 FE | 140 mB |
| Ender Crystal | Enderman | 76,800 FE | 1,440 mB |
| Enticing Crystal | Villager | 51,200 FE | 800 mB |
| Frank 'N' Zombie | Zombie | 51,200 FE | 800 mB |
| Sentient Ender | Witch | 51,200 FE | 800 mB |
| Powered Spawner | Pink Sheep | 288,000 FE | 2,240 mB |
| Broken Spawner | Saddled Pig | 288,000 FE | 2,240 mB |
| Soul Engine | Blaze | 188,000 FE | 1,100 mB |
| Prescient Crystal | Yellow Shulker | 100,000 FE | 2,240 mB |

The 6.2.18-beta profile also repeats the original-format custom Soul Engine recipe from [cycle 260](cycle-260.md). Its separate incorrect-soul control retains the pig vial, material, energy and XP Juice without starting a job.

## Commands

```sh
export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Djava.net.preferIPv4Stack=true'
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_forge="$port_runtime/cycle-047-legacy-discover"
port_jar=build/port-evidence/cycle-272-release/source-local/enderio/build/libs/enderio-8.2.14.271-dev+a00893d995.jar
python3 -Bu scripts/porting/packaged_legacy_soul_binder.py --name cycle-273-original-jobs --port 26434 \
  --java "$port_java" --forge-server "$port_forge" \
  --legacy-jar "$port_runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar" --jar "$port_jar"
python3 -Bu scripts/porting/packaged_legacy_soul_binder.py --name cycle-273-early-jobs --port 26435 --early-6-2 --stock-only \
  --java "$port_java" --forge-server "$port_forge" \
  --legacy-jar "$port_runtime/legacy-artifacts/EnderIO-1.20.1-6.2.0-beta-all.jar" --jar "$port_jar"
```

## Results

- The 6.2.18-beta profile passes 98 assertions and the 6.2.0-beta profile passes 81. All 22 jobs finish at the exact remaining energy budget and keep their outputs across restart. They consume 2,013,200 FE and 22,720 mB of XP Juice in total. The later Powered Spawner output preserves the named sheep and its color. All eight headless sessions exit at zero with zero registry or runtime errors.
- Both profiles use the packaged [cycle 272](cycle-272.md) runtime with SHA-256 `58803ec07dc520e3da7ec4dc413ba04dc78db24b524aa20435e00f8d2cbf98bd`. The original release digests are `9d1471e6d4562d0a50f0564bc46573b87fd738bd440fe19397d31847da20f172` for 6.2.18-beta and `64cd7ca4b094470ba425c849787ca40b984de1f65aedfd9fa166a421fcc57ec3` for 6.2.0-beta.
- The catalog comparison reconciles the original recipe IDs, costs and outputs with the executing cases. Per-phase assertions and logs are in `build/port-runtime/cycle-273-{original,early}-jobs/legacy-soul-binder-results.json`. The reconciled cases and artifact hashes are in `build/port-evidence/cycle-273-legacy-binding/{catalog.json,results-verified.json}`.

## Next verification

Exercise all seven stock silverfish bindings with the current Forge recipe set. Verify the zero-XP jobs, input matching, saved partial payment and infested-stone outputs. [Completion audit](completion-audit.md)
