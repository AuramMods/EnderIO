plugins {
    id("mod-common-conventions")
}

val gameTestSourceSet = sourceSets.create("gametest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations[gameTestSourceSet.implementationConfigurationName].extendsFrom(configurations.implementation.get())

// Exercise the actual standalone main-mod state classes while the rest of the port
// still cannot compile. These sources enter only the test output, never EnderCore's jar.
tasks.named<JavaCompile>(gameTestSourceSet.compileJavaTaskName) {
    val mainSource = rootProject.file("enderio/src/main/java/com/enderio/enderio")
    source(listOf(
        "api/soul/Soul.java",
        "api/attachment/CoordinateSelection.java",
        "api/EnderIOAPI.java",
        "foundation/MachineRecipe.java",
        "api/io/IOConfigurable.java",
        "api/io/IOMode.java",
        "api/io/RedstoneControl.java",
        "foundation/io/IOConfig.java",
        "foundation/io/LegacyMachineData.java",
        "api/io/SideConfig.java",
        "foundation/io/SidedIOConfigurable.java",
        "foundation/io/TransferUtil.java",
        "foundation/block/EIOBlockEntity.java",
        "foundation/block/EIOEntityBlock.java",
        "foundation/block/MachineBlock.java",
        "foundation/block/ProgressMachineBlock.java",
        "foundation/block/MachineWrench.java",
        "foundation/block/WrenchableBlockHandler.java",
        "foundation/block/entity/MachineBlockEntity.java",
        "foundation/block/entity/PoweredMachineBlockEntity.java",
        "foundation/block/entity/MachineInstallable.java",
        "foundation/block/entity/flags/CapacitorSupport.java",
        "foundation/energy/PoweredMachineEnergyStorage.java",
        "content/capacitors/CapacitorItem.java",
        "api/capacitor/CapacitorScalable.java",
        "api/capacitor/QuadraticScalable.java",
        "api/capacitor/FixedScalable.java",
        "foundation/block/entity/Wrenchable.java",
        "content/tools/YetaWrenchItem.java",
        "content/misc_blocks/ResettingLeverBlock.java",
        "api/io/energy/EnergyIOMode.java",
        "api/capacitor/CapacitorData.java",
        "api/capacitor/CapacitorModifier.java",
        "api/capacitor/ICapacitorExtension.java",
        "foundation/EIONBTKeys.java",
        "foundation/MachineNBTKeys.java",
        "foundation/state/MachineState.java",
        "foundation/state/MachineStateType.java",
        "content/machines/MachinesLang.java",
        "foundation/lang/EIOCommonLang.java",
        "foundation/inventory/MachineInventory.java",
        "foundation/inventory/MachineInventoryLayout.java",
        "foundation/inventory/MultiSlotAccess.java",
        "foundation/inventory/SingleSlotAccess.java",
        "foundation/block/entity/MachineInventoryHolder.java",
        "foundation/attachment/FluidTankUser.java",
        "foundation/io/fluid/MachineFluidHandler.java",
        "foundation/io/fluid/MachineFluidTank.java",
        "foundation/io/fluid/MachineTankLayout.java",
        "foundation/io/fluid/TankAccess.java",
        "foundation/io/energy/IMachineEnergyStorage.java",
        "foundation/io/energy/MachineEnergyStorage.java",
        "foundation/task/MachineTask.java",
        "foundation/task/PoweredMachineTask.java",
        "foundation/task/CraftingMachineTask.java",
        "foundation/task/PoweredCraftingMachineTask.java",
        "foundation/task/host/MachineTaskHost.java",
        "foundation/task/host/CraftingMachineTaskHost.java",
        "foundation/recipe/RecipeInputCache.java",
        "foundation/util/ValidatingListCodec.java",
        "content/machines/alloy/AlloySmeltingRecipe.java",
        "content/machines/alloy/SmeltingRecipeInheritance.java",
        "content/machines/slicer/SlicerRecipeManager.java",
        "content/machines/slicer/SlicingRecipe.java",
        "content/fire_crafting/FireCraftingRecipe.java",
        "foundation/datamap/RangeExtender.java",
        "foundation/datamap/VatReagent.java",
        "api/components/GrindingBallData.java",
        "content/machines/vat/FermentingRecipe.java",
        "content/broken_spawner/BrokenSpawnerLootModifier.java",
        "content/capacitors/SetLootCapacitorFunction.java",
        "content/paint/BlockPaintData.java",
        "content/paint/CopyPaintFunction.java",
        "content/paint/PaintingTrigger.java",
        "content/advancements/AdvancementsLang.java",
        "content/tools/vials/SoulVialItem.java",
        "content/tools/ToolsLang.java",
        "foundation/util/EntityCaptureUtils.java",
        "api/soul/SoulBoundUtils.java",
        "foundation/soul/SoulCapabilityProviders.java",
        "content/paint/block/entity/PaintedBlockEntity.java",
        "content/tools/hang_glider/UseGliderTrigger.java",
        "init/EIOLootModifiers.java",
        "init/EIOCriterions.java",
        "config/base/BaseConfig.java",
        "config/base/client/BaseClientConfig.java",
        "config/base/common/BaseCommonConfig.java",
        "config/base/common/BlocksConfig.java",
        "config/base/common/InfinityConfig.java",
        "config/base/common/ItemsConfig.java",
        "content/machines/obelisks/weather/WeatherChangeRecipe.java",
        "api/filter/ItemFilter.java",
        "api/filter/ItemFilterContext.java",
        "api/filter/ItemFilterDirection.java",
        "api/filter/FluidFilter.java",
        "content/filters/item/general/DamageFilterMode.java",
        "content/filters/item/general/EnderItemFilter.java",
        "content/filters/item/existing/ExistingItemFilter.java",
        "content/filters/item/limited/LimitedItemFilter.java",
        "content/filters/item/mod_id/ModIdItemFilter.java",
        "content/filters/fluid/EnderFluidFilter.java",
        "foundation/network/packets/ServerboundTimerFilterPacket.java",
        "foundation/network/packets/ServerboundTransferItemsPacket.java",
        "foundation/network/packets/ServerboundUpdateCrafterTemplatePacket.java",
        "api/soul/binding/SoulBindable.java",
        "api/soul/binding/ComponentSoulBindable.java",
        "api/soul/binding/ReadOnlyComponentSoulBindable.java",
        "api/soul/storage/SoulHandler.java",
        "api/soul/storage/SoulHandlerModifiable.java",
        "api/soul/storage/SingleComponentSoulHandler.java",
        "content/filters/item/ItemFilterUtils.java",
        "compat/vanilla/SpawnEggSoulBindable.java",
        "compat/vanilla/SpawnEggSoulHandler.java"
    ).map { mainSource.resolve(it) })
    source(rootProject.file("enderio/src/datagen/java/com/enderio/enderio/datagen/common/loot/EIOLootModifiersProvider.java"))
    source(rootProject.file("enderio/src/datagen/java/com/enderio/enderio/datagen/common/advancement/EIOAdvancementGenerator.java"))
    source(rootProject.file("enderio/src/datagen/java/com/enderio/enderio/datagen/common/tags/EIOEntityTagsProvider.java"))
}


// CPU-only model regression tests use the real main-mod helper and native Forge
// quad builder. They never initialize Minecraft or create a display, and the
// bridged sources remain confined to test output.
tasks.named<JavaCompile>(sourceSets.test.get().compileJavaTaskName) {
    source(rootProject.file("enderio/src/main/java/com/enderio/enderio/client/foundation/model/ModelRenderUtil.java"))
    source(rootProject.file("enderio/src/test/java/com/enderio/enderio/client/ModelRenderUtilTests.java"))
    source(rootProject.file("enderio/src/main/java/com/enderio/enderio/client/foundation/renderer/PreviewLayerBuffer.java"))
    source(rootProject.file("enderio/src/test/java/com/enderio/enderio/client/PreviewLayerBufferTests.java"))
    source(rootProject.file("enderio/src/main/java/com/enderio/enderio/client/foundation/renderer/FixedAlphaVertexConsumer.java"))
    source(rootProject.file("enderio/src/test/java/com/enderio/enderio/client/FixedAlphaVertexConsumerTests.java"))
}

dependencies {
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")
    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

tasks.test {
    useJUnitPlatform()
    workingDir = layout.buildDirectory.dir("test-runtime").get().asFile
    doFirst { workingDir.mkdirs() }
}

legacyForge {
    enable {
        forgeVersion = "1.20.1-${libs.versions.forge.get()}"
        isDisableRecompilation = System.getenv("CI") == "true"
    }

    mods {
        create("endercore") {
            sourceSet(sourceSets["main"])
        }
        create("endercore_tests") {
            sourceSet(gameTestSourceSet)
        }
    }

    addModdingDependenciesTo(sourceSets.test.get())
    addModdingDependenciesTo(gameTestSourceSet)

    runs {
        create("gameTestServer") {
            type = "gameTestServer"
            programArguments.add("nogui")
            systemProperty("java.awt.headless", "true")
            sourceSet = gameTestSourceSet
            gameDirectory = project.file("run/gametest-server")
            loadedMods = listOf(mods["endercore"], mods["endercore_tests"])
            systemProperty("forge.enabledGameTestNamespaces", "endercore_tests")
            systemProperty("endercore.gametest.report", layout.buildDirectory.file("test-results/gameTestServer/results.xml").get().asFile.absolutePath)
        }
    }
}

val prepareGameTestServerProfile = tasks.register<PrepareTestProfile>("prepareGameTestServerProfile") {
    gameDirectory = layout.projectDirectory.dir("run/gametest-server")
    client = false
}
tasks.matching { it.name == "runGameTestServer" }.configureEach {
    dependsOn(prepareGameTestServerProfile)
}
tasks.named("check") {
    dependsOn("runGameTestServer")
}

// Expand variables in mods.toml
var generateModMetadata = tasks.register<ProcessResources>("generateModMetadata") {
    val replaceProperties = mapOf(
            "mod_version" to project.version,
            "minecraft_version_range" to libs.versions.minecraft.get(),
            "forge_version_range" to "[${libs.versions.forge.get()},48)",
            "loader_version_range" to "[47,)",
    )

    inputs.properties(replaceProperties)
    expand(replaceProperties)
    from("src/main/templates")
    into("build/generated/sources/modMetadata")
}

// Add results to source set and to IDE sync
sourceSets.main.get().resources.srcDir(generateModMetadata)
legacyForge.ideSyncTask(generateModMetadata)

publishing {
    publications {
        create<MavenPublication>("endercore") {
            groupId = "com.enderio"
            artifactId = "endercore"
            version = "${project.version}"

            from(components["java"])

            pom {
                name.set("EnderCore")
                description.set("Ender Core is the library mod backing Ender IO")
                url.set("https://github.com/Team-EnderIO/EnderCore")

                licenses {
                    license {
                        name.set("Unlicense")
                        url.set("https://github.com/Team-EnderIO/EnderCore/blob/dev/1.21/LICENSE.txt")
                    }
                }

                scm {
                    url.set("https://github.com/Team-EnderIO/EnderCore.git")
                }
            }
        }
    }
}


mixin {
    add(sourceSets.main.get(), "endercore.refmap.json")
    config("endercore.mixins.json")
}

tasks.named<Jar>("jar") {
    manifest.attributes["MixinConfigs"] = "endercore.mixins.json"
}

// Load the actual production spawner modifier and painting advancements in the native test server.
tasks.named<ProcessResources>(gameTestSourceSet.processResourcesTaskName) {
    from(rootProject.file("enderio/src/generated/resources")) {
        include("data/enderio/loot_modifiers/*.json")
        include("data/enderio/advancements/adventure/rich.json")
        include("data/enderio/advancements/adventure/richer.json")
        include("data/enderio/tags/entity_types/*.json")
        include("data/c/tags/entity_types/bosses.json")
        include("data/c/tags/entity_types/capturing_not_supported.json")
    }
}
