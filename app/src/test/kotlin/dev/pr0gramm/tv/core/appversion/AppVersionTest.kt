package dev.pr0gramm.tv.core.appversion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The version rule of AC10, shared by the Gradle build and the self-update (docs/release.md, "Version code"). */
class AppVersionTest {
    @Test
    fun aStableCodeIsTheBaseTimes1000() {
        assertEquals(1_000L, AppVersion.parse("0.0.1")?.code)
        assertEquals(100_000L, AppVersion.parse("0.1.0")?.code)
        assertEquals(101_000L, AppVersion.parse("0.1.1")?.code)
        assertEquals(10_203_000L, AppVersion.parse("1.2.3")?.code)
        assertEquals(129_999_000L, AppVersion.parse("12.99.99")?.code)
    }

    @Test
    fun aNightlyCodeIsTheBaseOfTheLastStableTimes1000PlusN() {
        assertEquals(5L, AppVersion.parse("0.0.0-nightly.5")?.code)
        assertEquals(100_003L, AppVersion.parse("0.1.0-nightly.3")?.code)
        assertEquals(100_017L, AppVersion.parse("0.1.0-nightly.17")?.code)
        assertEquals(101_001L, AppVersion.parse("0.1.1-nightly.1")?.code)
        assertEquals(10_203_042L, AppVersion.parse("1.2.3-nightly.42")?.code)
        assertEquals(17, AppVersion.parse("0.1.0-nightly.17")?.nightly)
        assertEquals(0, AppVersion.parse("0.1.0")?.nightly)
    }

    @Test
    fun aNightlyLiesBetweenItsStableAndTheNextStable() {
        val codes = listOf("0.1.0", "0.1.0-nightly.1", "0.1.0-nightly.999", "0.1.1", "0.1.1-nightly.1", "0.2.0")
            .map { AppVersion.parse(it)!!.code }
        assertEquals(codes.sorted(), codes)
        assertTrue(codes.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun theNameIsMajorMinorPatchAndTheNightlyNumber() {
        assertEquals("0.1.0", AppVersion.parse("0.1.0").toString())
        assertEquals("1.2.3", AppVersion.parse("v1.2.3").toString())
        assertEquals("0.1.0-nightly.17", AppVersion.parse("0.1.0-nightly.17").toString())
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
    fun theNightlyNumberIs1To999() {
        listOf("0.1.0-nightly.0", "0.1.0-nightly.1000", "0.1.0-nightly.01", "0.1.0-nightly.-1")
            .forEach { assertNull(it, AppVersion.parse(it)) }
        assertEquals(100_999L, AppVersion.parse("0.1.0-nightly.999")?.code)
    }

    @Test
    fun aTextWithoutTheSemVerFormIsNoVersion() {
        listOf(
            "", "1", "1.2", "1.2.3.4", "1.2.3-rc1", "1.2.3+build", "V1.2.3", "vv1.2.3", " 1.2.3", "1.2.3 ",
            "01.2.3", "1.02.3", "1.2.03", "-1.2.3", "1.-2.3", "a.b.c", "1..3", "１.2.3",
            "1.2.3-nightly", "1.2.3-nightly.", "1.2.3-Nightly.1", "1.2.3-nightly.1.2", "1.2.3.nightly.1",
        ).forEach { assertNull(it, AppVersion.parse(it)) }
    }

    @Test
    fun theCodeIsAValidAndroidVersionCode() {
        assertNull(AppVersion.parse("0.0.0"))
        assertEquals(2_100_000_000L, AppVersion.parse("210.0.0")?.code)
        assertNull(AppVersion.parse("210.0.1"))
        assertNull(AppVersion.parse("210.0.0-nightly.1"))
        assertEquals(2_099_999_999L, AppVersion.parse("209.99.99-nightly.999")?.code)
        assertNull(AppVersion.parse("99999999999999999999.0.0"))
    }
}
