# Cycle 058 — restore Stirling fuel and energy transfer

On the cycle 057 packaged jar, a vanilla hopper retained two tagged sticks while a Stirling generator with a basic capacitor and an enabled item input accepted neither. The machine called Forge's item-level override directly. Vanilla fuels use the furnace table fallback, which `ForgeHooks.getBurnTime` applies. The same lookup now validates item insertion and ignition.

The generator uses its normal inventory update when consuming fuel, which preserves the spare stack's tag and synchronizes slot changes. Its active burn dirties the chunk even when its buffer cannot accept more energy. The live storage callback updates the full-power status when capacity or stored energy changes. Redstone still gates new fuel and transfer, while a lit fuel continues burning. The generator continues to reject fuel with a container remainder, matching the 1.21.1 baseline.

## Reproduction

```sh
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_jar=enderio/build/libs/enderio-8.2.14.57-1.20.1+c8137dda69.jar

mkdir build/port-runtime/cycle-058-client
cp -R build/port-runtime/cycle-051-client-verified/libraries \
  build/port-runtime/cycle-051-client-verified/versions build/port-runtime/cycle-058-client/
python3 scripts/porting/client/prepare_packaged.py --name cycle-058-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/client/run_stirling.py --name cycle-058-stirling-capture --port 25693 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" \
  --launch build/port-runtime/cycle-058-client/launch.json

python3 scripts/porting/packaged_legacy_migration.py --name cycle-058-legacy-stirling --port 25695 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" \
  --legacy-jar build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar --stirling-generator
```

Use fresh run names and loopback ports. Set `port_jar` to the packaged jar built from the checkout under test. Without `--launch`, the runner uses the dedicated-server fixture and disk restart without launching a client. The client profile copies only the official game libraries and version files from a disposable profile. The server starts with a fresh world and binds to loopback.

## Results

- The cycle 057 reference failed the first packaged assertion. The generator saved an empty fuel slot, 0 FE and no burn duration after repeated hopper attempts. See `build/port-runtime/cycle-058-baseline/`. A separate reference fixture seeded a full buffer with a running timer and retained elapsed burn on ordinary eviction. The persistence change explicitly marks every elapsed burn tick for saving.
- The full build passed. It ran 78 ordinary JUnit tests and 424 native GameTests with no failures, errors or skips. The new gameplay tests measure 1,480 FE from each of two sticks, exercise capacitor and redstone gating and fuel rejection, and check full-power status on fill and extraction. See `build/port-evidence/cycle-058-build-verified.log`.
- The fresh-world packaged profile passed 12 assertions. The generator consumed two hopper-fed sticks and transferred exactly 2,960 FE to an adjacent alloy smelter. The transferred balance survived restart. A second generator capped at 64,000 FE, left one tagged coal and kept the elapsed burn timer across actual chunk eviction and restart. Both server processes exited cleanly with zero ERROR or FATAL lines. See `build/port-runtime/cycle-058-stirling-capture/stirling-results.json`.
- The historical fixture passed 24 packaged assertions. The official Forge 6.2.18-beta jar generated the saved 600-tick burn. The original, converted and restarted servers observed 596, 593 and 590 ticks remaining, respectively. The generator retained its 64,000 FE, tagged spare coal, capacitor, facing and eastward output setting. All three servers exited cleanly. Conversion had three Forge diagnostics for other legacy IDs and zero other runtime errors. See `build/port-runtime/cycle-058-legacy-stirling/legacy-migration-results.json`.
- The guarded client passed eight checks over real loopback TCP. It opened the machine with an ordinary block-use packet, shift-clicked coal and a capacitor, observed the flame and energy through the menu, and scrolled the redstone widget. The server confirmed that it would light no new fuel. The existing coal kept generating while the widget displayed the new redstone state. The two 960×540 captures followed the offline-server toast's dismissal and show the resulting menu states. Both captures show narrator OFF and all ten channels at zero volume. The native observer recorded 2,032 samples, zero activation, foreground or visible-window events and exit code zero. The client log had zero ERROR or FATAL entries. See `build/port-evidence/cycle-058-stirling-capture-tcp/`.
- The distributable SHA-256 is `2190d9c5fd9e5e54e65933c3e26ce79353dcca442252cc50d6892768fdcd1507`. The separate client QA jar SHA-256 is `0e73cc3fd33f3a3f8fa2cbbd3efe35d756fabe9688537dde2c69ea8e3a78f787`.

The next runtime checks should measure solar generation, external charging and their resource conservation on packaged Forge.
