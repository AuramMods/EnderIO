# Ender IO Guide coverage

The English Patchouli book is `enderio:guide` on Minecraft 1.20.1. Use the [feature matrix](features.md) to account for ported features, and check instructions against the current registrations, recipes, and default configs. Core entries must be usable with Patchouli alone.

| Chapter | Coverage to verify | Status |
| --- | --- | --- |
| Getting Started | Guide craft, fire and grains, starter parts, powered machines, conductive alloy and binders, first energy line | Six entries authored |
| Power and Storage | Stirling fuel, capacitors, photovoltaics, banks, wired and wireless charging, energy tiers and sided transfer | Pending |
| Conduits and Filters | Placing and splitting bundles, item and fluid movement, redstone signals, upgrades and filters, facades, wrench and probe, worked networks | Pending |
| Machines and Processing | Alloys and powders, mills and grinding balls, Slice’N’Splice, VAT, tanks and reservoirs, painting, Enchanter, Crafter, sorting and hoppers, farming and collection, weather and redstone controls | Pending |
| Souls and Obelisks | Vials, Soul Binder, soul components and fuels, Powered Spawner, XP, attraction, aversion, relocation and teleport inhibition | Pending |
| Tools, Travel and Building | Travel blocks and staff, gliders and mobility, magnets, XP equipment and enchantments, utility and mobility items, painted and steel construction, fused glass, hidden redstone | Pending |
| Integrations and Experiments | AE2, Mekanism, RS, LaserIO, CC:Tweaked, Jade, Curios, FTB Ultimine, JEI, Enderface and Niard settings and dependencies | Pending |
| Troubleshooting | Energy, blocked outputs, connection sides, channels and redstone, recipe tags and optional dependencies | Pending |

## Cycle 001

- Replaced the placeholder entries with a six-entry starter progression and generated the English title and landing text. Added a shapeless book-and-redstone recipe gated on Patchouli. Existing books retain their ID and NBT.
- Java 17 data generation and Forge 47.4.10 assembly passed. The reobfuscated jar contains one category, six entries, 14 pages, eight recipe references and five entry links. The book resources matched the source JSON, both title keys resolved in the packaged English file, and all linked entries and recipes were present.
- The candidate booted and stopped cleanly in disposable Forge 47.4.10 dedicated servers with and without Patchouli 1.20.1-84.1-forge. The build, packaged resource assertions and startup report are in `build/port-evidence/guide-cycle-001/`.
- Next verify the actual survival craft, tooltip and starter pages in the guarded client, then document power and storage. The visual review, resource reload, JEI profile and Auram deployment are pending.
