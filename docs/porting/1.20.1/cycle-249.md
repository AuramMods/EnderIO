# Cycle 249 — Refined Storage conduit crafting and live grid transfers

A fresh Forge world contains an empty conduit route between a Refined Storage Creative Controller and Disk Drive. The survival player receives Conduit Binder and Refined Storage Cable. The shipped recipe unlocks in the recipe book and converts six binder and three cables into three Refined Storage Conduits. The player places two of the crafted bundles across the chunk edge, retains the spare, and places a native Refined Storage Grid at the controller.

The storage probe checks that the controller, two conduits, grid and remote drive share one active Refined Storage network. It mounts a native 1k disk and loads 37 tagged iron ingots. The player deposits five through the actual grid and withdraws half of the resulting 42. The server reads the physical disk independently of the menu. Both route chunks evict and reload with 21 ingots on the disk.

After the dedicated restart, the first 21 withdrawn ingots are still in the player's hotbar. The reopened grid accepts three more tagged ingots and returns 12 of the resulting 24. The physical disk holds 12, the player has both exact withdrawals and the spare conduit, and all 45 ingots are accounted for.

## Commands

```sh
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew :enderio:reobfJar :enderio:reobfClientSmokeJar --max-workers=2 --console=plain

port_runtime=build/port-runtime
port_forge="$port_runtime/cycle-047-legacy-discover"
port_seed="$port_runtime/cycle-248-ae2-final-client"
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.248-1.20.1+c4136048d7.jar
port_mods="$port_runtime/cycle-177-tin-fixed-client/mods"
port_ae="$port_mods/ae2-QRpVsqDI.jar"
port_guide="$port_mods/guideme-i7Tp1AHw.jar"
port_rs="$port_mods/refined-storage-ZITLFjjf.jar"
port_probe_dir="$port_runtime/cycle-249-storage-probe"
port_art="$HOME/.gradle/caches/modules-2/files-2.1/net.neoforged/AutoRenamingTool/2.0.17/9388df4c3589faf10c35d9154cfb2451c83bd717/AutoRenamingTool-2.0.17-all.jar"
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' python3 -B scripts/porting/legacy_fixture/build.py \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar" \
  --art "$port_art" --storage-probe --mod "$port_ae" --mod "$port_rs" --output "$port_probe_dir"

export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Djava.net.preferIPv4Stack=true'
run_storage_profile() {
  local port_name="$1"
  shift
  local port_client="$port_runtime/$port_name-client"
  mkdir -p "$port_client/config"
  cp -cR "$port_seed/libraries" "$port_seed/versions" "$port_client/"
  cp "$port_seed/config/fml.toml" "$port_client/config/"
  python3 scripts/porting/client/prepare_packaged.py --name "$port_name-client" --java "$port_java" \
    --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
    --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets" \
    --mod "$port_ae" --mod "$port_guide" --mod "$port_rs"
  python3 -Bu scripts/porting/packaged_storage_terminals.py --name "$port_name" --port 26367 \
    --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --probe "$port_probe_dir/storage-probe.jar" \
    --launch "$port_client/launch.json" --mod "$port_ae" --mod "$port_guide" --mod "$port_rs" "$@"
}

run_storage_profile cycle-249-stock-rs-verified --stock
run_storage_profile cycle-249-converted-storage-control --world "$port_runtime/cycle-139-storage-verified/converted/port-migration"

python3 -Bu scripts/porting/packaged_registry_audit.py --name cycle-249-rs-absent --port 26366 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" \
  --probe "$port_runtime/cycle-165-audit-probe/registry-probe.jar"
```

## Results

- The production jar, separate client QA jar and native storage probe build and remap. See `build/cycle-249-rs-assembly.log` and `build/cycle-249-storage-probe.log`.
- The first stock run crafts and places the route and verifies the initial deposit and withdrawal on the disk. Its final player-inventory assertion fails. The captured hotbar shows that the survival crafting menu put the spare conduit in slot eight. The fixture then occupied that slot when it supplied the grid. The corrected fixture supplies the grid and the first tagged ingot batch before crafting, so the crafting menu has its own free slot. See `build/port-runtime/cycle-249-stock-rs/storage-terminal-results.json` and `build/port-evidence/cycle-249-stock-rs-exercise/rs-crafted-route.png`.
- The corrected fresh-world run passes 21 server assertions and 38 client checks. The grid and remote drive share the crafted route. The disk is verified at 37, 42 and 21 ingots during the first session. It keeps 21 through actual chunk eviction and cold start. The reopened grid raises the disk to 24 and then withdraws 12. The final physical disk contains 12, the player has 21 and 12 in separate slots, and the spare conduit stays in inventory. Both server processes exit cleanly without runtime or registry errors. See `build/port-runtime/cycle-249-stock-rs-verified/storage-terminal-results.json`.
- The converted Forge 6.2 control passes 23 server assertions and 33 client checks on the same build. Both storage menus deposit and retrieve the original tagged items. Both reopen with the exact saved counts, and both servers exit cleanly. See `build/port-runtime/cycle-249-converted-storage-control/storage-terminal-results.json`.
- The core-only audit reconciles 1,343 live Ender IO recipes and ten conduit variants. The Refined Storage recipe and conduit variant are absent with Refined Storage unloaded. The headless server exits cleanly without runtime or registry errors. See `build/port-runtime/cycle-249-rs-absent/registry-audit-results.json` and `registry-snapshot.json` in the same directory.
- All four clients load matching Ender IO and addon jars. Sixteen captures confirm all ten volume categories muted, narrator off and the cursor uncaptured. The native window observer records zero activation, foreground or onscreen events in 7,450 samples. The client QA and server storage probe live in separate test archives.

| Packaged artifact | SHA-256 |
| --- | --- |
| Ender IO | `d8f8f78ed150ed29403a438fe22eb0640bb25412f2cbfd173863a990e998ca5c` |
| AE2 15.4.12 | `b9c5928893a20d3feb0b4393e2c23d756a16d9abee24d9c14c98e6096d53844d` |
| GuideME 20.1.15 | `cf8052ab3da121012efc59a20e731c24bfe7488dfa556c9b88809f42dea3b24a` |
| Refined Storage 1.12.4 | `afda49a01e5ac8e20706032eb80699cc3b50d3b346478324ead62503cb774acd` |
| Server measurement probe | `c1959fa3238f75d7b5585d06febc14cd675e2dca47198955ce615682cdcdfda7` |

## Next verification

Craft the stock redstone conduit and configurable redstone filters in packaged survival. Exercise the resulting bundled route and verify that a configured filter's stock erasure recipe restores its defaults.
