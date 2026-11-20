# Cycle 244 — Bundled redstone sampling during a cold start

The packaged headless fixture runs a stock redstone line across five chunks. A remote powered blue lever gates two local fluid routes and one local energy route. One fluid route requires a signal. The other fluid route and the energy route require its absence. The test evicts the remote chunk while the local endpoint chunk stays loaded and then reloads the saved powered lever. The positive fluid gate closes and reopens, and the inverse gates transfer only while the signal is absent.

The fixture saves another 1,350 mB of tagged water and 910 FE behind the powered inverse gates. On the first cold start, the original jar transfers all 1,350 mB before sampling the powered conduit. Its destination reaches 2,470 mB while the lever is still on. One trace showed four redstone network ticks before any of the 76 nodes attached. Another trace showed 62 nodes ticking and blue at 0, with the other 14 nodes and blue at 15 arriving on the next sample. The patch keeps extraction closed until the first sample can include the conduit nodes in chunks scheduled to tick. That sample refreshes dependent routes even when all channels are off. Always-active controls and ordinary neighbor redstone keep using their existing signal behavior.

The native regression leaves one redstone node detached in a ticking chunk and runs the stock redstone and fluid updates. It verifies that the source holds all 6,000 mB until the node reattaches and the graph takes a real unpowered sample. The next fluid update delivers 5,000 mB. The packaged fixture checks the powered balances through three cold starts before removing the signal.

## Commands

```sh
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew build --continue --max-workers=2 --console=plain

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_jar=enderio/build/libs/enderio-8.2.14.243-1.20.1+b07f0a3051.jar
export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Djava.net.preferIPv4Stack=true'

python3 -Bu scripts/porting/packaged_bundled_gate_lifecycle.py --name cycle-244-gates-before --port 26367 \
  --java "$port_java" --forge-server "$port_forge" --jar enderio/build/libs/enderio-8.2.14.242-1.20.1+403fe988b7.jar
python3 -Bu scripts/porting/packaged_bundled_gate_lifecycle.py --name cycle-244-gates-ready --port 26367 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar"
```

The fluid conduit menu check reuses the guarded Forge client setup from cycle 243.

```sh
port_runtime=build/port-runtime
port_seed="$port_runtime/cycle-242-fluid-verified-client"
port_menu=cycle-244-gates-ready-menu
port_client="$port_runtime/$port_menu-client"
mkdir -p "$port_client/config"
cp -cR "$port_seed/libraries" "$port_seed/versions" "$port_client/"
cp "$port_seed/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name "$port_menu-client" --java "$port_java" \
  --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 -Bu scripts/porting/packaged_fluid_conduit_controls.py --name "$port_menu" --port 26366 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --launch "$port_client/launch.json"
```

## Results

- The original jar passes all seven live assertions, including remote chunk eviction and recovery. The first cold-start assertion fails. Inspection of the saved region shows that the inverted fluid source is empty and its receiver holds 2,470 mB. The energy route still holds its original 910 FE and 900 FE. Both processes exit without runtime or registry errors. See `build/port-runtime/cycle-244-gates-before/bundled-gate-lifecycle-results.json`.
- The first two patches still let the cold-start batch through. The diagnostic traces show the empty and partial graphs in `build/port-runtime/cycle-244-cold-trace/smoke-trace.log` and `build/port-runtime/cycle-244-cold-trace2/smoke-reseed.log`.
- The full build passes 79 unit cases and 548 native runs, including all 220 main gameplay cases. The new native test keeps 6,000 mB in its source while a graph node in a ticking chunk is detached. The first complete off sample allows exactly 5,000 mB through on the next update. See `build/cycle-244-gates-ready-build.log` and `enderio/build/test-results/gameTestServer/results.xml`.
- The final packaged run passes all 14 assertions. Three cold starts keep 1,350 mB and 910 FE behind the powered gates. Turning the lever off after the third start releases both batches once, bringing the respective receivers to 2,470 mB and 1,810 FE. The positive fluid route retains its 1,600 mB total and its tag. All four dedicated server processes exit cleanly without runtime or registry errors. The tested production SHA-256 is `f4ae76adb7f33b6faf28115696cdb208ebd62ea50fa7071f3fcdf695a7e5e1ab`. See `build/port-runtime/cycle-244-gates-ready/bundled-gate-lifecycle-results.json`.
- An earlier guarded menu run ended during login. The final run passes 14 server assertions and 58 client checks on the same production jar. The reopened menus retain their settings and deliver the waiting 730 mB once the lever switches off. Both clients record zero activations, foreground samples and onscreen events across 5,300 observations. All eight captures confirm narrator off, all ten sound categories at zero and an uncaptured cursor. The client test code ships in the separate QA jar. See `build/port-runtime/cycle-244-gates-ready-menu/fluid-conduit-controls-results.json`.

## Next verification

Exercise the Mekanism chemical conduit menu and a saved bundled gate with Mekanism loaded. Check that the selected channel opens and closes extraction in both signal modes and that the saved restriction survives dedicated restart.
