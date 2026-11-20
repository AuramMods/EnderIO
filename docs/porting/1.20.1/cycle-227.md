# Cycle 227 — Named Soul Vials in JEI

Soul Binder recipes accept captured souls by entity type, mob category, or soul data rule. JEI uses the entity type when it indexes recipes and matches inventory stacks for transfer. The ingredient list retains the full soul. When a player views an eligible captured vial or a bound output, the preview keeps the original captured data on the vial and any bindable result.

| Stock recipe | Vial | XP fluid | Energy |
| --- | --- | --- | --- |
| Ender Crystal | Enderman named West | 1,440 mB | 76,800 FE |
| Energetic Photovoltaic Module | Phantom named Sky | 2,240 mB | 12,800 FE |

The TCP client carries an empty vial and a zombie vial alongside each recipe's named vial. It opens the survival Soul Binder menu, checks recipe lookup and the displayed slots, and presses the actual JEI transfer button with no ingredient focus. The dedicated server checks the resulting inventory before acknowledging it. The Forge-only control feeds the same named vials and tagged materials through vanilla hoppers.

The server starts with 400 FE and one mB less XP fluid than the recipe requires. It then supplies XP, saves the partially powered job, restarts, and supplies the remaining energy plus 317 FE. The test checks the returned empty vial, the captured name and health on the bound panel, and balances of 37 mB and 317 FE. A final restart checks the saved outputs and resources.

## Commands

```sh
./gradlew :enderio:assemble :enderio:reobfClientSmokeJar --console=plain
./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_seed="$port_runtime/cycle-226-vat-shared-synced-client"
port_jei="$port_runtime/cycle-158-jei-final-client/mods/jei-9jqubC9n.jar"
port_jar=build/cycle-227-artifacts/fixed.jar
port_qa=build/cycle-227-artifacts/fixed-qa.jar
export JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true

run_binder_profile() {
  port_name="$1"
  port_number="$2"
  port_product="$3"
  port_client="$port_runtime/$port_name-client"
  mkdir -p "$port_client/config"
  cp -cR "$port_seed/libraries" "$port_seed/versions" "$port_client/"
  cp "$port_seed/config/fml.toml" "$port_client/config/"
  python3 scripts/porting/client/prepare_packaged.py --name "$port_name-client" \
    --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" \
    --jar "$port_product" --qa-jar "$port_qa" --assets "$HOME/.gradle/caches/neoformruntime/assets" --mod "$port_jei"
  python3 -u scripts/porting/packaged_soul_binder_jei.py --name "$port_name" --port "$port_number" \
    --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_product" \
    --jei "$port_jei" --launch "$port_client/launch.json"
}

run_binder_profile cycle-227-binder-baseline 26295 build/cycle-227-artifacts/before.jar
run_binder_profile cycle-227-binder-fixed 26297 "$port_jar"
python3 -u scripts/porting/packaged_soul_binder_jei.py --name cycle-227-binder-core --port 26296 \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar"
```

## Results

- The first QA client tried to read the recipe materials before Forge registered them. The corrected fixture resolves the items when the menu opens. See `build/port-evidence/cycle-227-binder-before-client/focus.json.client.log`.
- The corrected probe opens the survival menu on the baseline jar. JEI finds the Ender Crystal recipe from the generic Enderman vial and fails the same lookup for the named vial. The baseline Forge-only hopper control passes all 15 assertions. See `build/port-evidence/cycle-227-binder-baseline-client/result.json` and `build/port-runtime/cycle-227-binder-core-before/soul-binder-jei-results.json`.
- The Java 17 build passes 79 unit tests and all 543 native runs, including 215 main gameplay cases. The build produces the same packaged jar used in the Forge profiles, and the production jar and its nested jars contain no QA classes. See `build/cycle-227-binder-full-build.log`.
- The fixed packaged client passes 31 checks. It finds both stock recipes through the correct named vials, displays the Phantom in both directions of JEI lookup, and moves the exact vial and one tagged ingredient through each unfocused recipe transfer. The native observer records zero activations, foreground samples or onscreen windows across 3,087 samples. The four captures record ten volumes at zero and narrator `OFF`. See `build/port-evidence/cycle-227-binder-fixed-client/summary.json`.
- The dedicated server with JEI passes 14 assertions across three launches with no registry or runtime errors. It checks the transferred stacks and decoys, exact XP and FE, returned vials, captured Phantom data and both saved results. See `build/port-runtime/cycle-227-binder-fixed/soul-binder-jei-results.json`.
- The fixed Forge-only control passes 15 assertions across feeding, partial progress, completion and restart. See `build/port-runtime/cycle-227-binder-core/soul-binder-jei-results.json`.

| Jar | SHA-256 |
| --- | --- |
| Baseline Ender IO | `54e7052eb05d4bc42993ab6c980051f03a58cec26cc0cd05417235655ea3a12d` |
| Fixed Ender IO | `dc4f5246696dcfd2e1565a01a73d526dea6608c36b4744aab841904a32f9fe25` |
| Final client QA | `de37fe0bd8eddcd9ce5e01a49827fa6fd1d2dbb25c84abadd9e7ae080a769530` |
| JEI `15.56.0.205` | `6b251e60a4719c874da89d750af97aa6498f6f4475e3ae79cb1288c7bf0cb65c` |

## Next verification

Exercise JEI lookup and transfer for stock Soul Binder recipes that accept a mob category or a reloadable soul data rule. Check the resulting token or bound machine and verify that the recipe viewer follows the server's rule after a live datapack reload.
