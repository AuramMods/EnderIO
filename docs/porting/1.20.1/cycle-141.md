# Cycle 141 — Use native storage terminals over multiplayer

The packaged client and dedicated server load AE2 15.4.12, GuideME 20.1.15 and Refined Storage 1.12.4. The server starts from a copy of the converted [cycle 139](cycle-139.md) world. The survival player opens the AE2 item terminal and places and opens the Refined Storage grid. Both screens interact with remote media reached through the original Forge 6.2 conduits.

Both screens initially show 37 tagged ingots. The survival player deposits five through the native interface and sees 42. A right-click withdraws 21 with the original tag, and an inventory click puts the stack in a hotbar slot. The server independently reads each physical cell or disk before opening, after deposit and after withdrawal. The two other AE2 routes each retain their separate 37 tagged ingots. After the dedicated server restarts, a fresh client opens both screens and sees 21 in each network and 21 in each designated hotbar slot.

## Commands

The variables `port_java`, `port_forge`, `port_legacy`, `port_art`, `port_ae`, `port_guide` and `port_rs` use the exact pinned artifact paths in [cycle 139](cycle-139.md).

```sh
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-141-storage-terminal-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-141-storage-terminal-qa-final.log 2>&1

port_jar=enderio/build/libs/enderio-8.2.14.140-1.20.1+362d1ae664.jar
port_client=build/port-runtime/cycle-141-storage-verified-client
port_previous=build/port-runtime/cycle-138-impulse-guarded-client
port_world=build/port-runtime/cycle-139-storage-verified/converted/port-migration
python3 scripts/porting/legacy_fixture/build.py \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" --art "$port_art" \
  --storage-probe --mod "$port_ae" --mod "$port_rs" --output build/port-runtime/cycle-141-storage-verified-probe
mkdir -p "$port_client/config"
cp -cR "$port_previous/libraries" "$port_previous/versions" "$port_client/"
cp "$port_previous/config/fml.toml" "$port_client/config/fml.toml"
python3 scripts/porting/client/prepare_packaged.py --name cycle-141-storage-verified-client \
  --java "$port_java" --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets" \
  --mod "$port_ae" --mod "$port_guide" --mod "$port_rs"
python3 scripts/porting/packaged_storage_terminals.py --name cycle-141-storage-verified --port 26010 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --launch "$port_client/launch.json" \
  --world "$port_world" --probe build/port-runtime/cycle-141-storage-verified-probe/storage-probe.jar \
  --mod "$port_ae" --mod "$port_guide" --mod "$port_rs" > build/cycle-141-storage-verified.log 2>&1
```

The addon-absence check uses the same client preparation in a fresh `cycle-141-storage-absent-client` profile, with the same production and QA jars and no `--mod` arguments. `run_smoke.py --launch build/port-runtime/cycle-141-storage-absent-client/launch.json --scenario title --output build/port-evidence/cycle-141-storage-absent` launches that profile under the same native guard.

## Results

- The build passes 79 unit cases and all 513 native runs. The final remapped client QA jar also builds successfully.
- The packaged run passes 24 interaction checks, nine reconnect checks and 23 independent server assertions. Both headless servers and both guarded clients exit normally, with zero client, server or registry errors. The first scripted grid placement had skipped Refined Storage's Forge player-placement listener. The final run uses the survival player's ordinary placement and independently verifies the grid joins its controller on both server starts.
- Both client runs load the same Ender IO and addon artifacts as the server. The production jar has SHA-256 `1aa3b68924c0f7fbb979e6dbf75b5c6f04f627b82b5d053e197b61c8b058b352`. The packaged title-screen check passes with AE2, GuideME and Refined Storage absent.
- Across the three guarded launches, 3,552 native samples record zero activation, foreground or visible-window events. All recorded frames have the narrator off and all sound categories at zero. The disposable profiles retain the splash setting from [cycle 138](cycle-138.md).

The dedicated result is `build/port-runtime/cycle-141-storage-verified/storage-terminal-results.json`. Reports and screenshots are under `build/port-evidence/cycle-141-storage-verified-exercise`, `build/port-evidence/cycle-141-storage-verified-verify` and `build/port-evidence/cycle-141-storage-absent`.

## Next verification

Exercise the Mekanism chemical filter and heat conduit controls in a guarded packaged client. [Cycle 054](cycle-054.md) records the headless transfer and migration evidence.
