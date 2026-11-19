# Cycle 111 — Restore Withering and Repellent from Forge 6 saves

Restore the original `enderio:withering` and `enderio:repellent` registry IDs and their native Forge combat callbacks. Withering applies the original 200-tick Wither I effect when the attacker's main-hand item carries the enchantment. The native projectile callback also uses the shooter's held item. Repellent calls EnderCore's existing teleport helper after a player wearing the armor takes a successful hit from a living attacker.

The common configuration reads the historical Withering cost keys and Repellent level, cost, chance, range and non-player chance keys. The fixture sets Repellent IV to a guaranteed proc within the same 40-block per-axis range as its historical default. The enchantments are available on existing gear, books and authored recipes.

An Ender IO 6.2.18-beta server writes the exact sword, bow and chestplate stacks into a disposable world. The port equips these original stacks over a quiet TCP client. The scenario uses native survival sword actions, a vanilla arrow with the player as its owner, and a stationary zombie's melee attack. An offhand-only Withering sword and ordinary chestplate provide controls. A restart reopens the same player and barrel data and repeats the interactions.

## Commands

```sh
./gradlew :enderio:runData --console=plain > build/cycle-111-datagen.log 2>&1
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-111-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_prior=build/port-runtime/cycle-110-fixed-client
port_jar=enderio/build/libs/enderio-8.2.14.110-1.20.1+29b1329e37.jar

prepare_client() {
  port_client="build/port-runtime/$1"
  mkdir -p "$port_client"
  cp -R "$port_prior/libraries" "$port_prior/versions" "$port_client/"
  python3 scripts/porting/client/prepare_packaged.py --name "$1" --java "$port_java" \
    --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$2" \
    --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
}

run_combat() {
  port_client="build/port-runtime/${3:-$1-client}"
  python3 scripts/porting/packaged_legacy_combat.py --name "$1" --port "$2" --java "$port_java" \
    --forge-server "$port_forge" --legacy-jar "$port_forge/mods/enderio.jar" \
    --jar "$port_client/mods/enderio.jar" --launch "$port_client/launch.json"
}

prepare_client cycle-111-before-client "$port_prior/mods/enderio.jar"
prepare_client cycle-111-fixed-client "$port_jar"
run_combat cycle-111-before 25910
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-111-qa-final.log 2>&1
prepare_client cycle-111-final-client "$port_jar"
run_combat cycle-111-verified 25911 cycle-111-final-client
```

## Results

- The previous port loads all three gear stacks. The player stats record a successful sword hit, and the target has no active Wither effect. The patched jar's first run records Wither I with 199 ticks remaining. The fixture now uses Forge's namespaced effect ID for its server checks. See `build/cycle-111-before.log`, `build/port-runtime/cycle-111-before/port/smoke-converted.log` and `build/port-runtime/cycle-111-fixed/port/smoke-converted.log`.
- Datagen and the full build pass 79 unit cases and 483 Forge GameTest executions, including all 161 discovered gameplay tests. The revised guarded client compiles separately. See `build/cycle-111-datagen.log`, `build/cycle-111-build.log` and `build/cycle-111-qa-final.log`.
- The packaged migration passes 26 server assertions. The original sword spends one durability and applies Wither I. The offhand-only control deals melee damage without applying Wither. A player-owned arrow applies Wither I while the original bow is held. Both effect timers match the historical 200-tick duration at the server's first observation. The ordinary-armor trial leaves the stationary zombie at its original position after the hit. Repellent IV teleports the attacker within the configured 40-block bounds. The reopened world verifies the saved equipment and repeats all interactions. See `build/port-runtime/cycle-111-verified/legacy-combat-results.json`.
- Both guarded clients pass three checks each. The native observer records 3,421 window samples with zero activation, foreground or on-screen events. Both captures have all volumes at zero and narrator off. See `build/port-evidence/cycle-111-verified-converted-client/summary.json` and `build/port-evidence/cycle-111-verified-reopened-client/summary.json`.
- All three dedicated processes exit at zero. The production artifact SHA-256 is `4b38b9aba6d7a29485a4bb40e93ac1a94a092fb3931473baee99c99a2a5ba9da`. Initial conversion reports Shimmer and XP Boost as missing. The reopened world has zero registry diagnostics.

## Remaining verification

Historical Shimmer and XP Boost still need migration. Withering crossbow firing, Repellent on player attackers and anvil behavior need additional runtime coverage.
