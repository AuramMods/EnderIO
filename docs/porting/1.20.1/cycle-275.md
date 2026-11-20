# Cycle 275 — Zero-XP Soul Binder and JEI menus

The guarded survival client uses every stock silverfish recipe in the real multiplayer Soul Binder menu. For each host block, an ordinary dispenser captures a different named silverfish. The server gives the resulting vial to the player along with an Endermite vial, an empty vial and two marked host blocks.

JEI finds the seven recipes from an actual captured silverfish vial and finds each individual result through its host block and infested output. The recipe card shows the matching Silverfish vial, an empty return vial, the infested block, `Enchantment Cost: 0` and `10,000 µI` at player level zero. The stock recipe transfer selects one marked host and the exact captured vial. The client-only JEI profile exercises the same recipe viewer and enters the two inputs through the native menu.

In both profiles, the native Binder renders a zero level requirement with an empty XP tank while synchronizing 400 FE of work and both original inputs. The saved jobs retain that progress through restart. Each completes after receiving the remaining budget, produces one exact infested block and one empty vial, and keeps all 37 mB of XP Juice added after the craft began. The Endermite vial, empty vial and second marked host remain in the player inventory during insertion.

## Commands

```sh
export JAVA_HOME="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
export CI=true JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=4
./gradlew --no-daemon --no-build-cache --no-configuration-cache --max-workers=2 --console=plain :enderio:reobfClientSmokeJar

zsh build/cycle-275-silverfish-profiles.zsh cycle-275-silverfish-shared 26438 --silverfish
zsh build/cycle-275-silverfish-profiles.zsh cycle-275-silverfish-client-side 26439 --silverfish --client-only

export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Djava.net.preferIPv4Stack=true'
port_runtime=build/port-runtime
port_forge="$port_runtime/cycle-047-legacy-discover"
port_jar=build/port-evidence/cycle-272-release/source-local/enderio/build/libs/enderio-8.2.14.271-dev+a00893d995.jar
python3 -Bu scripts/porting/packaged_silverfish_binding.py --name cycle-275-silverfish-core --port 26437 \
  --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" --jar "$port_jar"
python3 -Bu scripts/porting/packaged_soul_binder_jei.py --name cycle-275-soul-rules-core --port 26440 --rules \
  --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" --jar "$port_jar"
```

The archived profile launcher copies the Forge libraries into isolated client directories, installs the cycle 272 production jar and the new reobfuscated QA jar, and installs pinned JEI `15.56.0.205` on the requested sides. Both launches use the native macOS focus observer and the test client's muted options.

## Results

- The fresh Java 17 QA build passes. The client QA jar hashes to `8b538a02553e2514e34c17e2cb6bbb212f48188eb1c846083d6fcd157df281a5`. The packaged Ender IO runtime hashes to `58803ec07dc520e3da7ec4dc413ba04dc78db24b524aa20435e00f8d2cbf98bd`. Pinned JEI hashes to `6b251e60a4719c874da89d750af97aa6498f6f4475e3ae79cb1288c7bf0cb65c`. See `build/port-evidence/cycle-275-silverfish-jei/qa-build.log`.
- JEI installed on both sides passes 128 client checks and 58 server assertions. Client-only JEI passes 121 client checks and 58 server assertions on a Forge server with Ender IO alone. Both profiles keep all seven completed results across the second restart. All six dedicated sessions exit at zero with zero runtime or registry errors.
- Fourteen JEI captures show the same zero level and 10,000 µI labels, and fourteen Binder captures show the same zero requirement. The visually inspected label regions match pixel for pixel across both profiles. The observer records 3,596 samples with zero activations, foreground observations or onscreen observations. Every capture reports narration off and all ten volume categories at zero.
- The Forge-only silverfish control repeats all 73 assertions. The positive-XP Animal Token and reloadable Soul Engine control passes 22 assertions on Forge without JEI. All six control server sessions exit cleanly.
- Complete results and frames are in `build/port-evidence/cycle-275-silverfish-{shared,client-side}-client/`. The server reports are in `build/port-runtime/cycle-275-silverfish-{shared,client-side}/soul-binder-jei-results.json`. The visual comparison and verified profiles are in `build/port-evidence/cycle-275-silverfish-jei/{rendered-labels,accepted-profiles}.json`.

## Next verification

Compare the stock VAT recipe cards with the dedicated server's reagent and output rules. Verify the viewer for the ten stock outputs, including the high-yield Blue Ice variant. [Completion audit](completion-audit.md)
