# Cycle 267 — Attractor handling of third-party bosses

The packaged Forge server loads Twilight Forest 4.3.2508 in a disposable world. Its archive contributes `#twilightforest:bosses` to `#forge:bosses`. Ender IO's generated data includes that Forge tag in `#c:bosses`, which the Attractor reads. Native selectors confirm that both the registered Minoshroom and Ur-Ghast belong to both tags, while the Minotaur control belongs to neither. [Twilight Forest Forge release](https://www.curseforge.com/minecraft/mc-mods/the-twilight-forest/files/5468648)

The fixture charges the Attractor and installs an empty inverted soul filter, a setting available in the filter menu. The read-only QA observer samples actual mob ticks, positions, navigation destinations and velocity. The test compares the stock boss setting with the opt-in on a cold Forge restart. The Minotaur provides a positive navigation control in both stages. The boss checks measure the Minoshroom's path toward the machine and the Ur-Ghast's directed pull velocity. The earlier Wither, Ghast, Phantom and Slime scenario runs against the same Ender IO and QA archives in a separate headless world.

## Commands

```sh
export JAVA_HOME="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
export CI=true JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=4
./gradlew --no-daemon --no-build-cache --no-configuration-cache --max-workers=2 --console=plain \
  :enderio:assemble :enderio:reobfClientSmokeJar

port_runtime=build/port-runtime
port_forge="$port_runtime/cycle-047-legacy-discover"
port_jar=enderio/build/libs/enderio-8.2.14.266-1.20.1+72043aa69e.jar
port_qa=enderio/build/test-libraries/enderio-client-qa.jar
port_twilight="$port_runtime/cycle-267-artifacts/twilightforest-1.20.1-4.3.2508-universal.jar"
mkdir -p "$port_runtime/cycle-267-artifacts"
curl -fL --retry 2 'https://edge.forgecdn.net/files/5468/648/twilightforest-1.20.1-4.3.2508-universal.jar' -o "$port_twilight"

export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Djava.net.preferIPv4Stack=true'
python3 -B scripts/porting/packaged_attractor.py --name cycle-267-twilight-survival-filter --port 26412 \
  --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" --jar "$port_jar" --qa-jar "$port_qa" \
  --twilight-forest "$port_twilight"
python3 -B scripts/porting/packaged_attractor.py --name cycle-267-core-attractor --port 26413 \
  --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" --jar "$port_jar" --qa-jar "$port_qa"
```

## Results

- Java 17 assembly and QA reobfuscation pass. The runtime SHA-256 is `1743264193feb06522f246c306e564a30f38356f2090a0c04729030e33c5e5b3`. The QA SHA-256 is `b82c3e2ed844f066cb1ce33fd7d47b905fd2fa20329348d86c82a7d393e2ef3b`. The Twilight Forest SHA-256 is `0bdc89263616d1b35c32ef82c5e9c14cbd20368e2fe8b468c72a28320be7a778`. See `build/port-evidence/cycle-267-attractor-bosses/build.log`.
- The initial native Twilight Forest run with explicit sample entries passes 13 assertions. The final run replaces those entries with an empty inverted filter so the settings can be reproduced in survival. See `build/port-runtime/cycle-267-twilight-bosses/attractor-results.json`.
- The final Twilight Forest profile passes 13 assertions. Both bosses have 16 default-mode observations without a machine-directed path or pull. The Minotaur walks toward the machine in both modes. After the opt-in and cold restart, the Minoshroom follows a path to `[2225, 80, 8]` and closes from 11.41 to 7.84 blocks. The Ur-Ghast receives six observed one-block-per-tick pulls and closes from 19.45 to 1.89 blocks. See `build/port-runtime/cycle-267-twilight-survival-filter/attractor-results.json`.
- The core profile passes all nine existing checks for Ghast movement, Phantom and Slime decoy release, and the default and opt-in Wither behavior. All six headless server sessions across the three runs exit cleanly without runtime or registry errors. The distribution excludes the observer and the Twilight Forest archive. See `build/port-runtime/cycle-267-core-attractor/attractor-results.json`.

## Next verification

Exercise an earlier published 6.2 build in a disposable migration fixture and compare its saved state and active routes with the tested 6.2.18-beta cases. [Completion audit](completion-audit.md)
