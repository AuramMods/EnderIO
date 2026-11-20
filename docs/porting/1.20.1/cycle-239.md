# Cycle 239 — Hang Glider interruption and redeployment while boating

The packaged integrated-server test now boards a vanilla boat through the survival entity-interaction packet while the player is already gliding. The boat fixture stays suspended above the runway so the player is airborne and clear of water during the ride. Both game sides must stop gliding while the original tagged glider remains equipped. The client holds the standard dismount key, then walks clear of the boat if needed. The same glider must stay stowed at first, deploy after falling and land safely. The test still runs the armor swap and save/reopen checks from [cycle 238](cycle-238.md).

## Commands

```sh
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew :enderio:reobfClientSmokeJar --max-workers=4 --console=plain

port_name=cycle-239-glider-boat
port_runtime=build/port-runtime
port_seed="$port_runtime/cycle-237-glider-client"
port_client="$port_runtime/$port_name-client"
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.237-1.20.1+7f00dcf40a.jar
port_qa=enderio/build/test-libraries/enderio-client-qa.jar
export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4'

mkdir -p "$port_client/config"
cp -cR "$port_seed/libraries" "$port_seed/versions" "$port_client/"
cp "$port_seed/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name "$port_name-client" --java "$port_java" \
  --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" --qa-jar "$port_qa" \
  --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/client/run_smoke.py --launch "$port_client/launch.json" --scenario hang-glider-single-player \
  --output "build/port-evidence/$port_name" --timeout 265
```

## Results

- The reobfuscated QA jar builds successfully. The guarded packaged client passes all 31 checks on Forge 47.4.28 and Java 17 using product SHA-256 `5f1fc48603a51271c746842b49fdcf618bfe827071b2a89e240f6ad4df4dfff7`. Both sides confirm that the glider stays stowed during the 20-tick ride. The player dismounts at Y 90.56, both sides confirm redeployment 21 client ticks later at server Y 83.91, and the player lands at Z 43.44 with all 20 health points. The same run repeats the armor-swap and save/reopen checks. See `build/port-evidence/cycle-239-glider-boat/summary.json` and `build/cycle-239-glider-qa.log`.
- The native observer records zero activations, foreground samples or onscreen samples across 3,588 observations. All eight captures show ten muted volume categories and narration off. The pre-boarding, mounted and redeployed frames show the canopy rotate upright for the ride and flatten again once gliding resumes. See `build/port-evidence/cycle-239-glider-boat/`.

## Next verification

Exercise the stock Industrial Insulation alloy recipe and verify native fluid pickup and block reuse. The feature matrix currently records historical block migration for it.
