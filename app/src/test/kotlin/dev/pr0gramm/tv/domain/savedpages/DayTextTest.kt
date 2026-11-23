package dev.pr0gramm.tv.domain.savedpages

import dev.pr0gramm.tv.core.text.Strings
import dev.pr0gramm.tv.data.savedpages.Shelf
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** [DayText] in German and in English (AC10, AC11). */
class DayTextTest {
    private val zone: ZoneId = ZoneOffset.UTC

    // Wednesday, 18 November 2026.
    private val today = LocalDate.of(2026, 11, 18)

    private fun millis(day: LocalDate) = day.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun relative(day: LocalDate) =
        DayText.relative(millis(day), today, zone).let { Strings.de(it) to Strings.en(it) }

    @Test
    fun anEntryOfMyListSaysTheDayItWasSaved() {
        val text = DayText.of(Shelf.MY_LIST, millis(LocalDate.of(2026, 11, 13)), today, zone)
        assertEquals("gemerkt am 13.11.", Strings.de(text))
        assertEquals("saved Nov 13", Strings.en(text))
    }

    @Test
    fun anEntryOfContinueWatchingSaysTheDayRelativeToToday() {
        val text = DayText.of(Shelf.CONTINUE_WATCHING, millis(today.minusDays(1)), today, zone)
        assertEquals("gestern", Strings.de(text))
        assertEquals("yesterday", Strings.en(text))
    }

    @Test
    fun aDayIsTodayYesterdayAWeekdayWithinAWeekElseTheDate() {
        assertEquals("heute" to "today", relative(today))
        assertEquals("heute" to "today", relative(today.plusDays(1)))
        assertEquals("gestern" to "yesterday", relative(today.minusDays(1)))
        assertEquals("11.11." to "Nov 11", relative(today.minusDays(7)))
    }

    @Test
    fun everyWeekdayWithinTheLastWeekHasItsShortName() {
        // Monday 16 to Sunday 22 November 2026, each seen from two days later.
        val days = (16..22).map { LocalDate.of(2026, 11, it) }
        assertEquals(DayOfWeek.entries, days.map { it.dayOfWeek })
        val names = days.map { day ->
            DayText.relative(millis(day), day.plusDays(2), zone).let { Strings.de(it) to Strings.en(it) }
        }
        assertEquals(listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"), names.map { it.first })
        assertEquals(listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"), names.map { it.second })
    }
}
