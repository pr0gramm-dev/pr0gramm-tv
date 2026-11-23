package dev.pr0gramm.tv.core.appversion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The version rule of AC10, shared by the Gradle build and the self-update. */
class AppVersionTest {
    @Test
    fun codeIsMajorTimes10000PlusMinorTimes100PlusPatch() {
        assertEquals(1L, AppVersion.parse("0.0.1")?.code)
        assertEquals(100L, AppVersion.parse("0.1.0")?.code)
        assertEquals(10203L, AppVersion.parse("1.2.3")?.code)
        assertEquals(129999L, AppVersion.parse("12.99.99")?.code)
    }

    @Test
    fun theNameIsMajorMinorPatch() {
        assertEquals("0.1.0", AppVersion.parse("0.1.0").toString())
        assertEquals("1.2.3", AppVersion.parse("v1.2.3").toString())
    }

    @Test
    fun aTagWithVParsesLikeTheVersion() {
        assertEquals(AppVersion.parse("1.2.3"), AppVersion.parse("v1.2.3"))
    }

    @Test
    fun minorOrPatchOver99IsNoVersion() {
        assertNull(AppVersion.parse("1.100.0"))
        assertNull(AppVersion.parse("1.0.100"))
    }

    @Test
    fun aTextWithoutTheSemVerFormIsNoVersion() {
        listOf(
            "", "1", "1.2", "1.2.3.4", "1.2.3-rc1", "1.2.3+build", "V1.2.3", "vv1.2.3", " 1.2.3", "1.2.3 ",
            "01.2.3", "1.02.3", "1.2.03", "-1.2.3", "1.-2.3", "a.b.c", "1..3", "１.2.3",
        ).forEach { assertNull(it, AppVersion.parse(it)) }
    }

    @Test
    fun theCodeIsAValidAndroidVersionCode() {
        assertNull(AppVersion.parse("0.0.0"))
        assertEquals(2_100_000_000L, AppVersion.parse("210000.0.0")?.code)
        assertNull(AppVersion.parse("210000.0.1"))
        assertNull(AppVersion.parse("99999999999999999999.0.0"))
    }
}
