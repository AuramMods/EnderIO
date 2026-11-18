# Cycle 048 — load historical Forge conduit bundles and graphs

An actual Ender IO 6.2.18-beta save previously failed during chunk initialization because the port sent Forge 6.x `ConduitBundle` NBT through a later codec. This cycle reads the 6.x ordered type IDs, directional connection states, backup data and the dimension's indexed graph. The later bundle and graph formats continue through their existing decoders.

- The converter maps the six core historical conduit IDs to the corresponding datapack entries. It translates 6.x color ordinals to dye colors and preserves dynamic endpoint options and disabled sides. It reads item priority, self-feed, round-robin settings and the stored cursor. Basic item and fluid filter records import their entries and matching flags. A six-face facade using one default block state becomes a painted facade item.
- Graph import uses the historical packed block positions and indexed edges to initialize current networks and their chunk-coverage index. Multiple disconnected graphs can share a conduit type. The normal current save path writes converted data.
- The separate QA fixture jar runs only with the historical release. Its command uses the old block item and a Forge fake player to place the conduits, then configures them through the old block entity. Minecraft writes the chunk NBT and saved graph on the authentic 6.2 runtime. The runner copies the closed world and starts the converted profile with the bundled port jar alone.

## Commands and results

```sh
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain
python3 scripts/porting/legacy_fixture/build.py \
  --java /Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java \
  --forge-server build/port-runtime/cycle-047-legacy-discover \
  --legacy-jar build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar \
  --art /Users/cyberpwn/.gradle/caches/modules-2/files-2.1/net.neoforged/AutoRenamingTool/2.0.17/9388df4c3589faf10c35d9154cfb2451c83bd717/AutoRenamingTool-2.0.17-all.jar \
  --output build/port-runtime/cycle-048-official-fixture
python3 scripts/porting/packaged_legacy_migration.py \
  --name cycle-048-packaged --port 25648 \
  --java /Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java \
  --forge-server build/port-runtime/cycle-047-legacy-discover \
  --legacy-jar build/port-runtime/legacy-artifacts/EnderIO-1.20.1-6.2.18-beta-all.jar \
  --jar enderio/build/libs/enderio-8.2.14.47-1.20.1+859e1b4837.jar \
  --conduit-fixture build/port-runtime/cycle-048-official-fixture/fixture.jar
```

The build passed 78 ordinary unit tests and 411 native GameTests. The packaged fixture passed **48 assertions** across the source server, the converted server and its restart. Eighteen recheck cycle 047's machine and registry results. The historical conduits first move seven diamonds and reject five emeralds. After conversion, the test checks both endpoints' exact settings, the cross-chunk links, the disabled north side, the disconnected item bundle, and the painted facade's saved block ID. It forces the two conduit chunks to unload and then load again. The migrated filter and route deliver three more diamonds, reaching ten. Restart verifies the same configuration and three more deliveries, reaching 13. All five emeralds remain upstream. Both converted runs complete without conduit recovery or mismatch messages. Forge reports four missing-registry error headers during initial conversion for unresolved old content, and the three servers exit cleanly with no other errors.

The fixture runs use Forge 47.4.28 and Temurin 17.0.18. The bundled port jar's SHA-256 is `a0775ed5d7606a864ec850cdb5426a9d7d22b72e9d16985ba6df94c7022d0fd7`. The fixture jar's SHA-256 is `4251d59c0b35f46dffcae792c986c39599515b20dccca5fa91a2484aea706f02`. Worlds, logs and per-assertion results are in `build/port-runtime/cycle-048-packaged`. The complete build log is `build/port-evidence/cycle-048-build.log`. All launches were headless and all owned servers stopped.

## Remaining historical migration

The original crash fixture now starts and passes the empty-bundle conversion assertion. That same fixture exposes a separate travel saved-data error. `enderio_traveldata.dat` has legacy `name` and `data` fields, and the current codec expects `type`. The result and stack trace are in `build/port-evidence/cycle-048-empty.log` and `build/port-runtime/cycle-048-empty/smoke-1.log`. Fix travel target conversion and verify named targets and actual travel after reopening a copied old world.

The old item conduit stacks and extraction speed upgrades still need a migration strategy. The running item scenario deliberately includes an old installed speed upgrade, which is absent from the converted endpoint. Mixed, partial and non-default facade faces, other historical filter variants, old fluid locks, nonzero energy buffers and optional conduit types remain open. Item delivery was verified with the mixed bundle in place. Legacy energy and fluid transfer, redstone signals and client rendering were outside this fixture. General 6.x upgrades and full port parity remain open.
