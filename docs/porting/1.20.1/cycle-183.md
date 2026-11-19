# Cycle 183 — Stock SAG Mill grinding balls

This fixture covers the eight stock alloy grinding balls and vanilla flint. It reuses the stock Alloy Smelter progression from [cycle 182](cycle-182.md). The multiplayer client takes the original ingots from the output chest and uses the recipe book with `doLimitedCrafting` enabled. The server and client check that each recipe spends five ingots and yields 24 balls. A native inventory click moves the crafted Dark Steel stack into a SAG Mill. The server transfers the other original stacks from the player's inventory to the SAG Mill feeder hoppers and checks that no duplicate stacks remain.

On the 1.21.1 baseline, the mill claims its first grinding ball after completing a recipe that supports bonuses. The first coal job therefore spends the unmodified 2,400 FE, and the next job uses the ball. Earlier stone processing also spends 2,400 FE and leaves the flint unclaimed because the recipe disables bonuses. The fixture verifies both behaviors and pauses the second coal job at 300 FE. The saved state must contain the stock modifiers, the exact energy cost, 300 wear, and the already rolled output. After restart, the client opens all nine native SAG Mill menus and checks the synced state and tooltip. The server pays the remaining FE and checks the hopper receives the same roll that was saved.

The final stage puts each ball one FE away from exhaustion using the vanilla data command. The live recipe must keep its original cost and output after the ball expires. The mill must claim one fresh replacement only after finishing the job.

| Stock item | Main output multiplier | Bonus multiplier | Power multiplier | Full durability | Coal cost |
| --- | ---: | ---: | ---: | ---: | ---: |
| Conductive Alloy | 1.35 | 1.00 | 1.00 | 40,000 | 2,400 FE |
| Dark Steel | 1.35 | 2.00 | 0.70 | 125,000 | 1,680 FE |
| End Steel | 1.40 | 2.40 | 0.70 | 75,000 | 1,680 FE |
| Energetic Alloy | 1.60 | 1.10 | 1.10 | 80,000 | 2,640 FE |
| Pulsating Alloy | 1.00 | 1.85 | 1.00 | 100,000 | 2,400 FE |
| Redstone Alloy | 1.00 | 1.00 | 0.35 | 30,000 | 840 FE |
| Soularium | 1.20 | 2.15 | 0.90 | 80,000 | 2,160 FE |
| Vibrant Alloy | 1.75 | 1.35 | 1.13 | 80,000 | 2,712 FE |
| Flint | 1.20 | 1.25 | 0.85 | 24,000 | 2,040 FE |

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_forge="$port_runtime/cycle-047-legacy-discover"
port_jar=enderio/build/libs/enderio-8.2.14.182-1.20.1+3cfdf46a22.jar
port_guarded="$port_runtime/cycle-182-alloys-verified-client"
port_client="$port_runtime/cycle-183-balls-settled-client"

python3 -u scripts/porting/packaged_grinding_balls.py --name cycle-183-balls-headless-fixed --port 26159 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_runtime/cycle-182-alloys-verified/mods/enderio.jar"
./gradlew build :enderio:reobfClientSmokeJar --continue --console=plain > build/cycle-183-balls-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-183-balls-qa-rebuild.log 2>&1
mkdir -p "$port_client/config"
cp -cR "$port_guarded/libraries" "$port_guarded/versions" "$port_client/"
cp "$port_guarded/config/fml.toml" "$port_client/config/"
python3 scripts/porting/client/prepare_packaged.py --name cycle-183-balls-settled-client \
  --java "$port_java" --installer "$port_runtime/forge-47.4.28-installer.jar" --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 -u scripts/porting/packaged_grinding_balls.py --name cycle-183-balls-settled --port 26161 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --launch "$port_client/launch.json"
python3 -u scripts/porting/packaged_sag_progression.py --name cycle-183-sag-fixture-control --port 26162 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar"
```

## Results

- The first headless attempt exposed a fixture error. The shared assertion expected its original 600 FE checkpoint, while all nine persisted tasks had this fixture's intended 300 FE paid. The corrected shared helper defaults to 600 FE for the original caller and accepts the stock test's 300 FE checkpoint. The retry passes 48 assertions across two dedicated server sessions. See `build/port-runtime/cycle-183-balls-headless-fixed/grinding-balls-results.json`.
- The full build passes 79 ordinary unit tests and 540 native test runs, including all 213 discovered main gameplay cases. The production jar contains no client QA classes. See `build/cycle-183-balls-build.log`.
- The first TCP run verifies crafting all eight recipes. The subsequent fixture teleport crosses 34 blocks and the server corrects movement before the native SAG Mill menu opens. The client times out waiting for the menu. The corrected fixture waits for the player to settle before sending the actual use packet, following the existing SAG Mill test. See `build/port-evidence/cycle-183-balls-verified-exercise-client/result.json` and the server's `smoke-fresh.log`.
- The final packaged run passes 62 server assertions and 76 client checks. The original ingots from the eight alloys produce 192 balls, and the Dark Steel stack moves intact through the native SAG Mill menu. After restart, the client confirms all nine menus and tooltips. Each hopper receives the exact previously rolled powder count. The Vibrant Alloy mill pays out a three-powder roll, above the two-powder maximum for unmodified coal. All nine balls keep their queued job intact on exhaustion and take exactly one replacement at completion. Both server sessions exit cleanly with zero runtime or registry errors.
- Both peers load production SHA-256 `fe5f17df5ff01c71cd1653b36d3407019a6d0bb0ff10b40c6de828e69868a8ee`. The guarded launches record zero activation, foreground or visible-window events across 4,180 observer samples. All eleven captures confirm all volume categories at zero and narrator off. See `build/port-runtime/cycle-183-balls-settled/grinding-balls-results.json` and the two `build/port-evidence/cycle-183-balls-settled-*-client/summary.json` reports.
- The earlier flint and authored-ball fixture passes 13 assertions across two packaged server sessions. It still uses the original 600 FE checkpoint and returns to the full 2,400 FE coal cost when the authored ball expires with no spare. See `build/port-runtime/cycle-183-sag-fixture-control/sag-progression-results.json`.

## Next verification

Continue survival progression through Soul Chains, Ensouled Chassis and the alloy gears. Reuse stock alloy production and extend the SAG Mill fixture to produce quartz powder from the shipped recipe.
