# Cycle 109 — Restore Soulbound on historical gear

Restore `enderio:soulbound` at its original ID so active enchantments on old tools, armor and shields still protect the stack when a player dies. The historical tier and original default cost range also allow old books and datapacks to resolve the native enchantment. Soulbound is hidden from random discovery and villager trades to keep it available through historical gear, books and authored recipes.

The Forge death event preserves stacks that carry the active enchantment. It writes their exact stack NBT to the player's persisted Forge data and removes them from the captured drops. Respawn restores the stacks and clears the pending entry. A player who logs in alive with pending data can recover it through the login handler. Vanilla `keepInventory` governs normal retention when enabled.

The fixture creates an Ender IO 6.2.18-beta world with a damaged Soulbound pickaxe, chestplate and shield, plus a stored Soulbound book. The pickaxe also has Efficiency III. The port equips the original stacks, triggers a native survival death, and compares the ground drops and player data. A second death tests logging out before respawn and restarting with the same player save.

## Commands

```sh
./gradlew :enderio:runData --console=plain > build/cycle-109-datagen.log 2>&1
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-109-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_prior=build/port-runtime/cycle-107-legacy-client
port_before=build/port-runtime/cycle-108-final/port/mods/enderio.jar
port_jar=enderio/build/libs/enderio-8.2.14.108-1.20.1+6fb26d0435.jar

prepare_client() {
  port_name="$1"
  port_dir="build/port-runtime/$port_name"
  mkdir -p "$port_dir"
  cp -R "$port_prior/libraries" "$port_prior/versions" "$port_dir/"
  python3 scripts/porting/client/prepare_packaged.py --name "$port_name" --java "$port_java" \
    --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$2" \
    --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
}

run_soulbound() {
  port_client="build/port-runtime/$1-client"
  python3 scripts/porting/packaged_legacy_soulbound.py --name "$1" --port "$2" --java "$port_java" \
    --forge-server "$port_forge" --legacy-jar "$port_forge/mods/enderio.jar" \
    --jar "$port_client/mods/enderio.jar" --launch "$port_client/launch.json"
}

prepare_client cycle-109-before-client "$port_before"
prepare_client cycle-109-fixed-client "$port_jar"
run_soulbound cycle-109-before 25906
run_soulbound cycle-109-fixed 25907
```

## Results

- The previous port reads the original enchanted item data, but after native death all three Soulbound items appear on the ground. See `build/cycle-109-before.log` and `build/port-runtime/cycle-109-before/port/smoke-converted.log`.
- Datagen and the build pass all 79 unit cases and 483 native GameTest executions, including all 161 discovered gameplay tests. See `build/cycle-109-datagen.log` and `build/cycle-109-build.log`.
- The packaged migration passes 11 server assertions. All three enchanted items return exactly once at native respawn, including when the player quits while dead and reconnects after a full server restart. The copies retain their original damage and tags, including Efficiency III on the pickaxe. The stored-enchantment book and six unbound emeralds drop normally. See `build/port-runtime/cycle-109-fixed/legacy-soulbound-results.json`.
- Three guarded clients pass 13 checks and record 2,463 window samples with zero activation, foreground or on-screen events. All three captures have every volume at zero and narrator off. See `build/port-evidence/cycle-109-fixed-first-death-client/summary.json`, `build/port-evidence/cycle-109-fixed-disconnect-client/summary.json` and `build/port-evidence/cycle-109-fixed-restart-respawn-client/summary.json`.
- All four dedicated processes exit at zero. Initial conversion reports five outstanding historical enchantments. The two later startups have zero registry diagnostics. The production artifact SHA-256 is `0bd244a2cfd8a92f5dd267b8d57c1b6d181b46f6eb3c2992aae42239ee0e499b`.

## Remaining verification

Historical IDs `auto_smelt`, `repellent`, `shimmer`, `withering` and `xp_boost` remain open. Soulbound still needs native anvil, `keepInventory`, full-inventory recovery and death-management mod coverage.
