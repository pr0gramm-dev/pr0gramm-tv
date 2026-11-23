package dev.pr0gramm.tv.data.savedpages

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import dev.pr0gramm.tv.data.manifest.SiteId
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * The stored list as JSON text. Images are stored by file name and resolved against [imageDir].
 * A single broken entry is skipped; a file that is not a JSON array is corrupt ([CorruptionException]).
 */
internal class SavedListSerializer(private val imageDir: File) : Serializer<List<SavedPage>> {
    override val defaultValue: List<SavedPage> = emptyList()

    override suspend fun readFrom(input: InputStream): List<SavedPage> {
        val text = input.readBytes().toString(Charsets.UTF_8)
        if (text.isEmpty()) return defaultValue
        val array = try {
            JSONArray(text)
        } catch (e: JSONException) {
            throw CorruptionException("my list file is not valid JSON", e)
        }
        return (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::entry) }
    }

    override suspend fun writeTo(t: List<SavedPage>, output: OutputStream) {
        val array = JSONArray()
        t.forEach { array.put(json(it)) }
        output.write(array.toString().toByteArray(Charsets.UTF_8))
    }

    private fun entry(o: JSONObject): SavedPage? = if (o.has(SAVED_AT)) required(o) else null

    private fun required(o: JSONObject): SavedPage? {
        val site = text(o, SITE)
        val url = text(o, URL)
        val title = text(o, TITLE)
        val image = text(o, IMAGE)?.let { File(imageDir, it) }?.takeIf { it.isFile }
        return if (site != null && url != null && title != null) {
            SavedPage(SiteId(site), url, title, text(o, DESCRIPTION), o.optLong(SAVED_AT), image)
        } else {
            null
        }
    }

    private fun text(o: JSONObject, key: String): String? = o.optString(key).takeIf { it.isNotEmpty() }

    private fun json(e: SavedPage) = JSONObject().apply {
        put(SITE, e.site.value)
        put(URL, e.url)
        put(TITLE, e.title)
        e.description?.let { put(DESCRIPTION, it) }
        put(SAVED_AT, e.savedAt)
        e.image?.let { put(IMAGE, it.name) }
    }

    private companion object {
        const val SITE = "site"
        const val URL = "url"
        const val TITLE = "title"
        const val DESCRIPTION = "description"
        const val SAVED_AT = "savedAt"
        const val IMAGE = "image"
    }
}
