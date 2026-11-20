# Cycle 276 — Stock VAT recipe cards and live yields

The dedicated Forge server enumerates the ten stock VAT recipes, their live reagent tags and the baseline VAT data map. The fixture selects the highest-yield stock reagent pair for every output. It preloads Blue Ice in the left slot for Cloud Seed and gives the survival player two marked copies of each selected reagent.

The guarded JEI client checks that all ten outputs find their recipes. Each card's unfocused reagent lists match the full server tags and tooltip multipliers. The focused recipe layout shows the chosen inputs, exact source-fluid amount, base output and both rendered reagent modifiers. The Blue Ice card shows a 1,000 mB Cloud Seed base and an `x81.0` modifier. Each card also uses the real JEI transfer button against the ordinary Forge server.

The server verifies which marked reagents moved and which surplus items stayed in the inventory. It then pulses each VAT with redstone, pauses partway through fermentation and checks the exact queued yield. The native VAT menu synchronizes the same recipe and progress and shows the modifiers on the physical slots. After a cold restart, the VATs finish and export to pressurized tanks. The Blue Ice job uses three receivers for 32,000, 32,000 and 17,000 mB. A second restart checks every output and each remaining 137 mB input reserve.

## Commands

```sh
export JAVA_HOME="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
export CI=true JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=4
./gradlew --no-daemon --no-build-cache --no-configuration-cache --max-workers=2 --console=plain :enderio:reobfClientSmokeJar
zsh build/cycle-276-vat-card-profile.zsh cycle-276-stock-vat-verified 26441

export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Djava.net.preferIPv4Stack=true'
port_jar=build/port-evidence/cycle-272-release/source-local/enderio/build/libs/enderio-8.2.14.271-dev+a00893d995.jar
python3 -Bu scripts/porting/packaged_vat_jei.py --name cycle-276-vat-forge-control --port 26442 \
  --java "$JAVA_HOME/bin/java" --forge-server build/port-runtime/cycle-047-legacy-discover --jar "$port_jar"
```

The archived launcher runs `scripts/porting/packaged_vat_jei_stock.py` with the retained production jar, the current registry probe and JEI `15.56.0.205` on the guarded client.

## Results

- The Java 17 QA build passes after correcting the fixture's JEI focus-list type. The first multiplayer run confirms the transfer in both the saved player inventory and the native VAT. Its reused server assertion expected hopper and tank feeders at positions this fixture leaves open. The corrected condition checks the exact transferred stacks and fluid balances. All 1,140 default predicates still match the previous headless stock fixture. See `build/port-evidence/cycle-276-vat-cards/{qa-build.log,qa-rebuild.log,fixture-state.log,reused-checks.json}`.
- The corrected packaged run passes 133 client checks and 95 server assertions. All ten jobs resume and spend exactly 12,500 mB of input to export 88,200 mB of product. The reversed Blue Ice case pauses at 22 ticks and its three tanks retain all 81,000 mB after the second restart. All three dedicated sessions exit cleanly. The separate Forge-only control passes seven assertions over two more clean sessions.
- The native observer records 2,443 samples with zero activations, foreground observations or onscreen observations. All 20 captures confirm narration off and all ten volume categories at zero. The Blue Ice images show `x81.0` beneath the right reagent in JEI and beneath the left reagent in the paused VAT. See `build/port-evidence/cycle-276-stock-vat-verified-client/`.
- The tested production jar SHA-256 is `58803ec07dc520e3da7ec4dc413ba04dc78db24b524aa20435e00f8d2cbf98bd`. The QA jar hashes to `c5bb63706e23b0b69b96c0367ff6924316f4be464c321756dd019f539c6fdef3` and JEI hashes to `6b251e60a4719c874da89d750af97aa6498f6f4475e3ae79cb1288c7bf0cb65c`. See `build/port-runtime/cycle-276-stock-vat-verified/vat-jei-stock-results.json` and `build/port-runtime/cycle-276-vat-forge-control/vat-jei-results.json`.

## Next verification

Verify optional SAG Mill recipes in JEI against the live Mekanism and Immersive Engineering ingredient and output tags. [Completion audit](completion-audit.md)
