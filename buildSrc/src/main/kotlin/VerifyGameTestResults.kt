import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import javax.xml.parsers.DocumentBuilderFactory

/** Reconciles actual discovery and execution after the native server has finished. */
@DisableCachingByDefault(because = "Verifies the reports from the immediately preceding server run")
abstract class VerifyGameTestResults : DefaultTask() {
    @get:InputFile
    abstract val discoveredTests: RegularFileProperty

    @get:InputFile
    abstract val results: RegularFileProperty

    @TaskAction
    fun verify() {
        val expected = discoveredTests.get().asFile.readLines().filter { it.isNotBlank() }.toSet()
        val factory = DocumentBuilderFactory.newInstance()
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        val document = factory.newDocumentBuilder().parse(results.get().asFile)
        val cases = document.getElementsByTagName("testcase")
        val executed = (0 until cases.length).map {
            cases.item(it).attributes.getNamedItem("name").nodeValue
        }.toSet()
        val missing = expected - executed
        val unexpected = executed - expected
        if (expected.isEmpty() || missing.isNotEmpty() || unexpected.isNotEmpty()) {
            throw GradleException("Native GameTest coverage mismatch after execution: discovered=${expected.size}, executed=${executed.size}, missing=$missing, unexpected=$unexpected")
        }
        val failures = document.getElementsByTagName("failure").length + document.getElementsByTagName("error").length
        val skipped = document.getElementsByTagName("skipped").length
        if (failures != 0 || skipped != 0) {
            throw GradleException("Native GameTests reported $failures failures/errors and $skipped skipped cases")
        }
        logger.lifecycle("Verified all ${expected.size} discovered gameplay tests executed and passed")
    }
}
