# Cycle 144 — Exercise an AE2 facade on a powered ME conduit

Copy the converted [cycle 141](cycle-141.md) world and load the packaged Forge jar with AE2 15.4.12, GuideME 20.1.15 and Refined Storage 1.12.4. The server probe creates a genuine AE2 facade using the diamond block and gives it to the survival player. The client uses ordinary block placement, the Yeta Wrench, a diamond pickaxe and item pickup. The server checks the saved provider stack, the surviving ME node and the exact tagged items in the remote cell. The client captures the rendered facade before and after a resource reload and a dedicated restart.

## Commands

The variables `port_java`, `port_forge`, `port_legacy`, `port_art`, `port_ae`, `port_guide` and `port_rs` use the pinned paths from [cycle 139](cycle-139.md).

```sh
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-144-ae2-facade-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-144-ae2-facade-qa-basic.log 2>&1

port_jar=enderio/build/libs/enderio-8.2.14.143-1.20.1+b21de38fb6.jar
port_client=build/port-runtime/cycle-144-ae2-facade-client
port_previous=build/port-runtime/cycle-141-storage-verified-client
port_world=build/port-runtime/cycle-141-storage-verified/port-spawners
python3 scripts/porting/legacy_fixture/build.py \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" --art "$port_art" \
  --storage-probe --mod "$port_ae" --mod "$port_rs" --output build/port-runtime/cycle-144-ae2-probe-built
mkdir -p "$port_client/config"
cp -cR "$port_previous/libraries" "$port_previous/versions" "$port_client/"
cp "$port_previous/config/fml.toml" "$port_client/config/fml.toml"
python3 scripts/porting/client/prepare_packaged.py --name cycle-144-ae2-facade-client \
  --java "$port_java" --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets" \
  --mod "$port_ae" --mod "$port_guide" --mod "$port_rs"
python3 scripts/porting/packaged_ae2_facade.py --name cycle-144-ae2-basic --port 26020 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --launch "$port_client/launch.json" \
  --world "$port_world" --probe build/port-runtime/cycle-144-ae2-probe-built/storage-probe.jar \
  --mod "$port_ae" --mod "$port_guide" --mod "$port_rs" > build/cycle-144-ae2-basic.log 2>&1
```

## Results

- The full build passes 79 unit cases and all 516 native runs, including 190 main gameplay cases and 31 addon cases. The remapped client QA jar and the separate server probe build successfully.
- The packaged session passes 18 client checks during placement, mining and reinstallation, three more after restart, and 15 independent server assertions. Both clients and both dedicated server runs exit normally with zero reported client, server or registry errors. The AE2 route exposes exactly 21 tagged ingots from the same native remote cell before, during and after the facade changes and across restart.
- The central frame crop contains zero diamond-colored pixels before placement, 16,456 after placement and 12 in wrench mode. The wrench frame shows the conduit through the faded diamond surface. After survival pickup and reinstallation, the facade renders on the original conduit after both a resource reload and a dedicated restart. The native inventory and block-state checks verify the exact diamond provider and a single recovered item.
- The first fixture loaded with the wrench selected. Selecting the facade explicitly yielded normal visibility. The next check assumed the BASIC facade omitted all conduit quads, while the painted opaque block covers the conduit in the rendered scene. The final check uses the BASIC model state and actual frame output.
- Both clients load byte-identical Ender IO, AE2, GuideME and Refined Storage jars to the server. The Ender IO jar has SHA-256 `46a72b87083bf0badb0d3696c377f7669e2f30d0d4cc0a6e95d4cdc9bd612afb`. Across 3,355 native observations there are zero activation, foreground or visible-window events. The narrator is off and all sound categories are at zero in all five captured frames.

The dedicated report is `build/port-runtime/cycle-144-ae2-basic/ae2-facade-results.json`. Client results and frames are under `build/port-evidence/cycle-144-ae2-basic-exercise` and `build/port-evidence/cycle-144-ae2-basic-verify`.

## Next verification

Verify the baseline's same-color conduit connections and different-color isolation with data-driven ME conduit definitions on a live AE2 grid.
