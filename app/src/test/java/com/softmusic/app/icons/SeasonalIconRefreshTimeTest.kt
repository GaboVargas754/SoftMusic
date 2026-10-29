package com.softmusic.app.icons

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonalIconRefreshTimeTest {
    @Test
    fun theNextRefreshUsesTheLocalHolidayBoundaryRatherThanUtcMidnight() {
        val now = Instant.parse("2026-11-01T03:00:00Z")
        val next = nextSeasonalIconRefresh(now, ZoneId.of("America/Mexico_City"))
        assertEquals(Instant.parse("2026-11-01T06:00:00Z"), next)
        assertTrue(next.isAfter(now))
    }

    @Test
    fun changingTimezoneRecomputesTheNextEventUsingTheNewLocalDate() {
        val now = Instant.parse("2026-11-01T03:00:00Z")
        assertEquals(Instant.parse("2026-11-10T15:00:00Z"), nextSeasonalIconRefresh(now, ZoneId.of("Asia/Tokyo")))
        assertEquals(Instant.parse("2026-11-01T06:00:00Z"), nextSeasonalIconRefresh(now, ZoneId.of("America/Mexico_City")))
    }

    @Test
    fun newYearReturnsToOriginalOnJanuaryElevenAcrossTheYearBoundary() {
        assertEquals(
            Instant.parse("2027-01-11T06:00:00Z"),
            nextSeasonalIconRefresh(Instant.parse("2026-12-31T20:00:00Z"), ZoneId.of("America/Mexico_City")),
        )
    }

    @Test
    fun theScheduleResolvesTheOffsetAtTheFutureBoundaryAcrossDaylightSavingTime() {
        assertEquals(
            Instant.parse("2026-11-11T05:00:00Z"),
            nextSeasonalIconRefresh(Instant.parse("2026-11-01T04:30:00Z"), ZoneId.of("America/New_York")),
        )
    }
}
