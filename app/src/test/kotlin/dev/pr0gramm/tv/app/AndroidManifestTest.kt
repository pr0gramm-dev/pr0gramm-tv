package dev.pr0gramm.tv.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The manifest settings of the localisation (AC3, AC7). The app names its locale config, and no activity lists
 * `locale` in `configChanges`: Android rebuilds the activity on a language change, so `remember` values resolve again.
 * Unit tests run in the module directory.
 */
class AndroidManifestTest {
    private val document = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(File("src/main/AndroidManifest.xml"))

    @Test
    fun theApplicationNamesTheLocaleConfig() {
        val application = document.getElementsByTagName("application").item(0) as Element
        assertEquals("@xml/locales_config", application.getAttributeNS(ANDROID, "localeConfig"))
    }

    @Test
    fun noActivityHandlesALocaleChangeItself() {
        val activities = document.getElementsByTagName("activity")
        assertTrue("no activity in the manifest", activities.length > 0)
        for (i in 0 until activities.length) {
            val activity = activities.item(i) as Element
            val changes = activity.getAttributeNS(ANDROID, "configChanges").split('|')
            assertFalse("${activity.getAttributeNS(ANDROID, "name")}: $changes", "locale" in changes)
        }
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
