# Cycle 074 — Forge 6 Aversion and its soul filter

## Scope

Restore the Aversion Obelisk and standalone soul filter from an official Forge 6.2.18-beta save. Exercise the placed block, the official block drop, survival replanting, the multiplayer editor and a server restart.

## Changes

- Restore the old range and visibility where the obelisk has no native range. Keep explicit zero distinct from a new, unconfigured machine when loading a capacitor and saving a world.
- Restore the carried range, charge and redstone mode from the original block drop. Preserve its block inventory until native placement loads it.
- Translate the five-slot legacy filter into the current ordered soul list, including empty slots, the allow or deny mode and tag comparison. The conversion also recognizes a filter whose ID was already remapped. The current soul format keeps relevant entity tags and drops its normal transient fields.
- Send the full soul tags to the filter editor via native menu payloads. The EnderCore channel now uses `forge-2`, so clients and servers need matching builds. The server and client now use the same nine-slot layout.
- Add regressions for native range loading, the effect of a migrated tag filter on Forge natural-spawn hooks, ordered legacy stack loading, preservation of unresolved entity IDs, and nested tags in the clientbound menu packet.

## Commands

```sh
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.73-1.20.1+df547a5703.jar
port_forge=build/port-runtime/cycle-047-legacy-discover
port_old=build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar
mkdir build/port-runtime/cycle-074-client-final
cp -R build/port-runtime/cycle-073-client-final/libraries build/port-runtime/cycle-073-client-final/versions \
  build/port-runtime/cycle-074-client-final/
python3 scripts/porting/client/prepare_packaged.py --name cycle-074-client-final --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_legacy_aversion.py --name cycle-074-final --port 25782 \
  --java "$port_java" --forge-server "$port_forge" --legacy-jar "$port_old" --jar "$port_jar" \
  --launch build/port-runtime/cycle-074-client-final/launch.json
```

## Results

- Cycle 073 reproduced the pre-fix range and filter loss in a native copy. On the first fixed build, the official 6.2 server wrote an Aversion with a four-block range and 5,842 FE, and generated a drop with zero range and 7,123 FE. The packaged port restored the placed block, standalone filter and survival replant. The editor still lost the first slot's name in transit while the server held it. This identified the existing type-only menu packet. See `build/port-runtime/cycle-074-converted/` and `build/port-evidence/cycle-074-converted-client/`.
- The final build passed 79 ordinary unit tests and 448 required Forge GameTests with zero failures or skips. The Forge profiles ran 171 core, seven Mekanism, 117 registry, 126 gameplay and 27 addon cases. The gameplay profile checked that the migrated filter's tag rule admits an exact named pig and blocks a plain pig and an unlisted cow via native Forge spawn finalization. See `build/cycle-074-network-build.log` and the module test XML.
- The final packaged run passed 10 server assertions and nine client checks over a real survival TCP connection. The editor received the first slot's name and the cow in the fifth slot. Clearing the fifth slot and disabling tag comparison updated the server stack while preserving the first slot and deny mode. Both obelisks retained their original ranges, exact charge, redstone mode and filters after restart. The three servers exited with code 0 and reported zero runtime errors. The initial conversion reported three Forge registry log entries for other historical mappings. The reopened port reported none. See `build/port-runtime/cycle-074-final/aversion-results.json`.
- The client and server used the same production jar, SHA-256 `8e708375488cacdb60284a7289e6bb8bec9b995925f8e21ebd7b6cd847c83bbc`. The official legacy jar was `9d1471e6d4562d0a50f0564bc46573b87fd738bd440fe19397d31847da20f172`. The production classes target Java 17 and exclude the client QA probe.
- The native observer recorded 1,714 samples, with zero window activations, foreground samples or on-screen samples. Both captures show fullscreen and cursor capture disabled, narrator off and all sound volumes at zero. See `build/port-evidence/cycle-074-final-client/focus.json`, `summary.json`, `migrated-aversion.png` and `aversion-filter.png`.

## Remaining verification

Verify Aversion and Relocator with engine-scheduled spawns on the packaged server. Legacy filters referring to an unavailable entity retain their original data and require that entity type to be present before migration can run. The fixture does not cover all retired Forge 6 registries or simultaneous use with other mods' spawn handlers.
