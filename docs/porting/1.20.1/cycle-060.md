# Cycle 060 — Curios charging and keybindings

## Scope

Verify the three Ender IO call sites that use equipped Curios in the 1.21.1 baseline and this port. The packaged fixture defines and tags a regular staff slot and a regular magnet slot in a world datapack, then equips the genuine items with Curios' native `replace` command. The test uses Curios 5.14.1+1.20.1 (Modrinth version `IPQlZkz1`). Production code and slot defaults are unchanged.

The standalone client probe uses local Minecraft key mappings to toggle the magnet and tap the travel key with empty hands. The dedicated server reads the live inventory and Curios capability after each scenario, and a new connection after a full server restart verifies the saved charge and toggle on both sides. A second clean client/server profile exercises the same controls with only ordinary inventory and the bundled mod.

## Reproduction

Use Java 17, an official Forge 47.4.28 test server installation and the [macOS hidden-client harness](client-harness.md). The client prep commands can use `--install` with the official installer for each fresh directory, or copy `libraries` and `versions` from an existing disposable Forge client as shown here. Use fresh run names if repeating a scenario.

```sh
./gradlew :enderio:reobfClientSmokeJar --console=plain
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_jar=enderio/build/libs/enderio-8.2.14.58-1.20.1+e4f25395b1.jar
port_forge=build/port-runtime/cycle-047-legacy-discover
port_curios="$HOME/.gradle/caches/modules-2/files-2.1/maven.modrinth/curios/IPQlZkz1/452175b95ad3db6ff58bb8968f6bf7a9d1e0f480/curios-IPQlZkz1.jar"

for profile in present absent; do
  directory="build/port-runtime/cycle-060-client-$profile"
  mkdir "$directory"
  cp -R build/port-runtime/cycle-051-client-verified/libraries \
    build/port-runtime/cycle-051-client-verified/versions "$directory/"
  set --
  if [ "$profile" = present ]; then
    set -- --mod "$port_curios"
  fi
  python3 scripts/porting/client/prepare_packaged.py --name "cycle-060-client-$profile" \
    --java "$port_java" --installer build/port-runtime/forge-47.4.28-installer.jar \
    --jar "$port_jar" --qa-jar enderio/build/test-libraries/enderio-client-qa.jar \
    --assets "$HOME/.gradle/caches/neoformruntime/assets" "$@"
done

python3 scripts/porting/packaged_curios.py --name cycle-060-curios --port 25697 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --curios-jar "$port_curios" \
  --launch build/port-runtime/cycle-060-client-present/launch.json
python3 scripts/porting/packaged_curios.py --name cycle-060-no-curios --port 25698 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" \
  --launch build/port-runtime/cycle-060-client-absent/launch.json
./gradlew build --continue :enderio:reobfClientSmokeJar --console=plain
```

## Results

- The Curios server passed 21 assertions across its initial run and restart. Over 12 ticks the charger supplied 20 FE to the ordinary staff and 700 FE to the equipped staff, with 24 FE of upkeep. The buffer moved from 32,000 to 31,256 FE. The travel key blinked from the test platform to the expected destination and spent exactly 1,000 FE from the equipped staff. The competing staff kept its 9,000 FE. The magnet key toggled the equipped magnet while the inventory magnet stayed off. The server and reconnecting client read the persisted 5,000 FE and active magnet after restart. See `build/port-runtime/cycle-060-curios/curios-results.json`.
- The server without Curios passed 10 assertions. The ordinary staff received its final 20 FE, the buffer retained 31,956 FE after 12 ticks, and the keybindings toggled the magnet and spent exactly 1,000 FE from the inventory staff. Both profiles verified identical Ender IO jars on the client and server. The Curios profile also checked matching Curios jars. All three server processes exited cleanly with no ERROR or FATAL lines. See `build/port-runtime/cycle-060-no-curios/curios-results.json`.
- The initial Curios client passed six checks, the restart client three, and the client without Curios six. All captured 960×540 world frames. Narrator was OFF and all ten sound channels were muted. Across 4,916 native observations, there were zero activation events, foreground samples or on-screen window samples. All three clients exited zero with no ERROR or FATAL lines. Evidence directories are `build/port-evidence/cycle-060-curios-initial/`, `build/port-evidence/cycle-060-curios-restart/` and `build/port-evidence/cycle-060-no-curios-initial/`.
- The full build passed 78 ordinary JUnit tests (61 EnderCore, 16 Ender IO, one addon) and 427 native tests (171 EnderCore, seven Mekanism, 115 registry, 107 Ender IO gameplay and 27 addon). XML reports recorded zero failures, errors or skips. See `build/port-cycle-060-build.log` and each module's `build/test-results/`.

| Artifact | SHA-256 |
| --- | --- |
| Packaged TCP mod `enderio-8.2.14.58-1.20.1+e4f25395b1.jar` from cycle 059 | `2a69763857c4950849f4330a07454063e82f21a489e41f8c40eb2a70e610af52` |
| Curios `curios-IPQlZkz1.jar` | `1e817919a35b37cf30524aaec73f0ca5130452f23f168f844854df282eb8e51f` |
| Test-only `enderio-client-qa.jar` | `bc6df83df5bd3d1a09f2544a43d4de1e5af81ed5af30b9fbef23a69a3eb30377` |
| Full-build mod `enderio-8.2.14.59-1.20.1+662cffdd8c.jar` | `bfecbea8dadecc1396a805a2b35708c784357bf1a739346c21407b39d93e66be` |

## Remaining verification

The tests cover the Ender IO integration with configured regular Curios slots. Electromagnet attraction, pickup and active energy draw still need gameplay coverage. Historical solar and wired charger migration and the wired charger client menu remain next in the charging and generation work. Broader single-player coverage and the remaining integrations are open.
