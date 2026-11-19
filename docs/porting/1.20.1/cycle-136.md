# Cycle 136 — Verify both vacuum menus and survival placement

The packaged multiplayer scenario uses a disposable copy of the official Forge 6.2.18 beta save created in cycle 135. It exercises the Vacuum Chest and XP Vacuum through ordinary recipe unlock and crafting, controls, collection with the menu open, their range overlays, factory reset, and placement of each original archived drop. It reopens the resulting world with the dedicated server to verify the operated machines, replanted drops, and reset items.

The client runner waits for the fixture's teleport to settle before using a held machine item. The first placement attempt received a client-side success result while the server reported a movement correction. The saved server world confirmed the destination remained empty. The final test uses nearby placement sites and an ordinary settled player position. The test mod also defers lookup of the registered blocks until its first gameplay events.

The first live XP probe recorded seven points in the player's saved data and seven in the machine. The final fixture keeps the player farther from the orb while the menu stays open and checks the player's XP balance and the machine's fluid directly on the server.

## Commands

```sh
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-136-vacuums-build-final.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
source_client=build/port-runtime/cycle-134-drain-verified-client
port_jar=enderio/build/libs/enderio-8.2.14.135-1.20.1+c8c9eb7d29.jar
legacy_world=build/port-runtime/cycle-135-vacuums-legacy/legacy/port-spawners
client_profile=build/port-runtime/cycle-136-vacuum-complete-client
mkdir "$client_profile"
cp -R "$source_client/libraries" "$source_client/versions" "$client_profile/"
python3 scripts/porting/client/prepare_packaged.py --name cycle-136-vacuum-complete-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_vacuum_menus.py --name cycle-136-vacuum-complete --port 25998 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" \
  --launch "$client_profile/launch.json" --legacy-world "$legacy_world"
```

## Results

- The full build passes 79 unit cases and 507 native Forge runs, including all 185 main gameplay cases. The final packaged run passes 45 client checks and 24 server assertions. The client and server use SHA-256 `3b1925716ae0db0246aecf18951058b8f8a29af3058b9ddcc7cb6bcfcec4e493`.
- Picking up a Pulsating Crystal unlocks both shaped recipes. Each native survival craft consumes seven iron ingots, one crystal and one center ingredient, a chest or Void Vial respectively. The factory-reset recipes consume actual populated machine loot and produce one clean machine item each. Both reset outputs remain clean after a dedicated restart.
- Both menus receive their saved settings, accept the plus, minus, visibility and redstone controls, and render their overlays at the expected world coordinates. The Vacuum Chest shift-clicks its migrated filter out and back in, then accepts three tagged ingots, leaves five mismatched ones in the world, and transfers its five stored ingots to the player and back. The XP Vacuum menu updates from 140 to 420 mB after collecting all 14 points in a merged orb. The server confirms the player still has zero XP.
- Ordinary survival placement consumes both original Forge 6 drops. The restored Vacuum Chest keeps its two tagged ingots, filter and visible range of one. The restored XP Vacuum keeps 140 mB and a visible range of two. Both maintain their signal-based redstone mode. A fresh server process reloads the operated machines, restored machines and reset items. Both server processes exit normally with zero runtime and registry errors. See `build/port-runtime/cycle-136-vacuum-complete/vacuum-menu-results.json`.
- The eight captures show both native screens, the XP Juice texture, colored range overlays and the reset previews. The client records zero activation, foreground or onscreen events in 1,547 observations. Every capture records all volumes at zero and the narrator off. See `build/port-evidence/cycle-136-vacuum-complete/summary.json` and the captured screens.

## Remaining verification

Separate chunk eviction and reload during active vacuum collection remains open, along with Vacuum Chest behavior for installed filter types beyond the basic and limited filters exercised in cycles 135 and 136. The Impulse Hopper is still pending in the machine inventory.
