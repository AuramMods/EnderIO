# Cycle 145 — Verify authored ME conduit colors on powered AE2 grids

The source baseline loads ME conduit colors from a datapack and matches adjacent conduits by exact color. Its built-in normal and dense variants use the default fluix color. This cycle loads two test-only conduit definitions through the same data registry, using red and blue and the existing ME texture.

The packaged Forge world has two routes across a chunk boundary. Each has a powered AE2 controller on the left and a separately powered native drive on the right. Matching red conduits in the first route expose 11 tagged ingots from its cell. The red-to-blue route keeps its cell of 15 tagged ingots accessible to the drive's grid alone. Ordinary conduit item replacement changes the second route's color and checks that the native grids join and separate again.

## Commands

The variables `port_java`, `port_forge`, `port_legacy`, `port_art`, `port_ae`, `port_guide` and `port_rs` use the pinned paths from [cycle 139](cycle-139.md).

```sh
./gradlew :enderio-modded-conduits:runGameTestServer --console=plain > build/cycle-145-me-colors-native.log 2>&1
./gradlew build --continue --console=plain > build/cycle-145-me-colors-build.log 2>&1

port_jar=enderio/build/libs/enderio-8.2.14.143-1.20.1+b21de38fb6.jar
python3 scripts/porting/legacy_fixture/build.py \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" --art "$port_art" \
  --storage-probe --mod "$port_ae" --mod "$port_rs" --output build/port-runtime/cycle-145-me-color-probe
python3 scripts/porting/packaged_me_colors.py --name cycle-145-me-colors --port 26021 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" \
  --probe build/port-runtime/cycle-145-me-color-probe/storage-probe.jar \
  --mod "$port_ae" --mod "$port_guide" --mod "$port_rs" > build/cycle-145-me-colors.log 2>&1
```

## Results

- The full build passes 79 unit tests and 517 native runs, including 190 main gameplay cases and 32 addon cases. The new `authoredmecolorskeepadjacentgridsseparateuntilcolorsmatch` case passes in the addon GameTest report at `enderio-modded-conduits/build/test-results/gameTestServer/results.xml`.
- The packaged dedicated run passes all 27 assertions. Native AE2 node color, grid membership and the physical link agree with the saved Ender IO connection status. The two original physical cells retain 11 and 15 tagged ingots. Matching conduits expose those counts from their controllers. The mixed route exposes zero from its controller and still exposes all 15 from the drive's own grid.
- Replacing blue with red lets the original second controller see all 15 ingots. Replacing red with blue isolates them again. Both final states survive eviction and reloading of both chunks and a new dedicated server process. Each server exits normally with zero runtime or registry errors.
- The packaged test uses the same production code as [cycle 144](cycle-144.md). Its jar SHA-256 is `46a72b87083bf0badb0d3696c377f7669e2f30d0d4cc0a6e95d4cdc9bd612afb`. The report and stage logs are in `build/port-runtime/cycle-145-me-colors`.

## Next verification

The powered routing checks cover the authored conduit definitions on the server. A packaged client still needs to verify that it receives the authored registry entries and displays the corresponding names, models and connection geometry.
