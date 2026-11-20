# Cycle 252 — Shared registries and Reinforced Obsidian dust

The packaged registry audit now compares live menu, block-entity, sound-event and particle-type IDs against the fixed 1.21.1 sources. It expands the three capacitor bank tiers and the three photovoltaic tiers from their baseline enums. The Mekanism menu is required when Mekanism is loaded. The audit also verifies the explicit Forge entries for legacy lights, the primitive alloy smelter and the two backported particle providers.

The steel construction fixture puts a Reinforced Obsidian wall in a fresh survival world. Opaque quartz covers five faces and leaves the south face exposed. The guarded client approaches the wall and measures live vanilla dust particles produced during ordinary client ticks. It then disables machine particles, waits 50 ticks for existing dust to expire, measures another 50 ticks and re-enables the setting. Native sound manager lookups resolve the five machine sound events to resources while all sound categories remain muted.

## Commands

```sh
source build/cycle-177-pins.zsh
port_jar=build/port-evidence/cycle-251-distribution/jars/enderio/enderio-8.2.14.250-dev+fb31bbe8bc.jar
JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4' ./gradlew :enderio:reobfClientSmokeJar --max-workers=2 --console=plain
export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Djava.net.preferIPv4Stack=true'
python3 -Bu scripts/porting/packaged_registry_audit.py --name cycle-252-core-audit --port 26364 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --probe "$port_probe"
python3 -Bu scripts/porting/packaged_registry_audit.py --name cycle-252-combined-audit --port 26365 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --probe "$port_probe" \
  --experiments --aluminum-fixture --unified-furnaces "${port_mods[@]}"

port_name=cycle-252-obsidian
port_profile="$port_runtime/$port_name-client"
port_seed="$port_runtime/cycle-251-core-recheck-client"
mkdir -p "$port_profile/config"
cp -cR "$port_seed/libraries" "$port_seed/versions" "$port_profile/"
cp "$port_seed/config/fml.toml" "$port_profile/config/"
python3 -B scripts/porting/client/prepare_packaged.py --name "$port_name-client" --java "$port_java" \
  --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 -Bu scripts/porting/packaged_steel_construction.py --name "$port_name" --port 26367 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --launch "$port_profile/launch.json"
```

## Results

- Both registry audits pass on the production jar preserved from cycle 251. All 42 baseline block-entity types and three Forge entries load in each profile. The core server loads 40 baseline menus and the primitive smelter menu. Loading Mekanism adds exactly its baseline chemical filter menu. All five expected sound events, the range particle type and two backported particle types load. There are zero missing or unexpected IDs in the four registries. See `build/port-runtime/cycle-252-{core,combined}-audit/registry-audit-results.json`.
- The byte comparison reads the packaged `sounds.json` and its five referenced Ogg files from the production archive and the corresponding blobs at the fixed baseline commit. The definitions and all five samples match byte for byte. See `build/cycle-252-sound-assets.json`.
- The updated client QA archive builds and remaps separately. The production jar has SHA-256 `a75142885db0e9038d954a755ce1fbbe904c4c45c41725d5ea745e061ad2c0fd`. See `build/cycle-252-steel-qa.log`.
- The steel fixture passes 34 client checks and 24 server assertions, including the saved door and bar checks after restart. The native particle renderer holds up to 14 live dust particles with the option enabled, zero during the disabled measurement and eight after reinstatement. Observed dust stays on the exposed side. The native sound manager resolves all five machine resources. Both servers exit cleanly with zero runtime or registry errors. The client and server use matching Ender IO bytes. See `build/port-runtime/cycle-252-obsidian/steel-construction-results.json`.
- Captures of the ladder and all three particle phases confirm every volume at zero, narrator off and the cursor uncaptured. The native observer records zero activation, foreground or onscreen events across 2,177 samples. See `build/port-evidence/cycle-252-obsidian/summary.json` and the four captures in that directory.

## Next verification

Exercise the backported experience effect and SAG Mill dust through their original gameplay triggers in the guarded packaged client. Verify their configured suppression and the actual rendered particles.
