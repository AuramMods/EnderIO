# Cycle 168 — Packaged model inventory and conduit probe localization

The packaged client audits every registered Ender IO item and every block state that uses a model. It compares Forge's baked registry with the fixed 1.21.1 inventory before and after a normal resource reload. It also checks the actual textured quads for the empty and filled Soul and Void Vials, the two cereal and probe appearances, and all three photovoltaic items. The last three bake geometry inherited from the baseline photovoltaic parent model.

The first client run showed that the Probe and Copy/Paste modes and the copied-data heading had each inherited a different label. The copied energy conduit displayed the raw key `item.enderio.conduit.energy`. The fix puts all 35 packaged translations under their intended keys. The probe uses the conduit description from the loaded world and keeps it translatable in both confirmations. If the definition is unavailable, the tooltip uses the standard conduit translation and falls back to the resource ID. The standalone Probe inspection action is also a stub in the 1.21.1 source. Copy/Paste is the implemented action.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_before=enderio/build/libs/enderio-8.2.14.166-1.20.1+9d04391939.jar
port_jar=enderio/build/libs/enderio-8.2.14.167-1.20.1+97e9d2f05a.jar
port_qa=enderio/build/test-libraries/enderio-client-qa.jar
port_installer=build/port-runtime/forge-47.4.28-installer.jar
port_assets="$HOME/.gradle/caches/neoformruntime/assets"
port_mekanism=build/port-runtime/cycle-164-fixed/mods/mekanism-uxe1WQp4.jar

./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-168-client-build.log 2>&1
for port_profile in cycle-168-model-core cycle-168-model-mekanism; do
  port_target="build/port-runtime/$port_profile"
  mkdir -p "$port_target/config"
  cp -cR build/port-runtime/cycle-163-default-client/libraries \
    build/port-runtime/cycle-163-default-client/versions "$port_target/"
  cp build/port-runtime/cycle-163-default-client/config/fml.toml "$port_target/config/"
done
python3 scripts/porting/client/prepare_packaged.py --name cycle-168-model-core --java "$port_java" \
  --installer "$port_installer" --jar "$port_before" --qa-jar "$port_qa" --assets "$port_assets"
python3 scripts/porting/client/run_smoke.py --launch build/port-runtime/cycle-168-model-core/launch.json \
  --scenario model-inventory --output build/port-evidence/cycle-168-before --timeout 150

./gradlew :enderio:runData --console=plain > build/cycle-168-datagen.log 2>&1
./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-168-with-probe-build.log 2>&1
python3 scripts/porting/client/prepare_packaged.py --name cycle-168-model-core --java "$port_java" \
  --installer "$port_installer" --jar "$port_jar" --qa-jar "$port_qa" --assets "$port_assets"
python3 scripts/porting/client/prepare_packaged.py --name cycle-168-model-mekanism --java "$port_java" \
  --installer "$port_installer" --jar "$port_jar" --qa-jar "$port_qa" --assets "$port_assets" --mod "$port_mekanism"
printf 'lang:fr_fr\n' >> build/port-runtime/cycle-168-model-mekanism/options.txt
python3 scripts/porting/client/run_smoke.py --launch build/port-runtime/cycle-168-model-core/launch.json \
  --scenario model-inventory --output build/port-evidence/cycle-168-verified-core --timeout 150
python3 scripts/porting/client/run_smoke.py --launch build/port-runtime/cycle-168-model-mekanism/launch.json \
  --scenario model-inventory --output build/port-evidence/cycle-168-verified-mekanism --timeout 150
python3 scripts/porting/client/audit_models.py --core build/port-evidence/cycle-168-verified-core/summary.json \
  --mekanism build/port-evidence/cycle-168-verified-mekanism/summary.json \
  > build/port-evidence/cycle-168-model-reconciliation.json
```

## Results

- The first packaged run loaded all registered models and failed the two probe tooltip checks. The Void Vial correctly showed 100/3200 mB and five experience points. The filled Soul Vial showed the Cow label and the filled model.
- Both corrected profiles pass all 19 checks. The core profile has 967 registered items and the Mekanism 10.4.16.80 profile has 968. The profiles match all 938 and 939 applicable baseline item IDs, respectively. Both contain all 825 baseline block IDs among 833 total registrations. Each audit finds 3,416 baked block states and 198 engine-managed invisible states. The conduit, facade and painted models account for the 550 block states and 17 inventory items that have no static particle sprite without model data. Their connected, faced and painted geometry is exercised in the individual feature fixtures.
- The live client renders all 11 fixture appearances and preserves their sprites and hover text through a resource reload. Normal mode, Copy/Paste, the copied-data heading and the energy conduit name are correct in both English and French. The synthetic unresolved conduit appears by resource ID. The Mekanism profile also bakes the otherwise unregistered chemical filter.
- The native Forge test clicks the real west and east conduit connectors with the probe, copies a disabled extraction flag and priority 7, then checks the entire pasted connection configuration. The world-backed tooltip and both server confirmations preserve the conduit name as a translatable component. Datagen and the full build pass, with all 79 unit tests and 530 native runs passing and zero skips. All 203 discovered main gameplay cases execute.
- Both verified profiles have zero model bakery warnings and zero runtime errors. The atlas reports unused frames in the active smelter sheets and a mip suggestion for one 24×24 item-conduit sprite. The source animation deliberately uses the sequence 0, 1, 2, 3, 2, 1, and the Alloy Smelter and conduit assets match the baseline bytes. The native focus watcher reports zero foreground, activation or on-screen samples in 579 core and 636 Mekanism observations. Every game volume remains zero and the narrator is off.
- Packaged evidence is under `build/port-evidence/cycle-168-{before,verified-core,verified-mekanism}/`, including the PNG captures and the actual per-ID and per-state inventories. The production jar has SHA-256 `bc109602eb35ba649c8db945603acab8d5c31e82912506ded7c295811be81b73`.

## Next verification

Continue through the remaining active-use and recipe-display paths, including Vial of the Void collection, transfer and drinking in a packaged world. Continue compatibility coverage for connected textures and shader combinations.
