import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/** Writes only isolated development settings; never modifies a normal launcher profile. */
@DisableCachingByDefault(because = "Minecraft can rewrite its options between launches")
abstract class PrepareTestProfile : DefaultTask() {
    @get:Internal
    abstract val gameDirectory: DirectoryProperty

    @get:Input
    abstract val client: Property<Boolean>

    @TaskAction
    fun prepare() {
        val directory = gameDirectory.get().asFile
        directory.mkdirs()
        // The user has explicitly accepted the Minecraft server EULA.
        directory.resolve("eula.txt").writeText("eula=true\n")
        if (!client.get()) return

        val optionsFile = directory.resolve("options.txt")
        val options = linkedMapOf<String, String>()
        if (optionsFile.exists()) {
            optionsFile.readLines().forEach { line ->
                val separator = line.indexOf(':')
                if (separator > 0) options[line.substring(0, separator)] = line.substring(separator + 1)
            }
        }
        options["narrator"] = "0"
        options["onboardAccessibility"] = "false"
        options["fullscreen"] = "false"
        options["pauseOnLostFocus"] = "false"
        options["tutorialStep"] = "none"
        for (category in listOf("master", "music", "record", "weather", "block", "hostile", "neutral", "player", "ambient", "voice")) {
            options["soundCategory_$category"] = "0.0"
        }
        optionsFile.writeText(options.entries.joinToString("\n", postfix = "\n") { "${it.key}:${it.value}" })
    }
}
