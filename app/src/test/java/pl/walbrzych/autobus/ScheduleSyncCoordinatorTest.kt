package pl.walbrzych.autobus

import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.walbrzych.autobus.data.DepartureInfo
import pl.walbrzych.autobus.data.LiveVehicle
import pl.walbrzych.autobus.data.MyBusService
import pl.walbrzych.autobus.data.RealTimeDepartures
import pl.walbrzych.autobus.data.ScheduleCache
import pl.walbrzych.autobus.data.ScheduleSnapshot
import pl.walbrzych.autobus.data.ScheduleSyncCoordinator
import pl.walbrzych.autobus.data.ScheduleVersion
import pl.walbrzych.autobus.data.SyncResult

class ScheduleSyncCoordinatorTest {
    @Test
    fun keepsExistingCacheWhenServerSaysVersionIsCurrent() = runBlocking {
        val old = snapshot(377)
        val cache = FakeCache(old)
        val service = FakeService(compareCurrent = true)

        val result = ScheduleSyncCoordinator(service, cache).synchronize()

        assertTrue(result is SyncResult.Current)
        assertSame(old, cache.cachedSnapshot())
        assertEquals(0, service.downloadCalls)
    }

    @Test
    fun installsNewDatabaseOnlyAfterSuccessfulDownload() = runBlocking {
        val old = snapshot(377)
        val new = snapshot(378)
        val cache = FakeCache(old, nextInstall = new)
        val service = FakeService(compareCurrent = false)

        val result = ScheduleSyncCoordinator(service, cache).synchronize()

        assertTrue(result is SyncResult.Downloaded)
        assertSame(new, cache.cachedSnapshot())
        assertEquals(1, service.downloadCalls)
    }

    @Test
    fun interruptedOrBadDownloadLeavesLastKnownGoodCache() = runBlocking {
        val old = snapshot(377)
        val cache = FakeCache(old, installFailure = IllegalArgumentException("uszkodzona baza"))
        val service = FakeService(compareCurrent = false)

        val failure = runCatching { ScheduleSyncCoordinator(service, cache).synchronize() }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertSame(old, cache.cachedSnapshot())
    }

    @Test
    fun offlineFailureDoesNotInvalidateLastCache() = runBlocking {
        val old = snapshot(377)
        val cache = FakeCache(old)
        val service = FakeService(pingFailure = IOException("offline"))

        assertTrue(runCatching { ScheduleSyncCoordinator(service, cache).synchronize() }.isFailure)
        assertSame(old, cache.cachedSnapshot())
    }

    private fun snapshot(version: Int) = ScheduleSnapshot(
        stops = emptyList(), serviceDays = emptyList(), calendar = emptyMap(),
        version = ScheduleVersion(version, "2026-09-11", 1), lastSuccessfulUpdate = Instant.EPOCH,
    )

    private class FakeCache(
        private var active: ScheduleSnapshot?,
        private val nextInstall: ScheduleSnapshot? = null,
        private val installFailure: Throwable? = null,
    ) : ScheduleCache {
        override fun cachedSnapshot(): ScheduleSnapshot? = active
        override fun install(gzipPayload: ByteArray): ScheduleSnapshot {
            installFailure?.let { throw it }
            return requireNotNull(nextInstall).also { active = it }
        }
    }

    private class FakeService(
        private val compareCurrent: Boolean = false,
        private val pingFailure: IOException? = null,
    ) : MyBusService {
        var downloadCalls = 0
        override suspend fun ping(): Int = pingFailure?.let { throw it } ?: 2046
        override suspend fun compareSchedule(version: Int, generation: Int): Boolean = compareCurrent
        override suspend fun downloadSchedule(): ByteArray = "payload".encodeToByteArray().also { downloadCalls++ }
        override suspend fun realTimeDepartures(stopId: Int, groupId: Int): RealTimeDepartures = RealTimeDepartures("", null, emptyList())
        override suspend fun departureInfo(date: LocalDate, stopId: Int, uniqueTripId: Long): DepartureInfo? = null
        override suspend fun vehicles(line: String, directionCode: String): List<LiveVehicle> = emptyList()
    }
}
