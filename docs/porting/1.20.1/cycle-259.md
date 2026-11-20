# Cycle 259 — Recover custom Forge 6.2 grinding balls

Forge 6.2 stores the active SAG Mill ball's recipe ID and wear. The original datapack defines the modifiers. The port now accepts the historical `enderio:grinding_ball` recipe and resolves the saved ID when the mill loads with a level. It stores the resolved modifiers as a snapshot alongside the original ID and wear. Stock saves still have the released Forge 6.2 fallback values. Missing custom definitions leave the original ID and wear available for a later load.

The same recipe definition supplies a new ball to the hopper, machine, tooltip and JEI when the item lacks a modern data-map entry. Modern entries have priority for newly inserted balls. Active snapshots and paid jobs retain their captured values through recipe changes.

The packaged fixture starts with the released Forge 6.2.18-beta jar and an original-format prismarine-crystal recipe. It saves a real 600-of-1,200 FE coal job and its native machine loot. The converted mill uses the original 50% power multiplier even after the legacy datapack changes it to 25%. The headless controls also collect new balls under the changed legacy definition and a conflicting modern data map, and recover the unresolved record after returning its missing definition. The guarded stage uses another copy of the original world to test Forge recipe sync, JEI, normal placement and the native machine menu.

## Commands

```sh
source build/cycle-177-pins.zsh
export JAVA_HOME="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
export CI=true JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=4
./gradlew --no-daemon --no-build-cache --no-configuration-cache --max-workers=2 --console=plain :enderio:assemble
./gradlew --no-daemon --no-build-cache --no-configuration-cache --max-workers=2 --console=plain \
  :enderio:reobfClientSmokeJar check --continue

export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Djava.net.preferIPv4Stack=true'
port_evidence=build/port-evidence/cycle-259-custom-grinding
port_legacy=build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar
port_jar="$port_evidence/enderio-fixed.jar"
port_jei="$port_cache/maven.modrinth/jei/9jqubC9n/7f64b7f8fde7f001ef054dabe3618a8780ba2650/jei-9jqubC9n.jar"
python3 -Bu scripts/porting/packaged_legacy_custom_sag.py --name cycle-259-custom-sag-before --port 26377 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" \
  --jar "$port_evidence/enderio-before.jar"
python3 -Bu scripts/porting/packaged_legacy_custom_sag.py --name cycle-259-custom-sag-fixed --port 26378 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" --jar "$port_jar"
python3 -Bu scripts/porting/packaged_legacy_sag.py --name cycle-259-stock-sag --port 26379 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" --jar "$port_jar"
python3 -Bu scripts/porting/packaged_registry_audit.py --name cycle-259-core-registry --port 26381 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --probe "$port_probe"

port_seed="$port_runtime/cycle-258-enderface-core-final-client"
port_name=cycle-259-custom-sag-guarded
port_client="$port_runtime/$port_name-install"
mkdir -p "$port_client/config"
cp -cR "$port_seed/libraries" "$port_seed/versions" "$port_client/"
cp "$port_seed/config/fml.toml" "$port_client/config/"
python3 -B scripts/porting/client/prepare_packaged.py --name "$port_name-install" \
  --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" \
  --jar "$port_jar" --qa-jar "$port_evidence/enderio-client-qa.jar" \
  --assets "$HOME/.gradle/caches/neoformruntime/assets" --mod "$port_jei"
python3 -Bu scripts/porting/packaged_legacy_custom_sag.py --name "$port_name" --port 26380 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" --jar "$port_jar" \
  --launch "$port_client/launch.json" --jei-jar "$port_jei"
python3 -Bu scripts/porting/packaged_legacy_custom_sag.py --name cycle-259-custom-sag-verified --port 26382 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" --jar "$port_jar" \
  --launch "$port_client/launch.json" --jei-jar "$port_jei"
```

The stock client control prepares the same jars in `cycle-259-stock-sag-client-install` without optional mods. It runs `packaged_legacy_sag.py` with `--name cycle-259-stock-sag-client --port 26383` and that profile's launch file.

## Results

- The original release activates a real custom ball and saves its ID, 600 FE of wear, one already produced powder and two queued powder. The previous port rejects the original `enderio:grinding_ball` serializer. Its machine keeps the ID and task but has no active modifier snapshot. See `build/port-runtime/cycle-259-custom-sag-before/{legacy-custom-sag-results.json,port/smoke-converted.log}`.
- The updated headless profile passes 14 assertions across six clean server stages. The original job needs exactly 600 more FE and delivers all three powder. A new mill with the edited 25% power recipe finishes its second coal for 600 FE. The modern data-map control finishes its second coal for 1,800 FE. The missing-definition copy retains the ID and wear and restores the original snapshot after the recipe returns. See `build/port-runtime/cycle-259-custom-sag-fixed/legacy-custom-sag-results.json`.
- A repeat with the original fixture expectations stops before client launch because the stock coal recipe can roll bonus powder. The revised test pauses the second job with 100 FE paid, records the hopper balance and the actual queued roll, then verifies their exact sum after finishing the job. Both the guaranteed output and the possible bonus are included in the bounds check. See `build/port-evidence/cycle-259-custom-grinding/guarded.log`.
- The real Java 17 product assembly succeeds. The tested runtime SHA-256 is `a2548ec23bfced89326d7af6d9f58f71d0e56b9640353f277f0f55eb305b839b`. See `build/port-evidence/cycle-259-custom-grinding/build-fixed-retry.log`.
- The CI check passes 79 unit cases and 548 native runs with zero failures, errors or skips. All 180 recorded baseline methods appear in the current suite reports. The main gameplay profile executes all 220 discovered cases. The stock headless migration passes its 21 assertions. The packaged registry audit loads all 1,222 applicable baseline recipe IDs without a serializer difference. See `build/port-evidence/cycle-259-custom-grinding/{check.log,xml-audit.json,stock.log,registry.log}`.
- The final custom run passes 24 server assertions and 18 client checks across eight server stages. Forge sends the original recipe to the client. JEI 15.56.0.205 shows the custom ball with a 1,200 µI cost, and its native tooltip shows 200% main output, 150% bonus output and 50% power use. The placed and survival-replanted mills both retain the original recipe ID. The native menu shows each ball's modifiers and exact wear alongside its 600-of-1,200 FE job. Each uses exactly 600 more FE, and both hoppers retain the exact three powder after restart. See `build/port-runtime/cycle-259-custom-sag-verified/legacy-custom-sag-results.json`.
- The stock client control passes 24 server assertions and 15 client checks with the same runtime and without optional mods. All 12 server processes across the two guarded profiles exit successfully with empty registry and runtime error lists. The native observer records 3,474 samples across both clients without a foreground, activation or onscreen event. All ten captures report a hidden window, uncaptured cursor, disabled narrator and ten muted sound categories. See `build/port-runtime/cycle-259-stock-sag-client/legacy-sag-results.json` and `build/port-evidence/cycle-259-{custom-sag-verified,stock-sag-client}-client/summary.json`.

## Next verification

Exercise another Forge 6.2 Soul Binder recipe with paid work using its original input and output payload. [Completion audit](completion-audit.md)
