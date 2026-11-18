import java.text.SimpleDateFormat
import java.util.*

plugins {
    id("mod-common-conventions")
}

println("Building Ender IO version ${project.version}")


sourceSets {
    main {
        resources {
            srcDir("src/generated/resources")
        }
    }

    create("datagen") {
        compileClasspath += sourceSets.main.get().output
    }

    create("gametest") {
        compileClasspath += sourceSets.main.get().output
        //runtimeClasspath += configurations.getByName("gametestLocalRuntime")
    }

    create("registryTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

// JUnit runs inside Forge's transformed server for registry-dependent tests.
// Its libraries are a test-only game library, never part of a shipped mod jar.
val registryTestLibraries = configurations.create("registryTestLibraries")
configurations["registryTestImplementation"].extendsFrom(configurations.implementation.get(), registryTestLibraries)
configurations["registryTestCompileOnly"].extendsFrom(configurations.compileOnly.get())
configurations["registryTestRuntimeOnly"].extendsFrom(configurations.runtimeOnly.get())

dependencies {
    registryTestLibraries(libs.junitJupiter)
    registryTestLibraries("org.junit.jupiter:junit-jupiter-engine:5.7.1")
    registryTestLibraries(libs.junitPlatformLauncher)
}

val registryTestLibraryJar = tasks.register<Jar>("registryTestLibraryJar") {
    archiveBaseName = "enderio-registry-test-libraries"
    archiveVersion = ""
    destinationDirectory = layout.buildDirectory.dir("test-libraries")
    from({ registryTestLibraries.map { zipTree(it) } })
    exclude("module-info.class", "META-INF/versions/**", "META-INF/MANIFEST.MF", "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest.attributes("FMLModType" to "GAMELIBRARY", "Automatic-Module-Name" to "enderio.registry.test.libraries")
}

configurations {
    val localRuntime by creating

    runtimeClasspath {
        extendsFrom(localRuntime)
    }

    testImplementation {
        extendsFrom(implementation.get())
    }

    testCompileOnly  {
        extendsFrom(compileOnly.get())
    }

    testRuntimeOnly  {
        // TODO: Mekanism breaks our unit tests...
        exclude(group = "mekanism", module = "Mekanism")
        extendsFrom(runtimeOnly.get())
    }

    testAnnotationProcessor  {
        extendsFrom(annotationProcessor.get())
    }

    val datagenImplementation by getting {
        extendsFrom(implementation.get())
    }
    val datagenCompileOnly by getting {
        extendsFrom(compileOnly.get())
    }
    val datagenRuntimeOnly by getting {
        extendsFrom(runtimeOnly.get())
    }
    val datagenAnnotationProcessor by getting {
        extendsFrom(annotationProcessor.get())
    }
    val gametestImplementation by getting {
        extendsFrom(implementation.get())
    }
    val gametestCompileOnly by getting {
        extendsFrom(compileOnly.get())
    }
    val gametestRuntimeOnly by getting {
        extendsFrom(runtimeOnly.get())
    }
    val gametestAnnotationProcessor by getting {
        extendsFrom(annotationProcessor.get())
    }
}

obfuscation {
    createRemappingConfiguration(configurations["localRuntime"])
}

dependencies {
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")
    val localRuntime by configurations.getting

    // EnderIO bundles EnderCore.
    api(project(":endercore"))
    jarJar(project(":endercore"))

    // Include built-in "addons"
    jarJar(project(":enderio-modded-conduits"))
    localRuntime(project(":enderio-modded-conduits"))

    // Almost Unified
    modCompileOnly(libs.almostUnified)

    // JEI
    modCompileOnly(libs.jei)
    "modLocalRuntime"(libs.jei)

    // CC: Tweaked
    modCompileOnly(libs.ccTweaked)
    "modLocalRuntime"(libs.ccTweaked)

    // Jade for conduit addon
    modCompileOnly(libs.jade)
    "modLocalRuntime"(libs.jade)

    //Athena ctm
    "modLocalRuntime"(libs.athena)

    // AE2
    modCompileOnly(libs.ae2)

    "modLocalRuntime"(libs.ae2)

    // TODO: Re-add Enchantment descriptions if we add enchantments again

    // Mekanism
    modCompileOnly(libs.mekanism)

    "modLocalRuntime"(libs.mekanism)

    // Refined Storage
    modCompileOnly(libs.refinedStorage)
    "modLocalRuntime"(libs.refinedStorage)

    //Laserio
    modCompileOnly(libs.laserio)
    "modLocalRuntime"(libs.laserio)
    "modLocalRuntime"(libs.patchouli)

    // Dependencies of optional mods (legacy remapping configurations are non-transitive).
    "modLocalRuntime"(libs.guideMe)
    "modLocalRuntime"(libs.ftbLibrary)
    modCompileOnly(libs.architectury)
    "modLocalRuntime"(libs.architectury)

    // Graphlib
    api(libs.graphlib)
    jarJar(libs.graphlib)

    // FTB Ultimine Addon
    modCompileOnly(libs.ftbUltimine)
    "modLocalRuntime"(libs.ftbUltimine)

	// Curio compat
    modCompileOnly(libs.curios)
    "modLocalRuntime"(libs.curios)

    // Forge 1.20.1 renderer/shader implementations for compatibility testing
    modCompileOnly(libs.embeddium)
    modCompileOnly(libs.oculus)
    "modLocalRuntime"(libs.embeddium)
    "modLocalRuntime"(libs.oculus)

    // Unit tests
    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)

    // GameTest sources will be migrated from NeoForge to the Forge GameTest API.
}

legacyForge {
    enable {
        forgeVersion = "1.20.1-${libs.versions.forge.get()}"
        isDisableRecompilation = System.getenv("CI") == "true"
    }

    accessTransformers {
        publish(project.file("src/main/resources/META-INF/accesstransformer.cfg"))
    }

    addModdingDependenciesTo(sourceSets.getByName("datagen"))
    addModdingDependenciesTo(sourceSets.getByName("gametest"))
    addModdingDependenciesTo(sourceSets.getByName("registryTest"))

    val modEnderio by mods.creating {
        sourceSet(sourceSets.getByName("datagen"))
        sourceSet(sourceSets.getByName("main"))
    }

    val modEnderioGametests by mods.creating {
        sourceSet(sourceSets.getByName("gametest"))
    }

    val modEnderioRegistryTests by mods.creating {
        sourceSet(sourceSets["registryTest"])
    }

    runs {
        configureEach {
            logLevel = org.slf4j.event.Level.INFO
        }

        // Client & Server runs contain default addons for ease.
        // Data + Game Test focus purely on the core mod.
        val client by creating {
            client()
            gameDirectory = project.file("run/client")
            loadedMods = listOf(modEnderio)
        }

        val server by creating {
            server()
            programArguments.add("nogui")
            loadedMods = listOf(modEnderio)

            gameDirectory = project.file("run/server")
        }

        val data by creating {
            data()
            gameDirectory = project.file("run/datagen")
            systemProperty("java.awt.headless", "true")
            loadedMods = listOf(modEnderio)

            programArguments.addAll(
                    "--mod", "enderio",
                    // TODO: Fix missing models...
                    //"--all",
                    "--server", "--client",
                    "--output", file("src/generated/resources").absolutePath,
                    "--existing", file("src/main/resources").absolutePath,
            )
        }

        val gameTestServer by creating {
            type = "gameTestServer"
            gameDirectory = project.file("run/gametest-server")
            loadedMods = listOf(modEnderio, modEnderioGametests)
            sourceSet = sourceSets["gametest"]

            // Where the test framework's JUnitSummaryDumper writes its XML, for CI reporting.
            systemProperty("enderio.gametest.junit_dir", layout.buildDirectory.dir("test-results/gameTestServer").get().asFile.absolutePath)
        }

        val registryTestServer by creating {
            type = "gameTestServer"
            sourceSet = sourceSets["registryTest"]
            gameDirectory = project.file("run/registry-test-server")
            loadedMods = listOf(modEnderio, modEnderioRegistryTests)
            programArguments.add("nogui")
            systemProperty("java.awt.headless", "true")
            systemProperty("forge.enabledGameTestNamespaces", "enderio_registry_tests")
            systemProperty("enderio.registry.report", layout.buildDirectory.file("test-results/registryTestServer/results.xml").get().asFile.absolutePath)
            dependencies.add(additionalRuntimeClasspathConfiguration.name, files(registryTestLibraryJar))
            taskBefore(registryTestLibraryJar)
        }

        // Useful for debugging tests, but will include the gametest datapack so I separated the run config.
        val gameTestClient by creating {
            client()
            gameDirectory = project.file("run/gametest-client")
            loadedMods = listOf(modEnderio, modEnderioGametests)
        }
    }

    addModdingDependenciesTo(sourceSets.test.get())
}

// Expand variables in mods.toml
var generateModMetadata = tasks.register<ProcessResources>("generateModMetadata") {
    val replaceProperties = mapOf(
            "mod_version" to project.version,
            "minecraft_version_range" to libs.versions.minecraft.get(),
            "forge_version_range" to "[${libs.versions.forge.get()},48)",
            "loader_version_range" to "[47,)",
            "mekanism_version_range" to libs.versions.mekanismMod.get(),
            "ae2_version_range" to libs.versions.ae2.get(),
            "refinedstorage_version_range" to libs.versions.refinedStorage.get(),
            "ftb_ultimine_version_range" to libs.versions.ftbUltimine.get(),
			"curios_version_range" to libs.versions.curios.get()
    )

    inputs.properties(replaceProperties)
    into("build/generated/sources/modMetadata")

    from("src/main/templates") {
        filesMatching("META-INF/mods.toml") {
            expand(replaceProperties)
        }
    }

    from("${rootDir}/docs/img/") {
        include("logo.png")
    }
}

// Add results to source set and to IDE sync
sourceSets.main.get().resources.srcDir(generateModMetadata)
legacyForge.ideSyncTask(generateModMetadata)

tasks {
    test {
        val testDirectory = layout.buildDirectory.dir("unit-test-run").get().asFile
        workingDir(testDirectory)
        doFirst { testDirectory.mkdirs() }
    }
}

tasks.named<Jar>("jar") {
    // FIXME: Temporary - shipping datagen classes with build again for Endergy.
    from(sourceSets["datagen"].output)
}

tasks.withType<Jar> {
    if (name == "registryTestLibraryJar") return@withType
    manifest {
        attributes(mapOf(
                "MixinConfigs" to "enderio.mixins.json",
                "Specification-Title" to "Ender IO",
                "Specification-Vendor" to "Team Ender IO",
                "Specification-Version" to "1",
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
                "Implementation-Vendor" to "Team Ender IO",
                "Implementation-Timestamp" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(Date())
        ))
    }
}

tasks.register<Jar>("apiJar") {
    archiveClassifier.set("api")

    from(sourceSets["main"].output)
    from(sourceSets["main"].allJava)
    include("com/enderio/enderio/api/**")
}

tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets["main"].allJava)
}

tasks.build {
    dependsOn(tasks["apiJar"])
    dependsOn(tasks["sourcesJar"])
}

publishing {
    publications {
        create<MavenPublication>(project.name) {
            groupId = "com.enderio"
            artifactId = project.name
            version = "${project.version}"

            from(components["java"])
            artifact(tasks["apiJar"])
            artifact(tasks["sourcesJar"])

            pom {
                name.set("EnderIO")
                description.set("Ender IO")
                url.set("https://github.com/Team-EnderIO/EnderIO")

                licenses {
                    license {
                        name.set("Unlicense")
                        url.set("https://github.com/Team-EnderIO/EnderIO/blob/dev/1.21.1/LICENSE.txt")
                    }
                }

                scm {
                    url.set("https://github.com/Team-EnderIO/EnderIO.git")
                }
            }
        }
    }
}

mixin {
    add(sourceSets.main.get(), "enderio.refmap.json")
    config("enderio.mixins.json")
}

// These settings provide silence. Non-activating window instrumentation is a
// separate runtime-port task and must be implemented before launching clients.
for ((runName, directory) in mapOf(
    "Client" to "client", "Server" to "server",
    "GameTestClient" to "gametest-client", "GameTestServer" to "gametest-server",
    "RegistryTestServer" to "registry-test-server"
)) {
    val prepareProfile = tasks.register<PrepareTestProfile>("prepare${runName}Profile") {
        gameDirectory.set(layout.projectDirectory.dir("run/$directory"))
        client.set(runName.endsWith("Client"))
    }
    tasks.matching { it.name == "run$runName" }.configureEach {
        dependsOn(prepareProfile)
    }
}

tasks.check { dependsOn("runRegistryTestServer") }

obfuscation {
    reobfuscate(tasks.named<Jar>("apiJar"), sourceSets.main.get())
}

// Some pinned integrations construct client key mappings during parallel mod construction.
// Datagen has no client; serial Forge loading avoids the observed shared-key-table race.
val prepareDataProfile = tasks.register<PrepareTestProfile>("prepareDataProfile") {
    gameDirectory = layout.projectDirectory.dir("run/datagen")
    client = true
    forgeLoadingThreads = 1
}
tasks.matching { it.name == "runData" }.configureEach { dependsOn(prepareDataProfile) }
