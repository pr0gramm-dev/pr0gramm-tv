import com.android.build.api.variant.AndroidComponentsExtension
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * `pr0gramm.blocklist` (AC2b, AC9, AC9a): validates `local.json` (or `-PlocalJson=<file>`, only `adb` and `blocklist`) and
 * writes the blocklist asset. Without a private file it falls back to `local.example.json`, with a warning. The app
 * embeds no catalog; it shows only the one from the server. Apply it to the Android application module.
 */
class BlocklistPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val root = project.rootProject.projectDir
        val file = resolve(project, root)
        val copyFile = File(root, "blocklist.txt")
        val config = try {
            LocalConfig.parse(file.readText(), file.name)
        } catch (e: LocalConfigException) {
            throw GradleException(e.message!!, e)
        }

        // Deploy runs `acceptBlocklist assembleRelease` in one call, so everything that reads the copy runs after it.
        val accept = project.tasks.register("acceptBlocklist", AcceptBlocklistTask::class.java) {
            group = "build"
            description = "Adopts -PblocklistCandidate=<file> as blocklist.txt, or keeps the old copy, or fails."
            url.set(project.providers.provider { config.blocklist })
            candidate.set(project.providers.gradleProperty("blocklistCandidate").map { path ->
                File(path).let { if (it.isAbsolute) it else File(root, path) }
            })
            this.copyFile.set(copyFile)
        }
        val asset = project.tasks.register("blocklistAsset", BlocklistAssetTask::class.java) {
            group = "build"
            description = "Writes the cleaned blocklist.txt asset (empty without a list)."
            url.set(project.providers.provider { config.blocklist })
            list.from(copyFile)
            outputDir.set(project.layout.buildDirectory.dir("generated/blocklist/assets"))
            mustRunAfter(accept)
        }

        project.extensions.extraProperties.set("localConfig", config)
        project.pluginManager.withPlugin("com.android.application") {
            project.extensions.getByType(AndroidComponentsExtension::class.java).onVariants { variant ->
                variant.sources.assets?.addGeneratedSourceDirectory(asset, BlocklistAssetTask::outputDir)
            }
        }
    }

    private fun resolve(project: Project, root: File): File {
        val chosen = project.providers.gradleProperty("localJson").orNull?.let { File(it).let { f -> if (f.isAbsolute) f else File(root, it) } }
        if (chosen != null) {
            if (!chosen.isFile) throw GradleException("-PlocalJson=$chosen: Datei nicht gefunden")
            return chosen
        }
        val private = File(root, "local.json")
        if (private.isFile) return private
        project.logger.warn("WARNUNG: keine local.json gefunden, der Build nutzt local.example.json.")
        return File(root, "local.example.json")
    }

}

/** Writes the app asset `blocklist.txt`. It is empty when no list is configured or no copy exists (`hasBlocklist=false`). */
abstract class BlocklistAssetTask : DefaultTask() {
    @get:Input
    @get:Optional
    abstract val url: Property<String>

    /** The copy `blocklist.txt` in the repo root; a collection, because the file may be missing. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val list: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun write() {
        val out = outputDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        val file = list.files.single()
        val text = if (url.isPresent && file.isFile) file.readText() else ""
        if (url.isPresent && !file.isFile) {
            logger.warn("WARNUNG: ${file.name} fehlt, der Build hat keine Blockliste (erst mise run deploy ausführen).")
        }
        File(out, "blocklist.txt").writeText(if (text.isEmpty()) "" else BlocklistFormat.asset(BlocklistFormat.domains(text)))
    }
}

/** Adopts the downloaded list as `blocklist.txt` ([BlocklistFormat.choose]). Without `-PblocklistCandidate` the download counts as failed. */
abstract class AcceptBlocklistTask : DefaultTask() {
    @get:Input
    @get:Optional
    abstract val url: Property<String>

    @get:Internal
    abstract val candidate: Property<File>

    @get:Internal
    abstract val copyFile: Property<File>

    @TaskAction
    fun accept() {
        if (!url.isPresent) return
        val copy = copyFile.get()
        val candidateText = candidate.orNull?.takeIf { it.isFile }?.readText()
        when (val choice = BlocklistFormat.choose(candidateText, copy.takeIf { it.isFile }?.readText(), url.get())) {
            is BlocklistFormat.Choice.UseCandidate -> {
                val body = candidateText!!.lineSequence().dropWhile { it.startsWith(BlocklistFormat.SOURCE_PREFIX) }.joinToString("\n")
                val tmp = File(copy.parentFile, "${copy.name}.tmp")
                tmp.writeText(BlocklistFormat.copy(body, url.get()))
                tmp.renameTo(copy) || throw GradleException("${copy.name} nicht schreibbar.")
                logger.lifecycle("Blockliste: ${choice.count} Domains.")
            }
            // quiet, not warn: deploy runs Gradle with -q, and this warning must still reach the user.
            is BlocklistFormat.Choice.UseCopy -> logger.quiet("Warnung: ${choice.warning}")
            is BlocklistFormat.Choice.Fail -> throw GradleException(choice.message)
        }
    }
}
