import io.gitlab.arturbosch.detekt.DetektPlugin
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * `pr0gramm.quality` (AC13): detekt with detekt-formatting (ktlint rules) and the config in
 * `config/detekt.yml`, plus `sourceRules` ([SourceRules]) and `packageGraph` ([PackageGraph]). `check` runs all
 * three.
 */
class QualityPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply(LifecycleBasePlugin::class.java)
        project.pluginManager.apply(DetektPlugin::class.java)

        val detekt = project.extensions.getByType(DetektExtension::class.java)
        detekt.buildUponDefaultConfig = true
        detekt.config.setFrom(project.rootProject.file("config/detekt.yml"))
        project.dependencies.add(
            "detektPlugins",
            "io.gitlab.arturbosch.detekt:detekt-formatting:${detekt.toolVersion}",
        )

        val sourceRules = project.tasks.register("sourceRules", SourceRulesTask::class.java) {
            group = LifecycleBasePlugin.VERIFICATION_GROUP
            description = "No .kt, .kts or .js file over ${SourceRules.MAX_LINES} lines, no @Suppress without a reason, " +
                "no German string literal in the app code."
            sources.from(
                project.fileTree(project.projectDir) {
                    include(SourceRules.EXTENSIONS.flatMap { listOf("*.$it", "src/**/*.$it") })
                },
            )
            baseDir.set(project.rootProject.layout.projectDirectory)
            marker.set(project.layout.buildDirectory.file("sourceRules/ok"))
        }
        val packageGraph = project.tasks.register("packageGraph", PackageGraphTask::class.java) {
            group = LifecycleBasePlugin.VERIFICATION_GROUP
            description = "Layers app > ui > domain > data > core, no references between slices (architecture.md §5)."
            sources.from(project.fileTree(project.projectDir) { include("${PackageGraph.SOURCE_ROOT}**/*.kt") })
            baseDir.set(project.rootProject.layout.projectDirectory)
            marker.set(project.layout.buildDirectory.file("packageGraph/ok"))
        }
        project.tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) { dependsOn(sourceRules, packageGraph) }
    }
}

/**
 * A check over the text of [sources]: [problems] reads every file at once, and the task fails with all of them under
 * [heading]. On success it writes [marker], so an unchanged tree stays up to date.
 */
abstract class SourceCheckTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:IgnoreEmptyDirectories
    abstract val sources: ConfigurableFileCollection

    /** Messages name files relative to this directory. */
    @get:Internal
    abstract val baseDir: DirectoryProperty

    /** Written on success so an unchanged tree stays up to date. */
    @get:OutputFile
    abstract val marker: RegularFileProperty

    /** The first line of the failure message. */
    @get:Internal
    protected abstract val heading: String

    /** Every problem in [files], a map from the path relative to [baseDir] to the file's text, sorted by path. */
    protected abstract fun problems(files: Map<String, String>): List<String>

    @TaskAction
    fun run() {
        val base = baseDir.get().asFile
        val files = sources.files.sortedBy { it.path }
            .associate { it.relativeTo(base).invariantSeparatorsPath to it.readText() }
        val problems = problems(files)
        if (problems.isNotEmpty()) {
            throw GradleException(heading + "\n  " + problems.joinToString("\n  "))
        }
        marker.get().asFile.writeText("ok\n")
    }
}

/** Runs [SourceRules] over [sources] (each file, then the unused allowlist entries) and fails with every problem at once. */
abstract class SourceRulesTask : SourceCheckTask() {
    override val heading = "Quelltext-Regeln verletzt:"

    override fun problems(files: Map<String, String>) =
        files.flatMap { (path, text) -> SourceRules.violations(path, text) } + SourceRules.unusedAllowed(files)
}

/** Runs [PackageGraph] over [sources] (each file, then the `core` cycles) and fails with every problem at once. */
abstract class PackageGraphTask : SourceCheckTask() {
    override val heading = "Package-Graph verletzt (Tabelle: PackageGraph.TABLE, docs/architecture.md §5):"

    override fun problems(files: Map<String, String>) =
        files.flatMap { (path, text) -> PackageGraph.violations(path, text) } + PackageGraph.cycles(files)
}
