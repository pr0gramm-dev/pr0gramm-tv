package dev.pr0gramm.tv.data.sitesettings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.pr0gramm.tv.data.manifest.Control
import dev.pr0gramm.tv.data.manifest.Layout
import dev.pr0gramm.tv.data.manifest.SiteId
import dev.pr0gramm.tv.data.manifest.SiteOptions
import dev.pr0gramm.tv.data.manifest.Zoom

/**
 * The stored form of per-site settings: only deviations from the site's defaults, one key
 * `<id>/<field>` per field. A value equal to its default is never stored.
 */
internal object SiteOverrides {
    private const val SEPARATOR = '/'
    private const val CONTROL = "control"
    private const val ZOOM = "zoom"
    private const val LAYOUT = "layout"
    private const val BLOCKLIST = "blocklist"
    private const val FAVORITE = "favorite"
    private val FIELDS = listOf(CONTROL, ZOOM, LAYOUT, BLOCKLIST, FAVORITE)

    /** The site id of a stored key, or null when the key does not have the `<id>/<field>` shape. */
    fun siteOf(key: Preferences.Key<*>): SiteId? {
        val name = key.name
        val at = name.lastIndexOf(SEPARATOR)
        return if (at > 0 && name.substring(at + 1) in FIELDS) SiteId(name.substring(0, at)) else null
    }

    /** [defaults] with every valid stored value of site [id] applied; invalid values are ignored. */
    fun decode(defaults: SiteOptions, prefs: Preferences, id: SiteId): SiteOptions {
        val stored = prefs.asMap().entries.associate { (key, value) -> key.name to value }
        fun value(field: String): Any? = stored[name(id, field)]
        return defaults.copy(
            control = (value(CONTROL) as? String)?.let { s -> Control.entries.firstOrNull { it.name == s } }
                ?: defaults.control,
            zoom = (value(ZOOM) as? Int)?.let(Zoom::of) ?: defaults.zoom,
            layout = (value(LAYOUT) as? String)?.let { s -> Layout.entries.firstOrNull { it.name == s } }
                ?: defaults.layout,
            blocklist = value(BLOCKLIST) as? Boolean ?: defaults.blocklist,
            favorite = value(FAVORITE) as? Boolean ?: defaults.favorite,
        )
    }

    /** Replaces all keys of site [id] in [into] with the deviations of [value] from [defaults]. */
    fun encode(defaults: SiteOptions, value: SiteOptions, id: SiteId, into: MutablePreferences) {
        clear(id, into)
        if (value.control != defaults.control) into[stringPreferencesKey(name(id, CONTROL))] = value.control.name
        if (value.zoom != defaults.zoom) into[intPreferencesKey(name(id, ZOOM))] = value.zoom.percent
        if (value.layout != defaults.layout) into[stringPreferencesKey(name(id, LAYOUT))] = value.layout.name
        if (value.blocklist != defaults.blocklist) into[booleanPreferencesKey(name(id, BLOCKLIST))] = value.blocklist
        if (value.favorite != defaults.favorite) into[booleanPreferencesKey(name(id, FAVORITE))] = value.favorite
    }

    /** Removes every key of site [id]. */
    fun clear(id: SiteId, from: MutablePreferences) {
        from.asMap().keys.filter { siteOf(it) == id }.forEach { from.remove(it) }
    }

    private fun name(id: SiteId, field: String) = "${id.value}$SEPARATOR$field"
}
