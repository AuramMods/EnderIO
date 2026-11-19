# Cycle 112 — Restore Shimmer and XP Boost from Forge 6 saves

Restore `enderio:shimmer` and `enderio:xp_boost`, the last two missing registry IDs in the conversion fixtures. Shimmer uses native enchantment glint and tooltip rendering, including the original restriction on enchanting books through normal generation. Both enchantments use the historical cost configuration paths.

XP Boost keeps the historical bonus formula `round(base × 2 × log10(level + 1))`. Block bonuses adjust Forge's accepted experience award, which keeps the same reward roll and avoids dropping a second orb independently of the actual harvest. Creature bonuses use Forge's original XP as the base. A native arrow carries its launch level in the original `EnderIOXpBoostLevel` field. The fatal-hit handler reads the actual damage source before vanilla updates its last-damage-source field and keeps that level through the XP drop event.

The fixture starts from a disposable Ender IO 6.2.18-beta world containing the original tagged hoe, sword, bow and chestplate. A packaged survival client mines sculk with XP Boost III, repeats with a combined Silk Touch control, and attacks a zombie with XP Boost I. It then fires a native arrow into a glass wall and replaces the bow with an ordinary sword. After restart, the fixture sends that saved arrow into the second target and checks the resulting XP. Both sessions open the native inventory to verify Shimmer.

## Commands

```sh
./gradlew :enderio:runData --console=plain > build/cycle-112-datagen.log 2>&1
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-112-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_prior=build/port-runtime/cycle-111-final-client
port_jar=enderio/build/libs/enderio-8.2.14.111-1.20.1+2bfc75a90c.jar

prepare_client() {
  port_client="build/port-runtime/$1"
  mkdir -p "$port_client"
  cp -R "$port_prior/libraries" "$port_prior/versions" "$port_client/"
  python3 scripts/porting/client/prepare_packaged.py --name "$1" --java "$port_java" \
    --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$2" \
    --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
}

run_experience() {
  port_client="build/port-runtime/${3:-$1-client}"
  python3 scripts/porting/packaged_legacy_experience.py --name "$1" --port "$2" --java "$port_java" \
    --forge-server "$port_forge" --legacy-jar "$port_forge/mods/enderio.jar" \
    --jar "$port_client/mods/enderio.jar" --launch "$port_client/launch.json"
}

prepare_client cycle-112-before-client "$port_prior/mods/enderio.jar"
prepare_client cycle-112-fixed-client "$port_jar"
run_experience cycle-112-before 25912
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-112-qa-final.log 2>&1
prepare_client cycle-112-final-client "$port_jar"
run_experience cycle-112-verified 25913 cycle-112-final-client
```

## Results

- The previous port keeps all four item tags and breaks sculk in survival. The player receives one point from the block, so the two-point XP Boost assertion fails. Initial conversion reports Shimmer and XP Boost as missing. See `build/cycle-112-before.log` and `build/port-runtime/cycle-112-before/port/smoke-converted.log`.
- Datagen and the full build pass 79 unit cases and 483 Forge GameTest executions, including all 161 discovered gameplay cases. See `build/cycle-112-datagen.log` and `build/cycle-112-build.log`.
- The first guarded client replicated the mining and melee results before its native bow draw was released by vanilla's input tick. The final fixture holds the use mapping inside the test client until release. See `build/port-evidence/cycle-112-fixed-exercise-client/result.json` and `build/cycle-112-qa-final.log`.
- The final packaged migration passes 13 server assertions. XP Boost III grants two XP from sculk. The combined Silk Touch control drops one sculk with zero XP. XP Boost I grants eight XP from a zombie, bringing the player's total to ten. A native bow shot uses one arrow and one durability and saves its rank III snapshot. After the server restarts, the same arrow awards eleven XP while the player holds an ordinary sword. The total reaches 21. See `build/port-runtime/cycle-112-verified/legacy-experience-results.json`.
- The two guarded clients pass 15 checks, including inventory rendering, foil on the old chestplate and the localized Shimmer tooltip. The native window observer records 3,038 samples with zero activation, foreground or on-screen events. Both captures have all volumes at zero and narrator off. See `build/port-evidence/cycle-112-verified-exercise-client/summary.json` and `build/port-evidence/cycle-112-verified-verify-client/summary.json`.
- The original server, conversion and restart all exit at zero with zero runtime and registry errors. The production artifact SHA-256 is `43c2bbfbc238d2ef830971a7d24bdd546b26f5c618306ee7de7ea45f7cc0197e`.

## Remaining verification

The 6.2.18-beta registry result applies to this sampled release and world. Shimmer anvil rules, XP Boost with other XP-modifying mods, other projectile types and broader legacy save fixtures still need runtime coverage.
