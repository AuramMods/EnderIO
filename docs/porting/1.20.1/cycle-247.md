# Cycle 247 — Mekanism heat conduit and chemical filter crafting

The stock Forge world starts with a crafting table, two empty heat conduit positions between a Mekanism Resistive Heater and a Basic Thermodynamic Conductor, and a chemical line between two native tanks. The recipe book unlocks the Basic Chemical Filter from the osmium ingredient and the heat conduit from Conduit Binder. One osmium ingot and four paper yield one filter. Three Advanced Thermodynamic Conductors and six binder yield three heat conduits. The survival player places two of the crafted heat conduits and keeps the spare.

The client installs the crafted filter in the chemical extraction slot, copies an oxygen sample from a portable tank and selects deny mode. The server verifies that the filter retains the ordered sample, leaves oxygen behind and delivers hydrogen. The second client opens the same filter after the dedicated restart.

The heater and conductor use their native Mekanism heat capabilities. The separate test probe prepares a 650 K heater and records the destination's peak temperature over 20 server ticks. The crafted line warms the conductor when extraction is enabled. The client pauses extraction through the heat conduit menu, and the saved setting holds the conductor at ambient before and after restart. Re-enabling extraction through the reopened menu warms the conductor again.

## Commands

```sh
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew :enderio:reobfJar :enderio:reobfClientSmokeJar --max-workers=2 --console=plain

port_runtime=build/port-runtime
port_seed="$port_runtime/cycle-246-chemical-craft-client"
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.246-1.20.1+3e782bd741.jar
port_mek="$port_runtime/cycle-177-tin-fixed-client/mods/mekanism-uxe1WQp4.jar"
port_probe="$port_runtime/cycle-143-heat-probe/mekanism-probe.jar"
export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Djava.net.preferIPv4Stack=true'

run_mek_profile() {
  local port_name="$1"
  shift
  local port_client="$port_runtime/$port_name-client"
  mkdir -p "$port_client/config"
  cp -cR "$port_seed/libraries" "$port_seed/versions" "$port_client/"
  cp "$port_seed/config/fml.toml" "$port_client/config/"
  python3 scripts/porting/client/prepare_packaged.py --name "$port_name-client" --java "$port_java" \
    --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
    --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets" \
    --mod "$port_mek"
  python3 -Bu scripts/porting/packaged_mekanism_controls.py --name "$port_name" --port 26367 \
    --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar" \
    --mekanism "$port_mek" --probe "$port_probe" --launch "$port_client/launch.json" "$@"
}

run_mek_profile cycle-247-stock-mek --stock
run_mek_profile cycle-247-converted-mek-control --world "$port_runtime/cycle-054-mekanism-verified/converted/port-migration"
```

## Results

- The production and QA jars build and reobfuscate. See `build/cycle-247-mek-craft-assembly.log`.
- The fresh survival profile passes 16 server assertions and 38 client checks. Providing ingredients alone unlocks both recipes. Each recipe consumes exactly the required materials, leaving one of each supplied ingredient. The two placed heat conduits connect across the chunk edge and the player keeps the third. The crafted filter saves a 1,450-unit oxygen sample, blocks the complete oxygen batch and passes 1,450 units of hydrogen. Reopening the filter restores the sample and the saved gas balance.
- The native heat target peaks at 645.92 K with the crafted line enabled and stays at 300 K after the menu pause. It stays at 300 K after restart with the saved pause and reaches 645.71 K after the player re-enables extraction. Both server processes exit cleanly without runtime or registry errors. See `build/port-runtime/cycle-247-stock-mek/mekanism-control-results.json`.
- The converted Forge 6.2 control passes 11 server assertions and 28 client checks on the same production build. The four chemical samples, the exact saved hydrogen balance and both heat menu transitions pass. Both server processes exit cleanly without runtime or registry errors. See `build/port-runtime/cycle-247-converted-mek-control/mekanism-control-results.json`.
- All four clients load matching production and Mekanism jars. Eleven captures confirm every volume category muted, narrator off and the cursor uncaptured. The native window observer records zero activation, foreground or onscreen events in 8,818 samples. The client and server probes live in separate test archives.

| Packaged artifact | SHA-256 |
| --- | --- |
| Ender IO | `9792c0eff6f6d942699e2a8c3395a941b447a6e8e1dc57fa748b11941e237a42` |
| Mekanism 10.4.16.80 | `3b0d191fa4a45e662f9725991d76a30192f30186abb2ba694b873b5f7b9358d2` |
| Server measurement probe | `70977de8fca94ee765f42ade4ddad68f6c4a7d28990bb08d3d98a1ffbb904072` |

## Next verification

Craft ME and Dense ME Conduits from all three shipped AE2 recipes and put the freshly crafted parts into a working channel-limited network in packaged survival.
