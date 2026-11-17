# Cycle 026 — native loot and advancement criteria

## Objective and implementation

Port implemented spawner/chest loot modifications, capacitor and paint loot functions, and painting/glider criteria onto Forge 1.20.1. Preserve Ender IO IDs, conditions, item state and baseline defaults. This is an intermediate cycle, not complete gameplay parity.

- Registered global modifier `Codec` serializers through Forge's registry and loot functions through native `LootItemFunctionType` serializers. Capacitor number providers, random modifier selection and function conditions remain intact. Primary/secondary paint selectors serialize natively and reject missing or non-boolean input.
- Added `endercore:add_table`, implementing the source NeoForge helper's raw subtable-roll semantics using real Forge/Minecraft APIs. Raw rolls pass through the native stack splitter; subsequent global modifiers process the combined output once, retaining the queried parent table ID.
- Migrated the three modifier definitions/list to Forge, retaining `minecraft:any_of` and changing the loader-specific table condition to `forge:loot_table_id`. Moved the two chest tables into `loot_tables` without changing their rolls/items. The modifier list retains `replace: false` and all three Ender IO IDs.
- Replaced private newer spawner-field access with native `BaseSpawner.save` spawn data. Broken-spawner drops retain entity type, configured chance and the tool blacklist. Missing/unregistered entity IDs do not erase existing drops or create an invalid soul. This preserves the source's type-only soul behavior; it does not start copying arbitrary mob NBT.
- Centralized the existing soul and paint `ItemDataKey` instances beside their codecs; `EIODataComponents` aliases those same instances. Existing NBT IDs, the soul's earlier `stored_entity` alias, and capability refresh policy are unchanged.
- Registered the two native criterion instances during enqueued common setup. `painting` retains the actual source registration/generated-resource ID; its old unused `create_painted_block` getter was not the baseline registered ID. Native player predicates, listener removal, paint matching and criterion JSON are supported. The existing glider advancement reward hook uses the native advancement ID accessor.
- Ported both actual painting advancement definitions to plural directories/native icon fields and ported their generator to `ForgeAdvancementProvider`. Native `ExistingFileHelper` resolves the vanilla parent when saving. The two advancements retain their IDs, parents, requirements and translations. The source's commented-out glider advancement remains commented out.
- Ported the actual loot-modifier provider and compiled/executed it in the test harness. The native Forge provider uses a `HashMap` for its output list, so tests validate the exact three IDs and replacement semantics without requiring incidental generator iteration order. These three modifiers target disjoint tables. Runtime modifier ordering is independently tested with a downstream fixture modifier.

## Verification

Final command, exit **0**:

```sh
./gradlew :endercore:test :endercore:runGameTestServer :endercore:jar :endercore:reobfJar -I scripts/porting/compiler-diagnostics.init.gradle :enderio:portCompilerArguments --no-configuration-cache --console=plain
```

| Suite | Discovered | Executed | Passed | Failed/errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| EnderCore unit tests | 57 | 57 | 57 | 0 | 0 |
| Native Forge headless GameTests | 146 | 146 | 146 | 0 | 0 |

Eight new GameTests exercise:

1. Actual native spawner block drops through the loaded production modifier, stored cow soul, tool blacklist and zero drop chance.
2. Missing/unregistered spawn entries, preservation of existing drops and false global-modifier conditions.
3. Native capacitor function JSON round trips, constant/uniform number providers, modifier bounds, native saved item NBT and false conditions.
4. Both paint faces through actual loot functions, NBT persistence, missing paint/block entity, false conditions and malformed selectors.
5. An actual resource-loaded extra table emitting an oversized dynamic drop, native stack splitting, preservation of the parent table ID, a downstream modifier running once, and missing-table behavior.
6. Both actual production painting advancements loaded by the server and awarded by native listeners, wrong paint, player predicates and invalid paint IDs.
7. Native glider criterion registration, predicate serialization/matching and listener removal. This does not test gliding physics or enable the unfinished source advancement.
8. The actual production loot/advancement generators writing temporary native resources, generated loot definitions matching shipped JSON, codec decoding, native advancement icon syntax and criterion deserialization. Temporary output is removed.

The bridge compiles **108 actual main production files plus two actual datagen files** into test output. They remain absent from the core production jar. Registered fixture items/block entities stand in for main content that cannot yet load; they do not establish actual broken-spawner-item, painting-machine or complete chest-loot gameplay.

Intermediate runs exposed and corrected test source/type mistakes, an incorrect assumption that native `set_count` can emit an oversized stack, the incorrect `AlternativeLootItemCondition` API conversion, and the advancement generator's unresolved native parent. The oversized fixture now uses a real dynamic loot entry. The generator test was corrected to avoid asserting Forge's incidental `HashMap` iteration order. All final tests pass. Existing malformed data-map/packet cases intentionally log rejection errors. The unit task executed earlier in this cycle and was up-to-date on the final run; native GameTests executed on the final code.

```sh
./gradlew :enderio:compileJava --console=plain
/Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/javac -J-Xmx4G @build/port-evidence/main-javac.args
```

Both exit **1**. Normal Gradle compilation stops with **80 early symbol errors**. Supplemental javac, with annotation processing disabled, reports **309 errors and 100 warnings**, down from 350 supplemental errors in cycle 025. No diagnostics remain in the production source files changed here. Remaining errors include FTB integration, farming, entity-storage recipes, spawning/vials, fluid tanks and other content APIs. Supplemental diagnostics are not a successful build.

Evidence under ignored `build/port-evidence/`:

- `cycle-026-compile-first.log`, `cycle-026-core-first.log`, `cycle-026-core-second.log`, `cycle-026-core-third.log`, `cycle-026-core-fourth.log`, `cycle-026-core-final.log`, and successful `cycle-026-core-verified.log`.
- `cycle-026-tests.json`, `cycle-026-gametest-results.xml`, `cycle-026-main-gradle.log`, `cycle-026-main-javac.log`, `cycle-026-error-counts.json`.
- `cycle-026-artifacts.json` and `cycle-026-commit-scout.txt`.

Reobfuscated core jar built before this cycle's commit:

- `endercore/build/libs/endercore-8.2.14.25-1.20.1+a09e040fc6.jar`
- SHA-256: `460620a4fe96b48b6748e783d404e59b53c9164b9f8ae2c11196354714312e7f`
- 242 classes, all Java 17 class-file major 61; no test or bridged main/datagen classes.

The isolated GameTest server ran with `nogui` and `java.awt.headless=true`, then saved and shut down. No client/window/audio or user world was used. All cycle processes completed. No Git merge/rebase is active, the previous merge recovery refs remain preserved, and the local port branch still has no upstream. This cycle has its own local commit; no push.

## Outstanding work

Complete the remaining main/addon API conversions, all datagen/resources and packaging. Exercise the full production chest tables/items, painted block drops, painting-machine progression, actual spawner/vial interactions and glider gameplay in packaged client/server profiles. Full resource reload, client synchronization/rendering, native save migration, conduit graph reconciliation, optional integrations and multiplayer remain unverified. The full goal stays active and incomplete.

Sources: [Forge 1.20.1 global loot modifiers](https://docs.minecraftforge.net/en/1.20.1/resources/server/glm/), pinned Forge 47.4.28 sources (`LootTable`, `BaseSpawner`, native criterion and datagen classes), and the previously downloaded [NeoForge 21.1.216 source archive](https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.216/neoforge-21.1.216-sources.jar) (`AddTableLootModifier`). Pinned source and executed tests establish version-specific behavior.
