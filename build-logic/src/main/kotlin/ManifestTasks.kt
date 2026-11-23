import java.io.File
import kotlin.system.exitProcess

/**
 * Entry point of `./gradlew -p build-logic checkManifest -Pmanifest=<file>` (see `build.gradle.kts`): prints every
 * problem of the manifest and exits 1 if there is one. Arguments: the manifest file, optionally `--skip-apks`.
 */
object ManifestTasks {
    @JvmStatic
    fun main(args: Array<String>) {
        val file = File(args.firstOrNull() ?: error("-Pmanifest=<Datei> fehlt"))
        require(file.isFile) { "-Pmanifest=$file: Datei nicht gefunden" }
        val problems = ManifestCheck.check(file.readText(), file.absoluteFile.parentFile, apks = "--skip-apks" !in args)
        problems.forEach { System.err.println("${file.name}: $it") }
        if (problems.isNotEmpty()) exitProcess(1)
        println("${file.name}: in Ordnung")
    }
}
