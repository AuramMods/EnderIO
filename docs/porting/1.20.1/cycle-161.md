# Cycle 161 — Verify Slice’N’Splice recipes and tool choices in JEI

The Slice’N’Splice JEI layout now uses the same tool rules as the machine’s slots. The category builds its axe and shears choices from the registered items the slicer accepts. This keeps the viewer and ingredient lookup consistent with usable tools, including registered modded tools that satisfy the same rules.

The packaged test checks all five stock recipe registrations and presses the real JEI transfer button in a survival menu. It supplies the Z-Logic Controller ingredients out of order, including tagged stacks with extra Soularium and Silicon. The test checks the exact six one-item placements and the untouched tools, capacitor and surplus ingredients.

The fixture then replaces a custom recipe through a live datapack reload. The replacement swaps two pairs of input positions, changes the cost from 2,400 to 1,800 FE and replaces the previous result with two tagged quartz. The test verifies the changed JEI output lookup and slot validation, runs the new transfer, and checks that the original and custom outputs survive dedicated restart.

## Commands

```sh
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-161-slicer-jei-probe-build.log 2>&1
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-161-slicer-jei-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_previous=build/port-runtime/cycle-158-jei-final-client
port_jei="$port_previous/mods/jei-9jqubC9n.jar"
port_before=enderio/build/libs/enderio-8.2.14.159-1.20.1+dbbc92c082.jar
port_jar=enderio/build/libs/enderio-8.2.14.160-1.20.1+87dbb01633.jar
port_before_client=build/port-runtime/cycle-161-slicer-jei-before-client
port_client=build/port-runtime/cycle-161-slicer-jei-client
mkdir -p "$port_before_client/config" "$port_client/config"
for port_profile in "$port_before_client" "$port_client"; do
  cp -cR "$port_previous/libraries" "$port_previous/versions" "$port_profile/"
  cp "$port_previous/config/fml.toml" "$port_profile/config/fml.toml"
done
python3 scripts/porting/client/prepare_packaged.py --name cycle-161-slicer-jei-before-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_before" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets" \
  --mod "$port_jei"
python3 scripts/porting/packaged_slicer_jei.py --name cycle-161-slicer-jei-before --port 26072 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_before" --jei "$port_jei" \
  --launch "$port_before_client/launch.json" > build/cycle-161-slicer-jei-before.log 2>&1
python3 scripts/porting/client/prepare_packaged.py --name cycle-161-slicer-jei-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets" \
  --mod "$port_jei"
python3 scripts/porting/packaged_slicer_jei.py --name cycle-161-slicer-jei-final --port 26073 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --jei "$port_jei" \
  --launch "$port_client/launch.json" > build/cycle-161-slicer-jei-final.log 2>&1
python3 scripts/porting/packaged_slicer.py --name cycle-161-slicer-core --port 26074 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" > build/cycle-161-slicer-core.log 2>&1
```

## Results

- The baseline runtime finds all five stock recipes and the authored recipe, then fails because the JEI tool choices differ from the actual machine slots. Its client report is in `build/port-evidence/cycle-161-slicer-jei-before-client`.
- The full build passes 79 unit cases and 524 native runs, including all 197 main gameplay cases.
- Packaged Minecraft passes 28 client checks and seven server assertions with JEI installed on both peers. The tool slots and JEI list accept the same items, and iron axes and shears find the stock recipe in the catalyst lookup. The two survival transfers align every input and keep its tag. The stock recipe uses exactly 20,000 FE. The reloaded recipe replaces its previous JEI result, uses exactly 1,800 FE and delivers two tagged quartz. Both outputs and two uses of wear per tool persist after restart.
- A separate core-only profile passes 33 assertions across three server runs. All five stock recipes preserve their exact saved tasks, outputs and wear. The same runtime jar serves both profiles.
- The final JEI client and all server stages exit normally. The native observer records zero activation, foreground and visible-window samples across 1,742 observations. Every volume is zero and the narrator is off in all five captures.

The two server reports are in `build/port-runtime/cycle-161-slicer-jei-final` and `build/port-runtime/cycle-161-slicer-core`. The client report and captures are in `build/port-evidence/cycle-161-slicer-jei-final-client`.

| Artifact | SHA-256 |
| --- | --- |
| Bundled Ender IO | `33d6f8bf815d4ad8668e6a45368f81035ed4abea157b94ad0713c1161c2244b0` |
| JEI 15.56.0.205 for Forge | `6b251e60a4719c874da89d750af97aa6498f6f4475e3ae79cb1288c7bf0cb65c` |

## Next verification

Exercise the direct and upgrade recipes for the Enhanced and Ender Item Conduits. Measure transfer budgets on uniform and mixed routes, then verify survival replacement and dedicated restart.
