import java.io.File
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ManifestCheckTest {
    @TempDir lateinit var dir: Path

    private val apkBytes = "apk".toByteArray()
    private val sha = MessageDigest.getInstance("SHA-256").digest(apkBytes).joinToString("") { "%02x".format(it) }

    private fun manifest(
        sha256: String = sha,
        size: Long = apkBytes.size.toLong(),
        banner: String = "img/a.png",
        image: String = "img/kodi.png",
        apk: String = "apks/kodi.apk",
    ) =
        """{"version": 1, "date": "2026-11-18",
            "sites": [{"id": "nova", "name": "Nova", "url": "https://nova.example/", "banner": "$banner"}],
            "apps": [{"package": "org.kodi", "name": "Kodi", "image": "$image",
            "versionCode": 1, "apk": "$apk", "sha256": "$sha256", "size": $size}]}"""

    private fun file(path: String, bytes: ByteArray = byteArrayOf(1)) =
        File(dir.toFile(), path).apply { parentFile.mkdirs(); writeBytes(bytes) }

    private fun complete() {
        file("img/a.png"); file("img/kodi.png"); file("apks/kodi.apk", apkBytes)
    }

    private fun check(text: String, apks: Boolean = true) = ManifestCheck.check(text, dir.toFile(), apks)

    @Test fun `a complete manifest has no problems`() {
        complete()
        assertEquals(emptyList<String>(), check(manifest()))
    }

    @Test fun `parser problems are reported and nothing else`() {
        val problems = check("""{"version": 0}""")
        assertEquals(
            listOf("version: 0 must be between 1 and 2147483647", "date: missing", "sites: missing", "apps: missing"),
            problems,
        )
    }

    @Test fun `broken json gives one English line`() {
        assertEquals(listOf("not valid JSON: field name in quotes expected (character 1)"), check("{"))
    }

    @Test fun `missing images and apk are reported`() {
        val problems = check(manifest())
        val expected = listOf("img/a.png: file missing", "img/kodi.png: file missing", "apks/kodi.apk: file missing")
        assertEquals(expected, problems)
    }

    @Test fun `wrong sha256 and size are reported`() {
        complete()
        val problems = check(manifest(sha256 = "0".repeat(64), size = 99))
        val expected = listOf(
            "apks/kodi.apk: sha256 is $sha, manifest has ${"0".repeat(64)}",
            "apks/kodi.apk: size is 3, manifest has 99",
        )
        assertEquals(expected, problems)
    }

    @Test fun `apks can be skipped for the example`() {
        file("img/a.png"); file("img/kodi.png")
        assertEquals(emptyList<String>(), check(manifest(sha256 = "0".repeat(64), size = 1), apks = false))
        assertTrue(check(manifest(), apks = false).isEmpty())
    }

    @Test fun `absolute urls are checked by the parser only, without network`() {
        file("img/a.png")
        val apk = "https://github.com/o/r/releases/download/v1/a.apk"
        val text = manifest(image = "https://cdn.example/kodi.png", apk = apk)
        assertEquals(emptyList<String>(), check(text, apks = true))
        assertEquals(emptyList<String>(), check(text, apks = false))
    }
}
