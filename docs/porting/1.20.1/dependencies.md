# Target dependency selection

Selected on 2026-11-17. Artifact coordinates are pinned in `gradle/libs.versions.toml`. Version availability was checked against publisher Maven metadata or the project's Modrinth/CurseForge release record. Selection is not evidence of working integration; all runtime results remain pending.

| Dependency | Target release | Artifact selection | Runtime evidence |
| --- | --- | --- | --- |
| Forge | 47.4.28 | `net.minecraftforge:forge:1.20.1-47.4.28` through MDG legacy; recommended Forge release | Pending |
| JEI | 15.56.0.205 | Modrinth `jei:9jqubC9n` | Pending |
| CC:Tweaked | 1.121.0 | Modrinth `cc-tweaked:Qm9iIIEY` | Pending |
| Jade | 11.13.3+forge | Modrinth `jade:xJQHCmWJ` | Pending |
| Athena | 3.1.2 | Modrinth `athena-ctm:DULOQFj7` | Pending |
| AE2 | 15.4.12 | Modrinth `ae2:QRpVsqDI` | Pending |
| GuideME (AE2 dependency) | 20.1.15 | Modrinth `guideme:i7Tp1AHw` | Pending |
| Mekanism | 10.4.16.80 | Modrinth `mekanism:uxe1WQp4` | Pending |
| Refined Storage | 1.12.4 | Modrinth `refined-storage:ZITLFjjf` | Pending |
| LaserIO | 1.6.8 | Curse Maven `laserio-626839:5074767` | Pending |
| Almost Unified | 1.20.1-0.10.1 | `com.almostreliable.mods:almostunified-forge` | Pending |
| FTB Ultimine | 2001.1.8 | `dev.ftb.mods:ftb-ultimine-forge` | Pending |
| FTB Library | 2001.2.13 | `dev.ftb.mods:ftb-library-forge` | Pending |
| Architectury | 9.2.14+forge | Modrinth `architectury-api:1MKTLiiG` | Pending |
| Curios | 5.14.1+1.20.1 | Modrinth `curios:IPQlZkz1` | Pending |
| Embeddium | 0.3.31+mc1.20.1 | Modrinth `embeddium:UTbfe5d1`; renderer compatibility profile | Pending |
| Oculus | 1.20.1-1.8.0 | Modrinth `oculus:iQ1SwGc3`; shader compatibility profile | Pending |
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
