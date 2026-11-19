# Cycle 146 — Verify authored ME conduit models over multiplayer

The two powered routes use a disposable server datapack that defines separate conduit names and uses the vanilla red and blue wool sprites to distinguish the models. The packaged client starts with the normal mod and dependency jars and receives the conduit definitions when it joins. It inspects the names and baked models of both inventory stacks, the model data and geometry at each side of the seam, and the final framebuffer. [Route setup](cycle-145.md)

After the dedicated restart, the client compares the red-to-red connection with the red-to-blue gap. When the server clears the blue tail, the survival player installs a red replacement through ordinary use on the supporting block. The server checks both grids, the tagged drive contents and the survival inventory. The client then verifies that the rendered gap has closed.

## Commands

Use the recorded dependency paths and storage probe. [Pinned paths](cycle-139.md), [Storage probe](cycle-145.md)

```sh
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-146-me-colors-build.log 2>&1

port_jar=enderio/build/libs/enderio-8.2.14.145-1.20.1+4dc8b40e56.jar
port_client=build/port-runtime/cycle-146-me-colors-client
port_previous=build/port-runtime/cycle-144-ae2-facade-client
mkdir -p "$port_client/config"
cp -cR "$port_previous/libraries" "$port_previous/versions" "$port_client/"
cp "$port_previous/config/fml.toml" "$port_client/config/fml.toml"
python3 scripts/porting/client/prepare_packaged.py --name cycle-146-me-colors-client \
  --java "$port_java" --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets" \
  --mod "$port_ae" --mod "$port_guide" --mod "$port_rs"
python3 scripts/porting/packaged_me_colors.py --name cycle-146-me-colors-camera --port 26023 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --launch "$port_client/launch.json" \
  --probe build/port-runtime/cycle-145-me-color-probe/storage-probe.jar \
  --mod "$port_ae" --mod "$port_guide" --mod "$port_rs" > build/cycle-146-me-colors-camera.log 2>&1
```

## Results

- The build passes 79 unit cases and 517 native runs. All 190 main gameplay and 32 addon cases pass, and the separate remapped QA jar builds successfully.
- Packaged multiplayer passes 25 client checks and 30 server assertions. The client receives the server's two conduit definitions, displays the corresponding inventory names and sprites, and bakes geometry to the shared edge only when the conduits match. The central frame crop contains 1,245 red pixels for the restored red link, zero for the restored split, and 1,088 after survival placement rejoins the second route.
- The server sees placement consume one red conduit and leave the blue stack intact. The rejoined controller sees the original 15 tagged ingots from the remote cell. Both server sessions and the client exit normally with zero reported client, server or registry errors. The first fixture used integer teleport coordinates, which Minecraft offsets to the block centers. The final run uses explicit decimal coordinates to frame the shared edge.
- The client and server use byte-identical production Ender IO and optional addon jars. The Ender IO SHA-256 is `fffc081b99415720933f54b2b7b0f0e3c7bfed867a56da6e68f3fb84835b25f9`. The observer records 4,487 native samples across the stopped and completed launches with zero activation, foreground or visible-window events. All three frames record a disabled narrator and zero volume in every category.

The server results and logs are under `build/port-runtime/cycle-146-me-colors-camera`. Client reports and frames are under `build/port-evidence/cycle-146-me-colors-camera-client`.

## Next verification

Exercise painted sand through the native Painting Machine recipe, survival placement, entity rendering during a fall, and paint retention after landing or dropping as an item.
