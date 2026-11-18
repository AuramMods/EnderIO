# Cycle 059 — conserve solar generation and verify charging

A packaged four-panel array reproduced a generation error on the cycle 058 jar. One exposed energetic panel should add 52 FE over 13 ticks. The array added 364 FE because each accepting neighbor increased the amount offered to the next. A separate packaged fixture spent a wired charger's last 60 FE to fill a staff and left the fully charged staff in the input slot.

Solar panels now share only the unclaimed portion of one tick's generation. Network totals stay within the Forge Energy integer range. Reducing the configured capacity stops new generation into an already overfilled panel and leaves its saved charge available. Each fragment can push after a split. Removed panels invalidate retained energy handlers. A loaded or placed panel reads its bound soul's current datapack rule, including after reload. Its soul can be cleared safely.

The wired charger debits the amount the item's Forge capability actually accepts. A full item moves to the output as soon as it finishes, including when the final charge uses the whole buffer. An installed capacitor, an available output slot and the configured redstone condition still govern the operation.

## Reproduction

```sh
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_jar=enderio/build/libs/enderio-8.2.14.58-1.20.1+e4f25395b1.jar

mkdir build/port-runtime/cycle-059-client
cp -R build/port-runtime/cycle-051-client-verified/libraries \
  build/port-runtime/cycle-051-client-verified/versions build/port-runtime/cycle-059-client/
python3 scripts/porting/client/prepare_packaged.py --name cycle-059-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_solar_charging.py --name cycle-059-packaged-release-path --port 25696 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" \
  --launch build/port-runtime/cycle-059-client/launch.json
```

Use a new run name and loopback port, and set `port_jar` to the distributable built by the checkout under test. The client profile copies the official game libraries and version files from a disposable installation. Omitting `--launch` runs the dedicated solar, phantom and wired fixtures without a client. The fixture's datapack stores each sample's game tick and energy fields in the same function before the console reads them.

## Results

- The previous packaged jar exhibited the excess solar generation and stranded staff. The evidence is in `build/port-runtime/cycle-059-before/`, including `smoke-wired-before-verified.log`. Initial fixed-build fixtures sampled sunlight before the light engine settled. The final packaged fixtures compare two samples with stationary day or night. The GameTests exercise the actual Liquid Sunshine and Liquid Darkness overrides because their starting structures lack the skylight needed by the earlier native fixtures.
- The full build passed 78 ordinary JUnit tests and 427 Forge GameTests with zero failures, errors or skips. The solar tests verify the overflow budget, both fragments' push after splitting, invalidated handles, restored connections, and loading and clearing a phantom soul. The wired test verifies exact completion and release with the last 60 FE and accepts a full item with an empty buffer. See `build/port-evidence/cycle-059-build-release-path.log`.
- The final packaged server profile passed 22 assertions. The mixed-tier array produced 52 FE over 13 daytime ticks and retained the exact per-panel balances on eviction, reload, and restart. The vibrant wired fixture accounted for all 576 generated FE, including the 100 FE transferred into the staff. The tagged staff and buffer survived restart. A saved phantom panel generated 704 FE over 11 ticks at midnight, stopped at noon, and generated 960 FE over 15 daytime ticks after the real datapack reload. Both server processes exited cleanly with zero ERROR or FATAL lines. See `build/port-runtime/cycle-059-packaged-release-path/solar-charging-results.json`.
- The wireless fixture accounted for all 32,000 FE including upkeep and actual staff charge. At the same position, 20 blocks from the charger, the player's staff charged only while the advanced antenna extended the range. Its menu button changed the active range from 48 to 47 blocks in both the dedicated server and the client. The guarded client passed five checks and captured the menu, range, energy bar and charged staff at 960×540. The native observer recorded 1,527 samples, zero activation, foreground or visible-window events, and exit code zero. Narrator was OFF, all ten channels were muted, and the client log had zero ERROR or FATAL entries. See `build/port-evidence/cycle-059-packaged-release-path-tcp/`.
- The distributable SHA-256 is `2a69763857c4950849f4330a07454063e82f21a489e41f8c40eb2a70e610af52`. The separate client QA jar SHA-256 is `c8c8734f86372da1849878ddc0c32d3ca5c20488e082c7fd553ab1c0b6ef832d`.

Weather and other dimension cases, migration from historical 6.x solar and charger saves, and optional Curios inventory charging still need packaged coverage. The wired charger GUI and solar placement and item models also remain unverified in the client.
