package pl.walbrzych.autobus

import org.junit.Assert.assertTrue
import org.junit.Test
import pl.walbrzych.autobus.data.ScheduleIntegrityRules

class ScheduleIntegrityRulesTest {
    @Test
    fun acceptsACompleteScheduleRegardlessOfStopCount() {
        ScheduleIntegrityRules.requireCompleteCounts(stops = 1, lines = 1, variants = 1, timetables = 1)
    }

    @Test
    fun acceptsCompleteLiveDatabaseCountsAndRouteReferences() {
        ScheduleIntegrityRules.requireCompleteCounts(stops = 636, lines = 21, variants = 267, timetables = 2311)
        ScheduleIntegrityRules.requireNoOrphanDepartures(0)
        ScheduleIntegrityRules.requireKnownRouteStops(listOf("2", "3"), setOf("2", "3", "4"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPartialScheduleInsteadOfReplacingCache() {
        ScheduleIntegrityRules.requireCompleteCounts(stops = 0, lines = 6, variants = 8, timetables = 60)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsRouteWithUnknownStop() {
        ScheduleIntegrityRules.requireKnownRouteStops(listOf("2", "missing"), setOf("2"))
    }

    @Test
    fun reportHasNoOrphans() {
        assertTrue(runCatching { ScheduleIntegrityRules.requireNoOrphanDepartures(0) }.isSuccess)
    }
}
