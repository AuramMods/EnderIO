# Cycle 264 — Natural aquatic spawns and the Relocator

The Forge fixture uses a disposable lush-caves world. A water-filled source chamber with a clay floor gives the native scheduler a habitat for tropical fish and axolotls. A real survival TCP player enables natural spawning from a glass station. The separate destination is close to the Relocator and too close to the player for independent natural spawns. The charged Relocator filters for tropical fish.

With the destination dry, both species insert at their original positions. Filling the same destination with water lets the active Relocator move the matching fish. Axolotls remain at their source. Turning the Relocator off stops transfer despite the available water. A dedicated restart reopens the water-filled destination and the enabled Relocator with its original filter and range.

The separate observer records only native natural-spawn finalization, the actual teleport event and insertion. It matches the records by UUID and verifies body water at finalization and insertion. Native selectors confirm that a sampled fish is alive and that its insertion block is water.

## Commands

```sh
export JAVA_HOME="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
export CI=true JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=4
./gradlew --no-daemon --no-build-cache --no-configuration-cache --max-workers=2 --console=plain \
  :enderio:assemble :enderio:reobfClientSmokeJar

port_runtime=build/port-runtime
port_seed="$port_runtime/cycle-263-enchanter-core-final-client"
port_forge="$port_runtime/cycle-047-legacy-discover"
port_jar=enderio/build/libs/enderio-8.2.14.263-1.20.1+bb3511e0fb.jar
port_qa=enderio/build/test-libraries/enderio-client-qa.jar
port_client="$port_runtime/cycle-264-aquatic-client"
mkdir -p "$port_client/config"
cp -cR "$port_seed/libraries" "$port_seed/versions" "$port_client/"
cp "$port_seed/config/fml.toml" "$port_client/config/"
python3 -B scripts/porting/client/prepare_packaged.py --name cycle-264-aquatic-client --java "$JAVA_HOME/bin/java" \
  --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" --qa-jar "$port_qa" \
  --assets "$HOME/.gradle/caches/neoformruntime/assets"

export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Djava.net.preferIPv4Stack=true'
python3 -B scripts/porting/packaged_aquatic_relocator.py --name cycle-264-aquatic --port 26403 \
  --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" --jar "$port_jar" --qa-jar "$port_qa" \
  --launch "$port_client/launch.json"
python3 -B scripts/porting/packaged_natural_spawns.py --name cycle-264-ground-regression --port 26404 \
  --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" --jar "$port_jar" --qa-jar "$port_qa" \
  --launch "$port_client/launch.json"
```

## Results

- Java 17 builds the distributable and the separate reobfuscated observer. The production SHA-256 is `a363172835c900ed07fbcc62438bd8f8bbdc2040009248af8a56c746575f6e11`, and the QA jar is `59c3bf2c561e71bea4c079d9aae1a3488abdff7258b624c37a3cf48111963a23`. See `build/port-evidence/cycle-264-aquatic-relocator/build.log`.
- The aquatic run passes 13 assertions over two dedicated sessions. It observes 65 scheduled spawns. All 17 axolotls remain in the source. The dry and disabled cases keep 20 and 15 fish at their source respectively. The flooded active case moves eight fish, and the reopened world moves five more. Every recorded final position contains water. See `build/port-runtime/cycle-264-aquatic/aquatic-relocator-results.json`.
- Both aquatic server processes exit successfully without registry or runtime errors. The guarded clients record 2,986 samples with zero activations, foreground or onscreen events. Both captures record an uncaptured cursor, ten muted sound categories and the narrator off. See `build/port-evidence/cycle-264-aquatic-{exercise,reopened}-client/summary.json`.
- The ground-spawn regression passes all 16 assertions on the same build and observes 60 native attempts. Ten matching zombies relocate to solid ground across the two enabled phases, including after chunk eviction. Inhibitor, Aversion and the original skeleton control all pass. The third guarded client records 2,105 samples with zero activations, foreground or onscreen events, and the dedicated server exits cleanly. See `build/port-runtime/cycle-264-ground-regression/natural-spawn-results.json`.

## Next verification

Exercise the Relocator's lava spawn placement path in the Nether. [Completion audit](completion-audit.md)
