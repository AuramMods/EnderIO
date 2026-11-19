# Cycle 110 — Restore historical Auto Smelt mining

Restore `enderio:auto_smelt` and its Forge loot modifier at their original IDs. The modifier uses the loaded furnace recipe for each generated drop. It emits the recipe's tagged result multiplied by the number of input items, splits the result at its native stack limit, and retains each drop that has no smelting recipe. The enchantment keeps its historical Silk Touch conflict and compatibility with Fortune. Its distribution is limited to saved tools, existing books and authored recipes.

The fixture creates a disposable Ender IO 6.2.18-beta world with a damaged Auto Smelt pickaxe and an ancient debris block. It compares the historical and previous port jars through Forge's native loot command. The final jar also exercises survival mining and pickup over a guarded TCP client, a mixed loot table and tagged furnace recipe, a live recipe reload, and reconnecting after restart.

## Commands

```sh
./gradlew :enderio:runData --console=plain > build/cycle-110-datagen.log 2>&1
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-110-build-final.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_prior=build/port-runtime/cycle-109-fixed-client
port_client=build/port-runtime/cycle-110-fixed-client
port_jar=enderio/build/libs/enderio-8.2.14.109-1.20.1+cbcdc87365.jar

python3 scripts/porting/packaged_legacy_auto_smelt.py --name cycle-110-before --port 25908 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_forge/mods/enderio.jar" \
  --jar "$port_prior/mods/enderio.jar"
mkdir -p "$port_client"
cp -R "$port_prior/libraries" "$port_prior/versions" "$port_client/"
python3 scripts/porting/client/prepare_packaged.py --name cycle-110-fixed-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_legacy_auto_smelt.py --name cycle-110-fixed --port 25909 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_forge/mods/enderio.jar" \
  --jar "$port_client/mods/enderio.jar" --launch "$port_client/launch.json"
```

## Results

- The original jar produces one netherite scrap. The previous port preserves the pickaxe's Auto Smelt NBT and yields the unsmelted ancient debris. See `build/port-runtime/cycle-110-before/legacy-auto-smelt-results.json` and both server logs in that profile.
- Datagen and the full build pass. The EnderCore test profile registers the actual enchantment and modifier and checks the generated JSON against packaged resources. All 79 unit cases and 483 Forge GameTest executions pass, including all 161 discovered gameplay tests. See `build/cycle-110-datagen.log` and `build/cycle-110-build-final.log`.
- The packaged server verifies the ordinary tool and Silk Touch controls. The mixed loot table supplies 20 raw copper and a tagged pickaxe with no furnace recipe. The original custom recipe produces 80 tagged copper ingots in stacks of 64 and 16. Reloading to the new recipe produces 60 tagged gold ingots. Both results preserve the unrelated pickaxe and its damage and tag.
- The migration passes 18 server assertions. The equipped original pickaxe rejects Silk Touch and accepts Fortune III. Each survival break drops exactly one netherite scrap. After reconnecting across a dedicated restart, the player has the original tagged pickaxe and first scrap. The second break leaves two scraps and pickaxe damage 39, up from 37 before mining. The reloaded recipe and its saved result also survive restart. See `build/port-runtime/cycle-110-fixed/legacy-auto-smelt-results.json`.
- Both clients pass five checks each and record 3,211 total window samples with zero activation, foreground or on-screen events. Both captures have every volume at zero and narrator off. See `build/port-evidence/cycle-110-fixed-exercise-client/summary.json` and `build/port-evidence/cycle-110-fixed-verify-client/summary.json`.
- All three dedicated processes exit at zero. The production artifact SHA-256 is `da2e43f1fca3b9f485e6e1352cb6ae17b54f360e3f65717364be3b0b184a9d35`. First conversion reports four outstanding enchantments. The reopened world has zero registry diagnostics.

## Remaining verification

Historical `repellent`, `shimmer`, `withering` and `xp_boost` remain open. Auto Smelt still needs native anvil, Fortune-sensitive ore yield and optional loot-mod interoperability coverage.
