package com.softmusic.app.icons

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SeasonalIconCalendarTest {
    @Test
    fun `seasonal icon keys remain stable`() {
        assertEquals(
            listOf(
                "original", "halloween", "christmas", "day_of_the_dead", "new_year",
                "valentines", "independence", "easter", "music_day", "anniversary",
            ),
            SeasonalIcon.entries.map { it.key },
        )
    }

    @Test
    fun `fixed seasonal windows include both boundaries`() {
        assertWindow("2026-02-07", "2026-02-21", SeasonalIcon.Valentines)
        assertWindow("2026-05-29", "2026-06-12", SeasonalIcon.Anniversary)
        assertWindow("2026-06-15", "2026-06-30", SeasonalIcon.MusicDay)
        assertWindow("2026-09-01", "2026-09-30", SeasonalIcon.Independence)
        assertWindow(
            "2026-10-15", "2026-10-31", SeasonalIcon.Halloween,
            after = SeasonalIcon.DayOfTheDead,
        )
        assertWindow(
            "2026-11-01", "2026-11-10", SeasonalIcon.DayOfTheDead,
            before = SeasonalIcon.Halloween,
        )
        assertWindow(
            "2026-12-01", "2026-12-30", SeasonalIcon.Christmas,
            after = SeasonalIcon.NewYear,
        )
    }

    @Test
    fun `anniversary is centered on June fifth and leaves a gap before music day`() {
        assertIcon("2026-06-05", SeasonalIcon.Anniversary)
        assertIcon("2026-06-13", SeasonalIcon.Original)
        assertIcon("2026-06-14", SeasonalIcon.Original)
        assertIcon("2026-06-15", SeasonalIcon.MusicDay)
    }

    @Test
    fun `Christmas and New Year switch directly across leap and ordinary years`() {
        for (year in 2023..2026) {
            assertWindow(
                "$year-12-31", "${year + 1}-01-10", SeasonalIcon.NewYear,
                before = SeasonalIcon.Christmas,
            )
            assertIcon("$year-12-30", SeasonalIcon.Christmas)
            assertIcon("${year + 1}-01-01", SeasonalIcon.NewYear)
        }
    }

    @Test
    fun `Easter follows Gregorian Sunday with seven days on either side`() {
        // Published Gregorian Easter dates, including the earliest and latest possible Sundays:
        // https://www.census.gov/data/software/x13as/genhol/easter-dates.html
        val easterSundays = listOf(
            "1818-03-22", "1943-04-25", "1954-04-18", "1962-04-22", "2000-04-23",
            "2024-03-31", "2025-04-20", "2026-04-05", "2027-03-28", "2038-04-25",
        )
        for (sunday in easterSundays.map(LocalDate::parse)) {
            assertWindow(
                sunday.minusDays(7).toString(),
                sunday.plusDays(7).toString(),
                SeasonalIcon.Easter,
            )
        }
    }

    @Test
    fun `dates outside approved windows use the original icon including leap day`() {
        listOf(
            "2024-02-29", "2026-01-11", "2026-03-01", "2026-05-01",
            "2026-07-01", "2026-08-31", "2026-10-14", "2026-11-30",
        ).forEach { assertIcon(it, SeasonalIcon.Original) }
    }

    @Test
    fun `next change finds fixed window starts exits and adjacent seasons`() {
        val changes = listOf(
            "2026-01-01" to "2026-01-11",
            "2026-01-10" to "2026-01-11",
            "2026-01-11" to "2026-02-07",
            "2026-02-07" to "2026-02-22",
            "2026-05-28" to "2026-05-29",
            "2026-06-05" to "2026-06-13",
            "2026-06-13" to "2026-06-15",
            "2026-06-15" to "2026-07-01",
            "2026-07-01" to "2026-09-01",
            "2026-09-01" to "2026-10-01",
            "2026-10-14" to "2026-10-15",
            "2026-10-15" to "2026-11-01",
            "2026-10-31" to "2026-11-01",
            "2026-11-01" to "2026-11-11",
            "2026-11-30" to "2026-12-01",
            "2026-12-01" to "2026-12-31",
            "2026-12-30" to "2026-12-31",
            "2026-12-31" to "2027-01-11",
        )
        changes.forEach { (date, expectedChange) -> assertNextChange(date, expectedChange) }
    }

    @Test
    fun `next change finds Easter boundaries after ordinary and leap Februaries`() {
        assertNextChange("2024-02-29", "2024-03-24")
        assertNextChange("2024-03-24", "2024-04-08")
        assertNextChange("2024-04-07", "2024-04-08")
        assertNextChange("2025-02-22", "2025-04-13")
        assertNextChange("2025-04-20", "2025-04-28")
        assertNextChange("2026-03-01", "2026-03-29")
        assertNextChange("2026-04-12", "2026-04-13")
        assertNextChange("2027-03-01", "2027-03-21")
    }

    private fun assertWindow(
        start: String,
        end: String,
        icon: SeasonalIcon,
        before: SeasonalIcon = SeasonalIcon.Original,
        after: SeasonalIcon = SeasonalIcon.Original,
    ) {
        val first = LocalDate.parse(start)
        val last = LocalDate.parse(end)
        assertEquals(before, SeasonalIconCalendar.iconFor(first.minusDays(1)), "Day before $start")
        var date = first
        while (date <= last) {
            assertEquals(icon, SeasonalIconCalendar.iconFor(date), date.toString())
            date = date.plusDays(1)
        }
        assertEquals(after, SeasonalIconCalendar.iconFor(last.plusDays(1)), "Day after $end")
    }

    private fun assertIcon(date: String, icon: SeasonalIcon) {
        assertEquals(icon, SeasonalIconCalendar.iconFor(LocalDate.parse(date)), date)
    }

    private fun assertNextChange(date: String, expected: String) {
        val currentDate = LocalDate.parse(date)
        val actualChange = SeasonalIconCalendar.nextChangeAfter(currentDate)
        assertEquals(LocalDate.parse(expected), actualChange, date)
        assertTrue(actualChange > currentDate, date)
    }
}
