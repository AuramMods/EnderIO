# Cycle 222 — Ultimine shapes and canceled breaks

The dedicated Forge fixture exercises the five directional shapes in FTB Ultimine 2001.1.8. It sends the native mode-change packets while holding the Ultimine key and reads the actual server preview before using normal survival mining. The separate Forge-only profile uses the same geometry and action with the optional mods absent.

| Trial | Fixture |
| --- | --- |
| Small Tunnel | A line of oak facades stops at birch, with another oak facade behind the birch and one outside the tunnel |
| Small Square | An offhand Yeta Wrench exposes the shared energy network across a chunk edge. The same plane also contains a redstone-only bundle and an isolated energy bundle. An attached energy bundle behind the plane remains outside the square |
| Large Tunnel | The 3-wide tunnel crosses a chunk edge and includes a mixed bundle whose Forge break event is canceled. An energy connection outside the footprint stays intact |
| Mining Tunnel | The descending oak steps match the preview. A rising step and a level step remain outside it |
| Escape Tunnel | The rising oak steps match the preview. The descending and level steps remain outside it |
| Canceled origin | A mixed bundle with an oak facade and its matching neighbor stay intact after the origin break is refused. The client checks the restored block entity |

Only the separate QA jar provides the fixture event listeners. One cancels genuine Forge break events at exact coordinates tagged on the disposable test player. The other prevents that player from picking up the drops before the runner measures them. The runner compares the cancellation coordinates, the surviving conduit types and paints, and the exact tagged item drops. It repeats the world checks after a dedicated restart.

## Commands

```sh
./gradlew :enderio:assemble --console=plain > build/cycle-222-ultimine-baseline-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-222-ultimine-qa.log 2>&1
./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-222-ultimine-full-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-222-ultimine-fixture-qa.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_jar=enderio/build/libs/enderio-8.2.14.221-1.20.1+50c642ab02.jar
port_qa=enderio/build/test-libraries/enderio-client-qa.jar
port_source="$port_runtime/cycle-221-solar-core-retry-client"
port_mods="$port_runtime/cycle-177-tin-fixed-client/mods"
port_ult="$port_mods/ftb-ultimine-forge-2001.1.8.jar"
port_lib="$port_mods/ftb-library-forge-2001.2.13.jar"
port_arch="$port_mods/architectury-api-1MKTLiiG.jar"
export JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true

run_shapes_profile() {
  port_profile="$1"
  port_number="$2"
  port_client="$port_runtime/$port_profile-client"
  shift 2
  port_client_mods=()
  if [ "$#" -gt 0 ]; then
    port_client_mods=(--mod "$port_ult" --mod "$port_lib" --mod "$port_arch")
  fi
  mkdir -p "$port_client/config"
  cp -cR "$port_source/libraries" "$port_source/versions" "$port_client/"
  cp "$port_source/config/fml.toml" "$port_client/config/"
  python3 scripts/porting/client/prepare_packaged.py --name "$port_profile-client" \
    --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
    --qa-jar "$port_qa" --assets "$HOME/.gradle/caches/neoformruntime/assets" "${port_client_mods[@]}"
  python3 -u scripts/porting/packaged_ultimine_shapes.py --name "$port_profile" --port "$port_number" \
    --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar" \
    --qa-jar "$port_qa" --launch "$port_client/launch.json" "$@"
}
run_shapes_profile cycle-222-ultimine-enabled-fixed 26269 --ultimine "$port_ult" "$port_lib" "$port_arch"
run_shapes_profile cycle-222-ultimine-core-retry 26271
```

## Results

- The full Java 17 build passes 79 unit tests and 543 native tests, including all 215 discovered gameplay cases. The production jar and each of its three nested jars exclude the client QA classes. The packaged profiles use the same production bytes as the full build.
- The first enabled run receives the exact three-position Small Tunnel preview and removes the three expected facades. Both initial fixtures fail to count the resulting drops because the floor only covers the player approach. The closed Forge-only world contains the original tagged oak facade at y=32.83 under the y=80 fixture. The corrected fixture extends the floor beneath the mining and drop positions.
- Both runs with expanded flooring pass Small Tunnel. The next comparison expects an explicit empty `Conduits` list at the wrench-mined origin. Both servers report the original oak facade and omit `Conduits` when the bundle is empty. The enabled server also preserves Redstone and the birch facade on the selected neighbor. The revised comparison checks paint and verifies that there is no conduit at index zero. The floor extends below the descending trial to catch its drops as well.
- The next Forge-only run passes the corrected Small Square layout comparison. The saved player inventory contains the expected tagged energy conduit after the player automatically picks up the drop. The fixture now cancels pickup for the tagged test player so the dropped stack stays available for both world checks. The enabled profile times out during one loopback login and is relaunched in a fresh profile.
- With Ultimine present, both dedicated sessions exit cleanly. The harness passes 49 server assertions and the client passes all 33 checks. The actual preview selects three Small Tunnel facades, two Small Square energy conduits, five Large Tunnel positions, three Mining Tunnel facades, three Escape Tunnel facades and both positions in the protected-origin Small Square. The only Forge cancellations are the mixed Large Tunnel neighbor at `(2880, 80, 67)` and the protected origin at `(2879, 80, 110)`. The Large Tunnel removes four energy conduits and preserves both conduit types at the canceled neighbor. The protected origin produces zero drops, and the client restores its oak paint and both conduit types. Across the six trials, the exact drop totals are `3, 2, 4, 3, 3, 0` before and after restart. See `build/port-runtime/cycle-222-ultimine-enabled-fixed/ultimine-shapes-results.json`.
- The final Forge-only launch first disconnects during loopback login. A fresh copy with the same jars passes all 49 server assertions and 27 client checks. Ordinary mining removes the one targeted part in each of the first five trials. The protected origin retains its paint and both conduit types. Exact drop totals are `1, 1, 1, 1, 1, 0` before and after restart. See `build/port-runtime/cycle-222-ultimine-core-retry/ultimine-shapes-results.json`.
- The native focus observer records zero activations, foreground samples or onscreen windows across 3,424 Ultimine and 2,617 Forge-only samples. All 12 client captures record all ten volumes at zero and narrator `OFF`. The client and server load jars with matching hashes. See the two profile summaries in `build/port-evidence/`.

| Pinned jar | SHA-256 |
| --- | --- |
| Ender IO `8.2.14.221-1.20.1+50c642ab02` | `82be3aba1e809b284006824517df08702f6d851f6247dfc587df9b720064a111` |
| Test-only client QA jar | `31ff043c6cf9a158bc357d7153425e6592b0e31170709b307fab84379eef596a` |
| FTB Ultimine `2001.1.8` | `b80dde55b2c91fd27f09f15ed2aefb0756c7bd704316e488ae8a06b179e85730` |
| FTB Library `2001.2.13` | `41810281b65336265cae2c88412022d02f2f0a3f28c66a06173d86f98acf14a7` |
| Architectury `9.2.14+forge` | `218b471d0b8a1f6cda14cfc1beb9eeb0df54304500acc6c5613d9b88ec65d9af` |

## Next verification

Exercise the powered Alloy Smelter category in packaged JEI. Verify transfer of sized ingredients and exact processing with the native menu before and after a server recipe reload.
