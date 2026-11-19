# Cycle 185 — Silicon production and Slice’N’Splice progression

Three stock SAG Mills grind clay, red sand and deepslate redstone ore without grinding balls. Each recipe spends 2,400 FE per input. A locked vanilla hopper captures the original silicon and byproducts from each mill. Unlocking it sends exactly two silicon horizontally into the two filtered slots of a neighboring Slice’N’Splice. The other recipe ingredients and tools arrive through its separate top hopper. Locking the transfer hopper again preserves the surplus and all byproducts. The fixture checks the actual rolled output and allowed yield ranges. It can run one additional stock batch if the first rolls produce fewer than two silicon.

The clay supplies Z-Logic Controller, red sand supplies Zombie Electrode, and deepslate redstone ore supplies Ender Resonator. All three jobs pause at 3,600 of 20,000 FE with the original ordered inputs, queued output and unworn tools. After restart, the established guarded client opens the restored Z-Logic Controller job, removes the shears while the remaining 16,400 FE is available, and puts the same shears back. The server verifies the paid job, exact output and tool wear for each route. A second restart verifies all three resulting parts, remaining silicon, retained byproducts and mill energy. The earlier test also covers Skeletal Contractor and Guardian Diode, which require no silicon. [Cycle 159](cycle-159.md)

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_forge="$port_runtime/cycle-047-legacy-discover"
port_jar=enderio/build/libs/enderio-8.2.14.184-1.20.1+5f8015598e.jar
port_guarded="$port_runtime/cycle-184-components-final-client"
port_client="$port_runtime/cycle-185-silicon-final-client"

python3 -u scripts/porting/packaged_silicon_progression.py --name cycle-185-silicon-headless --port 26167 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_runtime/cycle-184-components-final/mods/enderio.jar"
./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-185-silicon-build.log 2>&1
mkdir -p "$port_client/config"
cp -cR "$port_guarded/libraries" "$port_guarded/versions" "$port_client/"
cp "$port_guarded/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name cycle-185-silicon-final-client \
  --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 -u scripts/porting/packaged_silicon_progression.py --name cycle-185-silicon-final --port 26168 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --launch "$port_client/launch.json"
```

## Results

- The full build passes 79 ordinary unit tests and 540 native test runs, including all 213 discovered main gameplay cases. The production jar contains no client QA classes. See `build/cycle-185-silicon-build.log`.
- The packaged headless run passes 33 assertions across three server sessions. The three raw inputs produce 12, 12 and five silicon respectively. The clay hopper keeps 16 clay balls. The redstone hopper keeps 48 redstone and one cobblestone after delivering its silicon. The headless route also pauses the paid Z-Logic Controller job while the shears are absent, then reinstalls them through the top hopper. See `build/port-runtime/cycle-185-silicon-headless/silicon-progression-results.json`.
- The final multiplayer run passes 33 server assertions and 10 client checks. Its mills spend 72,000 FE across 30 stock grinds and produce 24 silicon. The three slicers spend 60,000 FE and consume six silicon to produce one of each part. After the second restart, the hoppers retain 18 silicon, 18 clay balls and 50 redstone. The Z-Logic Controller and Zombie Electrode each use one durability on both tools. The Ender Resonator consumes the final use of both of its tools. All three server sessions exit with zero runtime or registry errors.
- Both multiplayer peers use production SHA-256 `181c010813460e072915548d620227bb62b68547f07f5b01033d3b4a48b8c7da`. The guarded client's three captures show all volume categories at zero and narrator off. The native observer records zero activation, foreground or visible-window events over 3,614 samples. See `build/port-runtime/cycle-185-silicon-final/silicon-progression-results.json` and `build/port-evidence/cycle-185-silicon-final-client/summary.json`.

## Next verification

Use the original Z-Logic Controller and Ender Resonator in the stock Soul Binder recipes for Frank ’N’ Zombie and Sentient Ender. Verify the exact XP, energy, extracted item and returned Soul Vial.
