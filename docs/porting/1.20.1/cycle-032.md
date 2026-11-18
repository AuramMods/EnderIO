# Cycle 032 — native addon lifecycle, generated resources and first full-mod server tests

## Objective and changes

Continue from `d30ab6f17` on `1.20.1`. The full objective and tracker were reread. The working tree was clean, with no unmerged index entries, merge/rebase state or upstream. The earlier automatic merge stash and both cycle-012 recovery refs remain intact. No CodeGraph directory or manual-change protocol was present.

- Converted the remaining addon lifecycle, datagen and integration APIs. All three modules' main Java and both datagen source sets now compile against the pinned Forge 1.20.1 dependencies.
- Added composable native recipe conditions, including advancement conditions, while preserving finished-recipe IDs, serializers, NBT/count outputs and existing conditions. Added a native `DatapackBuiltinEntriesProvider` wrapper that inserts `forge:conditions` before writing to `CachedOutput` and recalculates the hash for the actual bytes. Optional conduit definitions use Forge's own conditional registry loader.
- Ported AE2 capabilities to `appeng.capabilities.Capabilities`, facades to native `IFacadeItem`, and color serialization to native `registryPrefix` values, including `fluix`. Kept the historical `enderio:ae2` conduit-type alias through Forge's registry. Pending ME node NBT now survives save-before-initialization, deep copying, migration and live-node removal; removal captures state before destroying the managed node.
- Replaced RS 2.x containers with RS 1.12.4's actual `NetworkNode`, `INetworkNodeProxy`, node manager and registered save factory. The conduit retains zero idle power consumption from the source branch. Enabled sides govern incoming/outgoing traversal; detached nodes reject connections. Owned manager entries are removed by identity, retained proxy references keep their detached node, native state is copied into conduit persistence, and affected networks are invalidated through RS's queue-aware graph API. Real controller/channel routing and unload/restart sequences still need gameplay tests.
- Ported remaining Mekanism datagen and creative-tab hooks. An actual full-mod launch exposed construction of native empty chemicals before Mekanism's registries exist. The adapter's untyped empty value now initializes without a native stack; native stack access is deferred until requested. A plain-JVM regression test exercises the early codec/filter initialization, and all seven native chemical tests still pass.
- Actual datagen exposed the glider mixin's newer four-float target signature. Converted it to the native three-float signature and enabled normal remapping; the generated refmap contains this mixin. This is startup verification, not visual glider validation.
- Ran both real datagens and committed their generated output. Recipes, advancements, loot and tags now use native 1.20 directories and payloads. Restored common `c:` root-tag aliases to Forge tags, kept the baseline `enchanting/sweeping_edge` recipe ID while using the native enchantment, and moved the Curios charm tag to `tags/items`.
- Added a separate headless addon GameTest profile loading the actual Ender IO mod and pinned integrations. Its six tests are part of addon `check`; test sources/resources are excluded from release jars. This replaces an unsuccessful plain-JUnit attempt to bootstrap Minecraft/Forge for these integration tests. No assertions were removed to accommodate that environment failure.

## Actual commands and results

Initial addon compilation fell from 97 early errors to 46 after the shared native datagen conversion, then main compilation passed. The remaining addon datagen imports were converted and compilation passed. One new recipe test initially used a native-invalid one-item shaped recipe; its fixture was corrected to two ingredients. The initial native integration launch discovered all six tests but failed because JUnit assertions were unavailable in the transformed server; those assertions now use native GameTest failures.

Final combined verification:

```sh
./gradlew :enderio:runData :enderio-modded-conduits:runData :endercore:check :enderio-modded-conduits:check :enderio:jar :enderio:reobfJar --console=plain
```

Exit **0**. Native datagen completed for both modules. Authoritative reports show:

| Suite | Executed | Failed/errors | Skipped |
| --- | ---: | ---: | ---: |
| Core unit tests | 61 | 0 | 0 |
| Core native GameTests, without Mekanism | 170 | 0 | 0 |
| Native chemical tests with Mekanism | 7 | 0 | 0 |
| Addon early-initialization unit test | 1 | 0 | 0 |
| Actual-mod native integration GameTests | 6 | 0 | 0 |

The two new core tests exercise nested recipe/advancement conditions through Forge's condition processing, and actual native registry datagen bytes, loader conditions and cache hashes. The six actual-mod tests cover pending/live ME state, legacy copying/migration, all native AE colors and malformed colors, pending/legacy RS state, and the registered native RS factory with owner persistence, immutable connection sides and detached-node rejection. ME lifecycle callback tests use an `IManagedGridNode` test double; the RS factory/owner test uses the real native node implementation. These tests do **not** test actual ME/RS controller networks, channels, item transfer or restart gameplay.

Gradle can build resource dependencies before datagen in a combined invocation. An artifact inspection caught the first jar missing the newly generated tag aliases and recipe ID. Packaging was therefore rerun **after** datagen completed:

```sh
./gradlew :enderio:jar :enderio:reobfJar :enderio-modded-conduits:check --console=plain
```

Exit **0**. Inspection of the refreshed jar confirms the new common tags, Curios charm tag and `enchanting/sweeping_edge` recipe are present; the obsolete generated `enchanting/sweeping` recipe is absent. Use separate datagen and packaging invocations when regenerating resources.

The normalized baseline inventory accounts for all 4,173 data/blockstate IDs after mapping singular 1.21 paths to native plural paths and the NeoForge global-loot-modifier entry point to Forge's namespace. This is an ID inventory, not proof of resource semantics or full feature parity.

## Artifacts and evidence

Development artifacts carry the pre-commit version `8.2.14.31-1.20.1+d30ab6f172`; these are **not validated releases**.

| Artifact under `build/libs` of its module | Own classes | Java class major | SHA-256 |
| --- | ---: | ---: | --- |
| `endercore-8.2.14.31-1.20.1+d30ab6f172.jar` | 251 | 61 | `af800aacf934c113b8aa42fd4d58a093bfafe70565ec055f1bb6cf09f4af83f4` |
| `enderio-8.2.14.31-1.20.1+d30ab6f172.jar` | 1260 | 61 | `3af4679526a4cfd1c814cb69a6848e6f7d03ef499e897f54f7463556c0d9e530` |
| `enderio-modded-conduits-8.2.14.31-1.20.1+d30ab6f172.jar` | 69 | 61 | `a658d58d3bf34da0d0e182347fb51078efc1a31394523d46978d2ba2da6eb4a2` |

The main jar contains nested GraphLib3, EnderCore and modded-conduit jars. Inspected artifacts contain no new test classes or test-mod resources. The native integration server used the development launch environment; this does not satisfy the final clean packaged-client/dedicated-multiplayer requirement.

Local ignored evidence is under `build/port-evidence/`: `cycle-032-final.log`, `cycle-032-final-packaging.log`, earlier compile/startup/test logs, `cycle-032-test-results/`, `cycle-032-test-summary.json`, `cycle-032-resource-inventory.json`, `cycle-032-artifacts.json` and the commit scout. Pinned RS source inspected for lifecycle/traversal is in `cycle-032-rs-source/`, from [RS v1.12.4](https://github.com/refinedmods/refinedstorage/tree/v1.12.4). No additional repository was cloned.

## Remaining failures and next work

- Native resource loading logs two recipe errors: LaserIO's book recipe requires the missing `patchouli:shapeless_book_recipe` serializer, and `enderio:darksteel_upgrade` names an unregistered `enderio:dark_steel_upgrade` serializer. The latter JSON exists in the fixed baseline, with no matching Java registration found there. Audit this orphan resource and the appropriate optional book dependency instead of masking loader errors or inventing implementation.
- Embeddium logs a fingerprint error during datagen because no Minecraft client instance exists; datagen still completes. Investigate the appropriate datagen-only dependency setup separately from supported client rendering.
- Full main-module unit/GameTest migration, exhaustive recipe/tag/resource semantic checks, real conduit graphs/transfers, all gameplay families and integration absence tests remain incomplete. Existing known chemical transfer accounting and other tracker risks are not resolved by these state tests.
- No game client has been launched. Non-activating client instrumentation, visuals, single-player interaction, clean packaged installations and dedicated multiplayer remain required. Native tests used isolated `nogui`, headless server profiles, with EULA accepted. All created test servers exited.
- Next cycle: resolve observed resource-loading errors, expand actual-mod native gameplay/network tests, and verify clean packaged server loading. Continue toward silent, non-activating client validation; the goal remains active.
