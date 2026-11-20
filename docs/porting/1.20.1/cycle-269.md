# Cycle 269 — Packaged stock SAG Mill input matrix

The fixture reads the 86 SAG Mill recipes at the fixed 1.21.1 baseline and compares their energy, bonus modes and output definitions with the packaged Forge jar. The live server reports which recipes load and every item in the active ingredient tags. One mill per distinct item receives a tagged input from a native hopper, pays 600 FE and saves the rolled result. After cold restart, the same item, recipe, cost and result must still be present. The mill receives its remaining cost plus 137 FE and exports the exact saved result to a hopper. A second restart checks every final hopper balance and the 137 FE reserve again.

The core profile uses vanilla and Ender IO ingredients. The second profile adds Mekanism 10.4.16 and Immersive Engineering 10.2.0-183 for the actual ore, raw material, ingot and dust tags. Immersive Engineering supplies both regular and deepslate aluminum ore, raw aluminum and aluminum dust. [Immersive Engineering Forge release](https://modrinth.com/mod/immersiveengineering/version/10.2.0-183)

Both baseline grass recipes accept tall grass. The baseline NeoForge `c:stones` tag also contains deepslate, which has a separate direct-input recipe. The fixture records both candidates at these overlaps and checks the actual selected recipe and saved outputs. The shipped output probabilities are compared as recipe data. Each machine outcome is checked against the possible stock rolls.

## Commands

```sh
export JAVA_HOME="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
export CI=true JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=4
./gradlew --no-daemon --no-build-cache --no-configuration-cache --max-workers=2 --console=plain :enderio:assemble

export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Djava.net.preferIPv4Stack=true'
port_runtime=build/port-runtime
port_forge="$port_runtime/cycle-047-legacy-discover"
port_jar=enderio/build/libs/enderio-8.2.14.268-1.20.1+b63bfdcbb9.jar
port_art=$(rg --files --hidden "$HOME/.gradle/caches/modules-2/files-2.1/net.neoforged/AutoRenamingTool/2.0.17" \
  | rg '/AutoRenamingTool-2\.0\.17-all\.jar$' | head -1)
port_probe="$port_runtime/cycle-269-current-probe/registry-probe.jar"
python3 -B scripts/porting/legacy_fixture/build.py --registry-probe --java "$JAVA_HOME/bin/java" \
  --forge-server "$port_forge" --legacy-jar "$port_jar" --art "$port_art" --output "$(dirname "$port_probe")"

port_mek="$port_runtime/cycle-252-combined-audit/mods/mekanism-uxe1WQp4.jar"
port_ie="$port_runtime/cycle-269-artifacts/ImmersiveEngineering-1.20.1-10.2.0-183.jar"
mkdir -p "$(dirname "$port_ie")"
curl -fL --retry 2 'https://cdn.modrinth.com/data/tIm2nV03/versions/WUzj4tgJ/ImmersiveEngineering-1.20.1-10.2.0-183.jar' -o "$port_ie"
python3 -B scripts/porting/packaged_sag_stock.py --name cycle-269-stock-core-verified --port 26424 \
  --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" --jar "$port_jar" --probe "$port_probe"
python3 -B scripts/porting/packaged_sag_stock.py --name cycle-269-stock-metals-verified --port 26425 \
  --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" --jar "$port_jar" --probe "$port_probe" \
  --mod "$port_mek" --mod "$port_ie"
```

## Results

- Java 17 assembly passes. The runtime jar SHA-256 is `d2bc9ecb4500546cef898d2d55d19cc9e03a62fc495f86227218fc279ced9f74`, the separate registry probe is `07a9e7eab2cd50f3936c0f4f3a84fc4ba5243d3e536e627aa49ea25e3d8f9cbc`, Mekanism is `3b0d191fa4a45e662f9725991d76a30192f30186abb2ba694b873b5f7b9358d2` and Immersive Engineering is `061dd0e8e066dc39277736d5607a47a18fee4aff428de3363882f96e5c405aaa`. The first probe build used the historical jar and could not resolve the port's data-map classes. Building the probe against the current jar succeeds. See `build/port-evidence/cycle-269-sag-stock/`.
- Both initial profiles restored every paused job. Their final assertion generated an extra space in the native command for an empty output roll. The corrected harness joins complete conditions and verifies empty and nonempty hoppers. The full rerun uses fresh worlds. See `build/port-runtime/cycle-269-stock-{core,metals}/smoke-finish.log`.
- Core Forge loads 71 stock recipes and runs 139 distinct inputs. Each grind uses 2,400 FE, for 333,600 FE total, and seven rolls produce an empty hopper. The profile passes 1,134 assertions across both cold restarts. See `build/port-runtime/cycle-269-stock-core-verified/sag-stock-results.json`.
- With Mekanism and Immersive Engineering, all 86 stock recipe IDs load and all 169 distinct inputs grind. Both aluminum ore variants export actual Immersive Engineering raw aluminum, and the raw-material route exports its dust. The profile passes 1,314 assertions, spends 405,600 FE and preserves eight empty rolls. See `build/port-runtime/cycle-269-stock-metals-verified/sag-stock-results.json`.
- Both profiles select `grass` for the shared tall-grass input, so `tall_grass` is counted in the loaded inventory and `grass` is counted in the executed jobs. Both select the direct deepslate recipe for deepslate and exercise the `stone` recipe with other tag members. All six headless sessions exit successfully with zero runtime or registry errors. The registry probe and third-party archives are separate runtime files.

## Next verification

Exercise a wider set of stock VAT reagent combinations with native hopper and tank transfers and exact recipe yields. [Completion audit](completion-audit.md)
