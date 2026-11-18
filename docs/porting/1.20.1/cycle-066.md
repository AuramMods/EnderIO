# Cycle 066 — bind a Phantom in the packaged Soul Binder

The packaged server runs the shipped vibrant photovoltaic recipe. The disposable fixture starts with a plain module, a Phantom soul vial and an octadic capacitor. Hoppers supply the items, an adjacent fluid tank supplies XP, and the initial 36,781 FE budget stops the job partway through. The first restart resumes the existing job. The complete recipe must spend exactly 288,000 FE and 5,600 mB, produce one bound module and return one empty vial.

The first packaged run spent the expected FE and returned the correct items, but left 620 mB of XP fluid from a 6,237 mB input. Converting the remaining XP to whole points discarded the final 17 mB. The binder now drains the fluid equivalent of the recipe's XP level cost directly. The existing Forge Soul Binder GameTest now uses a non-integral point balance and asserts the exact fluid remainder.

## Reproduction

Use the official Forge 47.4.28 server libraries installed at `cycle-047-legacy-discover` and the [guarded macOS client harness](client-harness.md). Use fresh names when repeating the fixture.

```sh
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_jar=enderio/build/libs/enderio-8.2.14.65-1.20.1+684fd08421.jar
python3 scripts/porting/packaged_soul_binder.py --name cycle-066-tank-hopper --port 25729 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar"
mkdir build/port-runtime/cycle-066-client
cp -R build/port-runtime/cycle-065-client/libraries build/port-runtime/cycle-065-client/versions \
  build/port-runtime/cycle-066-client/
python3 scripts/porting/client/prepare_packaged.py --name cycle-066-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_soul_binder.py --name cycle-066-tcp --port 25730 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" \
  --launch build/port-runtime/cycle-066-client/launch.json
```

## Results

- The baseline used the cycle 065 production jar and direct fluid seeding. It passed the exact FE and product checks and failed the remainder check. Both servers exited cleanly. See `build/port-runtime/cycle-066-baseline/soul-binder-results.json` and `smoke-finish.log` in that profile.
- The fixed headless automation profile passed seven assertions. A neighboring tank supplied 5,599 mB and both hoppers inserted their items. The job waited for the next fluid transfer, saved exactly 36,781 FE of progress and resumed after restart. It kept 637 mB and 317 FE after completing. The output hopper extracted the module and the empty vial. Both servers exited zero with no ERROR or FATAL lines. See `build/port-runtime/cycle-066-tank-hopper/soul-binder-results.json`.
- The TCP profile passed 14 server assertions and eight client checks. The native menu showed the saved progress, the 14-level requirement, the exact fluid and FE, both inputs and both outputs. The player shift-clicked the actual crafted module, then placed it through normal block use. At fixed midnight the bound module showed 256 FE after placement and 1,344 FE after reopening. The unbound control stayed at zero. The binder retained its 637 mB, 317 FE and empty vial after the second restart. All three servers exited zero with no ERROR or FATAL lines. See `build/port-runtime/cycle-066-tcp/soul-binder-results.json`.
- The guarded client rendered both menu states at 960×540. The client exited zero with no ERROR or FATAL lines. Narrator was OFF, all ten sound categories were muted and the cursor stayed uncaptured. The native watcher recorded 3,076 samples with zero activation, foreground or visible-window events. See `build/port-evidence/cycle-066-tcp-client/`.
- The build passed 78 unit tests and 430 native Forge GameTests, including the precise fluid remainder assertion added to the existing Soul Binder test. The test XML reports recorded zero failures, errors or skips. See `build/cycle-066-build.log` and the modules' `build/test-results/test/`.

| Artifact | SHA-256 |
| --- | --- |
| Baseline production jar | `4a26e802727d0cf5e8d02e9dca80ccffcadb88042b448937ced6c89de604e184` |
| Fixed production jar | `2f4cdcb1a7491f2c82163c00eb66fb6bd176099487448f2194bd76f8700bb8b3` |
| Test-only client probe | `1d6b906f08c2c44956ad71d23bbf19ccebd712606d6b986ce2396981cc5c027b` |

## Remaining verification

The input soul vial is seeded with normal item data. Live capture and release, additional Soul Binder recipe families and historical 6.2 binder state still need packaged verification.
