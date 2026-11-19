# Cycle 166 — Reconcile live tag contents and restore VAT and wrench compatibility

The packaged registry probe now records resolved tag members and the underlying registry IDs. The comparison expands the 173 shipped baseline tags against the pinned Minecraft 1.21.1 and NeoForge 21.1.216 tag files. It compares each Ender IO contribution with live Forge membership and records additions, missing members, references and entries unavailable on Forge 1.20.1. The pinned references were downloaded into the evidence directory and checked against their published SHA-1 values. [Mojang server jar](https://piston-data.mojang.com/v1/objects/59353fb40c36d304f2035d51e7d6e6baa98dc05c/server.jar), [NeoForge universal jar](https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.216/neoforge-21.1.216-universal.jar).

The live comparison found that Forge's stock crop and seed tags omit cactus, cocoa beans, melon blocks, pumpkin, sugar cane and torchflower seeds. The new common tags preserve those baseline members and accept contributions from the native Forge crop and seed tags. Ender IO's recipe tags use the common tags. The Forge singular and plural wrench tags now include the Yeta Wrench, and Ender IO accepts their members through its existing wrench tag.

The test supplies each restored reagent to a stock VAT through vanilla hoppers. It also uses the native Forge item interaction on the world-east face of fresh Alloy Smelters. The test examines the machine's relative I/O tag and the other five sides. The first fixture expected the world direction as the saved key. The corrected fixture checks the relative west key and verifies the Yeta Wrench and a stick as controls.

## Commands

```sh
port_java="$HOME/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/java"
port_forge=build/port-runtime/cycle-047-legacy-discover
port_owner_jar=enderio/build/libs/enderio-8.2.14.163-1.20.1+bc7b6923a3.jar
port_jar=enderio/build/libs/enderio-8.2.14.165-1.20.1+29f2bfb9a2.jar
port_art="$HOME/.gradle/caches/modules-2/files-2.1/net.neoforged/AutoRenamingTool/2.0.17/9388df4c3589faf10c35d9154cfb2451c83bd717/AutoRenamingTool-2.0.17-all.jar"
port_refs=build/port-evidence/cycle-166-baseline
port_probe=build/port-runtime/cycle-166-gameplay-probe/registry-probe.jar
```

The integrated runs use the same `port_mods` array as [cycle 165](cycle-165.md). The reference download inputs are the server object above and the corresponding `21.1.216` NeoForge Maven checksum. Mojang version metadata and both reference jars are preserved under `port_refs`.

```sh
python3 scripts/porting/legacy_fixture/build.py --java "$port_java" --forge-server "$port_forge" \
  --legacy-jar "$port_owner_jar" --art "$port_art" --registry-probe \
  --output build/port-runtime/cycle-166-gameplay-probe > build/cycle-166-gameplay-probe.log 2>&1
python3 scripts/porting/packaged_registry_audit.py --name cycle-166-repro --port 26094 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_owner_jar" --probe "$port_probe" \
  --experiments --tag-progression "${port_mods[@]}" > build/cycle-166-repro.log 2>&1

./gradlew :enderio:runData --console=plain > build/cycle-166-tag-datagen.log 2>&1
./gradlew build --continue --console=plain > build/cycle-166-tag-build.log 2>&1
python3 scripts/porting/packaged_registry_audit.py --name cycle-166-fixed-core --port 26095 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --probe "$port_probe" \
  --tag-progression > build/cycle-166-fixed-core.log 2>&1
python3 scripts/porting/packaged_registry_audit.py --name cycle-166-fixed-integrated --port 26096 \
  --java "$port_java" --forge-server "$port_forge" --jar "$port_jar" --probe "$port_probe" \
  --tag-progression --experiments --aluminum-fixture "${port_mods[@]}" > build/cycle-166-fixed-integrated.log 2>&1
for port_name in cycle-166-fixed-core cycle-166-fixed-integrated; do
  port_run="build/port-runtime/$port_name"
  python3 scripts/porting/compare_tag_members.py --server-jar "$port_refs/minecraft-1.21.1-server.jar" \
    --neoforge-jar "$port_refs/neoforge-21.1.216-universal.jar" --snapshot "$port_run/registry-snapshot.json" \
    --output "$port_run/tag-audit-results.json" >> "build/$port_name.log" 2>&1
done
```

## Results

- The original jar shows six missing tag members and five unimported third-party tools. The corrected packaged reproducer records all 11 corresponding gameplay failures. The stock VATs still hold their water without a running job, and the installed third-party tools leave the Alloy Smelter's I/O empty. The Yeta Wrench cycles the correct side and the stick leaves all sides alone.
- The comparator accounts for the 25 newer vanilla blocks in the redstone-connectable tag and the Wind Charge in the reagent tag using the actual Forge registry. The shipped Firework Rocket substitute is already exercised in [cycle 116](cycle-116.md). The source datagen has an empty soul-vial whitelist and includes the Warden and the two common restriction tags in the blacklist. Two stale generated zombie entries added the same mob to both lists in the baseline, so the comparator follows the source datagen and capture outcome established in [cycle 027](cycle-027.md).
- Both final profiles match 3,027 baseline memberships across 173 tags, with zero missing available members or unresolved nested references. Each profile exposes 10 crops and five seeds. The core-only fixture passes all 10 runtime assertions and the integrated fixture passes all 22. Each VAT exports 250 mB of Hootch except the Sugar Cane batch, which exports exactly 125 mB at its shipped 0.5 multiplier. The input hoppers, water supplies and both VAT tanks empty.
- All five third-party tools cycle the clicked machine face exactly once and leave the other five sides at their defaults. The integrated run also repeats the three aluminum SAG Mill jobs and accounts for all 1,248 baseline recipes. Both server processes run headless and exit normally with zero runtime or registry errors.
- Native datagen succeeds, and the full build passes 79 unit cases and 529 native runs, including 202 main gameplay cases. The fixed packaged jar has SHA-256 `6d5ab680ef06f384ad2b41f6dc163c55d2e9e17156f7c077fd81445ed33338fd`. The reports and saved fixtures are in `build/port-runtime/cycle-166-{repro,fixed-core,fixed-integrated}/`. The preliminary snapshots and membership comparison are under `build/port-runtime/cycle-166-{core,integrated}/`.

## Next verification

Reconcile custom data-map and soul data entries. Exercise their consumers and the remaining client model, tooltip and recipe display paths.
