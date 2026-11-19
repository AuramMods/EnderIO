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
| `CONDUCTIVE_ALLOY_BLOCK` | [EIOBlocks:111](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial. A placed copper alloy block migrates and persists across the packaged restart ([cycle 047](cycle-047.md)). |
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
| `PAINTED_FENCE` | [EIOBlocks:185](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java#L185) | Partial. A real diamond-painted fence is produced, transferred in the menu, placed in survival and rendered over packaged TCP. Native loot and a server restart preserve the paint. Historical migration and other placement configurations remain open. [Cycle 083](cycle-083.md) |
| `PAINTED_FENCE_GATE` | [EIOBlocks:182](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `PAINTED_SAND` | [EIOBlocks:183](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `PAINTED_STAIRS` | [EIOBlocks:184](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `PAINTED_CRAFTING_TABLE` | [EIOBlocks:185](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `PAINTED_REDSTONE_BLOCK` | [EIOBlocks:186](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `PAINTED_TRAPDOOR` | [EIOBlocks:187](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `PAINTED_WOODEN_PRESSURE_PLATE` | [EIOBlocks:188](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `PAINTED_SLAB` | [EIOBlocks:194](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java#L194) | Forge tests cover both stacking orders, an unpainted existing half, pick-block and native loot. Packaged multiplayer places both real Forge 6 drops and typed modern drops in survival. The client renders both colors and every fixture slab and drop survives server restart. A migrated Painting Machine finishes a paid-in-part slab job and stacks its output with the imported slab already in the slot. Native hopper and menu extraction and survival placement pass. [Cycle 084](cycle-084.md), [cycle 085](cycle-085.md) |
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
| `CONDUIT_BUNDLE` | [EIOBlocks:323](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java#L323) | Partial. Native routing, split, merge, priority and tier transfer pass, as do nine break, wrench and drop scenarios. A mixed 6.2 item and energy bundle keeps its settings, filter, facade and route through conversion, chunk reload and restart. Forge and Embeddium cull matching glass facade faces across a chunk boundary and rebuild both meshes when one facade is repainted. Packaged Ultimine selects matching facade or conduit parts across a chunk boundary and preserves unrelated parts and drops through restart. Full legacy transfer and rendering coverage remain open. [Cycle 035](cycle-035.md), [Cycle 048](cycle-048.md), [Cycle 088](cycle-088.md), [Cycle 096](cycle-096.md) |
| `SOUL_CHAIN` | [EIOBlocks:301](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `COLD_FIRE` | [EIOBlocks:309](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial: age/expiration scenario passes; the baseline cold-fire crafting scenario remains commented out and unverified ([cycle 035](cycle-035.md)) |
| `ENDERMAN_HEAD` | [EIOBlocks:312](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `WALL_ENDERMAN_HEAD` | [EIOBlocks:315](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `INDUSTRIAL_INSULATION` | [EIOBlocks:318](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial. A placed 6.2 block migrates to the current ID and persists across the packaged restart ([cycle 047](cycle-047.md)). |
| `FLUID_TANK` | [EIOBlocks:326](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial: prior native transfer/recipe/NBT cases and packaged server restarts pass. Packaged single-player now verifies the real menu, bucket shift-click, exact counts, widget/server update, visible fluid and disk reopen. Packaged TCP also verifies these interactions, reconnect and dedicated-server restart. Full drain/recipe/automation/placement remain pending ([cycle 036](cycle-036.md), [cycle 040](cycle-040.md), [cycle 041](cycle-041.md)) |
| `PRESSURIZED_FLUID_TANK` | [EIOBlocks:333](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial: prior native transfer/recipe/NBT cases and packaged server restarts pass. Packaged single-player now verifies the real menu, bucket shift-click, exact counts, widget/server update, visible fluid and disk reopen. Full drain/recipe/automation/placement and multiplayer remain pending ([cycle 036](cycle-036.md), [cycle 040](cycle-040.md)) |
| `ENCHANTER` | [EIOBlocks:341](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial. The Forge composite loader supplies all four facing states, and a packaged world renders the model and book. The packaged TCP menu has a prepared Sharpness preview and Inventory Sorter leaves its preview and ingredients intact. Result collection, XP and ingredient costs, ready-job resumption and a full visual pass remain open. [Cycle 038](cycle-038.md), [cycle 040](cycle-040.md), [cycle 103](cycle-103.md) |
| `ENDERFACE` | [EIOBlocks:378](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial. Opt-in persists after restart. On packaged TCP, the Staff of Travelling opens the native preview and selects a remote chest and Ender IO tank, including targets five blocks deep in the preview. Shift-click transfers the exact chest stack. Removing the Enderface closes the tank menu. Live preview changes and other camera angles remain open. [Cycle 098](cycle-098.md) |
| `ALLOY_SMELTER` | [EIOBlocks:356](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial: alloying and vanilla smelting inputs/outputs/energy pass; full automation/persistence/GUI coverage remains pending ([cycle 035](cycle-035.md)) |
| `PAINTING_MACHINE` | [EIOBlocks:391](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java#L391) | Partial. Forge verifies invalid saves, live paint removal, changing the palette in a paid saved job, advancement after delivery, native menu transfer and paid progress across restart. Placed and carried Forge 6 machines retain charge, progress, I/O, redstone and inventory. Resuming the migrated slab job spends its exact remaining FE and stacks with the imported output. Broader recipe families remain open. [Cycle 083](cycle-083.md), [cycle 085](cycle-085.md) |
| `WIRELESS_CHARGER` | [EIOBlocks](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial. A real TCP player receives the exact stored charge after upkeep. Adding and removing the advanced antenna changes the same player's eligibility, and a native menu button updates the saved range. Ordinary inventory and equipped Curios share a single budget. [Cycle 059](cycle-059.md), [cycle 060](cycle-060.md) |
| `STIRLING_GENERATOR` | [EIOBlocks](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial. Native capacitor, redstone and full-buffer states pass. Packaged hopper-fed sticks transfer exactly 2,960 FE. A running 6.2 generator keeps its energy, fuel and face settings through conversion and restart. The real TCP menu synchronizes coal, capacitor installation, burn, energy and redstone. See [cycle 058](cycle-058.md). |
| `SAG_MILL` | [EIOBlocks:368](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial. Native tests cover stone, copper ore and raw copper processing. Packaged Forge verifies exact iron, tin and osmium production and energy use, with outputs retained across restart. Grinding balls, bonus outputs, automation, saved jobs and the menu remain open. [Cycle 035](cycle-035.md), [Cycle 095](cycle-095.md) |
| `SLICE_AND_SPLICE` | [EIOBlocks:371](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial: test electrode recipe inputs/output/energy pass; full automation/persistence/GUI remain pending ([cycle 035](cycle-035.md)) |
| `IMPULSE_HOPPER` | [EIOBlocks:374](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `SOUL_BINDER` | [EIOBlocks:377](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial. Native recipe packet/category synchronization passes. The packaged Phantom solar recipe accepts hopper and sided fluid feeds, spends exactly 288,000 FE and 5,600 mB, and resumes after restart. Both outputs can be extracted by hopper. The guarded TCP menu shows the correct XP requirement and restored job, then lets a survival player shift-click the bound output. Additional recipes and 6.2 saves remain open. [Cycle 035](cycle-035.md), [Cycle 041](cycle-041.md), [Cycle 066](cycle-066.md) |
| `CRAFTER` | [EIOBlocks:412](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Packaged Forge verifies hopper feeds, exact cake and stick outputs, one charge per craft and a blocked single-item output. A live datapack reload updates the preview and subsequent crafted result. The native TCP menu verifies JEI transfers for a shaped recipe and a Forge recipe requiring tagged paper, manual ghost slots with JEI absent, and exact output and charge across restart. Survival mining, pickup and placement preserve a paid cake, three queued buckets, charge, template, capacitor and controls. A hopper exports the cake and buckets exactly once across two restarts. [Cycle 099](cycle-099.md), [cycle 100](cycle-100.md), [cycle 101](cycle-101.md) |
| `DRAIN` | [EIOBlocks:383](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `WIRED_CHARGER` | [EIOBlocks](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial. The last 60 FE finish and release a tagged staff. Historical buffers, tagged input and output, and the selected I/O and redstone modes survive conversion and restart. Native and TCP menu interactions cover shift-click transfers, equipped slots, the redstone control and progress synchronization. [Cycle 059](cycle-059.md), [Cycle 061](cycle-061.md) |
| `WIRELESS_CHARGER_ANTENNA` | [EIOBlocks:391](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `WIRELESS_CHARGER_ANTENNA_ADVANCED` | [EIOBlocks](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial. A real TCP player's staff starts charging at 20 blocks from the base when this antenna extends the charger to 48 blocks. Removing it restores the 16-block limit. [Cycle 059](cycle-059.md) |
| `CREATIVE_POWER` | [EIOBlocks:406](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `POWERED_SPAWNER` | [EIOBlocks](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial. Packaged clients load all eight facing and powered states and render both menu modes. A 6.2 machine resumes its saved FE job and spawns cows. The capture menu fills a cow vial with the exact budget and retains output after restart. A server datapack verifies blacklist and whitelist precedence and the copy recipe. A paid copy job waits for space through restart, then preserves the mob's name and health. Inventory Sorter leaves both capture slots untouched after sort and scroll commands. The nearby-spawner efficiency setting still needs runtime coverage. [Cycle 038](cycle-038.md), [cycle 068](cycle-068.md), [cycle 069](cycle-069.md), [cycle 103](cycle-103.md) |
| `MIND_KILLER` | [EIOBlocks:441](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial. Placing it above a packaged server's Powered Spawner gives copied cows no AI while they retain movement from applied velocity. Client rendering and ordinary crafting remain to be exercised. [Cycle 069](cycle-069.md) |
| `VACUUM_CHEST` | [EIOBlocks:423](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `XP_VACUUM` | [EIOBlocks:427](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `TRAVEL_ANCHOR` | [EIOBlocks:432](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial. Authentic 6.2 names, icons and visibility survive conversion and restart. A dedicated client reaches a hidden elevator and receives the correct separate Nether targets ([cycle 049](cycle-049.md)). |
| `PAINTED_TRAVEL_ANCHOR` | [EIOBlocks:436](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial. The migrated purpur anchor retains its paint and renamed copper alloy icon. Its menu and actual horizontal arrival pass over dedicated TCP before and after server restart ([cycle 049](cycle-049.md)). |
| `SOLAR_PANELS` | [EIOBlocks](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial. A mixed-tier array conserves solar production before and after actual chunk eviction. Native split/merge and both fragments' output pass. Packaged Phantom binding persists and follows a live rule reload. All three historical tiers retain their balances, and the restarted array delivers 18,500 FE across a chunk boundary. The packaged client checks all three item models and places a vibrant panel that joins a migrated panel. Weather, Nether liquid overrides and live Nether/End rule changes are covered. Survival mining, pickup and replacement preserve exact charge and the Phantom soul. A module made by the Soul Binder generates at midnight after actual TCP placement and a dedicated restart. [Cycle 059](cycle-059.md), [Cycle 061](cycle-061.md), [Cycle 062](cycle-062.md), [Cycle 066](cycle-066.md) |
| `CAPACITOR_BANKS` | [EIOBlocks:452](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial. A packaged array containing all three tiers conserves 7,000,000 FE across chunk reload, splitting and TCP survival replanting. The wired charger receives exactly 128,000 FE after restart. The multiplayer menu shows the exact totals and changes redstone mode across the array. A packaged 6.2 world retains all three tiers' charge, side settings, displays and redstone mode through conversion, restart and a retry from the isolated bank's original chunk. The basic bank updates its same-tier seam with Athena enabled and shows I/O markers in both client profiles. Other CTM layouts still need coverage. [Cycle 035](cycle-035.md), [Cycle 063](cycle-063.md), [Cycle 064](cycle-064.md), [Cycle 065](cycle-065.md) |
| `SOUL_ENGINE` | [EIOBlocks:487](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Native tests verify burn timing, redstone, capacitor, buffer space and fuel conservation when the binding changes. A 6.2 engine resumes its paid ticks. All six built-in rules accept the matching Forge fluid and save the exact generated charge across restart. The packaged generator follows a live reload and transfers exact FE to a wired charger. The TCP menu updates the recipe and multiplier, and packaged JEI replaces the synced Blaze rule and its fluid index on reload. [Cycle 070](cycle-070.md) · [Cycle 071](cycle-071.md) |
| `NIARD` | [EIOBlocks:499](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial. Package tests verify opt-in selection, hopper input, radius zero, independent redstone control and one water source per 1,500 FE and 1,000 mB. Saves preserve charge, tank, capacitor, radius and redstone mode across restart. The menu, crafting, other fluids and carried items remain open. [Cycle 097](cycle-097.md) |
| `VAT` | [EIOBlocks:475](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial: reagent ordering and full-output handling regressions pass; full progression/automation/persistence/GUI remain pending ([cycle 035](cycle-035.md)) |
| `BLOCK_DETECTOR` | [EIOBlocks:480](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial: neighbor-update regression passes; all configurations/render/restart behavior remain pending ([cycle 035](cycle-035.md)) |
| `FARMING_STATION` | [EIOBlocks:510](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java#L510) | Vanilla task coverage. Forge verifies seed conservation, capacitor gating, tall crops, tree recovery, tilling, wheat, pumpkin, pitcher plants, torchflowers, nether wart, and reloadable soul rules. Packaged multiplayer verifies survival carriage of full state, menu transfers, redstone and range. Crafting and factory reset remain to be exercised end to end. [Cycle 078](cycle-078.md), [cycle 079](cycle-079.md), [cycle 080](cycle-080.md), [cycle 081](cycle-081.md), [cycle 082](cycle-082.md) |
| `XP_OBELISK` | [EIOBlocks:515](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Native tests verify whole-point transfers, fractional fluid and tagged near-capacity deposits. The actual multiplayer menu passes all six buttons. A packaged Forge tank supplies tagged XP juice and the obelisk rejects water. Native and 6.2 survival placement retain 1,573 mB, tagged data, redstone and side settings. Erase crafting remains open. [Cycle 072](cycle-072.md) · [Cycle 077](cycle-077.md) |
| `INHIBITOR_OBELISK` | [EIOBlocks:518](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Native tests verify capacitor and upkeep, both teleport endpoints and the redstone gate. Packaged TCP verifies the menu and two real pearls after chunk reload. The protected landing blocks natural-spawn relocation. Charge, redstone mode, range, visibility and capacitor persist through survival replanting and restart. [Cycle 073](cycle-073.md) · [Cycle 075](cycle-075.md) · [Cycle 077](cycle-077.md) |
| `AVERSION_OBELISK` | [EIOBlocks:521](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Forge spawn finalization and insertion honor cause, tags, polarity, range changes and removal. Packaged TCP verifies controls, persistence and suppression of scheduler-spawned zombies while skeletons spawn normally. Native and 6.2 drops retain range, visibility, exact charge, redstone and the filter through survival placement. [Cycle 073](cycle-073.md) · [Cycle 074](cycle-074.md) · [Cycle 075](cycle-075.md) · [Cycle 077](cycle-077.md) |
| `RELOCATOR_OBELISK` | [EIOBlocks:524](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Packaged TCP verifies controls and saved settings, including a visible zero range and tagged filter through survival replanting. Scheduler-spawned zombies move across the chunk boundary onto solid ground after chunk eviction. Skeletons stay at their original positions. A sealed landing or Inhibitor field preserves the original zombie spawn. Other spawn placement types remain open. [Cycle 073](cycle-073.md) · [Cycle 075](cycle-075.md) · [Cycle 077](cycle-077.md) |
| `ATTRACTOR_OBELISK` | [EIOBlocks:527](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Native tests verify ground navigation, collision checks, target release, overlapping fields and Wither goal restoration. Packaged Forge verifies menus, Ghast pulls, Phantom and Slime decoy approach and release, and the Wither opt-in. Survival drops preserve charge, range, visibility, redstone, filter and capacitor across placement and restart. Mod-added bosses remain open. [Cycle 073](cycle-073.md) · [Cycle 076](cycle-076.md) · [Cycle 077](cycle-077.md) |
| `WEATHER_OBELISK` | [EIOBlocks:530](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | The packaged server pauses under a roof and resumes paid rain ticks after restart. Rain, thunder and clear each spend one bucket and one rocket and launch a firework. The multiplayer menu synchronizes fluid, rockets and progress. Survival replanting keeps tagged inputs and begins a new job. The tested rain job leaves 237 of 1,237 mB and one of two rockets. JEI remains open. [Cycle 072](cycle-072.md) · [Cycle 077](cycle-077.md) |
| `FLUID_TANK_ITEM` | [EIOBlocks:509](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial. Native tagged partial transfer passes. A carried 6.2 tank places with its tagged water, and a second transfers all 11,500 mB before placement and stays empty across restart. Both keep their machine settings ([cycle 036](cycle-036.md), [cycle 051](cycle-051.md)). |
| `PRESSURIZED_FLUID_TANK_ITEM` | [EIOBlocks:511](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial. A carried 6.2 tank transfers 1,000 mB of tagged XP Juice and places with exactly 20,750 mB and its original machine settings, confirmed again after restart ([cycle 051](cycle-051.md)). |
| `POWERED_SPAWNER_ITEM` | [EIOBlocks:539](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Packaged survival crafting transfers the Broken Spawner's cow soul and placement restores it. A 6.2 loot drop restores its cow soul, 877 FE, octadic capacitor, north side mode and redstone control through ordinary placement. [Cycle 068](cycle-068.md) |
| `SOUL_ENGINE_ITEM` | [EIOBlocks:542](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | The Soul Binder creates a Blaze-bound item and returns one empty vial. A native 6.2 drop survives ordinary survival placement with its soul, charge, capacitor, tagged lava, redstone, side mode and burn progress. The baseline shapeless erase recipe consumes one carried engine and returns one factory-state engine. The result retains that state through survival placement and restart. [Cycle 070](cycle-070.md) · [Cycle 071](cycle-071.md) |
| `SOLAR_PANEL_ITEMS` | [EIOBlocks:520](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Pending |
| `CAPACITOR_BANK_ITEMS` | [EIOBlocks:529](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial. A real 6.2 loot stack retains 78,912 FE and its face data, and a packaged charger adds exactly 10,001 FE. Native placement restores historical and recharged balances once. A real TCP survival player mines and picks up a basic bank with 500,000 FE and two custom side settings. Replanting consumes the item, restores the graph and synchronizes the sides. Both settings and the exact balance persist through restart. [Cycle 064](cycle-064.md), [Cycle 065](cycle-065.md) |
| `GLASS_BLOCKS` | [EIOBlocks:581](../../../enderio/src/main/java/com/enderio/enderio/init/EIOBlocks.java) | Partial. Datagen emits Athena Forge blockstates for all 714 glass variants. Native blue clear glass connects to matching facades, preserves tint and transparency, and updates during repaint and resource reload. Other families and glass-specific gameplay remain open. [Cycle 090](cycle-090.md) |

## EIOItems

Obtain/craft where applicable, render/tooltip, use, copy/stack, transfer and persist state.

| Declaration | Source | Evidence |
| --- | --- | --- |
| `ITEMS` | [EIOItems:45](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `CONDUCTIVE_ALLOY_INGOT` | [EIOItems:49](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Partial. The exact copper alloy ingot count in a chest survives migration and restart ([cycle 047](cycle-047.md)). |
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
| `BROKEN_SPAWNER` | [EIOItems:186](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Packaged native vanilla spawner loot produces the cow-bound item. A genuine 6.2 drop converts to typed soul data and binds the output of the native Powered Spawner recipe. [Cycle 068](cycle-068.md) |
| `GLIDER` | [EIOItems:194](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `ENDERIOS` | [EIOItems:205](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `SOUL_VIAL` | [EIOItems:221](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Partial. Survival capture and release retain a named cow and librarian, including their health and trades. Client state, filled model and glint synchronize. Dispenser capture and release pass, including a converted 6.2 vial. Server tags enforce the sheep blacklist and pig whitelist. The default setting releases the mob and preserves the vanilla spawner. The opt-in setting replaces its weighted mob pool and preserves the new type through two spawn cycles and restart. [Cycle 067](cycle-067.md), [cycle 069](cycle-069.md) |
| `VOID_VIAL` | [EIOItems:213](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `EXPERIENCE_ROD` | [EIOItems:214](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `YETA_WRENCH` | [EIOItems:215](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `COORDINATE_SELECTOR` | [EIOItems:216](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `LOCATION_PRINTOUT` | [EIOItems:217](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `LEVITATION_STAFF` | [EIOItems:218](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `TRAVEL_STAFF` | [EIOItems:219](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Partial. A genuine 6.2 charged staff retains its balance on conversion. Survival anchor travel, cooldown, crouch blink, exact energy accounting and dedicated restart pass. In dedicated multiplayer, the travel key uses an eligible equipped staff first and supports ordinary inventory without Curios. [Cycle 050](cycle-050.md), [cycle 060](cycle-060.md) |
| `ELECTROMAGNET` | [EIOItems:220](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Partial. The magnet key prioritizes equipped Curios, syncs the toggle and saves it across restart. It toggles the inventory magnet without Curios. Attraction, collection and active energy consumption still need gameplay coverage. [Cycle 060](cycle-060.md) |
| `COLD_FIRE_IGNITER` | [EIOItems:221](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `CONDUIT_PROBE` | [EIOItems:222](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `DARK_STEEL_SWORD` | [EIOItems:224](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `CONDUIT` | [EIOItems:233](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Partial. Six core and seven integration types from 6.2.18-beta retain their exact type, quantity and custom NBT in a vanilla inventory across upgrade and restart. The item type also migrates in an alloy smelter inventory. Native placement passes for all six core types. [Cycle 052](cycle-052.md) |
| `CONDUIT_FACADE` | [EIOItems:245](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java#L245) | Gold and red stained glass pass machine production, hopper recovery, survival installation, rendering, wrench visibility, loot and restart. The conduit remains visible through the glass before and after client resource reload. [Cycle 086](cycle-086.md), [cycle 087](cycle-087.md) |
| `TRANSPARENT_CONDUIT_FACADE` | [EIOItems:247](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java#L247) | Redstone and red stained glass pass the same lifecycle. A standalone facade accepts a conduit in survival. Matching glass facades cull both shared faces across a chunk boundary. Matching vanilla glass culls its facing quad in normal view and restores it while the wrench ghosts the facade. Athena connects painted clear-glass facades to each other and to ordinary clear glass, with correct tint and transparency during repaint and resource reload. These joins pass on Forge and Embeddium. [Cycle 086](cycle-086.md), [cycle 087](cycle-087.md), [cycle 088](cycle-088.md), [cycle 089](cycle-089.md), [cycle 090](cycle-090.md) |
| `HARDENED_CONDUIT_FACADE` | [EIOItems:249](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java#L249) | Diamond and blue stained glass pass the same lifecycle. The conduit remains visible through the glass before and after client resource reload. The baseline hardened flag and tooltip are retained. [Cycle 086](cycle-086.md), [cycle 087](cycle-087.md) |
| `TRANSPARENT_HARDENED_CONDUIT_FACADE` | [EIOItems:251](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java#L251) | Emerald and blue stained glass pass the same lifecycle. Both type flags persist and the glass hides its conduit until the player holds the wrench. [Cycle 086](cycle-086.md), [cycle 087](cycle-087.md) |
| `BASIC_ITEM_FILTER` | [EIOItems:252](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `BIG_ITEM_FILTER` | [EIOItems:253](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `ADVANCED_ITEM_FILTER` | [EIOItems:254](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `BIG_ADVANCED_ITEM_FILTER` | [EIOItems:256](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `LIMITED_ITEM_FILTER` | [EIOItems:258](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Pending |
| `BASIC_FLUID_FILTER` | [EIOItems:256](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | Partial: actual extraction/insertion filter items obey live allow/deny edits; existing saved NBT-match settings reject mismatches and preserve transferred fluid NBT. GUI and multiplayer editing remain pending ([cycle 037](cycle-037.md)) |
| `BASIC_SOUL_FILTER` | [EIOItems:276](../../../enderio/src/main/java/com/enderio/enderio/init/EIOItems.java) | A Forge 6.2 filter retains all five indexed slots, tag data, deny mode and tag comparison. The nine-slot multiplayer editor receives the tags and writes native slot and mode changes. A missing entity registration leaves the original legacy data recoverable. [Cycle 074](cycle-074.md) |
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
| `FLUID` | [EIOConduits:19](../../../enderio/src/main/java/com/enderio/enderio/init/EIOConduits.java) | Partial. Native tanks transfer 1,000 mB per five-tick update. Partial destinations share that source budget. Mixed paths, filters and channel/redstone changes pass. Rejected execution fluid survives retry, native callback and codec recovery, item drops and placement, waterlogging and native destruction. Packaged TCP uses both LaserIO filter slots and verifies type-only allowance, exact tagged-fluid denial and cross-chunk routing across restart. [Cycle 037](cycle-037.md), [cycle 042](cycle-042.md), [cycle 102](cycle-102.md) |
| `ENERGETIC_FLUID` | [EIOConduits:21](../../../enderio/src/main/java/com/enderio/enderio/init/EIOConduits.java) | Partial: pressurized tier transfers 5,000 mB per five-tick native update with exact source/destination accounting. Native upgrade into this tier preserves existing and carried rejected-fluid resources without duplicating the returned lower-tier item. Complete feature/disk-lifecycle/client evidence remains pending ([cycle 037](cycle-037.md), [cycle 042](cycle-042.md)) |
| `VIBRANT_FLUID` | [EIOConduits:23](../../../enderio/src/main/java/com/enderio/enderio/init/EIOConduits.java) | Partial: ender tier transfers all 32,000 mB available in the actual test tank (below its 40,000 mB update limit); mixed basic-tier path limits transfer to 1,000 mB. Complete feature/lifecycle/client evidence remains pending ([cycle 037](cycle-037.md)) |
| `ITEM` | [EIOConduits:25](../../../enderio/src/main/java/com/enderio/enderio/init/EIOConduits.java) | Partial. Native tests cover routing, priority and round robin. All four installed Forge 6.x upgrades retain their exact per-update rates after conversion, chunk reload and restart. Loose stacks keep their types and tags. The packaged integrated client installs and removes an upgrade through the real conduit menu. Packaged TCP removes and reinstalls a LaserIO Basic Filter and item card and verifies exact allowed and denied NBT and quantities across a chunk boundary and restart. [Cycle 055](cycle-055.md), [cycle 102](cycle-102.md) |
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
| `XP_JUICE` | [EIOFluids](../../../enderio/src/main/java/com/enderio/enderio/init/EIOFluids.java) | Partial. Tagged XP Juice survives the old fluid ID remap, tank conversion and restart ([cycle 047](cycle-047.md)). |
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
| `SAG_MILLING` | [EIORecipes:45](../../../enderio/src/main/java/com/enderio/enderio/init/EIORecipes.java) | Partial. Packaged Forge loads stock vanilla iron and Mekanism tin and osmium ingot recipes. Tin follows Almost Unified's preferred Forge output when present and a live datapack restriction to the recipe tag. Other optional materials and recipe display remain open. [Cycle 095](cycle-095.md) |
| `SLICING` | [EIORecipes:47](../../../enderio/src/main/java/com/enderio/enderio/init/EIORecipes.java) | Pending |
| `SOUL_BINDING` | [EIORecipes:49](../../../enderio/src/main/java/com/enderio/enderio/init/EIORecipes.java) | Partial: loaded native recipe packet and all mob-category values round-trip; malformed categories reject correctly. Packaged dedicated login/reconnect passes after the codec fix. Full recipe gameplay remains pending ([cycle 041](cycle-041.md)) |
| `TANK` | [EIORecipes:51](../../../enderio/src/main/java/com/enderio/enderio/init/EIORecipes.java) | Pending |
| `PAINTING` | [EIORecipes:56](../../../enderio/src/main/java/com/enderio/enderio/init/EIORecipes.java#L56) | Partial. Packaged Forge runs the fence, wooden slab and four facade recipes and verifies template consumption, reusable paint and output transfer. Fence and slab profiles verify advancement and job persistence. Other recipe families remain to be exercised. [Cycle 083](cycle-083.md), [cycle 085](cycle-085.md), [cycle 086](cycle-086.md) |
| `VAT_FERMENTING` | [EIORecipes:55](../../../enderio/src/main/java/com/enderio/enderio/init/EIORecipes.java) | Pending |
| `WEATHER_CHANGE` | [EIORecipes:57](../../../enderio/src/main/java/com/enderio/enderio/init/EIORecipes.java) | Pending |

## Optional modded conduits

| Feature | Required behavior | Evidence |
| --- | --- | --- |
| AE2 ME conduits | Channels, colored connections, network host lifecycle, split/merge, facades, save/restart and missing-AE2 loading | Partial. Native split/merge, bundle reload and an AE2 energy cell powering a drive pass. Packaged migration keeps powered normal, dense and mixed routes and their owners across chunk reload and server restart. The core packaged profile passes without AE2. [Cycle 053](cycle-053.md) |
| Mekanism chemical conduits and filters | Adapt the newer unified chemical API to all applicable 1.20.1 chemical types; transfer/filter/persist without mixing or losing contents | Partial. Native four-family transfer, filtering and retention pass. Packaged migration preserves all three placed tiers, connections, clean gas, infusion, pigment and slurry delivery, and both rejection and acceptance by a migrated hydrogen lock. [Cycle 054](cycle-054.md) |
| Mekanism heat conduits | Heat transfer, equilibrium, disconnect/reconnect, unload/reload | Partial. A packaged cross-chunk legacy route keeps its I/O directions, disabled face and redstone controls. A real native conductor warms after each pulse on initial conversion, chunk reload and restart. Equilibrium, toggle behavior and client verification remain open. [Cycle 054](cycle-054.md) |
| Refined Storage conduits | 1.x network integration, connectivity, split/merge, saved nodes and missing-RS loading | Partial. Native controller routing, split/reconnect and bundle reload pass. Packaged migration keeps the controller-to-drive route and both owners across chunk reload and server restart. The core packaged profile passes without Refined Storage. [Cycle 053](cycle-053.md) |

## Historical 6.2 save compatibility

| Feature | Verified behavior | Evidence |
| --- | --- | --- |
| Standard, inverted, wired and wireless lights | Six placed block IDs and six tagged items survive conversion, chunk eviction and restart. Owned light nodes persist across a chunk edge. Redstone toggling, wired energy use, node cleanup and single-player lighting updates pass. The six variants use the retained historical fixture geometry and vanilla lamp textures. | [Cycle 056](cycle-056.md) |
| Primitive alloy smelter | The original block and tagged item, remaining fuel and exact job progress survive packaged conversion, chunk eviction and restart with Ender IO or AE2 silicon. The queued output is produced once. All 47 historical recipes resolve. Native lava fuel returns a bucket, and the guarded client verifies inputs, progress and output collection. | [Cycle 057](cycle-057.md) |
| Painted slabs | A placed two-color slab and both native Forge 6 drops retain their colors through conversion and restart. Both drops render and replant as separate slab halves in survival. Modern loot returns one item per half. | [Cycle 084](cycle-084.md) |
| Painting Machine | A placed machine and an old machine item with an original slab in output both resume a 600-of-2,400 FE slab job. The carried machine preserves 1,800 FE, redstone, east pull, capacitor and every slot. Native hopper and menu transfer recover both matching slabs. The replanted machine, hopper and painted block retain their expected state after restart. | [Cycle 085](cycle-085.md) |

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

Cycle 040 verifies both tank variants in a packaged integrated-server world: fifteen checks each cover native menu/inventory/widget packets, exact bucket conservation, fluid synchronization/rendering and save/reopen. These thirty client checks are separate from the 78 unit/383 native GameTest results. Full tank operations, the remaining feature families and dedicated TCP multiplayer remain unverified. See [cycle 040](cycle-040.md).

Cycle 041 adds real packaged dedicated-server TCP login, standard-tank menu/inventory/widget packets, server-to-client fluid updates, reconnect and a new client after full server restart. Nineteen client checks and five native-console assertions pass. The first login exposed a soul-binding category codec cast defect; three native recipe tests reproduce it and pass after the fix. Hidden/silent observers pass. This does not complete the remaining feature matrix, integration or transfer-conservation requirements. See [cycle 041](cycle-041.md).

Cycle 042 adds eleven actual native fluid-conservation and lifecycle cases, including optimistic simulation/partial-or-zero execution against output-only sources, tagged retained resources, node/block recovery, native placement/upgrades, waterlogging and single/mixed bundle drops. Full build passes 78 ordinary unit/397 native results; the packaged TCP tank regression passes on the same distributable bytes. Chemical execution remainders and actual retained-conduit disk restarts/topology transitions remain unresolved. See [cycle 042](cycle-042.md).

Cycle 043 conserves Mekanism gas, infusion, pigment and slurry after partial or total destination rejection. Eight added native cases verify exact retries, filtering, redstone, split/merge, reload callbacks, upgrades and dropped-item placement with long amounts. The packaged server without optional mods passes 16 assertions. A live conduit import on the packaged Mekanism server exposes missing graph nodes and a chunk-save failure before restart. Disk-backed conduit recovery and other integration behavior remain open. See [cycle 043](cycle-043.md).

Cycle 044 fixes attached bundle imports and verifies mixed fluid/Mekanism chemical resource state across actual chunk eviction and two packaged server restarts. The test confirms 750 mB tagged water and 4,294,967,297 units of oxygen survive in the bundle and in exactly one item each after native destruction. Five added native tests cover import/rollback, graph identity/removal and disabling an existing link. Wider topology and dedicated-client conduit interaction remain open. See [cycle 044](cycle-044.md).

Cycle 046 verifies packaged TCP data-map synchronization for grinding balls, VAT reagents and range extenders across initial login, two actual server datapack reloads, reconnect and restart. Native tooltip events show changed flint values and charcoal addition/removal. Logout clears the old snapshot. Full machine and JEI behavior, large TCP snapshots and an intermittent handshake timeout remain open. See [cycle 046](cycle-046.md).
