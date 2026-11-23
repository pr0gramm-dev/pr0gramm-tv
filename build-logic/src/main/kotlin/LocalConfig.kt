import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.net.URI

/** All problems of a local.json, one line each, like `blocklist: muss eine http(s)-Adresse sein`. */
class LocalConfigException(val source: String, val problems: List<String>) :
    RuntimeException("$source ist fehlerhaft:\n  " + problems.joinToString("\n  "))

/** The validated local.json: the private operator settings `adb` and `blocklist`. The sites live in the manifest. */
data class LocalConfig(val adb: String?, val blocklist: String?) {
    companion object {
        private val TOP_LEVEL = setOf("adb", "blocklist")

        /** Throws [LocalConfigException]. */
        fun parse(text: String, source: String): LocalConfig {
            val root = try {
                JsonParser.parseString(text)
            } catch (e: JsonParseException) {
                throw LocalConfigException(source, listOf("kein gültiges JSON: ${e.message}"))
            }
            if (!root.isJsonObject) throw LocalConfigException(source, listOf("oberste Ebene muss ein Objekt sein"))
            val errors = mutableListOf<String>()
            val obj = root.asJsonObject
            (obj.keySet() - TOP_LEVEL).forEach { errors += "$it: unbekanntes Feld (erlaubt: ${TOP_LEVEL.joinToString()})" }
            val adb = obj.get("adb")?.let { a ->
                if (a.isJsonPrimitive && a.asJsonPrimitive.isString) a.asString
                else null.also { errors += "adb: muss ein String sein, z. B. \"192.0.2.10:5555\"" }
            }
            val blocklist = obj.get("blocklist")?.let { b ->
                if (b.isJsonPrimitive && b.asJsonPrimitive.isString && isHttpUrl(b.asString)) b.asString
                else null.also { errors += "blocklist: muss eine http(s)-Adresse sein" }
            }
            if (errors.isNotEmpty()) throw LocalConfigException(source, errors)
            return LocalConfig(adb, blocklist)
        }

        private fun isHttpUrl(s: String): Boolean {
            val uri = try { URI(s) } catch (_: Exception) { null }
            return uri != null && uri.scheme in setOf("http", "https") && !uri.host.isNullOrEmpty()
        }
    }
}
