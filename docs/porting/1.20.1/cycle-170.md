# Cycle 170 — Craft XP tools and both cereal appearances in survival

The packaged integrated server creates a disposable survival world and runs the vanilla crafting table with limited crafting enabled. The normal profile collects suspicious seeds and grains from 64 stock bedrock fires. The test moves the dropped entities within pickup range and the player collects them through vanilla. The remaining materials are supplied as fixture inputs. Inventory advancements unlock the three recipes and the native recipe book places their ingredients.

The second packaged profile uses a disposable integrated-server UUID with the hash used by the source's special crafting condition. The same Enderios recipe supplies the SOIREDNE variant. The test takes the result from the native crafting slot, checks the name and baked item appearance, and compares the item with the integrated server's inventory before eating it. A configured zero probability in the normal profile and a probability of one in the special profile make the two teleport outcomes observable. Both use the stock nutrition values and the teleport range setting of four blocks.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.168-1.20.1+9a311ec4a1.jar
port_runtime=build/port-runtime
port_qa=enderio/build/test-libraries/enderio-client-qa.jar
port_assets="$HOME/.gradle/caches/neoformruntime/assets"

./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-170-client-verified.log 2>&1
for port_profile in cycle-170-final-normal cycle-170-final-special; do
  port_dir="$port_runtime/$port_profile"
  mkdir -p "$port_dir/config/enderio"
  cp -cR "$port_runtime/cycle-169-xp-client/libraries" "$port_runtime/cycle-169-xp-client/versions" "$port_dir/"
  cp "$port_runtime/cycle-169-xp-client/config/fml.toml" "$port_dir/config/"
  python3 scripts/porting/client/prepare_packaged.py --name "$port_profile" --java "$port_java" \
    --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" --qa-jar "$port_qa" --assets "$port_assets"
done
python3 - <<'PY'
import json
from pathlib import Path

for variant, chance in [('normal', 0.0), ('special', 1.0)]:
    root = Path('build/port-runtime', f'cycle-170-final-{variant}')
    (root / 'config/enderio/base-common.toml').write_text(
        f'[items.food]\nenderioChance = {chance}\nenderioRange = 4.0\n')
    if variant == 'special':
        path = root / 'launch.json'
        launch = json.loads(path.read_text())
        arguments = launch['arguments']
        arguments[arguments.index('--uuid') + 1] = '00000000-0000-4000-8000-00002a76236e'
        path.write_text(json.dumps(launch, indent=2) + '\n')
PY
python3 scripts/porting/client/run_smoke.py --launch "$port_runtime/cycle-170-final-normal/launch.json" \
  --scenario xp-tool-crafting --output build/port-evidence/cycle-170-final-normal --timeout 180
python3 scripts/porting/client/run_smoke.py --launch "$port_runtime/cycle-170-final-special/launch.json" \
  --scenario special-enderios --output build/port-evidence/cycle-170-final-special --timeout 180
./gradlew check --continue --console=plain > build/cycle-170-check.log 2>&1
```

## Results

- The first normal run produced the required fire drops and both XP tool results. The client received the rod's result slot before the other grid updates, which made the immediate grid assertion fail. The updated fixture waits for the actual inputs and result, then waits for consumption and checks the server's inventory and crafting statistics. The final fixture selects an empty hotbar slot because the collected drops can already occupy slot zero.
- The normal profile passes all 25 checks. The fires produce 20 seeds and 92 grains. The player picks up the stock drops, unlocks the recipes and takes exactly one vial, one rod and one bowl of Enderios from the native crafting table. The server records one craft for each output and the expected ingredient consumption. The cereal recipe returns one empty bucket.
- The special profile passes all 18 checks. The native result acquires the SOIREDNE name and inverted sprite, and the server agrees with the client after the menu closes. Both meals raise food from 10 to 20, raise saturation from 0 to 16 and return one bowl. The normal profile stays at the table. The guaranteed teleport moves the special profile from `(0.5, 80, 3.5)` to approximately `(3.28, 80.06, 7.32)`.
- Both clients and integrated servers exit normally with zero runtime errors. The native observer records zero activation, foreground or on-screen events in 1,501 normal and 768 special samples. All captures have narration off, zero volume in every category and no fullscreen or cursor capture. Reports and screenshots are under `build/port-evidence/cycle-170-final-{normal,special}/`.
- The native regression check passes 79 unit tests and 530 Forge runs with zero failures, errors or skips. All 203 discovered main gameplay tests execute.
- The production artifact is the same Forge 47.4.28 jar verified in [cycle 169](cycle-169.md), with SHA-256 `aee822f7e8f3302e4ae83c3de7c974dbb9c2f19c1c98885302e69d393b80ddfc`. The separate remapped client QA jar carries the new scenario.

## Next verification

Exercise the Athena connected-texture pack and the Oculus shader path together. Continue the remaining crafting and item-use checks in the feature matrix.
