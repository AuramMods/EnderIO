# Ender IO Guide coverage

The English Patchouli book is `enderio:guide` on Minecraft 1.20.1. Use the [feature matrix](features.md) to account for ported features, and check instructions against the current registrations, recipes, and default configs. Core entries must be usable with Patchouli alone.

| Chapter | Coverage to verify | Status |
| --- | --- | --- |
| Getting Started | Guide craft, fire and grains, starter parts, powered machines, conductive alloy and binders, first energy line | Six entries authored |
| Power and Storage | Stirling fuel, capacitors, photovoltaics, banks, wired and wireless charging, energy tiers and sided transfer | Six entries authored |
| Conduits and Filters | Placing and splitting bundles, item and fluid movement, redstone signals, upgrades and filters, facades, wrench and probe, worked networks | Nine entries authored |
| Workshop Machines | Alloys and powders, mills and grinding balls, Slice’N’Splice, VAT, tanks, painting, Enchanter, Crafter and hoppers, farming and collection, weather and redstone controls | Twelve entries authored |
| Souls and Obelisks | Vials, Soul Binder, soul components and fuels, Powered Spawner, XP, attraction, aversion, relocation and teleport inhibition | Pending |
| Tools, Travel and Building | Travel blocks and staff, gliders and mobility, magnets, XP equipment and enchantments, utility and mobility items, painted and steel construction, fused glass, hidden redstone | Pending |
| Integrations and Experiments | AE2, Mekanism, RS, LaserIO, CC:Tweaked, Jade, Curios, FTB Ultimine, JEI, Enderface and Niard settings and dependencies | Pending |
| Troubleshooting | Energy, blocked outputs, connection sides, channels and redstone, recipe tags and optional dependencies | Pending |

## Cycle 001

- Replaced the placeholder entries with a six-entry starter progression and generated the English title and landing text. Added a shapeless book-and-redstone recipe gated on Patchouli. Existing books retain their ID and NBT.
- Java 17 data generation and Forge 47.4.10 assembly passed. The reobfuscated jar contains one category, six entries, 14 pages, eight recipe references and five entry links. The book resources matched the source JSON, both title keys resolved in the packaged English file, and all linked entries and recipes were present.
- The candidate booted and stopped cleanly in disposable Forge 47.4.10 dedicated servers with and without Patchouli 1.20.1-84.1-forge. The build, packaged resource assertions and startup report are in `build/port-evidence/guide-cycle-001/`.
- Next verify the actual survival craft, tooltip and starter pages in the guarded client, then document power and storage. The visual review, resource reload, JEI profile and Auram deployment are pending.

## Cycle 002

- Added Power and Storage with six entries, including a worked bank buffer, photovoltaic plates, antenna range and the difference between accessible bank faces and faces that push power. Added a recipe-backed furnace page for the starter binder. The shipped book has 12 entries, 46 pages, 26 live recipe references and ten entry links.
- Added `scripts/porting/packaged_enderio_guide.py` to craft the guide in a real survival inventory and visit each entry through Patchouli’s UI. It checks the translated tooltip and landing text, active recipe cards, page layout, an inline link, and reopening after resource reload. Both dedicated peers use the same artifacts.
- The Patchouli-only run passed 147 client checks and four server assertions. Image review found several long recipe headings, which now use shorter titles. The subsequent Patchouli and JEI 15.20.0.129 run passed 177 client checks and four server assertions, with 31 captures and no runtime errors. Both runs recorded zero activation, foreground or onscreen samples and verified muted sound, narrator off and no cursor capture. See `build/port-evidence/guide-cycle-002-power-final/` and `build/port-evidence/guide-cycle-002-power-jei/`.
- A fresh survival player received no Ender IO guide in the stock profile. Auram separately stops including the guide in its first-login tome while retaining it in the crafted tome. The CraftTweaker 14.0.59 run verified both tome variants and checked that the starter still contains its Botania entry. See Auram commit `73779d1` and `build/port-runtime/guide-cycle-002-auram-tome-retry/logs/crafttweaker.log`.
- Next exercise the conduit chapter, then add machine processing.

## Cycle 003

- Added nine conduit entries covering four resource types, channels and priority, item and fluid filters, logic and timing, upgrades, probe copying and painted facades. The examples include a chest-to-barrel line, tank transfer, and a lever-controlled item route.
- The reobfuscated jar contains three categories, 21 entries, 98 pages, 53 recipe references and 17 entry links. The packaged book runner with Patchouli alone passed 342 client checks and four dedicated server assertions, with 60 captures, exact matching artifacts, and a successful reload. Reviewed the chapter menu and representative filter layouts. The window observer recorded 2,957 samples and zero activation, foreground or onscreen observations. See `build/port-evidence/guide-cycle-003-conduits/`.
- A real first login with Auram’s updated script saved a 40-book starter tome with its other entries and no Ender IO guide. The fixture’s client exited after login because its selected Terramity install lacked Caelus. Forge saved the inventory and stopped cleanly. See `build/port-runtime/guide-cycle-002-auram-login/first-login-inventory.json`. The independent Patchouli and JEI guide profiles completed successfully.
- Next document alloying, grinding, slicing, fluid processing and the other workshop machines.

## Cycle 004

- Documented 12 workshop entries with live machine crafts and worked examples for alloying, milling, slicing, the VAT, tanks, painting, enchanting, crafting and batching. Included the legacy fuel-fired smelter, farming, collection, weather and block sensing.
- The reobfuscated jar contains four categories, 33 entries, 162 pages, 71 live recipe references and 24 entry links. The guarded Patchouli-only tour passed 526 client checks and four server assertions with 96 captures. It exercised the second menu spread for Workshop Machines. Reviewed both chapter spreads and representative VAT and Slicer layouts. See `build/port-evidence/guide-cycle-004-machines/`.
- Next verify the soul-binding recipes, Powered Spawner, Soul Engine and obelisk entries.
