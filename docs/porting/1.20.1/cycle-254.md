# Cycle 254 — AE2 terminal help over a live ME conduit route

AE2 15.4.12 declares GuideME `[20.1.7,20.2.0)` as a mandatory Forge dependency on both sides. Ender IO's optional AE2 integration still activates from the AE2 mod ID. The packaged test installs AE2 and GuideME 20.1.15 together. A GuideME-only server checks that loading the guide library by itself leaves the ten core conduit variants.

The dedicated fixture starts from a copy of the converted Forge 6.2 storage world. The native storage probe checks each grid and its owner data, including the two regular ME conduits between the terminal's power source and the remote drive. The survival player opens the native powered ME terminal and clicks its Help button. The guard observes the actual GuideME screen, the AE2 Terminals page from the bundled guide, and normal document scrolling. Escape returns to the original terminal and its tagged item balance.

The survival player then deposits five ingots through the terminal, withdraws 21 and keeps them in the original hotbar slot. The server reads the physical cell and native storage view independently of the client. The test restarts the dedicated server, opens the same powered terminal and repeats the guide round trip with the saved balance.

## Commands

```sh
source build/cycle-177-pins.zsh
port_jar=build/port-evidence/cycle-251-distribution/jars/enderio/enderio-8.2.14.250-dev+fb31bbe8bc.jar
port_inputs="$port_runtime/cycle-251-combined-audit/mods"
port_guide="$port_inputs/guideme-i7Tp1AHw.jar"
port_mod_args=(--mod "$port_inputs/ae2-QRpVsqDI.jar" --mod "$port_guide" --mod "$port_inputs/refined-storage-ZITLFjjf.jar")
port_name=cycle-254-ae-guide
port_profile="$port_runtime/$port_name-client"
port_seed="$port_runtime/cycle-249-converted-storage-control-client"
port_world="$port_runtime/cycle-139-storage-verified/converted/port-migration"
port_storage_probe="$port_runtime/cycle-249-storage-probe/storage-probe.jar"
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew :enderio:reobfClientSmokeJar --max-workers=2 --console=plain
export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Djava.net.preferIPv4Stack=true'

python3 -Bu scripts/porting/packaged_registry_audit.py --name cycle-254-guideme-only --port 26365 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --probe "$port_probe" --mod "$port_guide"

mkdir -p "$port_profile/config"
cp -cR "$port_seed/libraries" "$port_seed/versions" "$port_profile/"
cp "$port_seed/config/fml.toml" "$port_profile/config/"
python3 -B scripts/porting/client/prepare_packaged.py --name "$port_name-client" --java "$port_java" \
  --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets" "${port_mod_args[@]}"
python3 -Bu scripts/porting/packaged_storage_terminals.py --name "$port_name" --port 26367 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --launch "$port_profile/launch.json" \
  --world "$port_world" --probe "$port_storage_probe" --ae-guide "${port_mod_args[@]}"

port_control=cycle-254-guideme-only-client
port_profile="$port_runtime/$port_control"
port_seed="$port_runtime/cycle-253-gameplay-final-client"
mkdir -p "$port_profile/config"
cp -cR "$port_seed/libraries" "$port_seed/versions" "$port_profile/"
cp "$port_seed/config/fml.toml" "$port_profile/config/"
python3 -B scripts/porting/client/prepare_packaged.py --name "$port_control" --java "$port_java" \
  --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets" --mod "$port_guide"
python3 -B scripts/porting/client/run_smoke.py --launch "$port_profile/launch.json" \
  --scenario title --output "build/port-evidence/$port_control" --timeout 135
```

## Results

- The GuideME-only packaged audit passes. The snapshot includes GuideME and the ten core conduit variants. The AE2 recipes and variant IDs remain conditional on AE2. The headless server exits cleanly. See `build/port-runtime/cycle-254-guideme-only/{registry-audit-results,registry-snapshot}.json`.
- The separate client QA jar builds and remaps. The main jar used for both profiles is the verified distribution from cycle 251 with SHA-256 `a75142885db0e9038d954a755ce1fbbe904c4c45c41725d5ea745e061ad2c0fd`. See `build/cycle-254-guide-qa.log`.
- The fresh and restarted sessions pass 39 client checks and 15 server assertions. The native Help button shows `ae2:items-blocks-machines/terminals.md` in GuideME. The captures show the original text and 3D terminal scene. A native scroll advances 40 document pixels on both sessions, and Escape restores the same live terminal and balance. The original ME route keeps its native owners and links. The client and physical cell agree on 37, 42 and 21 tagged ingots, then reopen with 21 after restart. Both dedicated servers exit cleanly. See `build/port-runtime/cycle-254-ae-guide/storage-terminal-results.json` and `build/port-evidence/cycle-254-ae-guide-{exercise,verify}/summary.json`.
- With GuideME as the only optional mod, the packaged client reaches the title screen and bakes all 12 sampled Ender IO machine states. Across the three launches, all nine captures confirm every volume at zero, narrator off and the cursor uncaptured. The native observer records zero activation, foreground or onscreen events in 4,065 samples. Both terminal sessions load byte-identical Ender IO, AE2, GuideME and Refined Storage artifacts on each peer. See `build/port-evidence/cycle-254-guideme-only-client/summary.json` and the storage report's artifact hashes.

## Next verification

Audit the full completion checklist against the fixed baseline, packaged runtime reports, optional integration evidence and historical fixtures. Identify every open requirement and prepare the installation and migration instructions from verified behavior.
