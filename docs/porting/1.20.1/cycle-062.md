# Cycle 062 — solar conditions and carried panels

A live Phantom datapack rule can name a dimension without skylight. A packaged reproduction showed that a matching Nether rule still produced zero FE. Forge's light engine has no sky layer in that dimension, so the ordinary skylight check rejected the same panel after the rule allowed it.

Solar panels now use the ambient skylight check in dimensions that provide one. In a dimension without skylight, a matching soul rule satisfies that check. The existing day and night selection and liquid overrides still run. The fixture changes the matching dimension in a running server and samples both panels on the same game tick.

The packaged client scenario mines a charged, Phantom-bound panel with a survival pickaxe, observes the actual dropped item, picks it up, and places it over the normal TCP connection. The server checks the materialized drop, inventory, placed block and restart. The machine and item stay isolated from power receivers and ambient generation during this measurement.

## Reproduction

Use Java 17, official Forge 47.4.28, and the [macOS hidden-client harness](client-harness.md). Use fresh names when repeating the fixture.

```sh
./gradlew :enderio:assemble :enderio:reobfClientSmokeJar --console=plain
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_jar=enderio/build/libs/enderio-8.2.14.61-1.20.1+a7a6d1f5a1.jar
mkdir build/port-runtime/cycle-062-client
cp -R build/port-runtime/cycle-051-client-verified/libraries \
  build/port-runtime/cycle-051-client-verified/versions build/port-runtime/cycle-062-client/
python3 scripts/porting/client/prepare_packaged.py --name cycle-062-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_solar_charging.py --name cycle-062-packaged --port 25706 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --environment \
  --carry-launch build/port-runtime/cycle-062-client/launch.json
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain
```

The baseline ran with `--name cycle-062-baseline --port 25705 --environment` and the cycle 061 distributable `enderio-8.2.14.60-1.20.1+34ae81f064.jar`, whose SHA-256 is `e882adbc1704b44c145f4104c08b8b0660e13397e9ad02ea0ce0b04bcbc9d064`.

## Results

- The baseline passed 39 assertions before the matching Nether rule failed. The panel produced zero FE over 16 ticks where 1,024 FE was expected. Both processes exited cleanly with zero ERROR or FATAL lines. The same run measured 64 FE/t in clear daytime, 45 FE/t in rain and 20 FE/t in thunderstorms. The shielded panel stayed at zero in all three cases. Liquid Sunshine and Liquid Darkness each enabled 64 FE/t in the Nether under their applicable rules. See `build/port-runtime/cycle-062-baseline/solar-charging-results.json`.
- The packaged client passed five checks over real dedicated TCP. The server's survival loot entity held one vibrant panel with exactly 23,789 FE and a Phantom soul. The player collected it through normal item pickup, then placed it with the vanilla block-use path. The source remained air, the placed block held the original energy and soul, and the carried stack was consumed. The guarded 960×540 frame shows the placed block. Across 1,811 native samples, no activation, foreground or visible-window events were observed. Narrator was OFF, all ten sound categories were muted, the client exited zero, and its log had zero ERROR or FATAL lines. See `build/port-evidence/cycle-062-packaged-carry/`.
- The fixed packaged build passed all 54 server assertions. The replacement panel kept its exact charge and soul after restart. In the Overworld the clear, rain and thunder samples reproduced 64, 45 and 20 FE/t. In the Nether each applicable liquid produced 832 FE in 13 ticks. The matching Nether rule produced 4,160 FE in 65 ticks while the unbound and End panels stayed idle. Retargeting the live rule to the End stopped the Nether panel and generated 3,072 FE in 48 ticks in the End. Both server processes exited zero with no ERROR or FATAL lines. See `build/port-runtime/cycle-062-packaged/solar-charging-results.json`.
- The full build ran 78 ordinary JUnit tests and 428 native tests, with zero failures, errors or skips in the XML reports. See `build/port-cycle-062-build.log` and the modules' `build/test-results/`.

| Artifact | SHA-256 |
| --- | --- |
| Packaged port jar | `f846d39d3dcf30955322b98c604eae46ee109b34a897369eb5a9497f586bdb67` |
| Test-only client probe | `6b02192ea5995fa7da36e3f013f3199643b761e0bd4f6f8181d6aa451606f31d` |

## Remaining verification

Solar production at day and night transition boundaries and crafting the Phantom-bound panel through the Soul Binder still need coverage. Capacitor banks and other generation, storage and integration features remain in scope.
