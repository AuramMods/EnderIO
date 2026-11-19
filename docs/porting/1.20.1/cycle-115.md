# Cycle 115 — Verify the Staff of Levity progression in single-player

Use the Staff's Prescient Crystal ingredient tag for its recipe-book advancement. The previous criterion looked for a Pulsating Crystal, so the standard ingredient failed to unlock the recipe. Datagen regenerates the advancement from the same tag the crafting recipe matches.

The guarded packaged client creates a new survival world and receives the stock crafting and fermentation ingredients. Native crafting, bucket use, menu slot swaps, the wired charger and the VAT perform the full workflow. The fixture provides the charger with one basic capacitor and its initial FE balance. A save and reopen interrupts an in-progress fermentation batch, and a final reopen checks the cancelled Staff and both drained machines.

## Commands

```sh
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-115-qa.log 2>&1
./gradlew :enderio:runData --console=plain > build/cycle-115-datagen.log 2>&1
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-115-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-115-qa-final.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_prior=build/port-runtime/cycle-114-fixed-client
port_jar=enderio/build/libs/enderio-8.2.14.114-1.20.1+ed652dcbab.jar

prepare_client() {
  port_client="build/port-runtime/$1"
  mkdir -p "$port_client"
  cp -R "$port_prior/libraries" "$port_prior/versions" "$port_client/"
  python3 scripts/porting/client/prepare_packaged.py --name "$1" --java "$port_java" \
    --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$2" \
    --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
}

run_progression() {
  python3 scripts/porting/client/run_smoke.py --launch "build/port-runtime/$1/launch.json" \
    --scenario levitation-progression --output "build/port-evidence/$2" --timeout 260
}

prepare_client cycle-115-before-client "$port_prior/mods/enderio.jar"
run_progression cycle-115-before-client cycle-115-before
prepare_client cycle-115-fixed-client "$port_jar"
run_progression cycle-115-fixed-client cycle-115-fixed
```

## Results

- The original packaged Forge client entered a native integrated survival world and opened the crafting table. Its book lacked the Staff recipe after receiving the Prescient Crystal and Infinity Rods. See `build/port-evidence/cycle-115-before/result.json`.
- The generated advancement matches `c:gems/prescient_crystal`. The published recipe ID and ingredient pattern are identical before and after datagen. See `build/cycle-115-datagen.log`.
- The corrected package unlocks the recipe and crafts one uncharged staff from two Infinity Rods and one Prescient Crystal. Four native bucket uses supply the VAT with 4,000 mB of Nutrient Distillation. The Dew recipe accepts reversed reagents and returns exactly 1,000 mB of Dew. The player draws the Dew into a vanilla bucket and pours it back into the VAT for the next recipe.
- The client closes the integrated server when the Vapor batch is at 25%. The reopened VAT reports 27.75% progress and produces one 1,000 mB batch. The menu and captures show both stock recipes and the resumed job. See `build/port-evidence/cycle-115-fixed/vapor-before-save.png` and `vapor-produced.png` in the same directory.
- The wired charger transfers all 10,000 FE to the crafted Staff. The Staff accepts the 1,000 mB batch directly from the VAT in survival. It lifts the player and stops on the second crouch-use while airborne. The final reopen preserves its disabled state, 9,943 FE and 943 mB. The server also verifies an empty charger and VAT output. See `build/port-evidence/cycle-115-fixed/result.json`.
- The build passes 79 unit cases and 483 native Forge GameTest executions across all 161 discovered gameplay cases. Packaged single-player passes 23 client checks with zero unclassified runtime errors. The observer records 4,520 samples with zero activation, foreground or on-screen events. All five captures record every volume at zero and the narrator off. See `build/cycle-115-build.log` and `build/port-evidence/cycle-115-fixed/summary.json`.
- The tested production artifact SHA-256 is `1bc439de246479239b30cbff986e6f959cba3ac820d40e925d3c87bfcb9c75b1`.

## Remaining verification

Verify Vapor bucket placement, the remaining VAT recipes, sided automation and the broader historical VAT save format in separate fixtures.
