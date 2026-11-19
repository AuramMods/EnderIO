# Target dependency selection

Selected on 2026-11-17. Artifact coordinates are pinned in `gradle/libs.versions.toml`. Version availability was checked against publisher Maven metadata or the project's Modrinth/CurseForge release record. Selection alone is not evidence of working integration; the runtime column records verified subsets and remaining work.

| Dependency | Target release | Artifact selection | Runtime evidence |
| --- | --- | --- | --- |
| Forge | 47.4.28 | `net.minecraftforge:forge:1.20.1-47.4.28` through MDG legacy; recommended Forge release | Pending |
| JEI | 15.56.0.205 | Modrinth `jei:9jqubC9n` | A packaged multiplayer client displays all six Soul Engine rules and finds them by Forge fluid, spawn egg and filled Soul Vial. Reloading the server datapack replaces the Blaze rule in the viewer and its ingredient index. Other categories, recipe transfers and overlays still need coverage. [Cycle 071](cycle-071.md) |
| CC:Tweaked | 1.121.0 | Modrinth `cc-tweaked:Qm9iIIEY` | Pending |
| Jade | 11.13.3+forge | Modrinth `jade:xJQHCmWJ` | Pending |
| Athena | 3.1.2 | Modrinth `athena-ctm:DULOQFj7` | Packaged Forge multiplayer loads the built-in pack on the client. The basic bank's same-tier seam updates across break and replant, and the disabled and pull markers share the model in its cutout layer. The core profile uses the vanilla bank face. Other CTM layouts and renderer combinations remain pending ([cycle 065](cycle-065.md)). |
| AE2 | 15.4.12 | Modrinth `ae2:QRpVsqDI` | Native grid split/merge, bundle reload and powering an AE2 drive pass. Packaged migration verifies powered normal, dense and mixed routes and their owners. Detailed channel limits, storage operations and the client remain pending ([cycle 053](cycle-053.md)) |
| GuideME (AE2 dependency) | 20.1.15 | Modrinth `guideme:i7Tp1AHw` | Loads alongside the pinned AE2 jar in both packaged migration releases. Guide UI remains pending ([cycle 053](cycle-053.md)) |
| Mekanism | 10.4.16.80 | Modrinth `mekanism:uxe1WQp4` | Native four-family transfer, budgets and filters pass. Packaged legacy upgrades preserve placed chemical tiers, a gas lock and a working directional heat route across chunk reload and restart. Broader gameplay and the client remain pending ([cycle 054](cycle-054.md)) |
| Refined Storage | 1.12.4 | Modrinth `refined-storage:ZITLFjjf` | Native controller routing, split/reconnect, sides and bundle reload pass. Packaged migration keeps a controller-to-drive route and owners across chunk reload and restart. Storage operations and the client remain pending ([cycle 053](cycle-053.md)) |
| LaserIO | 1.6.8 | Curse Maven `laserio-626839:5074767` | Native book recipe passes with Patchouli; card/filter gameplay pending |
| Patchouli (LaserIO book) | 1.20.1-84.1-forge | Modrinth `patchouli:8Qyw08ld` | Native LaserIO book matching/assembly passes; book UI pending |
| Almost Unified | 1.20.1-0.10.1 | `com.almostreliable.mods:almostunified-forge` | Pending |
| FTB Ultimine | 2001.1.8 | `dev.ftb.mods:ftb-ultimine-forge` | Pending |
| FTB Library | 2001.2.13 | `dev.ftb.mods:ftb-library-forge` | Pending |
| Architectury | 9.2.14+forge | Modrinth `architectury-api:1MKTLiiG` | Pending |
| Curios | 5.14.1+1.20.1 | Modrinth `curios:IPQlZkz1` | Packaged TCP verifies wireless charging, both Ender IO keybindings, client sync and restart persistence with configured regular slots. The same inventory paths pass without Curios. [Cycle 060](cycle-060.md) |
| Embeddium | 0.3.31+mc1.20.1 | Modrinth `embeddium:UTbfe5d1`; compile-only mesh-appender API and optional renderer profile | Packaged TCP client renders four painted conduit facade variants across a chunk boundary, exposes the conduits when the Yeta Wrench is held and restores the opaque covers when it is put away. Standard Forge passes the same fixture without Embeddium. Other layers and shader combinations remain open. [Cycle 086](cycle-086.md) |
| Oculus | 1.20.1-1.8.0 | Modrinth `oculus:iQ1SwGc3`; shader compatibility profile | Pending; Forge mod-ID detection corrected in cycle 022 |
| GraphLib3 | 3.0.5 | Existing `dev.gigaherz.graph:GraphLib3` bundled library | Pending |

Inventory Sorter hooks still need a target-mod test profile. Optional dependency loading, metadata dependencies inside downloaded jars (including nested jars), default/minimal/all-integrations profiles and API changes still require validation. The standard Gradle dependency report resolves the dependency graph; it does not prove all remapped jar files have been downloaded or that the APIs compile.

ModDevGradle's `modCompileOnly`, `modRuntimeOnly`, and custom `modLocalRuntime` configurations remap Forge SRG artifacts to named mappings. These configurations do not pull transitive mod dependencies automatically, so required integration dependencies must be explicitly supplied. EnderCore, modded conduits and GraphLib remain bundled as in the source baseline; the final production jar nesting/remapping must be tested.

Sources:

- [MDG legacy Forge documentation](https://github.com/neoforged/ModDevGradle/blob/main/LEGACY.md).
- [Forge 1.20.1 releases](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html).
- [Modrinth project version API](https://docs.modrinth.com/api/operations/getprojectversions/) queried with `game_versions=["1.20.1"]` and `loaders=["forge"]`; exact release IDs are recorded above.
- [LaserIO 1.6.8](https://www.curseforge.com/minecraft/mc-mods/laserio/files/5074767).
- [Almost Unified Maven metadata](https://maven.blamejared.com/com/almostreliable/mods/almostunified-forge/maven-metadata.xml).
- [FTB Ultimine Maven metadata](https://maven.ftb.dev/releases/dev/ftb/mods/ftb-ultimine-forge/maven-metadata.xml).
- [FTB Library Maven metadata](https://maven.ftb.dev/releases/dev/ftb/mods/ftb-library-forge/maven-metadata.xml).

Cycle 032 loads pinned AE2, Mekanism and Refined Storage with the actual mod in a native headless development server and passes six addon state tests; seven isolated Mekanism tests also pass. These are partial integration checks, not complete gameplay certification. Datagen and Java compilation succeed. LaserIO's book recipe currently reports a missing Patchouli serializer, and Embeddium reports a datagen-only fingerprint error; resolve these while retaining the supported integrations. See [cycle 032](cycle-032.md).

Cycle 033 resolves LaserIO's missing book serializer with pinned [Patchouli 1.20.1-84.1-forge](https://modrinth.com/mod/patchouli/version/8Qyw08ld), and verifies real AE2/RS networks and native Mekanism tank transfers. Datagen uses isolated serial-loading profiles after an observed AE2/Forge key-mapping race; normal gameplay profiles retain their loading settings. See [cycle 033](cycle-033.md) for precise coverage and outstanding work.
