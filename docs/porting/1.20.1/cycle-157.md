# Cycle 157 — Restore active Forge 6.2 SAG Mill grinding balls

The Forge 6.2.18-beta SAG Mill stores its active grinding ball by recipe ID and saves wear as energy spent. The port already recovered the queued work and inventory, but discarded the active ball and wear. The migration restores the modifiers for all ten stock recipe IDs from the released Forge 6.2.18-beta data. It converts a known active ball to the current snapshot format before the saved recipe resumes. An old copper-alloy ball uses its historical 0.8 power multiplier while the tagged, unused spare resolves to the current conductive item.

An unsupported custom ball ID and its stored wear remain in block and carried-item data. Forge 6.2 saved the ID alone, so a custom ball's modifiers are unavailable without its original recipe definition. The mill uses no active modifiers for an unknown ID. A native GameTest verifies the exact stock copper data, modern snapshot precedence and preservation of the unrecognized record on export.

The packaged test runs the original release on Forge to grind the first coal in each of three mills. It uses a native loot table to take one mill as an item while the next job has 600 FE paid. It copies that world to the port, checks both placed mills and the old loot, then reopens the save. Original flint and copper mills use native output hoppers. A muted packaged client inspects the converted copper mill, places the original carried mill in survival, and observes both jobs complete over a real TCP connection.

## Commands

```sh
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-157-sag-final-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_legacy=build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar
port_before=enderio/build/libs/enderio-8.2.14.155-1.20.1+18a13a4fb5.jar
port_jar=enderio/build/libs/enderio-8.2.14.156-1.20.1+f1a8bf1a30.jar
port_client=build/port-runtime/cycle-157-sag-client
port_previous=build/port-runtime/cycle-156-sag-client
python3 scripts/porting/packaged_legacy_sag.py --name cycle-157-sag-before --port 26052 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" --jar "$port_before" \
  > build/cycle-157-sag-before.log 2>&1
python3 scripts/porting/packaged_legacy_sag.py --name cycle-157-sag-headless --port 26053 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" --jar "$port_jar" \
  > build/cycle-157-sag-headless.log 2>&1
mkdir -p "$port_client/config"
cp -cR "$port_previous/libraries" "$port_previous/versions" "$port_client/"
cp "$port_previous/config/fml.toml" "$port_client/config/fml.toml"
python3 scripts/porting/client/prepare_packaged.py --name cycle-157-sag-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_legacy_sag.py --name cycle-157-sag-client-run --port 26055 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" --jar "$port_jar" \
  --launch "$port_client/launch.json" > build/cycle-157-sag-client-run.log 2>&1
```

## Results

- The baseline production jar restores the flint mill's 600-of-2,040 FE job and tagged spare, with no active ball or wear in the converted block NBT. The baseline probe exits at that assertion. The source was the released Forge 6.2.18-beta jar with SHA-256 `9d1471e6d4562d0a50f0564bc46573b87fd738bd440fe19397d31847da20f172`.
- The full build passes 79 unit cases and 522 native runs, including all 195 main gameplay cases. The headless packaged profile passes 21 native-console assertions. The flint mill finishes with 1,440 additional FE and the copper mill with 1,320. Both export their previously rolled powder and retain the exact completed wear after a second restart. All four server stages exit cleanly.
- The first client-profile attempt ended before client launch because the assertion treated Forge 6.2's empty output entries as extra items. The corrected check compares the saved powder yield and the exact count delivered to the hoppers.
- The final packaged profile passes 24 server assertions and 15 client checks. Survival placement spends one original mill item and restores its active copper ball, tagged spare, configured hopper sides and saved 600-of-1,920 FE job. The native menu keeps the original modifiers and updates its wear when the job finishes. The replanted mill retains its final wear and exact output through another restart.
- All four server stages and the client exit without runtime or registry errors. Client and server use production SHA-256 `bcfa96e3496932c2c0c90d4c3ee9dd2fda446df209b2592e7fb3ced9975c9d0a`. The observer records zero activation, foreground or visible-window events across 1,757 samples. All five captures show narrator off and every volume at zero.

Server logs and results are in `build/port-runtime/cycle-157-sag-client-run`. The client results and captures are in `build/port-evidence/cycle-157-sag-client-run-client`.

## Next verification

Exercise the SAG Mill recipe view and grinding-ball display in packaged JEI, including a stock ore recipe with a bonus output.
