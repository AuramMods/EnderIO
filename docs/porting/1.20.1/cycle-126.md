# Cycle 126 — Craft filters and configure them in conduits

An installed filter editor now uses the same reach and block-entity checks as its parent conduit menu. It also stays bound to that endpoint's inventory. The editor closes when the endpoint becomes unavailable, even if the old handler still contains its original filter. Menu providers that use the original API entry point can continue doing so.

The packaged survival fixture lists the shipped filter recipes and provides the exact ingredients for each craft. It feeds crafted advanced filters into both upgrade recipes and carries the crafted redstone gates through all six conversions. After crafting, it uses the ordinary conduit slots and configure buttons to install and edit the three larger item filter types.

## Commands

```sh
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-126-qa-repro.log 2>&1
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain > build/cycle-126-build.log 2>&1
./gradlew :enderio:reobfClientSmokeJar --console=plain > build/cycle-126-qa-final.log 2>&1

run_filter_progression() {
  run_name=$1
  port_number=$2
  production_jar=$3
  port_profile="build/port-runtime/$run_name-client"
  port_source=build/port-runtime/cycle-121-fixed-client
  port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
  mkdir "$port_profile"
  cp -R "$port_source/libraries" "$port_source/versions" "$port_profile/"
  python3 scripts/porting/client/prepare_packaged.py --name "$run_name-client" --java "$port_java" \
    --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$production_jar" \
    --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
  python3 scripts/porting/packaged_filter_progression.py --name "$run_name" --port "$port_number" \
    --java "$port_java" --forge-server build/port-runtime/cycle-047-legacy-discover \
    --jar "$port_profile/mods/enderio.jar" --launch "$port_profile/launch.json"
}

run_filter_progression cycle-126-range-repro 25965 build/port-runtime/cycle-125-verified/server/mods/enderio.jar
run_filter_progression cycle-126-confirmed 25967 enderio/build/libs/enderio-8.2.14.125-1.20.1+8ea71c2681.jar
```

## Results

- The first packaged run on the previous jar crafts all 23 construction and conversion cases. It then finds the unlocked Basic Item Filter erasure recipe but the vanilla recipe book leaves the configured input in the inventory. Minecraft 1.20.1's recipe placement looks up an item of the chosen type with the same tags as its default stack. The fixture places configured inputs directly into the real crafting grid to exercise erasure. See `build/port-evidence/cycle-126-repro/result.json`.
- The full build and QA packaging pass with 79 unit tests and 489 native Forge test executions. All 167 discovered gameplay cases execute.
- Both runtime scenarios craft all 34 filter recipes present in the packaged jar. The two advanced upgrade paths need two distinct crafted Advanced Item Filters, so each scenario performs 35 crafts. Each craft yields exactly one clean output and consumes the supplied inputs.
- The previous jar installs and edits all three crafted stacks through their conduit screens. Its Limited Item Filter editor stays open after the player is teleported ten blocks farther from the conduit. See the failing range assertion in `build/port-evidence/cycle-126-range-repro/result.json`.
- The fixed run passes 88 client checks and 59 server assertions. The three crafted stacks move into their actual conduit slots and are configured using the normal screens. The out-of-range limited editor closes. The server confirms that the installed stack still holds its 37-item target, and the client returns and reads the same setting before using Back.
- Both before and after a dedicated restart, the installed Big Item Filter moves all three iron ingots and keeps the five paper in the source. The Big Advanced Item Filter moves the matching pickaxe at damage 45 and keeps the one at damage 80. The Limited Item Filter moves exactly 37 matching cobblestone, leaving 27 matching and five differently tagged pieces at the source.
- Both final server sessions exit at zero with no runtime or registry errors. The final guarded capture reports no activation, foreground or onscreen events in 2,377 samples, with the narrator off and all ten volumes at zero. The client and server use SHA-256 `ef5b09420c2ff40c7d9ff083499547c6a8e3f2f37236ba13be2009b170f643b6`. See `build/port-runtime/cycle-126-confirmed/filter-progression-results.json` and `build/port-evidence/cycle-126-confirmed`.
- An earlier run of the same fixed jar ended during login without entering gameplay. The fresh confirmed run joined successfully and exercised the full scenario.

## Remaining verification

Configured inputs for the shipped erasure recipes are placed by hand in the crafting grid. The next cycle verifies installed editor closure on whole-block destruction and the recovered filter's routing through chunk eviction and restart. [Cycle 127](cycle-127.md)
