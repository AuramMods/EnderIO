# Cycle 165 — Reconcile the packaged Forge recipes and registries

A separate Forge probe snapshots the live dedicated server after world creation. It reads recipe IDs and serializers, registry IDs, enabled blocks and items, tags, loaded advancements, loot tables, feature flags and selected packs. The runner compares these with the immutable baseline inventory, the baseline JSON at `4c482da69e76f2b46bfeeac64ef0ac277c318eaa` and the distributable jar, including its nested addons. Recipe conditions are evaluated against the observed mods and bound item tags. The probe remains a separate test jar.

Three headless profiles cover default Forge, Forge with AE2 15.4.12, GuideME 20.1.15, Refined Storage 1.12.4, Mekanism 10.4.16.80, LaserIO 1.6.8 and Patchouli 1.20.1-84.1 with the three Ender IO feature packs enabled, and the same integrated profile with aluminum tags supplied by a disposable datapack. The fixture contributes four deliberately distinct vanilla items to Forge tags. Ender IO's shipped common tags then expose those items to its aluminum recipes.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_jar=enderio/build/libs/enderio-8.2.14.163-1.20.1+bc7b6923a3.jar
port_art="$HOME/.gradle/caches/modules-2/files-2.1/net.neoforged/AutoRenamingTool/2.0.17/9388df4c3589faf10c35d9154cfb2451c83bd717/AutoRenamingTool-2.0.17-all.jar"
port_probe=build/port-runtime/cycle-165-audit-probe/registry-probe.jar
port_cache="$HOME/.gradle/caches/modules-2/files-2.1"
port_mods=(
  --mod "$port_cache/maven.modrinth/ae2/QRpVsqDI/4e81984691e35c729880a11f72deb52dcc74d41f/ae2-QRpVsqDI.jar"
  --mod "$port_cache/maven.modrinth/guideme/i7Tp1AHw/3d432fc99cefcfd17a12635d2d220f09a069f1c7/guideme-i7Tp1AHw.jar"
  --mod "$port_cache/maven.modrinth/refined-storage/ZITLFjjf/14bf4de4975f6310d22a6ed06c6a7e1e0805641/refined-storage-ZITLFjjf.jar"
  --mod build/port-runtime/cycle-142-controls-final/mods/mekanism-uxe1WQp4.jar
  --mod "$port_cache/curse.maven/laserio-626839/5074767/f9e8dc07506eaa058f64e875e1f46dcd44ab6003/laserio-626839-5074767.jar"
  --mod "$port_cache/maven.modrinth/patchouli/8Qyw08ld/e91d2e2f251375d647d592b670ea6f310c2554eb/patchouli-8Qyw08ld.jar"
)
python3 scripts/porting/legacy_fixture/build.py --java "$port_java" --forge-server "$port_forge" \
  --legacy-jar "$port_jar" --art "$port_art" --registry-probe \
  --output build/port-runtime/cycle-165-audit-probe > build/cycle-165-audit-probe.log 2>&1
python3 scripts/porting/packaged_registry_audit.py --name cycle-165-verified-core --port 26088 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --probe "$port_probe" \
  > build/cycle-165-verified-core.log 2>&1
python3 scripts/porting/packaged_registry_audit.py --name cycle-165-verified-integrated --port 26089 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --probe "$port_probe" \
  --experiments "${port_mods[@]}" > build/cycle-165-verified-integrated.log 2>&1
python3 scripts/porting/packaged_registry_audit.py --name cycle-165-verified-aluminum --port 26090 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --probe "$port_probe" \
  --experiments --aluminum-fixture "${port_mods[@]}" > build/cycle-165-verified-aluminum.log 2>&1
```

## Results

| Live data | Default | Integrated | Integrated with aluminum tags |
| --- | ---: | ---: | ---: |
| Expected baseline recipes loaded | 1,222 | 1,245 | 1,248 |
| Total Ender IO recipes loaded | 1,343 | 1,393 | 1,396 |
| Live furnace recipes inherited by the Alloy Smelter | 74 | 101 | 101 |
| Expected baseline advancements loaded | 984 | 995 | 995 |
| Baseline conduit variants loaded | 10 | 17 | 17 |

- Every active baseline recipe uses its original serializer ID. The 26 inactive baseline recipes on default Forge consist of 11 integration recipes and 15 optional-material SAG Mill recipes. The integrated profile loads 23 of those 26. All 47 historical Primitive Alloy Smelter recipes are also present in every profile. The separate baseline `darksteel_upgrade` orphan remains excluded as recorded in [cycle 033](cycle-033.md).
- All three aluminum jobs run through the shipped SAG Mill with hopper input and output. The saved task names the expected ingot, ore or raw recipe at 600 FE paid. Each then spends exactly 1,800 additional FE and exports the recorded main product and quantity. The profile passes eight runtime assertions.
- All 825 baseline block IDs, 813 baseline loot tables and 173 baseline tags are present in each profile. The archive contains all 825 baseline blockstates and all 944 baseline item model files. The runtime item checks account for five model-only IDs and the integration-only chemical filter. Four models are item overrides, and `photovoltaic_cell` is the parent model of the three solar panel items. All 938 expected baseline item IDs register by default, and all 939 register with Mekanism.
- The default world registers the Enderface and Niard blocks and items, with their feature flags disabled. The integrated worlds enable all three experimental packs and both gated blocks and items. The farming station stays enabled in all profiles. Eight extra block IDs and 29 extra item IDs include historical lamps, the Primitive Alloy Smelter, extraction upgrades and retained materials.
- All three final server processes exit normally with zero runtime or registry errors. Each runs the cycle 164 production jar with SHA-256 `0434ac954dc32d22b8ae47b6935c0454069f7bd92217211fb2859df609e63bd0`. Initial snapshots under `build/port-runtime/cycle-165-{core,integrated}/` exposed model templates that the first comparison had treated as registry candidates. The final runs account for them and check that their resources still ship.

The final `registry-snapshot.json` and `registry-audit-results.json` reports are in `build/port-runtime/cycle-165-verified-{core,integrated,aluminum}/`.

## Next verification

Compare live tag membership and loaded progression data with the baseline. Check feature behavior where IDs alone leave gaps, including the remaining client presentation and packaged progression paths.
