# Cycle 228 — Soul Binder category and datapack rules

The packaged Soul Binder scenario verifies the stock Animal Token and Soul Engine recipes. The token accepts a named Cow and rejects a Zombie. The stock engine accepts a named Blaze. The server then reloads a datapack that replaces the Blaze entry with one for Cow, without replacing the binding recipe. In the same session, JEI must remove Blaze from the recipe lookup, find the recipe from a named Cow, and preserve the captured Cow in the output preview.

| Recipe and rule | Accepted vial | Rejected vial | XP fluid | Energy |
| --- | --- | --- | --- | --- |
| Animal Token, creature category | Cow named Meadow | Zombie | 140 mB | 12,800 FE |
| Soul Engine, stock rule | Blaze named Ember | Cow | 1,100 mB | 188,000 FE |
| Soul Engine, reloaded rule | Cow named Clover | Blaze | 1,100 mB | 188,000 FE |

All profiles verify an empty returned vial, the stock resource cost and the saved outputs. The stock Blaze engine finishes before reload and retains its captured soul afterward. The token and Cow jobs save at 400 FE of progress and finish after restart. The Forge-only server also attempts to bind the now-ineligible Blaze while the binder has enough XP fluid and power to begin a job.

## Commands

```sh
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-228-binder-baseline-build.log 2>&1
./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-228-binder-full-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_seed="$port_runtime/cycle-227-binder-fixed-client"
port_jei="$port_runtime/cycle-158-jei-final-client/mods/jei-9jqubC9n.jar"
port_jar=build/cycle-228-artifacts/tested.jar
port_qa=build/cycle-228-artifacts/tested-qa.jar
export JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true

run_binder_client() {
  port_name="$1"
  port_number="$2"
  shift 2
  port_client="$port_runtime/$port_name-client"
  mkdir -p "$port_client/config"
  cp -cR "$port_seed/libraries" "$port_seed/versions" "$port_client/"
  cp "$port_seed/config/fml.toml" "$port_client/config/"
  python3 scripts/porting/client/prepare_packaged.py --name "$port_name-client" \
    --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" \
    --jar "$port_jar" --qa-jar "$port_qa" --assets "$HOME/.gradle/caches/neoformruntime/assets" --mod "$port_jei"
  python3 -u scripts/porting/packaged_soul_binder_jei.py --name "$port_name" --port "$port_number" \
    --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar" \
    --jei "$port_jei" --launch "$port_client/launch.json" "$@"
}

run_binder_client cycle-228-binder-client-only 26300 --rules --client-only
run_binder_client cycle-228-binder-rules 26301 --rules
python3 -u scripts/porting/packaged_soul_binder_jei.py --name cycle-228-binder-core --port 26302 \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" --jar "$port_jar" --rules
run_binder_client cycle-228-binder-exact 26303
run_binder_client cycle-228-binder-exact-retry 26304
```

## Results

- The initial packaged run passes 50 guarded client checks and 19 dedicated-server assertions using the cycle 227 product. JEI refreshes its recipe runtime after the datapack reload. All three jobs survive the final restart. The initial Forge-only run passes 22 assertions. See `build/port-runtime/cycle-228-binder-before/soul-binder-jei-results.json` and `build/port-runtime/cycle-228-binder-core-before/soul-binder-jei-results.json`.
- The Java 17 build passes 79 unit tests and all 543 native runs. The rebuilt production jar matches the jar in the packaged profiles. Inspection of that jar and its nested jars finds no client QA classes. See `build/cycle-228-binder-full-build.log`.
- On the rebuilt jar, the two JEI profiles pass 50 and 47 client checks and 19 dedicated-server assertions apiece. A client-only JEI installation displays the same rule change and gives JEI's server requirement for the transfer button. The player loads those inputs through the regular survival menu. See `build/port-runtime/cycle-228-binder-rules/soul-binder-jei-results.json` and `build/port-runtime/cycle-228-binder-client-only/soul-binder-jei-results.json`.
- The rebuilt Forge-only profile passes all 22 assertions. The stock Blaze remains bound to its original result after reload and restart. The binder rejects the removed Blaze in a fresh job, then processes the Cow with exact XP and FE. See `build/port-runtime/cycle-228-binder-core/soul-binder-jei-results.json`.
- The first exact-entity regression reaches the loopback listener and disconnects during the Forge login handshake before joining. The guarded retry uses the same jars in a fresh profile and passes 33 client checks and 14 server assertions. Both cycle 227 recipes retain their original transfer, binding and restart behavior. See `build/port-runtime/cycle-228-binder-exact/logs/debug.log` and `build/port-runtime/cycle-228-binder-exact-retry/soul-binder-jei-results.json`.
- The guards record zero activations, foreground samples or onscreen windows across 8,800 samples from the three successful clients. All 16 captures show ten volumes at zero and the narrator `OFF`. See the `summary.json` reports under `build/port-evidence/cycle-228-binder-rules-client`, `build/port-evidence/cycle-228-binder-client-only-client` and `build/port-evidence/cycle-228-binder-exact-retry-client`.

| Jar | SHA-256 |
| --- | --- |
| Rebuilt Ender IO | `501f62f238ef9d6005033f7ead042088622e28ca0c8bb8389308f95543014f2e` |
| Rebuilt client QA | `3e80675223d0e24817129b809aa8e2f4be5c70f8525bf6bb9f7fc1490304b666` |
| JEI `15.56.0.205` | `6b251e60a4719c874da89d750af97aa6498f6f4475e3ae79cb1288c7bf0cb65c` |

## Next verification

Exercise the stock Soul Binder recipe that rebinds an existing Powered Spawner. Check the viewer and transfer with a configured spawner, then verify that the newly bound item preserves its machine data and resumes the new spawn behavior after placement.
