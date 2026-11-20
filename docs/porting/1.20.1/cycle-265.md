# Cycle 265 — Natural Nether striders and lava destinations

The disposable world uses the native Nether dimension type and Nether Wastes biome with a flat generator. A contained source pool gives the vanilla scheduler an isolated lava habitat. A real survival TCP player watches from a glass platform. The separate Relocator basin is within range of the source and close enough to the player to exclude independent natural spawning.

The charged Relocator keeps the original strider spawn when its destination is dry. Filling the same basin with lava lets it relocate matching spawns. The test then enables a dedicated QA listener that receives a native Forge teleport event with a lava target and changes that target to air. The obelisk must leave the strider in its original lava while that listener is active. A cold restart restores the same Nether pool, filter and range and admits new striders into the saved lava basin.

The observer matches natural finalization, teleport and insertion by UUID. It records the native `IN_LAVA` placement type, dimension, fluid and positions for each attempt. Native commands also verify a live strider and lava at its recorded position in each phase. The override listener and observer are in the separate test jar.

## Commands

```sh
export JAVA_HOME="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
export CI=true JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=4
./gradlew --no-daemon --no-build-cache --no-configuration-cache --max-workers=2 --console=plain \
  :enderio:assemble :enderio:reobfClientSmokeJar

port_runtime=build/port-runtime
port_seed="$port_runtime/cycle-264-aquatic-client"
port_forge="$port_runtime/cycle-047-legacy-discover"
port_jar=enderio/build/libs/enderio-8.2.14.264-1.20.1+4af703d0a0.jar
port_qa=enderio/build/test-libraries/enderio-client-qa.jar
port_client="$port_runtime/cycle-265-relocator-client"
mkdir -p "$port_client/config"
cp -cR "$port_seed/libraries" "$port_seed/versions" "$port_client/"
cp "$port_seed/config/fml.toml" "$port_client/config/"
python3 -B scripts/porting/client/prepare_packaged.py --name cycle-265-relocator-client --java "$JAVA_HOME/bin/java" \
  --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" --qa-jar "$port_qa" \
  --assets "$HOME/.gradle/caches/neoformruntime/assets"

export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Djava.net.preferIPv4Stack=true'
python3 -B scripts/porting/packaged_lava_relocator.py --name cycle-265-lava --port 26405 \
  --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" --jar "$port_jar" --qa-jar "$port_qa" \
  --launch "$port_client/launch.json"
python3 -B scripts/porting/packaged_aquatic_relocator.py --name cycle-265-water-regression --port 26406 \
  --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" --jar "$port_jar" --qa-jar "$port_qa" \
  --launch "$port_client/launch.json"

port_client="$port_runtime/cycle-265-water-retry-client"
mkdir -p "$port_client/config"
cp -cR "$port_seed/libraries" "$port_seed/versions" "$port_client/"
cp "$port_seed/config/fml.toml" "$port_client/config/"
python3 -B scripts/porting/client/prepare_packaged.py --name cycle-265-water-retry-client --java "$JAVA_HOME/bin/java" \
  --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" --qa-jar "$port_qa" \
  --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 -B scripts/porting/packaged_aquatic_relocator.py --name cycle-265-water-retry --port 26407 \
  --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" --jar "$port_jar" --qa-jar "$port_qa" \
  --launch "$port_client/launch.json"
```

## Results

- Java 17 builds the packaged Forge mod and its separate reobfuscated QA jar. The runtime SHA-256 is `502950ed7f83af6e7b2265c3ba25cf39b3b6dd774d3dd8923e23009737132843`. The QA jar is `58e6c371878724b93c5248240a435a85751df280f4bbe44accffb45861f512bf`. See `build/port-evidence/cycle-265-lava-relocator/build.log`.
- The Nether fixture passes 16 assertions over two dedicated sessions. All 32 observed striders use the native `IN_LAVA` placement. Eight stay at the source with a dry destination. Seven enter the lava basin, and all seven redirected attempts stay at the source. Ten new striders reach the basin after restart. Both server processes exit successfully without registry or runtime errors. See `build/port-runtime/cycle-265-lava/lava-relocator-results.json`.
- The two guarded Nether clients record 5,726 samples with zero activations, foreground or onscreen events. Each capture confirms all ten volumes at zero, narrator off and an uncaptured cursor. See `build/port-evidence/cycle-265-lava-{exercise,reopened}-client/summary.json`.
- The first aquatic regression run disconnected during Forge login before any aquatic phases. A fresh isolated client and server profile with identical product and QA jars passes all 13 assertions and observes 101 native tropical fish and axolotl spawns. Both servers exit without registry or runtime errors. The two client sessions add 3,080 samples with zero activations, foreground or onscreen observations and confirm mute and narrator settings. See `build/port-runtime/cycle-265-water-retry/aquatic-relocator-results.json` and `build/port-evidence/cycle-265-water-regression-exercise-client/summary.json`.

## Next verification

Exercise another mixed conduit bundle with a live facade change and verify that the existing transfers continue. [Completion audit](completion-audit.md)
