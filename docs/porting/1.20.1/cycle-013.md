# Cycle 013 — Stack persistence, filters and registry-backed conduit items

Status: progress, not completion. Cycle 012 was verified progress and recovered the accidental historical-branch merge. This cycle starts at `b6420df4c`. Main Ender IO and the addon remain incomplete; no completion requirement has been reduced.

## Changes

- Added native saved-stack codecs that preserve Forge capability data, item/fluid tags, empty entries and integer stock targets. Native `ItemStack.save` writes a byte count; the codec writes an integer and restores the native stack before applying the logical count. Unknown registry IDs and malformed counts/tags fail instead of becoming default air/empty fluid.
- JSON output uses SNBT strings for nested item/fluid NBT and `ForgeCaps`, preserving numeric tag types and typed arrays. Native NBT output retains compound tags. Decoding accepts compound or SNBT forms. This fixes a failure observed with actual Forge `EnergyStorage`, whose saved energy requires an `IntTag`; ordinary JSON/NBT conversion narrowed that number and broke deserialization.
- Added bounded filter item-data packets with a separate VarInt logical count and a native one-item payload. Targets above 127 survive without byte truncation. Ordinary physical item packets retain their existing native format. Converted the five standalone general, existing, limited, mod-ID and fluid filters to these native codecs and equality/hash operations. Updated remaining newer item-comparison calls to native NBT comparison.
- Limited-filter inventory totals use a `long`. The new runtime test reproduced integer overflow across large external inventory slots, which previously allowed insertion into an overstocked inventory and prevented valid extraction. Simulation still leaves incoming items, target inventories and the configured stock target unchanged.
- Added `RegistryItemDataKey`, storing a stable entry ID in typed item NBT. Reads resolve against explicitly supplied current registry access; direct, unbound and wrong-registry holders are rejected. Unknown IDs remain saved, with an unresolved lookup result instead of a substituted entry.
- Converted conduit item data, placement lookups, tooltips, recipe identity and JEI subtype identity to stable IDs/current world lookup. Contextless item-name presentation uses the current logical-side server/client context; gameplay lookups pass their level explicitly. Conduit models use native Forge model APIs and clear their holder/model cache when the rendering registry context changes. Tooltip signatures use native nullable levels; keyboard state is isolated to the physical client. Actual rendering, datapack reload, placement and JEI operation remain unverified.
- Converted all four custom ingredient registrations to actual Forge `CraftingHelper` serializers during recipe-serializer registration. `CodecIngredientSerializer` connects strict JSON codecs and project packet codecs to native ingredient dispatch. Conduit ingredients compare stable IDs; soul ingredient predicates retain their existing binding behavior. Native ingredient emptiness, item arrays and JSON serialization replace the newer ingredient API. Actual main-mod ingredient startup/crafting remains pending.

## Actual verification

Final combined command:

```sh
./gradlew :endercore:check :endercore:jar :endercore:reobfJar :enderio:compileJava --continue --console=plain
```

Exit **1**, solely from main Ender IO compilation at the normal **400-error cap**. EnderCore check, jar and reobfuscation pass. Log: `build/port-evidence/cycle-013-check-package-main.log`.

| Suite | Executed | Passed | Failures/errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| EnderCore JUnit | 39 | 39 | 0 | 0 |
| Native headless Forge GameTests | 71 | 71 | 0 | 0 |

Thirteen new GameTests cover:

- Real serialized Forge item capabilities, large counts, typed NBT arrays, NBT/JSON round trips, mutation independence, malformed saved state and item-data packet rejection.
- Registry-backed item identity across copy, native item save/load, native item packets and two distinct registry contexts; unknown IDs survive, invalid holder writes leave state unchanged.
- All five actual main-module filter codecs, ordered empty slots, stock counts, flags and hash consistency. Behavior checks include insert/extract stock thresholds, large-inventory arithmetic, NBT variants, damage modes, live/snapshot inventories, namespace matching and fluid identity independent of quantity.
- Actual native `Ingredient.fromJson`, `Ingredient.toNetwork`/`fromNetwork` and Forge custom serializer dispatch using a test ingredient with the production serializer adapter. Missing fields/unknown types fail; custom matching and display-item stacking IDs survive.

The first expanded run executed 71 GameTests with **69 passed and two failed**. It exposed the JSON capability tag-type loss and stock-sum overflow described above. Both were fixed and the full combined command rerun successfully for core tests. Initial log: `build/port-evidence/cycle-013-tests-first.log`.

The temporary source bridge now compiles **25 actual main-module source files** into the core GameTest output, ten more than cycle 012. The added files are the four filter API types, damage mode and five filter implementations. These are the actual production sources, not substitute implementations. They are excluded from the production core jar. This bridge still does not boot the main module or exercise its actual conduits, ingredient classes, capability registration, menus or integration lifecycle. Registry tests use real native registries with fixture values; ingredient dispatch tests use a fixture ingredient. Move/retain these tests under the full main runtime when that becomes available.

Counts/names: `build/port-evidence/cycle-013-tests.json`. XML: `endercore/build/test-results/test/` and `endercore/build/test-results/gameTestServer/results.xml`. The isolated `nogui`, headless GameTest server saved and shut down cleanly. No client, sound subsystem, desktop activation or input interaction was launched.

Supplemental compiler diagnostics:

```sh
./gradlew -I scripts/porting/compiler-diagnostics.init.gradle :enderio:portCompilerArguments --no-configuration-cache --console=plain
/Users/cyberpwn/.jabba/jdk/temurin@17.0.18/Contents/Home/bin/javac -J-Xmx4G @build/port-evidence/main-javac.args
```

Argument export passes. Direct Java 17 compilation exits **1 with 1,670 errors and 100 reported warnings**, down from cycle 012's 1,796. This is diagnostic progress, not a full-build result or a count of independent defects. Annotation processing is disabled for this supplemental command. Logs: `cycle-013-compiler-arguments.log` and `cycle-013-main-javac.log` under `build/port-evidence/`.

Intermediate core artifact, named from this cycle's base commit:

- `endercore/build/libs/endercore-8.2.14.12-1.20.1+b6420df4c8.jar`
- SHA-256: `56c146ed5de9d85d3a680e55726358919acc1f3a3118cba2bc5cdd6791030876`
- All classes are version `61` (Java 17). Test fixtures and main-source bridge classes are absent.

Inspection: `build/port-evidence/cycle-013-artifacts.json`. `git diff --check` passes. All owned build/test commands reached terminal states. The branch has no merge state or unmerged paths; cycle 012's recovery stash and refs remain preserved. No push was performed.

## Remaining work and next cycle

Continue native recipe interfaces/serializers and sized item/fluid ingredients, remaining registry-aware codecs and block-entity item drop/place state. Attachments/data maps, saved-data APIs, vanilla block interaction, rendering/UI, datagen and optional integrations still have substantial port work.

Full-mod startup, real custom ingredient registration and crafting, conduit placement/rendering/creative entries, registry/datapack reload, filter GUIs/network handlers, inventory routing, save/restart and integration behavior remain unverified. The tests above do not establish historical Ender IO 6.x save compatibility or promise Minecraft world downgrades. No final distributable or packaged client/server parity is claimed. The goal remains active.
