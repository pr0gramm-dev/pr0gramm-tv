package dev.pr0gramm.tv.ui.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ScriptsTest {
    /** The real asset files; unit tests run in the module directory. */
    private val opened = mutableListOf<String>()
    private val scripts = Scripts { path ->
        opened += path
        File("src/main/assets", path).inputStream()
    }

    @Test
    fun everyScriptIsAJsFileInTheAssets() {
        for (script in Script.entries) {
            assertTrue(script.file, script.file.startsWith("js/") && script.file.endsWith(".js"))
            assertTrue(script.file, scripts[script].isNotBlank())
        }
    }

    @Test
    fun eachFileIsReadOnce() {
        repeat(3) { scripts[Script.FOCUS_RING] }
        scripts[Script.WATCH_VIDEO]
        assertEquals(listOf(Script.FOCUS_RING.file, Script.WATCH_VIDEO.file), opened)
    }

    @Test
    fun focusRingTakesTheThemeColoursAndReportsToTheBridge() {
        val ring = scripts[Script.FOCUS_RING]
        assertTrue(ring.contains(Scripts.ACCENT) && ring.contains(Scripts.OUTLINE))
        assertTrue(ring.contains("${PageBridge.NAME}.focused(") && ring.contains("${PageBridge.NAME}.fullscreen("))
        val calls = listOf("ring", "focusCenter", "focusAt", "standIn", "enterPlayer", "seek", "toggle")
        for (call in calls + "allowKeyboard") {
            assertTrue(call, ring.contains("$call:function"))
        }
    }

    @Test
    fun watchVideoReportsStartedPlayback() {
        assertTrue(scripts[Script.WATCH_VIDEO].contains("${PageBridge.NAME}.started()"))
    }

    @Test
    fun pageMetaReturnsTheKeysPageMetaParses() {
        val meta = scripts[Script.PAGE_META]
        for (key in listOf("titles", "title", "ogDescription", "description", "image", "heading")) {
            assertTrue(key, meta.contains("$key:"))
        }
    }
}
