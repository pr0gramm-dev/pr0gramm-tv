package dev.pr0gramm.tv.ui.browser

import android.content.res.AssetManager
import java.io.InputStream

/** The JavaScript the browser runs in pages, one file each under `assets/js/` (AC12). */
enum class Script(val file: String) {
    FOCUS_RING("js/focus-ring.js"),
    ZOOM_VIEWPORT("js/zoom-viewport.js"),
    WATCH_VIDEO("js/watch-video.js"),
    PAGE_META("js/page-meta.js"),
    PAUSE_MEDIA("js/pause-media.js"),
}

/** Reads each [Script] once and keeps it. Main thread only. */
class Scripts(private val open: (String) -> InputStream) {
    constructor(assets: AssetManager) : this({ path -> assets.open(path) })

    private val cache = mutableMapOf<Script, String>()

    operator fun get(script: Script): String =
        cache.getOrPut(script) { open(script.file).bufferedReader().use { it.readText() } }

    companion object {
        /** Placeholders in [Script.FOCUS_RING] for the CSS colours of the frame and its contrast line. */
        const val ACCENT = "@ACCENT@"
        const val OUTLINE = "@OUTLINE@"
    }
}
