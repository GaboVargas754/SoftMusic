package com.softmusic.app.icons

import java.time.LocalDate

/**
 * Resolves the approved inclusive seasonal windows using the caller's local date.
 * Fixed dates take precedence over Easter, followed by the original icon.
 */
object SeasonalIconCalendar {
    fun iconFor(date: LocalDate): SeasonalIcon {
        val month = date.monthValue
        val day = date.dayOfMonth
        val fixedIcon = when {
            (month == 12 && day == 31) || (month == 1 && day <= 10) -> SeasonalIcon.NewYear
            month == 2 && day in 7..21 -> SeasonalIcon.Valentines
            (month == 5 && day >= 29) || (month == 6 && day <= 12) -> SeasonalIcon.Anniversary
            month == 6 && day >= 15 -> SeasonalIcon.MusicDay
            month == 9 -> SeasonalIcon.Independence
            month == 10 && day >= 15 -> SeasonalIcon.Halloween
            month == 11 && day <= 10 -> SeasonalIcon.DayOfTheDead
            month == 12 -> SeasonalIcon.Christmas
            else -> null
        }
        if (fixedIcon != null) return fixedIcon

        val easterSunday = easterSunday(date.year)
        return if (date >= easterSunday.minusDays(7) && date <= easterSunday.plusDays(7)) {
            SeasonalIcon.Easter
        } else {
            SeasonalIcon.Original
        }
    }

    /** Returns the first date strictly after [date] that uses a different icon. */
    fun nextChangeAfter(date: LocalDate): LocalDate {
        val currentIcon = iconFor(date)
        for (daysAhead in 1L..367L) {
            val candidate = date.plusDays(daysAhead)
            if (iconFor(candidate) != currentIcon) return candidate
        }
        error("The seasonal icon must change within the next year")
    }

    private fun easterSunday(year: Int): LocalDate {
        // Gregorian computus as published by the Australian Bureau of Meteorology:
        // https://www.sws.bom.gov.au/Educational/5/3/1
        val a = Math.floorMod(year, 19)
        val b = Math.floorDiv(year, 100)
        val c = Math.floorMod(year, 100)
        val d = Math.floorDiv(b, 4)
        val e = Math.floorMod(b, 4)
        val f = Math.floorDiv(b + 8, 25)
        val g = Math.floorDiv(b - f + 1, 3)
        val h = Math.floorMod(19 * a + b - d - g + 15, 30)
        val i = c / 4
        val k = c % 4
        val l = Math.floorMod(32 + 2 * e + 2 * i - h - k, 7)
        val m = (a + 11 * h + 22 * l) / 451
        val monthAndDay = h + l - 7 * m + 114
        return LocalDate.of(year, monthAndDay / 31, monthAndDay % 31 + 1)
    }
}
