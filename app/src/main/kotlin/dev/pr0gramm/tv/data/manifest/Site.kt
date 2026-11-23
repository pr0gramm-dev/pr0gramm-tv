package dev.pr0gramm.tv.data.manifest

/** The `id` of a site in the manifest, `[a-z][a-z0-9]*`. */
@JvmInline
value class SiteId(val value: String)

enum class Control { FOCUS, POINTER }

enum class Layout { NORMAL, DESKTOP }

/** Page zoom in percent, always inside [RANGE]. */
@JvmInline
value class Zoom internal constructor(val percent: Int) {
    companion object {
        val RANGE = 50..150
        const val STEP = 10

        /** null outside [RANGE]. */
        fun of(percent: Int): Zoom? = if (percent in RANGE) Zoom(percent) else null

        /** 50, 60 … 150, plus [default] in its place when it is off that grid (AC4). */
        fun steps(default: Zoom): List<Zoom> =
            (RANGE step STEP).map(::Zoom).plus(default).distinct().sortedBy { it.percent }
    }
}

data class SiteOptions(
    val control: Control,
    val zoom: Zoom,
    val layout: Layout,
    val blocklist: Boolean,
    val favorite: Boolean,
)

data class Site(
    val id: SiteId,
    val name: String,
    val url: String,
    val description: String?,
    val category: String?,
    val tags: List<String>,
    val color: Int?,
    val allowInsecureContent: Boolean,
    /** Domains never blocked for this site; already contains the site's own host. */
    val allow: Set<String>,
    /** From the manifest: cursor, desktopMode, zoom, blocklist; favorite is false. */
    val defaults: SiteOptions,
    /** The banner image, a path next to the manifest or an absolute URL; null without one. */
    val banner: Resource?,
)
