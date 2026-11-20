# Cycle 245 — Bundled redstone controls for Mekanism chemical conduits

The fixture connects two Mekanism Ultimate Chemical Tanks with Ender and Pressurized Chemical Conduits across a chunk boundary. The same two bundles carry the blue redstone signal from a lever at the outlet. The survival client opens each native conduit arm, edits both transfer channels and the extraction gate, and revisits the saved menus after a dedicated restart.

The test holds oxygen behind a red gate while the blue lever is on. Switching the gate to blue releases the first batch. A mismatched outlet blocks the second batch until the transfer channels match. Removing and restoring power opens the positive gate only when the lever powers its matching channel. After switching to inverted mode, the lever holds a fresh batch while powered and releases it once power is removed. The final powered batch stays queued from the original save through the restart and menu reopen.

The client reuses the fluid conduit menu probe and checks the common settings against the registered chemical configuration codec. The fluid profile still checks its insertion priority. The packaged server script uses the stock Forge and Mekanism block entities, and each client loads the same mod jars as its server.

## Commands

```sh
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew :enderio:reobfJar :enderio:reobfClientSmokeJar --max-workers=2 --console=plain

port_runtime=build/port-runtime
port_seed="$port_runtime/cycle-244-gates-ready-menu-client"
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.244-1.20.1+8822f9dda8.jar
port_mek="$port_runtime/cycle-177-tin-fixed-client/mods/mekanism-uxe1WQp4.jar"
port_name=cycle-245-mekanism-controls
port_client="$port_runtime/$port_name-client"
export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Djava.net.preferIPv4Stack=true'

mkdir -p "$port_client/config"
cp -cR "$port_seed/libraries" "$port_seed/versions" "$port_client/"
cp "$port_seed/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name "$port_name-client" --java "$port_java" \
  --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets" \
  --mod "$port_mek"
python3 -Bu scripts/porting/packaged_fluid_conduit_controls.py --name "$port_name" --port 26366 \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" \
  --jar "$port_jar" --launch "$port_client/launch.json" --mekanism "$port_mek"
```

The control uses the same commands with `port_name=cycle-245-fluid-controls` and both Mekanism arguments omitted.

## Results

- The packaged production and QA jars build and reobfuscate. Inspection covers the production archive and its three nested jars. Client probe classes are in the separate QA archive. See `build/cycle-245-controls-assembly.log`.
- With Mekanism, 16 server assertions and 58 client checks pass. Changing the gate color releases 11,440 units of oxygen. Rematching the outlet channel releases another 21,920. Toggling the lever proves positive control with 3,375 units and inverted control with 32,100 units. The powered gate keeps 40,730 units queued with 68,835 already delivered. The reopened menus show the same state, and switching off the lever brings the receiver to 109,565 with an empty source. Both dedicated server processes exit cleanly without runtime or registry errors. See `build/port-runtime/cycle-245-mekanism-controls/chemical-conduit-controls-results.json`.
- Without Mekanism, the fluid control passes 14 server assertions and 58 client checks. The original menus keep the blue insertion channel and priority 2, and the restarted route delivers exactly 730 mB to reach 6,190 mB of tagged water. Both dedicated server processes exit cleanly without runtime or registry errors. See `build/port-runtime/cycle-245-fluid-controls/fluid-conduit-controls-results.json`.
- All four clients load Ender IO jars matching their servers. The two chemical clients also load the pinned Mekanism jar with the server's checksum. Forge reports the mod version as `10.4.16`. All 16 captures confirm ten muted volume categories, narrator off and the cursor uncaptured. The window observer records zero activation, foreground or onscreen events in 8,036 samples.

| Packaged artifact | SHA-256 |
| --- | --- |
| Ender IO | `6a530b31b3c50d1e29f78654df60978b37b1a0116cbecf2ebc3d4a3ee8839ff2` |
| Mekanism 10.4.16.80 | `3b0d191fa4a45e662f9725991d76a30192f30186abb2ba694b873b5f7b9358d2` |

## Next verification

Craft the three shipped chemical conduit tiers using their stock recipes in a packaged survival profile. Upgrade the installed line and check that its saved settings and gas balances survive native replacement.
