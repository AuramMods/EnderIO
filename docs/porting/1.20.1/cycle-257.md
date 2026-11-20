# Cycle 257 — Drain at maximum range across chunks

The packaged Drain runs at radius ten across four real chunks. The source cells sit at the first and last positions of its 21-by-21-by-21 scan, with a third source directly under the machine. The fixture also places matching sources one block beyond the horizontal range and below the scan, plus a different fluid inside the scan. The output tank sits across the chunk edge from the Drain.

After the first bucket, the Drain spends its remaining 600 FE toward the last source. The server removes the tickets, observes that all four chunks are absent, reloads them and reopens the saved world in a new Java process. A 2,400 FE top-up pays 900 FE for the last source and 1,500 FE for the intake after a full pass. The fixture runs once with vanilla lava and once with Mekanism 10.4.16.80 heavy water. The Forge 6.2.18-beta control exercises the existing migration separately. All worlds are disposable and all server runs are headless.

## Commands

```sh
export JAVA_HOME="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
CI=true JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=4 ./gradlew --no-daemon --no-build-cache \
  --no-configuration-cache --max-workers=2 --console=plain :enderio:assemble
export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=4 -Djava.net.preferIPv4Stack=true'
port_forge=build/port-runtime/cycle-047-legacy-discover
port_jar=enderio/build/libs/enderio-8.2.14.256-1.20.1+37ea4a651a.jar
python3 -Bu scripts/porting/packaged_drain_range.py --name cycle-257-drain-core --port 26368 \
  --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" --jar "$port_jar"
python3 -Bu scripts/porting/packaged_drain_range.py --name cycle-257-drain-mekanism --port 26369 \
  --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" --jar "$port_jar" \
  --mekanism-jar build/port-runtime/cycle-142-controls-final/mods/mekanism-uxe1WQp4.jar
python3 -Bu scripts/porting/packaged_drain.py --name cycle-257-drain-migration --port 26370 \
  --java "$JAVA_HOME/bin/java" --forge-server "$port_forge" --jar "$port_jar" \
  --legacy-jar build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar
```

## Results

- Java 17 assembly with `CI=true` succeeds. The reobfuscated Ender IO jar has SHA-256 `308f610395e013727c8c34b0570f77693d67ef20aa066c37b70d6d4968a64e78`. The Mekanism specimen has SHA-256 `3b0d191fa4a45e662f9725991d76a30192f30186abb2ba694b873b5f7b9358d2`. See `build/port-evidence/cycle-257-drain/build.log`.
- Both packaged profiles pass 11 assertions each. They retain the 1,000 mB first collection, 600 FE partial payment and both uncollected sources across real eviction of four chunks, chunk reload and a cold start. After refill, the first and final scan positions contribute to 2,000 mB of export while the intake stays in place. The intake then supplies the last 1,000 mB. The neighboring tank retains exactly 3,000 mB of the original fluid after the last restart, with an empty Drain and all controls and excluded sources intact. Each profile spends a total of 4,500 FE. See `build/port-runtime/cycle-257-drain-{core,mekanism}/drain-range-results.json`.
- The Forge 6.2.18-beta control passes nine assertions. It retains the original placed and carried states and resumes the historical second bucket using the remaining 173 FE. All ten headless server processes across the three profiles exit zero with empty registry and runtime error lists. See `build/port-runtime/cycle-257-drain-migration/drain-results.json`.

## Next verification

Update a remote target while the experimental Enderface preview is open, and exercise selection from more than one camera angle. [Completion audit](completion-audit.md)
