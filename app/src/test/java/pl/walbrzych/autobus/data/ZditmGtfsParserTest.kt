package pl.walbrzych.autobus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZditmGtfsParserTest {
    @Test
    fun parsesQuotedGtfsCsvFieldsWithoutBreakingCommasOrQuotes() {
        assertEquals(
            listOf("101", "Plac, Rodła \"A\"", "70.1"),
            parseGtfsCsvLine("101,\"Plac, Rodła \"\"A\"\"\",70.1"),
        )
    }

    @Test
    fun acceptsStandardGtfsAfterMidnightTimes() {
        assertEquals(86_460, parseGtfsTimeSeconds("24:01:00"))
        assertEquals(172_799, parseGtfsTimeSeconds("47:59:59"))
    }

    @Test
    fun rejectsInvalidGtfsClockValues() {
        assertNull(parseGtfsTimeSeconds("24:60:00"))
        assertNull(parseGtfsTimeSeconds("48:00:00"))
        assertNull(parseGtfsTimeSeconds("12:30"))
    }
}
