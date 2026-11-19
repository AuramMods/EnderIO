# Cycle 180 — Cold Fire Igniter and fire crafting

The original igniter drained 10 mB before checking the place it would light. A native survival interaction reproduced fuel loss when the clicked face had no support for fire. The igniter now checks the actual adjacent target, uses Forge's successful placement result, and drains on the server only after placing cold fire. Occupied and unsupported targets retain their blocks and fuel. A remainder below 10 mB stays in the igniter.

The previously disabled native cold-fire crafting test now keeps a flammable oak log beside the fire. The log prevents early burnout without changing the granite recipe base. The test observes the fire at the stock 260-tick maturity, extinguishes it and checks that it produces the recipe drop while leaving both supporting blocks intact.

The packaged survival client unlocks and crafts the stock igniter from dark steel and flint. A placed Ender IO tank transfers 25 mB of tagged Vapor of Levity through its normal interaction. The server and client compare the exact fuel after unsuccessful uses, a successful ignition, restart, a second ignition and a final use with only 5 mB. A fixture datapack uses the production fire-crafting serializer with a guaranteed single-grain result on granite. One fire completes with `doFireTick` disabled, and the player extinguishes the other through its normal survival action with `doFireTick` enabled. The player collects each native item drop.

## Commands

```sh
./gradlew :enderio:verifyGameplayTests --console=plain > build/cycle-180-cold-fire-before.log 2>&1
./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-180-cold-fire-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_forge="$port_runtime/cycle-047-legacy-discover"
port_jar=enderio/build/libs/enderio-8.2.14.179-1.20.1+84fa417f17.jar
port_guarded="$port_runtime/cycle-179-printout-verified-client"
port_client="$port_runtime/cycle-180-cold-fire-final-client"
mkdir -p "$port_client/config"
cp -cR "$port_guarded/libraries" "$port_guarded/versions" "$port_client/"
cp "$port_guarded/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name cycle-180-cold-fire-final-client \
  --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 -u scripts/porting/packaged_cold_fire.py --name cycle-180-cold-fire-final --port 26149 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --launch "$port_client/launch.json"
```

## Results

- With the regression added to the previous source, the full main gameplay profile fails only on fuel lost during an unsupported ignition. The restored cold-fire crafting test and the existing cold-fire age test already pass. See `build/cycle-180-cold-fire-before.log`.
- The fixed full build passes 79 ordinary unit tests and 539 native runs. All 212 discovered gameplay cases run and pass.
- The first packaged attempt had the crafted igniter and its exact 25 mB in the saved player inventory. The initial assertion expected the tank's empty `Contents` compound to be absent. The native tank retains an empty compound, so the check now looks for the fluid identifier.
- The next attempt passed the first ignition and crafting. A dropped grain moved outside the restart assertion's narrow search radius. The saved player file still held exactly 15 mB and the saved entity chunk still held the one grain. The final fixture waits for the native pickup delay and places the player within reach of the item so the restart checks the collected grain directly in inventory.
- One retry ended during the Forge handshake before the player entered the world. The next fresh profile used the same jar and passed both server stages.
- The final packaged run passes 14 server assertions and 40 client checks. Two rejected targets keep the initial 25 mB. A successful ignition leaves 15 mB and produces one collected grain. Both survive restart. The second ignition leaves 5 mB, and the next attempt retains those 5 mB. The player extinguishes the second mature fire and collects the second grain. Both oak logs remain intact, and the server stages report zero runtime or registry errors.
- The tested production jar has SHA-256 `4529d463367201c1a2220b94ae3fdaeaee4492c4e4b7c193ac042242a1072345`. The matching clients captured the placed fires with all volumes at zero and the narrator off. The independent observer records zero activation, foreground or on-screen window events across 4,374 samples. See `build/port-runtime/cycle-180-cold-fire-final/cold-fire-results.json` and `build/port-evidence/cycle-180-cold-fire-final-{exercise,verify}-client/summary.json`.

## Next verification

The Forge 6.2 igniter used Forge's `FluidHandlerItemStack` and saved its fuel under the top-level `Fluid` tag. Verify the stored fuel on copied saves and translate it to current igniter item data where needed.
