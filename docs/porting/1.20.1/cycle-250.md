# Cycle 250 — Redstone conduit crafting and live filter erasure

In a fresh core-only Forge world, a survival player unlocks the shipped recipes, crafts eight Redstone Conduits and uses the crafted Redstone Filter Base to make a Counting Filter. The player places two original conduits across a chunk edge between a vanilla lever and a lamp. One of each supplied material and six conduits remain.

The player uses the handheld filter menu to select blue and enter a limit of two. The native conduit menu installs that same filter. The first blue pulse leaves the lamp off and the second turns it on. The player removes the live filter through the same menu and crafts its stock erasure recipe. The result is a clean filter with the original green channel, a limit of eight and no accumulated pulses. The reinstalled filter ignores blue before the source switches to green. The server measures the filter and lamp on each of the eight pulses.

The fixture uses server commands to set up the lever, vanilla lamp and conduit endpoint channels and to pulse the input at known intervals. Crafting, placement, handheld settings and the filter transfers use the client menus and stock player actions.

## Commands

```sh
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew :enderio:reobfJar :enderio:reobfClientSmokeJar --max-workers=2 --console=plain

port_runtime=build/port-runtime
port_seed="$port_runtime/cycle-246-fluid-craft-control-client"
port_name=cycle-250-stock-redstone
port_client="$port_runtime/$port_name-client"
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.249-1.20.1+66e571ff5d.jar
export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Djava.net.preferIPv4Stack=true'

mkdir -p "$port_client/config"
cp -cR "$port_seed/libraries" "$port_seed/versions" "$port_client/"
cp "$port_seed/config/fml.toml" "$port_client/config/"
python3 -B scripts/porting/client/prepare_packaged.py --name "$port_name-client" --java "$port_java" \
  --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 -Bu scripts/porting/packaged_redstone_progression.py --name "$port_name" --port 26367 \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar" --launch "$port_client/launch.json"
```

## Results

- The production and separate client QA jars build and remap. See `build/cycle-250-redstone-assembly.log`.
- The guarded client passes all 36 checks over dedicated TCP. Four captures confirm that all ten sound categories remain at zero, the narrator stays off and the cursor stays uncaptured. The native observer records zero activation, foreground or onscreen events in 2,534 samples. The client reports no unexpected errors. See `build/port-evidence/cycle-250-stock-redstone-client/summary.json`.
- The fresh-world session and two cold starts pass all 34 server assertions. Each cold start restores count eight and the lit lamp while the lever stays high. After the second restart, a falling edge rearms the counter and the next green pulse wraps the count to one and turns the lamp off. All three servers exit cleanly without runtime or registry errors. See `build/port-runtime/cycle-250-stock-redstone/redstone-progression-results.json`.
- The server and all client captures use the same Ender IO jar with SHA-256 `2ec62ee94c3f65f9cbf88cd6b1707feb1406ae8cbcf8a19551be38c2f190f09d`. The production archive check confirms the QA classes ship in their separate archive.

## Next verification

Reconcile the feature and dependency matrices against the packaged and native evidence. Verify remaining gaps against the baseline and prioritize the ones that still affect the port's completion criteria.
