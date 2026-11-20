# Cycle 220 — Jade soul overlays on machines

The packaged client reads Jade's actual rendered tooltip, the block entity identifier and the soul payload sent by Jade. The probe targets the Powered Spawner, Farming Station and Soul Engine during native creative vial binding. A filled vial binds the first soul, an empty vial clears it, and a second filled vial binds a different valid soul on the same machine.

Three packaged Soul Binders run the original Phantom binding recipes for Energetic, Pulsating and Vibrant Photovoltaic Modules. Hoppers export the bound products and the empty vials. The runner saves this setup before the client joins, then transfers the actual outputs to the player for survival placement. An unbound module stays nearby as a control. The client changes Jade's own provider setting while looking at a bound module, and a second client inspects the machines after the dedicated server restarts.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_jar=enderio/build/libs/enderio-8.2.14.219-1.20.1+611de61c08.jar
port_jade="$port_runtime/cycle-177-tin-fixed-client/mods/jade-xJQHCmWJ.jar"
port_source="$port_runtime/cycle-218-stock-laserio-editor-verified-client"
port_run=cycle-220-jade-souls-verified
port_client="$port_runtime/$port_run-client"

./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain \
  > build/cycle-220-jade-souls-production-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-220-jade-client-action.log 2>&1
mkdir -p "$port_client/config/jade"
cp -cR "$port_source/libraries" "$port_source/versions" "$port_client/"
cp "$port_source/config/fml.toml" "$port_client/config/"
printf '%s\n' '{"general":{"enableTextToSpeech":false,"debug":true}}' > "$port_client/config/jade/jade.json"
python3 scripts/porting/client/prepare_packaged.py --name "$port_run-client" \
  --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets" \
  --mod "$port_jade"
JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true \
  python3 -u scripts/porting/packaged_jade_souls.py --name "$port_run" --port 26258 \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar" \
  --jade "$port_jade" --launch "$port_client/launch.json"
```

## Results

- The full build passes 79 unit cases and 542 native Forge runs, including all 214 discovered gameplay tests.
- The separate client probe reobfuscates successfully. The production jar and its nested jars contain no client QA classes.
- The first run verifies all four binding states on the Powered Spawner and Farming Station. The fixture then offers Phantom to the Soul Engine. The engine opens its ordinary menu because Phantom has no stock engine rule. The corrected fixture uses Enderman, a supported engine soul. See `build/cycle-220-jade-souls-first.log`.
- The unchanged production jar records the live Pig binding, then saves the world during a clean shutdown. The saved Powered Spawner's `EntityStorage` is empty. The Farming Station's Villager binding is present. A second run saves the initial fixture first and confirms the same loss after a real dedicated restart. Jade renders the Powered Spawner title with an empty soul payload and omits the Pig line. The Powered Spawner now marks the block entity dirty when the vial changes its soul. See `build/cycle-220-spawner-first-save.json` and `build/port-runtime/cycle-220-jade-souls-checkpoint/jade-souls-results.json`.
- One check with the fix keeps the Cow binding after an empty-vial click following a repeated teleport to the same location. The final runner leaves the player in place between interactions on the same machine and checks the server result of each native use before checking Jade's overlay.
- The verified run exercises Cow, empty and Pig on the Powered Spawner, Bee, empty and Villager on the Farming Station, and Blaze, empty and Enderman on the Soul Engine. All three original Soul Binder outputs display Phantom after survival placement. Jade's provider toggle hides and restores the line while the module keeps its soul. The saved chunk contains Pig, Villager, Enderman and the three Phantom bindings. The unbound control stays empty. See `build/cycle-220-jade-souls-verified-save.json`.
- The first reconnect with the fix disconnects during Forge login before a player joins. The server records outbound login packets and the client stops before the play session. The retry opens the same world with the same jars and runs the saved-machine checks. See `build/cycle-220-jade-reopen.py` and `build/cycle-220-jade-reopen-retry.log`.
- The fresh session and successful reconnect pass 33 packaged server assertions and 94 client checks. Jade displays the correct saved soul for all six bound machines and no soul for the unbound module. The native client agrees for the Powered Spawner, Farming Station and Soul Engine. All three solar panels still report an empty soul in their native client block entities after reconnect, even though Jade reads Phantom from the server. The live survival placements had matching client and server state on every tier. See `build/port-runtime/cycle-220-jade-souls-verified/jade-souls-reopened-retry-results.json`.
- The successful clients record 2,738 and 2,170 native window samples with zero activation, foreground or onscreen observations. All 25 captures record narrator off and every sound category at zero. Both sessions use the isolated Jade setting with text to speech disabled.

| Packaged artifact | SHA-256 |
| --- | --- |
| Ender IO before the fix | `73628ff20095aa53553f9fdb4a4fcace7435b6ae9503a27af8f1c67b6344e657` |
| Ender IO with the fix | `cb5c73e91314a8427d5a4bc19063cc2cf1b6eb920701a68c0325be28e5d04645` |
| Jade 11.13.3+forge | `a632c77975e5aedff6322b0072f8b0db3f5d4bf914df6bdcc87d1aea4d5f55f4` |

## Next verification

Sync the saved Phantom soul in the native solar block entity's chunk update and verify all three tiers on reconnect. The current solar disk writer includes the soul and the initial chunk update uses a separate sync hook.
