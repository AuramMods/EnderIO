# Cycle 130 — Verify resetting levers and pressure plates

The new native Forge tests use the 26 registered blocks and their loaded recipes and loot tables. Both versions of each resetting lever must hold their signal through the tick before the deadline and reset on the deadline. The pressure plate test measures output from items, passive mobs, hostile mobs and Forge survival players. It records the actual sound events sent by each plate's press and release paths.

The packaged test uses a fresh, headless dedicated server with the production jar. Vanilla lamps read lever polarity and pressure plate output. The server mines each variant through the native loot command, saves the world while the plates are occupied, and checks that the plates remain active where expected and release after their occupants are removed following a restart.

## Commands

```sh
./gradlew :enderio:runGameTestServer --console=plain > build/cycle-130-native.log 2>&1
cp enderio/build/test-results/gameTestServer/results.xml build/cycle-130-native-before.xml
./gradlew build --continue --console=plain > build/cycle-130-build.log 2>&1
cp enderio/build/test-results/gameTestServer/results.xml build/cycle-130-native-fixture.xml
./gradlew build --continue --console=plain > build/cycle-130-verified-build.log 2>&1
./gradlew :enderio:runGameTestServer --rerun --console=plain > build/cycle-130-native-repeat.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
production_jar=enderio/build/libs/enderio-8.2.14.129-1.20.1+eae53001ce.jar
python3 scripts/porting/packaged_decorative_redstone.py --name cycle-130-verified-packaged --port 25980 \
  --java "$port_java" --forge-server build/port-runtime/cycle-047-legacy-discover --jar "$production_jar"

PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=scripts/porting python3 - <<'PY'
from zipfile import ZipFile
from packaged_decorative_redstone import LEVERS, PLATES

with ZipFile("build/port-runtime/cycle-130-verified-packaged/server/mods/enderio.jar") as jar:
    paths = set(jar.namelist())
    for folder in ("blockstates", "models/item"):
        expected = {f"assets/enderio/{folder}/{block}.json" for block in (*LEVERS, *PLATES)}
        print(folder, len(expected & paths), "of", len(expected), "missing", sorted(expected - paths))
PY
```

## Results

- The final build passes 79 unit tests and 493 native Forge cases, with no failures or skips. The main gameplay profile discovers and passes 171 cases and repeats successfully. The initial recipe count in the fixture was one short. The baseline and installed recipes both include 28 lever crafts and 16 pressure plate crafts. All 44 match native crafting ingredients and assemble their expected results. All 26 blocks yield exactly one of their own item from the installed loot tables.
- Each registered normal and inverted lever passes the main-hand and off-hand interaction checks and resets at exactly 100, 200, 600, 1,200 or 6,000 ticks. The weak and support-side strong output retain the expected normal or inverted polarity.
- Both Dark Steel plate variants detect players. Both Soularium variants detect zombies and ignore players, passive cows and dropped items. The ten silent wood and stone variants detect item entities. Twelve separate items produce power 2 on the heavy plate and power 12 on the light plate. The two audible plates emit the iron press and release sounds. All fourteen silent variants emit the empty sound on both transitions.
- The first packaged mob fixture used motionless zombies. A control showed that those zombies failed to activate either an Ender IO plate or a vanilla stone plate. Both activated when zombies could move and fall onto the plates. The packaged run now uses mobile zombies confined to their pressure plate. The native fixture moves the test mobs through the normal block collision path. See the original failure in `build/cycle-130-native-fixture.xml` and the vanilla control at `build/port-runtime/cycle-130-decorative-redstone/server/smoke-plate-live-mob.log`.
- The packaged jar passes 112 assertions. A fresh world loads all 26 blocks and their native loot. Vanilla lamps show each lever's resting polarity and follow the item, hostile-mob and weighted plate states. The restart preserves those outputs with the occupants still present. Removing the occupants releases the plates and darkens their lamps. Both dedicated sessions exit zero with no registry or runtime errors. The production SHA-256 is `3de350157e8d1a1eb5bf3087889604e35527ed3265235294aeca610f9a2f8676`. See `build/port-runtime/cycle-130-verified-packaged/decorative-redstone-results.json`.

## Remaining verification

The release jar contains blockstate and inventory model JSON for all 26 variants. The client still needs to bake and render those models in a packaged profile. In-progress lever pulses across a dedicated restart and historical saves are separate from the verified runtime timing.
