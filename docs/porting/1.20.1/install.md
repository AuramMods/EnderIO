# Ender IO for Minecraft Forge 1.20.1

The port targets Minecraft 1.20.1, Forge 47.4.28 and Java 17. The tested Java distribution is Temurin 17.0.18. The core profile uses one Ender IO runtime jar. Its nested archives include EnderCore, Modded Conduits and GraphLib3 3.0.5. [Packaging evidence](cycle-251.md)

## Install the runtime jar

1. Create a Minecraft 1.20.1 instance with Forge 47.4.28 and configure its launcher to use Java 17. For a dedicated server, use the pinned Forge installer, then use its generated startup script.
2. Put the main reobfuscated `enderio-<version>.jar` in the instance's `mods` directory. If upgrading an Ender IO installation, move the previous Ender IO jar to the backup first. The API, sources, standalone submodule and QA archives serve the build and tests.
3. On a dedicated server, use matching Ender IO bytes on the server and client. Install compatible optional mods on the sides they require. Start a disposable world and confirm that Ender IO content loads before upgrading any world you use.

The [dependency table](dependencies.md) lists exact versions and artifact coordinates. Install AE2 15.4.12 with GuideME 20.1.15 on both sides to use ME Conduits. Install Mekanism 10.4.16.80 or Refined Storage 1.12.4 for their respective conduits. Use Patchouli 1.20.1-84.1-forge alongside LaserIO 1.6.8 for LaserIO 101. For the tested shader profile, use Embeddium 0.3.31+mc1.20.1 and Oculus 1.20.1-1.8.0 on the client. Athena 3.1.2 and the built-in Athena resource pack enable the tested connected textures.

Enderface and Niard are experimental. Select their respective built-in packs when creating the world. The dedicated fixtures enable them through `initial-enabled-packs` with `mod/enderio:data/enderio/datapacks/enderface` or `mod/enderio:data/enderio/datapacks/niard`. The Farming Station is available in a default world. [Experiment selection and defaults](cycle-097.md), [Enderface](cycle-098.md), [Farming Station](cycle-212.md)

## Build and verify

Run from the port checkout with Java 17 active. The wrapper pins Gradle 9.8.2 and the version catalog pins Forge and the optional build dependencies. Preserve the checkout and source revision when comparing jars because the archive version embeds the Git revision and branch.

```sh
./gradlew build --continue
./gradlew :enderio:runData :enderio-modded-conduits:runData
./gradlew :enderio:reobfJar
```

The runtime archive is in `enderio/build/libs`. To run the unit and native GameTest profiles without assembling every artifact, use `./gradlew check --continue`. The module `build/test-results` directories contain XML reports. Main gameplay coverage has a separate discovery list and an execution check. [Packaging comparison](cycle-251.md), [Main gameplay test suite](../../../enderio/src/gametest/README.md), [Registry test suite](../../../enderio/src/registryTest/README.md)

Use the [packaged server harness](packaged-server.md) for a clean dedicated Forge instance and the [guarded client harness](client-harness.md) for the matching client and TCP scenarios. The macOS guard verifies focus and window state independently of the game and keeps the isolated client's volumes and narrator off. The harness documentation gives the commands and evidence locations.

## Upgrade a saved world

The 6.2.18-beta fixtures on Minecraft 1.20.1 Forge cover placed and carried machines, conduit bundles and filters, powered addon networks, and several historical items and enchantments. Copy-based tests of 6.2.0-beta also verify mixed conduit bundles, item and fluid filters, tagged tanks, renamed blocks, Alloy Smelter settings and a running Stirling Generator. Verify other 6.x releases on their own copies. Keep Minecraft 1.21.1 worlds on their existing Minecraft version. [Historical fixture coverage](features.md#historical-62-save-compatibility), [Early Forge 6.2 conversion commands](cycle-268.md)

1. Stop the game or server. Back up the entire world directory and the associated instance configuration and mods. Work on a separate copy. Keep the original copy available with its original jar set for rollback.
2. Keep the world on Minecraft 1.20.1. Install the candidate Ender IO jar and the matching optional mods referenced by the save before its first load. Include the world's `serverconfig`, enabled datapacks and dimension data when copying it.
3. Open the copy and review the first-load logs. Verify representative inventories, running machines, conduit routes and stored resources in the world. Shut it down cleanly and reopen the copy to confirm they persist. Some recorded Forge 6.2 fixtures report first-load legacy ID remapping while valid content is recovered. Compare concerning IDs and saved behavior against the relevant fixture.
4. To revert, use the original backup with its original Forge instance and jar set.

For a SAG Mill with an active custom Forge 6.2 grinding ball, include the original datapack that defines its recipe. The port uses the saved ID and wear to capture the original modifiers on load. If the definition is absent, loading the mill after returning the datapack recovers the retained record. Modern item data maps take priority for balls inserted into the mill after the upgrade. [Custom grinding ball migration](cycle-259.md)

Keep the original enabled datapack when upgrading a world with an in-progress custom Soul Binder recipe. The port reads the Forge 6.2 `exp` and `souldata` fields and resolves the saved job by its original recipe ID. [Soul Binder migration](cycle-260.md)

The [feature evidence matrix](features.md) identifies the state exercised by each fixture. The [completion audit](completion-audit.md) tracks the remaining verification work and the tested candidate artifacts.
