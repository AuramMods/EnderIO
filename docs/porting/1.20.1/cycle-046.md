# Cycle 046 — verify client data-map updates over dedicated TCP

This cycle exercises the existing Forge data-map implementation with a bundled Ender IO jar on an actual dedicated server. The added code lives in the client QA jar and the test runner. A disposable datapack on the server supplies all changed values.

## Scenario

The client verifies real TCP login and reads production data-map lookups for grinding balls, VAT reagents and wireless range extenders. It also invokes the native item tooltip path with the connected player. This fires Forge's tooltip event and checks the lines Ender IO generates from the received data.

| Point in the session | Grinding ball data | VAT reagent data | Range extender data |
| --- | --- | --- | --- |
| First login | Flint uses 24,000 durability and 120% main output. Charcoal has no grinding-ball entry. | Gunpowder multiplier is 0.25. Fire charge is 0.5. | Basic and advanced wireless antennas add 16 and 32. |
| First live reload | A server pack tag binds flint and charcoal to 45,678 durability and 150% main output. Both show a grinding tooltip. | Gunpowder is 2.25. Fire charge is removed. | Basic adds 29. Advanced is removed. |
| Second live reload | The tag retains flint only. Flint uses 33,333 durability and 200% main output. The charcoal entry and tooltip disappear. | Deleting the override restores 0.25 and 0.5. | Deleting the override restores 16 and 32. |
| Reconnect and server restart | The second reload's values and tag membership are present on both fresh connections. | Default values remain restored. | Default values remain restored. |

The two live updates use native `reload` commands while the same client is connected. The fixture waits for the actual client snapshot before making the next update, and the client requires the matching item tag and all three maps at every checkpoint. Disconnect also clears the old recipe manager's data map snapshot. Reconnecting creates a different recipe manager with the expected data. The server saves and reopens the same owned world with the final datapack files still in place.

The scenario retains the previous TCP tank interaction test after the live reloads. It exercises real menu opening, a bucket shift-click and the redstone widget. Both the server and the later client verify the resulting tank state. No production code changes were needed for this scenario. The earlier full build from cycle 045 passed 78 ordinary unit tests and 410 native GameTests.

## Commands

Prepare a separate official Forge client installation as described in the client harness. This run used `cycle-046-client`, copied official libraries and version metadata from the previous disposable profile and reset quiet settings.

```sh
./gradlew :enderio:reobfClientSmokeJar :enderio:reobfJar --console=plain
python3 scripts/porting/client/prepare_packaged.py \
  --name cycle-046-client \
  --java /Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java \
  --installer build/port-runtime/forge-47.4.28-installer.jar \
  --jar enderio/build/libs/enderio-8.2.14.45-1.20.1+d580370b0d.jar \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar \
  --assets /Users/cyberpwn/.gradle/caches/neoformruntime/assets
python3 scripts/porting/client/run_multiplayer.py --data-maps \
  --name cycle-046-repeat --port 25646 \
  --java /Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java \
  --installer build/port-runtime/forge-47.4.28-installer.jar \
  --jar enderio/build/libs/enderio-8.2.14.45-1.20.1+d580370b0d.jar \
  --launch build/port-runtime/cycle-046-client/launch.json
```

Use a new server name for each run. `--data-maps` selects the server-only datapack and the additional client checks. Fresh server installations have no development classes or optional integrations. This checks the supported data-map values and a production tooltip consumer. Dedicated-client machine throughput, JEI and large multi-chunk payloads still need runtime coverage.


## Results and open observation

The final run passes **31 client checks and five server assertions**. The connected exercise passes 24 client checks, and the fresh client after server restart passes seven. Twelve checks extend the original packaged tank scenario. The two server logs each record a clean exit, and both client and server logs have zero ERROR/FATAL entries. Both peers consumed identical Ender IO jar bytes with SHA-256 `ffb3cfefb5f926db5df9e0754af560d3808f3808896e4e6c320c78ae1b2698f6`. The separate final client QA jar is `2e91ab5b0e586ffa43c61a2abe7fccc6391280013b2d132a7c118c10be6b5bb0`. Recursive inspection finds no QA entries in the installed distribution.

The first client observer records 1,614 samples and the second 1,186. Both see zero activation, foreground or onscreen events, with native queries available and normal exit. The eight saved framebuffer captures confirm ten zero volume categories, narrator OFF and hidden, unfocused windows with uncaptured cursors. The post-restart tank menu image shows its bucket, fluid gauge and redstone icon.

The initial scenario `cycle-046-datamaps` passed end to end. The next run `cycle-046-final` used the final QA jar after removing a redundant owner check from the fresh-process phase. It passed both live reloads and the tank interactions, then timed out during the second login. The native disconnect screen says “Timed out.” Server debug shows the handshake dispatch and no acknowledgements for that connection. The peer had no play session, and the client log has no data-map decoder error. The same QA and production jars then passed in the fresh `cycle-046-repeat` server installation on port 25646. The timeout's cause remains unresolved.

Reports, input artifact hashes, observer JSON, runtime logs and captures are in `build/port-evidence/cycle-046-*`. The failed rerun's screenshot and copied client debug log are in `cycle-046-final-exercise`. The three server installations and their final datapacks and logs are under `build/port-runtime/cycle-046-{datamaps,final,repeat}`. All owned processes completed. Full gameplay, integrations, historical migration and dedicated-client conduit interactions remain open. The port goal remains active.
