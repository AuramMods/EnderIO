# Cycle 106 — Carry and replant a ready Enchanter

Verify that survival players can mine a ready Enchanter, pick up its single drop, and place it elsewhere with its ingredients intact. The recipe uses a writable book with a page, 25 quartz and 10 lapis. After restarting the dedicated server, verify that the replanted machine denies collection with 10 levels and allows it at 30 levels.

The shared carry client drives Minecraft's native mining, pickup and placement. The dedicated server checks the dropped item, the player's inventory, the replaced machine and the saved state. The existing Enchanter collection client then exercises the replanted machine's menu and checks the exact payment.

## Commands

```sh
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-106-qa.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_prior=build/port-runtime/cycle-105-fixed-client
port_client=build/port-runtime/cycle-106-final-client
mkdir -p "$port_client"
cp -R "$port_prior/libraries" "$port_prior/versions" "$port_client/"
python3 scripts/porting/client/prepare_packaged.py --name cycle-106-final-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_prior/mods/enderio.jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_enchanter_carry.py --name cycle-106-final --port 25901 \
  --java "$port_java" --forge-server build/port-runtime/cycle-047-legacy-discover \
  --jar "$port_client/mods/enderio.jar" --launch "$port_client/launch.json"
```

## Results

- The first collection client reconnected at the target position and clicked immediately without opening the menu. The scenario now lets the new session settle at the machine before sending that native interaction. The mined item, replanted machine and state after the first restart had already passed their server checks.
- The QA jar builds successfully. The packaged scenario passes 13 guarded client checks and nine server assertions using Forge 47.4.28 and the cycle 105 production jar, SHA-256 `0630a8393de70f55866dc0548bf6038f86b7c14303a984de15fd6c7892d8f90f`. All three server processes exit at zero without runtime or registry errors. See `build/cycle-106-qa.log` and `build/port-runtime/cycle-106-final/enchanter-carry-results.json`.
- Mining drops one Enchanter with the writable book's page, 25 quartz and 10 lapis. Pickup and placement preserve the ingredients, restore the Sharpness II preview and consume the carried block. A full restart preserves the ready machine. At 10 levels the player cannot take the book. At 30 levels the player takes one Sharpness II book and keeps 19 levels. The machine keeps one quartz and four lapis. A second restart verifies those remainders and an empty output slot.
- The two clients record 2,519 native window samples without activation, foreground or on-screen events. All three captures have zero volume in every category and narrator off. See `build/port-evidence/cycle-106-final-carry-client/summary.json` and `build/port-evidence/cycle-106-final-exercise-client/summary.json`.

## Remaining verification

Historical Forge 6 Enchanter items use `BlockEntityTag` instead of the current carry format. Their upgrade path, other enchanting recipes, config edits during an open session and broader visual checks remain open.
