import dev.pr0gramm.tv.data.manifest.Manifest
import dev.pr0gramm.tv.data.manifest.ManifestProblem
import dev.pr0gramm.tv.data.manifest.ManifestResult
import dev.pr0gramm.tv.data.manifest.ServerPath
import java.io.File
import java.security.MessageDigest

/**
 * `checkManifest` (AC2): what the TV's parser cannot see. It parses with the app's own [Manifest.parse], then checks
 * that every referenced file exists below `dir` and that the APKs have the `sha256` and `size` the manifest promises.
 * Only a [ServerPath] is a file below `dir`; an absolute URL gets the parser's check only, without network.
 */
object ManifestCheck {
    /** One English line per problem; empty when the manifest is fine. [apks] = false skips the APK files (the example has none). */
    fun check(text: String, dir: File, apks: Boolean = true): List<String> {
        val manifest = when (val result = Manifest.parse(text)) {
            is ManifestResult.Invalid -> return result.problems.map(ManifestProblem::english)
            is ManifestResult.Valid -> result.manifest
        }
        val problems = mutableListOf<String>()
        fun exists(file: ServerPath): File? =
            File(dir, file.path).takeIf { it.isFile } ?: null.also { problems += "${file.path}: file missing" }
        manifest.images.filterIsInstance<ServerPath>().sortedBy { it.path }.forEach { exists(it) }
        if (apks) {
            manifest.apps.forEach { app ->
                val path = app.apk.file as? ServerPath ?: return@forEach
                val file = exists(path) ?: return@forEach
                val sha = sha256(file)
                if (sha != app.apk.sha256) problems += "${path.path}: sha256 is $sha, manifest has ${app.apk.sha256}"
                if (file.length() != app.apk.size) {
                    problems += "${path.path}: size is ${file.length()}, manifest has ${app.apk.size}"
                }
            }
        }
        return problems
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private const val BUFFER = 64 * 1024
}
