# Cycle 158 — Verify SAG Mill recipes and transfer with JEI

The packaged client uses the pinned JEI 15.56.0.205 recipe manager and viewer. The stock iron-ore recipe resolves both ordinary and deepslate ore through the Forge ingredient tag. Searches by raw iron and cobblestone find the same recipe. The viewer shows one guaranteed raw iron, one raw iron at 33% and one cobblestone at 15%. Both bonus tooltips identify the grinding-ball modifier.

The flint focus displays 120% main output, 125% bonus output and 85% power use. The layout renders 2,040 µI. The stock iron-ingot recipe has no grinding-ball options and displays 2,400 µI. A live datapack reload removes flint and adds a feather with 200% main output, 300% bonus output and 50% power use. The actual JEI lookup and rendered layout adopt the new ball and display 1,200 µI. The removed flint stops matching the ore recipe in JEI.

With JEI on both peers, the native JEI transfer button moves one tagged deepslate iron ore to the SAG Mill. The player retains the second tagged ore and two tagged flint. JEI on the client alone still displays and reloads the recipes. Its generic transfer handler requires JEI on the server and shows the corresponding tooltip. The survival menu can transfer the same ore manually in that profile. Both fixtures grind the ore at 2,400 FE without a ball, deliver the exact saved output roll to a hopper, and keep that output through dedicated restart.

## Commands

```sh
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-158-sag-jei-final-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-158-sag-jei-tooltip-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_previous=build/port-runtime/cycle-158-jei-before-client
port_client=build/port-runtime/cycle-158-jei-final-client
port_jar=enderio/build/libs/enderio-8.2.14.157-1.20.1+2e85579d37.jar
port_jei="$port_previous/mods/jei-9jqubC9n.jar"
mkdir -p "$port_client/config"
cp -cR "$port_previous/libraries" "$port_previous/versions" "$port_client/"
cp "$port_previous/config/fml.toml" "$port_client/config/fml.toml"
python3 scripts/porting/client/prepare_packaged.py --name cycle-158-jei-final-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets" \
  --mod "$port_jei"
python3 scripts/porting/packaged_sag_jei.py --name cycle-158-jei-tooltip-client-only --port 26060 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --launch "$port_client/launch.json" \
  > build/cycle-158-sag-jei-tooltip-client-only.log 2>&1
python3 scripts/porting/packaged_sag_jei.py --name cycle-158-jei-tooltip-both --port 26061 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --jei "$port_jei" \
  --launch "$port_client/launch.json" > build/cycle-158-sag-jei-tooltip-both.log 2>&1
```

## Results

- The full build passes 79 unit cases and 522 native runs, including all 195 main gameplay cases. The final reobfuscated QA jar builds with the active JEI tooltip API.
- The first probe opened the correct recipe and showed flint while connected to an Ender IO server without JEI. Its automatic-transfer assertion failed. The pinned JEI handler requires server installation for this operation, so the fixture now checks its diagnostic on the client-only profile and exercises the actual button with JEI on both peers.
- Both final profiles pass 22 client checks and five server assertions each. Each native hopper receives the one raw iron that its job rolled, and both dedicated restarts preserve that output. The two captured recipe frames in each profile display the expected ore, ball, three outputs and energy cost.
- The four server stages and both clients exit without runtime or registry errors. The native observer records zero activation, foreground or visible-window events across 3,405 samples. In all six captures, every volume is zero and the narrator is off.

The server reports are under `build/port-runtime/cycle-158-jei-tooltip-client-only` and `build/port-runtime/cycle-158-jei-tooltip-both`. Client reports and screenshots are under `build/port-evidence` with `-client` appended to those profile names.

| Artifact | SHA-256 |
| --- | --- |
| Bundled Ender IO | `017af140f5626802cdef94c00885e169b0066b041290996d307b4e8512354eaf` |
| Reobfuscated client QA | `2b3457abf02a182a79f0ef4d28ff643f5f00ccab52154dcf30d5c64963ebfa45` |
| JEI 15.56.0.205 for Forge | `6b251e60a4719c874da89d750af97aa6498f6f4475e3ae79cb1288c7bf0cb65c` |

## Next verification

Exercise stock Slice'N'Splice head recipes with native tool wear, sided automation, menu synchronization and persistence.
