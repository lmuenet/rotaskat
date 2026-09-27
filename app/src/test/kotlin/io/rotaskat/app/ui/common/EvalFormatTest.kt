package io.rotaskat.app.ui.common

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.Test
import kotlin.test.assertEquals

class EvalFormatTest {

    private val saturdayEvening = Instant.parse("2026-03-14T19:30:00Z")

    @Test
    fun `kurzes Datum mit Wochentag`() {
        assertEquals("Sa 14.3.", formatShortDate(saturdayEvening, TimeZone.UTC))
    }

    @Test
    fun `Uhrzeit ohne Sekunden`() {
        assertEquals("19:30", formatTime(saturdayEvening, TimeZone.UTC))
    }
}
