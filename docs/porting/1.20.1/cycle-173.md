# Cycle 173 — Verify glass collision, lighting and blast behavior

The 42 families span two materials, three lighting modes and seven collision rules. Each has an undyed block and 16 dyed blocks. Native Forge movement carries a test player, a zombie, a cow and a dropped item toward each of the 714 registered variants. For each entity the test measures the resulting position against the expected stopping point or full movement. It also verifies the full targeting outline and the collision shape used without an entity. A native explosion breaks blue dark clear glass and leaves the corresponding blue dark fused quartz in place.

Two stone shafts isolate light from the surrounding world. One contains dyed clear glass and the other undyed fused quartz, with different collision rules. In the native GameTest, glowstone sends block light of 14 through plain glass and 13 to the space below it. Dark glass reduces both positions to zero. With the glowstone removed, enlightened glass emits 15 at the block and 14 below it. Replacing it with plain glass returns both positions to zero.

The packaged Forge server uses vanilla location predicates to measure the same variant families. At noon plain glass admits light level 15 and dark glass blocks it. At midnight enlightened glass supplies 15 at the block and 14 below it, and the light fades when the plain variants return. The final dark-blue-glass and enlightened-quartz layout is checked after both chunks evict and reload, then after a dedicated restart.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_runtime=build/port-runtime
port_before=enderio/build/libs/enderio-8.2.14.171-1.20.1+f5f162dbbe.jar
port_jar=enderio/build/libs/enderio-8.2.14.172-1.20.1+6e12b24c82.jar

./gradlew :enderio:verifyGameplayTests --console=plain > build/cycle-173-gameplay-registered.log 2>&1
python3 -u scripts/porting/packaged_glass_light.py --name cycle-173-before-packaged \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" \
  --jar "$port_before" --port 26120 > build/cycle-173-before-packaged.log 2>&1
./gradlew build --continue --console=plain > build/cycle-173-build.log 2>&1
python3 -u scripts/porting/packaged_glass_light.py --name cycle-173-verified-packaged \
  --java "$port_java" --forge-server "$port_runtime/cycle-047-legacy-discover" \
  --jar "$port_jar" --port 26120 > build/cycle-173-verified-packaged.log 2>&1
```

## Results

- The original native shaft fixture read zero skylight above the plain glass. The packaged server on the previous build passes all ten assertions including daytime skylight. The final native fixture uses a controlled glowstone source to exercise block-light transmission and blocking. The packaged fixture retains the daylight and save/reload checks. See `build/port-evidence/cycle-173-gametest-skylight.xml` and `build/port-runtime/cycle-173-before-packaged/glass-light-results.json`.
- The full build passes 79 unit tests and 533 native runs, including all 206 discovered main gameplay tests and the three new glass cases. Each of the 714 movement variants passes four independent entity checks. See `build/cycle-173-build.log` and `enderio/build/test-results/gameTestServer/results.xml`.
- The rebuilt jar passes all ten packaged assertions. Both Forge sessions exit cleanly with zero runtime or registry errors. The packaged jar matches the build output with SHA-256 `bc502fb535219a5e5237d9ede54e584d739652a85301e2a0b509f61d445d8ab2`. See `build/port-runtime/cycle-173-verified-packaged/glass-light-results.json`.
- The existing gameplay implementation passes these checks. This cycle adds the native GameTests and headless packaged fixture.

## Next verification

Exercise the stock glass smelting, token, inversion and dye recipes in survival. Verify the authored translucent and shadow shader paths.
