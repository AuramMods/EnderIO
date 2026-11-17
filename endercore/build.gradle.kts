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
