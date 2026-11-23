package dev.pr0gramm.tv.app

import dev.pr0gramm.tv.BuildConfig
import dev.pr0gramm.tv.data.manifest.Manifest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The parser compares against a constant, because build-logic compiles it without the app's `BuildConfig` (AC8). */
class OwnPackageTest {
    @Test fun `the manifest's own package is the application id`() {
        assertEquals(BuildConfig.APPLICATION_ID, Manifest.OWN_PACKAGE)
    }
}
