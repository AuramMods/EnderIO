# Cycle 116 — Automate VAT production on dedicated Forge

The Concentrated Cloud Seed recipe uses the existing `enderio:wind_charges` tag. Add Firework Rockets as the Forge 1.20.1 reagent and retain the optional Wind Charge entry. The recipe's ID, other inputs, timing and output stay the same.

Build the scaled fermentation output from a copy of the recipe fluid stack so its full NBT survives. The authored test batch uses different reagent multipliers and a typed tag containing a string, byte and integer array. A prior batch of the same fluid with different NBT blocks the VAT while the recipe waits at 100%.

The disposable dedicated-server fixture runs all ten stock VAT recipes in separate stations with real hopper-fed reagents, sided tank inputs and tank outputs. The authored batch checks a disabled inlet, backpressure, two disk restarts and batch separation. Each fluid supply is preloaded with the recipe's own input so each station can run independently.

## Commands

```sh
./gradlew :enderio:runData --console=plain > build/cycle-116-datagen.log 2>&1
./gradlew build --continue --console=plain > build/cycle-116-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_prior=build/port-runtime/cycle-115-fixed-client/mods/enderio.jar
port_jar=enderio/build/libs/enderio-8.2.14.115-1.20.1+5ccc9ee072.jar

run_vat() {
  python3 scripts/porting/packaged_vat_automation.py --name "$1" --port "$2" --java "$port_java" \
    --forge-server "$port_forge" --jar "$3"
}

run_vat cycle-116-original 25919 "$port_prior"
run_vat cycle-116-fixed 25920 "$port_jar"
```

## Results

- Datagen updates the `enderio:wind_charges` tag. The full Gradle build passes all 79 unit cases and 483 Forge GameTest executions across the complete set of 161 discovered gameplay scenarios. See `build/cycle-116-datagen.log` and `build/cycle-116-build.log`.
- An initial fixture used the wrong serialized name for disabled faces and placed machines facing their default direction. A disk reopen showed the hopper transfers had worked and the fluid tanks were still full. The final fixture uses `disable` and explicit south-facing stations so the configured north and south faces match the physical stations. See `build/port-runtime/cycle-116-before/server/smoke-setup.log` and `build/cycle-116-diagnosis.log`.
- The corrected fixture reproduces both bugs on the previous package. The Concentrated Cloud Seed station keeps the Firework Rocket in its hopper and produces no fluid. The authored job reaches 450 mB but its saved output lacks the fluid tag. See `build/port-runtime/cycle-116-original/vat-automation-results.json` and the console logs beside it.
- The fixed package exports every stock product in its exact expected quantity. The ten receiving tanks hold 250 mB Nutrient Distillation, 250 mB Hootch, 500 mB Rocket Fuel, 1,000 mB Fire Water, 300 mB Liquid Sunshine, 1,000 mB Liquid Darkness, 110 mB Cloud Seed, 500 mB Concentrated Cloud Seed, 1,000 mB Dew and 1,000 mB Vapor. Each station empties its supply, reagents and VAT tanks.
- The disabled north face holds 1,000 mB at the supply. Reopening the inlet feeds the custom VAT. It waits at full progress with both reagents and 1,000 mB of water intact because its output tank holds a different tagged batch. After restart, opening the south face exports the prior 70 mB and produces 450 mB with the full authored tag. A fresh receiving tank accepts the second batch separately. A second restart preserves both batches and the empty VAT with no replay.
- All 17 packaged assertions pass. The three dedicated-server runs are headless and exit at zero with zero runtime or registry errors. The tested production artifact SHA-256 is `53a9b72fff4016bd1d735c953cb283d78d1836f65989cba6baa9375a9d915f05`. See `build/port-runtime/cycle-116-fixed/vat-automation-results.json`.

## Remaining verification

Production with all reagent variants, oversized batches that exceed the VAT's output capacity, older VAT save formats and Vapor bucket placement remain open. Verify the client display of asymmetric modifiers with the reagents reversed.
