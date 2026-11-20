# Cycle 242 — Fluid conduit survival upgrades and transfer budgets

The packaged survival client uses all five shipped fluid conduit recipes. The basic recipe uses clear glass, and the direct Energetic and Vibrant recipes use fused quartz. The two upgrade recipes reject a conduit of the wrong variant before accepting exactly one of the required lower tier. Each craft yields eight properly typed conduits. The native tooltips report 200, 1,000 and 8,000 mB per tick.

Two crafted basic conduits join across a chunk boundary. Survival upgrades replace both pieces with Energetic and then Vibrant conduits. Each replacement gives back one lower-tier piece. The fixture configures the original two external faces and installs a standard water filter. The filter rejects lava, then stays installed while five mixed and uniform configurations carry tagged water. Subsequent transfers check actual chunk eviction, dedicated restart and a full destination tank.

Two native GameTests feed 64,000 mB of tagged water through real networks using Forge capability endpoints. Two consecutive network updates measure the Vibrant cap and the lower cap caused by a single Energetic segment in a Vibrant route. The tests check both balances and the original fluid tag after each update.

## Commands

```sh
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew :enderio:reobfClientSmokeJar --max-workers=4 --console=plain
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew build :enderio:reobfClientSmokeJar --continue --max-workers=2 --console=plain

port_runtime=build/port-runtime
port_seed="$port_runtime/cycle-241-painted-fence-client"
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_qa=enderio/build/test-libraries/enderio-client-qa.jar
export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Djava.net.preferIPv4Stack=true'

run_port_progression() {
  local port_name="$1" port_test="$2" port_number="$3" port_jar="$4"
  local port_client="$port_runtime/$port_name-client"
  mkdir -p "$port_client/config"
  cp -cR "$port_seed/libraries" "$port_seed/versions" "$port_client/"
  cp "$port_seed/config/fml.toml" "$port_client/config/"
  python3 scripts/porting/client/prepare_packaged.py --name "$port_name-client" --java "$port_java" \
    --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" --qa-jar "$port_qa" \
    --assets "$HOME/.gradle/caches/neoformruntime/assets"
  python3 -Bu "scripts/porting/packaged_${port_test}_progression.py" --name "$port_name" --port "$port_number" \
    --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" \
    --jar "$port_jar" --launch "$port_client/launch.json"
}

run_port_progression cycle-242-fluid-verified fluid 26363 enderio/build/libs/enderio-8.2.14.241-1.20.1+9eb75b9cad.jar
run_port_progression cycle-242-item-control item 26364 enderio/build/libs/enderio-8.2.14.239-1.20.1+8ad22b6898.jar
```

## Results

- The first packaged test crafts all five recipes and installs the two basic conduits. Its lava assertion uses invalid console path syntax. The next attempt reads the resulting world from disk and confirms that the 615 mB of lava remains at the source. The filter was saved with an integer stack count, matching the conduit's codec, while the assertion expected a byte. The final fixture matches the saved stack and full endpoint configuration. See `build/port-runtime/cycle-242-fluid/smoke-exercise.log` and `build/port-runtime/cycle-242-fluid-final/`.
- The full build passes 79 unit cases and 546 required Forge runs, including all 218 main gameplay cases. The two new tests measure 40,000 mB then 24,000 mB on the uniform Vibrant line and 5,000 mB on each update with an Energetic segment. The spent and received amounts balance after each update and the water tag is intact. See `build/cycle-242-fluid-full.log` and `enderio/build/test-results/gameTestServer/results.xml`.
- The existing mixed-tier item progression passes 17 server assertions and 35 packaged client checks using the same QA build. See `build/port-runtime/cycle-242-item-control/item-progression-results.json`.
- The final fluid run passes 16 server assertions and 35 packaged client checks. All five recipes yield eight of the correct variant and both upgrade recipes reject the wrong variant. The filter leaves all 615 mB of lava at the source. Across five configurations the line delivers 30,500 mB of tagged water, then another 500 mB after evicting and reloading both chunks. After restart, 1,000 mB fills the tank to 32,000 mB and 500 mB stays at the source until the test clears space. Only that saved remainder then transfers. See `build/port-runtime/cycle-242-fluid-verified/fluid-progression-results.json`.
- Both final profiles have clean server exits and zero runtime or registry errors. The fluid client and servers use the newly built production SHA-256 `574022bc370e35939e259f9dcb27eb936eda313464592ee0c04f4a7ad70783cb`. The item control uses production SHA-256 `b9aa8cce0274d3a8f7ca7db3aea55c0c03004d65c5bef9171730f8b4e9a23d80`. The guarded clients record zero activation, foreground or visible-window events over 4,395 observations. All six screenshots have the narrator off and every sound category at zero.

## Next verification

Configure a higher-tier fluid line through the actual conduit menus. Verify that channel and redstone changes synchronize over TCP, affect live transfer and persist through restart.
