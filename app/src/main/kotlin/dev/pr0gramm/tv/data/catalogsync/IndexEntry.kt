package dev.pr0gramm.tv.data.catalogsync

import java.net.URI

/**
 * An app as the server's manifest describes it. [apk] is the resolved URL of its APK: next to the manifest or any
 * http(s) URL the manifest names.
 */
internal data class IndexEntry(
    val packageName: String,
    val name: String,
    val versionCode: Long,
    val apk: URI,
    val sha256: String,
    val size: Long,
)
