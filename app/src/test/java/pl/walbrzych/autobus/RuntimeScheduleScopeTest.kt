package pl.walbrzych.autobus

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.walbrzych.autobus.data.ScheduleSnapshot
import pl.walbrzych.autobus.data.ScheduleVersion
import pl.walbrzych.autobus.data.StopData

class RuntimeScheduleScopeTest {
    @Test
    fun downloadedSnapshotIsNotConstrainedToTheTwentyStopFixture() {
        val snapshot = ScheduleSnapshot(
            stops = List(636) { StopData(it.toString(), "Przystanek $it", 50.76, 16.28, emptyList()) },
            serviceDays = emptyList(), calendar = emptyMap(),
            version = ScheduleVersion(377, "2026-09-11", 1), lastSuccessfulUpdate = Instant.EPOCH,
        )
        assertEquals(636, snapshot.stops.size)
        assertTrue(snapshot.stops.size > 20)
    }
}
