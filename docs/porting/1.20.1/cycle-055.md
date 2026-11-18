# Cycle 055 — restore historical item extraction upgrades

Ender IO 6.2.18-beta installed one extraction upgrade on an item conduit face. Its four tiers delivered up to 8, 16, 32 and 64 items per 20 ticks. The previous port discarded installed upgrades and their loose item stacks because those item IDs had no registry entries. The old packaged server reproduced all four per-update amounts. The previous port then failed the first installed-upgrade assertion after copying the closed world. See `build/port-runtime/cycle-055-baseline-verified/`.

The port restores all four registry IDs and reuses the original textures already in the repository. Datagen supplies item models, names and a slot tooltip. The third item-conduit slot accepts one upgrade and preserves the native stack tag. The Forge 6.x decoder imports the original `ExtractUpgrade` stack. Earlier port inventories with two slots load with an empty third slot. The player can transfer upgrades between inventory and the conduit menu. Breaking the conduit returns its upgrade with the other connection inventory.

An installed upgrade sets the extraction budget for that face. Standard basic conduit segments carry the historical tier-four batch of 64 with an upgrade installed. Other configured route limits still apply. Faces with an empty upgrade slot use the current conduit tier's ordinary rate. The four old items are retained for save compatibility and have no new crafting recipes.

The legacy test places four routes across a chunk boundary with one upgrade per source and stores four loose, tagged stacks in a barrel. A separate Forge probe samples the first delivery of each batch at the end of the server tick. It checks exact source and destination counts before clearing the probe's remaining input for the next phase. Loose stacks and installed stacks are checked on the old release, on conversion, after eviction and reload, and after a dedicated server restart.

## Reproduction

```sh
./gradlew :enderio:runData --console=plain
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain

port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_legacy=build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar
port_jar=enderio/build/libs/enderio-8.2.14.54-1.20.1+ed4f143e52.jar
port_art="$HOME/.gradle/caches/modules-2/files-2.1/net.neoforged/AutoRenamingTool/2.0.17/9388df4c3589faf10c35d9154cfb2451c83bd717/AutoRenamingTool-2.0.17-all.jar"

python3 scripts/porting/legacy_fixture/build.py \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" --art "$port_art" \
  --output build/port-runtime/cycle-055-fixture
python3 scripts/porting/legacy_fixture/build.py \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_legacy" --art "$port_art" \
  --item-upgrade-probe --output build/port-runtime/cycle-055-probe
python3 scripts/porting/packaged_legacy_migration.py \
  --name cycle-055-upgrades --port 25687 --java "$port_java" \
  --forge-server "$port_forge" --legacy-jar "$port_legacy" --jar "$port_jar" \
  --conduit-fixture build/port-runtime/cycle-055-fixture/fixture.jar --extraction-upgrades --loose-conduits \
  --mod build/port-runtime/cycle-055-probe/item-upgrade-probe.jar
```

Use a fresh run name and loopback port. Set `port_jar` to the distributable produced from the checkout under test. The combined profile uses the same command with `--name cycle-055-combined --port 25688`. Replace `--loose-conduits` with `--loose-integration-conduits` and add the placed storage and Mekanism options and pinned mod jars from [cycle 054](cycle-054.md).

The client uses the existing guarded launcher. Copy only the official libraries and version files from an already installed disposable profile before preparation.

```sh
mkdir build/port-runtime/cycle-055-client
cp -R build/port-runtime/cycle-051-client-verified/libraries \
  build/port-runtime/cycle-051-client-verified/versions build/port-runtime/cycle-055-client/
python3 scripts/porting/client/prepare_packaged.py \
  --name cycle-055-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/client/run_smoke.py --launch build/port-runtime/cycle-055-client/launch.json \
  --scenario extraction-upgrades --timeout 260 --output build/port-evidence/cycle-055-hidden-menu-final
```

## Results

- Datagen and the full build passed. There were 78 ordinary JUnit tests and 419 native GameTests with no failures, errors or skips. The 99 gameplay tests include a native upgrade picker that checks the slot rejects unsuitable items, takes one upgrade from a stack of three and returns the complete tagged item. The logs are `build/port-evidence/cycle-055-{datagen,build}.log`.
- The packaged core profile passed 114 assertions. The combined profile passed 238 with AE2, Refined Storage and Mekanism installed. Both preserve the old native 8, 16, 32 and 64 item transfers during conversion, chunk eviction and restart. At each observation, 128 equals the items delivered plus the items remaining. Loose stacks retain counts from two through five and their tags. Breaking the tested tier-four source conduit yields one tagged upgrade. All six servers exit cleanly. Four first-load Forge diagnostics per profile record the remaining unresolved legacy registry IDs. The reports contain zero other runtime errors. See `build/port-runtime/cycle-055-{upgrades,combined}/legacy-migration-results.json`.
- The packaged client passed nine checks. The Forge screen-open packet builds the upgrade slot, and actual inventory click packets place and return the tagged stack. Server-side observations independently confirm the slot and cursor state. All four item models bake successfully. The 960×540 capture shows the upgrade beside the existing filter slot. The native observer records 773 samples, zero activation events, zero foreground or visible-window samples, and exit code zero. Narrator is OFF, all ten sound channels are at zero and the final log contains no ERROR/FATAL entries. The first attempt passed the in-menu interaction but encountered reentrant rendering when the QA scenario closed the integrated server. The scenario now stops processing render events before closing the level.
- The final client evidence is in `build/port-evidence/cycle-055-hidden-menu-final/`. The distributable SHA-256 is `dc998e7e1bdfe6566aee04d6598d1baa254f78b498b6c01df78f29242c97d61c`. The final client QA jar SHA-256 is `de62428a16e83251943d35d800a5da15438f5c6a94f58480f4203c5f834db212`. The distributable contains all four upgrade models and textures. The server probe and client QA classes are tested from separate jars.

Dedicated multiplayer testing of the item conduit menu and broader client parity remain open. The remaining known historical gaps include old light blocks, the primitive alloy smelter and other retired IDs that Forge still reports during conversion.
