# Cycle 071 — Soul Engine fuels, erase crafting and JEI

## Scope

Exercise all six built-in fuel rules on a dedicated Forge server, preserve the source recipe's factory reset, and verify JEI's Soul Engine category in a guarded multiplayer client.

## Changes

- Add a packaged server probe that feeds the four shipped fluids through ordinary Forge tank output and checks the generated FE, remaining fluid and paid ticks before and after restart. An Enderman engine first rejects rocket fuel and then accepts dew of the void.
- Use the vanilla crafting table over TCP to erase a loot-generated engine with a Blaze, 1,543 FE, a basic capacitor, 5 mB of lava and custom redstone and I/O settings. Both 1.21.1 and the port define this as a one-input shapeless recipe for a fresh, untagged engine. The fixture checks that the ordinary result consumes the input and places a factory-state block.
- Resolve JEI Soul Engine lookups from the client's current soul rules and fluid tags. Forge sends the recipe list before the tag packet at login. The old JEI registration could capture an empty fuel ingredient for a tagged rule, even though the GUI resolved it correctly when opened later.
- Share fuel matching with the machine and preserve the catalyst, spawn egg and filled vial lookups. The guarded probe checks the actual rendered GUI and searches by both tag-based and named fuels. The server reload replaces lava with a new tag containing water.
- Display one source entry when a tag includes both source and flowing forms of the same liquid. JEI's cycling tank could draw an empty gauge with both lava forms. Tags that name only the flowing form retain that entry.

## Commands

```sh
./gradlew :enderio:assemble :enderio:reobfClientSmokeJar --console=plain
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_jar=enderio/build/libs/enderio-8.2.14.70-1.20.1+730e07956d.jar
port_jei=$(find "$HOME/.gradle/caches/modules-2/files-2.1/maven.modrinth/jei/9jqubC9n" -name 'jei-9jqubC9n.jar' -print -quit)
python3 scripts/porting/packaged_soul_engine_fuels.py --name cycle-071-core-render --port 25765 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar"
mkdir build/port-runtime/cycle-071-client-render
cp -R build/port-runtime/cycle-071-client-lookup/libraries build/port-runtime/cycle-071-client-lookup/versions \
  build/port-runtime/cycle-071-client-render/
python3 scripts/porting/client/prepare_packaged.py --name cycle-071-client-render --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets" \
  --mod "$port_jei"
python3 scripts/porting/packaged_soul_engine_fuels.py --name cycle-071-jei-render --port 25764 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" \
  --launch build/port-runtime/cycle-071-client-render/launch.json
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain
```

## Results

- The full build passed 78 ordinary tests and 433 required Forge GameTests with zero failures, errors or skips. The Forge suites ran 171 core, seven Mekanism, 115 registry, 113 gameplay and 27 addon tests. See `build/cycle-071-lookup-build.log`, `build/cycle-071-build-final.log` and the three modules' `build/test-results/test/` XML.
- The Soul Engine class uses Java 17 bytecode. The production jar bundles EnderCore, modded conduits and GraphLib. The crafting and JEI probes reside in the separate reobfuscated client QA jar.
- The packaged core profile passed 14 assertions across two clean server exits. Each of the six engines consumed exactly 2 mB. Blaze and Creeper generated 1,600 FE each, Enderman generated 2,400 FE, and Husk, Zombie and Zombie Villager generated 2,000 FE each. The Enderman kept its tank empty while the adjacent tank retained both millibuckets of rocket fuel. See `build/port-runtime/cycle-071-core-render/soul-engine-fuels-results.json`.
- The JEI multiplayer profile passed 18 server assertions. The factory reset survived restart, and the Blaze engine generated another 1,800 FE from two millibuckets of water under the new tag. All 3,400 FE survived restart. See `build/port-runtime/cycle-071-jei-render/soul-engine-fuels-results.json`.
- The JEI client passed 29 checks. The two actual JEI frames display the tagged lava and water and show the rate changing from 18 ticks and 800 FE per mB to four ticks and 900 FE per mB. The native watcher recorded 2,071 samples with zero activation, foreground or visible-window events. All ten sound levels stayed at zero, with narrator and fullscreen off and the cursor uncaptured. See `build/port-evidence/cycle-071-jei-render-client/`.
- The first headless fixture omitted the native soul payload's required `entity_tag`. The first expanded loot check also used the block entity's inventory wrapper in place of the carried item's container structure. The corrected probes use the native formats. See `build/cycle-071-baseline-fuels.log`, `build/cycle-071-core.log` and `build/cycle-071-jei-final.log`.
- The previous packaged viewer's layout included the lava ingredient, while the focused lava lookup failed after login. The server had already placed the reset block. The probe now waits for the server's placement confirmation before checking JEI. See `build/port-evidence/cycle-071-jei-verified-client/summary.json`.
- The first fixed lookup passed but its initial lava frame still showed the empty gauge. See `build/port-evidence/cycle-071-jei-lookup-client/jei-default.png`.

| Artifact | SHA-256 |
| --- | --- |
| Bundled Ender IO production jar | `0a485169f9fea41c7861ac97a8cde68ca4356c27945ecdc8bb2a73a4cd620e18` |
| Reobfuscated client QA jar | `39ea94b75f988d1d30f3004ab4274db1386706f73189fa21063fd144b260fc01` |
| JEI 15.56.0.205 for Forge | `6b251e60a4719c874da89d750af97aa6498f6f4475e3ae79cb1288c7bf0cb65c` |

## Remaining verification

Exercise the XP and Weather Obelisks and the four entity obelisks, including real Forge spawn and teleport events, menus and saved state. JEI coverage for the other categories, recipe transfers and overlays remains open.
