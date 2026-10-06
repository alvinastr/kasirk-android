package com.kasirkita.pos.presentation.transaction

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TransactionHistoryDateRangeTest {

    @Test
    fun localDateToUtcRange_usesAsiaJakartaHalfOpenLocalDay() {
        val zone = ZoneId.of("Asia/Jakarta")
        val (from, toExclusive) = localDateToUtcRange(LocalDate.of(2026, 10, 6), zone)

        assertEquals("2026-10-05T17:00:00Z", from)
        assertEquals("2026-10-06T17:00:00Z", toExclusive)
    }

    @Test
    fun localDateToUtcRange_excludesNextDayStartAndIncludesPreviousDayEnd() {
        val zone = ZoneId.of("Asia/Jakarta")
        val from = LocalDate.of(2026, 10, 6).atStartOfDay(zone).toInstant()
        val toExclusive = LocalDate.of(2026, 10, 7).atStartOfDay(zone).toInstant()

        val beforeStart = java.time.Instant.parse("2026-10-05T16:59:59.999Z")
        val atStart = java.time.Instant.parse("2026-10-05T17:00:00.000Z")
        val beforeEnd = java.time.Instant.parse("2026-10-06T16:59:59.999Z")
        val atEnd = java.time.Instant.parse("2026-10-06T17:00:00.000Z")

        assertEquals(false, beforeStart >= from && beforeStart < toExclusive)
        assertEquals(true, atStart >= from && atStart < toExclusive)
        assertEquals(true, beforeEnd >= from && beforeEnd < toExclusive)
        assertEquals(false, atEnd >= from && atEnd < toExclusive)
    }
}
