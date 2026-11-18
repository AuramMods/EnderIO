# Forge 1.20.1 feature evidence matrix

Baseline: `4c482da69e76f2b46bfeeac64ef0ac277c318eaa`. All rows are **pending** until actual runtime/test evidence is entered. This matrix lists concrete source declarations, including factory maps; it is not a claim that every declaration creates exactly one registry entry. The immutable [baseline inventory](baseline.json) also records all captured content/integration source paths, test methods and generated data/blockstate IDs. Reconcile those IDs with runtime registries before completion.

For each completed row, replace Pending with the tested commit/profile, command or procedure and evidence path. Shared scenario evidence may cover multiple rows if it actually exercises their behavior.

Factory expansion: `SOLAR_PANELS` covers energetic/pulsating/vibrant photovoltaic modules; `CAPACITOR_BANKS` covers basic/advanced/vibrant banks. `GLASS_BLOCKS` covers 42 combinations (3 lighting modes × 7 collision rules × 2 materials), each with clear plus 16 dye colors: 714 block/item variants. Check registration/assets for every variant and behavior across each lighting/collision/material dimension.

Enablement audit: farming station is registered without a required feature flag in the baseline. Enderface and Niard require their named flags. Existing opt-in datapacks and their availability must be retained through a Forge-compatible implementation.

## EIOBlocks

Place, render, interact/automate, break/drop and reload; machine rows also require their full processing behavior.

| Declaration | Source | Evidence |
| --- | --- | --- |
| `BLOCKS` | [EIOBlocks:104](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `ITEMS` | [EIOBlocks:105](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `CONDUCTIVE_ALLOY_BLOCK` | [EIOBlocks:111](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `ENERGETIC_ALLOY_BLOCK` | [EIOBlocks:112](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `VIBRANT_ALLOY_BLOCK` | [EIOBlocks:113](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `REDSTONE_ALLOY_BLOCK` | [EIOBlocks:114](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `PULSATING_ALLOY_BLOCK` | [EIOBlocks:115](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `DARK_STEEL_BLOCK` | [EIOBlocks:116](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `SOULARIUM_BLOCK` | [EIOBlocks:117](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `END_STEEL_BLOCK` | [EIOBlocks:118](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `VOID_CHASSIS` | [EIOBlocks:130](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `ENSOULED_CHASSIS` | [EIOBlocks:136](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `DARK_STEEL_LADDER` | [EIOBlocks:153](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `DARK_STEEL_BARS` | [EIOBlocks:155](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `DARK_STEEL_DOOR` | [EIOBlocks:158](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `DARK_STEEL_TRAPDOOR` | [EIOBlocks:161](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `END_STEEL_BARS` | [EIOBlocks:164](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `REINFORCED_OBSIDIAN` | [EIOBlocks:167](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `PAINTED_FENCE` | [EIOBlocks:181](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `PAINTED_FENCE_GATE` | [EIOBlocks:182](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `PAINTED_SAND` | [EIOBlocks:183](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `PAINTED_STAIRS` | [EIOBlocks:184](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `PAINTED_CRAFTING_TABLE` | [EIOBlocks:185](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `PAINTED_REDSTONE_BLOCK` | [EIOBlocks:186](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `PAINTED_TRAPDOOR` | [EIOBlocks:187](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `PAINTED_WOODEN_PRESSURE_PLATE` | [EIOBlocks:188](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `PAINTED_SLAB` | [EIOBlocks:189](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `PAINTED_GLOWSTONE` | [EIOBlocks:190](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `PAINTED_WALL` | [EIOBlocks:191](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `RESETTING_LEVERS` | [EIOBlocks:203](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `RESETTING_LEVER_FIVE` | [EIOBlocks:205](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `RESETTING_LEVER_FIVE_INV` | [EIOBlocks:206](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `RESETTING_LEVER_TEN` | [EIOBlocks:207](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `RESETTING_LEVER_TEN_INV` | [EIOBlocks:208](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `RESETTING_LEVER_THIRTY` | [EIOBlocks:209](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `RESETTING_LEVER_THIRTY_INV` | [EIOBlocks:210](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `RESETTING_LEVER_SIXTY` | [EIOBlocks:211](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `RESETTING_LEVER_SIXTY_INV` | [EIOBlocks:212](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `RESETTING_LEVER_THREE_HUNDRED` | [EIOBlocks:213](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `RESETTING_LEVER_THREE_HUNDRED_INV` | [EIOBlocks:214](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `DARK_STEEL_PRESSURE_PLATE` | [EIOBlocks:226](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `SILENT_DARK_STEEL_PRESSURE_PLATE` | [EIOBlocks:230](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `SOULARIUM_PRESSURE_PLATE` | [EIOBlocks:234](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `SILENT_SOULARIUM_PRESSURE_PLATE` | [EIOBlocks:238](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `SILENT_OAK_PRESSURE_PLATE` | [EIOBlocks:242](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `SILENT_ACACIA_PRESSURE_PLATE` | [EIOBlocks:245](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `SILENT_DARK_OAK_PRESSURE_PLATE` | [EIOBlocks:248](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `SILENT_SPRUCE_PRESSURE_PLATE` | [EIOBlocks:251](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `SILENT_BIRCH_PRESSURE_PLATE` | [EIOBlocks:254](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `SILENT_JUNGLE_PRESSURE_PLATE` | [EIOBlocks:257](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `SILENT_CRIMSON_PRESSURE_PLATE` | [EIOBlocks:260](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `SILENT_WARPED_PRESSURE_PLATE` | [EIOBlocks:263](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `SILENT_STONE_PRESSURE_PLATE` | [EIOBlocks:266](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `SILENT_POLISHED_BLACKSTONE_PRESSURE_PLATE` | [EIOBlocks:269](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `SILENT_HEAVY_WEIGHTED_PRESSURE_PLATE` | [EIOBlocks:272](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `SILENT_LIGHT_WEIGHTED_PRESSURE_PLATE` | [EIOBlocks:275](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `CONDUIT_BUNDLE` | [EIOBlocks:293](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial: native routing/split/merge, priority/tier transfer and nine break/wrench/drop scenarios pass; full lifecycle/render coverage remains pending ([cycle 035](cycle-035.md)) |
| `SOUL_CHAIN` | [EIOBlocks:301](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `COLD_FIRE` | [EIOBlocks:309](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial: age/expiration scenario passes; the baseline cold-fire crafting scenario remains commented out and unverified ([cycle 035](cycle-035.md)) |
| `ENDERMAN_HEAD` | [EIOBlocks:312](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `WALL_ENDERMAN_HEAD` | [EIOBlocks:315](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `INDUSTRIAL_INSULATION` | [EIOBlocks:318](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `FLUID_TANK` | [EIOBlocks:326](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial: prior NBT/load/recipe regressions plus bucket fill/drain, partial capacity, blocked outputs and tagged containers pass. Clean packaged server preserves fluid/items and recipe outputs through two restarts; GUI/full automation remain pending ([cycle 036](cycle-036.md)) |
| `PRESSURIZED_FLUID_TANK` | [EIOBlocks:333](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial: native load/resumption and the same five transfer scenarios pass; packaged fluid NBT/inventory and processing persist through two restarts. GUI/full automation remain pending ([cycle 036](cycle-036.md)) |
| `ENCHANTER` | [EIOBlocks:341](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial: Forge composite loader corrected; actual hidden client loads all four facing states. In-world rendering, interactions and full feature behavior remain pending ([cycle 038](cycle-038.md)) |
| `ENDERFACE` | [EIOBlocks:348](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `ALLOY_SMELTER` | [EIOBlocks:356](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial: alloying and vanilla smelting inputs/outputs/energy pass; full automation/persistence/GUI coverage remains pending ([cycle 035](cycle-035.md)) |
| `PAINTING_MACHINE` | [EIOBlocks:359](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `WIRELESS_CHARGER` | [EIOBlocks:362](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `STIRLING_GENERATOR` | [EIOBlocks:365](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `SAG_MILL` | [EIOBlocks:368](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial: stone, copper ore and raw copper processing/energy pass; bonus outputs/full automation/persistence/GUI remain pending ([cycle 035](cycle-035.md)) |
| `SLICE_AND_SPLICE` | [EIOBlocks:371](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial: test electrode recipe inputs/output/energy pass; full automation/persistence/GUI remain pending ([cycle 035](cycle-035.md)) |
| `IMPULSE_HOPPER` | [EIOBlocks:374](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `SOUL_BINDER` | [EIOBlocks:377](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial: test binding recipe soul/inputs/output/energy pass; full automation/persistence/GUI remain pending ([cycle 035](cycle-035.md)) |
| `CRAFTER` | [EIOBlocks:380](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `DRAIN` | [EIOBlocks:383](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `WIRED_CHARGER` | [EIOBlocks:387](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `WIRELESS_CHARGER_ANTENNA` | [EIOBlocks:391](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `WIRELESS_CHARGER_ANTENNA_ADVANCED` | [EIOBlocks:398](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `CREATIVE_POWER` | [EIOBlocks:406](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `POWERED_SPAWNER` | [EIOBlocks:411](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial: Forge composite loader corrected; actual hidden client loads all eight facing/powered states. In-world rendering, interactions and full feature behavior remain pending ([cycle 038](cycle-038.md)) |
| `MIND_KILLER` | [EIOBlocks:416](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `VACUUM_CHEST` | [EIOBlocks:423](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `XP_VACUUM` | [EIOBlocks:427](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `TRAVEL_ANCHOR` | [EIOBlocks:432](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `PAINTED_TRAVEL_ANCHOR` | [EIOBlocks:436](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `SOLAR_PANELS` | [EIOBlocks:442](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `CAPACITOR_BANKS` | [EIOBlocks:452](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial: basic-bank capacity/split/merge and conduit energy scenarios pass; all tier/render/restart coverage remains pending ([cycle 035](cycle-035.md)) |
| `SOUL_ENGINE` | [EIOBlocks:462](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `NIARD` | [EIOBlocks:467](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `VAT` | [EIOBlocks:475](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial: reagent ordering and full-output handling regressions pass; full progression/automation/persistence/GUI remain pending ([cycle 035](cycle-035.md)) |
| `BLOCK_DETECTOR` | [EIOBlocks:480](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial: neighbor-update regression passes; all configurations/render/restart behavior remain pending ([cycle 035](cycle-035.md)) |
| `FARMING_STATION` | [EIOBlocks:485](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `XP_OBELISK` | [EIOBlocks:490](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `INHIBITOR_OBELISK` | [EIOBlocks:493](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `AVERSION_OBELISK` | [EIOBlocks:496](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `RELOCATOR_OBELISK` | [EIOBlocks:499](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `ATTRACTOR_OBELISK` | [EIOBlocks:502](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `WEATHER_OBELISK` | [EIOBlocks:505](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `FLUID_TANK_ITEM` | [EIOBlocks:509](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial: actual native item capability transfers 500 mB from a 3,000 mB tagged container into either nearly full tank, retaining 2,500 mB and NBT. Placement/render/other interactions remain pending ([cycle 036](cycle-036.md)) |
| `PRESSURIZED_FLUID_TANK_ITEM` | [EIOBlocks:511](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `POWERED_SPAWNER_ITEM` | [EIOBlocks:514](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `SOUL_ENGINE_ITEM` | [EIOBlocks:517](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `SOLAR_PANEL_ITEMS` | [EIOBlocks:520](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `CAPACITOR_BANK_ITEMS` | [EIOBlocks:529](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `GLASS_BLOCKS` | [EIOBlocks:581](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |

## EIOItems

Obtain/craft where applicable, render/tooltip, use, copy/stack, transfer and persist state.

| Declaration | Source | Evidence |
| --- | --- | --- |
| `ITEMS` | [EIOItems:45](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `CONDUCTIVE_ALLOY_INGOT` | [EIOItems:49](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `ENERGETIC_ALLOY_INGOT` | [EIOItems:50](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `VIBRANT_ALLOY_INGOT` | [EIOItems:51](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `REDSTONE_ALLOY_INGOT` | [EIOItems:52](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `PULSATING_ALLOY_INGOT` | [EIOItems:53](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `DARK_STEEL_INGOT` | [EIOItems:54](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `SOULARIUM_INGOT` | [EIOItems:55](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `END_STEEL_INGOT` | [EIOItems:56](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `CONDUCTIVE_ALLOY_NUGGET` | [EIOItems:58](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `ENERGETIC_ALLOY_NUGGET` | [EIOItems:59](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `VIBRANT_ALLOY_NUGGET` | [EIOItems:60](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `REDSTONE_ALLOY_NUGGET` | [EIOItems:61](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `PULSATING_ALLOY_NUGGET` | [EIOItems:62](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `DARK_STEEL_NUGGET` | [EIOItems:63](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `SOULARIUM_NUGGET` | [EIOItems:64](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `END_STEEL_NUGGET` | [EIOItems:65](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `ENERGETIC_ALLOY_BALL` | [EIOItems:71](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `VIBRANT_ALLOY_BALL` | [EIOItems:73](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `REDSTONE_ALLOY_BALL` | [EIOItems:75](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `CONDUCTIVE_ALLOY_BALL` | [EIOItems:77](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `PULSATING_ALLOY_BALL` | [EIOItems:79](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `DARK_STEEL_BALL` | [EIOItems:81](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `SOULARIUM_BALL` | [EIOItems:83](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `END_STEEL_BALL` | [EIOItems:85](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `SILICON` | [EIOItems:95](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `GRAINS_OF_INFINITY` | [EIOItems:96](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `INFINITY_ROD` | [EIOItems:97](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `CONDUIT_BINDER_COMPOSITE` | [EIOItems:98](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `CONDUIT_BINDER` | [EIOItems:99](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `GEAR_IRON` | [EIOItems:101](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `GEAR_ENERGIZED` | [EIOItems:102](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `GEAR_VIBRANT` | [EIOItems:103](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `GEAR_DARK_STEEL` | [EIOItems:104](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `ZOMBIE_ELECTRODE` | [EIOItems:106](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `Z_LOGIC_CONTROLLER` | [EIOItems:107](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `FRANK_N_ZOMBIE` | [EIOItems:108](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `ENDER_RESONATOR` | [EIOItems:109](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `SENTIENT_ENDER` | [EIOItems:110](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `SKELETAL_CONTRACTOR` | [EIOItems:111](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `GUARDIAN_DIODE` | [EIOItems:112](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `SUSPICIOUS_SEED` | [EIOItems:113](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `BASIC_CAPACITOR` | [EIOItems:119](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `DOUBLE_LAYER_CAPACITOR` | [EIOItems:122](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `OCTADIC_CAPACITOR` | [EIOItems:125](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `LOOT_CAPACITOR` | [EIOItems:128](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `PULSATING_CRYSTAL` | [EIOItems:135](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `VIBRANT_CRYSTAL` | [EIOItems:136](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `ENDER_CRYSTAL` | [EIOItems:137](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `ENTICING_CRYSTAL` | [EIOItems:138](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `WEATHER_CRYSTAL` | [EIOItems:139](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `PRESCIENT_CRYSTAL` | [EIOItems:140](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `POWDERED_COAL` | [EIOItems:146](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `POWDERED_IRON` | [EIOItems:147](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `POWDERED_GOLD` | [EIOItems:148](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `POWDERED_COPPER` | [EIOItems:149](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `POWDERED_TIN` | [EIOItems:150](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `POWDERED_ENDER_PEARL` | [EIOItems:151](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `POWDERED_OBSIDIAN` | [EIOItems:152](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `POWDERED_LAPIS_LAZULI` | [EIOItems:153](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `POWDERED_QUARTZ` | [EIOItems:154](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `PRESCIENT_POWDER` | [EIOItems:155](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `VIBRANT_POWDER` | [EIOItems:156](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `PULSATING_POWDER` | [EIOItems:157](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `ENDER_CRYSTAL_POWDER` | [EIOItems:158](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `PHOTOVOLTAIC_COMPOSITE` | [EIOItems:159](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `SOUL_POWDER` | [EIOItems:160](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `CONFUSION_POWDER` | [EIOItems:161](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `WITHERING_POWDER` | [EIOItems:162](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `PHOTOVOLTAIC_PLATE` | [EIOItems:168](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `NUTRITIOUS_STICK` | [EIOItems:169](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `PLANT_MATTER_GREEN` | [EIOItems:170](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `PLANT_MATTER_BROWN` | [EIOItems:171](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `GLIDER_WING` | [EIOItems:172](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `ANIMAL_TOKEN` | [EIOItems:173](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `MONSTER_TOKEN` | [EIOItems:174](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `PLAYER_TOKEN` | [EIOItems:175](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `BROKEN_SPAWNER` | [EIOItems:177](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `GLIDER` | [EIOItems:194](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `ENDERIOS` | [EIOItems:205](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `SOUL_VIAL` | [EIOItems:212](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `VOID_VIAL` | [EIOItems:213](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `EXPERIENCE_ROD` | [EIOItems:214](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `YETA_WRENCH` | [EIOItems:215](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `COORDINATE_SELECTOR` | [EIOItems:216](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `LOCATION_PRINTOUT` | [EIOItems:217](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `LEVITATION_STAFF` | [EIOItems:218](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `TRAVEL_STAFF` | [EIOItems:219](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `ELECTROMAGNET` | [EIOItems:220](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `COLD_FIRE_IGNITER` | [EIOItems:221](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `CONDUIT_PROBE` | [EIOItems:222](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `DARK_STEEL_SWORD` | [EIOItems:224](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `CONDUIT` | [EIOItems:233](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `CONDUIT_FACADE` | [EIOItems:236](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `TRANSPARENT_CONDUIT_FACADE` | [EIOItems:239](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `HARDENED_CONDUIT_FACADE` | [EIOItems:242](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `TRANSPARENT_HARDENED_CONDUIT_FACADE` | [EIOItems:245](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `BASIC_ITEM_FILTER` | [EIOItems:252](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `BIG_ITEM_FILTER` | [EIOItems:253](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `ADVANCED_ITEM_FILTER` | [EIOItems:254](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `BIG_ADVANCED_ITEM_FILTER` | [EIOItems:256](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `LIMITED_ITEM_FILTER` | [EIOItems:258](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `BASIC_FLUID_FILTER` | [EIOItems:256](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Partial: actual extraction/insertion filter items obey live allow/deny edits; existing saved NBT-match settings reject mismatches and preserve transferred fluid NBT. GUI and multiplayer editing remain pending ([cycle 037](cycle-037.md)) |
| `BASIC_SOUL_FILTER` | [EIOItems:262](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `REDSTONE_FILTER_BASE` | [EIOItems:264](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `NOT_FILTER` | [EIOItems:266](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `OR_FILTER` | [EIOItems:267](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `AND_FILTER` | [EIOItems:268](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `NOR_FILTER` | [EIOItems:269](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `NAND_FILTER` | [EIOItems:270](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `XOR_FILTER` | [EIOItems:271](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `XNOR_FILTER` | [EIOItems:272](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `TLATCH_FILTER` | [EIOItems:273](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `COUNT_FILTER` | [EIOItems:274](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `SENSOR_FILTER` | [EIOItems:275](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `TIMER_FILTER` | [EIOItems:276](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `CREATIVE_ICON` | [EIOItems:282](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |

## EIOConduits

Connect, transfer, configure/filter, split/merge and unload/reload without loss or duplication.

| Declaration | Source | Evidence |
| --- | --- | --- |
| `ENERGY` | [EIOConduits:11](../../../enderio/src/main/java/com/enderio/enderio/init/EIOConduits.java) | Pending |
| `ENERGETIC_ENERGY` | [EIOConduits:13](../../../enderio/src/main/java/com/enderio/enderio/init/EIOConduits.java) | Pending |
| `VIBRANT_ENERGY` | [EIOConduits:15](../../../enderio/src/main/java/com/enderio/enderio/init/EIOConduits.java) | Pending |
| `REDSTONE` | [EIOConduits:17](../../../enderio/src/main/java/com/enderio/enderio/init/EIOConduits.java) | Pending |
| `FLUID` | [EIOConduits:19](../../../enderio/src/main/java/com/enderio/enderio/init/EIOConduits.java) | Partial: actual native tanks transfer 1,000 mB per five-tick update; partial destinations share that source budget. Mixed paths, filters and channel/redstone changes pass. Full lifecycle/client/conservation remain pending ([cycle 037](cycle-037.md)) |
| `ENERGETIC_FLUID` | [EIOConduits:21](../../../enderio/src/main/java/com/enderio/enderio/init/EIOConduits.java) | Partial: pressurized tier transfers 5,000 mB per five-tick native update with exact source/destination accounting. Complete feature/lifecycle/client evidence remains pending ([cycle 037](cycle-037.md)) |
| `VIBRANT_FLUID` | [EIOConduits:23](../../../enderio/src/main/java/com/enderio/enderio/init/EIOConduits.java) | Partial: ender tier transfers all 32,000 mB available in the actual test tank (below its 40,000 mB update limit); mixed basic-tier path limits transfer to 1,000 mB. Complete feature/lifecycle/client evidence remains pending ([cycle 037](cycle-037.md)) |
| `ITEM` | [EIOConduits:25](../../../enderio/src/main/java/com/enderio/enderio/init/EIOConduits.java) | Pending |
| `ENERGETIC_ITEM` | [EIOConduits:27](../../../enderio/src/main/java/com/enderio/enderio/init/EIOConduits.java) | Pending |
| `VIBRANT_ITEM` | [EIOConduits:29](../../../enderio/src/main/java/com/enderio/enderio/init/EIOConduits.java) | Pending |

## EIOFluids

Register, render, fill/drain, world behavior where applicable, recipes and persistent storage.

| Declaration | Source | Evidence |
| --- | --- | --- |
| `FLUIDS` | [EIOFluids:14](../../../enderio/src/main/java/com/enderio/enderio/init/EIOFluids.java) | Pending |
| `NUTRIENT_DISTILLATION` | [EIOFluids:16](../../../enderio/src/main/java/com/enderio/enderio/init/EIOFluids.java) | Pending |
| `DEW_OF_THE_VOID` | [EIOFluids:22](../../../enderio/src/main/java/com/enderio/enderio/init/EIOFluids.java) | Pending |
| `VAPOR_OF_LEVITY` | [EIOFluids:29](../../../enderio/src/main/java/com/enderio/enderio/init/EIOFluids.java) | Pending |
| `HOOTCH` | [EIOFluids:35](../../../enderio/src/main/java/com/enderio/enderio/init/EIOFluids.java) | Pending |
| `ROCKET_FUEL` | [EIOFluids:41](../../../enderio/src/main/java/com/enderio/enderio/init/EIOFluids.java) | Pending |
| `FIRE_WATER` | [EIOFluids:47](../../../enderio/src/main/java/com/enderio/enderio/init/EIOFluids.java) | Partial: fire-crafting drops and block conversion pass with test recipes; full fluid/visual/lifecycle behavior remains pending ([cycle 035](cycle-035.md)) |
| `XP_JUICE` | [EIOFluids:55](../../../enderio/src/main/java/com/enderio/enderio/init/EIOFluids.java) | Pending |
| `LIQUID_SUNSHINE` | [EIOFluids:62](../../../enderio/src/main/java/com/enderio/enderio/init/EIOFluids.java) | Pending |
| `LIQUID_DARKNESS` | [EIOFluids:70](../../../enderio/src/main/java/com/enderio/enderio/init/EIOFluids.java) | Pending |
| `CLOUD_SEED` | [EIOFluids:76](../../../enderio/src/main/java/com/enderio/enderio/init/EIOFluids.java) | Pending |
| `CLOUD_SEED_CONCENTRATED` | [EIOFluids:82](../../../enderio/src/main/java/com/enderio/enderio/init/EIOFluids.java) | Pending |

## EIOEntities

Spawn, render, interact and persist/synchronize where applicable.

| Declaration | Source | Evidence |
| --- | --- | --- |
| `PAINTED_SAND` | [EIOEntities:15](../../../enderio/src/main/java/com/enderio/enderio/init/EIOEntities.java) | Pending |

## EIORecipes

Load, match, execute, consume/produce correct amounts, synchronize and reload.

| Declaration | Source | Evidence |
| --- | --- | --- |
| `FIRE_CRAFTING` | [EIORecipes:34](../../../enderio/src/main/java/com/enderio/enderio/init/EIORecipes.java) | Pending |
| `SHAPED_ENTITY_STORAGE` | [EIORecipes:37](../../../enderio/src/main/java/com/enderio/enderio/init/EIORecipes.java) | Pending |
| `ENCHANTING` | [EIORecipes:41](../../../enderio/src/main/java/com/enderio/enderio/init/EIORecipes.java) | Pending |
| `ALLOY_SMELTING` | [EIORecipes:43](../../../enderio/src/main/java/com/enderio/enderio/init/EIORecipes.java) | Pending |
| `SAG_MILLING` | [EIORecipes:45](../../../enderio/src/main/java/com/enderio/enderio/init/EIORecipes.java) | Pending |
| `SLICING` | [EIORecipes:47](../../../enderio/src/main/java/com/enderio/enderio/init/EIORecipes.java) | Pending |
| `SOUL_BINDING` | [EIORecipes:49](../../../enderio/src/main/java/com/enderio/enderio/init/EIORecipes.java) | Pending |
| `TANK` | [EIORecipes:51](../../../enderio/src/main/java/com/enderio/enderio/init/EIORecipes.java) | Pending |
| `PAINTING` | [EIORecipes:53](../../../enderio/src/main/java/com/enderio/enderio/init/EIORecipes.java) | Pending |
| `VAT_FERMENTING` | [EIORecipes:55](../../../enderio/src/main/java/com/enderio/enderio/init/EIORecipes.java) | Pending |
| `WEATHER_CHANGE` | [EIORecipes:57](../../../enderio/src/main/java/com/enderio/enderio/init/EIORecipes.java) | Pending |

## Optional modded conduits

| Feature | Required behavior | Evidence |
| --- | --- | --- |
| AE2 ME conduits | Channels, colored connections, network host lifecycle, split/merge, facades, save/restart and missing-AE2 loading | Pending |
| Mekanism chemical conduits and filters | Adapt the newer unified chemical API to all applicable 1.20.1 chemical types; transfer/filter/persist without mixing or losing contents | Pending |
| Mekanism heat conduits | Heat transfer, equilibrium, disconnect/reconnect, unload/reload | Pending |
| Refined Storage conduits | 1.x network integration, connectivity, split/merge, saved nodes and missing-RS loading | Pending |

## Shared state and presentation

Capabilities and invalidation, NBT item state, codecs, packets, menus, attachment replacements, data-map replacements, keybindings, dynamic models, particles and resource reload remain pending. Existing test scenarios are listed individually in `baseline.json`; port their actual assertions rather than replacing them with compile-only checks.

Cycle 024 adds five native Forge GameTests for the shared transient level capability and two spatial-lookup unit tests. The real obelisk/fire-crafting managers now use that capability; their complete gameplay and chunk/restart behavior remain **UNVERIFIED** until the main mod runs. This does not change the feature-family acceptance statuses. See [cycle 024](cycle-024.md).

Cycle 025 adds seven native GameTests covering data-map loading, layered filesystem packs, staged tags, conditions/removals, datagen round trips, snapshot/chunk isolation, actual vat recipe modifiers and grinding-ball NBT. Client synchronization transport, UI and full machine gameplay remain **UNVERIFIED**; the tests do not establish complete feature-family parity. See [cycle 025](cycle-025.md).

Cycle 026 adds eight native GameTests for spawner drops/blacklists/chance, capacitor and paint loot functions, raw subtable rolls/global-modifier sequencing, native painting/glider criteria, and actual loot/advancement generators. The two production painting advancements load and award through native listeners. Tests use registered fixture items/block entities for unavailable main content; complete chest/item/painting-machine/glider gameplay and feature-family parity remain **UNVERIFIED**. See [cycle 026](cycle-026.md).

Cycle 027 adds nine native GameTests using the actual soul-vial item, actual read-only soul provider, actual dispenser registration, live entities/native server-player fixtures and shipped capture tags. Tests cover player/dispenser conservation, failed/cancelled releases, creative/specific interactions, configured vanilla spawner changes, native tag generation and tag-driven cache refresh. Packaged client interaction/rendering, multiplayer and complete feature-family parity remain **UNVERIFIED**. See [cycle 027](cycle-027.md).

Cycle 031 adds seven headless GameTests with pinned Mekanism loaded. They exercise the production chemical resource/handler adapters, filters and sync slots against all four actual chemical registries and Mekanism's native tank implementation. Same IDs in different families, long amounts, JSON/NBT/packet round trips, malformed input, quantity-preserving allow/deny filters, simulation versus execution and menu snapshots are covered. Actual conduit graph routing, neighboring-machine capability lifecycle, GUI rendering, LaserIO hooks and packaged integration remain **UNVERIFIED**. See [cycle 031](cycle-031.md).

Cycle 032 runs six tests with the **actual Ender IO mod** and pinned integrations in a headless native Forge server. ME pending/live/legacy state, native color serialization, RS pending state and registered factory/owner/connection-side behavior pass. Native addon compilation and both datagens pass, and the normalized 4,173 baseline resource IDs are accounted for across native directory/loader namespace changes. Real ME/RS controller networks, chemical transfer, GUI rendering, integration-absence cases and packaged gameplay remain **UNVERIFIED**. Two recipe-loading errors remain; see [cycle 032](cycle-032.md). No existing feature row becomes complete solely from these state tests.

Cycle 033 adds 12 actual-mod native tests: ME grid split/merge and owner-preserving bundle reload; RS controller/disk-drive route split/reconnect, disabled-face behavior and owner-preserving bundle reload; gas/infusion/pigment/slurry transfers through native Mekanism tank blocks; partial-destination and large-rate budgets; and LaserIO book crafting. Chemical rates and extraction budgets are corrected after reproductions. This does not prove disk-backed chunk/restart behavior, powered ME channels/storage, RS storage operations, chemical execution-time remainder safety, client behavior or packaged installations. The source baseline's orphan `enderio:darksteel_upgrade` recipe has no implemented serializer and is explicitly removed; the immutable baseline inventory still records it. See [cycle 033](cycle-033.md).

Cycle 034 preserves all 128 existing main JUnit cases: 16 CPU-only cases and 112 registry-dependent cases executed by real Jupiter inside native Forge. The moved filter, soul, recipe, fluid-bucket, ghost-slot, transfer, conduit-pathing and save-codec tests retain all 310 assertion calls. The full build passes with 78 ordinary JUnit and 307 native GameTest executions across configured profiles. This does not cover the separate 43 main gameplay GameTest registrations, full integration absence, packaged installs, restart persistence or client/multiplayer behavior. Feature-family completion remains pending; see [cycle 034](cycle-034.md).

Cycle 035 restores all 58 enabled baseline gameplay GameTests and adds four fluid-tank load/resumption regressions. All 62 execute in native Forge. The first shared-template run exposed 15 namespace-filtered tests; a post-run comparison now reconciles actual discovered and executed names, and an intentional filtering probe correctly fails after the remaining tests pass. Both tank variants now load before level assignment and rebuild cached recipes on load. GUI, packaged installation and broader feature-family acceptance remain incomplete. See [cycle 035](cycle-035.md).

Cycle 038 directly verifies a hidden, muted development-client title screen and twelve native baked enchanter/spawner block states after fixing three loader namespaces. Independent macOS observation records no activation/foreground/onscreen events; narrator and every volume category are off. This does not establish packaged-client, singleplayer, multiplayer, machine visuals or GUI interaction parity. See [cycle 038](cycle-038.md) and the [client harness](client-harness.md).

Cycle 039 loads the actual distributable in an official Forge client outside the development classpath. Ender IO, nested EnderCore and nested Modded Conduits are present with matching versions; the real title screen and twelve baked model states pass under hidden, muted native observation. This establishes limited packaged startup/resource evidence without optional integrations. Singleplayer, machine visuals/menus, multiplayer, synchronization and complete feature-family parity remain pending. See [cycle 039](cycle-039.md).
