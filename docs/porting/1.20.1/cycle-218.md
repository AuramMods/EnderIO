# Cycle 218 — LaserIO editor and saved chemical filters

LaserIO 1.6.8 stores Basic Filter ghost samples in the item's `inv` tag. The Mekanism bridge opened a fresh handler, so it ignored saved samples. A native Forge test reproduced the failure with a Basic Filter containing portable Mekanism tanks in four spaced ghost slots. Each held 1,450 units of oxygen, redstone infusion, red pigment or dirty iron slurry. The first matching 4,321-unit offer failed with `Persisted GAS sample should allow the full observed batch`.

The bridge now reads LaserIO's saved inventory through `FilterBasic.getInventory`, the accessor already used by the item and fluid bridges. The same regression reopens the serialized item and checks that all four families pass the full offered amount. It also verifies that an oxygen allowlist rejects unlisted hydrogen and that deny mode rejects oxygen and accepts the full 2,345-unit hydrogen offer.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_jar=enderio/build/libs/enderio-8.2.14.217-1.20.1+2b6bc61097.jar
port_mods="$port_runtime/cycle-177-tin-fixed-client/mods"
port_laser="$port_mods/laserio-626839-5074767.jar"
port_patch="$port_mods/patchouli-8Qyw08ld.jar"
port_mek="$port_mods/mekanism-uxe1WQp4.jar"
port_source="$port_runtime/cycle-217-laserio-guide-ipv4-client"
port_run=cycle-218-stock-laserio-editor-verified
port_client="$port_runtime/$port_run-client"

./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain \
  > build/cycle-218-laserio-editor-fixed-build.log 2>&1
mkdir -p "$port_client/config"
cp -cR "$port_source/libraries" "$port_source/versions" "$port_client/"
cp "$port_source/config/fml.toml" "$port_client/config/"
JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true \
  python3 scripts/porting/client/prepare_packaged.py --name "$port_run-client" \
  --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets" \
  --mod "$port_laser" --mod "$port_patch" --mod "$port_mek"
JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true \
  python3 -u scripts/porting/packaged_laserio_editor.py --name "$port_run" --port 26240 \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar" \
  --laserio "$port_laser" --patchouli "$port_patch" --mekanism "$port_mek" --launch "$port_client/launch.json"
```

## Results

- The native regression failed before the production change. See `build/cycle-218-laser-chemical-persisted-before.log` and `build/cycle-218-laser-chemical-persisted-before-results.xml`.
- The fixed build passes 79 unit cases and 542 native Forge GameTests, including 33 addon tests. The client probe reobfuscates separately. Inspection of the production jar and its nested jars finds no client QA classes.
- The first packaged attempt reaches the native item editor and installation before Minecraft rejects the harness's indexed NBT predicate. The second attempt verifies both native installations and the initial transfers. On restart, the receiver retains the loaded 4,321 units of oxygen when the harness supplies an empty Mekanism tank list. The final fixture uses the working conduit inventory predicate and explicitly writes an empty gas stack to tank zero. It checks the original delivery survived the restart before clearing the receiver for the hydrogen test.
- The packaged survival client passes 23 checks on IPv4 loopback. The actual LaserIO editor saves a tagged paper deny list with exact NBT matching and an oxygen allowlist. Each has a sample in ghost slot three. The physical samples stay with the player and the edited stacks move into the native conduit extraction slots. The matching profiles load identical production jars on both peers.
- The packaged server passes 17 assertions over two boots. Each run keeps five paper items matching the exact tag upstream and delivers seven differently tagged paper items and three iron ingots. The restarted output contains 14 paper items and six ingots. The chemical filter rejects 2,345 units of hydrogen into an empty receiver on both boots. It delivers 4,321 units of oxygen before restart, restores that exact saved amount and delivers a fresh 5,678-unit batch after the receiver is cleared for the second run. Both boots exit cleanly with no runtime or registry errors. See `build/port-runtime/cycle-218-stock-laserio-editor-verified/laserio-editor-results.json`.
- The native window watcher records 1,561 samples with zero activation, foreground or onscreen observations. Four captures record every volume category at zero and narrator off. See `build/port-evidence/cycle-218-stock-laserio-editor-verified-client/summary.json`.

| Packaged artifact | SHA-256 |
| --- | --- |
| Ender IO | `d90a3167e12151ca5923c192b56cf2e9a264869574f972c397b1ab2ca87bd444` |
| LaserIO 1.6.8 | `f07fc6cb7f2aa28197e87ffc3a35062390259ad803d8996b08b13473c0af6790` |
| Patchouli 1.20.1-84.1-forge | `9cc38ebb5e29b350222e337b7a5a2ba238c802230a89434ce00e90ee3026d056` |
| Mekanism 10.4.16.80 | `3b0d191fa4a45e662f9725991d76a30192f30186abb2ba694b873b5f7b9358d2` |

## Next verification

Exercise CC:Tweaked bundled redstone receivers across actual chunk eviction and reconnection. The existing packaged test covers a normal computer, a physical split and dedicated restart. See [cycle 094](cycle-094.md).
