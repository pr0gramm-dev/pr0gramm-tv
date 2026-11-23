package dev.pr0gramm.tv.data.appsettings

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList

/**
 * The app language the user picks on the settings page, and the one table of the app's languages (review item 44).
 * [tag] is what goes to `LocaleManager` ("" = none); [ownName] is the language's name in itself, null for [SYSTEM].
 * The order of the entries is the order of [next].
 *
 * A new language: one entry here, its `values-<tag>/strings.xml`, a `<locale>` in `res/xml/locales_config.xml` and
 * its help text in `SettingsHelp` (the compiler asks for it). `LanguagesTest` checks the tags against the resources.
 */
enum class LanguageChoice(val tag: String, val ownName: String?) {
    SYSTEM("", null),
    ENGLISH("en", "English"),
    GERMAN("de", "Deutsch"),
    ;

    /** The entry after this one, after the last [SYSTEM]: System -> English -> Deutsch -> System (AC6). */
    fun next(): LanguageChoice = entries[(ordinal + 1) % entries.size]

    companion object {
        /** The language of the texts in `values/`, for every tag the app has no texts for. */
        val DEFAULT = ENGLISH

        /** Every language with own texts, without [SYSTEM]. */
        val languages: List<LanguageChoice> get() = entries - SYSTEM

        /**
         * From `LocaleList.toLanguageTags()`: "" is [SYSTEM]; a first tag whose primary language is one of
         * [languages] is that language; every other list is [DEFAULT] (AC5).
         */
        fun of(tags: String): LanguageChoice {
            if (tags.isEmpty()) return SYSTEM
            val primary = tags.substringBefore(',').substringBefore('-').substringBefore('_')
            return languages.firstOrNull { it.tag.equals(primary, ignoreCase = true) } ?: DEFAULT
        }
    }
}

/**
 * The stored app locale as language tags: "" for none. Its one implementation is [AndroidLocaleStore]; tests use a
 * fake (review item 21).
 */
interface LocaleStore {
    var tags: String
}

/**
 * `LocaleManager` as a [LocaleStore]. Android keeps the app language after a process restart and an app update (AC8).
 */
class AndroidLocaleStore(context: Context) : LocaleStore {
    private val manager = context.getSystemService(LocaleManager::class.java)

    override var tags: String
        get() = manager.applicationLocales.toLanguageTags()
        set(value) {
            manager.applicationLocales = LocaleList.forLanguageTags(value)
        }
}

/**
 * The app language over a [LocaleStore]. It keeps no copy of the language (AC13): the store is the only truth.
 */
class AppLanguage(private val store: LocaleStore) {
    val current: LanguageChoice get() = LanguageChoice.of(store.tags)

    /**
     * Sets [LanguageChoice.next] in the store and returns it. Android recreates the activities when the effective
     * locale changes.
     */
    fun switch(): LanguageChoice {
        val next = current.next()
        store.tags = next.tag
        return next
    }
}
