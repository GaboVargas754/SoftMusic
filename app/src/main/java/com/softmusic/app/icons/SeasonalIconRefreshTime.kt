package com.softmusic.app.icons

import java.time.Instant
import java.time.ZoneId

internal fun nextSeasonalIconRefresh(now: Instant, zone: ZoneId): Instant {
    val today = now.atZone(zone).toLocalDate()
    return SeasonalIconCalendar.nextChangeAfter(today).atStartOfDay(zone).toInstant()
}
