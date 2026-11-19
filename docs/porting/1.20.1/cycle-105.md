# Cycle 105 — Synchronize Enchanter prices and live recipes

## Scope

Keep a real Enchanter menu open over packaged survival TCP while its recipe is removed and then replaced. Give the dedicated server customized Enchanter costs, with an independent client using its own default configuration. Verify the displayed cost, preservation of unspent ingredients and exact payment for the replacement. Open a second Enchanter that had no viewer during either reload.

## Changes

- Resolve the Enchanter output on the server. The menu synchronizes the server's XP cost with its existing integer slot protocol. The client receives the preview and price without applying local cost settings.
- Refresh an open menu when the loaded recipes change and resolve the preview before opening a previously closed Enchanter. The output slot checks the live recipe registry when deciding whether collection is allowed.

## Commands

```sh
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-105-baseline-qa.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_prior=build/port-runtime/cycle-104-fixed-client
port_qa=enderio/build/test-libraries/enderio-client-qa.jar

prepare_client() {
  profile="$1"
  jar="$2"
  profile_dir="build/port-runtime/$profile"
  mkdir -p "$profile_dir"
  cp -R "$port_prior/libraries" "$port_prior/versions" "$profile_dir/"
  python3 scripts/porting/client/prepare_packaged.py --name "$profile" --java "$port_java" \
    --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$jar" \
    --qa-jar "$port_qa" --assets "$HOME/.gradle/caches/neoformruntime/assets"
}

run_reload() {
  port_client="build/port-runtime/$1-client"
  python3 scripts/porting/packaged_enchanter_reload.py --name "$1" --port "$2" \
    --java "$port_java" --forge-server "$port_forge" --jar "$port_client/mods/enderio.jar" \
    --launch "$port_client/launch.json"
}

prepare_client cycle-105-before-client "$port_prior/mods/enderio.jar"
run_reload cycle-105-before 25898

./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-105-build.log 2>&1
port_jar=enderio/build/libs/enderio-8.2.14.104-1.20.1+d5680f7c64.jar
prepare_client cycle-105-fixed-client "$port_jar"
run_reload cycle-105-fixed 25899
port_client=build/port-runtime/cycle-105-fixed-client
python3 scripts/porting/packaged_enchanter.py --name cycle-105-collection --port 25900 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_client/mods/enderio.jar" \
  --launch "$port_client/launch.json"
```

## Results

- The baseline client showed 11 levels for Sharpness II with its default settings. The server applied its customized price of 20 and denied collection at 19. The result and all inputs stayed intact.
- The baseline client received the recipe-removal and replacement updates, but its open menu kept showing the original book and 11-level price. The server also kept the original Sharpness II preview in both cases. See `build/port-runtime/cycle-105-before/enchanter-reload-results.json`, `smoke-fresh.log` and `build/port-evidence/cycle-105-before-client/summary.json`.
- The build passes 79 JUnit and 483 Forge GameTest runs, including all 161 discovered gameplay tests. The Forge 47.4.28 production jar SHA-256 is `0630a8393de70f55866dc0548bf6038f86b7c14303a984de15fd6c7892d8f90f`. See `build/cycle-105-build.log`.
- The fixed reload scenario passes 10 guarded client checks and eight native-server assertions. The menu shows 20 levels for Sharpness II and denies collection at 19. Removing the recipe clears both preview and price. Replacing it with Mending updates the same menu to 33 levels. At 40 levels the player shift-clicks one Mending book, keeping seven levels. The Enchanter spends the writable book, six amethyst shards and ten lapis. The previously closed Enchanter opens with the same new preview and price, still holding all its ingredients. Both saved states survive restart. See `build/port-runtime/cycle-105-fixed/enchanter-reload-results.json`.
- The guarded reload client recorded 1,159 native samples without activation, foreground or on-screen events. Both captures verify zero volume in every sound category and narrator off. The replacement screenshot shows the exact red 33-level cost at 19 levels. See `build/port-evidence/cycle-105-fixed-client/summary.json` and `replacement-cost.png` in the same directory.
- The cycle 104 collection regression passes all nine server and 17 client checks on the same production artifact. Fresh normal pickup and saved shift-click both preserve exact payment. All five final dedicated server processes exit at zero without runtime or registry errors. The two collection clients also pass the focus and sound checks. See `build/port-runtime/cycle-105-collection/enchanter-results.json`.

## Remaining verification

Other enchantment recipes, cost configuration edits during a session, survival pickup and replanting, and a complete visual pass still need packaged coverage.
