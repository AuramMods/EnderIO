# Cycle 156 — Preserve SAG Mill output stacks and verify the coal progression

The SAG Mill merged same-item results even when their tags differed. It calculated the amount to merge from the receiving item's full maximum stack size, so a merge could overfill that stack. Each fully merged roll also left an empty entry in the saved task. The recipe now merges compatible stacks up to their free space and saves only nonempty remainders. A native GameTest supplies two compatible tagged outputs totaling 67 items and a third output with a different tag. It checks the resulting counts and tags in all three stacks.

The packaged fixture feeds both SAG Mills with real hoppers and runs the shipped coal recipe. The first mill uses stock flint. The second uses a datapack-authored feather with deterministic output and a short lifetime. The first paid recipe in each mill activates its ball. Both mills save a partially paid coal recipe, including its exact rolled output and ball wear. The test reopens their world, checks both menus over TCP and supplies the exact remaining FE while the menus are open. The feather expires and the next task uses the original 2,400 FE cost.

The client uses the real powder from the stock mill to craft a Double-Layer Capacitor. The fixture supplies the two Basic Capacitors and two Energetic Alloy Ingots for that recipe.

## Commands

```sh
./gradlew :enderio:verifyGameplayTests --console=plain > build/cycle-156-sag-output-reproduced.log 2>&1
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-156-sag-final-build.log 2>&1

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_jar=enderio/build/libs/enderio-8.2.14.155-1.20.1+18a13a4fb5.jar
port_client=build/port-runtime/cycle-156-sag-client
port_previous=build/port-runtime/cycle-155-energy-client
mkdir -p "$port_client/config"
cp -cR "$port_previous/libraries" "$port_previous/versions" "$port_client/"
cp "$port_previous/config/fml.toml" "$port_client/config/fml.toml"
python3 scripts/porting/client/prepare_packaged.py --name cycle-156-sag-client \
  --java "$port_java" --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_sag_progression.py --name cycle-156-sag-final --port 26050 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --launch "$port_client/launch.json" \
  > build/cycle-156-sag-final.log 2>&1
```

The first build command ran with the new assertion on the original merge code. Its only gameplay failure was the new stack-limit regression. A packaged baseline probe with the cycle 155 production jar verified that the old coal roll contained four powdered coal and three empty entries. Its record is `build/port-runtime/cycle-156-sag-headless/sag-progression-results.json`.

## Results

- The final build passes 79 unit cases and 521 native runs, including 194 main gameplay cases. The new regression produces 64 and three items with the first tag, plus two items with the second tag. The packaged authored roll saves four powdered coal as one stack.
- Both mills keep their input, paid 600 FE, ball data, exact wear and original rolled outputs across a dedicated server restart. The stock flint job costs 2,040 FE and completes on the remaining 1,440 FE. The authored job costs 1,200 FE and completes on the remaining 600 FE. Its ball reaches the 2,200 FE wear limit without changing the saved four-item result. The next grind costs 2,400 FE at its original stock yield.
- Packaged Forge passes 14 server assertions and 16 client checks. The native SAG Mill menus synchronize the saved progress, ball modifiers and observed wear, then update as the jobs finish and the authored ball expires. The survival crafting grid makes one Double-Layer Capacitor from the mill's output. The saved player has the capacitor and one remaining powdered coal.
- Both servers and the guarded client exit normally with no runtime or registry errors. The observer samples 2,009 times without recording focus, activation or visible-window events. All five final captures show narrator off and every volume at zero. Both peers load SHA-256 `3851aa51d40ce955e475c17088687669199ff26a51c552ddf0a6a0f0678e462d`.

The final server report is `build/port-runtime/cycle-156-sag-final/sag-progression-results.json`. The client report and five captures are in `build/port-evidence/cycle-156-sag-final-client`.

## Next verification

Generate a disposable Forge 6.2.18-beta world with active grinding balls and partially paid SAG Mill jobs. Check the older ball IDs, wear, saved powder rolls and loose spares during conversion, restart and native placement.
