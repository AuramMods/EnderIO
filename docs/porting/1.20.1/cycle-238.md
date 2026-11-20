# Cycle 238 — Single-player Hang Glider and equipment swaps

The packaged Forge client creates a new survival world with an integrated server. The player equips a tagged Hang Glider through ordinary item use, then glides along a raised runway. The client and integrated server both observe gliding. The player lands forward with the original health and equipment.

On a second descent, the player uses a tagged leather chestplate while the glider is deployed. The equipped glider returns to the player's hand, gliding stops and the ordinary fall causes damage. The player equips the same glider again and closes the world. On reopen, the saved player, health, position and tagged equipment are checked before the third flight.

## Commands

```sh
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew :enderio:reobfClientSmokeJar --max-workers=4 --console=plain
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew build :enderio:reobfClientSmokeJar --continue --max-workers=2 --console=plain

port_name=cycle-238-glider-single
port_runtime=build/port-runtime
port_seed="$port_runtime/cycle-237-glider-client"
port_client="$port_runtime/$port_name-client"
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=build/cycle-238-artifacts/product.jar
port_qa=build/cycle-238-artifacts/qa.jar
export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4'

mkdir -p build/cycle-238-artifacts
cp enderio/build/libs/enderio-8.2.14.236-1.20.1+1250a91eb4.jar "$port_jar"
cp enderio/build/test-libraries/enderio-client-qa.jar "$port_qa"
mkdir -p "$port_client/config"
cp -cR "$port_seed/libraries" "$port_seed/versions" "$port_client/"
cp "$port_seed/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name "$port_name-client" --java "$port_java" \
  --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" --qa-jar "$port_qa" \
  --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/client/run_smoke.py --launch "$port_client/launch.json" --scenario hang-glider-single-player \
  --output "build/port-evidence/$port_name" --timeout 265
```

## Results

- The packaged Forge 47.4.28 integrated client passes all 20 checks. The first flight lands at Z 177.43 with 20 health points. The midair chestplate swap stops the glide and the player lands at Z 5.12 with 12 health points. The saved player reopens at the same location with the tagged glider equipped and the tagged chestplate in hand. The new glide lands at Z 177.43 with the same 12 health points. See `build/port-evidence/cycle-238-glider-single/summary.json`.
- The guarded launch records zero activations, foreground samples or onscreen samples across 3,590 observations. All five captures confirm ten volume categories at zero and narration off. The canopy disappears when the player equips the chestplate and is visible in flight after reopening. See `build/port-evidence/cycle-238-glider-single/`.
- Both Gradle commands exit successfully. The full build passes 79 unit cases and 543 required Forge GameTest runs. The client loads production SHA-256 `b793302acde04c1b86118fed7d663b4f87466940ede5e8b28a17b7d9682ccad6`. The full build's jar uses current commit metadata and has SHA-256 `5f1fc48603a51271c746842b49fdcf618bfe827071b2a89e240f6ad4df4dfff7`. All 15 glider and associated integration classes are byte-identical between these jars. Neither production artifact includes client smoke classes. See `build/cycle-238-glider-full-build.log`.

## Next verification

Check that mounting a vehicle during flight stops the glide and that the original equipped glider redeploys after dismount.
