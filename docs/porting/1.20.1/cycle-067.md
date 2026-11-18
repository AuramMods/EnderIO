# Cycle 067 — restore and release saved Soul Vials

An official Ender IO 6.2.18-beta dispenser captures a real named cow in a disposable Forge world. It writes an `enderio:filled_soul_vial` with its entity and maximum health under `BlockEntityTag.EntityStorage`. The prior port jar remapped the item ID to the unified `enderio:soul_vial` but left the entity in the old field, so the current vial implementation treated it as empty.

Native item loading now converts a recognized vial's old entity and maximum health to the typed item keys. `Soul` performs its normal filtering, which removes the captured entity's original UUID. The migration leaves unrelated root and nested fields intact. The packaged conversion opens the copied 6.2 world, saves it, restarts, and uses the dispenser to release the original mob with a fresh UUID. The old empty-vial stack also maps to the unified item without gaining a soul.

The fresh packaged scenario also runs two guarded survival clients in sequence against the same dedicated world. The first captures a cow and a librarian from a stack of two vials using the ordinary entity interaction path. The second reconnects after the server restarts and releases both creatures using ordinary block-use packets. Server commands equip the initial empty stack and position the player in the disposable test area.

## Reproduction

Use the official Forge 47.4.28 server libraries in `cycle-047-legacy-discover` and the [guarded macOS client harness](client-harness.md). Use fresh profile names when repeating this sequence.

```sh
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_jar=enderio/build/libs/enderio-8.2.14.66-1.20.1+ef1012dcaf.jar
port_legacy=build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar
python3 scripts/porting/packaged_soul_vials.py --name cycle-067-legacy-fixed --port 25732 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --legacy-jar "$port_legacy"
mkdir build/port-runtime/cycle-067-client
cp -R build/port-runtime/cycle-066-client/libraries build/port-runtime/cycle-066-client/versions \
  build/port-runtime/cycle-067-client/
python3 scripts/porting/client/prepare_packaged.py --name cycle-067-client --java "$port_java" \
  --installer build/port-runtime/forge-47.4.28-installer.jar --jar "$port_jar" \
  --qa-jar enderio/build/test-libraries/enderio-client-qa.jar --assets "$HOME/.gradle/caches/neoformruntime/assets"
python3 scripts/porting/packaged_soul_vials.py --name cycle-067-live-verified --port 25734 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" \
  --launch build/port-runtime/cycle-067-client/launch.json
```

## Results

- The baseline confirmed the 6.2 dispenser payload and reproduced the missing typed soul in the converted item. Both servers exited zero. See `build/port-runtime/cycle-067-legacy-baseline/soul-vial-results.json` and `port/smoke-converted.log` in that profile.
- The fixed legacy profile passed eight assertions. It retained the named cow's 7.0 health, 10.0 maximum-health field, and both unrelated item tags. It released the cow and returned one empty vial after restart. All three servers exited zero. The conversion logged the same three Forge missing-mapping diagnostics as the previous legacy fixtures. No server logged a runtime ERROR or FATAL. See `build/port-runtime/cycle-067-legacy-fixed/soul-vial-results.json`.
- The first guarded run captured both creatures and verified the filled vials after reconnecting. Its release probe sampled inventory as soon as the cow's spawn arrived. The saved player data showed one empty vial and one occupied librarian vial. The final probe waits for both the spawn and inventory updates. See `build/port-runtime/cycle-067-live/soul-vial-results.json` and `build/port-evidence/cycle-067-live-release/result.json`.
- The final fresh profile passed 13 server assertions plus six guarded capture checks and five guarded release checks. The dispenser captured and released after restart. In survival, capturing the cow left one empty vial in hand, and capturing the librarian filled the last vial without opening trading. The occupied vials used the filled model and glint. After restarting, both entities kept their names and health, and the librarian kept its profession, XP and existing trade. Each release produced exactly one entity with a fresh UUID, and the inventory contained exactly two empty vials. Both servers and both clients exited zero with no ERROR or FATAL lines. See `build/port-runtime/cycle-067-live-verified/soul-vial-results.json`.
- The native watcher recorded 1,879 capture samples and 1,817 release samples with zero activation, foreground or visible-window events. Both captures had all ten sound categories muted, narration off and an uncaptured cursor. See `build/port-evidence/cycle-067-live-verified-capture/` and `build/port-evidence/cycle-067-live-verified-release/`.
- The full build passed 78 unit tests and 430 Forge GameTests with no failures, errors or skips. The final guarded probe was rebuilt after the inventory synchronization change. See `build/cycle-067-build.log`, `build/cycle-067-final-client.log` and the modules' `build/test-results/test/` XML.

| Artifact | SHA-256 |
| --- | --- |
| Baseline production jar | `2f4cdcb1a7491f2c82163c00eb66fb6bd176099487448f2194bd76f8700bb8b3` |
| Fixed production jar | `098f7a999eaca2c784e98b295fe9b84d28099a7bca7e60eb8a468990e42c95aa` |
| Official Ender IO 6.2.18-beta | `9d1471e6d4562d0a50f0564bc46573b87fd738bd440fe19397d31847da20f172` |
| Final guarded client probe | `26a0d9997302a2678815fbeda20b7cb6dff6c6bf6b4330f2eeb77f5559b8eca2` |

## Remaining verification

Packaged coverage still needs the configurable vanilla spawner replacement, capture restrictions across supported entity types, and the connected progression from Soul Vials through broken and powered spawners.
