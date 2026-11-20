# Cycle 248 — AE2 conduit crafting and channel limits

The packaged survival client uses all three stock recipes. Fluix Glass Cable and Orange Covered Cable each produce three standard ME Conduits. Blue Covered Dense Cable produces three Dense ME Conduits. Each craft consumes three native AE2 cables and six Conduit Binder. The client takes the typed and localized results through the crafting table and uses them to build and upgrade the two trunk segments across a chunk boundary.

An AE2 controller and Creative Energy Cell feed 33 native ME Drives through the crafted trunk and the prepared branches. The storage probe reads the controller state, both trunk nodes and each drive's AE2 grid state. The standard trunk and the mixed trunk carry eight channels. Finishing the two-piece dense trunk carries 32 channels. Removing one powered drive lets the waiting drive take its channel. The packaged server also checks the dense trunk and all 32 remaining active drives after actual chunk eviction and a cold restart.

## Commands

```sh
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew :enderio:reobfJar :enderio:reobfClientSmokeJar --max-workers=2 --console=plain

port_runtime=build/port-runtime
port_forge="$port_runtime/cycle-047-legacy-discover"
port_seed="$port_runtime/cycle-247-stock-mek-client"
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.247-1.20.1+26dfb686f1.jar
port_mods="$port_runtime/cycle-177-tin-fixed-client/mods"
port_ae="$port_mods/ae2-QRpVsqDI.jar"
port_guide="$port_mods/guideme-i7Tp1AHw.jar"
port_rs="$port_mods/refined-storage-ZITLFjjf.jar"
port_probe_dir="$port_runtime/cycle-248-channel-probe-fixed"
port_art="$HOME/.gradle/caches/modules-2/files-2.1/net.neoforged/AutoRenamingTool/2.0.17/9388df4c3589faf10c35d9154cfb2451c83bd717/AutoRenamingTool-2.0.17-all.jar"
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' python3 -B scripts/porting/legacy_fixture/build.py \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar" \
  --art "$port_art" --storage-probe --mod "$port_ae" --mod "$port_rs" --output "$port_probe_dir"

port_name=cycle-248-ae2-final
port_client="$port_runtime/$port_name-client"
export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Djava.net.preferIPv4Stack=true'
mkdir -p "$port_client/config"
cp -cR "$port_seed/libraries" "$port_seed/versions" "$port_client/"
cp "$port_seed/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name "$port_name-client" --java "$port_java" \
  --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets" \
  --mod "$port_ae" --mod "$port_guide" --mod "$port_rs"
python3 -Bu scripts/porting/packaged_ae2_progression.py --name "$port_name" --port 26367 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --probe "$port_probe_dir/storage-probe.jar" \
  --launch "$port_client/launch.json" --mod "$port_ae" --mod "$port_guide" --mod "$port_rs"

python3 -Bu scripts/porting/packaged_registry_audit.py --name cycle-248-ae2-absent --port 26366 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" \
  --probe "$port_runtime/cycle-165-audit-probe/registry-probe.jar"
```

## Results

- The first run crafts all three recipes and measures eight active channels, but the grid counts only 32 drives. The saved bundle at the last branch has its west link and none of its five drive links. The fixture now places the drives first, allows five native ticks for their nodes to initialize, and then adds the branch conduits. See `build/port-runtime/cycle-248-ae2-craft/ae2-progression-results.json`.
- The next run verifies each of the three channel limits. The test-only drive removal command calls an inherited Minecraft method through the AE2 subclass and hits `NoSuchMethodError` in the packaged profile. Calling through `BlockEntity` gives the remapper the method owner. See `build/port-runtime/cycle-248-ae2-verified/smoke-exercise.log` and `build/cycle-248-channel-probe-fixed.log`.
- The final packaged run passes 17 server assertions and 24 client checks. The three recipes unlock from the ingredients, leave one spare of each supplied material, and produce six standard and three dense conduits. Both native upgrades refund one standard part. The standard and mixed routes each carry eight channels, while the dense route activates 32 of 33 drives. Breaking one active drive leaves all remaining 32 active, including the original waiting drive. Both trunk chunks are evicted and reloaded. The 32 active channels are also restored on a dedicated cold start. Both server processes exit cleanly without runtime or registry errors. See `build/port-runtime/cycle-248-ae2-final/ae2-progression-results.json`.
- The core-only runtime audit reconciles 1,343 live Ender IO recipes and ten conduit variants. All three AE2 recipe IDs and both ME conduit variants are absent with AE2 unloaded. The headless server exits cleanly without runtime or registry errors. See `build/port-runtime/cycle-248-ae2-absent/registry-audit-results.json` and `registry-snapshot.json` in the same directory.
- The survival client and server load matching Ender IO, AE2, GuideME and Refined Storage jars. Four captures confirm all ten volume categories muted, narrator off and the cursor uncaptured. The native window observer records zero activation, foreground or onscreen events in 1,725 samples. The client and server probes live in separate test archives. See `build/port-evidence/cycle-248-ae2-final-client/summary.json`.

| Packaged artifact | SHA-256 |
| --- | --- |
| Ender IO | `d85c4714d8e7e50a1bbf0a7f540240a7c86f0f59bc5eb1ee1024abbdcdc547f4` |
| AE2 15.4.12 | `b9c5928893a20d3feb0b4393e2c23d756a16d9abee24d9c14c98e6096d53844d` |
| GuideME 20.1.15 | `cf8052ab3da121012efc59a20e731c24bfe7488dfa556c9b88809f42dea3b24a` |
| Refined Storage 1.12.4 | `afda49a01e5ac8e20706032eb80699cc3b50d3b346478324ead62503cb774acd` |
| Server measurement probe | `929b4593c2cd5432892a5d8b0fb8d0403758c823baa28917fc120be4de7cc8f8` |

## Next verification

Craft the stock Refined Storage conduit in packaged survival. Use the crafted pieces to connect a native controller, grid and drive, then verify that the item operations resume after the world reopens.
