# Cycle 256 — Vacuum collection through chunk eviction

The packaged fixture fills every slot of the Vacuum Chest's output barrel and fills the XP Vacuum's output tank with water. A Limited Item Filter caps the chest at four tagged ingots, leaving seven pending on the ground. The XP Vacuum has room for two of 21 points. It collects 40 mB, retains two seven-point orbs and splits off one five-point orb. An independent XP Vacuum supplies the 420 mB export control.

The server removes both force-load tickets while the machines are active. It observes that the chunks and their pending entities are absent, then reloads them and checks both blockers, the saved controls and the exact ground and stored quantities. It checks the same blocked state after a cold start. Emptying just the receivers lets the still-powered machines deliver the remaining inputs. A second cold start checks final conservation. Three additional chests exercise the large, advanced and large advanced filter tiers with a sample in the last slot of each filter. All fixtures are disposable and run headless with the reobfuscated distribution.

## Commands

```sh
export JAVA_HOME="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
CI=true JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=4 ./gradlew --no-daemon --no-build-cache \
  --no-configuration-cache --max-workers=2 --console=plain :enderio:assemble
export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Djava.net.preferIPv4Stack=true'
port_forge=build/port-runtime/cycle-047-legacy-discover
port_jar=enderio/build/libs/enderio-8.2.14.255-1.20.1+afda89059f.jar
python3 -Bu scripts/porting/packaged_vacuums.py --name cycle-256-vacuum-lifecycle --port 26365 \
  --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" --jar "$port_jar"
python3 -Bu scripts/porting/packaged_vacuums.py --name cycle-256-vacuum-migration --port 26366 \
  --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" --jar "$port_jar" \
  --legacy-jar build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar
python3 -Bu scripts/porting/packaged_vacuums.py --name cycle-256-vacuum-lifecycle-and-filters --port 26367 \
  --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" --jar "$port_jar"
```

## Results

- Java 17 assembly with `CI=true` exits successfully. The packaged jar has SHA-256 `a41135c20c5d77a15451829aab24a5bdeea069084ed29355d1ac5cb40ca8ce72`. See `build/port-evidence/cycle-256-vacuum-lifecycle/build.log`.
- The initial lifecycle run passes 26 assertions. It confirms that all three item entities and both orb entities unload and reload, along with both blockers. Both vacuums resume through their existing output faces. The barrel receives exactly 11 tagged ingots. The two input rejects stay on the ground. The XP Vacuum and its receiver finish with 2,147,468,020 mB and 16,000 mB of tagged XP Juice, accounting for the starting juice and all 21 points. No orbs remain in the fixture. The same balances survive the second restart. See `build/port-runtime/cycle-256-vacuum-lifecycle/vacuum-results.json`.
- The expanded lifecycle run passes all 29 assertions. The large deny-list rejects tagged paper with the filter's sample in slot 36. The advanced filters match custom tags independently of tool wear, then reject an undamaged or excessively worn tool according to their configured damage rule. Each chest preserves its installed filter, collects its allowed stack, and leaves its rejected stacks on the ground. All three Java processes exit zero without registry or runtime errors. See `build/port-runtime/cycle-256-vacuum-lifecycle-and-filters/vacuum-results.json`.
- The Forge 6.2.18-beta control passes all 13 assertions in a fresh historical world and its converted copy. The original placed machines and portable drops keep their settings through conversion and restart, and the machines still collect their exact inputs. All three Java processes exit zero without registry or runtime errors. See `build/port-runtime/cycle-256-vacuum-migration/vacuum-results.json`.

## Next verification

Exercise the Drain's maximum-radius scan across a chunk boundary and recapture an actual optional-mod fluid source. [Completion audit](completion-audit.md)
