# Cycle 260 — Forge 6.2 Soul Binder recipes and paid jobs

Forge 6.2 datapacks use `exp` and `souldata` in Soul Binder recipes. The port now reads those fields when the current equivalents are absent. Recipe encoding still uses `experience` and `soul_data`, so the saved recipe snapshot and packets use the current representation. The current fields take precedence when both spellings exist.

The fixture starts the released Forge 6.2.18-beta jar in a disposable world. Dispensers capture a named saddled pig, two named Blazes and a named colored Shulker. Hoppers feed those vials to three stock recipes and one original-format custom recipe. The Broken Spawner input comes from native mining loot for a cow spawner. The original server pays 6,000 FE for each job and saves their queued products and empty vials. The custom recipe uses a Blaze Rod and the original Soul Engine eligibility rule to make a bound engine at a distinct FE and XP cost.

The copied world keeps the original datapack. After conversion and cold reopen, each job has the same payment, input inventory, XP and queued products. The fixture supplies all but one FE, confirms neither output was released, then supplies the last FE. Each hopper must contain exactly one product and one empty vial after another restart. The custom recipe also receives a converted pig vial in a separate binder that already has enough power, XP and the correct item. The pig must stay unconsumed without starting a job.

| Fixture | Original energy | XP Juice | Expected hopper output beside the empty vial |
| --- | ---: | ---: | --- |
| Broken Spawner | 288,000 FE | 2,240 mB | One Broken Spawner with the captured named, saddled pig |
| Soul Engine | 188,000 FE | 1,100 mB | One Soul Engine with the captured named Blaze |
| Prescient Crystal | 100,000 FE | 2,240 mB | One Prescient Crystal from the captured Shulker and Vibrant Crystal |
| Custom Soul Engine | 35,600 FE | 800 mB | One Soul Engine with its own captured named Blaze |

## Commands

```sh
export JAVA_HOME="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
export CI=true JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=4
./gradlew --no-daemon --no-build-cache --no-configuration-cache --max-workers=2 --console=plain :enderio:assemble
./gradlew --no-daemon --no-build-cache --no-configuration-cache --max-workers=2 --console=plain check --continue

export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Djava.net.preferIPv4Stack=true'
port_evidence=build/port-evidence/cycle-260-historical-binding
port_legacy=build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar
port_forge=build/port-runtime/cycle-047-legacy-discover
python3 -B scripts/porting/packaged_legacy_soul_binder.py --name cycle-260-original-soul-binding-before --port 26387 \
  --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" --legacy-jar "$port_legacy" \
  --jar "$port_evidence/enderio-before.jar"
python3 -B scripts/porting/packaged_legacy_soul_binder.py --name cycle-260-original-soul-binding-fixed --port 26388 \
  --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" --legacy-jar "$port_legacy" \
  --jar "$port_evidence/enderio-fixed.jar"
python3 -B scripts/porting/packaged_legacy_soul_binder.py --stock-only --name cycle-260-stock-soul-binding-fixed --port 26389 \
  --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" --legacy-jar "$port_legacy" \
  --jar "$port_evidence/enderio-fixed.jar"
python3 -B scripts/porting/packaged_registry_audit.py --name cycle-260-core-registry --port 26390 \
  --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" --jar "$port_evidence/enderio-fixed.jar" \
  --probe build/port-runtime/cycle-165-audit-probe/registry-probe.jar
```

## Results

- With the previous port, the original Forge recipe begins and saves its 6,000 FE job. The copied world reports `No key experience` when parsing the original datapack and loses the custom task. All three stock tasks open with their original payment. See `build/port-runtime/cycle-260-original-soul-binding-before/{legacy-soul-binder-results.json,port/smoke-converted.log}`.
- The original fixture needed the legacy tank list size and a hopper that could pull directly from the dispenser. An early copy test also observed only 15 seconds of processing, shorter than the 27 seconds needed for the remaining stock Broken Spawner work at 540 FE per tick. The final fixture submits budgets together and observes the resulting machine state.
- The fixed jar passes 34 packaged assertions with the original datapack and 25 in the stock-only control. Each binder waits with one FE outstanding and retains 40 mB of XP Juice after completion. The custom recipe rejects the migrated pig vial after 40 server ticks. All eight headless stages exit cleanly with zero registry or runtime errors. See `build/port-runtime/cycle-260-{original,stock}-soul-binding-fixed/legacy-soul-binder-results.json`.
- Java 17 assembly and the full CI check pass. The XML audit records 79 unit cases, 548 native runs and all 180 baseline methods, with zero failures, errors or skips. The packaged registry audit finds all 1,222 applicable baseline recipes with their expected serializers. The fixed runtime SHA-256 is `3e08053553f541b8f6b3f679ee2a7e239798823a1dae6fcce972e84a8f519515`. See `build/port-evidence/cycle-260-historical-binding/{build-fixed.log,check.log,xml-audit.json,registry.log}`.

## Next verification

Exercise a stock VAT batch under a different redstone mode while its reagents and sided output remain available. [Completion audit](completion-audit.md)
