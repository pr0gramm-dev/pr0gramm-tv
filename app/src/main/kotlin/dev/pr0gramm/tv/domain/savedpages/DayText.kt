package dev.pr0gramm.tv.domain.savedpages

import dev.pr0gramm.tv.R
import dev.pr0gramm.tv.core.text.UiText
import dev.pr0gramm.tv.core.text.text
import dev.pr0gramm.tv.data.savedpages.Shelf
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** When something was saved or used, in the words of the start page. [today] and [zone] place the time. Pure. */
object DayText {
    /** How an entry of [shelf] says when it was saved: "saved Nov 13" in My List, else [relative]. */
    fun of(shelf: Shelf, millis: Long, today: LocalDate, zone: ZoneId): UiText {
        val day = day(millis, zone)
        return if (shelf == Shelf.MY_LIST) text(R.string.day_saved, date(day)) else relative(day, today)
    }

    /** "today", "yesterday", the weekday within the last week ("Mon"), else the date ("Nov 13"). */
    fun relative(millis: Long, today: LocalDate, zone: ZoneId): UiText = relative(day(millis, zone), today)

    private fun relative(day: LocalDate, today: LocalDate): UiText {
        val days = ChronoUnit.DAYS.between(day, today)
        return when {
            days <= 0 -> text(R.string.day_today)
            days == 1L -> text(R.string.day_yesterday)
            days < WEEK -> text(WEEKDAYS[day.dayOfWeek.value - 1])
            else -> date(day)
        }
    }

    private fun day(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    private fun date(day: LocalDate): UiText = UiText.Date(R.string.date_day, day)

    /** Monday first, as [java.time.DayOfWeek.getValue] counts. */
    private val WEEKDAYS = listOf(
        R.string.day_mon,
        R.string.day_tue,
        R.string.day_wed,
        R.string.day_thu,
        R.string.day_fri,
        R.string.day_sat,
        R.string.day_sun,
    )
    private const val WEEK = 7L
}
