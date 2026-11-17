plugins {
    id("mod-common-conventions")
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
